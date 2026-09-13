package ai.rever.boss.plugin.dynamic.blackout.application

import ai.rever.boss.plugin.dynamic.blackout.engine.*
import kotlinx.serialization.Serializable
import java.util.UUID

class GameError(val code: String, override val message: String) : IllegalArgumentException(message)

@Serializable data class Note(val role: String, val text: String)
/** Something the companion did through a tool, shown to the human. Never carries manual contents. */
@Serializable data class Activity(val seq: Long, val kind: String, val title: String, val detail: String, val ok: Boolean)
/** One entry of the human-facing conversation: either a message or a companion action. */
@Serializable data class ChatEntry(val seq: Long, val note: Note? = null, val activity: Activity? = null)
@Serializable data class Finding(val compartment: String, val text: String)
@Serializable enum class EscapeStage { POWER, CABINET, STORY, EXIT }
@Serializable enum class PowerMode { LOW, NORMAL, HIGH }
@Serializable enum class Arithmetic { ADD, SUBTRACT, MULTIPLY, DIVIDE }
@Serializable enum class LightingMode { EMERGENCY, WORK, ULTRAVIOLET }
@Serializable enum class VentilationMode { INTAKE, EXHAUST, HOLD }

@Serializable enum class RoomMode(val seconds: Int, val mistakePenalty: Int, val hintPenalty: Int) {
    STANDARD(600, 15, 20),
    SHOWCASE(240, 10, 10)
}

@Serializable data class CompanionDescriptor(
    val kind: String = "NONE",
    val provider: String = "",
    val model: String = "",
    val verified: Boolean = false
)

@Serializable data class RemoteSystemState(
    val power: PowerMode? = null,
    val decoderShift: Int? = null,
    val recorderChannel: String? = null,
    val lighting: LightingMode = LightingMode.EMERGENCY,
    val ventilation: VentilationMode? = null
)

@Serializable data class RoomEffect(val id: Long, val kind: String, val target: String, val message: String)
@Serializable data class RoomObject(val id: String, val label: String, val description: String, val available: Boolean, val shared: Boolean)

@Serializable data class EscapePublic(
    val roomId: String,
    val revision: Long,
    val mode: RoomMode,
    val stage: EscapeStage,
    val objective: String,
    val secondsLeft: Int,
    val outcome: String,
    val paused: Boolean,
    val practice: Boolean,
    val armSeconds: Int,
    val mistakes: Int,
    val hintsUsed: Int,
    val systems: RemoteSystemState,
    val discoveries: Int
)

@Serializable data class EscapePilotView(
    val status: EscapePublic,
    val objects: List<RoomObject>,
    val symbols: List<String>,
    val fragments: List<StoryFragment>,
    val notes: List<Note>,
    val inventory: List<String>,
    val feedback: String,
    val discoveries: List<String>,
    val effect: RoomEffect?,
    val ending: String?,
    val epilogue: List<String> = emptyList(),
    val timeline: List<ChatEntry> = emptyList(),
    /** Seconds since the companion last used a tool, or -1 if it never has. */
    val agentIdleSeconds: Int = -1
)

@Serializable data class EscapeAgentView(
    val status: EscapePublic,
    val brief: String,
    val reported: List<Finding>,
    val notes: List<Note>,
    val availableActions: List<String>,
    val queriesLeft: Int,
    val messagesLeft: Int,
    val armsLeft: Int,
    val routesLeft: Int,
    val calculationsLeft: Int,
    val controlsLeft: Int
)

@Serializable data class EscapeReceipt(val roomId: String, val revision: Long, val detail: String)
@Serializable data class EscapeEvent(val elapsedSeconds: Int, val stage: EscapeStage, val detail: String)
@Serializable data class EscapeDebrief(
    val rulesVersion: String = EscapeRoom.RULES_VERSION,
    val roomId: String,
    val seed: Long,
    val mode: RoomMode,
    val incidentId: String,
    val outcome: String,
    val secondsUsed: Int,
    val mistakes: Int,
    val hintsUsed: Int,
    val cluesShared: Int,
    val remoteActions: Int,
    val discoveries: Int,
    val cooperationRating: String,
    val practice: Boolean,
    val companion: CompanionDescriptor,
    val events: List<EscapeEvent>
)

