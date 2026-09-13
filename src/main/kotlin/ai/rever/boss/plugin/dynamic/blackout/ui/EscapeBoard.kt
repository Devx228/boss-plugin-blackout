package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.api.PluginStorageProvider
import ai.rever.boss.plugin.dynamic.blackout.application.*
import ai.rever.boss.plugin.dynamic.blackout.engine.EscapeRoom
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One room, one interaction at a time. The channel stays beside the world, not on top of it. */
@Composable fun EscapeBoard(session: Session, storage: PluginStorageProvider?) {
    val fallback = remember { MutableStateFlow(CrewStatus()) }
    val crew by (session.partner?.status ?: fallback).collectAsState()
    var view by remember { mutableStateOf<EscapePilotView?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var showJournal by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    var quit by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf("") }
    var persisted by remember { mutableStateOf<String?>(null) }
    var attached by remember { mutableStateOf(false) }
    LaunchedEffect(view?.status?.roomId) {
        session.escape?.takeIf { !it.finished() }?.let { room ->
            room.inspect("panel")
            selected="panel"
            view=room.pilotView()
        }
    }
    DisposableEffect(session) { onDispose { session.escape?.pause() } }
    LaunchedEffect(session) {
        while(true) {
            val room = session.escape
            view = room?.pilotView()
            if(room != null && room.finished() && persisted != room.id) {
                persisted = room.id
                saved = if(storage == null) "Debrief stays in this session; local storage is unavailable." else try {
                    storage.putJson("escape.${room.id}",Json.encodeToString(room.debrief()))
                    "Debrief saved on this device."
                } catch(e: CancellationException) { throw e }
                catch(_: Exception) { "Could not save the debrief. It remains available until you leave this session." }
            }
            delay(200)
        }
    }
    fun act(block: (Escape) -> Unit) {
        try { session.escape?.let(block); error=""; view=session.escape?.pilotView() }
        catch(e: GameError) { error=e.message }
    }
    fun begin() {
        session.startEscape(); selected=null; error=""; saved=""; showJournal=false
        view=session.escape?.pilotView()
    }
    MaterialTheme(colors=darkColors(primary=Ink.Amber,background=Ink.Ground,surface=Ink.Surface,onSurface=Ink.Text,onBackground=Ink.Text)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Ink.Ground)) {
            val compact = maxWidth < 820.dp
            val current=view
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if(compact) 16.dp else 28.dp),
                verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Display("BLACKOUT")
                        Label("A ROOM FOR TWO MINDS",Ink.Dim)
                    }
                    if(current!=null) Column(horizontalAlignment=Alignment.End) {
                        val s=current.status
                        Display(time(s.secondsLeft),if(s.secondsLeft<60 && s.outcome=="IN_PROGRESS") Ink.Red else Ink.Amber)
                        Label(if(s.outcome!="IN_PROGRESS") s.outcome else if(s.paused) "PAUSED · PRACTICE" else "AIR RESERVE",Ink.Dim)
                    } else Tag("01 / MAINTENANCE")
                }
                Divider(color=Ink.Line)
                if(current==null) {
                    OpeningRoom(crew,attached,onAttached={attached=it},onSetup={setup=true},onBegin={begin()})
                } else {
                    val live=current.status.outcome=="IN_PROGRESS"
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Label("${current.status.stage.ordinal+1} / 4",Ink.Amber)
                            Body(current.status.objective)
                        }
                        TextButton(onClick={showJournal=!showJournal}) { Label(if(showJournal) "CLOSE LOG" else "POCKET LOG",Ink.Dim) }
                    }
                    if(compact) {
                        World(current,selected,onSelect={id->act { it.inspect(id);selected=id }})
                        Interaction(current,selected,onReport={act { it.report(itId(selected)) }},
                            onPower={answer->act { it.submitPower(answer) }},onPassword={answer->act { it.submitPassword(answer) }},
                            onStory={answer->act { it.submitStory(answer) }},onExit={act { it.escape() }})
                        CompanionChannel(current,crew,onSend={text->act { it.say(text) }},onSetup={setup=true},
                            onRetry={session.partner?.reset()}, modifier=Modifier.fillMaxWidth())
                    } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                        Column(Modifier.weight(1.8f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            World(current,selected,onSelect={id->act { it.inspect(id);selected=id }})
                            Interaction(current,selected,onReport={act { it.report(itId(selected)) }},
                                onPower={answer->act { it.submitPower(answer) }},onPassword={answer->act { it.submitPassword(answer) }},
                                onStory={answer->act { it.submitStory(answer) }},onExit={act { it.escape() }})
                        }
                        CompanionChannel(current,crew,onSend={text->act { it.say(text) }},onSetup={setup=true},
                            onRetry={session.partner?.reset()},modifier=Modifier.weight(1f))
                    }
                    if(error.isNotBlank()) Body(error,Ink.Red)
                    if(showJournal) Journal(current,session.escape)
                    if(live) {
                        Divider(color=Ink.Line)
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick={act { it.hint() }},enabled=current.status.hintsUsed<3) { Label("HINT · −20s (${3-current.status.hintsUsed} LEFT)",Ink.Dim) }
                            TextButton(onClick={act { if(current.status.paused) it.resume() else it.pause() }}) { Label(if(current.status.paused) "RESUME" else "PAUSE",Ink.Dim) }
                            TextButton(onClick={quit=true}) { Label("LEAVE ROOM",Ink.Dim) }
                        }
                        if(current.status.practice) Label("PRACTICE RUN · THE CLOCK HAS BEEN PAUSED",Ink.Dim)
                    } else {
                        Divider(color=Ink.Line)
                        Display(if(current.status.outcome=="ESCAPED") "YOU MADE IT. BOTH OF YOU." else if(current.status.outcome=="FAILED") "THE ROOM GOES QUIET." else "UNTIL NEXT TIME.")
                        Body(current.ending.orEmpty(),Ink.Dim)
                        Body("${current.status.mistakes} wrong answers · ${current.status.hintsUsed} hints · ${time(session.escape?.debrief()?.secondsUsed ?: 0)} elapsed",Ink.Dim)
                        Body(saved,Ink.Dim)
                        Button(onClick={begin()}) { Label("ENTER A NEW ROOM",Ink.Ground) }
                    }
                }
            }
            if(setup) AlertDialog(onDismissRequest={setup=false},title={Display("YOUR COMPANION")},text={
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Body("${crew.label}",Ink.Amber)
                    Body("Use the configured BOSS model, or connect your own agent with the six blackout_v2 tools. Both receive the same clues and limits.")
                    Body("Required tools: observe · archive search · calculate · message · route power · arm exit. Web access is not needed for this room.",Ink.Amber)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Switch(checked=crew.enabled,onCheckedChange={session.partner?.setEnabled(it)},enabled=crew.available)
                        Body("Use BOSS companion")
                    }
                    Body(if(crew.available) "Enabling uses your configured model and may incur provider costs. The model only gets game tools." else "No gateway in this harness. Attach an external agent in BOSS to play together.",Ink.Dim)
                    Body("External prompt: Play my BLACKOUT companion. Call blackout_v2_observe, read the archive, and help me escape. Send suggestions through blackout_v2_message.",Ink.Dim)
                    Body("This is one local seat. Use either the built-in companion or one external agent, not both.",Ink.Dim)
                }
            },confirmButton={TextButton(onClick={setup=false}) { Label("DONE",Ink.Amber) }})
            if(quit) AlertDialog(onDismissRequest={quit=false},title={Display("LEAVE THIS ROOM?")},text={Body("Your progress will be recorded as interrupted.")},
                confirmButton={TextButton(onClick={act { it.interrupt() };quit=false}) { Label("LEAVE",Ink.Amber) }},
                dismissButton={TextButton(onClick={quit=false}) { Label("STAY",Ink.Dim) }})
        }
    }
}

