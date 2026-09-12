package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.api.PluginStorageProvider
import ai.rever.boss.plugin.dynamic.blackout.application.CrewStatus
import ai.rever.boss.plugin.dynamic.blackout.application.Note
import ai.rever.boss.plugin.dynamic.blackout.application.PilotView
import ai.rever.boss.plugin.dynamic.blackout.application.Profile
import ai.rever.boss.plugin.dynamic.blackout.application.ProfileStore
import ai.rever.boss.plugin.dynamic.blackout.application.Session
import ai.rever.boss.plugin.dynamic.blackout.application.SiteView
import ai.rever.boss.plugin.dynamic.blackout.engine.CaseFile
import ai.rever.boss.plugin.dynamic.blackout.engine.Difficulty
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun BlackoutBoard(session: Session, storage: PluginStorageProvider?) {
    val store = remember(storage) { ProfileStore(storage) }
    val fallback = remember { MutableStateFlow(CrewStatus()) }
    val crew by (session.archivist?.status ?: fallback).collectAsState()

    var profile by remember { mutableStateOf(Profile()) }
    var view by remember { mutableStateOf<PilotView?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var accusing by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { profile = store.load() }

    LaunchedEffect(Unit) {
        while (true) {
            val game = session.game
            val next = game?.pilotView()
            view = next
            if (game != null && next != null && next.outcome != "IN_PROGRESS" && recorded != next.caseId) {
                recorded = next.caseId
                val debrief = game.debrief()
                profile = profile.record(debrief.outcome, debrief.secondsUsed)
                store.save(profile)
                store.saveDebrief(debrief.caseId, Json.encodeToString(debrief))
            }
            delay(250)
        }
    }

    val current = view

    Box(Modifier.fillMaxSize().background(Ink.Ground)) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        if (current == null) {
            Opening(
                profile = profile,
                crew = crew,
                onSeat = { session.archivist?.setEnabled(it) },
                onStart = { difficulty ->
                    session.start(difficulty)
                    selected = null
                    accusing = false
                }
            )
        } else {
            TopBar(current, crew)
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1.55f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StationMap(
                        sites = current.sites,
                        selected = selected,
                        enabled = current.outcome == "IN_PROGRESS",
                        onSelect = { id ->
                            selected = id
                            val site = current.sites.first { it.id == id }
                            if (!site.inspected && current.inspectionsLeft > 0) {
                                runCatching { session.game?.inspect(id) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                    Detail(
                        site = current.sites.firstOrNull { it.id == selected },
                        view = current,
                        onReport = { id -> runCatching { session.game?.report(id) } },
                        onAccuse = { accusing = true }
                    )
                }
                Channel(
                    notes = current.notes,
                    crew = crew,
                    live = current.outcome == "IN_PROGRESS",
                    onSay = { text -> runCatching { session.game?.say(text) } },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        }

        // Overlays sit above the board rather than competing with it for height.
        if (current != null && current.outcome != "IN_PROGRESS") {
            Scrim {
                Closing(
                    view = current,
                    profile = profile,
                    onAgain = {
                        session.start(Difficulty.OPERATOR)
                        selected = null
                        accusing = false
                    }
                )
            }
        } else if (accusing && current != null) {
            Scrim {
                Accusation(
                    view = current,
                    onDismiss = { accusing = false },
                    onCall = { site, who ->
                        runCatching { session.game?.accuse(site, who) }
                        accusing = false
                    }
                )
            }
        }
    }
}

/** Dims the board and centres one panel over it. The only modal treatment in the game. */
@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Ground.copy(alpha = 0.88f))
            .padding(horizontal = 60.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.widthIn(max = 620.dp)) { content() }
    }
}

@Composable
private fun Opening(
    profile: Profile,
    crew: CrewStatus,
    onSeat: (Boolean) -> Unit,
    onStart: (Difficulty) -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "BLACKOUT",
            color = Ink.Text,
            fontSize = 44.sp,
            fontFamily = Type.family,
            fontWeight = FontWeight.Light,
            letterSpacing = 10.sp
        )
        Body(CaseFile.BRIEF, Ink.Dim, Modifier.widthIn(max = 620.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("THE SPLIT")
            Body("You walk the station and see what is physically there.", Ink.Text)
            Body("Your archivist reads the records and cannot see any of it.", Ink.Text)
            Body("Records say what people claimed. Only you can say what is true.", Ink.Dim)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("ARCHIVIST SEAT")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Toggle(
                    on = crew.enabled,
                    enabled = crew.available,
                    label = if (crew.available) crew.label else "No AI gateway on this host"
                ) { onSeat(it) }
            }
            Body(
                if (crew.available) {
                    "Switched off, the seat stays open for an agent attached over MCP."
                } else {
                    "Attach an agent over MCP to fill the seat."
                },
                Ink.Faint
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("OPEN A CASE")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Difficulty.entries.forEach { level ->
                    Action(
                        text = level.label + "  " + level.inspections + " WALKS  " + level.seconds / 60 + " MIN",
                        accent = level == Difficulty.OPERATOR
                    ) { onStart(level) }
                }
            }
        }

        Body(profile.headline(), Ink.Faint)
    }
}

