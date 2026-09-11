package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.api.PluginStorageProvider
import ai.rever.boss.plugin.dynamic.blackout.application.*
import ai.rever.boss.plugin.dynamic.blackout.engine.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun BlackoutBoard(session: Session, storage: PluginStorageProvider?) {
    var beat by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf(Difficulty.OPERATOR) }
    var showReplay by remember { mutableStateOf(false) }
    var guided by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(Profile()) }
    var saveNote by remember { mutableStateOf("") }
    val store = remember(storage) { ProfileStore(storage) }

    LaunchedEffect(session) {
        while (true) {
            session.match?.tick()
            beat++
            delay(200)
        }
    }
    LaunchedEffect(store) { profile = store.load() }
    DisposableEffect(session) { onDispose { session.match?.pause() } }

    val match = remember(beat) { session.match }
    val view = remember(beat) { match?.humanView() }
    val last = remember(beat) { match?.lastRound() }
    val ended = view != null && view.public.outcome != "IN_PROGRESS"

    val idle = remember { MutableStateFlow(CrewStatus()) }
    val crewStatus by (session.crew?.status ?: idle).collectAsState()

    LaunchedEffect(match?.id, ended) {
        if (ended && match != null) {
            val public = match.publicView()
            profile = profile.record(public.outcome, match.roundsPlayed(), public.difficulty, public.practice)
            val kept = store.save(profile)
            val filed = store.saveReplay(match.id, match.replayJson())
            saveNote = if (filed && kept) "Replay and crew record saved to plugin storage."
            else "Storage unavailable; this replay lives in the open tab only."
        } else {
            saveNote = ""
        }
    }

    fun act(block: () -> Unit) {
        try {
            block(); error = ""; beat++
        } catch (e: IllegalArgumentException) {
            error = e.message ?: "Invalid action"
        } catch (e: IllegalStateException) {
            error = e.message ?: "Action unavailable"
        }
    }

    MaterialTheme(colors = darkColors(primary = Ink.Cyan, secondary = Ink.Amber, background = Ink.Void, surface = Ink.Panel)) {
        Surface(color = Ink.Void, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TopBar(view?.public, crewStatus)
                if (view == null || match == null) {
                    Briefing(
                        profile = profile,
                        crew = crewStatus,
                        difficulty = difficulty,
                        onDifficulty = { difficulty = it },
                        onStart = { practice ->
                            act {
                                guided = practice
                                showReplay = false
                                session.start(tutorial = practice, difficulty = difficulty)
                            }
                        }
                    )
                } else {
                    MatchScreen(
                        match = match,
                        view = view,
                        last = last,
                        ended = ended,
                        guided = guided,
                        crew = crewStatus,
                        saveNote = saveNote,
                        showReplay = showReplay,
                        onToggleReplay = { showReplay = !showReplay },
                        onToggleCrew = { on -> session.crew?.setEnabled(on); beat++ },
                        onAct = ::act,
                        onNewDuel = {
                            act {
                                guided = false
                                showReplay = false
                                session.start(difficulty = difficulty)
                            }
                        }
                    )
                }
                if (error.isNotEmpty()) {
                    Panel(accent = Ink.Red) { Body(error, Ink.Red) }
                }
                FieldManual()
            }
        }
    }
}

@Composable
private fun TopBar(public: PublicView?, crew: CrewStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text("BLACKOUT", color = Ink.Cyan, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
            Text("RIVAL CREWS", color = Ink.Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        }
        Spacer(Modifier.weight(1f))
        if (public != null) {
            val urgent = public.remainingSeconds in 1..20
            Pill("ROUND ${public.round} / ${public.maxRounds}", Ink.Text)
            Pill(public.difficulty.label, Ink.Amber)
            if (public.practice) Pill("PRACTICE", Ink.Violet, filled = true)
            when {
                public.outcome != "IN_PROGRESS" -> Pill(public.outcome, Ink.Green, filled = true)
                public.paused -> Pill("PAUSED", Ink.Violet, filled = true)
                else -> Pill("${public.remainingSeconds}s", if (urgent) Ink.Red else Ink.Cyan, filled = urgent)
            }
        } else {
            Pill(crew.label, if (crew.available) Ink.Violet else Ink.Faint)
        }
    }
}

