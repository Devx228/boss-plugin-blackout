package ai.rever.boss.plugin.dynamic.blackout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Emergency-power console palette. Team signals never rely on colour alone. */
object Ink {
    val Void = Color(0xFF06090F)
    val Deep = Color(0xFF0C1220)
    val Panel = Color(0xFF111B2B)
    val Raised = Color(0xFF17253A)
    val Edge = Color(0xFF23364F)
    val Text = Color(0xFFDCE7F5)
    val Dim = Color(0xFF8095B0)
    val Faint = Color(0xFF4C5F7A)
    val Cyan = Color(0xFF5FE3D8)
    val Amber = Color(0xFFFFB454)
    val Red = Color(0xFFFF6B6B)
    val Green = Color(0xFF7BE38A)
    val Violet = Color(0xFFB08CFF)
}

@Composable
fun Panel(
    title: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = Ink.Cyan,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Ink.Panel,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Ink.Edge)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(3.dp, 13.dp).background(accent, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text(title, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    if (trailing != null) {
                        Spacer(Modifier.weight(1f))
                        trailing()
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun Pill(text: String, color: Color = Ink.Dim, filled: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(if (filled) color.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = if (filled) 0.7f else 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, maxLines = 1)
    }
}

@Composable fun Label(text: String, color: Color = Ink.Dim, modifier: Modifier = Modifier) =
    Text(text, color = color, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)

@Composable fun Body(text: String, color: Color = Ink.Text, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) =
    Text(text, color = color, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable fun Note(text: String, color: Color = Ink.Faint, modifier: Modifier = Modifier) =
    Text(text, color = color, fontSize = 11.sp, lineHeight = 16.sp, modifier = modifier)

@Composable fun Readout(text: String, color: Color = Ink.Text, size: Int = 22) =
    Text(text, color = color, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)

/** Segmented bar: countable, readable without colour discrimination. */
@Composable
fun Segments(filled: Int, total: Int, color: Color, modifier: Modifier = Modifier, height: Int = 9) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(height.dp)
                    .background(
                        if (index < filled) color else Ink.Raised,
                        RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

val MaterialColours
    @Composable get() = MaterialTheme.colors