@Composable
private fun TopBar(view: PilotView, crew: CrewStatus) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Readout("BATTERY", clock(view.secondsLeft), if (view.secondsLeft <= 60) Ink.Red else Ink.Text)
        Readout("WALKS LEFT", view.inspectionsLeft.toString(), if (view.inspectionsLeft == 0) Ink.Red else Ink.Text)
        Readout("CALLS LEFT", view.attemptsLeft.toString())
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Label("ARCHIVIST")
            Body(
                when {
                    !crew.available -> "MCP seat"
                    crew.busy -> "thinking"
                    crew.enabled -> crew.label
                    else -> "stood down"
                },
                if (crew.busy) Ink.Amber else Ink.Dim
            )
        }
    }
}

@Composable
private fun Detail(
    site: SiteView?,
    view: PilotView,
    onReport: (String) -> Unit,
    onAccuse: () -> Unit
) {
    Slab(Modifier.fillMaxWidth(), outlined = true) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                site == null -> {
                    Label("NO COMPARTMENT SELECTED")
                    Body("Pick a compartment to walk to. You have ${view.inspectionsLeft} walks left.", Ink.Dim)
                }
                !site.inspected -> {
                    Label(site.name.uppercase())
                    Body(
                        if (view.inspectionsLeft > 0) {
                            "Not searched yet. Selecting it again spends a walk."
                        } else {
                            "Not searched, and there is no battery left to walk there."
                        },
                        Ink.Dim
                    )
                    site.mark?.let { Body("Archivist: " + it.reason, Ink.Amber) }
                }
                else -> {
                    Label(site.name.uppercase())
                    Body(site.physical.orEmpty())
                    site.trace?.let { Body("Lying there: " + it + ".", Ink.Amber) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Action("SEND TO ARCHIVIST") { onReport(site.id) }
                        if (view.outcome == "IN_PROGRESS") Action("MAKE THE CALL", accent = true) { onAccuse() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Channel(
    notes: List<Note>,
    crew: CrewStatus,
    live: Boolean,
    onSay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(notes.size) {
        if (notes.isNotEmpty()) listState.animateScrollToItem(notes.size - 1)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("CREW CHANNEL")
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // An empty channel is the first thing a new player sees, so it carries the brief.
            if (notes.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Body(CaseFile.BRIEF, Ink.Dim)
                        Body(
                            "Your archivist reads the station records and cannot see the map. " +
                                "Ask where the work was signed off, walk there, and tell them what you actually find.",
                            Ink.Faint
                        )
                    }
                }
            }
            items(notes) { note ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Label(
                        note.role,
                        when (note.role) {
                            "ARCHIVIST" -> Ink.Amber
                            "SYSTEM" -> Ink.Red
                            else -> Ink.Faint
                        }
                    )
                    Body(note.text, if (note.role == "SYSTEM") Ink.Red else Ink.Text)
                }
            }
            if (crew.busy) {
                item { Label("ARCHIVIST IS READING...", Ink.Amber) }
            }
        }
        Input(
            value = draft,
            enabled = live,
            hint = "Tell the archivist what you see",
            onChange = { draft = it },
            onSubmit = {
                if (draft.isNotBlank()) { onSay(draft.trim()); draft = "" }
            }
        )
    }
}

