package ai.rever.boss.plugin.dynamic.blackout

import ai.rever.boss.plugin.dynamic.blackout.application.*
import ai.rever.boss.plugin.dynamic.blackout.mcp.EscapeTools
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class EscapeTest {
    private fun records(game: Escape) = game.archive("ALL")

    /** Complete all puzzles using only pilot views, shared clues, manuals and legal role methods. */
    private fun solveToDoor(game: Escape) {
        val manual = records(game)
        game.inspect("panel"); game.report("panel")
        val panel = game.pilotView().objects.first { it.id == "panel" }.description
        val watts = Regex("Load: (\\d+) W").find(panel)!!.groupValues[1].toInt()
        val volts = Regex("Supply: (\\d+) V").find(panel)!!.groupValues[1].toInt()
        val amps = game.calculate("amps", Arithmetic.DIVIDE, watts, volts).detail.substringAfter(" = ").toDouble().toInt()
        game.routePower("power", when { amps <= 6 -> PowerMode.LOW; amps <= 12 -> PowerMode.NORMAL; else -> PowerMode.HIGH })
        val priority = Regex("(SUN|WAVE|LEAF|MOON|EYE|STAR) = ([1-6])").findAll(manual[0]).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        val symbols = game.pilotView().symbols
        assertTrue(game.submitPower(symbols.sortedBy { priority.getValue(it) }))

        game.inspect("cabinet"); game.report("cabinet")
        val cabinet = game.pilotView().objects.first { it.id == "cabinet" }.description
        val cipher = Regex("reads ([A-Z]+)").find(cabinet)!!.groupValues[1]
        val shift = Regex("shifted forward ([1-5])").find(manual[1])!!.groupValues[1].toInt()
        game.tuneDecoder("decoder", shift)
        val decoded = cipher.map { 'A' + ((it - 'A' - shift + 26) % 26) }.joinToString("")
        assertTrue(game.submitPassword(decoded))

        game.inspect("recorder"); game.report("recorder")
        val recorder = game.pilotView().objects.first { it.id == "recorder" }.description
        val waveform = Regex("Waveform: ([A-Z]+)").find(recorder)!!.groupValues[1]
        val channel = Regex("$waveform = ([A-F])").find(manual[2])!!.groupValues[1]
        game.syncRecorder("recorder", channel)
        val fragments = game.pilotView().fragments
        fun rank(text: String) = when {
            listOf("alarm", "ruptured", "sensor found").any { it in text } -> 0
            listOf("Power is gone", "pumps stopped", "Airflow is gone").any { it in text } -> 1
            else -> 2
        }
        assertTrue(game.submitStory(fragments.sortedBy { rank(it.text) }.map { it.id }))
        assertEquals(EscapeStage.EXIT, game.status().stage)
    }

    private fun exitChannel(game: Escape): String {
        game.inspect("door"); game.report("door")
        val seal = Regex("Routing seal: ([A-Z]+)").find(game.pilotView().objects.first { it.id == "door" }.description)!!.groupValues[1]
        return Regex("$seal = ([A-F])").find(game.archive("EXIT").single())!!.groupValues[1]
    }

    @Test fun oneHundredLegalCrewsEscapeAcrossEveryIncident() {
        val incidents = mutableSetOf<String>()
        repeat(100) { seed ->
            val room = Escape(seed.toLong(), clock={0})
            solveToDoor(room)
            room.armExit("exit", exitChannel(room)); assertTrue(room.escape())
            incidents += room.debrief().incidentId
            assertEquals(0, room.debrief().mistakes)
        }
        assertEquals(setOf("COOLANT", "FLOOD", "SPORE"), incidents)
    }

    @Test fun everyPuzzleRequiresAnAgentMutation() {
        val room = Escape(42, clock={0}); val manual = records(room)
        room.inspect("panel")
        assertEquals("REMOTE_POWER_REQUIRED", assertFailsWith<GameError> { room.submitPower(room.pilotView().symbols) }.code)
        val panel=room.pilotView().objects.first{it.id=="panel"}.description
        val amps=Regex("Load: (\\d+)").find(panel)!!.groupValues[1].toInt()/Regex("Supply: (\\d+)").find(panel)!!.groupValues[1].toInt()
        room.routePower("p",if(amps<=6) PowerMode.LOW else if(amps<=12) PowerMode.NORMAL else PowerMode.HIGH)
        val pri=Regex("(SUN|WAVE|LEAF|MOON|EYE|STAR) = ([1-6])").findAll(manual[0]).associate{it.groupValues[1] to it.groupValues[2].toInt()}
        room.submitPower(room.pilotView().symbols.sortedBy{pri.getValue(it)})
        room.inspect("cabinet")
        assertEquals("REMOTE_DECODER_REQUIRED",assertFailsWith<GameError>{room.submitPassword("LIGHT")}.code)
    }

    @Test fun physicalCluesCrossOnlyWhenShared() {
        var time=0L; val room=Escape(42,clock={time})
        assertFailsWith<GameError>{room.report("panel")}
        room.inspect("panel")
        val hiddenSymbols=room.pilotView().symbols
        val before=Json.encodeToString(room.observe())
        assertTrue(room.observeAgentCluesAreEmptyForTest(before, hiddenSymbols))
        room.report("panel"); time+=1000
        assertTrue("Fitted symbols" in room.observe().reported.single().text)
    }

    @Test fun standardAndShowcaseUseTheirOwnTimingRules() {
        val standard=Escape(1,RoomMode.STANDARD,clock={0}); standard.inspect("panel")
        standard.hint(); assertEquals(580,standard.status().secondsLeft)
        val showcase=Escape(1,RoomMode.SHOWCASE,clock={0}); showcase.inspect("panel")
        showcase.hint(); assertEquals(230,showcase.status().secondsLeft)
        assertEquals(10,showcase.mode.mistakePenalty)
    }

    @Test fun wrongRemoteActionsPenalizeAndRemainRecoverable() {
        val room=Escape(7,clock={0}); solvePower(room)
        val correct=Regex("Tune the decoder to ([1-5])").find(room.archive("CABINET").single())!!.groupValues[1].toInt()
        val wrong=if(correct==1) 2 else 1
        room.tuneDecoder("wrong",wrong)
        assertEquals(585,room.status().secondsLeft)
        room.tuneDecoder("right",correct)
        assertEquals(correct,room.status().systems.decoderShift)
    }

    @Test fun optionalEnvironmentDiscoversStoryWithoutSkippingStages() {
        val room=Escape(9,clock={0}); solvePower(room)
        room.controlEnvironment("uv","LIGHTING","ULTRAVIOLET")
        val safe=Regex("incident [A-Z]+: (INTAKE|EXHAUST|HOLD)").find(room.archive("ENVIRONMENT").single())!!.groupValues[1]
        room.controlEnvironment("vent","VENTILATION",safe)
        assertEquals(2,room.status().discoveries)
        assertEquals(EscapeStage.CABINET,room.status().stage)
        assertEquals(2,room.pilotView().discoveries.size)
    }

    @Test fun prerequisitesCannotBeSkipped() {
        val room=Escape(42,clock={0})
        assertFailsWith<GameError>{room.tuneDecoder("x",1)}
        assertFailsWith<GameError>{room.syncRecorder("x","A")}
        assertFailsWith<GameError>{room.controlEnvironment("x","LIGHTING","WORK")}
        assertFailsWith<GameError>{room.armExit("x","A")}
        assertFailsWith<GameError>{room.escape()}
        assertEquals(EscapeStage.POWER,room.status().stage)
    }

    @Test fun invalidInputDoesNotSpendTimeAndWrongAnswerDoes() {
        val room=Escape(42,clock={0});val manual=records(room);room.inspect("panel")
        assertFailsWith<GameError>{room.submitPower(listOf("SUN","SUN","SUN"))}
        assertEquals(600,room.status().secondsLeft)
        val clue=room.pilotView().objects.first{it.id=="panel"}.description
        val amps=Regex("Load: (\\d+)").find(clue)!!.groupValues[1].toInt()/Regex("Supply: (\\d+)").find(clue)!!.groupValues[1].toInt()
        room.routePower("power",if(amps<=6) PowerMode.LOW else if(amps<=12) PowerMode.NORMAL else PowerMode.HIGH)
        val pri=Regex("(SUN|WAVE|LEAF|MOON|EYE|STAR) = ([1-6])").findAll(manual[0]).associate{it.groupValues[1] to it.groupValues[2].toInt()}
        room.submitPower(room.pilotView().symbols.sortedBy{pri.getValue(it)}.reversed())
        assertEquals(585,room.status().secondsLeft)
    }

    @Test fun timerExpiryAndTerminalStateFreeze() {
        var now=0L; val room=Escape(42,clock={now}); solveToDoor(room); room.armExit("a",exitChannel(room))
        now=600_000; assertFailsWith<GameError>{room.escape()}
        val ended=room.status(); assertEquals("FAILED",ended.outcome)
        now=900_000; room.pause();room.resume();room.tick();room.interrupt();assertEquals(ended,room.status())
    }

    @Test fun releaseWindowExpiresAndCanBeRearmed() {
        var now=0L;val room=Escape(42,clock={now});solveToDoor(room);val channel=exitChannel(room)
        val first=room.armExit("a",channel);now=20_000
        assertFailsWith<GameError>{room.escape()};assertEquals(first,room.armExit("a",channel))
        room.armExit("b",channel);now+=10_000;assertTrue(room.escape())
    }

    @Test fun pausePreservesRoomAndReleaseTimers() {
        var now=0L;val room=Escape(42,clock={now});solveToDoor(room);room.armExit("a",exitChannel(room))
        now=5_000;room.pause();now=505_000
        assertEquals(595,room.status().secondsLeft);assertEquals(15,room.status().armSeconds)
        room.resume();assertTrue(room.escape());assertTrue(room.debrief().practice)
    }

    @Test fun requestIdsAreIdempotentAndConflictsFail() {
        val room=Escape(1,clock={0})
        val first=room.message("x","Hello")
        assertEquals(first,room.message("x","Hello"))
        assertEquals("REQUEST_CONFLICT",assertFailsWith<GameError>{room.message("x","Changed")}.code)
    }

    @Test fun v3ToolsExposeAgentActionsAndNoHumanActions() {
        val tools=EscapeTools(Session())
        assertEquals(listOf("blackout_v3_observe","blackout_v3_archive","blackout_v3_message","blackout_v3_calculate",
            "blackout_v3_route_power","blackout_v3_tune_decoder","blackout_v3_sync_recorder","blackout_v3_control_environment","blackout_v3_arm_exit"),tools.tools().map{it.name})
        assertEquals(tools.tools().map{it.inputSchema},tools.aiTools().map{it.inputSchema})
        assertFalse(tools.tools().any { it.name.contains("inspect") || it.name.contains("password") || it.name.contains("handle") })
    }

    @Test fun toolsRejectMalformedSelectorsAndStaleRooms() {
        val session=Session();val tools=EscapeTools(session)
        assertTrue(tools.call("observe","{}").isError)
        val room=session.startEscape(42)
        assertTrue(tools.call("observe","{\"role\":\"human\"}").isError)
        assertTrue(tools.call("tune_decoder","{\"roomId\":1}").isError)
        val call="""{"roomId":"${room.id}","query":"ALL"}"""
        assertFalse(tools.call("archive",call).isError)
        session.startEscape(43);assertTrue(tools.call("archive",call).isError)
    }

    @Test fun calculatorIsBoundedTypedAndExact() {
        val room=Escape(1,clock={0})
        assertTrue(room.calculate("x",Arithmetic.MULTIPLY,1_000_000,1_000_000).detail.endsWith("1000000000000"))
        assertFailsWith<GameError>{room.calculate("zero",Arithmetic.DIVIDE,12,0)}
        val s=Session();val active=s.startEscape(1);val tools=EscapeTools(s)
        val prefix="""{"roomId":"${active.id}","requestId":"a","operation":"DIVIDE","a":"""
        assertTrue(tools.call("calculate",prefix+"\"24\",\"b\":2}").isError)
    }

    @Test fun debriefExcludesConversationAndLabelsExternalIdentityHonestly() {
        val descriptor=CompanionDescriptor("EXTERNAL_MCP",verified=false)
        val room=Escape(1,clock={0},companion=descriptor);room.say("private human note");room.message("a","private model note");room.interrupt()
        val saved=Json.encodeToString(room.debrief())
        assertFalse("private human note" in saved);assertFalse("private model note" in saved)
        assertFalse(room.debrief().companion.verified)
    }

    private fun solvePower(room:Escape) {
        val manual=records(room);room.inspect("panel")
        val clue=room.pilotView().objects.first{it.id=="panel"}.description
        val watts=Regex("Load: (\\d+)").find(clue)!!.groupValues[1].toInt();val volts=Regex("Supply: (\\d+)").find(clue)!!.groupValues[1].toInt()
        val amps=watts/volts
        room.routePower("p",if(amps<=6) PowerMode.LOW else if(amps<=12) PowerMode.NORMAL else PowerMode.HIGH)
        val pri=Regex("(SUN|WAVE|LEAF|MOON|EYE|STAR) = ([1-6])").findAll(manual[0]).associate{it.groupValues[1] to it.groupValues[2].toInt()}
        assertTrue(room.submitPower(room.pilotView().symbols.sortedBy{pri.getValue(it)}))
    }

    private fun Escape.observeAgentCluesAreEmptyForTest(serialized:String, hiddenSymbols:List<String>):Boolean =
        observeAfterSerializationIsPrivate(serialized, hiddenSymbols)

    private fun observeAfterSerializationIsPrivate(serialized:String, hiddenSymbols:List<String>) =
        "\"reported\":[]" in serialized && "\"seed\":" !in serialized && hiddenSymbols.none { it in serialized }

    @Test fun companionActivityIsVisibleWithoutManualContents() {
        val room = Escape(7L)
        val records = room.archive("ALL")
        room.inspect("panel"); room.report("panel")
        room.calculate("t-calc", Arithmetic.DIVIDE, 192, 24)
        room.routePower("t-route", PowerMode.NORMAL)
        val activity = room.pilotView().timeline.mapNotNull { it.activity }
        assertTrue(activity.any { it.kind == "CALCULATE" && it.detail == "192 ÷ 24 = 8" }, activity.toString())
        assertTrue(activity.any { it.kind == "ROUTE_POWER" && it.ok })
        val archive = activity.first { it.kind == "ARCHIVE" }
        assertFalse(Regex("\\d").containsMatchIn(archive.detail), archive.detail)
        assertTrue(records.none { archive.detail.contains(it.substringAfter(":").trim().take(24)) })
        room.calculate("t-calc", Arithmetic.DIVIDE, 192, 24)
        assertEquals(1, room.pilotView().timeline.count { it.activity?.kind == "CALCULATE" })
        assertTrue(room.pilotView().epilogue.isEmpty())
        room.interrupt()
        assertEquals(2, room.pilotView().epilogue.size)
    }
}
