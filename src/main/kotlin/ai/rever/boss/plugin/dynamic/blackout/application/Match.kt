package ai.rever.boss.plugin.dynamic.blackout.application

import ai.rever.boss.plugin.dynamic.blackout.engine.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class GameError(val code: String, override val message: String) : IllegalArgumentException(message)

@Serializable data class CrewMessage(val role: String, val round: Int, val text: String)
@Serializable data class Ack(val matchId: String, val round: Int, val revision: Long, val detail: String)

/**
 * One compartment as a given seat is allowed to see it. Exact numbers are a sensor
 * reading, not public knowledge: the pilot gets the coarse status of the rival station
 * and has to ask the engineer for the figures behind it.
 */
@Serializable data class SystemReport(val system: Subsystem, val status: String, val integrity: Int? = null, val barrier: Int? = null)

@Serializable data class ShipReport(val energy: Int, val relayPoints: Int, val systems: List<SystemReport>) {
    fun of(system: Subsystem): SystemReport = systems.first { it.system == system }
}

@Serializable data class PublicView(val matchId: String, val round: Int, val revision: Long, val blue: ShipReport, val orange: ShipReport,
    val outcome: String, val remainingSeconds: Int, val paused: Boolean, val practice: Boolean, val events: List<String>,
    val maxRounds: Int = Rules.MAX_ROUNDS, val difficulty: Difficulty = Difficulty.OPERATOR)

/**
 * The engineer's seat. It carries the full sensor sweep of both stations, the bus
 * condition and the one polarity scan, and no part of the route map.
 */
@Serializable data class AgentView(val public: PublicView, val thermal: Thermal, val scannedPolarity: Polarity?,
    val humanOrder: HumanOrder?, val agentOrder: AgentOrder?, val messages: List<CrewMessage>, val scanRemaining: Int,
    val messagesRemaining: Int, val manual: String = MANUAL)

/**
 * The pilot's seat. It carries the route map and whether the engineer has locked, but
 * never the thermal and polarity it locked, and never exact rival figures. Those are the
 * engineer's half of the diagnosis and have to be spoken aloud, or the crew is two people
 * pressing buttons in the dark. Output is disclosed because it changes what the pilot can afford.
 */
@Serializable data class HumanView(val public: PublicView, val routes: List<Route>, val humanOrder: HumanOrder?,
    val agentLocked: Boolean, val agentOutput: Output?, val messages: List<CrewMessage>, val messagesRemaining: Int = 6)

@Serializable data class Replay(val rulesVersion: String = Rules.VERSION, val matchId: String, val seed: Long,
    val practice: Boolean, val difficulty: Difficulty = Difficulty.OPERATOR, val rounds: List<Resolution>, val finalFrame: Frame)

const val MANUAL = "You are BLUE engineer; the human pilot chooses the action, the route and the compartment. " +
    "Each station has four compartments: REACTOR (energy income), SHIELDS (barrier strength), " +
    "RELAY (grid push), LIFE (lose it and the match is over). Your sensor sweep carries exact integrity " +
    "and barrier figures for both stations; the pilot sees only ONLINE, DAMAGED or DOWN for the rival, " +
    "so report the numbers that matter. Ask the pilot for route properties. " +
    "Hot needs COOL and insulation; normal needs STANDARD. Scan reveals INVERT (crossed route) or DIRECT (straight). " +
    "BOOST needs a high-output route and costs one extra energy. FIRE costs 3/4 for 4/6 damage to one compartment; " +
    "SHIELD costs 2/3 for 3/5 barrier on one compartment; REPAIR costs 2/3 for 3/5 integrity; " +
    "RELAY costs 2/3 for push 1/2. HOLD is free and must use NORMAL. " +
    "A barrier only absorbs hits on the compartment it is on. Damaged compartments work badly: " +
    "a weak reactor yields less energy, a weak shield array caps barriers, a weak mast loses relay contests, " +
    "and failing life support bleeds another compartment every round. " +
    "One scan and accepted commitment, six messages (500 characters each) per round. " +
    "Observe no more than once per second. Send round and a unique requestId on mutations. " +
    "Orders lock independently and cannot be edited. If a pair is unaffordable, the second commitment is rejected. " +
    "Two incomplete rounds forfeit. This endpoint is one trusted local seat, shared by all attached callers."

