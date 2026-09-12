package ai.rever.boss.plugin.dynamic.blackout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.rever.boss.plugin.dynamic.blackout.application.SiteView
import ai.rever.boss.plugin.dynamic.blackout.application.Verdict

/**
 * The station, drawn as a blueprint: a spine, eight berths and nothing else. Line work only,
 * because the map is the one thing on screen that should hold the eye.
 */
@Composable
fun StationMap(
    sites: List<SiteView>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        val moduleW = minOf(196.dp, w * 0.225f)
        val moduleH = 74.dp

        Canvas(Modifier.fillMaxSize()) {
            val spineY = size.height * 0.5f
            drawLine(
                color = Ink.Line,
                start = Offset(size.width * 0.05f, spineY),
                end = Offset(size.width * 0.95f, spineY),
                strokeWidth = 1f
            )
            sites.forEach { site ->
                val x = size.width * site.x
                drawLine(
                    color = Ink.Line,
                    start = Offset(x, size.height * site.y),
                    end = Offset(x, spineY),
                    strokeWidth = 1f
                )
                drawCircle(color = Ink.Line, radius = 2.5f, center = Offset(x, spineY))
            }
        }

        sites.forEach { site ->
            val left = (w * site.x - moduleW / 2).coerceIn(0.dp, (w - moduleW).coerceAtLeast(0.dp))
            val top = (h * site.y - moduleH / 2).coerceIn(0.dp, (h - moduleH).coerceAtLeast(0.dp))
            Berth(
                site = site,
                selected = site.id == selected,
                enabled = enabled,
                width = moduleW,
                height = moduleH,
                onSelect = onSelect,
                modifier = Modifier.offset(x = left, y = top)
            )
        }
    }
}

@Composable
private fun Berth(
    site: SiteView,
    selected: Boolean,
    enabled: Boolean,
    width: Dp,
    height: Dp,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suspect = site.mark?.verdict == Verdict.SUSPECT
    val cleared = site.mark?.verdict == Verdict.CLEAR

    val edge = when {
        selected -> Ink.Amber
        suspect -> Ink.Amber.copy(alpha = 0.55f)
        site.inspected -> Ink.Line
        cleared -> Ink.Line.copy(alpha = 0.5f)
        else -> Ink.Line
    }
    val name = when {
        selected || site.inspected -> Ink.Text
        cleared -> Ink.Faint
        else -> Ink.Dim
    }

    Column(
        modifier
            .width(width)
            .height(height)
            .background(if (site.inspected) Ink.Surface else Ink.Ground, RoundedCornerShape(3.dp))
            .border(if (selected) 2.dp else 1.dp, edge, RoundedCornerShape(3.dp))
            .clickable(enabled = enabled) { onSelect(site.id) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = site.name.uppercase(),
            color = name,
            fontSize = Type.body,
            fontFamily = Type.family,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                site.inspected -> Label("SEARCHED", Ink.Dim)
                suspect -> Label("SUSPECT", Ink.Amber)
                cleared -> Label("CLEAR", Ink.Faint)
                else -> Label("UNSEARCHED", Ink.Faint)
            }
            if (site.inspected && site.trace != null) Label("ITEM", Ink.Amber)
        }
    }
}

/** A thin readout that reads as one number, for the clock and the walk budget. */
@Composable
fun Readout(label: String, value: String, tint: Color = Ink.Text, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Label(label)
        Display(value, tint)
    }
}