@Composable
private fun Accusation(
    view: PilotView,
    onDismiss: () -> Unit,
    onCall: (String, String) -> Unit
) {
    var site by remember { mutableStateOf<String?>(null) }
    var who by remember { mutableStateOf<String?>(null) }

    Slab(Modifier.fillMaxWidth(), outlined = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("MAKE THE CALL", Ink.Amber)
            Body("Name the compartment where the fault is, and the crew member who caused it.", Ink.Dim)

            Label("COMPARTMENT")
            FlowOfChips(view.sites.map { it.id to it.name }, site) { site = it }

            Label("CREW")
            FlowOfChips(view.crew.map { it to it }, who) { who = it }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action("CONFIRM", accent = true, enabled = site != null && who != null) {
                    onCall(site!!, who!!)
                }
                Action("BACK") { onDismiss() }
            }
        }
    }
}

@Composable
private fun Closing(view: PilotView, profile: Profile, onAgain: () -> Unit) {
    val solved = view.outcome == "SOLVED"
    Slab(Modifier.fillMaxWidth(), outlined = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Display(
                when (view.outcome) {
                    "SOLVED" -> "CASE CLOSED"
                    "INTERRUPTED" -> "CASE ABANDONED"
                    else -> "THE STATION WENT COLD"
                },
                if (solved) Ink.Amber else Ink.Red
            )
            view.debrief?.let { Body(it) }
            Body(profile.headline(), Ink.Faint)
            Action("OPEN ANOTHER CASE", accent = true) { onAgain() }
        }
    }
}

// ---- small parts -----------------------------------------------------------------------

@Composable
private fun Action(
    text: String,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> Ink.Faint
        accent -> Ink.Amber
        else -> Ink.Dim
    }
    Box(
        Modifier
            .border(1.dp, tint.copy(alpha = if (enabled) 0.6f else 0.3f), RoundedCornerShape(2.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Label(text, tint)
    }
}

@Composable
private fun Toggle(on: Boolean, enabled: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .border(1.dp, if (on) Ink.Amber.copy(alpha = 0.6f) else Ink.Line, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled) { onChange(!on) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Label(if (on) "ON" else "OFF", if (on) Ink.Amber else Ink.Faint)
        Label(label, if (enabled) Ink.Dim else Ink.Faint)
    }
}

@Composable
private fun FlowOfChips(options: List<Pair<String, String>>, chosen: String?, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (value, label) ->
                    val picked = value == chosen
                    Box(
                        Modifier
                            .border(
                                1.dp,
                                if (picked) Ink.Amber else Ink.Line,
                                RoundedCornerShape(2.dp)
                            )
                            .clickable { onPick(value) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Label(label.uppercase(), if (picked) Ink.Amber else Ink.Dim)
                    }
                }
            }
        }
    }
}

@Composable
private fun Input(
    value: String,
    enabled: Boolean,
    hint: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Ink.Surface, RoundedCornerShape(3.dp))
            .border(1.dp, Ink.Line, RoundedCornerShape(3.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) Label(hint, Ink.Faint)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = Ink.Text,
                fontSize = Type.body,
                fontFamily = Type.family
            ),
            cursorBrush = SolidColor(Ink.Amber),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
