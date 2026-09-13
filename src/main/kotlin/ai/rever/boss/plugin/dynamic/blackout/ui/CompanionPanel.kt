package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.dynamic.blackout.application.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** The companion's side of the room: who it is, what it controls, and the conversation. */
@Composable
fun CompanionPanel(
    view: EscapePilotView,
    crew: CrewStatus,
    attachedExternal: Boolean,
    reducedMotion: Boolean,
    onSend: (String) -> Unit,
    onSetup: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val live = view.status.outcome == "IN_PROGRESS"
    Slab(modifier, tone = Ink.Surface) {
        Column(Modifier.fillMaxSize()) {
            CompanionHeader(crew, attachedExternal, reducedMotion, onSetup)
            Hairline()
            SystemsGrid(view.status)
            Hairline()
            Conversation(view, crew, attachedExternal, Modifier.weight(1f))
            val working = crew.busy || (live && view.agentIdleSeconds in 0..3)
            AnimatedVisibility(working, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                TypingIndicator(if (crew.busy) crew.status else "Companion is working")
            }
            if (live) {
                QuickReplies(view.status.stage, onSend)
                Composer(view.status.roomId, onSend)
            }
            CompanionFooter(crew, onRetry)
        }
    }
}

@Composable
private fun CompanionHeader(crew: CrewStatus, attachedExternal: Boolean, reducedMotion: Boolean, onSetup: () -> Unit) {
    val connected = crew.enabled || attachedExternal
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CompanionOrb(connected, crew.busy, reducedMotion, Modifier.size(44.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Title("COMPANION", if (connected) Ink.Text else Ink.Dim)
            Label(
                when {
                    crew.enabled -> crew.label
                    attachedExternal -> "External agent over MCP"
                    else -> "Not connected"
                },
                if (connected) Ink.Dim else Ink.Faint
            )
        }
        GameButton(if (connected) "LINK" else "CONNECT", onSetup, kind = if (connected) ButtonKind.QUIET else ButtonKind.SECONDARY, icon = Glyph.LINK, height = 32.dp)
    }
}

/** A ring of light that breathes while idle and ripples while the model is working. */
@Composable
fun CompanionOrb(connected: Boolean, busy: Boolean, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val time by rememberAmbientTime(reducedMotion)
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        val color = if (connected) Ink.Amber else Ink.Faint
        val breathe = .5f + .5f * sin(time * 1.6f)
        drawCircle(Brush.radialGradient(listOf(color.dim(.35f + .2f * breathe), Color.Transparent), c, r), r, c)
        drawCircle(Ink.Ground, r * .62f, c)
        drawCircle(color.dim(.7f), r * .62f, c, style = Stroke(1.5f))
        if (busy) {
            repeat(3) { k ->
                val phase = ((time * .9f + k / 3f) % 1f)
                drawCircle(color.dim((1f - phase) * .6f), r * (.62f + phase * .38f), c, style = Stroke(1.2f))
            }
        }
        val wave = Path()
        val steps = 24
        repeat(steps + 1) { k ->
            val u = k / steps.toFloat()
            val amp = if (busy) .22f else if (connected) .07f else .0f
            val y = c.y + sin(u * PI.toFloat() * 3f + time * (if (busy) 6f else 1.5f)) * r * amp * sin(u * PI.toFloat())
            val x = c.x - r * .42f + u * r * .84f
            if (k == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
        }
        drawPath(wave, color, style = Stroke(1.8f, cap = StrokeCap.Round))
    }
}

