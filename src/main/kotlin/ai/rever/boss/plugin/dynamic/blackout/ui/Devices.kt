package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.dynamic.blackout.application.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Callbacks for every physical action the human can take at a device. */
class DeviceActions(
    val onShare: () -> Unit,
    val onPower: (List<String>) -> Unit,
    val onPassword: (String) -> Unit,
    val onStory: (List<String>) -> Unit,
    val onExit: () -> Unit,
    val onSelect: (String) -> Unit
)

private val objectOrder = listOf("panel", "cabinet", "recorder", "door")

/** The close-up of whatever the human is examining. One device on screen at a time. */
@Composable
fun DeviceDock(view: EscapePilotView, selected: String?, actions: DeviceActions, modifier: Modifier = Modifier) {
    val obj = view.objects.find { it.id == selected }
    Slab(modifier, tone = Ink.Surface) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceHeader(view, obj, actions)
            Hairline()
            AnimatedContent(
                targetState = selected to view.status.stage,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "device"
            ) { (id, stage) ->
                Box(Modifier.fillMaxSize()) {
                    val target = view.objects.find { it.id == id }
                    when {
                        target == null -> NothingSelected(view, actions.onSelect)
                        !target.available -> LockedDevice(target)
                        Clues.stageFor(target.id) < stage -> CompletedDevice(target, stage)
                        target.id == "door" && stage != EscapeStage.EXIT -> DoorWaiting()
                        target.id == "panel" -> BreakerDevice(view, actions.onPower)
                        target.id == "cabinet" -> CabinetDevice(view, actions.onPassword)
                        target.id == "recorder" -> RecorderDevice(view, actions.onStory)
                        else -> DoorDevice(view, actions.onExit)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceHeader(view: EscapePilotView, obj: RoomObject?, actions: DeviceActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Title(obj?.label?.uppercase() ?: "THE ROOM")
                if (obj != null && Clues.stageFor(obj.id) == view.status.stage) Tag("CURRENT", Ink.Amber, filled = true)
            }
            Label(if (obj == null) "Click an object in the room to look closer." else Clues.humanStep(Clues.stageFor(obj.id)), Ink.Dim)
        }
        objectOrder.forEach { id ->
            val o = view.objects.find { it.id == id } ?: return@forEach
            ObjectChip(o, id == obj?.id) { actions.onSelect(id) }
        }
        if (obj != null && obj.available && view.status.outcome == "IN_PROGRESS") {
            if (obj.shared) Tag("SHARED", Ink.Dim)
            else GameButton("SHARE CLUE", actions.onShare, kind = ButtonKind.SECONDARY, icon = Glyph.SHARE)
        }
    }
}

@Composable
private fun ObjectChip(obj: RoomObject, active: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val glyph = when (obj.id) { "panel" -> Glyph.BOLT; "cabinet" -> Glyph.DIAL; "recorder" -> Glyph.TAPE; else -> Glyph.HANDLE }
    val tint = when { active -> Ink.Amber; !obj.available -> Ink.Faint; hovered -> Ink.Text; else -> Ink.Dim }
    Box(
        Modifier.size(34.dp)
            .background(if (active) Ink.Amber.copy(alpha = .12f) else if (hovered) Ink.Raised else Color.Transparent, RoundedCornerShape(5.dp))
            .border(1.dp, if (focused) Ink.Text else if (active) Ink.Amber.copy(alpha = .5f) else Ink.Line, RoundedCornerShape(5.dp))
            .hoverable(source)
            .clickable(source, null, onClickLabel = obj.label, role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(if (obj.available) glyph else Glyph.LOCK, tint, 16.dp)
    }
}

@Composable
private fun NothingSelected(view: EscapePilotView, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Body("Start where the objective points.", Ink.Dim)
        Spacer(Modifier.height(12.dp))
        GameButton("GO TO ${Clues.title(view.status.stage)}", { onSelect(Clues.focusFor(view.status.stage)) }, kind = ButtonKind.PRIMARY)
    }
}

@Composable
private fun LockedDevice(obj: RoomObject) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        GlyphIcon(Glyph.LOCK, Ink.Faint, 40.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Title("LOCKED", Ink.Dim)
            Body(obj.description, Ink.Dim)
            Label("Finish the step before this one first.", Ink.Faint)
        }
    }
}