@Composable private fun OpeningRoom(crew: CrewStatus, attached: Boolean, onAttached:(Boolean)->Unit,onSetup:()->Unit,onBegin:()->Unit) {
    Column(Modifier.widthIn(max=780.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        RoomScene(EscapeStage.POWER,null,false,false,{},Modifier.fillMaxWidth())
        Display("You woke up on the wrong side of a locked door.")
        Body(EscapeRoom.INTRO,Ink.Dim)
        Row(horizontalArrangement=Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) { Label("YOU",Ink.Amber);Body("Look around. Handle the locks. Share what you find.") }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) { Label("YOUR COMPANION",Ink.Amber);Body("Configure remote power. Decode clues. Authorize your exit.") }
        }
        Divider(color=Ink.Line)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Tag(if(crew.enabled) "COMPANION READY" else "COMPANION NOT SET",if(crew.enabled) Ink.Amber else Ink.Dim)
            TextButton(onClick=onSetup) { Label("CONNECT",Ink.Amber) }
        }
        if(!crew.enabled) Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(checked=attached,onCheckedChange=onAttached)
            Body("I have an external agent attached in BOSS.",Ink.Dim)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Button(onClick=onBegin,enabled=crew.enabled || attached) { Label("ENTER THE ROOM  →",Ink.Ground) }
            TextButton(onClick=onBegin) { Label("EXPLORE WITHOUT AGENT",Ink.Dim) }
        }
        Body("10 minutes. Three puzzles. One exit that takes both of you. Wrong answers cost 15 seconds; hints cost 20. No reflex tests.",Ink.Dim)
    }
}

