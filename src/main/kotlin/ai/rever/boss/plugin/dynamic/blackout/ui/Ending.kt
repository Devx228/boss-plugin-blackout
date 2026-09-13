package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.dynamic.blackout.application.EscapeDebrief
import ai.rever.boss.plugin.dynamic.blackout.application.EscapePilotView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/** The stages of an ending, played in order. SKIP jumps straight to DONE. */
private enum class Phase { ROOM, STORY, TITLE, DEBRIEF, CREDITS, DONE }

private val WarmWhite = Color(0xFFFFF4E2)
private val Ember = Color(0xFF2A1D12)
private val BloodDim = Color(0xFFB4403A)

/**
 * Endings take their time. Escaping pushes the camera through the open door into light, tells
 * what really happened, then rolls credits. Failing lets the room die around the player, tells
 * them they are still inside, then rolls credits in red. The debrief and buttons come last.
 */
@Composable
fun EndingScreen(
    view: EscapePilotView,
    debrief: EscapeDebrief?,
    saved: String,
    companionBusy: Boolean,
    muted: Boolean,
    reducedMotion: Boolean,
    compact: Boolean,
    onAgain: () -> Unit,
    onMenu: () -> Unit
) {
    val escaped = view.status.outcome == "ESCAPED"
    val failed = view.status.outcome == "FAILED"
    val cinematic = when { escaped -> Cinematic.ESCAPE; failed -> Cinematic.TRAPPED; else -> Cinematic.NONE }
    val roomId = view.status.roomId
    val lines = view.epilogue
    var skipped by remember(roomId) { mutableStateOf(false) }
    var phase by remember(roomId) { mutableStateOf(Phase.ROOM) }
    var linesShown by remember(roomId) { mutableStateOf(0) }
    val progress = remember(roomId) { Animatable(0f) }
    val pace = if (reducedMotion) .45f else 1f
    fun ms(value: Int) = (value * pace).toLong()

    LaunchedEffect(roomId, skipped) {
        if (skipped) {
            progress.snapTo(1f); linesShown = lines.size; phase = Phase.DONE
            return@LaunchedEffect
        }
        launch { progress.animateTo(1f, tween(ms(if (failed) 5200 else 4200).toInt(), easing = LinearEasing)) }
        delay(ms(when { escaped -> 4400; failed -> 5600; else -> 900 }))
        phase = Phase.STORY
        for (i in lines.indices) { linesShown = i + 1; delay(ms(if (failed) 3600 else 3100)) }
        phase = Phase.TITLE; delay(ms(3000))
        phase = Phase.DEBRIEF; delay(ms(if (cinematic == Cinematic.NONE) 0 else 6500))
        if (cinematic != Cinematic.NONE) { phase = Phase.CREDITS; delay(ms(8000)) }
        phase = Phase.DONE
    }

    val soundPhase = when (phase) { Phase.ROOM -> 0; Phase.STORY -> 1; Phase.TITLE -> 2; Phase.CREDITS -> 3; Phase.DEBRIEF -> 4; Phase.DONE -> 5 }
    CinematicSound(cinematic, soundPhase, heartbeat = failed && phase <= Phase.TITLE, muted = muted)

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Ink.Void)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && phase != Phase.DONE) { skipped = true; true } else false
            }
            .focusable()
    ) {
        RoomScene(
            SceneState(view.status.stage, null, view.status.outcome, view.status.systems, view.effect,
                view.objects.filter { it.shared }.map { it.id }.toSet(), 0, view.discoveries, companionBusy,
                cinematic = cinematic, cinematicProgress = progress.value),
            reducedMotion, interactive = false, onSelect = {}, modifier = Modifier.fillMaxSize()
        )

        if (failed && phase == Phase.ROOM) AirZero(progress.value, reducedMotion, Modifier.align(Alignment.Center))

        AnimatedVisibility(phase == Phase.STORY || phase == Phase.TITLE, Modifier.fillMaxSize(), enter = fadeIn(tween(900)), exit = fadeOut(tween(700))) {
            val scrim = when (cinematic) { Cinematic.ESCAPE -> WarmWhite; Cinematic.TRAPPED -> Color.Black; Cinematic.NONE -> Ink.Void }
            val edge = if (cinematic == Cinematic.ESCAPE) Color(0xFFE8C99A) else Color(0xFF2A0604)
            Box(Modifier.fillMaxSize().background(scrim).background(Brush.radialGradient(listOf(Color.Transparent, edge.copy(alpha = .45f))))
                .padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                if (phase == Phase.STORY) StoryLines(lines, linesShown, cinematic, reducedMotion)
                else OutcomeTitle(cinematic, compact)
            }
        }

        AnimatedVisibility(phase == Phase.DEBRIEF || phase == Phase.DONE, Modifier.fillMaxSize(), enter = fadeIn(tween(1100)), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Ink.Void)) {
                Debrief(view, debrief, saved, cinematic, compact, showButtons = phase == Phase.DONE, onAgain = onAgain, onMenu = onMenu)
            }
        }

        AnimatedVisibility(phase == Phase.CREDITS, Modifier.fillMaxSize(), enter = fadeIn(tween(1200)), exit = fadeOut(tween(900))) {
            Credits(debrief, cinematic, reducedMotion)
        }

        if (phase != Phase.DONE) {
            val onLight = cinematic == Cinematic.ESCAPE && (phase == Phase.STORY || phase == Phase.TITLE || (phase == Phase.ROOM && progress.value > .6f))
            Row(Modifier.align(Alignment.TopEnd).padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("ESC", if (onLight) Ember.copy(alpha = .5f) else Ink.Faint)
                GameButton("SKIP ›", { skipped = true }, kind = ButtonKind.QUIET, height = 32.dp)
            }
        }
    }
}

