package ai.rever.boss.plugin.dynamic.blackout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Seven colours and one accent. Amber means live or unresolved, red means the case is lost,
 * and nothing else is allowed to carry meaning through colour alone.
 */
object Ink {
    val Ground = Color(0xFF0B0B0C)
    val Surface = Color(0xFF131315)
    val Line = Color(0xFF2A2B2E)
    val Text = Color(0xFFE8E6E3)
    val Dim = Color(0xFF8A8A8F)
    val Faint = Color(0xFF55565B)
    val Amber = Color(0xFFE0A44C)
    val Red = Color(0xFFD9534F)
}

/** Three sizes, one family. Anything that needs a fourth size is doing too much. */
object Type {
    val label = 11.sp
    val body = 13.sp
    val display = 22.sp
    val family = FontFamily.Monospace
}

@Composable
fun Label(text: String, color: Color = Ink.Faint, modifier: Modifier = Modifier) = Text(
    text = text,
    color = color,
    fontSize = Type.label,
    fontFamily = Type.family,
    letterSpacing = 1.4.sp,
    fontWeight = FontWeight.Medium,
    modifier = modifier
)

@Composable
fun Body(text: String, color: Color = Ink.Text, modifier: Modifier = Modifier) = Text(
    text = text,
    color = color,
    fontSize = Type.body,
    fontFamily = Type.family,
    lineHeight = 19.sp,
    modifier = modifier
)

@Composable
fun Display(text: String, color: Color = Ink.Text, modifier: Modifier = Modifier) = Text(
    text = text,
    color = color,
    fontSize = Type.display,
    fontFamily = Type.family,
    fontWeight = FontWeight.Medium,
    letterSpacing = 1.sp,
    modifier = modifier
)

/** A one-word tag. Outline only, so it never competes with the map. */
@Composable
fun Tag(text: String, color: Color = Ink.Dim, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Label(text, color)
    }
}

/** Flat surface. No nesting, no shadow, one hairline if it needs an edge. */
@Composable
fun Slab(
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .background(Ink.Surface, RoundedCornerShape(3.dp))
            .then(if (outlined) Modifier.border(1.dp, Ink.Line, RoundedCornerShape(3.dp)) else Modifier)
    ) { content() }
}
