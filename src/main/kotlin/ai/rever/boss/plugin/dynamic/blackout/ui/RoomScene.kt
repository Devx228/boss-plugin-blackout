package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.dynamic.blackout.application.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import kotlin.math.sin

/** Everything the room illustration reacts to, gathered so drawing functions stay small. */
data class SceneState(
    val stage: EscapeStage = EscapeStage.POWER,
    val selected: String? = null,
    val outcome: String = "IN_PROGRESS",
    val systems: RemoteSystemState = RemoteSystemState(),
    val effect: RoomEffect? = null,
    val shared: Set<String> = emptySet(),
    val armSeconds: Int = 0,
    val discoveries: List<String> = emptyList(),
    val companionBusy: Boolean = false,
    val paused: Boolean = false,
    val cinematic: Cinematic = Cinematic.NONE,
    /** 0 when an ending sequence starts, 1 when its room animation is complete. */
    val cinematicProgress: Float = 0f,
    /** Extra light for decorative scenes such as the title screen. */
    val ambientBoost: Float = 0f
)

/** Room-level animation for the ending sequences. */
enum class Cinematic { NONE, ESCAPE, TRAPPED }

/** Hit areas on the 1600 x 820 virtual stage. The recorder lives inside the opened cabinet. */
private object Spots {
    val panel = Rect(150f, 215f, 410f, 575f)
    val cabinet = Rect(850f, 235f, 1110f, 640f)
    val recorder = Rect(880f, 300f, 1080f, 560f)
    val door = Rect(1215f, 120f, 1495f, 640f)
    val terminal = Rect(500f, 390f, 740f, 545f)
    val vent = Rect(560f, 95f, 780f, 175f)
    const val FLOOR = 640f
}

/** The band of the 820-tall stage that must stay visible; everything else may crop, so the room draws larger. */
private const val VIEW_TOP = 40f
private const val VIEW_HEIGHT = 680f

private val labels = mapOf("panel" to "BREAKER PANEL", "cabinet" to "CIPHER CABINET", "recorder" to "MEMORY RECORDER", "door" to "PRESSURE DOOR")

private data class Frame(
    val s: Stage,
    val state: SceneState,
    val time: Float,
    val power: Float,
    val cabinetOpen: Float,
    val doorOpen: Float,
    val fx: Float,
    val hovered: String?,
    val parallax: Float,
    val text: TextMeasurer
) {
    val kind: String? get() = if (fx < 1f) state.effect?.kind else null
    val target: String? get() = if (fx < 1f) state.effect?.target else null
    val failure: Boolean get() = kind?.endsWith("FAIL") == true || kind == "BREAKER_TRIP"
    val lightColor: Color get() = when (state.systems.lighting) {
        LightingMode.EMERGENCY -> Color(0xFFE88A4A)
        LightingMode.WORK -> Color(0xFFF3E2C4)
        LightingMode.ULTRAVIOLET -> Ink.Uv
    }
    val lit: Float get() = when {
        state.cinematic == Cinematic.TRAPPED -> (.55f * (1f - state.cinematicProgress)).coerceAtLeast(.03f)
        state.outcome == "FAILED" -> .08f
        state.systems.lighting == LightingMode.WORK -> (.35f + power * .6f + state.ambientBoost).coerceAtMost(.95f)
        state.systems.lighting == LightingMode.ULTRAVIOLET -> (.25f + power * .35f + state.ambientBoost).coerceAtMost(.9f)
        else -> (.12f + power * .38f + state.ambientBoost).coerceAtMost(.9f)
    }
}

@Composable
fun RoomScene(
    state: SceneState,
    reducedMotion: Boolean,
    interactive: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val time by rememberAmbientTime(reducedMotion)
    val power by animateFloatAsState(if (state.stage > EscapeStage.POWER) 1f else 0f, tween(1400), label = "power")
    val cabinetOpen by animateFloatAsState(if (state.stage >= EscapeStage.STORY) 1f else 0f, tween(1100), label = "cabinet")
    val doorOpen by animateFloatAsState(if (state.outcome == "ESCAPED") 1f else 0f, tween(1800), label = "door")
    val fx by rememberEffectProgress(state.effect?.id)
    val text = rememberTextMeasurer()
    var hovered by remember { mutableStateOf<String?>(null) }
    var pointerX by remember { mutableStateOf(.5f) }
    val canvasSize = remember { FloatArray(2) }

    fun hitTest(position: Offset): String? {
        if (canvasSize[0] <= 0f) return null
        val v = Stage(Size(canvasSize[0], canvasSize[1]), VIEW_TOP, VIEW_HEIGHT).toVirtual(position)
        return when {
            state.stage >= EscapeStage.STORY && Spots.recorder.contains(v) -> "recorder"
            Spots.cabinet.contains(v) -> if (state.stage >= EscapeStage.CABINET) "cabinet" else "cabinet-locked"
            Spots.panel.contains(v) -> "panel"
            Spots.door.contains(v) -> "door"
            else -> null
        }
    }

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerHoverIcon(if (interactive && hovered != null && hovered != "cabinet-locked") PointerIcon.Hand else PointerIcon.Default)
                .pointerInput(interactive, state.stage) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.firstOrNull()?.position ?: continue
                            when (event.type) {
                                PointerEventType.Exit -> { hovered = null; pointerX = .5f }
                                else -> {
                                    hovered = if (interactive) hitTest(position) else null
                                    if (size.width > 0) pointerX = (position.x / size.width).coerceIn(0f, 1f)
                                }
                            }
                        }
                    }
                }
                .pointerInput(interactive, state.stage) {
                    detectTapGestures { position ->
                        if (!interactive) return@detectTapGestures
                        when (val id = hitTest(position)) {
                            null -> {}
                            "cabinet-locked" -> onSelect("cabinet")
                            else -> onSelect(id)
                        }
                    }
                }
        ) {
            canvasSize[0] = size.width; canvasSize[1] = size.height
            val frame = Frame(Stage(size, VIEW_TOP, VIEW_HEIGHT), state, time, power, cabinetOpen, doorOpen, fx, hovered, if (reducedMotion) 0f else pointerX - .5f, text)
            drawRoom(frame)
        }
    }
}

