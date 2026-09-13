package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.dynamic.blackout.application.EscapePublic
import ai.rever.boss.plugin.dynamic.blackout.application.EscapeStage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Controls the heads-up bar needs from the board. */
class HudActions(
    val onHint: () -> Unit,
    val onPause: () -> Unit,
    val onLog: () -> Unit,
    val onSound: () -> Unit,
    val onLeave: () -> Unit
)

@Composable
fun Hud(status: EscapePublic, muted: Boolean, logOpen: Boolean, compact: Boolean, actions: HudActions, modifier: Modifier = Modifier) {
    val live = status.outcome == "IN_PROGRESS"
    Row(modifier.fillMaxWidth().height(if (compact) 64.dp else 72.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Wordmark(compact)
        if (!compact) {
            VerticalHairline(Modifier.padding(vertical = 18.dp))
            StageStepper(status.stage, status.outcome, Modifier.weight(1f))
        } else Spacer(Modifier.weight(1f))
        PenaltyPips(status)
        AirGauge(status)
        if (live) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(Glyph.HINT, "HINT ${3 - status.hintsUsed}", actions.onHint, enabled = status.hintsUsed < 3 && !status.paused)
            IconButton(if (status.paused) Glyph.PLAY else Glyph.PAUSE, if (status.paused) "RESUME" else "PAUSE", actions.onPause, active = status.paused)
            IconButton(Glyph.LOG, "LOG", actions.onLog, active = logOpen)
            IconButton(Glyph.SOUND, if (muted) "MUTED" else "SOUND", actions.onSound, active = !muted)
            IconButton(Glyph.LEAVE, "LEAVE", actions.onLeave)
        }
    }
}

@Composable
fun Wordmark(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).background(Ink.Amber, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.BOLT, Ink.Ground, 18.dp)
        }
        if (!compact) Column {
            Text("BLACKOUT", style = TextStyle(color = Ink.Text, fontSize = 18.sp, fontFamily = Type.mono, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            Label("MAINT-04 · 02:14", Ink.Faint)
        }
    }
}

/** Four stations joined by a track that fills as the pair progresses. */
@Composable
fun StageStepper(stage: EscapeStage, outcome: String, modifier: Modifier = Modifier) {
    val done = if (outcome == "ESCAPED") 4 else stage.ordinal
    val fill by animateFloatAsState(done / 3f, tween(900, easing = FastOutSlowInEasing), label = "track")
    Box(modifier.height(48.dp)) {
        Canvas(Modifier.fillMaxWidth().height(16.dp).align(Alignment.TopCenter)) {
            val inset = size.width / 8f
            val y = size.height / 2f
            drawLine(Ink.Line, Offset(inset, y), Offset(size.width - inset, y), 2f)
            drawLine(Ink.Amber, Offset(inset, y), Offset(inset + (size.width - inset * 2) * fill.coerceIn(0f, 1f), y), 2f, StrokeCap.Round)
            repeat(4) { i ->
                val x = inset + (size.width - inset * 2) * i / 3f
                val complete = i < done
                val current = i == stage.ordinal && outcome == "IN_PROGRESS"
                drawCircle(Ink.Ground, 8f, Offset(x, y))
                drawCircle(if (complete || current) Ink.Amber else Ink.Edge, 6f, Offset(x, y), style = if (complete) androidx.compose.ui.graphics.drawscope.Fill else Stroke(2f))
                if (current) drawCircle(Ink.Amber.dim(.25f), 12f, Offset(x, y))
            }
        }
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            EscapeStage.entries.forEach { s ->
                val active = s == stage && outcome == "IN_PROGRESS"
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Label(Clues.title(s), if (active) Ink.Amber else if (s.ordinal < done) Ink.Dim else Ink.Faint)
                }
            }
        }
    }
}

@Composable
private fun PenaltyPips(status: EscapePublic) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("HINTS", Ink.Faint)
            Spacer(Modifier.width(4.dp))
            repeat(3) { i -> Lamp(i >= status.hintsUsed, Ink.Amber, 6.dp) }
        }
        Label(if (status.mistakes == 0) "NO MISTAKES" else "${status.mistakes} MISTAKE${if (status.mistakes == 1) "" else "S"}",
            if (status.mistakes == 0) Ink.Faint else Ink.Red.copy(alpha = .8f))
    }
}

/** Remaining air as a big clock with a depleting bar underneath. Red and pulsing in the last minute. */
@Composable
fun AirGauge(status: EscapePublic, modifier: Modifier = Modifier) {
    val total = status.mode.seconds.coerceAtLeast(1)
    val fraction by animateFloatAsState(status.secondsLeft / total.toFloat(), tween(600), label = "air")
    val critical = status.secondsLeft < 60 && status.outcome == "IN_PROGRESS"
    val transition = rememberInfiniteTransition(label = "air pulse")
    val pulse by transition.animateFloat(.55f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "air pulse value")
    val color by animateColorAsState(if (critical) Ink.Red else if (status.paused) Ink.Dim else Ink.Amber, tween(400), label = "air colour")
    Column(modifier.width(128.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(formatTime(status.secondsLeft), style = TextStyle(color = if (critical) color.copy(alpha = pulse) else color, fontSize = 28.sp,
            fontFamily = Type.mono, fontWeight = FontWeight.Medium, letterSpacing = 1.sp))
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            drawRoundRect(Ink.Line, cornerRadius = CornerRadius(2f))
            drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = CornerRadius(2f))
        }
        Label(
            when {
                status.outcome == "ESCAPED" -> "AIR TO SPARE"
                status.outcome != "IN_PROGRESS" -> status.outcome
                status.paused -> "PAUSED · PRACTICE"
                else -> "AIR · ${status.mode.name}"
            },
            if (critical) Ink.Red else Ink.Faint
        )
    }
}

/** The one line of consequence after each action: what happened and what it cost. */
@Composable
fun FeedbackBar(message: String, error: String, modifier: Modifier = Modifier, overlay: Boolean = false) {
    val text = error.ifBlank { message }
    val isError = error.isNotBlank()
    val penalty = !isError && text.contains("−")
    val tone = when { isError || penalty -> Ink.Red; else -> Ink.Amber }
    AnimatedVisibility(text.isNotBlank(), modifier, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (overlay) Ink.Void.copy(alpha = .88f) else Color.Transparent, RoundedCornerShape(6.dp))
                .background(tone.copy(alpha = .08f), RoundedCornerShape(6.dp))
                .border(1.dp, tone.copy(alpha = .3f), RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(6.dp).background(tone, RoundedCornerShape(3.dp)))
            Body(text, if (isError) Ink.Red else Ink.Text, Modifier.weight(1f))
            if (isError) Label("NOT ALLOWED YET", Ink.Red.copy(alpha = .7f)) else if (penalty) Label("PENALTY", Ink.Red.copy(alpha = .7f))
        }
    }
}

/** Banner shown while the clock is frozen. */
@Composable
fun PausedVeil(paused: Boolean, onResume: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(paused, modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlyphIcon(Glyph.PAUSE, Ink.Text, 36.dp)
                Display("PAUSED")
                Label("THE CLOCK IS FROZEN · THIS RUN NOW COUNTS AS PRACTICE", Ink.Dim)
                GameButton("RESUME", onResume, kind = ButtonKind.PRIMARY, icon = Glyph.PLAY)
            }
        }
    }
}
