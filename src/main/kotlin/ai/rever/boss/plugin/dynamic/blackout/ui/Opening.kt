package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.dynamic.blackout.application.CrewStatus
import ai.rever.boss.plugin.dynamic.blackout.application.RoomMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.boss.plugin.dynamic.blackout.engine.EscapeRoom

/** Title screen: the dark room behind, the premise, who does what, and one way in. */
@Composable
fun OpeningScreen(
    crew: CrewStatus,
    attached: Boolean,
    reducedMotion: Boolean,
    compact: Boolean,
    onAttached: (Boolean) -> Unit,
    onSetup: () -> Unit,
    onBegin: (RoomMode) -> Unit
) {
    var mode by remember { mutableStateOf(RoomMode.STANDARD) }
    val connected = crew.enabled || attached
    Box(Modifier.fillMaxSize().background(Ink.Void)) {
        RoomScene(SceneState(ambientBoost = .45f), reducedMotion, interactive = false, onSelect = {}, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            if (compact) listOf(Ink.Void.copy(alpha = .9f), Ink.Void.copy(alpha = .9f))
            else listOf(Ink.Void.copy(alpha = .97f), Ink.Void.copy(alpha = .9f), Ink.Void.copy(alpha = .35f), Ink.Void.copy(alpha = 0f))
        )))
        Column(
            Modifier.fillMaxHeight().widthIn(max = 620.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 20.dp else 56.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            Wordmark()
            TitleBlock(reducedMotion)
            RoleList()
            ConnectionCard(crew, attached, onAttached, onSetup)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("CHOOSE YOUR AIR", Ink.Dim)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeCard(RoomMode.STANDARD, "10:00", "The full room. Mistakes cost 15s, hints 20s.", mode == RoomMode.STANDARD, Modifier.weight(1f)) { mode = RoomMode.STANDARD }
                    ModeCard(RoomMode.SHOWCASE, "4:00", "A fast demo run. Mistakes and hints cost 10s.", mode == RoomMode.SHOWCASE, Modifier.weight(1f)) { mode = RoomMode.SHOWCASE }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GameButton("ENTER THE ROOM", { onBegin(mode) }, kind = ButtonKind.PRIMARY, enabled = connected, icon = Glyph.PLAY, height = 48.dp)
                if (!connected) GameButton("EXPLORE ALONE", { onBegin(mode) }, kind = ButtonKind.QUIET)
            }
            if (!connected) Label("Exploring alone lets you look around, but only a companion can operate the remote systems.", Ink.Faint)
        }
    }
}

@Composable
private fun TitleBlock(reducedMotion: Boolean) {
    val time by rememberAmbientTime(reducedMotion)
    val glowAlpha = if (reducedMotion) 1f else flicker(time, 7)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("BLACKOUT", style = TextStyle(color = Ink.Text.copy(alpha = .75f + .25f * glowAlpha), fontSize = Type.hero,
            fontFamily = Type.mono, fontWeight = FontWeight.Bold, letterSpacing = 8.sp))
        Text("The lights die. The door seals.\nYour only teammate can't see the room.",
            style = TextStyle(color = Ink.Text, fontSize = 20.sp, fontFamily = Type.mono, lineHeight = 30.sp))
        Body(EscapeRoom.INTRO, Ink.Dim)
    }
}

@Composable
private fun RoleList() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RoleRow(Glyph.HANDLE, "YOU", "See and touch everything. Flip breakers, type the code, turn the handle.")
        RoleRow(Glyph.LINK, "YOUR AI", "Reads the manuals, does the maths, routes power and unlocks the door.")
        RoleRow(Glyph.AIR, "TOGETHER", "Four linked puzzles before the air runs out. Nobody escapes alone.")
    }
}

@Composable
private fun RoleRow(glyph: Glyph, title: String, copy: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(36.dp).border(1.dp, Ink.Line, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, Ink.Amber, 18.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(title, Ink.Amber)
            Body(copy, Ink.Dim)
        }
    }
}

@Composable
private fun ConnectionCard(crew: CrewStatus, attached: Boolean, onAttached: (Boolean) -> Unit, onSetup: () -> Unit) {
    Slab(Modifier.fillMaxWidth(), outlined = true, tone = Ink.Surface.copy(alpha = .9f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompanionOrb(crew.enabled || attached, false, true, Modifier.size(36.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Title(if (crew.enabled) "COMPANION READY" else if (attached) "EXTERNAL AGENT" else "NO COMPANION YET",
                        if (crew.enabled || attached) Ink.Text else Ink.Dim)
                    Label(if (crew.enabled) crew.label else if (crew.available) "Your BOSS model can take the seat." else "No BOSS model here. Attach an agent over MCP.", Ink.Faint)
                }
                GameButton(if (crew.enabled) "CHANGE" else "CONNECT", onSetup, kind = if (crew.enabled) ButtonKind.QUIET else ButtonKind.SECONDARY, icon = Glyph.LINK)
            }
            if (!crew.enabled) ToggleRow("I've attached an external agent", "It will play through the nine blackout_v3 tools.", attached, onAttached)
        }
    }
}

@Composable
private fun ModeCard(mode: RoomMode, time: String, copy: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) Ink.Text else if (selected) Ink.Amber else if (hovered) Ink.Edge else Ink.Line, tween(150), label = "mode border")
    Column(
        modifier
            .background(if (selected) Ink.Amber.copy(alpha = .07f) else Ink.Surface.copy(alpha = .9f), RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .hoverable(source)
            .clickable(source, null, onClickLabel = "Choose ${mode.name}", role = Role.RadioButton, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(mode.name, if (selected) Ink.Amber else Ink.Dim, Modifier.weight(1f))
            Box(Modifier.size(12.dp).border(1.dp, if (selected) Ink.Amber else Ink.Edge, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(6.dp).background(Ink.Amber, RoundedCornerShape(3.dp)))
            }
        }
        Display(time, if (selected) Ink.Text else Ink.Dim)
        Label(copy, Ink.Faint)
    }
}

/** A labelled on/off switch drawn to match the rest of the game. */
@Composable
fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val knob by androidx.compose.animation.core.animateDpAsState(if (checked) 16.dp else 2.dp, tween(160), label = "knob")
    Row(
        Modifier.fillMaxWidth()
            .background(if (hovered && enabled) Ink.Raised else Color.Transparent, RoundedCornerShape(6.dp))
            .border(1.dp, if (focused) Ink.Text else Color.Transparent, RoundedCornerShape(6.dp))
            .hoverable(source, enabled)
            .clickable(source, null, enabled, role = Role.Switch) { onChange(!checked) }
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(width = 34.dp, height = 20.dp).background(if (checked) Ink.Amber else Ink.Line, RoundedCornerShape(10.dp))) {
            Box(Modifier.padding(start = knob, top = 2.dp).size(16.dp).background(if (checked) Ink.Ground else Ink.Dim, RoundedCornerShape(8.dp)))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Body(title, if (enabled) Ink.Text else Ink.Faint)
            Label(detail, Ink.Faint)
        }
    }
}