private fun DrawScope.drawRoom(f: Frame) {
    drawRect(Ink.Void)
    val p = f.state.cinematicProgress.coerceIn(0f, 1f)
    val trapped = f.state.cinematic == Cinematic.TRAPPED
    val escape = f.state.cinematic == Cinematic.ESCAPE
    val failShake = if (f.failure && !f.state.paused) sin(f.fx * 60f) * (1f - f.fx) * f.s.len(9f) else 0f
    val dreadShake = if (trapped) sin(f.time * 37f) * f.s.len(5f) * p * (if (f.time % 3.1f < .3f) 1f else .12f) else 0f
    val zoom = when { escape -> 1f + .35f * p; trapped -> 1f + .12f * p; else -> 1f }
    val pivot = f.s.at(1355f, 400f)
    translate(failShake + dreadShake, 0f) {
      withTransform({ scale(zoom, zoom, pivot) }) {
        clipRect {
            drawShell(f)
            drawCables(f)
            drawVent(f)
            drawPanel(f)
            drawTerminal(f)
            drawCabinet(f)
            drawDoor(f)
            drawDarkness(f)
            drawEmissive(f)
            drawAtmosphere(f)
            drawFocus(f)
            drawEffects(f)
            if (trapped) drawDread(f, p)
        }
      }
    }
    if (escape) drawRect(Color(0xFFFFF4E2).dim(((p - .35f) / .65f).coerceIn(0f, 1f) * .92f))
    vignette(if (f.state.outcome == "FAILED") 1f else .75f)
    if (trapped) drawTrappedOverlay(f, p)
}

/** Two faint glints behind the porthole, blinking, once the room is nearly dark. */
private fun DrawScope.drawDread(f: Frame, p: Float) {
    if (p < .45f) return
    val s = f.s
    val blink = if (sin(f.time * 1.3f) > -.7f) 1f else 0f
    val a = ((p - .45f) / .3f).coerceIn(0f, 1f) * blink
    for (x in listOf(1343f, 1367f)) {
        val c = s.at(x, 262f)
        glow(c, s.len(18f), Ink.Red, .9f * a)
        drawCircle(Color(0xFFFFD9C8).dim(a), s.len(2.6f), c)
    }
}

/** Screen-space dread: a closing red vignette, scanline static and the odd glitch bar. */
private fun DrawScope.drawTrappedOverlay(f: Frame, p: Float) {
    val radius = maxOf(size.width, size.height) * (1.1f - .55f * p)
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xFF2A0604).dim(.55f * p), Color.Black.dim(.95f * p)), center, radius))
    scanlines(Rect(0f, 0f, size.width, size.height), 3f, .3f * p)
    val band = kotlin.math.floor(f.time * 7f).toInt()
    if (hash(band) > .6f) {
        val y = hash(band * 3) * size.height
        drawRect(Color.White.dim(.05f * p), Offset(0f, y), Size(size.width, 2f + hash(band * 5) * 6f))
    }
}