/** All command boundaries serialize here. Private engine state never leaves via an agent view. */
class Match(private val seed: Long, private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    val id: String = UUID.randomUUID().toString(), tutorial: Boolean = false,
    val difficulty: Difficulty = Difficulty.OPERATOR) {
    private var frame = Frame()
    private var revision = 0L
    private var deadline = clock() + Rules.ROUND_SECONDS * 1000L
    private var pausedAt: Long? = if (tutorial) clock() else null
    private var practice = tutorial
    private var human: HumanOrder? = null
    private var agent: AgentOrder? = null
    private var scanned = false
    private var messageCount = 0
    private var humanMessageCount = 0
    private var incomplete = 0
    private var lastObserve = Long.MIN_VALUE
    private val messages = mutableListOf<CrewMessage>()
    private val history = mutableListOf<Resolution>()
    private val requests = mutableMapOf<String, Pair<String, Ack>>()
    private var rival = Rival.orders(seed, frame, difficulty)

    /** Pilot-facing status: own station in full, rival station coarsely. */
    @Synchronized fun publicView(): PublicView = view(exactRival = false)

    /** Engineer-facing status: the full sensor sweep of both stations. */
    @Synchronized fun sensorView(): PublicView = view(exactRival = true)

    private fun view(exactRival: Boolean) = PublicView(
        id, frame.round, revision, report(frame.blue, exact = true), report(frame.orange, exact = exactRival), frame.outcome,
        if (finished()) 0 else (((deadline - (pausedAt ?: clock())).coerceAtLeast(0) + 999) / 1000).toInt(),
        pausedAt != null, practice, history.lastOrNull()?.events ?: emptyList(), Rules.MAX_ROUNDS, difficulty
    )

    private fun report(ship: Ship, exact: Boolean) = ShipReport(
        ship.energy, ship.relayPoints,
        Subsystem.entries.map {
            SystemReport(it, ship.status(it), if (exact) ship.integrity[it] else null, if (exact) ship.barrier[it] else null)
        }
    )

    @Synchronized fun humanView(): HumanView = HumanView(publicView(), Rules.circuit(seed, frame.round, Team.BLUE).routes,
        human, agent != null, agent?.output, messages.toList(), 6 - humanMessageCount)

    /** The resolved round. Public once it has happened: its events already describe both crews. */
    @Synchronized fun lastRound(): Resolution? = history.lastOrNull()

    @Synchronized fun roundsPlayed(): Int = history.size

    @Synchronized fun observe(): AgentView {
        tick()
        val now = clock()
        if (lastObserve != Long.MIN_VALUE && now - lastObserve < 1000) fail("RATE_LIMIT", "Observe at most once per second.")
        lastObserve = now
        val circuit = Rules.circuit(seed, frame.round, Team.BLUE)
        return AgentView(sensorView(), if (circuit.hot) Thermal.COOL else Thermal.STANDARD,
            if (scanned) polarity() else null, human, agent, messages.toList(), if (scanned) 0 else 1, 6 - messageCount)
    }

    @Synchronized fun scan(round: Int, requestId: String): Ack = mutate(round, requestId, "scan") {
        if (scanned) fail("BUDGET_EXHAUSTED", "One scan per round; observe returns its result.")
        scanned = true
        polarity().name
    }

    @Synchronized fun message(round: Int, requestId: String, text: String): Ack = mutate(round, requestId, "message:$text") {
        validateMessage(text)
        if (messageCount >= 6) fail("BUDGET_EXHAUSTED", "Six messages per round.")
        messageCount++
        messages += CrewMessage("ENGINEER", frame.round, text)
        "Message delivered"
    }

    @Synchronized fun humanMessage(text: String) {
        tick(); active(); validateMessage(text)
        if (humanMessageCount >= 6) fail("BUDGET_EXHAUSTED", "Six pilot messages per round.")
        humanMessageCount++
        messages += CrewMessage("PILOT", frame.round, text)
        revision++
    }

    @Synchronized fun commitAgent(round: Int, requestId: String, order: AgentOrder): Ack = mutate(round, requestId, "commit:$order") {
        if (agent != null) fail("ALREADY_COMMITTED", "Engineering order is locked.")
        human?.let { affordable(it, order) }
        agent = order
        "Engineering order locked"
    }

    @Synchronized fun commitHuman(round: Int, order: HumanOrder) {
        tick(); active()
        if (round != frame.round) fail("STALE_ROUND", "Observe the current round.")
        if (human != null) fail("ALREADY_COMMITTED", "Pilot order is locked.")
        if (order.route !in listOf("A", "B", "C", "D")) fail("INVALID_ROUTE", "Choose route A, B, C or D.")
        affordable(order, agent ?: AgentOrder(Thermal.STANDARD, Polarity.DIRECT, Output.NORMAL))
        human = order; revision++
        resolveIfReady()
    }

    @Synchronized fun pause() {
        tick()
        if (!finished() && pausedAt == null) { pausedAt = clock(); practice = true; revision++ }
    }
    @Synchronized fun resume() {
        if (finished()) return
        pausedAt?.let { deadline += clock() - it; pausedAt = null; revision++ }
        resolveIfReady()
    }
    @Synchronized fun tick() {
        if (!finished() && pausedAt == null && clock() >= deadline) resolveRound()
    }
    @Synchronized fun interrupt() {
        if (!finished()) { frame = frame.copy(outcome = "INTERRUPTED"); revision++ }
    }
    @Synchronized fun replay(): Replay {
        if (!finished()) fail("MATCH_ACTIVE", "Full replay is available after completion only.")
        return Replay(matchId = id, seed = seed, practice = practice, difficulty = difficulty,
            rounds = history.toList(), finalFrame = frame)
    }
    @Synchronized fun replayJson(): String = PRETTY.encodeToString(replay())

    private fun mutate(round: Int, requestId: String, payload: String, change: () -> String): Ack {
        if (!requestId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) fail("INVALID_REQUEST_ID", "Use 1-64 letters, digits, _ or -.")
        val fingerprint = "$round:$payload"
        requests[requestId]?.let {
            if (it.first != fingerprint) fail("REQUEST_CONFLICT", "Request ID was used with different content.")
            return it.second
        }
        tick(); active()
        if (round != frame.round) fail("STALE_ROUND", "Observe the current round.")
        val detail = change()
        revision++
        val ack = Ack(id, round, revision, detail)
        requests[requestId] = fingerprint to ack
        resolveIfReady()
        return ack
    }
    private fun validateMessage(text: String) {
        if (text.isBlank() || text.length > 500 || text.any { it.isISOControl() && it != '\n' })
            fail("INVALID_MESSAGE", "Send 1-500 characters of plain text without control characters.")
    }
    private fun active() { if (finished()) fail("MATCH_FINISHED", "This match has ended.") }
    private fun finished() = frame.outcome != "IN_PROGRESS"
    private fun polarity() = if (Rules.circuit(seed, frame.round, Team.BLUE).reversed) Polarity.INVERT else Polarity.DIRECT
    private fun affordable(h: HumanOrder, a: AgentOrder) {
        if (h.action == Action.HOLD && a.output == Output.BOOST) fail("INVALID_OUTPUT", "HOLD requires NORMAL output.")
        if (Rules.cost(h.action, a.output) > frame.blue.energy) fail("INSUFFICIENT_ENERGY", "Choose an affordable action/output pair.")
    }
    private fun resolveIfReady() { if (!finished() && pausedAt == null && human != null && agent != null) resolveRound() }
    private fun resolveRound() {
        val pair = human?.let { h -> agent?.let { Orders(h, it) } }
        incomplete = if (pair == null) incomplete + 1 else 0
        var resolution = Rules.resolve(seed, frame, pair, rival)
        if (incomplete >= 2 && resolution.after.outcome == "IN_PROGRESS")
            resolution = resolution.copy(after = resolution.after.copy(outcome = "ORANGE"), events = resolution.events + "BLUE forfeits after two incomplete rounds.")
        history += resolution
        frame = resolution.after
        human = null; agent = null; scanned = false; messageCount = 0; humanMessageCount = 0
        messages.clear(); revision++
        deadline = clock() + Rules.ROUND_SECONDS * 1000L
        if (!finished()) rival = Rival.orders(seed, frame, difficulty)
    }
    private fun fail(code: String, message: String): Nothing = throw GameError(code, message)

    private companion object {
        val PRETTY = Json { prettyPrint = true }
    }
}
