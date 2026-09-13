package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.api.PluginStorageProvider
import ai.rever.boss.plugin.dynamic.blackout.application.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Owns screen state and routes every action to the authoritative Escape. Drawing lives elsewhere. */
@Composable
fun EscapeBoard(session: Session, storage: PluginStorageProvider?) {
    val fallback = remember { MutableStateFlow(CrewStatus()) }
    val crew by (session.partner?.status ?: fallback).collectAsState()
    var view by remember { mutableStateOf(session.escape?.pilotView()) }
    var inMenu by remember { mutableStateOf(session.escape == null || session.escape?.finished() == true) }
    var knownRoom by remember { mutableStateOf(session.escape?.id) }
    var selected by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var logOpen by remember { mutableStateOf(false) }
    var setupOpen by remember { mutableStateOf(false) }
    var leaveOpen by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf("") }
    var persisted by remember { mutableStateOf<String?>(null) }
    var attached by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var reducedMotion by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(storage) {
        if (storage != null) {
            muted = runCatching { storage.getBoolean("settings.muted", false) }.getOrDefault(false)
            reducedMotion = runCatching { storage.getBoolean("settings.reducedMotion", false) }.getOrDefault(false)
        }
    }

    // Poll the authoritative room; the companion changes it from other threads.
    LaunchedEffect(session) {
        while (true) {
            val room = session.escape
            view = room?.pilotView()
            // A room started anywhere else (the harness, a previous tab) takes the screen out of the menu.
            if (room != null && room.id != knownRoom) { knownRoom = room.id; if (!room.finished()) inMenu = false }
            if (room != null && room.finished() && persisted != room.id) {
                persisted = room.id
                saved = if (storage == null) "Debrief kept for this session." else try {
                    storage.putJson("escape.${room.id}", Json.encodeToString(room.debrief()))
                    "Debrief saved on this device."
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { "Debrief could not be saved; it stays available this session." }
            }
            delay(150)
        }
    }

    // Walk the human to the device the objective is about each time the stage advances.
    val stage = view?.status?.stage
    val roomId = view?.status?.roomId
    LaunchedEffect(roomId, stage) {
        val room = session.escape ?: return@LaunchedEffect
        if (room.finished() || stage == null) return@LaunchedEffect
        val focus = Clues.focusFor(stage)
        try { room.inspect(focus); selected = focus; view = room.pilotView() } catch (_: GameError) {}
    }

    LaunchedEffect(error) { if (error.isNotBlank()) { delay(4500); error = "" } }
    DisposableEffect(session) { onDispose { session.escape?.pause() } }
    EffectSound(view?.effect, muted)

    fun act(block: (Escape) -> Unit) {
        try { session.escape?.let(block); error = ""; view = session.escape?.pilotView() }
        catch (e: GameError) { error = e.message }
    }
    fun select(id: String) = act { it.inspect(id); selected = id }
    fun begin(mode: RoomMode) {
        val descriptor = when {
            crew.enabled -> CompanionDescriptor("BUILT_IN", crew.provider, crew.model, verified = true)
            attached -> CompanionDescriptor("EXTERNAL_MCP", verified = false)
            else -> CompanionDescriptor()
        }
        knownRoom = session.startEscape(mode = mode, companion = descriptor).id
        selected = null; error = ""; saved = ""; logOpen = false; inMenu = false
        view = session.escape?.pilotView()
    }
    fun setMuted(value: Boolean) { muted = value; scope.launch { runCatching { storage?.putBoolean("settings.muted", value) } } }

    MaterialTheme(colors = darkColors(primary = Ink.Amber, background = Ink.Ground, surface = Ink.Surface, onSurface = Ink.Text, onBackground = Ink.Text)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Ink.Void)) {
            val compact = maxWidth < 1100.dp
            val current = view
            when {
                inMenu || current == null -> OpeningScreen(crew, attached, reducedMotion, compact, { attached = it }, { setupOpen = true }, ::begin)
                current.status.outcome != "IN_PROGRESS" -> EndingScreen(
                    current, runCatching { session.escape?.debrief() }.getOrNull(), saved, crew.busy, muted, reducedMotion, compact,
                    onAgain = { begin(current.status.mode) }, onMenu = { inMenu = true }
                )
                else -> {
                    val sceneState = SceneState(
                        current.status.stage, selected, current.status.outcome, current.status.systems, current.effect,
                        current.objects.filter { it.shared }.map { it.id }.toSet(), current.status.armSeconds,
                        current.discoveries, crew.busy, current.status.paused
                    )
                    val deviceActions = DeviceActions(
                        onShare = { act { it.report(selected.orEmpty()) } },
                        onPower = { order -> act { it.submitPower(order) } },
                        onPassword = { word -> act { it.submitPassword(word) } },
                        onStory = { order -> act { it.submitStory(order) } },
                        onExit = { act { it.escape() } },
                        onSelect = ::select
                    )
                    val hudActions = HudActions(
                        onHint = { act { it.hint() } },
                        onPause = { act { if (current.status.paused) it.resume() else it.pause() } },
                        onLog = { logOpen = !logOpen },
                        onSound = { setMuted(!muted) },
                        onLeave = { leaveOpen = true }
                    )
                    val companionPane: @Composable (Modifier) -> Unit = { modifier ->
                        CompanionPanel(current, crew, attached, reducedMotion, onSend = { text -> act { it.say(text) } },
                            onSetup = { setupOpen = true }, onRetry = { session.partner?.reset() }, modifier = modifier)
                    }
                    val scene: @Composable (Modifier) -> Unit = { modifier ->
                        Box(modifier) {
                            RoomScene(sceneState, reducedMotion, interactive = !current.status.paused, onSelect = ::select, modifier = Modifier.fillMaxSize())
                            ObjectiveChip(current, Modifier.align(Alignment.TopStart).padding(14.dp))
                            FeedbackBar(current.feedback, error, Modifier.align(Alignment.BottomCenter).padding(14.dp).widthIn(max = 780.dp), overlay = true)
                            PausedVeil(current.status.paused, { act { it.resume() } }, Modifier.fillMaxSize())
                        }
                    }
                    if (!compact) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Hud(current.status, muted, logOpen, compact = false, actions = hudActions)
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    scene(Modifier.weight(1f).fillMaxWidth().background(Ink.Ground, RoundedCornerShape(8.dp)))
                                    DeviceDock(current, selected, deviceActions, Modifier.fillMaxWidth().height(300.dp))
                                }
                                companionPane(Modifier.width(390.dp).fillMaxHeight())
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Hud(current.status, muted, logOpen, compact = true, actions = hudActions)
                            StageStepper(current.status.stage, current.status.outcome, Modifier.fillMaxWidth())
                            scene(Modifier.fillMaxWidth().height(400.dp))
                            DeviceDock(current, selected, deviceActions, Modifier.fillMaxWidth().height(320.dp))
                            companionPane(Modifier.fillMaxWidth().height(600.dp))
                        }
                    }
                    LogDrawer(logOpen, current, session.escape, { logOpen = false }, Modifier.align(Alignment.CenterEnd))
                    LeaveDialog(leaveOpen, onLeave = { act { it.interrupt() }; leaveOpen = false }, onStay = { leaveOpen = false })
                }
            }
            CompanionSetupDialog(setupOpen, crew, onEnabled = { session.partner?.setEnabled(it) }, onClose = { setupOpen = false })
        }
    }
}

/** The current objective pinned over the room, so the goal is never below the fold. */
@Composable
private fun ObjectiveChip(view: EscapePilotView, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Ink.Void.copy(alpha = .78f), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Label("${view.status.stage.ordinal + 1}/4", Ink.Amber)
        Body(view.status.objective, Ink.Text)
    }
}