@Composable
private fun CompletedDevice(obj: RoomObject, stage: EscapeStage) {
    val line = when (obj.id) {
        "panel" -> "Breakers energized. Emergency power is holding."
        "cabinet" -> "The cabinet is open. The recorder is inside."
        "recorder" -> "Timeline rebuilt. The manual key is released."
        else -> "Done."
    }
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        GlyphIcon(Glyph.CHECK, Ink.Amber, 40.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Title("SOLVED", Ink.Amber)
            Body(line, Ink.Text)
            Label("Current objective: ${Clues.title(stage)}", Ink.Dim)
        }
    }
}

@Composable
private fun DoorWaiting() {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        GlyphIcon(Glyph.HANDLE, Ink.Faint, 40.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Title("SEALED", Ink.Dim)
            Body("Two isolated release circuits. Nothing moves until power, the cabinet and the recorder are done.", Ink.Dim)
        }
    }
}

/** Label and value on one line with a lamp, for a remote system the companion controls. */
@Composable
fun RemoteReadout(label: String, value: String?, modifier: Modifier = Modifier, waiting: String = "WAITING FOR COMPANION") {
    Row(
        modifier.background(Ink.Ground, RoundedCornerShape(5.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Lamp(value != null)
        Label(label, Ink.Dim, Modifier.weight(1f))
        if (value != null) Label(value, Ink.Amber) else WaitingDots(waiting)
    }
}

@Composable
fun WaitingDots(text: String) {
    val transition = rememberInfiniteTransition(label = "waiting")
    val phase by transition.animateFloat(0f, 3f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "dots")
    Label(text + ".".repeat(phase.toInt() + 1).padEnd(3, ' '), Ink.Faint)
}

// ---------------------------------------------------------------- Breakers

@Composable
private fun BreakerDevice(view: EscapePilotView, onPower: (List<String>) -> Unit) {
    val clue = Clues.panel(view)
    var order by remember(view.status.roomId) { mutableStateOf<List<String>>(emptyList()) }
    val routed = view.status.systems.power
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(280.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Meter("LOAD", clue.watts, "W", 400, Modifier.weight(1f))
                Meter("SUPPLY", clue.volts, "V", 60, Modifier.weight(1f))
            }
            SupplyModes(routed)
            if (clue.watts == null) SelectionContainer { Body(view.objects.first { it.id == "panel" }.description, Ink.Dim) }
        }
        VerticalHairline()
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(
                    when {
                        routed == null -> "WAITING FOR REMOTE SUPPLY · SHARE THE PANEL"
                        order.size < 3 -> "SUPPLY $routed · FLIP THE BREAKERS IN ORDER"
                        else -> "READY · A WRONG ORDER TRIPS THE PANEL"
                    },
                    if (routed == null) Ink.Faint else Ink.Dim, Modifier.weight(1f)
                )
                GameButton("ENERGIZE", { onPower(order); order = emptyList() }, kind = ButtonKind.PRIMARY,
                    enabled = order.size == 3 && routed != null, icon = Glyph.BOLT, height = 34.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                clue.symbols.forEach { symbol ->
                    val position = order.indexOf(symbol)
                    Lever(symbol, position, enabled = position < 0 && view.status.outcome == "IN_PROGRESS") { order = order + symbol }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i -> SequenceSlot(i + 1, order.getOrNull(i)) }
                Spacer(Modifier.width(4.dp))
                GameButton("RESET", { order = emptyList() }, kind = ButtonKind.QUIET, enabled = order.isNotEmpty(), icon = Glyph.RESET)
            }
        }
    }
}

