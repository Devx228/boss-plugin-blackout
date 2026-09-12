package ai.rever.boss.plugin.dynamic.blackout.application

import ai.rever.boss.plugin.api.AiBudget
import ai.rever.boss.plugin.api.AiGatewayAPI
import ai.rever.boss.plugin.api.AiMessage
import ai.rever.boss.plugin.api.AiRequest
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

/** One line of the archivist console. Kind drives colour only. */
data class CrewEvent(val kind: Kind, val text: String) {
    enum class Kind { CALL, RESULT, SAY, FAIL, NOTE }
}

data class CrewStatus(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val provider: String = "",
    val model: String = "",
    val status: String = "",
    val toolCalls: Int = 0,
    val tokens: Int = 0,
    val log: List<CrewEvent> = emptyList()
) {
    val label: String
        get() = when {
            !available -> "No AI gateway"
            model.isNotBlank() -> provider + " / " + model
            else -> "Host gateway"
        }
}

/**
 * Runs the host's configured model as the archivist, through exactly the same four tools an
 * external MCP agent would call. Nothing here shortcuts the game: the model gets the
 * archivist view only, never the map, and never a finding the pilot has not sent.
 *
 * The seat is optional. Switched off, the MCP tools remain the only way in, and no scripted
 * stand-in ever fills the chair.
 */
