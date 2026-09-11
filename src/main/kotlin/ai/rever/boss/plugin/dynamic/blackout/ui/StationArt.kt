package ai.rever.boss.plugin.dynamic.blackout.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.boss.plugin.dynamic.blackout.application.ShipReport
import ai.rever.boss.plugin.dynamic.blackout.application.SystemReport
import ai.rever.boss.plugin.dynamic.blackout.engine.Grid
import ai.rever.boss.plugin.dynamic.blackout.engine.Rules
import ai.rever.boss.plugin.dynamic.blackout.engine.Subsystem

/**
 * A station drawn as a deck plan. Each compartment is its own target, so choosing what to
 * hit, brace or patch is a click on the thing itself rather than a line in a dropdown.
 */
@Composable
fun ShipMap(
    title: String,
    crew: String,
    report: ShipReport,
    delta: Grid?,
    accent: Color,
    selectable: Boolean,
    selected: Subsystem?,
    onSelect: (Subsystem) -> Unit,
    modifier: Modifier = Modifier
) {
    Panel(modifier = modifier, accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(title, accent)
                Note(crew)
            }
            Pill("${report.energy} ENERGY", Ink.Amber)
            Pill("${report.relayPoints}/${Rules.RELAY_TARGET} GRID", Ink.Cyan)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Compartment(report.of(Subsystem.REACTOR), delta?.get(Subsystem.REACTOR), accent, selectable, selected, onSelect, Modifier.weight(1f))
                Compartment(report.of(Subsystem.SHIELDS), delta?.get(Subsystem.SHIELDS), accent, selectable, selected, onSelect, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Compartment(report.of(Subsystem.RELAY), delta?.get(Subsystem.RELAY), accent, selectable, selected, onSelect, Modifier.weight(1f))
                Compartment(report.of(Subsystem.LIFE), delta?.get(Subsystem.LIFE), accent, selectable, selected, onSelect, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Compartment(
    report: SystemReport,
    change: Int?,
    accent: Color,
    selectable: Boolean,
    selected: Subsystem?,
    onSelect: (Subsystem) -> Unit,
    modifier: Modifier
) {
    val down = report.status == "DOWN"
    val hurt = report.status == "DAMAGED"
    val chosen = selected == report.system
    val alarm = rememberInfiniteTransition(label = "alarm")
    val flash by alarm.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "flash"
    )
    val tint = when {
        down -> Ink.Red
        hurt -> Ink.Amber
        else -> accent
    }
    Column(
        modifier
            .background(if (chosen) tint.copy(alpha = 0.14f) else Ink.Deep, RoundedCornerShape(7.dp))
            .border(if (chosen) 1.5.dp else 1.dp, if (chosen) tint else Ink.Edge, RoundedCornerShape(7.dp))
            .then(if (selectable) Modifier.clickable { onSelect(report.system) } else Modifier)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(18.dp)) { drawCompartment(report.system, tint, down, if (down) flash else 1f) }
            Text(report.system.label, color = if (chosen) tint else Ink.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                report.integrity?.let { "$it/${Rules.MAX_INTEGRITY}" } ?: report.status,
                color = tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            if (change != null && change != 0) {
                Pill(if (change > 0) "+$change" else "$change", if (change < 0) Ink.Red else Ink.Green, filled = true)
            }
        }
        if (report.integrity != null) {
            Segments(report.integrity, Rules.MAX_INTEGRITY, tint, height = 5)
        } else {
            Note(report.system.role, Ink.Faint)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Note("BARRIER", Ink.Faint)
            val barrier = report.barrier
            if (barrier == null) {
                Note("UNREAD", Ink.Faint)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(Rules.MAX_BARRIER) { index ->
                        Box(
                            Modifier.size(5.dp).background(
                                if (index < barrier) Ink.Violet else Ink.Raised, RoundedCornerShape(1.dp)
                            )
                        )
                    }
                }
            }
        }
    }
}

/** A small deck glyph per compartment, so the four rooms are told apart without reading. */
private fun DrawScope.drawCompartment(system: Subsystem, tint: Color, down: Boolean, flash: Float) {
    val colour = tint.copy(alpha = if (down) flash else 0.9f)
    val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
    val centre = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f - 1.dp.toPx()
    when (system) {
        // Reactor: a core with orbiting containment.
        Subsystem.REACTOR -> {
            drawCircle(colour, radius = radius * 0.42f, center = centre)
            drawCircle(colour, radius = radius, center = centre, style = stroke)
        }
        // Shield array: a layered arc facing the incoming fire.
        Subsystem.SHIELDS -> {
            for (step in 0..1) {
                val r = radius - step * radius * 0.35f
                drawArc(
                    color = colour,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(centre.x - r, centre.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = stroke
                )
            }
        }
        // Relay mast: an antenna with two transmission bars.
        Subsystem.RELAY -> {
            drawLine(colour, Offset(centre.x, centre.y + radius), Offset(centre.x, centre.y - radius), stroke.width, StrokeCap.Round)
            drawLine(colour, Offset(centre.x - radius * 0.7f, centre.y - radius * 0.2f), Offset(centre.x + radius * 0.7f, centre.y - radius * 0.2f), stroke.width, StrokeCap.Round)
            drawLine(colour, Offset(centre.x - radius * 0.4f, centre.y - radius * 0.7f), Offset(centre.x + radius * 0.4f, centre.y - radius * 0.7f), stroke.width, StrokeCap.Round)
        }
        // Life support: a breathing cycle.
        Subsystem.LIFE -> {
            drawArc(
                color = colour,
                startAngle = 30f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = stroke
            )
            drawCircle(colour, radius = radius * 0.25f, center = centre)
        }
    }
}

/** Relay control, drawn as a contested line rather than two separate numbers. */
@Composable
fun RelayMeter(blue: Int, orange: Int, target: Int, modifier: Modifier = Modifier) {
    Panel(title = "GRID CONTROL", modifier = modifier, accent = Ink.Cyan, trailing = {
        Note("FIRST TO $target TAKES THE GRID")
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Readout("$blue", Ink.Cyan, 20)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(target) { index ->
                    Box(
                        Modifier.weight(1f).height(10.dp).background(
                            if (index < blue) Ink.Cyan else Ink.Raised, RoundedCornerShape(2.dp)
                        )
                    )
                }
                Box(Modifier.width(2.dp).height(10.dp).background(Ink.Edge))
                repeat(target) { index ->
                    Box(
                        Modifier.weight(1f).height(10.dp).background(
                            if (index < orange) Ink.Amber else Ink.Raised, RoundedCornerShape(2.dp)
                        )
                    )
                }
            }
            Readout("$orange", Ink.Amber, 20)
        }
    }
}
