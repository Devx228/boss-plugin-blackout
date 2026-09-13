package ai.rever.boss.plugin.dynamic.blackout.application

import ai.rever.boss.plugin.dynamic.blackout.engine.*
import kotlinx.serialization.Serializable
import java.util.UUID

class GameError(val code: String, override val message: String) : IllegalArgumentException(message)
@Serializable data class Note(val role: String, val text: String)
@Serializable data class Finding(val compartment: String, val text: String)

@Serializable enum class EscapeStage { POWER, CABINET, STORY, EXIT }
@Serializable enum class PowerMode { LOW, NORMAL, HIGH }
@Serializable enum class Arithmetic { ADD, SUBTRACT, MULTIPLY, DIVIDE }
@Serializable data class RoomObject(val id: String, val label: String, val description: String, val available: Boolean)
@Serializable data class EscapePublic(val roomId: String, val revision: Long, val stage: EscapeStage, val objective: String,
    val secondsLeft: Int, val outcome: String, val paused: Boolean, val practice: Boolean, val armSeconds: Int,
    val mistakes: Int, val hintsUsed: Int, val remotePower: PowerMode?)
@Serializable data class EscapePilotView(val status: EscapePublic, val objects: List<RoomObject>, val symbols: List<String>,
    val fragments: List<StoryFragment>, val notes: List<Note>, val inventory: List<String>, val feedback: String,
    val ending: String?)
@Serializable data class EscapeAgentView(val status: EscapePublic, val brief: String, val reported: List<Finding>,
    val notes: List<Note>, val queriesLeft: Int, val messagesLeft: Int, val armsLeft: Int, val routesLeft: Int, val calculationsLeft: Int)
@Serializable data class EscapeReceipt(val roomId: String, val revision: Long, val detail: String)
@Serializable data class EscapeEvent(val elapsedSeconds: Int, val stage: EscapeStage, val detail: String)
@Serializable data class EscapeDebrief(val rulesVersion: String = EscapeRoom.RULES_VERSION, val roomId: String, val seed: Long,
    val outcome: String, val secondsUsed: Int, val mistakes: Int, val hintsUsed: Int, val practice: Boolean,
    val events: List<EscapeEvent>)

/** A trusted local room. Only human methods inspect objects and enter answers. */
class Escape(seed: Long, private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    val id: String = UUID.randomUUID().toString()) {
    private val scenario = EscapeRoom.generate(seed)
    private var stage = EscapeStage.POWER
    private var outcome = "IN_PROGRESS"
    private var revision = 0L
    private var pilotRevision = 0L
    private val startedAt = clock()
    private var deadline = startedAt + EscapeRoom.SECONDS * 1000L
    private var pausedAt: Long? = null
    private var pausedMs = 0L
    private var elapsedFinal: Int? = null
    private var remainingFinal: Int? = null
    private var practice = false
    private var armedUntil = 0L
    private var mistakes = 0
    private var hints = 0
    private var queries = 0
    private var agentMessages = 0
    private var arms = 0
    private var routes = 0
    private var calculations = 0
    private var remotePower: PowerMode? = null
    private var lastObserve: Long? = null
    private var feedback = "Start with the breaker panel. Your companion has its startup manual."
    private val inspected = linkedSetOf<String>()
    private val reported = mutableListOf<Finding>()
    private val notes = mutableListOf<Note>()
    private val events = mutableListOf<EscapeEvent>()
    private val requests = mutableMapOf<String, Pair<String, EscapeReceipt>>()

