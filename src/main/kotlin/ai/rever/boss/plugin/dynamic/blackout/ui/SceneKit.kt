package ai.rever.boss.plugin.dynamic.blackout.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Seconds since the composable appeared, sampled at about 30 frames a second. Frozen when motion is reduced. */
@Composable
fun rememberAmbientTime(reducedMotion: Boolean): State<Float> = produceState(0f, reducedMotion) {
    if (reducedMotion) return@produceState
    val start = System.nanoTime()
    while (true) {
        value = (System.nanoTime() - start) / 1_000_000_000f
        delay(33)
    }
}

/**
 * Progress of the most recent effect, from 0 when it fires to 1 when it has finished. An effect
 * that already existed when the screen appeared does not replay.
 */
@Composable
fun rememberEffectProgress(effectId: Long?, durationMs: Int = 1400): State<Float> {
    val progress = remember { Animatable(1f) }
    val initial = remember { effectId }
    LaunchedEffect(effectId) {
        if (effectId != null && effectId != initial) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
        }
    }
    return progress.asState()
}

/**
 * A virtual stage 1600 wide, letterboxed into whatever space the layout gives. Only the band
 * from viewTop to viewTop + viewHeight has to fit, so a tighter band draws the room larger.
 */
class Stage(val canvas: Size, val viewTop: Float = 0f, val viewHeight: Float = 820f, val virtualWidth: Float = 1600f) {
    val scale: Float = minOf(canvas.width / virtualWidth, canvas.height / viewHeight)
    val originX: Float = (canvas.width - virtualWidth * scale) / 2f
    val originY: Float = (canvas.height - viewHeight * scale) / 2f - viewTop * scale
    fun x(v: Float) = originX + v * scale
    fun y(v: Float) = originY + v * scale
    fun at(vx: Float, vy: Float) = Offset(x(vx), y(vy))
    fun len(v: Float) = v * scale
    fun rect(vx: Float, vy: Float, vw: Float, vh: Float) = Rect(x(vx), y(vy), x(vx + vw), y(vy + vh))
    fun toVirtual(p: Offset) = Offset((p.x - originX) / scale, (p.y - originY) / scale)
}

/** Deterministic pseudo-random value in [0, 1) so particles do not jitter between frames. */
fun hash(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0x7fffffff) / 2147483647f
}

fun Color.dim(amount: Float): Color = copy(alpha = (alpha * amount).coerceIn(0f, 1f))

fun lerpColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k, a.alpha + (b.alpha - a.alpha) * k)
}

/** A brushed metal plate with a lit top edge and a shadowed bottom edge. */
fun DrawScope.metalPlate(rect: Rect, base: Color = Ink.Metal, light: Float = 1f, corner: Float = 6f) {
    drawRoundRect(
        Brush.verticalGradient(listOf(lerpColor(base, Ink.MetalLight, .35f * light), base, lerpColor(base, Ink.MetalDark, .6f)), rect.top, rect.bottom),
        rect.topLeft, rect.size, CornerRadius(corner)
    )
    drawLine(Color.White.copy(alpha = .06f * light), Offset(rect.left + corner, rect.top + 1f), Offset(rect.right - corner, rect.top + 1f), 1.2f)
    drawLine(Color.Black.copy(alpha = .45f), Offset(rect.left + corner, rect.bottom - 1f), Offset(rect.right - corner, rect.bottom - 1f), 1.5f)
    drawRoundRect(Color.Black.copy(alpha = .5f), rect.topLeft, rect.size, CornerRadius(corner), style = Stroke(1.2f))
}

fun DrawScope.screw(center: Offset, radius: Float, light: Float = 1f) {
    drawCircle(lerpColor(Ink.MetalDark, Ink.MetalLight, .5f * light), radius, center)
    drawCircle(Color.Black.copy(alpha = .5f), radius, center, style = Stroke(radius * .25f))
    drawLine(Color.Black.copy(alpha = .6f), Offset(center.x - radius * .6f, center.y - radius * .2f), Offset(center.x + radius * .6f, center.y + radius * .2f), radius * .3f)
}

fun DrawScope.screwsAround(rect: Rect, radius: Float, inset: Float, light: Float = 1f) {
    screw(Offset(rect.left + inset, rect.top + inset), radius, light)
    screw(Offset(rect.right - inset, rect.top + inset), radius, light)
    screw(Offset(rect.left + inset, rect.bottom - inset), radius, light)
    screw(Offset(rect.right - inset, rect.bottom - inset), radius, light)
}

/** Soft circular glow, strongest in the middle. */
fun DrawScope.glow(center: Offset, radius: Float, color: Color, strength: Float = 1f) {
    if (strength <= 0.001f || radius <= 0f) return
    drawCircle(Brush.radialGradient(listOf(color.dim(.55f * strength), color.dim(.18f * strength), Color.Transparent), center, radius), radius, center)
}

/** A cone of light from a point, widening downward, used for ceiling lamps. */
fun DrawScope.lightCone(apex: Offset, halfWidthTop: Float, halfWidthBottom: Float, bottomY: Float, color: Color, strength: Float) {
    if (strength <= 0.001f) return
    val path = Path().apply {
        moveTo(apex.x - halfWidthTop, apex.y)
        lineTo(apex.x + halfWidthTop, apex.y)
        lineTo(apex.x + halfWidthBottom, bottomY)
        lineTo(apex.x - halfWidthBottom, bottomY)
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(color.dim(.32f * strength), color.dim(.1f * strength), Color.Transparent), apex.y, bottomY))
}