@Composable
private fun Briefing(
    profile: Profile,
    crew: CrewStatus,
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    onStart: (Boolean) -> Unit
) {
    Panel(title = "HOW A CREW WINS", accent = Ink.Cyan) {
        Body("Two stations are running on emergency power and the diagnostics have split in two. You are the pilot: you see the power routes and your own deck, and you choose what to shoot, brace or patch. Your AI engineer holds the sensor sweep and the one polarity scan. Neither half can restore a circuit alone.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleCard(
                "YOU / PILOT",
                Ink.Cyan,
                listOf(
                    "See all four power routes: broken, insulated, crossed, high output.",
                    "Choose the action, the route and the compartment, then lock.",
                    "See only ONLINE, DAMAGED or DOWN on the rival deck.",
                    "Cannot tell whether the bus is hot or the polarity inverted."
                ),
                Modifier.weight(1f)
            )
            RoleCard(
                "AI / ENGINEER",
                Ink.Violet,
                listOf(
                    "Reads exact integrity and barrier figures for both stations.",
                    "Reads bus telemetry and spends the one polarity scan.",
                    "Sets thermal, polarity and output, then locks.",
                    "Cannot see the route map at all."
                ),
                Modifier.weight(1f)
            )
        }
        Body("Both orders lock, then the round resolves at the same instant as the rival crew's. A wrong configuration still burns the energy, so talk before you commit.")
    }
    Panel(title = "THE FOUR COMPARTMENTS", accent = Ink.Violet) {
        Subsystem.entries.forEach { system ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(system.label, color = Ink.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(112.dp))
                Note(system.role, Ink.Dim)
            }
        }
        Note("Every shot names a compartment. A barrier only absorbs hits on the compartment it sits on, so defence is a read on what the other crew wants next.")
    }
    Panel(title = "RIVAL CREW", accent = Ink.Amber) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { level ->
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (level == difficulty) Ink.Raised else Ink.Deep, RoundedCornerShape(8.dp))
                        .border(1.dp, if (level == difficulty) Ink.Amber else Ink.Edge, RoundedCornerShape(8.dp))
                        .clickable { onDifficulty(level) }
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Label(level.label, if (level == difficulty) Ink.Amber else Ink.Dim)
                        Note(level.blurb)
                    }
                }
            }
        }
        Note("The rival is scripted and deterministic from the match seed. It never reads your pending orders or your route map.")
    }
    Panel(title = "ENGINEER SEAT", accent = Ink.Violet) {
        Body(crew.label, if (crew.available) Ink.Text else Ink.Dim)
        Note(
            if (crew.available) "The host model can take the seat through the same four blackout_v1 tools an external agent uses. Switch it on once the duel starts, or leave it off and attach your own MCP agent."
            else "No host AI gateway is exposed here. Attach an MCP agent to the four blackout_v1 tools to fill the seat."
        )
    }
    Panel(title = "CREW RECORD", accent = Ink.Green) {
        Body(profile.headline())
        if (profile.clearedVeteran) Pill("VETERAN CLEARED", Ink.Green, filled = true)
        Note("Practice duels and abandoned runs are not counted.")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onStart(false) },
            colors = ButtonDefaults.buttonColors(backgroundColor = Ink.Cyan, contentColor = Ink.Void)
        ) { Text("START DUEL", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        Button(
            onClick = { onStart(true) },
            colors = ButtonDefaults.buttonColors(backgroundColor = Ink.Raised, contentColor = Ink.Text)
        ) { Text("GUIDED PRACTICE", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
    }
}

@Composable
private fun RoleCard(title: String, accent: Color, lines: List<String>, modifier: Modifier) {
    Column(
        modifier
            .background(Ink.Deep, RoundedCornerShape(8.dp))
            .border(1.dp, Ink.Edge, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Label(title, accent)
        lines.forEach { Note("- $it", Ink.Dim) }
    }
}

@Composable
private fun MatchScreen(
    match: Match,
    view: HumanView,
    last: Resolution?,
    ended: Boolean,
    guided: Boolean,
    crew: CrewStatus,
    saveNote: String,
    showReplay: Boolean,
    onToggleReplay: () -> Unit,
    onToggleCrew: (Boolean) -> Unit,
    onAct: ((() -> Unit) -> Unit),
    onNewDuel: () -> Unit
) {
    val public = view.public
    val locked = view.humanOrder != null
    var action by remember(public.matchId, public.round) { mutableStateOf(Action.FIRE) }
    var target by remember(public.matchId, public.round) { mutableStateOf(Subsystem.LIFE) }
    var route by remember(public.matchId, public.round) {
        mutableStateOf(view.routes.firstOrNull { !it.broken }?.id ?: "A")
    }
    val aimAtRival = action == Action.FIRE
    val aimAtSelf = action == Action.SHIELD || action == Action.REPAIR
    val needsTarget = aimAtRival || aimAtSelf

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShipMap(
                    title = "BLUE STATION",
                    crew = if (aimAtSelf) "Click a compartment to ${action.name.lowercase()}" else "You and your engineer",
                    report = public.blue,
                    delta = last?.let { diff(it.before.blue.integrity, it.after.blue.integrity) },
                    accent = Ink.Cyan,
                    selectable = aimAtSelf && !locked && !ended,
                    selected = if (aimAtSelf) target else null,
                    onSelect = { target = it },
                    modifier = Modifier.weight(1f)
                )
                ShipMap(
                    title = "ORANGE STATION",
                    crew = if (aimAtRival) "Click a compartment to fire on" else "Rival crew - ${public.difficulty.label}",
                    report = public.orange,
                    delta = last?.let { diff(it.before.orange.integrity, it.after.orange.integrity) },
                    accent = Ink.Amber,
                    selectable = aimAtRival && !locked && !ended,
                    selected = if (aimAtRival) target else null,
                    onSelect = { target = it },
                    modifier = Modifier.weight(1f)
                )
            }
            RelayMeter(public.blue.relayPoints, public.orange.relayPoints, Rules.RELAY_TARGET)
            if (ended) {
                Outcome(match, public, saveNote, showReplay, onToggleReplay, onNewDuel)
            } else {
                if (guided) GuidedSteps()
                RoutePanel(view, route, locked) { route = it }
                OrderPanel(
                    view = view,
                    action = action,
                    target = target,
                    route = route,
                    needsTarget = needsTarget,
                    onAction = { action = it },
                    onCommit = { onAct { match.commitHuman(public.round, HumanOrder(action, route, target)) } }
                )
                MatchControls(match, public, onAct)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EngineerConsole(crew, view.agentLocked, onToggleCrew)
            CrewComms(view.messages, view.messagesRemaining, !ended) { text ->
                onAct { match.humanMessage(text) }
            }
            if (public.events.isNotEmpty()) {
                Panel(title = "LAST ROUND", accent = Ink.Amber) {
                    public.events.forEach { Note(it, Ink.Dim) }
                }
            }
        }
    }
}

private fun diff(before: Grid, after: Grid) = Grid(
    reactor = after.reactor - before.reactor,
    shields = after.shields - before.shields,
    relay = after.relay - before.relay,
    life = after.life - before.life
)

@Composable
private fun GuidedSteps() {
    Panel(title = "GUIDED PRACTICE", accent = Ink.Violet) {
        Note("1. Ask the engineer whether the bus is hot, whether polarity is inverted, and which rival compartment is weakest. Nothing else tells you.")
        Note("2. Report back which routes are intact, insulated, crossed and high output.")
        Note("3. Pick the action, then click the compartment you want to hit, brace or patch.")
        Note("4. Choose a route that matches: insulated if hot, crossed only if polarity is inverted, never broken.")
        Note("5. Lock your order, let the engineer lock its configuration, then resume to resolve the round.")
    }
}

@Composable
private fun RoutePanel(view: HumanView, route: String, locked: Boolean, onRoute: (String) -> Unit) {
    Panel(title = "POWER ROUTES", accent = Ink.Cyan, trailing = {
        Note(if (locked) "LOCKED" else "REACTOR TO COMBAT BUS")
    }) {
        view.routes.forEach { candidate ->
            RouteRow(candidate, candidate.id == route, locked, { onRoute(candidate.id) })
        }
        Note("Insulation matters only on a hot bus. A crossed route needs inverted polarity. High output is required for a boosted order.")
    }
}

@Composable
private fun OrderPanel(
    view: HumanView,
    action: Action,
    target: Subsystem,
    route: String,
    needsTarget: Boolean,
    onAction: (Action) -> Unit,
    onCommit: () -> Unit
) {
    val public = view.public
    val committed = view.humanOrder
    val output = view.agentOutput ?: Output.NORMAL
    val cost = Rules.cost(action, output)
    val affordable = cost <= public.blue.energy && !(action == Action.HOLD && output == Output.BOOST)

    Panel(title = "COMBAT ORDER", accent = Ink.Cyan) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Action.entries.forEach { candidate ->
                ActionChip(candidate, candidate == action, committed != null, output, Modifier.weight(1f)) { onAction(candidate) }
            }
        }
        Body(orderLine(action, target, route, needsTarget), Ink.Text)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("COST $cost", if (affordable) Ink.Amber else Ink.Red, filled = !affordable)
            Pill("ENERGY ${public.blue.energy}", Ink.Amber)
            Pill(if (view.agentLocked) "ENGINEER LOCKED $output" else "ENGINEER OPEN", if (view.agentLocked) Ink.Green else Ink.Dim)
        }
        if (!view.agentLocked) {
            Note("Costs assume normal output. If the engineer locks a boost, the order costs one more and needs a high-output route.")
        }
        Button(
            onClick = onCommit,
            enabled = committed == null && affordable,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (committed != null) Ink.Raised else Ink.Cyan,
                contentColor = if (committed != null) Ink.Dim else Ink.Void
            )
        ) {
            Text(
                committed?.let { "PILOT LOCKED: ${it.action} ON ${it.target.label} VIA ROUTE ${it.route}" }
                    ?: "LOCK PILOT ORDER",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

private fun orderLine(action: Action, target: Subsystem, route: String, needsTarget: Boolean): String = when (action) {
    Action.FIRE -> "Fire on the rival ${target.label} through route $route."
    Action.SHIELD -> "Raise a barrier on your own ${target.label} through route $route."
    Action.REPAIR -> "Patch your own ${target.label} through route $route."
    Action.RELAY -> "Push for grid control through route $route. No compartment is targeted."
    Action.HOLD -> "Hold. No energy spent, no route needed."
}.let { if (needsTarget) it else it }

@Composable
private fun ActionChip(action: Action, selected: Boolean, locked: Boolean, output: Output, modifier: Modifier, onClick: () -> Unit) {
    val boosted = output == Output.BOOST
    val effect = when (action) {
        Action.FIRE -> if (boosted) "${Rules.FIRE_DAMAGE_BOOST} DMG" else "${Rules.FIRE_DAMAGE} DMG"
        Action.SHIELD -> if (boosted) "+${Rules.SHIELD_GAIN_BOOST} BARRIER" else "+${Rules.SHIELD_GAIN} BARRIER"
        Action.REPAIR -> if (boosted) "+${Rules.REPAIR_GAIN_BOOST} INTEGRITY" else "+${Rules.REPAIR_GAIN} INTEGRITY"
        Action.RELAY -> if (boosted) "PUSH 2" else "PUSH 1"
        Action.HOLD -> "RECOVER"
    }
    Column(
        modifier
            .background(if (selected) Ink.Cyan.copy(alpha = 0.16f) else Ink.Deep, RoundedCornerShape(7.dp))
            .border(1.dp, if (selected) Ink.Cyan else Ink.Edge, RoundedCornerShape(7.dp))
            .then(if (locked) Modifier else Modifier.clickable { onClick() })
            .padding(vertical = 9.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(action.name, color = if (selected) Ink.Cyan else Ink.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Note(effect, if (selected) Ink.Text else Ink.Faint)
        Note("${Rules.cost(action, output)}e", Ink.Faint)
    }
}

@Composable
private fun MatchControls(match: Match, public: PublicView, onAct: ((() -> Unit) -> Unit)) {
    var abandon by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onAct { if (public.paused) match.resume() else match.pause() } },
            colors = ButtonDefaults.buttonColors(backgroundColor = Ink.Raised, contentColor = Ink.Text)
        ) { Text(if (public.paused) "RESUME" else "PAUSE", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Button(
            onClick = { abandon = true },
            colors = ButtonDefaults.buttonColors(backgroundColor = Ink.Raised, contentColor = Ink.Red)
        ) { Text("ABANDON", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Note("Pausing switches the duel to practice and stops it counting.", Ink.Faint)
    }
    if (abandon) {
        AlertDialog(
            onDismissRequest = { abandon = false },
            backgroundColor = Ink.Panel,
            title = { Text("End this duel?", color = Ink.Text, fontSize = 15.sp) },
            text = { Body("The run is recorded as interrupted and does not enter your crew record.") },
            confirmButton = {
                TextButton(onClick = { onAct { match.interrupt() }; abandon = false }) { Text("END RUN", color = Ink.Red) }
            },
            dismissButton = {
                TextButton(onClick = { abandon = false }) { Text("KEEP PLAYING", color = Ink.Cyan) }
            }
        )
    }
}

@Composable
private fun Outcome(
    match: Match,
    public: PublicView,
    saveNote: String,
    showReplay: Boolean,
    onToggleReplay: () -> Unit,
    onNewDuel: () -> Unit
) {
    val accent = when (public.outcome) {
        "BLUE" -> Ink.Green
        "ORANGE" -> Ink.Red
        else -> Ink.Amber
    }
    Panel(title = "RESULT: ${public.outcome}", accent = accent) {
        Body(
            when (public.outcome) {
                "BLUE" -> "Your crew holds the grid."
                "ORANGE" -> "The rival crew holds the grid. Read the rounds below and change the plan."
                "DRAW" -> "Both stations finish level."
                else -> "Run interrupted. No competitive result."
            },
            accent
        )
        Note("Victory order: life support lost, then ${Rules.RELAY_TARGET} grid points, then at round ${Rules.MAX_ROUNDS} grid points, total integrity and barriers.")
        if (saveNote.isNotEmpty()) Note(saveNote)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onToggleReplay,
                colors = ButtonDefaults.buttonColors(backgroundColor = Ink.Raised, contentColor = Ink.Text)
            ) { Text(if (showReplay) "HIDE ROUNDS" else "REVIEW ROUNDS", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onNewDuel,
                colors = ButtonDefaults.buttonColors(backgroundColor = Ink.Cyan, contentColor = Ink.Void)
            ) { Text("NEW DUEL", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
    if (showReplay) {
        match.replay().rounds.forEach { round ->
            Panel(title = "ROUND ${round.before.round}", accent = Ink.Dim) {
                round.events.forEach { Note(it, Ink.Dim) }
                Note(
                    "Blue life ${round.after.blue.integrity.life}, grid ${round.after.blue.relayPoints}   /   " +
                        "Orange life ${round.after.orange.integrity.life}, grid ${round.after.orange.relayPoints}",
                    Ink.Faint
                )
            }
        }
    }
}

@Composable
private fun FieldManual() {
    var open by remember { mutableStateOf(false) }
    Panel(title = "FIELD MANUAL", accent = Ink.Dim, trailing = {
        Box(Modifier.clickable { open = !open }) { Note(if (open) "HIDE" else "SHOW", Ink.Cyan) }
    }) {
        if (open) {
            Note("FIRE 3 energy for ${Rules.FIRE_DAMAGE} damage to one compartment, boosted 4 for ${Rules.FIRE_DAMAGE_BOOST}.")
            Note("SHIELD 2 for +${Rules.SHIELD_GAIN} barrier on one compartment, boosted 3 for +${Rules.SHIELD_GAIN_BOOST}. REPAIR 2 for +${Rules.REPAIR_GAIN} integrity, boosted 3 for +${Rules.REPAIR_GAIN_BOOST}.")
            Note("RELAY 2 for push 1, boosted 3 for push 2. HOLD is free and must stay on normal output.")
            Note("Barriers and repairs apply before both shots land, and a barrier only covers the compartment it sits on. The stronger relay push scores one grid point; equal pushes score nothing.")
            Note("A damaged reactor yields 1 energy a round instead of 2 and none when it is down. A damaged shield array halves the barrier cap. A damaged relay mast loses a push level. Failing life support bleeds one more compartment every round.")
            Note("A failed circuit still spends the energy. Missing orders count as a hold; two incomplete rounds in a row forfeit the duel.")
            Note("Balance values are experimental and unplaytested. A local duel is a trusted sandbox, not a protected competitive match: anyone with access to this desktop can read the game's memory and files.", Ink.Faint)
        } else {
            Note("Costs, effects, resolution order and the honest limits of this build.")
        }
    }
}
