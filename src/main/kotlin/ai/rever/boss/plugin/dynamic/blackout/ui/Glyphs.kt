package ai.rever.boss.plugin.dynamic.blackout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Interface icons drawn as line art, so the game ships no image assets. */
enum class Glyph { PAUSE, PLAY, HINT, LOG, SOUND, LEAVE, CLOSE, SEND, SHARE, CHECK, LOCK, BOLT, LINK, HANDLE, DIAL, TAPE, AIR, RESET }

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, color, Offset.Zero, this.size.minDimension) }
}

fun DrawScope.drawGlyph(glyph: Glyph, color: Color, topLeft: Offset, extent: Float) {
    val s = extent
    val w = (s * .09f).coerceAtLeast(1.2f)
    val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(topLeft.x + x * s, topLeft.y + y * s)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), w, StrokeCap.Round)
    fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = stroke)
    when (glyph) {
        Glyph.PAUSE -> { line(.35f, .22f, .35f, .78f); line(.65f, .22f, .65f, .78f) }
        Glyph.PLAY -> path { moveTo(p(.3f, .2f).x, p(.3f, .2f).y); lineTo(p(.8f, .5f).x, p(.8f, .5f).y); lineTo(p(.3f, .8f).x, p(.3f, .8f).y); close() }
        Glyph.HINT -> {
            drawArc(color, 160f, 220f, false, p(.25f, .1f), Size(.5f * s, .5f * s), style = stroke)
            line(.3f, .52f, .4f, .66f); line(.7f, .52f, .6f, .66f)
            line(.4f, .72f, .6f, .72f); line(.43f, .86f, .57f, .86f)
        }
        Glyph.LOG -> { line(.25f, .25f, .75f, .25f); line(.25f, .42f, .75f, .42f); line(.25f, .59f, .75f, .59f); line(.25f, .76f, .55f, .76f) }
        Glyph.SOUND -> {
            path { moveTo(p(.15f, .4f).x, p(.15f, .4f).y); lineTo(p(.32f, .4f).x, p(.32f, .4f).y); lineTo(p(.5f, .22f).x, p(.5f, .22f).y)
                lineTo(p(.5f, .78f).x, p(.5f, .78f).y); lineTo(p(.32f, .6f).x, p(.32f, .6f).y); lineTo(p(.15f, .6f).x, p(.15f, .6f).y); close() }
            drawArc(color, -45f, 90f, false, p(.42f, .3f), Size(.3f * s, .4f * s), style = stroke)
            drawArc(color, -50f, 100f, false, p(.4f, .18f), Size(.46f * s, .64f * s), style = stroke)
        }
        Glyph.LEAVE -> {
            path { moveTo(p(.55f, .2f).x, p(.55f, .2f).y); lineTo(p(.2f, .2f).x, p(.2f, .2f).y); lineTo(p(.2f, .8f).x, p(.2f, .8f).y); lineTo(p(.55f, .8f).x, p(.55f, .8f).y) }
            line(.42f, .5f, .85f, .5f); line(.7f, .35f, .85f, .5f); line(.7f, .65f, .85f, .5f)
        }
        Glyph.CLOSE -> { line(.25f, .25f, .75f, .75f); line(.75f, .25f, .25f, .75f) }
        Glyph.SEND -> {
            path { moveTo(p(.15f, .5f).x, p(.15f, .5f).y); lineTo(p(.85f, .18f).x, p(.85f, .18f).y); lineTo(p(.62f, .85f).x, p(.62f, .85f).y); lineTo(p(.48f, .55f).x, p(.48f, .55f).y); close() }
        }
        Glyph.SHARE -> {
            line(.3f, .7f, .72f, .28f); line(.45f, .28f, .72f, .28f); line(.72f, .28f, .72f, .55f)
            path { moveTo(p(.5f, .8f).x, p(.5f, .8f).y); lineTo(p(.2f, .8f).x, p(.2f, .8f).y); lineTo(p(.2f, .5f).x, p(.2f, .5f).y) }
        }
        Glyph.CHECK -> { line(.2f, .52f, .42f, .74f); line(.42f, .74f, .82f, .3f) }
        Glyph.LOCK -> {
            drawRoundRectOutline(color, p(.22f, .45f), Size(.56f * s, .42f * s), w)
            drawArc(color, 180f, 180f, false, p(.32f, .18f), Size(.36f * s, .5f * s), style = stroke)
        }
        Glyph.BOLT -> path { moveTo(p(.58f, .1f).x, p(.58f, .1f).y); lineTo(p(.28f, .55f).x, p(.28f, .55f).y); lineTo(p(.5f, .55f).x, p(.5f, .55f).y)
            lineTo(p(.42f, .9f).x, p(.42f, .9f).y); lineTo(p(.74f, .42f).x, p(.74f, .42f).y); lineTo(p(.52f, .42f).x, p(.52f, .42f).y); close() }
        Glyph.LINK -> {
            drawArc(color, 90f, 180f, false, p(.12f, .32f), Size(.36f * s, .36f * s), style = stroke)
            drawArc(color, -90f, 180f, false, p(.52f, .32f), Size(.36f * s, .36f * s), style = stroke)
            line(.3f, .32f, .7f, .32f); line(.3f, .68f, .7f, .68f)
        }
        Glyph.HANDLE -> { drawCircle(color, .14f * s, p(.3f, .5f), style = stroke); line(.44f, .5f, .88f, .5f) }
        Glyph.DIAL -> { drawCircle(color, .34f * s, p(.5f, .5f), style = stroke); line(.5f, .5f, .7f, .3f) }
        Glyph.TAPE -> { drawCircle(color, .14f * s, p(.3f, .5f), style = stroke); drawCircle(color, .14f * s, p(.7f, .5f), style = stroke); line(.3f, .64f, .7f, .64f) }
        Glyph.AIR -> {
            path { moveTo(p(.12f, .35f).x, p(.12f, .35f).y); cubicTo(p(.4f, .2f).x, p(.4f, .2f).y, p(.6f, .5f).x, p(.6f, .5f).y, p(.86f, .35f).x, p(.86f, .35f).y) }
            path { moveTo(p(.12f, .62f).x, p(.12f, .62f).y); cubicTo(p(.4f, .47f).x, p(.4f, .47f).y, p(.6f, .77f).x, p(.6f, .77f).y, p(.86f, .62f).x, p(.86f, .62f).y) }
        }
        Glyph.RESET -> {
            drawArc(color, 30f, 280f, false, p(.2f, .2f), Size(.6f * s, .6f * s), style = stroke)
            line(.76f, .22f, .78f, .42f); line(.6f, .42f, .78f, .42f)
        }
    }
}