/** Walls, ceiling pipes and a floor in single-point perspective. */
private fun DrawScope.drawShell(f: Frame) {
    val s = f.s
    val shift = f.parallax * s.len(-14f)
    translate(shift, 0f) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF15161A), Color(0xFF1C1D22), Color(0xFF141518)), s.y(0f), s.y(Spots.FLOOR)),
            s.at(-1200f, -600f), Size(s.len(4000f), s.len(Spots.FLOOR + 600f)))
        // Wall panels with seams and rivets.
        var x = -1180f
        while (x < 2800f) {
            drawLine(Color.Black.dim(.55f), s.at(x, 0f), s.at(x, Spots.FLOOR), s.len(2f))
            drawLine(Color.White.dim(.025f), s.at(x + 2f, 0f), s.at(x + 2f, Spots.FLOOR), s.len(1f))
            for (ry in listOf(200f, 440f, 610f)) drawCircle(Color.Black.dim(.4f), s.len(2.4f), s.at(x + 14f, ry))
            x += 190f
        }
        drawRect(Color(0xFF101114), s.at(-1200f, 430f), Size(s.len(4000f), s.len(16f)))
        drawLine(Color.White.dim(.04f), s.at(-1200f, 430f), s.at(2800f, 430f), s.len(1f))
        hazardStripes(s.rect(-1200f, 600f, 4000f, 40f), s.len(26f), Ink.Amber.dim(.22f))
        // Ceiling pipes.
        drawRect(Color(0xFF0E0F12), s.at(-1200f, -600f), Size(s.len(4000f), s.len(660f)))
        for ((y, w) in listOf(26f to 18f, 52f to 11f)) {
            drawLine(Brush.verticalGradient(listOf(Ink.MetalLight, Ink.MetalDark), s.y(y - w / 2), s.y(y + w / 2)), s.at(-1200f, y), s.at(2800f, y), s.len(w))
            var cx = -1140f
            while (cx < 2800f) { drawRect(Ink.MetalDark, s.at(cx, y - w / 2 - 3f), Size(s.len(10f), s.len(w + 6f))); cx += 240f }
        }
        // Pipe dropping down to the panel.
        drawLine(Ink.MetalDark, s.at(120f, 60f), s.at(120f, 250f), s.len(12f))
        drawLine(Ink.MetalLight.dim(.5f), s.at(116f, 60f), s.at(116f, 250f), s.len(2f))
        // Stencilled room code.
        drawText(f.text, "MAINT-04", s.at(1000f, 150f), TextStyle(color = Color.White.dim(.07f), fontSize = (s.len(46f)).px(this), fontWeight = FontWeight.Bold, fontFamily = Type.mono))
    }
    // Floor.
    val floor = Path().apply {
        moveTo(s.x(-1200f), s.y(Spots.FLOOR)); lineTo(s.x(2800f), s.y(Spots.FLOOR)); lineTo(s.x(2800f), s.y(1500f)); lineTo(s.x(-1200f), s.y(1500f)); close()
    }
    drawPath(floor, Brush.verticalGradient(listOf(Color(0xFF1A1B1F), Color(0xFF101114)), s.y(Spots.FLOOR), s.y(820f)))
    val vanish = s.at(800f + f.parallax * 60f, 300f)
    var fx = -2400f
    while (fx <= 4000f) {
        val bottom = s.at(fx, 820f)
        val t = (Spots.FLOOR - 300f) / (820f - 300f)
        val top = Offset(vanish.x + (bottom.x - vanish.x) * t, s.y(Spots.FLOOR))
        drawLine(Color.Black.dim(.45f), top, bottom, s.len(1.5f))
        fx += 160f
    }
    for (fy in listOf(660f, 690f, 730f, 785f)) drawLine(Color.Black.dim(.4f), s.at(-1200f, fy), s.at(2800f, fy), s.len(1.5f))
    // Contact shadows.
    for (r in listOf(Rect(140f, 632f, 420f, 650f), Rect(470f, 632f, 780f, 652f), Rect(830f, 632f, 1130f, 654f), Rect(1195f, 632f, 1515f, 654f))) {
        drawOval(Color.Black.dim(.55f), s.at(r.left, r.top), Size(s.len(r.width), s.len(r.height)))
    }
}

private fun Float.px(scope: DrawScope): TextUnit = with(scope) { this@px.toSp() }

/** Conduits from every device to the companion terminal; shared clues travel along them. */
private fun cableRoute(id: String): Triple<Offset, Offset, Float>? = when (id) {
    "panel" -> Triple(Offset(280f, 575f), Offset(540f, 560f), 60f)
    "cabinet" -> Triple(Offset(900f, 640f), Offset(700f, 560f), 40f)
    "recorder" -> Triple(Offset(980f, 560f), Offset(700f, 560f), 70f)
    "door" -> Triple(Offset(1240f, 600f), Offset(730f, 560f), 70f)
    else -> null
}

private fun DrawScope.drawCables(f: Frame) {
    val s = f.s
    for (id in listOf("panel", "cabinet", "door")) {
        val (a, b, sag) = cableRoute(id) ?: continue
        val sharedNow = id in f.state.shared || (id == "cabinet" && "recorder" in f.state.shared)
        cable(s.at(a.x, a.y), s.at(b.x, b.y), s.len(sag), Color(0xFF09090B), s.len(9f))
        cable(s.at(a.x, a.y), s.at(b.x, b.y), s.len(sag), if (sharedNow) Ink.Amber.dim(.35f) else Ink.MetalDark, s.len(3f))
    }
}

private fun DrawScope.drawVent(f: Frame) {
    val s = f.s
    val r = Spots.vent
    metalPlate(s.rect(r.left, r.top, r.width, r.height), Ink.MetalDark, .6f)
    val fanCenter = s.at(r.left + 40f, r.center.y)
    val speed = when (f.state.systems.ventilation) { null -> .6f; VentilationMode.HOLD -> .2f; else -> 5f }
    rotate(f.time * speed * 57f, fanCenter) {
        repeat(4) { i ->
            rotate(i * 90f, fanCenter) {
                drawOval(Ink.MetalLight.dim(.8f), Offset(fanCenter.x - s.len(6f), fanCenter.y - s.len(30f)), Size(s.len(12f), s.len(28f)))
            }
        }
    }
    drawCircle(Ink.MetalDark, s.len(7f), fanCenter)
    grille(s.rect(r.left + 90f, r.top + 10f, r.width - 105f, r.height - 20f), 5)
    screwsAround(s.rect(r.left, r.top, r.width, r.height), s.len(3.5f), s.len(9f), .7f)
}