@Composable
private fun Meter(label: String, value: Int?, unit: String, max: Int, modifier: Modifier = Modifier) {
    val target = ((value ?: 0).toFloat() / max).coerceIn(0f, 1f)
    val needle by animateFloatAsState(target, spring(dampingRatio = .45f, stiffness = 60f), label = "needle")
    Column(modifier.background(Ink.Ground, RoundedCornerShape(6.dp)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val c = Offset(size.width / 2f, size.height * .95f)
            val r = minOf(size.width / 2f, size.height) * .9f
            drawArc(Ink.Line, 180f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(3f))
            drawArc(Ink.Amber.copy(alpha = .55f), 180f, 180f * needle, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(3f))
            repeat(9) { k ->
                val a = PI.toFloat() * (1f + k / 8f)
                drawLine(Ink.Faint, Offset(c.x + cos(a) * r * .82f, c.y + sin(a) * r * .82f), Offset(c.x + cos(a) * r * .95f, c.y + sin(a) * r * .95f), 1.5f)
            }
            val a = PI.toFloat() * (1f + needle)
            drawLine(if (value == null) Ink.Faint else Ink.Text, c, Offset(c.x + cos(a) * r * .8f, c.y + sin(a) * r * .8f), 2.5f, StrokeCap.Round)
            drawCircle(Ink.Edge, 4f, c)
        }
        Title(if (value == null) "—" else "$value $unit", if (value == null) Ink.Faint else Ink.Text)
        Label(label, Ink.Faint)
    }
}