/** Everything the companion can operate, as five readouts. */
@Composable
private fun SystemsGrid(status: EscapePublic) {
    val systems = status.systems
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("REMOTE SYSTEMS", Ink.Faint)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SystemCell("POWER", systems.power?.name, status.stage == EscapeStage.POWER, Modifier.weight(1f))
            SystemCell("DECODER", systems.decoderShift?.let { "SHIFT $it" }, status.stage == EscapeStage.CABINET, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SystemCell("RECORDER", systems.recorderChannel?.let { "CH $it" }, status.stage == EscapeStage.STORY, Modifier.weight(1f))
            SystemCell("RELEASE", if (status.armSeconds > 0) "${status.armSeconds}s" else null, status.stage == EscapeStage.EXIT, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SystemCell("LIGHTS", systems.lighting.name.takeIf { systems.lighting != LightingMode.EMERGENCY } ?: "EMERGENCY", false, Modifier.weight(1f),
                color = if (systems.lighting == LightingMode.ULTRAVIOLET) Ink.Uv else Ink.Amber, always = true)
            SystemCell("AIRFLOW", systems.ventilation?.name, false, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SystemCell(label: String, value: String?, current: Boolean, modifier: Modifier, color: Color = Ink.Amber, always: Boolean = false) {
    val on = value != null && (always.not() || value != "EMERGENCY")
    Row(
        modifier.background(Ink.Ground, RoundedCornerShape(5.dp))
            .border(1.dp, if (current) Ink.Amber.copy(alpha = .35f) else Color.Transparent, RoundedCornerShape(5.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Lamp(on, color, 7.dp)
        Label(label, Ink.Faint, Modifier.weight(1f))
        Label(value ?: "—", if (on) color else Ink.Faint)
    }
}

@Composable
private fun Conversation(view: EscapePilotView, crew: CrewStatus, attachedExternal: Boolean, modifier: Modifier) {
    val list = rememberLazyListState()
    val clueTexts = remember(view.objects) { view.objects.map { it.description }.toSet() }
    val entries = view.timeline
    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) list.animateScrollToItem(entries.lastIndex) }
    Box(modifier.fillMaxWidth()) {
        if (entries.isEmpty()) {
            EmptyConversation(crew.enabled || attachedExternal, Modifier.align(Alignment.Center))
        } else {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(entries, key = { _, entry -> entry.seq }) { index, entry ->
                        val previous = entries.getOrNull(index - 1)
                        val note = entry.note
                        val activity = entry.activity
                        if (note != null) {
                            MessageBubble(note, clue = note.role == "YOU" && note.text in clueTexts, grouped = previous?.note?.role == note.role)
                        } else if (activity != null) {
                            ActivityRow(activity, first = previous?.activity == null)
                        }
                    }
                }
            }
        }
    }
}

/** One tool call by the companion, drawn as a line in its visible work log. */
@Composable
private fun ActivityRow(activity: Activity, first: Boolean) {
    val glyph = when (activity.kind) {
        "CALCULATE" -> Glyph.DIAL
        "ROUTE_POWER" -> Glyph.BOLT
        "DECODER" -> Glyph.DIAL
        "RECORDER" -> Glyph.TAPE
        "ARM" -> Glyph.HANDLE
        "LIGHTING" -> Glyph.HINT
        "VENTILATION" -> Glyph.AIR
        "ARCHIVE" -> Glyph.LOG
        else -> Glyph.LINK
    }
    val tone = if (activity.ok) Ink.Amber else Ink.Red
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (first) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(5.dp).background(Ink.Amber.copy(alpha = .7f), RoundedCornerShape(3.dp)))
            Label("COMPANION IS WORKING", Ink.Faint)
        }
        Row(
            Modifier.fillMaxWidth()
                .background(Ink.Ground, RoundedCornerShape(6.dp))
                .border(1.dp, tone.copy(alpha = if (activity.ok) .18f else .45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(26.dp).background(tone.copy(alpha = .12f), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                GlyphIcon(if (activity.ok) glyph else Glyph.CLOSE, tone, 14.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(activity.title, tone)
                Body(activity.detail, if (activity.ok) Ink.Text else Ink.Red.copy(alpha = .9f))
            }
            if (activity.ok) GlyphIcon(Glyph.CHECK, Ink.Faint, 12.dp)
        }
    }
}

@Composable
private fun EmptyConversation(connected: Boolean, modifier: Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlyphIcon(if (connected) Glyph.SHARE else Glyph.LINK, Ink.Faint, 28.dp)
        Body(if (connected) "The line is open." else "No one is on the line.", Ink.Dim)
        Label(
            if (connected) "Look at the breaker panel and press SHARE CLUE. Your companion can't see anything until you do."
            else "Press CONNECT to bring in your BOSS model, or attach an agent to the blackout_v3 tools.",
            Ink.Faint, align = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun MessageBubble(note: Note, clue: Boolean, grouped: Boolean) {
    val mine = note.role == "YOU"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!grouped) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (clue) GlyphIcon(Glyph.SHARE, Ink.Amber, 11.dp)
            Label(if (clue) "CLUE SENT" else if (mine) "YOU" else "COMPANION", if (mine) Ink.Faint else Ink.Amber)
        }
        if (mine) {
            Box(
                Modifier.widthIn(max = 300.dp)
                    .background(if (clue) Ink.Amber.copy(alpha = .08f) else Ink.Raised, RoundedCornerShape(8.dp, 2.dp, 8.dp, 8.dp))
                    .border(1.dp, if (clue) Ink.Amber.copy(alpha = .25f) else Color.Transparent, RoundedCornerShape(8.dp, 2.dp, 8.dp, 8.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) { Body(note.text, if (clue) Ink.Dim else Ink.Text) }
        } else {
            Row(Modifier.widthIn(max = 320.dp).height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(Ink.Amber.copy(alpha = .7f)))
                Box(Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp)) { Body(note.text, Ink.Text) }
            }
        }
    }
}

@Composable
private fun TypingIndicator(status: String) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "typing phase")
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(width = 30.dp, height = 10.dp)) {
            repeat(3) { k ->
                val lift = sin(((phase + k * .2f) % 1f) * PI.toFloat()).coerceAtLeast(0f)
                drawCircle(Ink.Amber.dim(.4f + .6f * lift), 3f, Offset(5f + k * 10f, size.height / 2f - lift * 3f))
            }
        }
        Label(status.ifBlank { "Working" }.uppercase(), Ink.Dim)
    }
}