fun DrawScope.hazardStripes(rect: Rect, stripe: Float, color: Color = Ink.Amber.dim(.5f)) {
    val path = Path()
    var x = rect.left - rect.height
    var i = 0
    while (x < rect.right) {
        if (i % 2 == 0) {
            path.moveTo(x, rect.bottom); path.lineTo(x + stripe, rect.bottom)
            path.lineTo(x + stripe + rect.height, rect.top); path.lineTo(x + rect.height, rect.top); path.close()
        }
        x += stripe; i++
    }
    drawContext.canvas.save()
    drawContext.canvas.clipRect(rect)
    drawPath(path, color)
    drawContext.canvas.restore()
}

fun DrawScope.grille(rect: Rect, slats: Int, color: Color = Color.Black.copy(alpha = .55f)) {
    val gap = rect.height / (slats + 1)
    repeat(slats) { i ->
        val y = rect.top + gap * (i + 1)
        drawLine(color, Offset(rect.left + 4f, y), Offset(rect.right - 4f, y), gap * .45f, StrokeCap.Round)
    }
}

/** Cable with a slight sag between two points. */
fun DrawScope.cable(from: Offset, to: Offset, sag: Float, color: Color, width: Float) {
    val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f + sag)
    val path = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(from.x, mid.y, to.x, mid.y, to.x, to.y)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
}

/** Point along the same sagging cable, for pulses travelling on it. */
fun cablePoint(from: Offset, to: Offset, sag: Float, t: Float): Offset {
    val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f + sag)
    val c1 = Offset(from.x, mid.y); val c2 = Offset(to.x, mid.y)
    val u = 1 - t
    return Offset(
        u * u * u * from.x + 3 * u * u * t * c1.x + 3 * u * t * t * c2.x + t * t * t * to.x,
        u * u * u * from.y + 3 * u * u * t * c1.y + 3 * u * t * t * c2.y + t * t * t * to.y
    )
}

/** Drifting dust motes inside a rectangle. */
fun DrawScope.dust(area: Rect, count: Int, time: Float, color: Color, visibility: Float) {
    if (visibility <= 0.01f) return
    repeat(count) { i ->
        val speed = .006f + hash(i * 7) * .014f
        val px = (hash(i) + sin(time * .3f + i) * .02f).mod(1f)
        val py = (hash(i * 3) - time * speed).mod(1f)
        val twinkle = .4f + .6f * abs(sin(time * (.6f + hash(i * 11)) + i))
        drawCircle(color.dim(visibility * twinkle * .55f), 1f + hash(i * 5) * 1.8f, Offset(area.left + px * area.width, area.top + py * area.height))
    }
}

/** Rising smoke wisps from a source line: soft radial puffs that fade in and out. */
fun DrawScope.smoke(origin: Offset, spread: Float, height: Float, time: Float, density: Float) {
    if (density <= 0.01f) return
    repeat(10) { i ->
        val cycle = ((time * .05f + hash(i * 13)) % 1f)
        val x = origin.x + (hash(i) - .5f) * spread + sin(time * .4f + i) * spread * .06f * cycle
        val y = origin.y - cycle * height
        val radius = spread * (.05f + cycle * .09f)
        val alpha = (1f - cycle) * cycle * 4f * .045f * density
        val center = Offset(x, y)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF9A9CA3).dim(alpha), Color.Transparent), center, radius), radius, center)
    }
}

/** Sparks thrown from a point; progress runs 0..1 over the burst. */
fun DrawScope.sparks(origin: Offset, progress: Float, reach: Float, color: Color = Ink.Amber) {
    if (progress >= 1f) return
    val fade = 1f - progress
    repeat(18) { i ->
        val angle = -PI.toFloat() * (.1f + hash(i * 17) * .8f)
        val distance = reach * (.3f + hash(i * 19) * .7f) * progress
        val gravity = reach * .6f * progress * progress
        val tip = Offset(origin.x + kotlin.math.cos(angle) * distance, origin.y + sin(angle) * distance + gravity)
        val tail = Offset(origin.x + kotlin.math.cos(angle) * distance * .8f, origin.y + sin(angle) * distance * .8f + gravity * .8f)
        drawLine(color.dim(fade), tail, tip, 2f, StrokeCap.Round)
    }
    glow(origin, reach * .6f * fade, color, fade)
}

fun DrawScope.dashedRect(rect: Rect, color: Color, width: Float, phase: Float, corner: Float = 8f) {
    drawRoundRect(color, rect.topLeft, rect.size, CornerRadius(corner),
        style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), phase)))
}

/** Screen-space vignette pulling the edges into darkness. */
fun DrawScope.vignette(strength: Float) {
    val radius = maxOf(size.width, size.height) * .75f
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.dim(.85f * strength)), center, radius))
}

/** Thin horizontal scanlines for screens. */
fun DrawScope.scanlines(rect: Rect, spacing: Float, alpha: Float) {
    var y = rect.top
    while (y < rect.bottom) {
        drawLine(Color.Black.dim(alpha), Offset(rect.left, y), Offset(rect.right, y), 1f)
        y += spacing
    }
}

/** Flicker between 0 and 1 driven by layered sines; used for emergency lamps. */
fun flicker(time: Float, seed: Int): Float {
    val base = .78f + .12f * sin(time * 2.1f + seed) + .06f * sin(time * 7.3f + seed * 2)
    val drop = if (((time * 1.7f + seed) % 5f) < .08f) .5f else 0f
    return (base - drop).coerceIn(0f, 1f)
}

