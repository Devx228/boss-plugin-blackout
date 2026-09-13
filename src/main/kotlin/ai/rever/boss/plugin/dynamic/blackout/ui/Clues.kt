package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.dynamic.blackout.application.EscapePilotView
import ai.rever.boss.plugin.dynamic.blackout.application.EscapeStage
import ai.rever.boss.plugin.dynamic.blackout.application.RoomObject

/**
 * The engine describes each object as plain text so both seats read the same words. The UI
 * lifts the numbers and symbols back out to draw them. When a pattern misses, the device
 * falls back to the original sentence, so a wording change never hides a clue.
 */
data class PanelClue(val symbols: List<String>, val watts: Int?, val volts: Int?)
data class CabinetClue(val cipher: String?)
data class RecorderClue(val waveform: String?, val strips: Map<String, String>)
data class DoorClue(val seal: String?)

object Clues {
    private val load = Regex("Load:\\s*(\\d+)\\s*W", RegexOption.IGNORE_CASE)
    private val supply = Regex("Supply:\\s*(\\d+)\\s*V", RegexOption.IGNORE_CASE)
    private val label = Regex("label reads\\s+([A-Z]+)", RegexOption.IGNORE_CASE)
    private val waveform = Regex("Waveform:\\s*([A-Z]+)", RegexOption.IGNORE_CASE)
    private val seal = Regex("Routing seal:\\s*([A-Z]+)", RegexOption.IGNORE_CASE)

    fun panel(view: EscapePilotView): PanelClue {
        val text = view.objects.find { it.id == "panel" }?.description.orEmpty()
        return PanelClue(view.symbols, load.find(text)?.groupValues?.get(1)?.toIntOrNull(), supply.find(text)?.groupValues?.get(1)?.toIntOrNull())
    }

    fun cabinet(view: EscapePilotView): CabinetClue {
        val text = view.objects.find { it.id == "cabinet" }?.description.orEmpty()
        return CabinetClue(label.find(text)?.groupValues?.get(1)?.uppercase())
    }

    fun recorder(view: EscapePilotView): RecorderClue {
        val text = view.objects.find { it.id == "recorder" }?.description.orEmpty()
        return RecorderClue(waveform.find(text)?.groupValues?.get(1)?.uppercase(), view.fragments.associate { it.id to it.text })
    }

    fun door(view: EscapePilotView): DoorClue {
        val text = view.objects.find { it.id == "door" }?.description.orEmpty()
        return DoorClue(seal.find(text)?.groupValues?.get(1)?.uppercase())
    }

    /** The object the current objective is about. */
    fun focusFor(stage: EscapeStage): String = when (stage) {
        EscapeStage.POWER -> "panel"
        EscapeStage.CABINET -> "cabinet"
        EscapeStage.STORY -> "recorder"
        EscapeStage.EXIT -> "door"
    }

    fun stageFor(objectId: String): EscapeStage = when (objectId) {
        "panel" -> EscapeStage.POWER
        "cabinet" -> EscapeStage.CABINET
        "recorder" -> EscapeStage.STORY
        else -> EscapeStage.EXIT
    }

    fun title(stage: EscapeStage): String = when (stage) {
        EscapeStage.POWER -> "POWER"
        EscapeStage.CABINET -> "CABINET"
        EscapeStage.STORY -> "RECORDER"
        EscapeStage.EXIT -> "EXIT"
    }

    fun humanStep(stage: EscapeStage): String = when (stage) {
        EscapeStage.POWER -> "Share the panel, then flip the breakers in the order your companion gives."
        EscapeStage.CABINET -> "Share the label, wait for the decoder, then type the decoded word."
        EscapeStage.STORY -> "Share the recorder, wait for sync, then put the strips in order."
        EscapeStage.EXIT -> "Share the seal, wait for the release lamp, then turn the handle."
    }

    fun companionStep(stage: EscapeStage): String = when (stage) {
        EscapeStage.POWER -> "Calculates the current and routes remote power."
        EscapeStage.CABINET -> "Reads the manual and tunes the decoder offset."
        EscapeStage.STORY -> "Maps the waveform and synchronizes the recorder."
        EscapeStage.EXIT -> "Matches the seal and arms the release for 20 seconds."
    }

    fun isShared(obj: RoomObject?): Boolean = obj?.shared == true
}