private fun DrawScope.drawRoundRectOutline(color: Color, topLeft: Offset, size: Size, width: Float) {
    drawRoundRect(color, topLeft, size, androidx.compose.ui.geometry.CornerRadius(width * 1.5f), style = Stroke(width))
}

/** The six breaker and seal symbols. Each is recognisable at 14px and at 90px. */
fun DrawScope.drawSymbol(symbol: String, color: Color, center: Offset, radius: Float, width: Float = radius * .14f) {
    val stroke = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val r = radius
    when (symbol.uppercase()) {
        "SUN" -> {
            drawCircle(color, r * .42f, center, style = stroke)
            repeat(8) { i ->
                val a = i * PI.toFloat() / 4f
                drawLine(color, Offset(center.x + cos(a) * r * .62f, center.y + sin(a) * r * .62f),
                    Offset(center.x + cos(a) * r * .92f, center.y + sin(a) * r * .92f), width, StrokeCap.Round)
            }
        }
        "WAVE" -> repeat(2) { row ->
            val y = center.y - r * .25f + row * r * .5f
            val path = Path().apply {
                moveTo(center.x - r * .9f, y)
                cubicTo(center.x - r * .55f, y - r * .4f, center.x - r * .25f, y - r * .4f, center.x, y)
                cubicTo(center.x + r * .25f, y + r * .4f, center.x + r * .55f, y + r * .4f, center.x + r * .9f, y)
            }
            drawPath(path, color, style = stroke)
        }
        "LEAF" -> {
            val path = Path().apply {
                moveTo(center.x - r * .7f, center.y + r * .7f)
                cubicTo(center.x - r * .8f, center.y - r * .5f, center.x + r * .2f, center.y - r * .9f, center.x + r * .8f, center.y - r * .8f)
                cubicTo(center.x + r * .9f, center.y - r * .1f, center.x + r * .4f, center.y + r * .8f, center.x - r * .7f, center.y + r * .7f)
                close()
            }
            drawPath(path, color, style = stroke)
            drawLine(color, Offset(center.x - r * .7f, center.y + r * .7f), Offset(center.x + r * .45f, center.y - r * .45f), width, StrokeCap.Round)
        }
        "MOON" -> {
            val path = Path().apply {
                addArc(Rect(center.x - r * .8f, center.y - r * .8f, center.x + r * .8f, center.y + r * .8f), 60f, 240f)
            }
            drawPath(path, color, style = stroke)
            drawArc(color, 115f, 130f, false, Offset(center.x - r * .35f, center.y - r * .7f), Size(r * 1.1f, r * 1.4f), style = stroke)
        }
        "EYE" -> {
            val path = Path().apply {
                moveTo(center.x - r * .95f, center.y)
                cubicTo(center.x - r * .45f, center.y - r * .7f, center.x + r * .45f, center.y - r * .7f, center.x + r * .95f, center.y)
                cubicTo(center.x + r * .45f, center.y + r * .7f, center.x - r * .45f, center.y + r * .7f, center.x - r * .95f, center.y)
                close()
            }
            drawPath(path, color, style = stroke)
            drawCircle(color, r * .26f, center, style = stroke)
            drawCircle(color, r * .08f, center)
        }
        "STAR" -> {
            val path = Path()
            repeat(10) { i ->
                val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
                val rr = if (i % 2 == 0) r * .92f else r * .4f
                val x = center.x + cos(a) * rr; val y = center.y + sin(a) * rr
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color, style = stroke)
        }
        else -> drawCircle(color, r * .6f, center, style = stroke)
    }
}

@Composable
fun SymbolIcon(symbol: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawSymbol(symbol, color, center, this.size.minDimension / 2f) }
}