private fun DrawScope.drawPanel(f: Frame) {
    val s = f.s
    val r = Spots.panel
    val box = s.rect(r.left, r.top, r.width, r.height)
    metalPlate(box, Ink.Metal, 1f, s.len(8f))
    screwsAround(box, s.len(5f), s.len(14f))
    hazardStripes(s.rect(r.left + 20f, r.top + 20f, r.width - 40f, 22f), s.len(16f), Ink.Amber.dim(.4f))
    // Readout window.
    val screen = s.rect(r.left + 30f, r.top + 58f, r.width - 60f, 62f)
    drawRoundRect(Color(0xFF060607), screen.topLeft, screen.size, CornerRadius(s.len(4f)))
    // Three levers.
    repeat(3) { i ->
        val cx = r.left + 62f + i * 68f
        val slot = s.rect(cx - 13f, r.top + 150f, 26f, 150f)
        drawRoundRect(Color(0xFF08080A), slot.topLeft, slot.size, CornerRadius(s.len(13f)))
        val up = f.power
        val knobY = r.top + 275f - up * 100f
        drawLine(Ink.MetalLight, s.at(cx, r.top + 225f), s.at(cx, knobY), s.len(7f), StrokeCap.Round)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF4A4C53), Color(0xFF2A2B30)), s.y(knobY - 16f), s.y(knobY + 16f)),
            s.at(cx - 24f, knobY - 14f), Size(s.len(48f), s.len(28f)), CornerRadius(s.len(6f)))
        // Symbol plate under each lever: the glyph shows only once the panel has been inspected in the device.
        val plate = s.rect(cx - 26f, r.top + 312f, 52f, 34f)
        drawRoundRect(Color(0xFF0B0B0D), plate.topLeft, plate.size, CornerRadius(s.len(4f)))
    }
    // Main isolator switch.
    val iso = s.at(r.left + 205f, r.top + 322f)
    drawCircle(Ink.MetalDark, s.len(10f), iso)
}

private fun DrawScope.drawTerminal(f: Frame) {
    val s = f.s
    // Desk.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2B2C31), Color(0xFF1B1C20)), s.y(545f), s.y(570f)), s.at(470f, 545f), Size(s.len(310f), s.len(25f)))
    drawRect(Color(0xFF131417), s.at(490f, 570f), Size(s.len(20f), s.len(70f)))
    drawRect(Color(0xFF131417), s.at(740f, 570f), Size(s.len(20f), s.len(70f)))
    drawRect(Color(0xFF16171B), s.at(530f, 570f), Size(s.len(190f), s.len(50f)))
    grille(s.rect(545f, 580f, 160f, 30f), 3)
    // Monitor housing.
    val r = Spots.terminal
    metalPlate(s.rect(r.left, r.top, r.width, r.height), Color(0xFF222329), .8f, s.len(10f))
    drawRect(Color(0xFF1E1F24), s.at(600f, 535f), Size(s.len(40f), s.len(12f)))
    // Keyboard.
    drawRoundRect(Color(0xFF26272D), s.at(555f, 552f), Size(s.len(130f), s.len(9f)), CornerRadius(s.len(2f)))
}