@Composable private fun World(view: EscapePilotView, selected: String?, onSelect:(String)->Unit) {
    RoomScene(view.status.stage,selected,view.status.outcome=="ESCAPED",view.status.outcome=="IN_PROGRESS",onSelect,Modifier.fillMaxWidth())
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        view.inventory.forEach { Tag(it.uppercase(),Ink.Dim) }
    }
}

@Composable private fun Interaction(view: EscapePilotView, selected: String?, onReport:()->Unit,
    onPower:(List<String>)->Unit,onPassword:(String)->Unit,onStory:(List<String>)->Unit,onExit:()->Unit) {
    val obj=view.objects.find { it.id==selected }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Divider(color=Ink.Line)
        Body(view.feedback,Ink.Amber)
        if(view.status.outcome!="IN_PROGRESS") return@Column
        if(obj==null) { Body("Select an object in the room. Start with the breaker beside the door.",Ink.Dim);return@Column }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Label(obj.label.uppercase(),Ink.Text)
            TextButton(onClick=onReport) { Label("SHARE CLUE ↗",Ink.Amber) }
        }
        SelectionContainer { Body(obj.description,Ink.Dim) }
        when {
            selected=="panel" && view.status.stage==EscapeStage.POWER -> {
                Body("REMOTE SUPPLY: ${view.status.remotePower?.name ?: "WAITING FOR COMPANION"}",Ink.Amber)
                SequenceInput("STARTUP ORDER",view.symbols,view.status.roomId,onPower)
            }
            selected=="cabinet" && view.status.stage==EscapeStage.CABINET -> {
                var word by remember(view.status.roomId) { mutableStateOf("") }
                OutlinedTextField(value=word,onValueChange={if(it.length<=12 && it.all(Char::isLetter)) word=it.uppercase()},
                    label={Label("PASSWORD",Ink.Dim)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii),modifier=Modifier.fillMaxWidth())
                Button(onClick={onPassword(word)},enabled=word.isNotBlank()) { Label("UNLOCK CABINET",Ink.Ground) }
            }
            selected=="recorder" && view.status.stage==EscapeStage.STORY -> {
                SequenceInput("WHAT HAPPENED FIRST → LAST",view.fragments.map { it.id }.sorted(),view.status.roomId,onStory)
            }
            selected=="door" && view.status.stage==EscapeStage.EXIT -> {
                Body(if(view.status.armSeconds>0) "Release armed · ${view.status.armSeconds}s. Turn the handle now." else "Ask your companion to authorize release, then turn the handle.",Ink.Amber)
                Button(onClick=onExit,enabled=view.status.armSeconds>0 && !view.status.paused) { Label("TURN THE HANDLE →",Ink.Ground) }
            }
            else -> Body(if(selected=="door") "The door needs power, the recovered log, and your companion's authorization." else "This part of the room is already solved. Follow the current objective.",Ink.Dim)
        }
    }
}