@Composable
private fun SupplyModes(routed: PowerMode?) {
    Column(Modifier.fillMaxWidth().background(Ink.Ground, RoundedCornerShape(6.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("REMOTE SUPPLY", Ink.Dim, Modifier.weight(1f))
            if (routed == null) WaitingDots("COMPANION") else Label("SET BY COMPANION", Ink.Faint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PowerMode.entries.forEach { mode ->
                val on = mode == routed
                Row(
                    Modifier.weight(1f)
                        .background(if (on) Ink.Amber.copy(alpha = .14f) else Color.Transparent, RoundedCornerShape(4.dp))
                        .border(1.dp, if (on) Ink.Amber.copy(alpha = .6f) else Ink.Line, RoundedCornerShape(4.dp))
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Lamp(on); Spacer(Modifier.width(6.dp)); Label(mode.name, if (on) Ink.Amber else Ink.Faint)
                }
            }
        }
    }
}

@Composable
private fun Lever(symbol: String, position: Int, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val thrown by animateFloatAsState(if (position >= 0) 1f else 0f, spring(dampingRatio = .5f, stiffness = 400f), label = "lever")
    val tint = if (position >= 0) Ink.Amber else if (hovered) Ink.Text else Ink.Dim
    Column(
        Modifier.width(92.dp)
            .background(if (hovered && enabled) Ink.Raised else Ink.Ground, RoundedCornerShape(6.dp))
            .border(1.dp, if (focused) Ink.Text else if (position >= 0) Ink.Amber.copy(alpha = .5f) else Ink.Line, RoundedCornerShape(6.dp))
            .hoverable(source, enabled)
            .clickable(source, null, enabled, onClickLabel = "Flip $symbol breaker", role = Role.Button, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(Modifier.width(36.dp).height(46.dp)) { drawLever(thrown, tint) }
        SymbolIcon(symbol, tint, 22.dp)
        Label(if (position >= 0) "${position + 1} · $symbol" else symbol, tint)
    }
}

private fun DrawScope.drawLever(thrown: Float, tint: Color) {
    val slotWidth = size.width * .36f
    drawRoundRect(Color(0xFF060607), Offset((size.width - slotWidth) / 2f, 0f), Size(slotWidth, size.height),
        androidx.compose.ui.geometry.CornerRadius(slotWidth / 2f))
    val pivot = Offset(size.width / 2f, size.height * .5f)
    val knobY = size.height * (.2f + .6f * thrown)
    drawLine(Ink.MetalLight, pivot, Offset(size.width / 2f, knobY), 4f, StrokeCap.Round)
    drawRoundRect(tint, Offset(size.width * .1f, knobY - 8f), Size(size.width * .8f, 16f), androidx.compose.ui.geometry.CornerRadius(5f))
    drawCircle(Ink.MetalDark, 4f, pivot)
}

@Composable
private fun SequenceSlot(number: Int, symbol: String?) {
    Row(
        Modifier.height(32.dp).widthIn(min = 96.dp)
            .border(1.dp, if (symbol != null) Ink.Amber.copy(alpha = .6f) else Ink.Line, RoundedCornerShape(5.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Label("$number", Ink.Faint)
        if (symbol != null) { SymbolIcon(symbol, Ink.Amber, 16.dp); Label(symbol, Ink.Text) } else Label("—", Ink.Faint)
    }
}

// ---------------------------------------------------------------- Cabinet

@Composable
private fun CabinetDevice(view: EscapePilotView, onPassword: (String) -> Unit) {
    val clue = Clues.cabinet(view)
    val shift = view.status.systems.decoderShift
    var word by remember(view.status.roomId) { mutableStateOf("") }
    fun submit() { if (word.isNotBlank() && shift != null) { onPassword(word); word = "" } }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("ENCODED LABEL", Ink.Dim)
            if (clue.cipher != null) LetterTiles(clue.cipher, Ink.Amber)
            else SelectionContainer { Body(view.objects.first { it.id == "cabinet" }.description, Ink.Dim) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DecoderDial(shift, Modifier.size(76.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label("DECODER OFFSET", Ink.Dim)
                    if (shift == null) WaitingDots("COMPANION") else Title("SHIFT $shift", Ink.Amber)
                    Label("Move each letter back by the offset.", Ink.Faint)
                }
            }
        }
        VerticalHairline()
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(if (shift == null) "KEYPAD DARK · WAITING FOR THE DECODER" else "TYPE THE DECODED WORD · ENTER TO UNLOCK",
                    if (shift == null) Ink.Faint else Ink.Dim, Modifier.weight(1f))
                GameButton("UNLOCK", ::submit, kind = ButtonKind.PRIMARY, enabled = word.isNotBlank() && shift != null, icon = Glyph.LOCK, height = 34.dp)
            }
            PasswordDisplay(word, enabled = view.status.outcome == "IN_PROGRESS", onChange = { word = it }, onSubmit = ::submit)
            Keypad(
                onKey = { if (word.length < 12) word += it },
                onBack = { word = word.dropLast(1) },
                onClear = { word = "" }
            )
        }
    }
}

@Composable
fun LetterTiles(word: String, color: Color, placeholder: Int = 0) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        val count = maxOf(word.length, placeholder)
        repeat(count) { i ->
            val ch = word.getOrNull(i)
            Box(
                Modifier.size(width = 30.dp, height = 38.dp)
                    .background(Ink.Ground, RoundedCornerShape(4.dp))
                    .border(1.dp, if (ch != null) color.copy(alpha = .55f) else Ink.Line, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material.Text(ch?.toString() ?: "", style = TextStyle(color = color, fontSize = 18.sp, fontFamily = Type.mono, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun DecoderDial(shift: Int?, modifier: Modifier) {
    val angle by animateFloatAsState(if (shift == null) -150f else -150f + (shift - 1) * 75f, spring(dampingRatio = .55f, stiffness = 80f), label = "dial")
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Ink.Ground, r, c)
        drawCircle(Ink.Line, r, c, style = Stroke(2f))
        for (k in 1..5) {
            val a = Math.toRadians((-150.0 + (k - 1) * 75.0) - 90.0).toFloat()
            val on = shift == k
            drawCircle(if (on) Ink.Amber else Ink.Faint, if (on) 4.5f else 3f, Offset(c.x + cos(a) * r * .78f, c.y + sin(a) * r * .78f))
        }
        rotate(angle, c) {
            drawCircle(Ink.MetalLight, r * .5f, c)
            drawCircle(Ink.MetalDark, r * .5f, c, style = Stroke(2f))
            drawLine(if (shift == null) Ink.Faint else Ink.Amber, c, Offset(c.x, c.y - r * .46f), 3.5f, StrokeCap.Round)
        }
    }
}

@Composable
private fun PasswordDisplay(word: String, enabled: Boolean, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = word,
        onValueChange = { raw -> val clean = raw.filter(Char::isLetter).uppercase().take(12); onChange(clean) },
        enabled = enabled,
        singleLine = true,
        interactionSource = source,
        cursorBrush = SolidColor(Ink.Amber),
        textStyle = TextStyle(color = Ink.Text, fontSize = 18.sp, fontFamily = Type.mono, letterSpacing = 6.sp),
        modifier = Modifier.fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) { onSubmit(); true } else false
            },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(40.dp)
                    .background(Ink.Ground, RoundedCornerShape(6.dp))
                    .border(1.dp, if (focused) Ink.Amber.copy(alpha = .7f) else Ink.Line, RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (word.isEmpty()) Label("TYPE OR TAP LETTERS", Ink.Faint)
                    inner()
                }
                Label("${word.length}/12", Ink.Faint)
            }
        }
    )
}

