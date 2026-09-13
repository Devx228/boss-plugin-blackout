package ai.rever.boss.plugin.dynamic.blackout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One warm accent. Amber means live or actionable, red means failure, violet appears only
 * while the companion has ultraviolet lighting on. Everything else is a grey.
 */
object Ink {
    val Void = Color(0xFF070708)
    val Ground = Color(0xFF0C0C0E)
    val Surface = Color(0xFF131316)
    val Raised = Color(0xFF1A1A1E)
    val Line = Color(0xFF2A2B30)
    val Edge = Color(0xFF3A3B41)
    val Text = Color(0xFFECEAE6)
    val Dim = Color(0xFF9A9AA0)
    val Faint = Color(0xFF5E5F66)
    val Amber = Color(0xFFE8A850)
    val AmberDeep = Color(0xFF8A5A22)
    val Red = Color(0xFFE0564F)
    val Uv = Color(0xFF9D8CFF)
    val Metal = Color(0xFF24252A)
    val MetalLight = Color(0xFF33353B)
    val MetalDark = Color(0xFF17181B)
}

object Type {
    val label = 11.sp
    val body = 13.sp
    val title = 17.sp
    val display = 26.sp
    val hero = 64.sp
    val mono = FontFamily.Monospace
}

fun labelStyle(color: Color = Ink.Faint) = TextStyle(color = color, fontSize = Type.label, fontFamily = Type.mono,
    letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium)

fun bodyStyle(color: Color = Ink.Text) = TextStyle(color = color, fontSize = Type.body, fontFamily = Type.mono, lineHeight = 20.sp)

@Composable
fun Label(text: String, color: Color = Ink.Faint, modifier: Modifier = Modifier, align: TextAlign? = null) =
    Text(text, modifier, style = labelStyle(color), textAlign = align, maxLines = 2)

@Composable
fun Body(text: String, color: Color = Ink.Text, modifier: Modifier = Modifier, align: TextAlign? = null) =
    Text(text, modifier, style = bodyStyle(color), textAlign = align)

@Composable
fun Title(text: String, color: Color = Ink.Text, modifier: Modifier = Modifier) = Text(text, modifier,
    style = TextStyle(color = color, fontSize = Type.title, fontFamily = Type.mono, fontWeight = FontWeight.Medium, letterSpacing = 1.sp))

@Composable
fun Display(text: String, color: Color = Ink.Text, modifier: Modifier = Modifier, align: TextAlign? = null) = Text(text, modifier,
    style = TextStyle(color = color, fontSize = Type.display, fontFamily = Type.mono, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp),
    textAlign = align)

/** Outline tag for short state words. */
@Composable
fun Tag(text: String, color: Color = Ink.Dim, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .background(if (filled) color.copy(alpha = .14f) else Color.Transparent, RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = .45f), RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) { Label(text, color) }
}

/** Flat surface. Separation comes from tone, with one hairline when needed. */
@Composable
fun Slab(modifier: Modifier = Modifier, outlined: Boolean = false, tone: Color = Ink.Surface, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(tone, RoundedCornerShape(6.dp))
            .then(if (outlined) Modifier.border(1.dp, Ink.Line, RoundedCornerShape(6.dp)) else Modifier)
    ) { content() }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Ink.Line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun VerticalHairline(modifier: Modifier = Modifier, color: Color = Ink.Line) {
    Box(modifier.fillMaxHeight().width(1.dp).background(color))
}

enum class ButtonKind { PRIMARY, SECONDARY, QUIET, DANGER }

/** One button for the whole game: hover, press and keyboard focus are all visible. */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.SECONDARY,
    enabled: Boolean = true,
    icon: Glyph? = null,
    height: Dp = 38.dp
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val accent = if (kind == ButtonKind.DANGER) Ink.Red else Ink.Amber
    val targetBackground = when {
        !enabled -> if (kind == ButtonKind.PRIMARY) Ink.Raised else Color.Transparent
        kind == ButtonKind.PRIMARY -> if (pressed) Ink.AmberDeep else if (hovered) accent.copy(alpha = .92f) else accent
        kind == ButtonKind.QUIET -> if (hovered) Ink.Raised else Color.Transparent
        else -> if (pressed) accent.copy(alpha = .22f) else if (hovered) accent.copy(alpha = .12f) else Color.Transparent
    }
    val background by animateColorAsState(targetBackground, tween(120), label = "button background")
    val content = when {
        !enabled -> Ink.Faint
        kind == ButtonKind.PRIMARY -> Ink.Ground
        kind == ButtonKind.QUIET -> if (hovered) Ink.Text else Ink.Dim
        else -> accent
    }
    val border = when {
        focused -> Ink.Text
        kind == ButtonKind.QUIET -> Color.Transparent
        kind == ButtonKind.PRIMARY -> Color.Transparent
        !enabled -> Ink.Line
        else -> accent.copy(alpha = if (hovered) .9f else .5f)
    }
    Row(
        modifier
            .height(height)
            .background(background, RoundedCornerShape(5.dp))
            .border(1.dp, border, RoundedCornerShape(5.dp))
            .hoverable(source, enabled)
            .clickable(source, null, enabled, role = Role.Button, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) GlyphIcon(icon, content, 14.dp)
        Label(text, content)
    }
}

/** Square icon-only control used in the heads-up bar. */
@Composable
fun IconButton(glyph: Glyph, description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, active: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val tint = when { !enabled -> Ink.Faint; active -> Ink.Amber; hovered -> Ink.Text; else -> Ink.Dim }
    Column(
        modifier
            .background(if (hovered && enabled) Ink.Raised else Color.Transparent, RoundedCornerShape(5.dp))
            .border(1.dp, if (focused) Ink.Text else Color.Transparent, RoundedCornerShape(5.dp))
            .hoverable(source, enabled)
            .clickable(source, null, enabled, onClickLabel = description, role = Role.Button, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GlyphIcon(glyph, tint, 16.dp)
        Label(description, tint)
    }
}

/** Small square status lamp with a caption, used for remote-system readouts. */
@Composable
fun Lamp(on: Boolean, color: Color = Ink.Amber, size: Dp = 8.dp) {
    Box(
        Modifier.size(size)
            .background(if (on) color else Ink.Line, RoundedCornerShape(2.dp))
            .border(1.dp, if (on) color.copy(alpha = .6f) else Ink.Edge, RoundedCornerShape(2.dp))
    )
}

fun formatTime(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
