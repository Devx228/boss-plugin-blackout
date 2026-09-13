package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.dynamic.blackout.application.CrewStatus
import ai.rever.boss.plugin.dynamic.blackout.application.Escape
import ai.rever.boss.plugin.dynamic.blackout.application.EscapePilotView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp

/** Scrim plus a centred panel. Escape or a click outside closes it. */
@Composable
fun Modal(visible: Boolean, onDismiss: () -> Unit, width: Int = 480, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .66f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
                .focusRequester(focus)
                .onPreviewKeyEvent { event -> if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) { onDismiss(); true } else false }
                .focusTarget(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(visible, enter = scaleIn(initialScale = .96f) + fadeIn(), exit = scaleOut(targetScale = .96f) + fadeOut()) {
                Column(
                    Modifier.widthIn(max = width.dp).padding(24.dp)
                        .background(Ink.Surface, RoundedCornerShape(10.dp))
                        .border(1.dp, Ink.Line, RoundedCornerShape(10.dp))
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun ModalHeader(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Display(title, modifier = Modifier.weight(1f))
        IconButton(Glyph.CLOSE, "CLOSE", onClose)
    }
}

@Composable
fun CompanionSetupDialog(visible: Boolean, crew: CrewStatus, onEnabled: (Boolean) -> Unit, onClose: () -> Unit) {
    Modal(visible, onClose, width = 540) {
        ModalHeader("COMPANION", onClose)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CompanionOrb(crew.enabled, crew.busy, true, Modifier.size(48.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Title(crew.label, if (crew.available) Ink.Text else Ink.Dim)
                Label(if (crew.enabled) "IN THE SEAT" else "NOT IN THE SEAT", if (crew.enabled) Ink.Amber else Ink.Faint)
            }
        }
        Body("Your companion reads the manuals and runs power, the decoder, the recorder, lights, airflow and the door release. Every physical action stays with you.", Ink.Dim)
        ToggleRow("Use my BOSS model", if (crew.available) "Calls your configured provider, which may cost tokens." else "No AI gateway in this window.",
            crew.enabled, onEnabled, enabled = crew.available)
        Hairline()
        Label("OR ATTACH YOUR OWN AGENT", Ink.Dim)
        Body("Enable the blackout_v3 tools in the MCP tab, then give your agent this prompt:", Ink.Dim)
        SelectionContainer {
            Box(Modifier.fillMaxWidth().background(Ink.Ground, RoundedCornerShape(6.dp)).padding(12.dp)) {
                Body(EXTERNAL_PROMPT, Ink.Text)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GameButton("DONE", onClose, kind = ButtonKind.PRIMARY)
        }
    }
}

const val EXTERNAL_PROMPT = "Play my BLACKOUT remote companion. Call blackout_v3_observe, read ALL records with blackout_v3_archive, operate the remote systems, and send me each next step with blackout_v3_message."

@Composable
fun LeaveDialog(visible: Boolean, onLeave: () -> Unit, onStay: () -> Unit) {
    Modal(visible, onStay, width = 420) {
        ModalHeader("LEAVE THE ROOM?", onStay)
        Body("The run ends here and is recorded as interrupted. Your companion's work so far is kept in the debrief.", Ink.Dim)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            GameButton("STAY", onStay, kind = ButtonKind.QUIET)
            GameButton("LEAVE", onLeave, kind = ButtonKind.DANGER, icon = Glyph.LEAVE)
        }
    }
}

/** Pocket log on the right edge: progress, what has crossed to the companion, and discoveries. */
@Composable
fun LogDrawer(visible: Boolean, view: EscapePilotView?, room: Escape?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible && view != null, modifier, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
        val current = view ?: return@AnimatedVisibility
        Column(
            Modifier.width(360.dp).fillMaxHeight()
                .background(Ink.Ground)
                .border(1.dp, Ink.Line)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ModalHeader("POCKET LOG", onClose)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBlock("SOLVED", "${current.status.stage.ordinal}/3", Modifier.weight(1f))
                StatBlock("MISTAKES", "${current.status.mistakes}", Modifier.weight(1f))
                StatBlock("HINTS", "${current.status.hintsUsed}/3", Modifier.weight(1f))
            }
            Hairline()
            Label("SENT TO COMPANION", Ink.Dim)
            val shared = current.objects.filter { it.shared }
            if (shared.isEmpty()) Body("Nothing yet. Your companion can't see anything you don't share.", Ink.Faint)
            shared.forEach { obj ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    GlyphIcon(Glyph.CHECK, Ink.Amber, 14.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Label(obj.label.uppercase(), Ink.Text); Body(obj.description, Ink.Dim) }
                }
            }
            if (current.inventory.isNotEmpty()) {
                Hairline()
                Label("CARRYING", Ink.Dim)
                current.inventory.forEach { Body("· $it", Ink.Text) }
            }
            if (current.discoveries.isNotEmpty()) {
                Hairline()
                Label("DISCOVERED", Ink.Dim)
                current.discoveries.forEach { Body(it, Ink.Uv) }
            }
            if (room != null && room.finished()) {
                Hairline()
                Label("TIMELINE", Ink.Dim)
                runCatching { room.debrief() }.getOrNull()?.events?.forEach { event ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Label(formatTime(event.elapsedSeconds), Ink.Faint)
                        Body(event.detail, Ink.Dim)
                    }
                }
            }
        }
    }
}

@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Column(modifier.background(Ink.Surface, RoundedCornerShape(6.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Display(value, if (accent) Ink.Amber else Ink.Text)
        Label(label, Ink.Faint)
    }
}