/** Click-to-order keeps mouse and keyboard equivalent; no drag precision or inaccessible Canvas input. */
@Composable private fun SequenceInput(label: String, options: List<String>, key: String, onSubmit:(List<String>)->Unit) {
    var order by remember(key,label) { mutableStateOf<List<String>>(emptyList()) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Label(label,Ink.Dim)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (0..2).forEach { i -> Box(Modifier.weight(1f).border(1.dp,if(order.size>i) Ink.Amber else Ink.Line).padding(12.dp),contentAlignment=Alignment.Center) {
                Body(order.getOrNull(i) ?: "${i+1} · —",if(order.size>i) Ink.Text else Ink.Faint)
            } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            options.forEach { symbol -> OutlinedButton(onClick={order=order+symbol},enabled=symbol !in order) { Body(symbol) } }
            TextButton(onClick={order=emptyList()},enabled=order.isNotEmpty()) { Label("RESET",Ink.Dim) }
        }
        Button(onClick={onSubmit(order);order=emptyList()},enabled=order.size==3) { Label("TRY SEQUENCE",Ink.Ground) }
    }
}

@Composable private fun CompanionChannel(view: EscapePilotView, crew: CrewStatus, onSend:(String)->Unit,onSetup:()->Unit,
    onRetry:()->Unit,modifier:Modifier=Modifier) {
    var text by remember(view.status.roomId) { mutableStateOf("") }
    val scroll=rememberScrollState()
    LaunchedEffect(view.notes.size) { scroll.animateScrollTo(scroll.maxValue) }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Label("COMPANION",Ink.Amber)
            TextButton(onClick=onSetup) { Label("CONNECTION",Ink.Dim) }
        }
        Body(if(crew.enabled) if(crew.busy) "Reading. Thinking. Still here." else crew.status else "External companion channel",Ink.Dim)
        Column(Modifier.heightIn(min=100.dp,max=330.dp).verticalScroll(scroll),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(view.notes.isEmpty()) Body("You hear a voice through the terminal. Share something from the room to begin.",Ink.Faint)
            view.notes.forEach { note -> Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Label(note.role,if(note.role=="COMPANION") Ink.Amber else Ink.Dim)
                SelectionContainer { Body(note.text) }
            } }
        }
        if(view.status.outcome=="IN_PROGRESS") {
            OutlinedTextField(value=text,onValueChange={if(it.length<=500) text=it},label={Label("TALK TO YOUR COMPANION",Ink.Dim)},
                modifier=Modifier.fillMaxWidth(),maxLines=4)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={onSend(text);text=""},enabled=text.isNotBlank()) { Label("SEND",Ink.Ground) }
                if(crew.enabled && !crew.busy) TextButton(onClick=onRetry) { Label("RETRY COMPANION",Ink.Dim) }
            }
        }
        if(crew.enabled) Label("${crew.label} · ${crew.toolCalls} CALLS",Ink.Faint)
    }
}

@Composable private fun Journal(view: EscapePilotView, room: Escape?) {
    Column(Modifier.fillMaxWidth().border(1.dp,Ink.Line).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Label("POCKET LOG",Ink.Amber)
        Body("${view.status.stage.ordinal} / 3 puzzles solved · ${view.status.mistakes} wrong answers · ${view.status.hintsUsed} hints",Ink.Dim)
        if(view.status.outcome=="IN_PROGRESS") {
            Body("Share clue sends only the object you chose. Your companion never receives the room automatically.",Ink.Dim)
            view.inventory.forEach { Body("✓ $it") }
        } else room?.debrief()?.events?.forEach { Body("${time(it.elapsedSeconds)}  ${it.detail}",Ink.Dim) }
    }
}
private fun time(seconds:Int) = "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"
private fun itId(selected:String?) = selected ?: ""