    @Synchronized fun status(): EscapePublic {
        tick()
        return EscapePublic(id, revision, stage, when(stage) {
            EscapeStage.POWER -> "Restore the emergency circuit"
            EscapeStage.CABINET -> "Decode the cabinet password"
            EscapeStage.STORY -> "Put the last shift back together"
            EscapeStage.EXIT -> "Release the door together"
        }, remaining(), outcome, pausedAt != null, practice, if (finished()) 0 else ((armedUntil - now()).coerceAtLeast(0) / 1000).toInt(), mistakes, hints, remotePower)
    }
    @Synchronized fun pilotView(): EscapePilotView {
        val status = status()
        return EscapePilotView(status, objectList(), if ("panel" in inspected) scenario.symbols else emptyList(),
            if (stage >= EscapeStage.STORY && "recorder" in inspected) scenario.fragments else emptyList(), notes.toList(),
            buildList { if (stage >= EscapeStage.CABINET) add("Emergency power"); if (stage >= EscapeStage.STORY) add("Three memory strips"); if (stage == EscapeStage.EXIT) add("Manual release key") },
            feedback, if (!finished()) null else if (outcome == "ESCAPED")
                "The door gives. Clean air. Your companion answers from the portable terminal. " +
                    "Mara did not trap you: she stopped a fire. Ivo sealed the room to protect you. They left a way out for two minds working together."
            else if (outcome == "FAILED") "The emergency reserve runs out. The room goes quiet. Your recovered log is saved; try again with a new circuit."
            else "Run ended. No escape result recorded.")
    }
    @Synchronized fun inspect(objectId: String) {
        active()
        val obj = objectList().find { it.id == objectId } ?: fail("UNKNOWN_OBJECT", "Choose an object in the room.")
        if (!obj.available) fail("LOCKED", "Restore the preceding system first.")
        if (inspected.add(objectId)) { revision++; event("Inspected ${obj.label}") }
    }
    @Synchronized fun report(objectId: String) {
        active()
        if (objectId !in inspected) fail("NOT_INSPECTED", "Inspect it before sharing the clue.")
        val obj = objectList().first { it.id == objectId }
        if (reported.none { it.compartment == obj.label && it.text == obj.description }) {
            reported += Finding(obj.label, obj.description)
            appendNote("YOU", obj.description)
            changedByPilot()
        }
    }
    @Synchronized fun say(text: String) {
        active(); validate(text)
        appendNote("YOU", text.trim()); changedByPilot()
    }
    @Synchronized fun submitPower(order: List<String>): Boolean {
        active(); requireStage(EscapeStage.POWER)
        if ("panel" !in inspected) fail("NOT_INSPECTED", "Inspect the breaker panel first.")
        if (order.size != 3 || order.toSet() != scenario.symbols.toSet()) fail("INVALID_ANSWER", "Use each of the three panel symbols once.")
        if (remotePower == null) fail("REMOTE_POWER_REQUIRED", "Your companion must configure the remote supply. Share the panel's load and voltage.")
        val required = when { scenario.drawAmps <= 6 -> PowerMode.LOW; scenario.drawAmps <= 12 -> PowerMode.NORMAL; else -> PowerMode.HIGH }
        if (remotePower != required) return wrong("The supply trips under load. Ask your companion to check its power setting against the panel's current draw. −15 seconds.")
        if (order != scenario.startupOrder) return wrong("The breaker trips. That startup order was wrong. −15 seconds.")
        stage = EscapeStage.CABINET
        solved("The lights settle. A coded label appears on the cabinet. The exit is still sealed.")
        return true
    }
    @Synchronized fun submitPassword(answer: String): Boolean {
        active(); requireStage(EscapeStage.CABINET)
        if ("cabinet" !in inspected) fail("NOT_INSPECTED", "Inspect the cabinet first.")
        if (!answer.matches(Regex("[A-Za-z]{1,12}"))) fail("INVALID_ANSWER", "Enter a word of 1–12 letters.")
        if (!answer.equals(scenario.password, true)) return wrong("The keypad flashes once. Wrong password. −15 seconds.")
        stage = EscapeStage.STORY
        solved("The cabinet opens. Inside: a recorder, three memory strips, and a release key locked in a cradle.")
        return true
    }
    @Synchronized fun submitStory(order: List<String>): Boolean {
        active(); requireStage(EscapeStage.STORY)
        if ("recorder" !in inspected) fail("NOT_INSPECTED", "Read the memory strips first.")
        if (order.size != 3 || order.toSet() != setOf("A", "B", "C")) fail("INVALID_ANSWER", "Use strips A, B and C once each.")
        if (order != scenario.storyOrder) return wrong("The recorder cannot reconcile that sequence. −15 seconds.")
        stage = EscapeStage.EXIT
        solved("The log resolves: shutdown, shelter, rescue. The key releases. Ask your companion to arm the door, then turn the handle.")
        return true
    }
    @Synchronized fun escape(): Boolean {
        active(); requireStage(EscapeStage.EXIT)
        if (pausedAt != null) fail("PAUSED", "Resume the clock before the final release.")
        if (clock() >= armedUntil) fail("NOT_ARMED", "Ask your companion to arm the release. You have 20 seconds to turn the handle.")
        remainingFinal = remaining(); outcome = "ESCAPED"; elapsedFinal = elapsed(); feedback = "The door opens. You made it out together."
        changedByPilot(); event("Door opened by human while companion release was armed")
        return true
    }
    @Synchronized fun hint() {
        active()
        if (hints >= 3) fail("NO_HINTS", "All three hints have been used.")
        hints++; deadline -= 20_000
        feedback = when(stage) {
            EscapeStage.POWER -> "Share the symbols, load in watts and voltage. Your companion can calculate current = watts ÷ volts, route the right power mode, and tell you the ascending startup priorities."
            EscapeStage.CABINET -> "Share the letters on the cabinet. Ask for the archive's cipher offset. Shift letters backward, wrapping A to Z."
            EscapeStage.STORY -> "Look for cause and effect: the alarm comes before the shutdown's consequences. The knocking is last."
            EscapeStage.EXIT -> "Share the door's routing seal. Your companion must choose its channel in blackout_v2_arm, then you turn the handle within 20 seconds."
        }
        changedByPilot(); event("Hint requested (20-second penalty)"); tick()
    }
    @Synchronized fun observe(): EscapeAgentView {
        tick()
        val time = clock()
        lastObserve?.let { if (time - it < 1000) fail("RATE_LIMIT", "Observe at most once per second.") }
        lastObserve = time
        return EscapeAgentView(status(), "You are the companion on the emergency terminal. You cannot see the room. " +
            "Read the archive, ask the human to inspect and share clues, then help them solve each lock. " +
            "Only the human operates breakers, enters passwords, arranges strips and turns the door handle. " +
            "Only you can calculate with the terminal, route remote power and authorize the correct exit channel. Ask for panel load/voltage and later the door seal. " +
            "After the story is reconstructed you can arm the exit for 20 seconds. Never invent physical clues. " +
            "Use roomId from status on every call except observe. Tools are one trusted local seat, not separate agent identities.",
            reported.toList(), notes.toList(), 12 - queries, 30 - agentMessages, 12 - arms, 8-routes, 16-calculations)
    }
    @Synchronized fun archive(query: String): List<String> {
        active()
        if (query.isBlank() || query.length > 120) fail("INVALID_QUERY", "Use 1–120 characters.")
        if (queries >= 12) fail("BUDGET_EXHAUSTED", "Archive query budget exhausted. Keep your earlier records.")
        queries++; revision++
        // Include all possible glyphs: archive alone cannot tell which are on the actual panel.
        val priorities = scenario.manualOrder
            .mapIndexed { i, symbol -> "$symbol = ${i + 1}" }.sorted()
        val records = listOf(
            "POWER MANUAL: Only three of the six symbols are fitted. Ask the human which. Energize the fitted symbols in ascending priority. " + priorities.joinToString("; ") +
                ". Before they energize, calculate current in amps = load watts DIVIDE supply volts, using the human's panel readings. Use the calculate tool, then route: 1–6 A needs LOW, 7–12 A NORMAL, 13–18 A HIGH. Neither reading is recorded here.",
            "CABINET MANUAL: The label was Caesar-encoded by shifting each letter forward ${scenario.shift}. Decode by moving each letter BACKWARD ${scenario.shift}, wrapping A to Z. The decoded word opens the cabinet.",
            "RECORDER INDEX: Reconstruct causes, not speaker names: first the deliberate safety shutdown; then the shelter response to losing power; finally the rescue plan after somebody was heard inside. Strip labels are local; ask the human to report their text.",
            "EXIT MANUAL: Ask for the seal displayed on the door after the log is reconstructed. Select its channel: " +
                scenario.channelOrder.mapIndexed { i, symbol -> "$symbol = ${'A'+i}" }.joinToString("; ") +
                ". Call arm with that channel. Wrong channel costs 15 seconds. The human must turn the handle within 20 seconds. Re-arm if it expires; arming never opens the door by itself.",
            "SHIFT RECORD: Mara was responsible for fire prevention. Ivo was responsible for containment. At 02:14 main power was deliberately cut; the evacuation report is incomplete."
        )
        if (query.trim().equals("ALL", true)) return records
        return records.filter { it.contains(query.trim(), true) }
    }
    @Synchronized fun message(requestId: String, text: String): EscapeReceipt = mutate(requestId, "message:$text") {
        validate(text)
        if (agentMessages >= 30) fail("BUDGET_EXHAUSTED", "Thirty companion messages per room.")
        agentMessages++; appendNote("COMPANION", text.trim()); "Message delivered"
    }
    @Synchronized fun calculate(requestId: String, operation: Arithmetic, a: Int, b: Int): EscapeReceipt =
        mutate(requestId, "calculate:$operation:$a:$b") {
            if (a !in -1_000_000..1_000_000 || b !in -1_000_000..1_000_000) fail("INVALID_NUMBER", "Operands must be integers between -1000000 and 1000000.")
            if (operation == Arithmetic.DIVIDE && b == 0) fail("DIVIDE_BY_ZERO", "The divisor must not be zero.")
            if (calculations >= 16) fail("BUDGET_EXHAUSTED", "Sixteen calculations per room.")
            val result = when(operation) {
                Arithmetic.ADD -> (a.toLong()+b).toString()
                Arithmetic.SUBTRACT -> (a.toLong()-b).toString()
                Arithmetic.MULTIPLY -> (a.toLong()*b).toString()
                Arithmetic.DIVIDE -> (a.toDouble()/b).toString()
            }
            calculations++
            "$a ${operation.name} $b = $result"
        }
    @Synchronized fun route(requestId: String, mode: PowerMode): EscapeReceipt = mutate(requestId, "route:$mode") {
        requireStage(EscapeStage.POWER)
        if (routes >= 8) fail("BUDGET_EXHAUSTED", "Eight remote power configurations per room.")
        routes++; remotePower = mode
        appendNote("COMPANION", "Remote supply configured: $mode. Ready for your breaker sequence.")
        event("Companion configured remote power: $mode")
        "Remote supply configured: $mode. Human must energize breakers; setting is not verified until then."
    }
    @Synchronized fun arm(requestId: String, channel: String): EscapeReceipt = mutate(requestId, "arm:$channel") {
        requireStage(EscapeStage.EXIT)
        if (pausedAt != null) fail("PAUSED", "Ask the human to resume before arming.")
        if (channel !in listOf("A","B","C","D","E","F")) fail("INVALID_CHANNEL", "Choose channel A–F from the exit manual.")
        if (arms >= 12) fail("BUDGET_EXHAUSTED", "Release authorization budget exhausted.")
        arms++
        if (channel != ('A' + scenario.channelOrder.indexOf(scenario.releaseSeal)).toString()) {
            armedUntil = 0
            wrong("Release channel rejected. Share the door seal and check the exit manual. −15 seconds.")
            "Channel rejected. 15-second penalty applied. Door remains locked."
        } else {
        armedUntil = clock() + 20_000
        appendNote("COMPANION", "Release armed. Turn the handle now — 20 seconds.")
        event("Companion armed exit for 20 seconds")
        "Release armed for 20 seconds. Human must turn the handle."
        }
    }
    @Synchronized fun pause() {
        tick()
        if (!finished() && pausedAt == null) { pausedAt = clock(); practice = true; changedByPilot() }
    }
    @Synchronized fun resume() {
        if (finished()) return
        pausedAt?.let {
            val interval = clock() - it
            deadline += interval; if (armedUntil > it) armedUntil += interval
            pausedMs += interval; pausedAt = null; changedByPilot()
        }
    }
    @Synchronized fun tick() {
        if (!finished() && pausedAt == null && clock() >= deadline) {
            outcome = "FAILED"; elapsedFinal = elapsed(); feedback = "Emergency air reserve depleted."
            revision++; event("Air reserve expired")
        }
    }
    @Synchronized fun interrupt() { if (!finished()) { remainingFinal = remaining(); outcome = "INTERRUPTED"; elapsedFinal = elapsed(); revision++; event("Run interrupted") } }
    @Synchronized fun finished() = outcome != "IN_PROGRESS"
    @Synchronized fun pilotSignal(): Long = pilotRevision
    @Synchronized fun debrief(): EscapeDebrief {
        if (!finished()) fail("ROOM_ACTIVE", "Debrief is available only after the run.")
        return EscapeDebrief(roomId = id, seed = scenario.seed, outcome = outcome, secondsUsed = elapsedFinal ?: elapsed(),
            mistakes = mistakes, hintsUsed = hints, practice = practice, events = events.toList())
    }
    private fun objectList(): List<RoomObject> = listOf(
        RoomObject("panel", "Breaker panel", if ("panel" in inspected) "Three breaker symbols: ${scenario.symbols.joinToString(", ")}. Load: ${scenario.drawAmps*scenario.supplyVolts} W. Supply: ${scenario.supplyVolts} V. A plate says: START LOW. One switch at a time." else "Three cold switches beside the door.", true),
        RoomObject("cabinet", "Locked cabinet", if (stage >= EscapeStage.CABINET && "cabinet" in inspected) "The lit label reads ${scenario.cipher}. The keypad accepts a word." else "A steel cabinet with an unpowered keypad.", stage >= EscapeStage.CABINET),
        RoomObject("recorder", "Memory recorder", if (stage >= EscapeStage.STORY && "recorder" in inspected) scenario.fragments.joinToString("\n") { "${it.id}: ${it.text}" } else "Something rattles inside the locked cabinet.", stage >= EscapeStage.STORY),
        RoomObject("door", "Exit door", if (stage == EscapeStage.EXIT && "door" in inspected) "The key is ready. Routing seal: ${scenario.releaseSeal}. A release indicator waits for your companion. Two seats, two actions." else "A heavy door. Beside the handle: MANUAL RELEASE — POWER, LOG, TWO-PART AUTHORIZATION.", true)
    )
    private fun mutate(id: String, payload: String, action: () -> String): EscapeReceipt {
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) fail("INVALID_REQUEST_ID", "Use 1–64 letters, digits, _ or -.")
        requests[id]?.let { if (it.first != payload) fail("REQUEST_CONFLICT", "Request ID already used with different content."); return it.second }
        active()
        val detail = action(); revision++
        return EscapeReceipt(this.id, revision, detail).also { requests[id] = payload to it }
    }
    private fun wrong(message: String): Boolean {
        mistakes++; deadline -= 15_000; feedback = message; changedByPilot(); event("Wrong answer (15-second penalty)"); tick(); return false
    }
    private fun solved(message: String) { feedback = message; changedByPilot(); event(message) }
    private fun changedByPilot() { revision++; pilotRevision++ }
    private fun appendNote(role: String, text: String) { notes += Note(role, text); if (notes.size > 80) notes.removeAt(0) }
    private fun requireStage(required: EscapeStage) { if (stage != required) fail("WRONG_STAGE", "Current objective: ${stage.name.lowercase()}.") }
    private fun active() { tick(); if (finished()) fail("ROOM_CLOSED", "The run is over.") }
    private fun validate(text: String) { if (text.isBlank() || text.length > 500 || text.any { it.isISOControl() && it != '\n' }) fail("INVALID_TEXT", "Use 1–500 plain-text characters.") }
    private fun now() = pausedAt ?: clock()
    private fun remaining() = remainingFinal ?: if (outcome == "FAILED") 0 else ((deadline - now()).coerceAtLeast(0) / 1000).toInt()
    private fun elapsed() = ((now() - startedAt - pausedMs).coerceAtLeast(0) / 1000).toInt()
    private fun event(detail: String) { events += EscapeEvent(elapsedFinal ?: elapsed(), stage, detail) }
    private fun fail(code: String, message: String): Nothing = throw GameError(code, message)
}
