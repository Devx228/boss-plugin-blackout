package ai.rever.boss.plugin.dynamic.blackout.application

import ai.rever.boss.plugin.api.AiBudget
import ai.rever.boss.plugin.api.AiGatewayAPI
import ai.rever.boss.plugin.api.AiMessage
import ai.rever.boss.plugin.api.AiRequest
import ai.rever.boss.plugin.api.AiStopReason
import ai.rever.boss.plugin.api.AiToolCall
import ai.rever.boss.plugin.api.AiToolOutcome
import ai.rever.boss.plugin.dynamic.blackout.mcp.BlackoutTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One line of the engineer console. Kind drives colour and icon only. */
data class CrewEvent(val round: Int, val kind: Kind, val text: String) {
    enum class Kind { CALL, RESULT, SAY, FAIL, NOTE }
}

data class CrewStatus(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val provider: String = "",
    val model: String = "",
    val status: String = "",
    val servedRound: Int = 0,
    val toolCalls: Int = 0,
    val tokens: Int = 0,
    val log: List<CrewEvent> = emptyList()
) {
    val label: String get() = when {
        !available -> "No AI gateway"
        model.isNotBlank() -> "$provider / $model"
        else -> "Host gateway"
    }
}

/**
 * Runs the host's configured model as the BLUE engineer, through exactly the same four
 * game tools an external MCP agent would call. Nothing here shortcuts the rules: the
 * model gets the agent view only, and a wrong configuration loses the round like anyone's.
 *
 * This is an optional seat. With it switched off the MCP tools remain the only way in,
 * and no scripted stand-in ever fills the engineer's chair.
 */
class EngineerCrew(private val gateway: AiGatewayAPI?, private val tools: BlackoutTools, private val session: Session) {

    private val _status = MutableStateFlow(CrewStatus(available = gateway != null))
    val status: StateFlow<CrewStatus> = _status

    private var loop: Job? = null
    private var shift: Job? = null
    private var servedMatch: String? = null
    private var servedRound = -1

    init {
        val model = runCatching { gateway?.activeModel() }.getOrNull()
        _status.update {
            it.copy(
                provider = model?.providerName.orEmpty(),
                model = model?.modelId.orEmpty(),
                status = if (gateway == null) "This host exposes no AI gateway. Attach an agent over MCP instead." else "Idle."
            )
        }
    }

    fun start(scope: CoroutineScope) {
        if (loop != null) return
        loop = scope.launch {
            while (isActive) {
                delay(400)
                if (_status.value.enabled && shift?.isActive != true) pump(scope)
            }
        }
    }

    fun stop() {
        shift?.cancel(); shift = null
        loop?.cancel(); loop = null
    }

    fun setEnabled(on: Boolean) {
        if (gateway == null) return
        _status.update { it.copy(enabled = on, status = if (on) "Waiting for the round to open." else "Stood down.") }
        if (!on) { shift?.cancel(); shift = null }
    }

    /** Clears the console for a new match. */
    fun reset() {
        shift?.cancel(); shift = null
        servedMatch = null; servedRound = -1
        _status.update { it.copy(log = emptyList(), busy = false, toolCalls = 0, tokens = 0, servedRound = 0,
            status = if (it.enabled) "Waiting for the round to open." else it.status) }
    }

    private fun pump(scope: CoroutineScope) {
        val match = session.match ?: return
        val view = match.publicView()
        if (view.outcome != "IN_PROGRESS") return
        if (match.humanView().agentLocked) return
        if (servedMatch == match.id && servedRound == view.round) return
        servedMatch = match.id
        servedRound = view.round
        shift = scope.launch { workRound(match.id, view.round) }
    }

    private suspend fun workRound(matchId: String, round: Int) {
        val api = gateway ?: return
        _status.update { it.copy(busy = true, servedRound = round, status = "Working round $round.") }
        log(round, CrewEvent.Kind.NOTE, "Engineer shift for round $round opened.")
        val request = AiRequest(
            system = SYSTEM,
            messages = listOf(AiMessage(AiMessage.ROLE_USER, opening(matchId, round))),
            temperature = 0.2f,
            maxTokens = 1400,
            timeoutMs = SHIFT_MS
        )
        val outcome = runCatching {
            api.runAgent(request, tools.aiTools(), AiBudget(maxSteps = 12, timeoutMs = SHIFT_MS, maxTokens = 24_000)) { call ->
                dispatch(round, call)
            }
        }
        val result = outcome.getOrNull()
        val failure = outcome.exceptionOrNull() ?: result?.exceptionOrNull()
        if (failure is CancellationException) throw failure
        when {
            failure != null -> {
                log(round, CrewEvent.Kind.FAIL, failure.message ?: failure::class.java.simpleName)
                _status.update { it.copy(busy = false, status = "Gateway call failed. Retry, or commit through MCP.") }
                servedRound = -1
            }
            else -> {
                val agent = result?.getOrNull()
                agent?.text?.takeIf { it.isNotBlank() }?.let { log(round, CrewEvent.Kind.SAY, it.trim().take(400)) }
                val committed = session.match?.takeIf { it.id == matchId }?.humanView()?.agentLocked == true
                val stop = agent?.stopReason ?: AiStopReason.UNKNOWN
                _status.update {
                    it.copy(
                        busy = false,
                        tokens = it.tokens + (agent?.usage?.totalTokens ?: 0),
                        status = if (committed) "Round $round locked by $stop." else "Round $round ended without a commitment ($stop)."
                    )
                }
                if (!committed) {
                    log(round, CrewEvent.Kind.FAIL, "No engineering order was locked. The round will count as incomplete.")
                    servedRound = -1
                }
            }
        }
    }