/** Trusted local authority. Human and agent methods are deliberately separate. */
class Escape(
    seed: Long,
    val mode: RoomMode = RoomMode.STANDARD,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    val id: String = UUID.randomUUID().toString(),
    private val companion: CompanionDescriptor = CompanionDescriptor()
) {
    private val scenario = EscapeRoom.generate(seed)
    private var stage = EscapeStage.POWER
    private var outcome = "IN_PROGRESS"
    private var revision = 0L
    private var pilotRevision = 0L
    private var effectRevision = 0L
    private val startedAt = clock()
    private var deadline = startedAt + mode.seconds * 1000L
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
    private var controls = 0
    private var systems = RemoteSystemState()
    private var lastObserve: Long? = null
    private var feedback = "Inspect the breaker panel and share its readings with your companion."
    private var latestEffect: RoomEffect? = null
    private val inspected = linkedSetOf<String>()
    private val reported = mutableListOf<Finding>()
    private val notes = mutableListOf<Note>()
    private val discoveries = linkedSetOf<String>()
    private val events = mutableListOf<EscapeEvent>()
    private val requests = mutableMapOf<String, Pair<String, EscapeReceipt>>()
    private val timeline = mutableListOf<ChatEntry>()
    private var sequence = 0L
    private var lastAgentAt: Long? = null
    private var lastObservedPilot = -1L

    @Synchronized fun status(): EscapePublic {
        tick()
        return EscapePublic(id, revision, mode, stage, objective(), remaining(), outcome, pausedAt != null, practice,
            if (finished()) 0 else ((armedUntil - now()).coerceAtLeast(0) / 1000).toInt(), mistakes, hints, systems, discoveries.size)
    }

    @Synchronized fun pilotView(): EscapePilotView {
        val public = status()
        return EscapePilotView(public, objectList(), if ("panel" in inspected) scenario.symbols else emptyList(),
            if (stage >= EscapeStage.STORY && "recorder" in inspected) scenario.fragments else emptyList(), notes.toList(),
            buildList {
                if (stage >= EscapeStage.CABINET) add("Emergency power")
                if (stage >= EscapeStage.STORY) add("Three memory strips")
                if (stage == EscapeStage.EXIT) add("Manual release key")
            }, feedback, discoveries.toList(), latestEffect,
            if (!finished()) null else when (outcome) {
                "ESCAPED" -> "The door gives. Clean air. ${scenario.incident.ending}"
                "FAILED" -> "The emergency reserve runs out. The recovered log remains in your local debrief."
                else -> "Run ended. No escape result recorded."
            }, epilogue(), timeline.toList(), lastAgentAt?.let { ((clock() - it) / 1000).toInt() } ?: -1)
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
            emit("SHARE", objectId, "Clue transmitted to companion")
        }
    }

    @Synchronized fun say(text: String) {
        active(); validate(text); appendNote("YOU", text.trim()); changedByPilot()
    }

    @Synchronized fun submitPower(order: List<String>): Boolean {
        active(); requireStage(EscapeStage.POWER)
        if ("panel" !in inspected) fail("NOT_INSPECTED", "Inspect the breaker panel first.")
        if (order.size != 3 || order.toSet() != scenario.symbols.toSet()) fail("INVALID_ANSWER", "Use each fitted symbol once.")
        val routed = systems.power ?: fail("REMOTE_POWER_REQUIRED", "Your companion must route the remote supply first.")
        val required = requiredPower()
        if (routed != required) return wrong("The supply trips under load. Ask your companion to check the current draw.", "BREAKER_TRIP", "panel")
        if (order != scenario.startupOrder) return wrong("The breaker trips. The startup order was wrong.", "BREAKER_TRIP", "panel")
        stage = EscapeStage.CABINET
        solved("Power restored. The cabinet decoder wakes, waiting for your companion to tune it.", "POWER_ON", "room")
        return true
    }

    @Synchronized fun submitPassword(answer: String): Boolean {
        active(); requireStage(EscapeStage.CABINET)
        if ("cabinet" !in inspected) fail("NOT_INSPECTED", "Inspect the cabinet first.")
        if (!answer.matches(Regex("[A-Za-z]{1,12}"))) fail("INVALID_ANSWER", "Enter a word of 1–12 letters.")
        if (systems.decoderShift != scenario.shift) fail("REMOTE_DECODER_REQUIRED", "Your companion must tune the decoder to the correct offset first.")
        if (!answer.equals(scenario.password, true)) return wrong("The keypad rejects that word.", "KEYPAD_FAIL", "cabinet")
        stage = EscapeStage.STORY
        solved("The cabinet opens. Three memory strips and a waveform reader slide forward.", "CABINET_OPEN", "cabinet")
        return true
    }

    @Synchronized fun submitStory(order: List<String>): Boolean {
        active(); requireStage(EscapeStage.STORY)
        if ("recorder" !in inspected) fail("NOT_INSPECTED", "Read the memory strips first.")
        if (order.size != 3 || order.toSet() != setOf("A", "B", "C")) fail("INVALID_ANSWER", "Use strips A, B and C once each.")
        if (systems.recorderChannel != requiredRecorderChannel()) fail("REMOTE_RECORDER_REQUIRED", "Your companion must synchronize the waveform channel first.")
        if (order != scenario.storyOrder) return wrong("The recorder cannot reconcile that timeline.", "RECORDER_FAIL", "recorder")
        stage = EscapeStage.EXIT
        solved("The log resolves and releases the manual key. Both operators are required at the exit.", "RECORDER_LOCK", "recorder")
        return true
    }

    @Synchronized fun escape(): Boolean {
        active(); requireStage(EscapeStage.EXIT)
        if (pausedAt != null) fail("PAUSED", "Resume the clock before the final release.")
        if (clock() >= armedUntil) fail("NOT_ARMED", "Ask your companion to arm the release, then turn the handle within 20 seconds.")
        remainingFinal = remaining(); outcome = "ESCAPED"; elapsedFinal = elapsed()
        feedback = "The door opens. You made it out together."
        changedByPilot(); emit("ESCAPE", "door", "Door opened by both operators"); event("Door opened while companion release was armed")
        return true
    }

    @Synchronized fun hint() {
        active()
        if (hints >= 3) fail("NO_HINTS", "All three hints have been used.")
        hints++; deadline -= mode.hintPenalty * 1000L
        feedback = contextualHint()
        changedByPilot(); emit("HINT", stage.name.lowercase(), "Hint requested"); event("Hint requested (${mode.hintPenalty}-second penalty)"); tick()
    }

    @Synchronized fun observe(): EscapeAgentView {
        tick()
        val time = clock()
        lastObserve?.let { if (time - it < 1000) fail("RATE_LIMIT", "Observe at most once per second.") }
        lastObserve = time
        if (pilotRevision != lastObservedPilot && !finished()) {
            lastObservedPilot = pilotRevision
            activity("OBSERVE", "CHECKED THE ROOM", if (reported.isEmpty()) "No clues shared yet" else "${reported.size} shared clue${if (reported.size == 1) "" else "s"} in view")
        }
        return EscapeAgentView(status(), AGENT_BRIEF, reported.toList(), notes.toList(), availableActions(),
            12 - queries, 30 - agentMessages, 12 - arms, 8 - routes, 16 - calculations, 16 - controls)
    }

    @Synchronized fun archive(query: String): List<String> {
        active()
        if (query.isBlank() || query.length > 120) fail("INVALID_QUERY", "Use 1–120 characters.")
        if (queries >= 12) fail("BUDGET_EXHAUSTED", "Archive query budget exhausted. Keep earlier records.")
        queries++; revision++
        val priorities = scenario.manualOrder.mapIndexed { i, symbol -> "$symbol = ${i + 1}" }.sorted()
        val records = listOf(
            "POWER MANUAL: Ask for the fitted symbols, load watts and supply volts. Calculate amps = watts DIVIDE volts. Route 1–6 A LOW, 7–12 A NORMAL, 13–18 A HIGH. Energize fitted symbols in ascending priority. ${priorities.joinToString("; ")}.",
            "CABINET MANUAL: The label was shifted forward ${scenario.shift}. Tune the decoder to ${scenario.shift}, decode by moving each letter BACKWARD ${scenario.shift}, then ask the human to enter the word.",
            "RECORDER INDEX: Ask for the waveform and strips. Waveform channels: ${scenario.recorderChannels.mapIndexed { i, symbol -> "$symbol = ${'A' + i}" }.joinToString("; ")}. Synchronize that channel. Order causes: safety shutdown, shelter response, rescue plan.",
            "EXIT MANUAL: Ask for the door seal. Channels: ${scenario.channelOrder.mapIndexed { i, symbol -> "$symbol = ${'A' + i}" }.joinToString("; ")}. Arm it; the human has 20 seconds to turn the handle.",
            "ENVIRONMENT: UV lighting exposes inspection ink. Safe ventilation for incident ${scenario.incident.id}: ${scenario.incident.safeVentilation}.",
            "INCIDENT ${scenario.incident.id}: The first action prevented a disaster; the seal protected the occupant; the companion circuit was deliberately left online."
        )
        val result = if (query.trim().equals("ALL", true)) records else records.filter { it.contains(query.trim(), true) }
        activity("ARCHIVE", "READ MANUALS", if (result.isEmpty()) "No matching record" else result.joinToString(" · ") { it.substringBefore(":").lowercase().replaceFirstChar(Char::titlecase) })
        return result
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
            val result = when (operation) {
                Arithmetic.ADD -> (a.toLong() + b).toString()
                Arithmetic.SUBTRACT -> (a.toLong() - b).toString()
                Arithmetic.MULTIPLY -> (a.toLong() * b).toString()
                Arithmetic.DIVIDE -> (a.toDouble() / b).toString()
            }
            calculations++
            val symbol = when (operation) { Arithmetic.ADD -> "+"; Arithmetic.SUBTRACT -> "−"; Arithmetic.MULTIPLY -> "×"; Arithmetic.DIVIDE -> "÷" }
            val pretty = result.toDoubleOrNull()?.let { if (it == kotlin.math.floor(it)) it.toLong().toString() else "%.2f".format(it) } ?: result
            activity("CALCULATE", "CALCULATE", "$a $symbol $b = $pretty")
            "$a ${operation.name} $b = $result"
        }

    @Synchronized fun routePower(requestId: String, mode: PowerMode): EscapeReceipt = mutate(requestId, "route:$mode") {
        requireStage(EscapeStage.POWER)
        if (routes >= 8) fail("BUDGET_EXHAUSTED", "Eight power configurations per room.")
        routes++; systems = systems.copy(power = mode)
        activity("ROUTE_POWER", "ROUTE POWER", "Remote supply set to $mode")
        appendNote("COMPANION", "Remote supply routed: $mode. Set the breaker sequence.")
        emit("POWER_ROUTE", "panel", "Companion routed $mode power"); event("Companion routed power: $mode")
        "Power routed: $mode. The human must operate the breakers."
    }

    /** Compatibility for application callers; it is not registered as a v2 tool. */
    @Synchronized fun route(requestId: String, mode: PowerMode) = routePower(requestId, mode)

    @Synchronized fun tuneDecoder(requestId: String, shift: Int): EscapeReceipt = mutate(requestId, "decoder:$shift") {
        requireStage(EscapeStage.CABINET)
        if (shift !in 1..5) fail("INVALID_SHIFT", "Choose a decoder offset from 1 to 5.")
        spendControl()
        systems = systems.copy(decoderShift = shift)
        if (shift != scenario.shift) {
            activity("DECODER", "TUNE DECODER", "Offset $shift rejected · −${mode.mistakePenalty}s", false)
            wrongAgent("Decoder phase mismatch. Offset rejected.", "DECODER_FAIL", "cabinet")
            "Decoder rejected offset $shift. ${mode.mistakePenalty}-second penalty applied."
        } else {
            activity("DECODER", "TUNE DECODER", "Offset $shift locked")
            appendNote("COMPANION", "Decoder tuned to offset $shift. Enter the decoded word.")
            emit("DECODER_TUNED", "cabinet", "Companion aligned the decoder"); event("Companion tuned cabinet decoder")
            "Decoder locked to offset $shift. Human may enter the decoded word."
        }
    }

    @Synchronized fun syncRecorder(requestId: String, channel: String): EscapeReceipt = mutate(requestId, "recorder:$channel") {
        requireStage(EscapeStage.STORY)
        if (channel !in CHANNELS) fail("INVALID_CHANNEL", "Choose recorder channel A–F.")
        spendControl(); systems = systems.copy(recorderChannel = channel)
        if (channel != requiredRecorderChannel()) {
            activity("RECORDER", "SYNC RECORDER", "Channel $channel rejected · −${mode.mistakePenalty}s", false)
            wrongAgent("The recorder fills with static. That waveform channel is wrong.", "RECORDER_FAIL", "recorder")
            "Recorder rejected channel $channel. ${mode.mistakePenalty}-second penalty applied."
        } else {
            activity("RECORDER", "SYNC RECORDER", "Locked on channel $channel")
            appendNote("COMPANION", "Recorder synchronized on channel $channel. Rebuild the timeline.")
            emit("RECORDER_SYNC", "recorder", "Companion synchronized the recorder"); event("Companion synchronized recorder")
            "Recorder synchronized. Human may reconstruct the strips."
        }
    }

    @Synchronized fun controlEnvironment(requestId: String, system: String, setting: String): EscapeReceipt =
        mutate(requestId, "environment:$system:$setting") {
            if (stage == EscapeStage.POWER) fail("NO_POWER", "Restore emergency power before using environmental controls.")
            when (system) {
                "LIGHTING" -> {
                    val value = LightingMode.entries.find { it.name == setting } ?: fail("INVALID_SETTING", "Lighting uses EMERGENCY, WORK or ULTRAVIOLET.")
                    spendControl()
                    systems = systems.copy(lighting = value)
                    activity("LIGHTING", "SET LIGHTS", value.name)
                    if (value == LightingMode.ULTRAVIOLET && discoveries.add(scenario.incident.ultravioletDiscovery)) event("Companion revealed UV evidence")
                    emit("LIGHTING", "room", "Lighting changed to $setting")
                    "Lighting set to $setting.${if (value == LightingMode.ULTRAVIOLET) " The human can now see hidden inspection ink." else ""}"
                }
                "VENTILATION" -> {
                    val value = VentilationMode.entries.find { it.name == setting } ?: fail("INVALID_SETTING", "Ventilation uses INTAKE, EXHAUST or HOLD.")
                    spendControl()
                    if (value.name != scenario.incident.safeVentilation) {
                        activity("VENTILATION", "SET AIRFLOW", "$setting unsafe · −${mode.mistakePenalty}s", false)
                        wrongAgent("The airflow alarms and returns to standby. Check the incident record.", "VENT_FAIL", "vent")
                        "Unsafe ventilation mode rejected. ${mode.mistakePenalty}-second penalty applied."
                    } else {
                        systems = systems.copy(ventilation = value)
                        activity("VENTILATION", "SET AIRFLOW", "$setting · air clearing")
                        if (discoveries.add(scenario.incident.ventilationDiscovery)) event("Companion stabilized ventilation")
                        emit("VENTILATION", "room", "Ventilation stabilized on $setting")
                        "Ventilation stabilized. The human can see a newly cleared detail."
                    }
                }
                else -> fail("INVALID_SYSTEM", "Choose LIGHTING or VENTILATION.")
            }
        }

    @Synchronized fun armExit(requestId: String, channel: String): EscapeReceipt = mutate(requestId, "arm:$channel") {
        requireStage(EscapeStage.EXIT)
        if (pausedAt != null) fail("PAUSED", "Ask the human to resume before arming.")
        if (channel !in CHANNELS) fail("INVALID_CHANNEL", "Choose exit channel A–F.")
        if (arms >= 12) fail("BUDGET_EXHAUSTED", "Twelve release authorizations per room.")
        arms++
        if (channel != requiredExitChannel()) {
            armedUntil = 0
            activity("ARM", "ARM RELEASE", "Channel $channel rejected · −${mode.mistakePenalty}s", false)
            wrongAgent("Release channel rejected. Check the shared door seal.", "ARM_FAIL", "door")
            "Channel rejected. ${mode.mistakePenalty}-second penalty applied."
        } else {
            armedUntil = clock() + 20_000
            activity("ARM", "ARM RELEASE", "Channel $channel armed · 20s")
            appendNote("COMPANION", "Release armed. Turn the handle now — 20 seconds.")
            emit("ARMED", "door", "Companion armed the release"); event("Companion armed exit for 20 seconds")
            "Release armed for 20 seconds. Human must turn the handle."
        }
    }

    @Synchronized fun arm(requestId: String, channel: String) = armExit(requestId, channel)

    @Synchronized fun pause() {
        tick()
        if (!finished() && pausedAt == null) { pausedAt = clock(); practice = true; changedByPilot() }
    }

    @Synchronized fun resume() {
        if (finished()) return
        pausedAt?.let {
            val interval = clock() - it
            deadline += interval
            if (armedUntil > it) armedUntil += interval
            pausedMs += interval; pausedAt = null; changedByPilot()
        }
    }

    @Synchronized fun tick() {
        if (!finished() && pausedAt == null && clock() >= deadline) {
            outcome = "FAILED"; elapsedFinal = elapsed(); feedback = "Emergency air reserve depleted."
            revision++; emit("TIMEOUT", "room", "Air reserve depleted"); event("Air reserve expired")
        }
    }

    @Synchronized fun interrupt() {
        if (!finished()) { remainingFinal = remaining(); outcome = "INTERRUPTED"; elapsedFinal = elapsed(); revision++; event("Run interrupted") }
    }

    @Synchronized fun finished() = outcome != "IN_PROGRESS"
    @Synchronized fun pilotSignal(): Long = pilotRevision

    @Synchronized fun debrief(): EscapeDebrief {
        if (!finished()) fail("ROOM_ACTIVE", "Debrief is available only after the run.")
        return EscapeDebrief(EscapeRoom.RULES_VERSION, id, scenario.seed, mode, scenario.incident.id, outcome,
            elapsedFinal ?: elapsed(), mistakes, hints, reported.size, routes + controls + arms,
            discoveries.size, cooperationRating(), practice, companion, events.toList())
    }

    private fun objectList(): List<RoomObject> {
        val panel = if ("panel" in inspected) "Fitted symbols: ${scenario.symbols.joinToString(", ")}. Load: ${scenario.drawAmps * scenario.supplyVolts} W. Supply: ${scenario.supplyVolts} V." else "Three unmarked breaker levers sit beside a dead current display."
        val cabinet = if (stage >= EscapeStage.CABINET && "cabinet" in inspected) "The powered label reads ${scenario.cipher}. The remote decoder dial is controlled by your companion." else "A steel cabinet with a dark electronic lock."
        val recorder = if (stage >= EscapeStage.STORY && "recorder" in inspected) "Waveform: ${scenario.recorderWaveform}. Strips: ${scenario.fragments.joinToString(" | ") { "${it.id}: ${it.text}" }}" else "A three-track recorder locked inside the cabinet."
        val door = if (stage == EscapeStage.EXIT && "door" in inspected) "Routing seal: ${scenario.releaseSeal}. The companion must arm its matching channel." else "A pressure door with two isolated release circuits."
        fun shared(label: String, text: String) = reported.any { it.compartment == label && it.text == text }
        return listOf(
            RoomObject("panel", "Breaker panel", panel, true, shared("Breaker panel", panel)),
            RoomObject("cabinet", "Cipher cabinet", cabinet, stage >= EscapeStage.CABINET, shared("Cipher cabinet", cabinet)),
            RoomObject("recorder", "Memory recorder", recorder, stage >= EscapeStage.STORY, shared("Memory recorder", recorder)),
            RoomObject("door", "Manual exit", door, true, shared("Manual exit", door))
        )
    }
    private fun requiredPower() = when { scenario.drawAmps <= 6 -> PowerMode.LOW; scenario.drawAmps <= 12 -> PowerMode.NORMAL; else -> PowerMode.HIGH }
    private fun requiredRecorderChannel() = ('A' + scenario.recorderChannels.indexOf(scenario.recorderWaveform)).toString()
    private fun requiredExitChannel() = ('A' + scenario.channelOrder.indexOf(scenario.releaseSeal)).toString()

    private fun availableActions() = buildList {
        add("MESSAGE")
        add("ARCHIVE")
        when (stage) {
            EscapeStage.POWER -> { add("CALCULATE"); add("ROUTE_POWER") }
            EscapeStage.CABINET -> { add("TUNE_DECODER"); add("CONTROL_ENVIRONMENT") }
            EscapeStage.STORY -> { add("SYNC_RECORDER"); add("CONTROL_ENVIRONMENT") }
            EscapeStage.EXIT -> { add("ARM_EXIT"); add("CONTROL_ENVIRONMENT") }
        }
    }

    private fun objective() = when (stage) {
        EscapeStage.POWER -> "Restore the emergency circuit together"
        EscapeStage.CABINET -> "Tune and decode the cabinet"
        EscapeStage.STORY -> "Synchronize and rebuild the last shift"
        EscapeStage.EXIT -> "Release the door together"
    }

    private fun contextualHint() = when (stage) {
        EscapeStage.POWER -> if (systems.power == null) "Share watts, volts and symbols. Your companion calculates amps and routes LOW, NORMAL or HIGH." else "Use only fitted symbols, ordered by the manual's ascending priorities."
        EscapeStage.CABINET -> if (systems.decoderShift == null) "Share the encoded label. Your companion must tune the shift from its cabinet manual." else "Move every encoded letter backward by the tuned offset, wrapping A to Z."
        EscapeStage.STORY -> if (systems.recorderChannel == null) "Share the waveform and strips. Your companion maps the waveform to a recorder channel." else "Order cause and effect: prevention, shelter, then rescue."
        EscapeStage.EXIT -> "Share the routing seal. Your companion maps it to a channel and arms the release; you turn the handle."
    }

    private fun cooperationRating() = when {
        outcome != "ESCAPED" -> "INCOMPLETE"
        mistakes == 0 && hints == 0 && reported.size >= 4 && routes + controls + arms >= 4 -> "SYNCHRONIZED"
        else -> "COORDINATED"
    }

    private fun spendControl() {
        if (controls >= 16) fail("BUDGET_EXHAUSTED", "Sixteen remote control actions per room.")
        controls++
    }

    private fun mutate(requestId: String, payload: String, action: () -> String): EscapeReceipt {
        if (!requestId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) fail("INVALID_REQUEST_ID", "Use 1–64 letters, digits, _ or -.")
        requests[requestId]?.let {
            if (it.first != payload) fail("REQUEST_CONFLICT", "Request ID already used with different content.")
            return it.second
        }
        active()
        val detail = action(); revision++
        return EscapeReceipt(id, revision, detail).also { requests[requestId] = payload to it }
    }

    private fun wrong(message: String, effect: String, target: String): Boolean {
        mistakes++; deadline -= mode.mistakePenalty * 1000L; feedback = "$message −${mode.mistakePenalty} seconds."
        changedByPilot(); emit(effect, target, message); event("Wrong answer (${mode.mistakePenalty}-second penalty)"); tick(); return false
    }

    private fun wrongAgent(message: String, effect: String, target: String) {
        mistakes++; deadline -= mode.mistakePenalty * 1000L; feedback = "$message −${mode.mistakePenalty} seconds."
        emit(effect, target, message); event("Wrong remote action (${mode.mistakePenalty}-second penalty)"); tick()
    }

    private fun solved(message: String, effect: String, target: String) {
        feedback = message; changedByPilot(); emit(effect, target, message); event(message)
    }

    private fun emit(kind: String, target: String, message: String) {
        latestEffect = RoomEffect(++effectRevision, kind, target, message)
    }

    private fun changedByPilot() { revision++; pilotRevision++ }
    private fun appendNote(role: String, text: String) {
        notes += Note(role, text); if (notes.size > 80) notes.removeAt(0)
        timeline += ChatEntry(++sequence, note = Note(role, text)); if (timeline.size > 120) timeline.removeAt(0)
    }
    private fun activity(kind: String, title: String, detail: String, ok: Boolean = true) {
        val seq = ++sequence
        timeline += ChatEntry(seq, activity = Activity(seq, kind, title, detail, ok)); if (timeline.size > 120) timeline.removeAt(0)
        lastAgentAt = clock()
    }
    private fun epilogue(): List<String> = when {
        !finished() -> emptyList()
        outcome == "ESCAPED" -> scenario.incident.escapeEpilogue
        outcome == "FAILED" -> scenario.incident.trappedEpilogue
        else -> listOf("You step back from the door.", "The room will wait for you.")
    }
    private fun requireStage(required: EscapeStage) { if (stage != required) fail("WRONG_STAGE", "Current objective: ${stage.name.lowercase()}.") }
    private fun active() { tick(); if (finished()) fail("ROOM_CLOSED", "The run is over.") }
    private fun validate(text: String) { if (text.isBlank() || text.length > 500 || text.any { it.isISOControl() && it != '\n' }) fail("INVALID_TEXT", "Use 1–500 plain-text characters.") }
    private fun now() = pausedAt ?: clock()
    private fun remaining() = remainingFinal ?: if (outcome == "FAILED") 0 else ((deadline - now()).coerceAtLeast(0) / 1000).toInt()
    private fun elapsed() = ((now() - startedAt - pausedMs).coerceAtLeast(0) / 1000).toInt()
    private fun event(detail: String) { events += EscapeEvent(elapsedFinal ?: elapsed(), stage, detail) }
    private fun fail(code: String, message: String): Nothing = throw GameError(code, message)

    private companion object {
        val CHANNELS = listOf("A", "B", "C", "D", "E", "F")
        const val AGENT_BRIEF = "You are the remote-systems companion. You cannot see or touch the room. Read records, ask the human to inspect and share clues, operate the available remote systems, and communicate each useful result. Only the human operates breakers, enters passwords, arranges strips and turns the handle. Never invent physical clues."
    }
}