@Composable
private fun Keypad(onKey: (String) -> Unit, onBack: () -> Unit, onClear: () -> Unit) {
    val rows = ('A'..'Z').map { it.toString() }.chunked(9)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { letter -> KeyCap(letter) { onKey(letter) } }
                if (index == rows.lastIndex) {
                    KeyCap("⌫", wide = true, onClick = onBack)
                    KeyCap("CLR", wide = true, onClick = onClear)
                }
            }
        }
    }
}

@Composable
private fun KeyCap(text: String, wide: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier.size(width = if (wide) 58.dp else 32.dp, height = 24.dp)
            .background(if (hovered) Ink.Raised else Ink.Ground, RoundedCornerShape(4.dp))
            .border(1.dp, if (hovered) Ink.Edge else Ink.Line, RoundedCornerShape(4.dp))
            .hoverable(source)
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) { Label(text, if (hovered) Ink.Text else Ink.Dim, align = TextAlign.Center) }
}

// ---------------------------------------------------------------- Recorder

@Composable
private fun RecorderDevice(view: EscapePilotView, onStory: (List<String>) -> Unit) {
    val clue = Clues.recorder(view)
    val channel = view.status.systems.recorderChannel
    var order by remember(view.status.roomId) { mutableStateOf<List<String>>(emptyList()) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(250.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Label("WAVEFORM", Ink.Dim)
            Row(Modifier.fillMaxWidth().background(Ink.Ground, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (clue.waveform != null) SymbolIcon(clue.waveform, Ink.Amber, 32.dp) else GlyphIcon(Glyph.TAPE, Ink.Faint, 32.dp)
                Column {
                    Title(clue.waveform ?: "—", if (clue.waveform != null) Ink.Text else Ink.Faint)
                    Label("PATTERN ON THE READER", Ink.Faint)
                }
            }
            Label("RECORDER CHANNEL", Ink.Dim)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                "ABCDEF".forEach { ch ->
                    val on = channel == ch.toString()
                    Box(Modifier.size(32.dp).background(if (on) Ink.Amber.copy(alpha = .16f) else Ink.Ground, RoundedCornerShape(4.dp))
                        .border(1.dp, if (on) Ink.Amber else Ink.Line, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        Label(ch.toString(), if (on) Ink.Amber else Ink.Faint)
                    }
                }
            }
            if (channel == null) WaitingDots("WAITING FOR SYNC") else Label("SYNCHRONIZED ON $channel", Ink.Amber)
        }
        VerticalHairline()
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(if (channel == null) "PLAYBACK LOCKED · WAITING FOR SYNC" else "CAUSE BEFORE EFFECT · CLICK A SLOT TO UNDO",
                    if (channel == null) Ink.Faint else Ink.Dim, Modifier.weight(1f))
                GameButton("RESET", { order = emptyList() }, kind = ButtonKind.QUIET, enabled = order.isNotEmpty(), icon = Glyph.RESET, height = 34.dp)
                GameButton("PLAY BACK", { onStory(order); order = emptyList() }, kind = ButtonKind.PRIMARY,
                    enabled = order.size == 3 && channel != null, icon = Glyph.PLAY, height = 34.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    val id = order.getOrNull(i)
                    TimelineSlot(i + 1, id, Modifier.weight(1f)) { if (id != null) order = order - id }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                clue.strips.keys.sorted().forEach { id ->
                    StripCard(id, clue.strips[id].orEmpty(), placed = id in order, enabled = id !in order && order.size < 3) { order = order + id }
                }
                if (clue.strips.isEmpty()) Body("Inspect the recorder to read the strips.", Ink.Faint)
            }
        }
    }
}