    private suspend fun dispatch(round: Int, call: AiToolCall): AiToolOutcome {
        val short = call.name.removePrefix(BlackoutTools.PREFIX)
        log(round, CrewEvent.Kind.CALL, "$short ${compact(call.argumentsJson)}")
        var result = tools.call(short, call.argumentsJson)
        // Observe is deliberately rate limited. A model cannot wait, so wait for it once.
        if (result.isError && "RATE_LIMIT" in result.text) {
            delay(1_100)
            result = tools.call(short, call.argumentsJson)
        }
        log(round, if (result.isError) CrewEvent.Kind.FAIL else CrewEvent.Kind.RESULT, compact(result.text))
        _status.update { it.copy(toolCalls = it.toolCalls + 1) }
        return AiToolOutcome(call.id, result.text, result.isError)
    }

    private fun opening(matchId: String, round: Int): String = buildString {
        append("Match $matchId, round $round. ")
        append("Call blackout_v1_observe first, then scan, then tell your pilot what the circuit needs, ")
        append("then lock your configuration with blackout_v1_commit. ")
        append("Pass matchId \"$matchId\" and round $round on every call, with a fresh requestId each time.")
    }

    private fun compact(text: String): String = text.replace(Regex("\\s+"), " ").trim().take(300)

    private fun log(round: Int, kind: CrewEvent.Kind, text: String) {
        _status.update { it.copy(log = (it.log + CrewEvent(round, kind, text)).takeLast(80)) }
    }

    private companion object {
        const val SHIFT_MS = 100_000L
        val SYSTEM = """
            You are the ENGINEER of crew BLUE in BLACKOUT: Rival Crews, a two-role station duel.
            A human PILOT shares your crew. Each round the pilot picks the combat action, the
            power route and the compartment to hit, brace or patch; you pick the engineering
            configuration: thermal, polarity and output. Both orders lock, then the round
            resolves simultaneously against a rival crew.

            Each station has four compartments: REACTOR (energy income), SHIELDS (barrier
            strength), RELAY (grid push) and LIFE (lose it and the match is over).

            You hold two things the pilot does not:
            - The sensor sweep, with exact integrity and barrier numbers for BOTH stations.
              The pilot sees only ONLINE, DAMAGED or DOWN on the rival deck.
            - The bus condition: the required thermal setting and the one polarity scan.

            You cannot see the route map at all. Only the pilot knows which routes are broken,
            insulated, crossed or high-output, and the pilot cannot choose a working route until
            you say whether the bus is hot and whether polarity is inverted.

            Work each round in this order:
            1. blackout_v1_observe - sweep, manual, pilot messages and your remaining budgets.
            2. blackout_v1_scan - one scan per round; it returns DIRECT or INVERT.
            3. blackout_v1_message - one short line carrying the facts the pilot cannot get
               anywhere else: hot or cool bus, inverted or direct polarity, which route
               properties that implies, and which rival compartment is worth hitting with the
               exact number behind it. Say if their own life support is one hit from failing.
            4. blackout_v1_commit - lock thermal, polarity and output. Never end a round without it.

            Configuration rules that decide the round:
            - Set thermal to exactly the value observe reports.
            - Set polarity to INVERT when the scan says INVERT, otherwise DIRECT.
            - Choose BOOST only when the pilot has confirmed the route is high-output and the
              crew can afford one more energy. BOOST on a normal route fails the entire round.
            - A failed circuit still spends the energy, so a wrong guess wastes the round.

            Read the board like a tactician, not a reporter. A reactor at or below 3 starves the
            rival of energy; a shield array at or below 3 halves what their barriers can hold;
            life support below 4 is a kill next round. Barriers only cover the compartment they
            sit on, so a braced compartment is the wrong thing to shoot.

            Keep messages to one or two short lines of plain fact. Do not narrate. Do not ask
            the pilot to do your job. Always finish the round with a commit.
        """.trimIndent()
    }
}
