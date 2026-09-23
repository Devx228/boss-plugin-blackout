package ai.rever.boss.plugin.dynamic.blackout

import ai.rever.boss.plugin.dynamic.blackout.application.*
import ai.rever.boss.plugin.dynamic.blackout.ui.Clues
import ai.rever.boss.plugin.dynamic.blackout.ui.EscapeBoard
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Renders each screen offscreen to a PNG. No window opens and nothing else on the desktop is
 * captured. The companion's moves are played through the same public methods its tools call,
 * using only what the archive tells it, so every frame shows a legal state.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/ui-snapshots" }).apply { mkdirs() }

    fun shoot(name: String, width: Int, height: Int, after: (Session) -> Unit = {}, settle: Int = 60, prepare: (Session) -> Unit) {
        val session = Session()
        val scene = ImageComposeScene(width, height, Density(1f)) { EscapeBoard(session, null) }
        var time = 0L
        fun frames(count: Int) = repeat(count) { scene.render(time); time += 50_000_000L; Thread.sleep(40) }
        frames(6)
        prepare(session)
        frames(30)
        after(session)
        frames(settle)
        val image = scene.render(time)
        File(out, "$name.png").writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
        scene.close()
        println("Rendered $name.png")
    }

    shoot("1-opening", 1600, 950) {}
    shoot("2-power", 1600, 950) { s -> Pair(s.startEscape(seed = 42), Companion2()).let { (room, ai) -> ai.power(room, solve = false) } }
    shoot("3-cabinet", 1600, 950) { s -> val room = s.startEscape(seed = 42); val ai = Companion2(); ai.power(room); ai.cabinet(room, solve = false) }
    shoot("4-recorder", 1600, 950) { s -> val room = s.startEscape(seed = 42); val ai = Companion2(); ai.power(room); ai.cabinet(room); ai.recorder(room, solve = false) }
    shoot("5-exit", 1600, 950) { s -> val room = s.startEscape(seed = 42); val ai = Companion2(); ai.power(room); ai.cabinet(room); ai.recorder(room); ai.exit(room, open = false) }
    shoot("6-escaped", 1600, 950, after = { s -> s.escape?.escape() }) { s -> val room = s.startEscape(seed = 42); val ai = Companion2(); ai.power(room); ai.cabinet(room); ai.recorder(room); ai.exit(room, open = false) }
    shoot("8-escape-story", 1600, 950, after = { s -> s.escape?.escape() }, settle = 230) { s -> val room = s.startEscape(seed = 42); val ai = Companion2(); ai.power(room); ai.cabinet(room); ai.recorder(room); ai.exit(room, open = false) }
    shoot("9-trapped-room", 1600, 950, after = { s -> suffocate(s) }, settle = 95) { s -> Companion2().power(s.startEscape(seed = 7, mode = RoomMode.SHOWCASE), solve = false) }
    shoot("10-trapped-story", 1600, 950, after = { s -> suffocate(s) }, settle = 240) { s -> Companion2().power(s.startEscape(seed = 7, mode = RoomMode.SHOWCASE), solve = false) }
    shoot("7-compact", 1000, 1500) { s -> val room = s.startEscape(seed = 42); val ai = Companion2(); ai.power(room); ai.cabinet(room, solve = false) }
}

/** Burns the air with wrong breaker orders so the trapped ending can be rendered without waiting four minutes. */
private fun suffocate(session: Session) {
    val room = session.escape ?: return
    val wrong = room.pilotView().symbols.reversed()
    repeat(40) { if (!room.finished()) runCatching { room.submitPower(wrong) } }
}

/** A scripted stand-in used only for rendering, never inside the plugin. It reads the archive like a model would. */
private class Companion2 {
    private var request = 0
    private fun id() = "snap-${request++}"
    private lateinit var records: List<String>
    private fun record(prefix: String) = records.first { it.startsWith(prefix) }
    private fun pairs(text: String) = Regex("([A-Z]+) = ([A-F0-9])").findAll(text).associate { it.groupValues[1] to it.groupValues[2] }

    fun power(room: Escape, solve: Boolean = true) {
        records = room.archive("ALL")
        room.inspect("panel"); room.report("panel")
        val view = room.pilotView()
        val clue = Clues.panel(view)
        val amps = (clue.watts ?: 0) / (clue.volts ?: 1)
        room.calculate(id(), Arithmetic.DIVIDE, clue.watts ?: 0, clue.volts ?: 1)
        val mode = when { amps <= 6 -> PowerMode.LOW; amps <= 12 -> PowerMode.NORMAL; else -> PowerMode.HIGH }
        room.routePower(id(), mode)
        val priorities = pairs(record("POWER MANUAL"))
        val order = view.symbols.sortedBy { priorities[it]?.toInt() ?: 9 }
        room.message(id(), "Current is $amps A, so I routed $mode. Flip ${order.joinToString(", ")}.")
        if (solve) room.submitPower(order)
    }

    fun cabinet(room: Escape, solve: Boolean = true) {
        room.inspect("cabinet"); room.report("cabinet")
        val serial = Regex("Serial plate: ([A-Z])-").find(room.pilotView().objects.first { it.id == "cabinet" }.description)!!.groupValues[1]
        val shift = Regex("\\b$serial = (\\d)").find(record("CABINET MANUAL"))!!.groupValues[1].toInt()
        room.tuneDecoder(id(), shift)
        val cipher = Clues.cabinet(room.pilotView()).cipher.orEmpty()
        val word = cipher.map { 'A' + ((it - 'A' - shift + 26) % 26) }.joinToString("")
        room.message(id(), "Decoder tuned to $shift. Shift each letter back: the word is $word.")
        if (solve) room.submitPassword(word)
    }

    fun recorder(room: Escape, solve: Boolean = true) {
        room.inspect("recorder"); room.report("recorder")
        val view = room.pilotView()
        val waveform = Clues.recorder(view).waveform.orEmpty()
        val channel = pairs(record("RECORDER INDEX"))[waveform] ?: "A"
        room.syncRecorder(id(), channel)
        room.controlEnvironment(id(), "LIGHTING", "ULTRAVIOLET")
        fun rank(text: String) = when { "companion" in text.lowercase() -> 2; "seal" in text.lowercase() || "hold pressure" in text.lowercase() -> 1; else -> 0 }
        val order = view.fragments.sortedBy { rank(it.text) }.map { it.id }
        room.message(id(), "Synced on $channel and switched to UV. Order: ${order.joinToString(" → ")}.")
        if (solve) room.submitStory(order)
    }

    fun exit(room: Escape, open: Boolean) {
        room.inspect("door"); room.report("door")
        val seal = Clues.door(room.pilotView()).seal.orEmpty()
        val channel = pairs(record("EXIT MANUAL"))[seal] ?: "A"
        room.armExit(id(), channel)
        room.message(id(), "Release armed on $channel. Turn the handle now!")
        if (open) room.escape()
    }
}