@Composable
private fun TimelineSlot(number: Int, id: String?, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier.height(32.dp)
            .background(if (id != null && hovered) Ink.Raised else Ink.Ground, RoundedCornerShape(5.dp))
            .border(1.dp, if (id != null) Ink.Amber.copy(alpha = .6f) else Ink.Line, RoundedCornerShape(5.dp))
            .hoverable(source)
            .clickable(source, null, enabled = id != null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Label(listOf("FIRST", "THEN", "FINALLY")[number - 1], Ink.Faint)
        Label(id?.let { "STRIP $it" } ?: "—", if (id != null) Ink.Amber else Ink.Faint)
    }
}

@Composable
private fun StripCard(id: String, text: String, placed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth()
            .background(if (hovered && enabled) Ink.Raised else Ink.Ground, RoundedCornerShape(5.dp))
            .border(1.dp, if (focused) Ink.Text else Ink.Line, RoundedCornerShape(5.dp))
            .hoverable(source, enabled)
            .clickable(source, null, enabled, onClickLabel = "Place strip $id", role = Role.Button, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(22.dp).background(if (placed) Ink.Line else Ink.Amber.copy(alpha = .15f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            Label(id, if (placed) Ink.Faint else Ink.Amber)
        }
        androidx.compose.material.Text(text, Modifier.weight(1f), style = bodyStyle(if (placed) Ink.Faint else Ink.Text), maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- Door

@Composable
private fun DoorDevice(view: EscapePilotView, onExit: () -> Unit) {
    val clue = Clues.door(view)
    val armed = view.status.armSeconds > 0
    val transition = rememberInfiniteTransition(label = "handle")
    val pulse by transition.animateFloat(.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(220.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("ROUTING SEAL", Ink.Dim)
            Box(Modifier.size(84.dp).background(Ink.Ground, RoundedCornerShape(10.dp)).border(1.dp, Ink.Line, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                if (clue.seal != null) SymbolIcon(clue.seal, Ink.Amber, 50.dp) else GlyphIcon(Glyph.LOCK, Ink.Faint, 32.dp)
            }
            Title(clue.seal ?: "—", if (clue.seal != null) Ink.Text else Ink.Faint)
        }
        VerticalHairline()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ReleaseRing(view.status.armSeconds, Modifier.size(64.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label("REMOTE RELEASE", Ink.Dim)
                    if (armed) Display("ARMED · ${view.status.armSeconds}s", Ink.Amber) else WaitingDots("WAITING FOR COMPANION")
                    Label(if (armed) "Turn the handle before the ring empties." else "Share the seal. Your companion arms the matching channel.", Ink.Faint)
                }
            }
            Box(Modifier.background(if (armed) Ink.Amber.copy(alpha = .12f * pulse) else Color.Transparent, RoundedCornerShape(8.dp)).padding(6.dp)) {
                GameButton("TURN THE HANDLE", onExit, kind = ButtonKind.PRIMARY, enabled = armed && !view.status.paused,
                    icon = Glyph.HANDLE, height = 46.dp, modifier = Modifier.widthIn(min = 260.dp))
            }
            if (view.status.paused) Label("Resume the clock before the final release.", Ink.Red)
        }
    }
}

@Composable
private fun ReleaseRing(seconds: Int, modifier: Modifier) {
    val fraction by animateFloatAsState(seconds / 20f, tween(900, easing = LinearEasing), label = "ring")
    Canvas(modifier) {
        val stroke = 6f
        val inset = stroke / 2f
        drawArc(Ink.Line, 0f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
        drawArc(Ink.Amber, -90f, 360f * fraction, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        drawGlyph(Glyph.HANDLE, if (seconds > 0) Ink.Amber else Ink.Faint, Offset(size.width * .3f, size.height * .3f), size.width * .4f)
    }
}