/** A glitching readout while the last air goes. */
@Composable
private fun AirZero(progress: Float, reducedMotion: Boolean, modifier: Modifier) {
    val time by rememberAmbientTime(reducedMotion)
    val visible = progress > .55f
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(600), label = "air zero")
    val jitter = if (reducedMotion) 0f else (if (sin(time * 23f) > .85f) sin(time * 91f) * 10f else 0f)
    Column(modifier.graphicsLayer { this.alpha = alpha; translationX = jitter }, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AIR 0%", style = TextStyle(color = Ink.Red, fontSize = 56.sp, fontFamily = Type.mono, fontWeight = FontWeight.Bold, letterSpacing = 10.sp))
        Label("EMERGENCY RESERVE DEPLETED", BloodDim)
    }
}

@Composable
private fun StoryLines(lines: List<String>, shown: Int, cinematic: Cinematic, reducedMotion: Boolean) {
    val color = when (cinematic) { Cinematic.ESCAPE -> Ember; Cinematic.TRAPPED -> BloodDim; Cinematic.NONE -> Ink.Text }
    Column(Modifier.widthIn(max = 760.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        lines.take(shown).forEachIndexed { index, line ->
            StoryLine(line, latest = index == shown - 1, color = color, reducedMotion = reducedMotion)
        }
    }
}

/** The newest line types itself out; earlier lines settle back. */
@Composable
private fun StoryLine(line: String, latest: Boolean, color: Color, reducedMotion: Boolean) {
    var chars by remember(line) { mutableStateOf(if (reducedMotion) line.length else 0) }
    LaunchedEffect(line) { while (chars < line.length) { chars++; delay(26) } }
    val fade by animateFloatAsState(if (latest) 1f else .5f, tween(900), label = "story fade")
    val cursor = if (latest && chars < line.length) "▌" else ""
    Text(line.take(chars) + cursor, Modifier.graphicsLayer { alpha = fade }, textAlign = TextAlign.Center,
        style = TextStyle(color = color, fontSize = 21.sp, fontFamily = Type.mono, lineHeight = 32.sp, letterSpacing = .5.sp))
}

@Composable
private fun OutcomeTitle(cinematic: Cinematic, compact: Boolean) {
    val (text, color) = when (cinematic) {
        Cinematic.ESCAPE -> "YOU MADE IT.\nBOTH OF YOU." to Ember
        Cinematic.TRAPPED -> "YOU ARE STILL\nIN HERE." to Ink.Red
        Cinematic.NONE -> "UNTIL\nNEXT TIME." to Ink.Text
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text, textAlign = TextAlign.Center, style = TextStyle(color = color, fontSize = if (compact) 44.sp else 64.sp,
            fontFamily = Type.mono, fontWeight = FontWeight.Bold, letterSpacing = 5.sp, lineHeight = if (compact) 52.sp else 74.sp))
        if (cinematic == Cinematic.TRAPPED) Label("NO ONE ANSWERED THE KNOCK", BloodDim)
    }
}

@Composable
private fun Debrief(
    view: EscapePilotView,
    debrief: EscapeDebrief?,
    saved: String,
    cinematic: Cinematic,
    compact: Boolean,
    showButtons: Boolean,
    onAgain: () -> Unit,
    onMenu: () -> Unit
) {
    val escaped = cinematic == Cinematic.ESCAPE
    val failed = cinematic == Cinematic.TRAPPED
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 860.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Label(
                when { escaped -> "ESCAPED · ${view.status.mode.name}"; failed -> "TRAPPED · ${view.status.mode.name}"; else -> "RUN INTERRUPTED" },
                if (escaped) Ink.Amber else if (failed) Ink.Red else Ink.Dim
            )
            Display(when { escaped -> "YOU MADE IT. BOTH OF YOU."; failed -> "THE ROOM KEPT YOU."; else -> "UNTIL NEXT TIME." },
                if (failed) Ink.Red else Ink.Text, align = TextAlign.Center)
            view.ending?.let { Body(it, Ink.Dim, Modifier.widthIn(max = 620.dp), align = TextAlign.Center) }
            if (debrief != null) {
                RatingBadge(debrief.cooperationRating, escaped)
                StatsGrid(debrief, view.status.secondsLeft, compact)
                if (debrief.practice) Label("PRACTICE RUN · THE CLOCK WAS PAUSED", Ink.Faint)
                Timeline(debrief)
            }
            AnimatedVisibility(showButtons, enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 3 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GameButton(if (failed) "TRY AGAIN" else "PLAY AGAIN", onAgain, kind = ButtonKind.PRIMARY, icon = Glyph.RESET, height = 46.dp)
                        GameButton("MAIN MENU", onMenu, kind = ButtonKind.SECONDARY, height = 46.dp)
                    }
                    if (saved.isNotBlank()) Label(saved.uppercase(), Ink.Faint)
                }
            }
        }
    }
}