private fun DrawScope.drawCabinet(f: Frame) {
    val s = f.s
    val r = Spots.cabinet
    val body = s.rect(r.left, r.top, r.width, r.height)
    metalPlate(body, Color(0xFF202126), .9f, s.len(6f))
    // Interior (visible as the doors swing).
    val inner = s.rect(r.left + 22f, r.top + 22f, r.width - 44f, r.height - 60f)
    drawRect(Color(0xFF09090B), inner.topLeft, inner.size)
    if (f.cabinetOpen > 0f) drawRecorder(f)
    // Two doors that fold outward, foreshortened as they open.
    val half = (r.width - 44f) / 2f
    val open = f.cabinetOpen
    val leafWidth = half * (1f - open * .82f)
    val leftLeaf = s.rect(r.left + 22f, r.top + 22f, leafWidth, r.height - 60f)
    val rightLeaf = s.rect(r.right - 22f - leafWidth, r.top + 22f, leafWidth, r.height - 60f)
    for (leaf in listOf(leftLeaf, rightLeaf)) {
        drawRect(Brush.horizontalGradient(listOf(Color(0xFF2C2D33), Color(0xFF1F2025)), leaf.left, leaf.right), leaf.topLeft, leaf.size)
        drawRect(Color.Black.dim(.5f), leaf.topLeft, leaf.size, style = Stroke(s.len(1.5f)))
        if (open < .5f) grille(Rect(leaf.left, leaf.bottom - s.len(90f), leaf.right, leaf.bottom - s.len(20f)), 4)
    }
    // Electronic lock and label when closed.
    if (open < .95f) {
        val a = 1f - open
        val lock = s.rect(r.center.x - 36f, r.top + 110f, 72f, 96f)
        drawRoundRect(Color(0xFF0B0B0D).dim(a), lock.topLeft, lock.size, CornerRadius(s.len(5f)))
        repeat(9) { k ->
            val kx = lock.left + s.len(14f) + (k % 3) * s.len(22f)
            val ky = lock.top + s.len(36f) + (k / 3) * s.len(18f)
            drawRoundRect(Ink.MetalLight.dim(.8f * a), Offset(kx, ky), Size(s.len(14f), s.len(11f)), CornerRadius(s.len(2f)))
        }
        val plate = s.rect(r.center.x - 70f, r.top + 58f, 140f, 34f)
        drawRoundRect(Color(0xFF15161A).dim(a), plate.topLeft, plate.size, CornerRadius(s.len(3f)))
    }
    // Decoder dial on the side.
    val dial = s.at(r.right - 34f, r.bottom - 22f)
    drawCircle(Ink.MetalDark, s.len(15f), dial)
    val shift = f.state.systems.decoderShift ?: 0
    rotate(-120f + shift * 48f, dial) { drawLine(Ink.MetalLight, dial, Offset(dial.x, dial.y - s.len(12f)), s.len(3f), StrokeCap.Round) }
}

private fun DrawScope.drawRecorder(f: Frame) {
    val s = f.s
    val r = Spots.recorder
    val a = f.cabinetOpen
    metalPlate(s.rect(r.left, r.top + 40f, r.width, 150f), Color(0xFF26272C), a)
    val spin = if (f.state.systems.recorderChannel != null && f.state.stage == EscapeStage.STORY) f.time * 120f else f.time * 8f
    for (cx in listOf(r.left + 55f, r.right - 55f)) {
        val c = s.at(cx, r.top + 105f)
        drawCircle(Color(0xFF0A0A0C).dim(a), s.len(34f), c)
        drawCircle(Ink.MetalLight.dim(.7f * a), s.len(34f), c, style = Stroke(s.len(3f)))
        rotate(spin, c) { repeat(3) { k -> rotate(k * 120f, c) { drawLine(Ink.MetalLight.dim(a), c, Offset(c.x, c.y - s.len(26f)), s.len(4f), StrokeCap.Round) } } }
        drawCircle(Ink.MetalDark.dim(a), s.len(8f), c)
    }
    drawLine(Color(0xFF3B2A18).dim(a), s.at(r.left + 55f, r.top + 139f), s.at(r.right - 55f, r.top + 139f), s.len(4f))
    // Memory strips on the shelf.
    repeat(3) { k ->
        val strip = s.rect(r.left + 12f + k * 64f, r.top + 205f, 56f, 36f)
        drawRoundRect(Color(0xFFD9D2C3).dim(.75f * a), strip.topLeft, strip.size, CornerRadius(s.len(2f)))
        drawLine(Color.Black.dim(.35f * a), Offset(strip.left + s.len(6f), strip.center.y), Offset(strip.right - s.len(6f), strip.center.y), s.len(2f))
    }
}

private fun DrawScope.drawDoor(f: Frame) {
    val s = f.s
    val r = Spots.door
    // Frame.
    metalPlate(s.rect(r.left, r.top, r.width, r.height), Color(0xFF1D1E23), .9f, s.len(10f))
    hazardStripes(s.rect(r.left + 8f, r.top + 8f, r.width - 16f, 26f), s.len(20f), Ink.Amber.dim(.45f))
    val opening = s.rect(r.left + 30f, r.top + 50f, r.width - 60f, r.height - 50f)
    // Light beyond the door.
    if (f.doorOpen > 0f) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF1D6), Color(0xFFF3C98A)), opening.top, opening.bottom).let { it }, opening.topLeft, opening.size, alpha = f.doorOpen)
    } else drawRect(Color.Black, opening.topLeft, opening.size)
    // The leaf slides into the wall as it opens.
    clipRect(opening.left, opening.top, opening.right, opening.bottom) {
        val slide = opening.width * f.doorOpen
        val leaf = Rect(opening.left + slide, opening.top, opening.right + slide, opening.bottom)
        drawRect(Brush.horizontalGradient(listOf(Color(0xFF33353B), Color(0xFF26272C), Color(0xFF2E3035)), leaf.left, leaf.right), leaf.topLeft, leaf.size)
        drawRect(Color.Black.dim(.5f), leaf.topLeft, leaf.size, style = Stroke(s.len(2f)))
        for (k in 1..3) {
            val y = leaf.top + leaf.height * k / 4f
            drawLine(Color.Black.dim(.35f), Offset(leaf.left + s.len(10f), y), Offset(leaf.right - s.len(10f), y), s.len(2f))
        }
        // Porthole.
        val port = Offset(leaf.center.x, leaf.top + s.len(90f))
        drawCircle(Ink.MetalDark, s.len(40f), port)
        drawCircle(Color(0xFF0A0B0D), s.len(30f), port)
        drawLine(Color.White.dim(.08f), Offset(port.x - s.len(16f), port.y - s.len(10f)), Offset(port.x - s.len(4f), port.y - s.len(22f)), s.len(3f), StrokeCap.Round)
        // Wheel handle.
        val wheel = Offset(leaf.center.x, leaf.center.y + s.len(40f))
        val turn = f.doorOpen * 270f + if (f.kind == "ARM_FAIL") sin(f.fx * 40f) * 8f * (1f - f.fx) else 0f
        rotate(turn, wheel) {
            drawCircle(Ink.MetalLight, s.len(44f), wheel, style = Stroke(s.len(7f)))
            repeat(4) { k -> rotate(k * 45f, wheel) { drawLine(Ink.MetalLight, Offset(wheel.x - s.len(44f), wheel.y), Offset(wheel.x + s.len(44f), wheel.y), s.len(5f)) } }
            drawCircle(Ink.MetalDark, s.len(12f), wheel)
        }
        // Seal plate.
        val plate = Rect(leaf.center.x - s.len(38f), leaf.bottom - s.len(120f), leaf.center.x + s.len(38f), leaf.bottom - s.len(70f))
        drawRoundRect(Color(0xFF0C0C0E), plate.topLeft, plate.size, CornerRadius(s.len(4f)))
    }
}