private fun repliesFor(stage: EscapeStage) = when (stage) {
    EscapeStage.POWER -> listOf("What's the breaker order?", "Did you route the power?")
    EscapeStage.CABINET -> listOf("Is the decoder tuned?", "What's the word?")
    EscapeStage.STORY -> listOf("Is the recorder synced?", "What order are the strips?")
    EscapeStage.EXIT -> listOf("Arm the release now.", "I'm at the handle.")
}

@Composable
private fun QuickReplies(stage: EscapeStage, onSend: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repliesFor(stage).forEach { text -> ReplyChip(text) { onSend(text) } }
    }
}

@Composable
private fun ReplyChip(text: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    Box(
        Modifier.background(if (hovered) Ink.Raised else Color.Transparent, RoundedCornerShape(12.dp))
            .border(1.dp, if (focused) Ink.Text else Ink.Line, RoundedCornerShape(12.dp))
            .hoverable(source)
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Label(text, if (hovered) Ink.Text else Ink.Dim) }
}

@Composable
private fun Composer(roomId: String, onSend: (String) -> Unit) {
    var text by remember(roomId) { mutableStateOf("") }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    fun send() { val clean = text.trim(); if (clean.isNotEmpty()) { onSend(clean); text = "" } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Ink.Ground, RoundedCornerShape(8.dp))
            .border(1.dp, if (focused) Ink.Amber.copy(alpha = .6f) else Ink.Line, RoundedCornerShape(8.dp))
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = text,
            onValueChange = { if (it.length <= 500) text = it },
            interactionSource = source,
            maxLines = 4,
            cursorBrush = SolidColor(Ink.Amber),
            textStyle = bodyStyle(Ink.Text),
            modifier = Modifier.weight(1f).padding(vertical = 6.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) { send(); true } else false
                },
            decorationBox = { inner ->
                Box { if (text.isEmpty()) Body("Talk to your companion…", Ink.Faint); inner() }
            }
        )
        SendButton(enabled = text.isNotBlank(), onClick = ::send)
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier.size(34.dp)
            .background(if (!enabled) Color.Transparent else if (hovered) Ink.Amber else Ink.Amber.copy(alpha = .85f), RoundedCornerShape(6.dp))
            .hoverable(source, enabled)
            .clickable(source, null, enabled, onClickLabel = "Send", role = Role.Button, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        contentAlignment = Alignment.Center
    ) { GlyphIcon(Glyph.SEND, if (enabled) Ink.Ground else Ink.Faint, 16.dp) }
}

@Composable
private fun CompanionFooter(crew: CrewStatus, onRetry: () -> Unit) {
    if (!crew.enabled) return
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Label("${crew.toolCalls} CALLS · ${crew.tokens} TOKENS", Ink.Faint, Modifier.weight(1f))
        if (!crew.busy) GameButton("NUDGE", onRetry, kind = ButtonKind.QUIET, icon = Glyph.RESET, height = 28.dp)
    }
}