/** Credits roll up the screen: warm after an escape, red after being trapped. */
@Composable
private fun Credits(debrief: EscapeDebrief?, cinematic: Cinematic, reducedMotion: Boolean) {
    val roll = remember { Animatable(0f) }
    LaunchedEffect(Unit) { roll.animateTo(1f, tween(if (reducedMotion) 3500 else 7800, easing = LinearEasing)) }
    val trapped = cinematic == Cinematic.TRAPPED
    val accent = if (trapped) Ink.Red else Ink.Amber
    val soft = if (trapped) BloodDim else Ink.Dim
    val seat = debrief?.companion
    val companion = when {
        seat != null && seat.kind == "BUILT_IN" -> listOf(seat.provider, seat.model).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "BOSS model" }
        seat != null && seat.kind == "EXTERNAL_MCP" -> "An agent over MCP"
        else -> "Nobody on the line"
    }
    val credits = listOf(
        "" to "BLACKOUT",
        "A ROOM FOR TWO MINDS" to "",
        "EYES AND HANDS" to "You",
        "RECORDS AND REMOTE CONTROL" to companion,
        "INCIDENT" to (debrief?.incidentId ?: "UNKNOWN"),
        "THE PEOPLE BEFORE YOU" to "Mara · Ivo · Sena · Orin · Tali · Ren",
        "ROOM, PUZZLES AND STORY" to "Made for fun",
        "SOUND" to "Generated tones, no samples",
        "" to if (trapped) "THE ROOM IS STILL WAITING" else "THANK YOU FOR PLAYING"
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(if (trapped) Color.Black else Ink.Void)) {
        val travel = maxHeight.value + 900f
        Column(
            Modifier.fillMaxWidth().graphicsLayer { translationY = (maxHeight.value - roll.value * travel) * density },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(46.dp)
        ) {
            credits.forEachIndexed { index, (role, name) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (role.isNotBlank()) Label(role, soft, align = TextAlign.Center)
                    if (name.isNotBlank()) {
                        val big = index == 0 || index == credits.lastIndex
                        Text(name, textAlign = TextAlign.Center, style = TextStyle(
                            color = if (big) accent else Ink.Text, fontSize = if (index == 0) 54.sp else if (big) 24.sp else 19.sp,
                            fontFamily = Type.mono, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal, letterSpacing = if (big) 6.sp else 1.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingBadge(rating: String, escaped: Boolean) {
    val copy = when (rating) {
        "SYNCHRONIZED" -> "No mistakes, no hints, every clue shared. That's a real partnership."
        "COORDINATED" -> "You got out together, with a few bumps on the way."
        else -> "The pair never reached the door."
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.background(if (escaped) Ink.Amber.copy(alpha = .1f) else Ink.Surface, RoundedCornerShape(20.dp))
                .border(1.dp, if (escaped) Ink.Amber.copy(alpha = .6f) else Ink.Line, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) { Label(rating, if (escaped) Ink.Amber else Ink.Dim) }
        Label(copy, Ink.Faint)
    }
}

@Composable
private fun StatsGrid(debrief: EscapeDebrief, secondsLeft: Int, compact: Boolean) {
    val stats = listOf(
        "TIME" to formatTime(debrief.secondsUsed),
        "AIR LEFT" to formatTime(secondsLeft),
        "MISTAKES" to "${debrief.mistakes}",
        "HINTS" to "${debrief.hintsUsed}/3",
        "CLUES SHARED" to "${debrief.cluesShared}",
        "REMOTE ACTIONS" to "${debrief.remoteActions}",
        "DISCOVERIES" to "${debrief.discoveries}/2"
    )
    val perRow = if (compact) 2 else 4
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    StatBlock(label, value, Modifier.weight(1f), accent = label == "TIME" && debrief.outcome == "ESCAPED")
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Timeline(debrief: EscapeDebrief) {
    if (debrief.events.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(Ink.Surface.copy(alpha = .8f), RoundedCornerShape(8.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("HOW IT WENT", Ink.Dim, Modifier.weight(1f))
            Label("INCIDENT ${debrief.incidentId}", Ink.Faint)
        }
        debrief.events.takeLast(14).forEach { event ->
            val bad = event.detail.startsWith("Wrong")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Label(formatTime(event.elapsedSeconds), Ink.Faint, Modifier.width(44.dp))
                Box(Modifier.padding(top = 5.dp).size(6.dp).background(if (bad) Ink.Red else Ink.Amber.copy(alpha = .7f), RoundedCornerShape(3.dp)))
                Body(event.detail, if (bad) Ink.Red.copy(alpha = .85f) else Ink.Dim, Modifier.weight(1f))
                Label(Clues.title(event.stage), Ink.Faint)
            }
        }
    }
}