/** Global darkness, lifted by power and lighting mode. Emissive things are drawn after this. */
private fun DrawScope.drawDarkness(f: Frame) {
    val s = f.s
    drawRect(Color.Black.dim((1f - f.lit).coerceIn(0f, .92f)))
    if (f.state.systems.lighting == LightingMode.ULTRAVIOLET) drawRect(Ink.Uv.dim(.06f))
    if (f.state.systems.lighting == LightingMode.EMERGENCY && f.power < 1f) {
        val pulse = .5f + .5f * sin(f.time * 2.4f)
        drawRect(Color(0xFF6B1F14).dim(.06f + .05f * pulse * (1f - f.power)))
    }
}

private fun DrawScope.drawEmissive(f: Frame) {
    val s = f.s
    val flick = flicker(f.time, 3)
    // Ceiling lamp.
    val lamp = s.at(800f, 72f)
    drawRoundRect(Color(0xFF26272C), s.at(700f, 62f), Size(s.len(200f), s.len(18f)), CornerRadius(s.len(4f)))
    val lampStrength = when {
        f.state.cinematic == Cinematic.TRAPPED -> (1f - f.state.cinematicProgress * 1.4f).coerceAtLeast(0f) * flicker(f.time * 3f, 9)
        f.state.outcome == "FAILED" -> 0f
        else -> (.25f + f.power * .75f + f.state.ambientBoost * .5f).coerceAtMost(1f) * (if (f.power < 1f) flick else 1f)
    }
    drawRoundRect(f.lightColor.dim(.35f + .65f * lampStrength), s.at(712f, 76f), Size(s.len(176f), s.len(6f)), CornerRadius(s.len(3f)))
    lightCone(lamp, s.len(90f), s.len(520f), s.y(Spots.FLOOR + 40f), f.lightColor, lampStrength * .9f)
    glow(lamp, s.len(260f), f.lightColor, lampStrength * .7f)
    // Emergency beacon above the door.
    val beacon = s.at(1355f, 102f)
    val beaconOn = f.state.outcome != "ESCAPED"
    drawRoundRect(Color(0xFF301410), s.at(1335f, 92f), Size(s.len(40f), s.len(20f)), CornerRadius(s.len(6f)))
    if (beaconOn) {
        val sweep = (sin(f.time * (if (f.state.cinematic == Cinematic.TRAPPED) 9f else 3.2f)) * .5f + .5f)
        val beaconColor = if (f.state.stage == EscapeStage.EXIT && f.state.armSeconds > 0) Ink.Amber else Ink.Red
        drawRoundRect(beaconColor.dim(.4f + sweep * .6f), s.at(1339f, 95f), Size(s.len(32f), s.len(13f)), CornerRadius(s.len(5f)))
        glow(beacon, s.len(110f), beaconColor, .3f + sweep * .5f)
    }
    // Panel readout: remote supply mode and lever lamps.
    val p = Spots.panel
    val screen = s.rect(p.left + 30f, p.top + 58f, p.width - 60f, 62f)
    val mode = f.state.systems.power
    val readout = when {
        f.power >= 1f -> "ONLINE"
        mode != null -> "SUPPLY $mode"
        else -> "NO SUPPLY"
    }
    val readoutColor = if (f.power >= 1f) Ink.Amber else if (mode != null) Ink.Amber.dim(.85f) else Ink.Red.dim(.4f + .5f * flick)
    drawText(f.text, readout, Offset(screen.left + s.len(14f), screen.top + s.len(18f)),
        TextStyle(color = readoutColor, fontSize = s.len(22f).px(this), fontFamily = Type.mono, fontWeight = FontWeight.Medium))
    scanlines(screen, s.len(4f), .35f)
    glow(screen.center, s.len(120f), readoutColor, .25f)
    repeat(3) { i ->
        val cx = p.left + 62f + i * 68f
        drawCircle(if (f.power >= 1f) Ink.Amber else Color(0xFF3A1A14), s.len(5f), s.at(cx, p.top + 140f))
    }
    // Terminal screen: the companion's presence in the room.
    val t = Spots.terminal
    val glass = s.rect(t.left + 14f, t.top + 14f, t.width - 28f, t.height - 34f)
    drawRoundRect(Color(0xFF07090A), glass.topLeft, glass.size, CornerRadius(s.len(6f)))
    val screenColor = if (f.state.systems.lighting == LightingMode.ULTRAVIOLET) Ink.Uv else Ink.Amber
    val active = f.state.companionBusy
    val wave = Path()
    val steps = 40
    repeat(steps + 1) { k ->
        val u = k / steps.toFloat()
        val amp = if (active) (.35f + .65f * sin(f.time * 5f + u * 9f).let { it * it }) else .08f
        val y = glass.center.y + sin(u * 18f + f.time * (if (active) 7f else 1.2f)) * glass.height * .3f * amp
        val x = glass.left + s.len(12f) + u * (glass.width - s.len(24f))
        if (k == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
    }
    drawPath(wave, screenColor.dim(if (active) .95f else .55f), style = Stroke(s.len(2.2f), cap = StrokeCap.Round))
    drawText(f.text, if (active) "COMPANION · THINKING" else "COMPANION", Offset(glass.left + s.len(10f), glass.top + s.len(6f)),
        TextStyle(color = screenColor.dim(.8f), fontSize = s.len(11f).px(this), fontFamily = Type.mono, letterSpacing = s.len(1.5f).px(this)))
    scanlines(glass, s.len(3f), .4f)
    glow(glass.center, s.len(180f), screenColor, if (active) .45f else .22f)
    // Cabinet lock and label light up once there is power.
    val c = Spots.cabinet
    if (f.cabinetOpen < .95f) {
        val a = f.power * (1f - f.cabinetOpen)
        val plate = s.rect(c.center.x - 70f, c.top + 58f, 140f, 34f)
        if (a > 0f) {
            drawRoundRect(Ink.Amber.dim(.18f * a), plate.topLeft, plate.size, CornerRadius(s.len(3f)))
            drawText(f.text, "ENCODED", Offset(plate.left + s.len(24f), plate.top + s.len(8f)),
                TextStyle(color = Ink.Amber.dim(a), fontSize = s.len(14f).px(this), fontFamily = Type.mono, letterSpacing = s.len(3f).px(this)))
            glow(plate.center, s.len(90f), Ink.Amber, .3f * a)
        }
        val lamp2 = s.at(c.center.x, c.top + 218f)
        drawCircle(if (f.state.stage >= EscapeStage.CABINET) Ink.Amber.dim(.5f + .5f * flick) else Color(0xFF3A1A14), s.len(5f), lamp2)
    }
    // Recorder channel lamps.
    if (f.cabinetOpen > .3f) {
        val r = Spots.recorder
        val channel = f.state.systems.recorderChannel
        "ABCDEF".forEachIndexed { k, ch ->
            val on = channel == ch.toString()
            drawCircle(if (on) Ink.Amber else Color(0xFF2A2B30), s.len(4f), s.at(r.left + 45f + k * 22f, r.top + 175f))
            if (on) glow(s.at(r.left + 45f + k * 22f, r.top + 175f), s.len(30f), Ink.Amber, .8f)
        }
    }
    // Door release lamp and countdown ring.
    val d = Spots.door
    val release = s.at(d.right - 16f, d.top + 300f)
    val armed = f.state.armSeconds > 0 && f.state.outcome == "IN_PROGRESS"
    drawCircle(if (armed) Ink.Amber else Color(0xFF3A1A14), s.len(7f), release)
    if (armed) {
        glow(release, s.len(60f), Ink.Amber, .9f)
        drawArc(Ink.Amber, -90f, 360f * f.state.armSeconds / 20f, false, Offset(release.x - s.len(14f), release.y - s.len(14f)),
            Size(s.len(28f), s.len(28f)), style = Stroke(s.len(2.5f), cap = StrokeCap.Round))
    }
    if (f.doorOpen > 0f) {
        val opening = s.rect(d.left + 30f, d.top + 50f, d.width - 60f, d.height - 50f)
        lightCone(Offset(opening.center.x, opening.top), opening.width / 2f, opening.width * 1.6f, s.y(820f), Color(0xFFFFE7BF), f.doorOpen)
        glow(opening.center, s.len(520f), Color(0xFFFFE2B0), f.doorOpen)
    }
    // Ultraviolet writing appears on the wall.
    if (f.state.systems.lighting == LightingMode.ULTRAVIOLET) {
        f.state.discoveries.firstOrNull()?.let { discovery ->
            drawText(f.text, discovery.uppercase(), s.at(170f, 160f), TextStyle(color = Ink.Uv.dim(.75f + .2f * sin(f.time)),
                fontSize = s.len(18f).px(this), fontFamily = Type.mono, letterSpacing = s.len(2f).px(this)),
                size = Size(s.len(620f), s.len(60f)))
        }
    }
}

private fun DrawScope.drawAtmosphere(f: Frame) {
    val s = f.s
    val vent = f.state.systems.ventilation
    val dread = if (f.state.cinematic == Cinematic.TRAPPED) 1f + 3f * f.state.cinematicProgress else 1f
    smoke(s.at(760f, 640f), s.len(700f), s.len(420f), f.time, (if (vent == null) 1f else .2f) * dread)
    dust(s.rect(560f, 90f, 480f, 560f), 34, f.time, f.lightColor, f.lit)
}

/** Hover and selection outlines with a floating name tag. */
private fun DrawScope.drawFocus(f: Frame) {
    val s = f.s
    fun rectFor(id: String) = when (id) {
        "panel" -> Spots.panel
        "cabinet", "cabinet-locked" -> Spots.cabinet
        "recorder" -> Spots.recorder
        "door" -> Spots.door
        else -> null
    }
    val selected = f.state.selected?.let(::rectFor)
    if (selected != null) {
        val r = s.rect(selected.left - 10f, selected.top - 10f, selected.width + 20f, selected.height + 20f)
        dashedRect(r, Ink.Amber.dim(.9f), s.len(2f), -f.time * 30f, s.len(10f))
    }
    val hoverId = f.hovered ?: return
    val hover = rectFor(hoverId) ?: return
    val locked = hoverId == "cabinet-locked"
    val r = s.rect(hover.left - 6f, hover.top - 6f, hover.width + 12f, hover.height + 12f)
    drawRoundRect((if (locked) Ink.Faint else Ink.Text).dim(.6f), r.topLeft, r.size, CornerRadius(s.len(8f)), style = Stroke(s.len(1.5f)))
    val name = if (locked) "LOCKED · NO POWER" else labels[hoverId].orEmpty()
    val style = TextStyle(color = if (locked) Ink.Dim else Ink.Ground, fontSize = s.len(13f).coerceAtLeast(9f).px(this), fontFamily = Type.mono,
        fontWeight = FontWeight.Medium, letterSpacing = 1.5f.px(this))
    val layout = f.text.measure(name, style)
    val pad = s.len(10f).coerceAtLeast(6f)
    val tag = Rect(r.center.x - layout.size.width / 2f - pad, r.top - layout.size.height - pad * 2.2f,
        r.center.x + layout.size.width / 2f + pad, r.top - pad * .6f)
    drawRoundRect(if (locked) Ink.Raised else Ink.Amber, tag.topLeft, tag.size, CornerRadius(s.len(4f)))
    drawText(layout, topLeft = Offset(tag.left + pad, tag.top + pad * .6f))
}

private fun DrawScope.drawEffects(f: Frame) {
    val s = f.s
    val kind = f.kind ?: return
    val progress = f.fx
    val anchor = when (f.target) {
        "panel" -> s.at(Spots.panel.center.x, Spots.panel.top + 230f)
        "cabinet" -> s.at(Spots.cabinet.center.x, Spots.cabinet.top + 150f)
        "recorder" -> s.at(Spots.recorder.center.x, Spots.recorder.top + 105f)
        "door" -> s.at(Spots.door.center.x, Spots.door.center.y)
        "vent" -> s.at(Spots.vent.center.x, Spots.vent.center.y)
        else -> s.at(800f, 360f)
    }
    when {
        kind == "SHARE" -> {
            val route = cableRoute(f.target ?: "") ?: return
            val (a, b, sag) = route
            repeat(3) { k ->
                val t = (progress * 1.3f - k * .12f).coerceIn(0f, 1f)
                if (t in .001f..0.999f) {
                    val pt = cablePoint(s.at(a.x, a.y), s.at(b.x, b.y), s.len(sag), t)
                    drawCircle(Ink.Amber.dim(1f - k * .3f), s.len(6f - k * 1.5f), pt)
                    glow(pt, s.len(40f), Ink.Amber, .8f - k * .2f)
                }
            }
            if (progress > .75f) glow(s.at(Spots.terminal.center.x, Spots.terminal.center.y), s.len(200f), Ink.Amber, (1f - progress) * 4f * .6f)
        }
        f.failure -> {
            sparks(anchor, progress, s.len(120f), Ink.Amber)
            drawRect(Ink.Red.dim(.22f * (1f - progress)))
        }
        kind == "POWER_ON" -> drawRect(Color(0xFFFFF4E0).dim(.5f * (1f - progress) * (1f - progress)))
        kind == "TIMEOUT" -> drawRect(Ink.Red.dim(.3f * (1f - progress)))
        kind == "ESCAPE" -> drawRect(Color(0xFFFFF0D0).dim(.35f * (1f - progress)))
        else -> {
            val ring = s.len(40f + 180f * progress)
            drawCircle(Ink.Amber.dim(.7f * (1f - progress)), ring, anchor, style = Stroke(s.len(3f)))
            glow(anchor, s.len(160f), Ink.Amber, .6f * (1f - progress))
        }
    }
}
