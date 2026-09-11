package ai.rever.boss.plugin.dynamic.blackout.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.boss.plugin.dynamic.blackout.engine.Route

/**
 * A power route drawn as the thing it is: a pair of conductors from the reactor to the
 * combat bus. Crossed routes swap the strands in the middle, insulated routes wear a
 * sheath, high-output routes carry a third heavy strand, broken routes have a burnt gap.
 * Every property is also written out, so the picture is a shortcut and never the only signal.
 */
@Composable
fun RouteRow(
    route: Route,
    selected: Boolean,
    locked: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val flow = rememberInfiniteTransition(label = "current")
    val phase by flow.animateFloat(
        initialValue = 0f,
        targetValue = -28f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val accent = when {
        route.broken -> Ink.Red
        selected -> Ink.Cyan
        else -> Ink.Faint
    }
    Surface(
        modifier = modifier.fillMaxWidth().then(if (locked) Modifier else Modifier.clickable { onSelect() }),
        color = if (selected) Ink.Raised else Ink.Deep,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Ink.Cyan else Ink.Edge)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(26.dp).background(if (selected) Ink.Cyan else Ink.Panel, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(route.id, color = if (selected) Ink.Void else Ink.Dim, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            Canvas(Modifier.weight(1f).height(34.dp)) {
                drawRoute(route, accent, selected && !route.broken, phase)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(if (route.broken) "BROKEN" else "INTACT", if (route.broken) Ink.Red else Ink.Green, filled = route.broken)
                    Pill(if (route.insulated) "INSULATED" else "BARE", if (route.insulated) Ink.Violet else Ink.Dim)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(if (route.crossed) "CROSSED" else "STRAIGHT", Ink.Cyan)
                    Pill(if (route.highOutput) "HIGH OUTPUT" else "NORMAL ONLY", if (route.highOutput) Ink.Amber else Ink.Dim)
                }
            }
        }
    }
}

private fun DrawScope.drawRoute(route: Route, accent: Color, animate: Boolean, phase: Float) {
    val midY = size.height / 2f
    val gapY = 6.dp.toPx()
    val left = 6.dp.toPx()
    val right = size.width - 6.dp.toPx()
    val crossFrom = size.width * 0.40f
    val crossTo = size.width * 0.58f
    val width = if (route.highOutput) 2.6.dp.toPx() else 1.6.dp.toPx()
    val dash = if (animate) PathEffect.dashPathEffect(floatArrayOf(9f, 7f), phase) else null

    if (route.insulated) {
        drawLine(
            color = Ink.Violet.copy(alpha = 0.22f),
            start = Offset(left, midY),
            end = Offset(right, midY),
            strokeWidth = gapY * 2.9f,
            cap = StrokeCap.Round
        )
    }

    // Two conductors. They swap sides in the middle when the route is crossed.
    val topStart = midY - gapY
    val bottomStart = midY + gapY
    val topEnd = if (route.crossed) midY + gapY else midY - gapY
    val bottomEnd = if (route.crossed) midY - gapY else midY + gapY

    fun strand(y0: Float, y1: Float) {
        drawLine(accent, Offset(left, y0), Offset(crossFrom, y0), width, StrokeCap.Round, dash)
        drawLine(accent, Offset(crossFrom, y0), Offset(crossTo, y1), width, StrokeCap.Round, dash)
        drawLine(accent, Offset(crossTo, y1), Offset(right, y1), width, StrokeCap.Round, dash)
    }
    strand(topStart, topEnd)
    strand(bottomStart, bottomEnd)

    if (route.highOutput) {
        drawLine(
            color = accent.copy(alpha = 0.45f),
            start = Offset(left, midY),
            end = Offset(right, midY),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = dash
        )
    }

    if (route.broken) {
        // Burn out a section and mark it, so damage is visible without reading the label.
        val burnFrom = size.width * 0.70f
        val burnTo = size.width * 0.80f
        drawRect(
            color = Ink.Deep,
            topLeft = Offset(burnFrom, midY - gapY * 2.6f),
            size = androidx.compose.ui.geometry.Size(burnTo - burnFrom, gapY * 5.2f)
        )
        val tick = 4.dp.toPx()
        drawLine(Ink.Red, Offset(burnFrom - tick, midY - tick), Offset(burnFrom + tick, midY + tick), 1.6.dp.toPx())
        drawLine(Ink.Red, Offset(burnTo - tick, midY - tick), Offset(burnTo + tick, midY + tick), 1.6.dp.toPx())
    }

    // Reactor and bus terminals.
    drawCircle(Ink.Edge, radius = 4.dp.toPx(), center = Offset(left, midY))
    drawCircle(accent, radius = 4.dp.toPx(), center = Offset(left, midY), style = Stroke(1.4.dp.toPx()))
    drawCircle(Ink.Edge, radius = 4.dp.toPx(), center = Offset(right, midY))
    drawCircle(accent, radius = 4.dp.toPx(), center = Offset(right, midY), style = Stroke(1.4.dp.toPx()))
}