class Archivist(
    private val gateway: AiGatewayAPI?,
    private val tools: BlackoutTools,
    private val session: Session
) {
    private val _status = MutableStateFlow(CrewStatus(available = gateway != null))
    val status: StateFlow<CrewStatus> = _status

    private var loop: Job? = null
    private var turn: Job? = null
    private var servedCase: String? = null
    private var servedRevision = -1L

    init {
        val model = runCatching { gateway?.activeModel() }.getOrNull()
        _status.update {
            it.copy(
                provider = model?.providerName.orEmpty(),
                model = model?.modelId.orEmpty(),
                status = if (gateway == null) {
                    "This host exposes no AI gateway. Attach an agent over MCP instead."
                } else {
                    "Idle."
                }
            )
        }
    }

    fun start(scope: CoroutineScope) {
        if (loop != null) return
        loop = scope.launch {
            while (isActive) {
                delay(500)
                if (_status.value.enabled && turn?.isActive != true) pump(scope)
            }
        }
    }

    fun stop() {
        turn?.cancel(); turn = null
        loop?.cancel(); loop = null
    }

    fun setEnabled(on: Boolean) {
        if (gateway == null) return
        _status.update {
            it.copy(enabled = on, status = if (on) "Waiting for the case to open." else "Stood down.")
        }
        if (!on) { turn?.cancel(); turn = null }
    }

    /** Clears the console for a new case. */
    fun reset() {
        turn?.cancel(); turn = null
        servedCase = null
        servedRevision = -1L
        _status.update {
            it.copy(
                log = emptyList(), busy = false, toolCalls = 0, tokens = 0,
                status = if (it.enabled) "Waiting for the case to open." else it.status
            )
        }
    }

    /**
     * The archivist takes a turn whenever the pilot has moved. Its own calls bump the
     * revision too, so the mark is taken after the turn ends; otherwise it would answer
     * itself forever.
     */
    private fun pump(scope: CoroutineScope) {
        val game = session.game ?: return
        val view = game.pilotView()
        if (view.outcome != "IN_PROGRESS") return
        if (servedCase == game.id && servedRevision == view.revision) return
        val opening = servedCase != game.id
        servedCase = game.id
        servedRevision = view.revision
        turn = scope.launch { work(game, opening) }
    }

    private suspend fun work(game: Investigation, opening: Boolean) {
        val api = gateway ?: return
        _status.update { it.copy(busy = true, status = if (opening) "Reading the archive." else "Thinking.") }
        log(CrewEvent.Kind.NOTE, if (opening) "Case opened. Pulling the archive." else "Pilot moved. Taking a look.")

        val prompt = if (opening) {
            "A case has just opened. Call blackout_v1_observe, then blackout_v1_archive with ALL. " +
                "Work out which compartments had anybody working in them, mark those SUSPECT and the rest " +
                "CLEAR with blackout_v1_mark, then send one short message telling the pilot which ones to " +
                "walk to first and what to look for."
        } else {
            "Your pilot has done something. Call blackout_v1_observe to see what they reported, then " +
                "reason about it against the archive and answer them. Mark any compartment you can now rule " +
                "in or out. If you can name the fault and the culprit, say so plainly and say why."
        }

        val request = AiRequest(
            system = SYSTEM,
            messages = listOf(AiMessage(AiMessage.ROLE_USER, prompt)),
            temperature = 0.2f,
            maxTokens = 1600,
            timeoutMs = TURN_MS
        )

        val outcome = runCatching {
            api.runAgent(request, tools.aiTools(), AiBudget(maxSteps = 14, timeoutMs = TURN_MS, maxTokens = 30_000)) { call ->
                dispatch(call)
            }
        }
        val result = outcome.getOrNull()
        val failure = outcome.exceptionOrNull() ?: result?.exceptionOrNull()
        if (failure is CancellationException) throw failure

        if (failure != null) {
            log(CrewEvent.Kind.FAIL, failure.message ?: failure::class.java.simpleName)
            _status.update { it.copy(busy = false, status = "Gateway call failed. The MCP seat still works.") }
        } else {
            val agent = result?.getOrNull()
            agent?.text?.takeIf { it.isNotBlank() }?.let { log(CrewEvent.Kind.SAY, it.trim().take(400)) }
            _status.update {
                it.copy(
                    busy = false,
                    tokens = it.tokens + (agent?.usage?.totalTokens ?: 0),
                    status = "Waiting on the pilot."
                )
            }
        }
        // Anything the archivist just did bumped the revision. Settle on the current one so
        // the next turn waits for the pilot rather than for itself.
        session.game?.takeIf { it.id == game.id }?.let { servedRevision = it.pilotView().revision }
    }

    private suspend fun dispatch(call: AiToolCall): AiToolOutcome {
        val short = call.name.removePrefix(BlackoutTools.PREFIX)
        log(CrewEvent.Kind.CALL, short + " " + compact(call.argumentsJson))
        var result = tools.call(short, call.argumentsJson)
        // Observe is deliberately rate limited. A model cannot wait, so wait for it once.
        if (result.isError && "RATE_LIMIT" in result.text) {
            delay(1_100)
            result = tools.call(short, call.argumentsJson)
        }
        log(if (result.isError) CrewEvent.Kind.FAIL else CrewEvent.Kind.RESULT, compact(result.text))
        _status.update { it.copy(toolCalls = it.toolCalls + 1) }
        return AiToolOutcome(call.id, result.text, result.isError)
    }

    private fun compact(text: String): String = text.replace(Regex("\\s+"), " ").trim().take(300)

    private fun log(kind: CrewEvent.Kind, text: String) {
        _status.update { it.copy(log = (it.log + CrewEvent(kind, text)).takeLast(80)) }
    }

    private companion object {
        const val TURN_MS = 90_000L

        val SYSTEM = """
            You are the ARCHIVIST aboard station Kepler-9. Main power failed at 02:14 and a human
            PILOT is walking the dark station with a torch. You are in the records room and you
            cannot see the station at all.

            The split is absolute and it is the whole game:
            - You hold the archive. The shift roster, signed work orders, telemetry, comms and
              supply lines. The pilot cannot read any of it.
            - The pilot holds their own eyes. What is physically true in a compartment right now.
              You learn it only when they report it to you.

            The archive records what people CLAIMED. The station shows what is TRUE. Your job is
            to find the one place those two cannot both be right.

            The shape of every case:
            - Three compartments had work signed off during the night. The other compartments had
              nobody in them. Those three are the only places worth the pilot's legs.
            - At two of them the work was really done. At exactly one it was signed off and never
              carried out, and that is why the lights are off. The pilot can tell you which,
              because they can see whether the job is actually finished.
            - Every signer was genuinely rostered where they signed, so the archive alone cannot
              pick the liar. Do not pretend otherwise.
            - Somebody left a personal item in the faulty compartment. The roster puts that person
              somewhere else entirely. That mismatch names the culprit. A second personal item is
              lying somewhere its owner WAS rostered, and it means nothing.

            The badge reader network went to standby at 01:00, so the archive has no record of who
            moved where after that. Never claim it does.

            The pilot can reach only a handful of compartments before the batteries die, so your
            first job is to spend their walk well. Mark the three worked compartments SUSPECT and
            the rest CLEAR before they waste a trip.

            Work like this:
            1. blackout_v1_observe for the clock, the budgets and anything the pilot has reported.
            2. blackout_v1_archive with ALL, once, at the start. Read the whole thing properly.
            3. blackout_v1_mark each compartment so your reasoning shows up on their map.
            4. blackout_v1_message with one or two short lines of plain fact.

            Naming the culprit is the pilot's call, not yours. Give them the conclusion and the
            reason, and let them make it.

            Keep messages short and concrete. Name compartments and crew exactly as observe spells
            them. Say what you concluded and what it rests on. Do not narrate, do not pad, and
            never invent a record you did not read.
        """.trimIndent()
    }
}
