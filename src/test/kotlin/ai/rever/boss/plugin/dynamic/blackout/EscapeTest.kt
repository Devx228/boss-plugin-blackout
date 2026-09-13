package ai.rever.boss.plugin.dynamic.blackout

import ai.rever.boss.plugin.dynamic.blackout.application.*
import ai.rever.boss.plugin.dynamic.blackout.mcp.EscapeTools
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class EscapeTest {
    /** Solve from permitted observations and manuals, never from the scenario or seed. */
    private fun solveToDoor(game: Escape) {
        val manual = game.archive("ALL")
        game.inspect("panel"); game.report("panel")
        val draw=current(game)
        game.route("supply",when {draw<=6->PowerMode.LOW;draw<=12->PowerMode.NORMAL;else->PowerMode.HIGH})
        val priority = Regex("(SUN|WAVE|LEAF|MOON|EYE|STAR) = ([1-6])").findAll(manual.first()).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        val symbols = game.pilotView().symbols
        assertTrue(game.submitPower(symbols.sortedBy { priority.getValue(it) }))
        game.inspect("cabinet"); game.report("cabinet")
        val cipher = Regex("label reads ([A-Z]+)").find(game.pilotView().objects.first { it.id=="cabinet" }.description)!!.groupValues[1]
        val shift = Regex("forward ([1-5])").find(manual[1])!!.groupValues[1].toInt()
        val decoded = cipher.map { 'A' + ((it-'A'-shift+26)%26) }.joinToString("")
        assertTrue(game.submitPassword(decoded))
        game.inspect("recorder"); game.report("recorder")
        val strips = game.pilotView().fragments
        val order = listOf("coolant alarm", "Power is gone", "heard them knocking").map { term -> strips.first { term in it.text }.id }
        assertTrue(game.submitStory(order))
        assertEquals(EscapeStage.EXIT,game.status().stage)
    }
    private fun current(game:Escape):Int {
        val clue=game.pilotView().objects.first {it.id=="panel"}.description
        val watts=Regex("Load: (\\d+) W").find(clue)!!.groupValues[1].toInt()
        val volts=Regex("Supply: (\\d+) V").find(clue)!!.groupValues[1].toInt()
        val result=game.calculate("current",Arithmetic.DIVIDE,watts,volts)
        return result.detail.substringAfter(" = ").toDouble().toInt()
    }
    private fun channel(game:Escape):String {
        game.inspect("door");game.report("door")
        val seal=Regex("Routing seal: ([A-Z]+)").find(game.pilotView().objects.first {it.id=="door"}.description)!!.groupValues[1]
        return Regex("$seal = ([A-F])").find(game.archive("EXIT").single())!!.groupValues[1]
    }

    @Test fun oneHundredLegalCrewsEscapeWithoutReadingPrivateScenario() {
        repeat(100) { seed ->
            val room=Escape(seed.toLong(), { 0 })
            solveToDoor(room)
            assertEquals("NOT_ARMED",assertFailsWith<GameError> { room.escape() }.code)
            room.arm("release",channel(room))
            assertEquals("IN_PROGRESS",room.status().outcome)
            assertTrue(room.escape()); assertEquals("ESCAPED",room.status().outcome)
            assertEquals(0,room.debrief().mistakes)
        }
    }
    @Test fun physicalCluesCrossOnlyWhenShared() {
        var time=0L; val room=Escape(42,{time})
        val before=Json.encodeToString(room.observe())
        assertFalse("symbols" in before);assertFalse("fragments" in before);assertFalse("seed" in before)
        assertFailsWith<GameError> { room.report("panel") }
        room.inspect("panel");time+=1000
        assertTrue(room.observe().reported.isEmpty())
        room.report("panel");time+=1000
        val reported=room.observe().reported
        assertEquals(1,reported.size);assertTrue("breaker symbols" in reported.single().text)
    }
    @Test fun prerequisitesCannotBeSkipped() {
        val room=Escape(42,{0})
        assertFailsWith<GameError> { room.inspect("recorder") }
        assertFailsWith<GameError> { room.submitPassword("HOME") }
        assertFailsWith<GameError> { room.submitStory(listOf("A","B","C")) }
        assertFailsWith<GameError> { room.arm("early","A") }
        assertFailsWith<GameError> { room.escape() }
        assertFailsWith<GameError> { room.debrief() }
        assertEquals(EscapeStage.POWER,room.status().stage)
    }
    @Test fun invalidAnswersDoNotSpendTimeAndWrongAnswersDo() {
        val room=Escape(42,{0});room.inspect("panel")
        val before=room.status()
        assertFailsWith<GameError> { room.submitPower(listOf("SUN","SUN","SUN")) }
        assertEquals(before,room.status())
        val priorities=Regex("(SUN|WAVE|LEAF|MOON|EYE|STAR) = ([1-6])").findAll(room.archive("POWER").first { it.startsWith("POWER MANUAL") }).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        val draw=current(room)
        room.route("supply",when {draw<=6->PowerMode.LOW;draw<=12->PowerMode.NORMAL;else->PowerMode.HIGH})
        val wrong=room.pilotView().symbols.sortedByDescending { priorities.getValue(it) }
        assertFalse(room.submitPower(wrong));assertEquals(585,room.status().secondsLeft)
        assertEquals(1,room.status().mistakes)
    }
    @Test fun timerExpiryBlocksSuccessAndFreezeIsTerminal() {
        var now=0L;val room=Escape(42,{now});solveToDoor(room);room.arm("a",channel(room))
        now=600_000;assertFailsWith<GameError> { room.escape() }
        val ended=room.status();assertEquals("FAILED",ended.outcome)
        now=900_000;room.pause();room.resume();room.tick();room.interrupt()
        assertEquals(ended,room.status());assertEquals(600,room.debrief().secondsUsed)
    }
    @Test fun releaseWindowExpiresAndCanBeRearmed() {
        var now=0L;val room=Escape(42,{now});solveToDoor(room)
        val channel=channel(room)
        val first=room.arm("a",channel);now=20_000
        assertFailsWith<GameError> { room.escape() }
        assertEquals(first,room.arm("a",channel)) // retry does not extend an expired authorization
        assertFailsWith<GameError> { room.escape() }
        room.arm("b",channel);now+=10_000;assertTrue(room.escape())
        val seconds=room.status().secondsLeft;now+=100_000;assertEquals(seconds,room.status().secondsLeft)
    }
    @Test fun pausePreservesBothTimersAndMarksPractice() {
        var now=0L;val room=Escape(42,{now});solveToDoor(room);room.arm("a",channel(room))
        now=5_000;room.pause();now=505_000
        assertEquals(595,room.status().secondsLeft);assertEquals(15,room.status().armSeconds)
        assertFailsWith<GameError> { room.escape() }
        room.resume();assertTrue(room.escape());assertEquals(5,room.debrief().secondsUsed);assertTrue(room.debrief().practice)
    }
    @Test fun hintsAreBoundedAndCanExhaustAirImmediately() {
        var now=0L;val room=Escape(1,{now})
        repeat(3) {room.hint()};assertEquals(540,room.status().secondsLeft)
        assertFailsWith<GameError> {room.hint()}
        val other=Escape(1,{now});now=590_000;other.hint();assertEquals("FAILED",other.status().outcome)
    }
    @Test fun retriesAndMessageLimitsAreEnforced() {
        val room=Escape(1,{0})
        val first=room.message("x","Hello")
        assertEquals(first,room.message("x","Hello"))
        assertEquals("REQUEST_CONFLICT",assertFailsWith<GameError> {room.message("x","Changed")}.code)
        repeat(29) {room.message("m$it","hello")}
        assertFailsWith<GameError> {room.message("extra","hello")}
        repeat(12) {room.archive("ALL")}
        assertFailsWith<GameError> {room.archive("ALL")}
    }
    @Test fun toolsRejectHumanActionsSelectorsAndOldRoomCalls() {
        val session=Session();val tools=EscapeTools(session)
        assertTrue(tools.call("observe","{}").isError)
        val room=session.startEscape(42)
        assertTrue(tools.call("escape","{}").isError)
        assertTrue(tools.call("observe","{\"role\":\"human\"}").isError)
        for(raw in listOf("[]","null","invalid","{\"query\":true}","x".repeat(4097))) assertTrue(tools.call("archive",raw).isError)
        val call="""{"roomId":"${room.id}","query":"ALL"}"""
        assertFalse(tools.call("archive",call).isError)
        session.startEscape(43);assertTrue(tools.call("archive",call).isError)
        session.dispose();assertTrue(tools.call("observe","{}").isError)
    }
    @Test fun gatewayAndMcpExposeIdenticalEscapeTools() {
        val tools=EscapeTools(Session())
        assertEquals(listOf("blackout_v2_observe","blackout_v2_archive","blackout_v2_message","blackout_v2_calculate","blackout_v2_route","blackout_v2_arm"),tools.tools().map {it.name})
        assertEquals(tools.tools().map {it.inputSchema},tools.aiTools().map {it.inputSchema})
    }
    @Test fun debriefExcludesFreeTextAndRevealsOnlyAfterEnd() {
        val room=Escape(1,{0});room.say("private human note");room.message("a","private companion note")
        room.interrupt();val saved=Json.encodeToString(room.debrief())
        assertFalse("private human note" in saved);assertFalse("private companion note" in saved)
        assertEquals("INTERRUPTED",room.debrief().outcome)
    }
    @Test fun humanCannotPowerUpUntilCompanionActs() {
        val room=Escape(123,{0});room.inspect("panel")
        assertEquals("REMOTE_POWER_REQUIRED",assertFailsWith<GameError> {room.submitPower(room.pilotView().symbols)}.code)
        assertEquals(600,room.status().secondsLeft)
        val receipt=room.route("r",PowerMode.LOW)
        assertEquals(receipt,room.route("r",PowerMode.LOW))
        assertFailsWith<GameError> {room.route("r",PowerMode.HIGH)}
        assertEquals(PowerMode.LOW,room.status().remotePower)
    }
    @Test fun calculatorIsBoundedExactAndIdempotent() {
        val room=Escape(1,{0})
        val ack=room.calculate("x",Arithmetic.MULTIPLY,1_000_000,1_000_000)
        assertTrue(ack.detail.endsWith("1000000000000"))
        assertEquals(ack,room.calculate("x",Arithmetic.MULTIPLY,1_000_000,1_000_000))
        assertFailsWith<GameError> {room.calculate("zero",Arithmetic.DIVIDE,12,0)}
        assertFailsWith<GameError> {room.calculate("huge",Arithmetic.ADD,1_000_001,0)}
        repeat(15) {room.calculate("n$it",Arithmetic.ADD,1,2)}
        assertFailsWith<GameError> {room.calculate("over",Arithmetic.ADD,1,2)}
    }
    @Test fun calculatorRejectsExpressionsAndCoercedNumbers() {
        val s=Session();val room=s.startEscape(1);val tools=EscapeTools(s)
        val prefix="""{"roomId":"${room.id}","requestId":"a","operation":"DIVIDE","a":"""
        assertTrue(tools.call("calculate",prefix+"\"24\",\"b\":2}").isError)
        assertTrue(tools.call("calculate",prefix+"24.5,\"b\":2}").isError)
        assertFalse(tools.call("calculate",prefix+"24,\"b\":2}").isError)
        assertTrue(tools.call("calculate","""{"expression":"24/2"}""").isError)
    }
    @Test fun wrongAuthorizationPenalizesWithoutOpeningDoor() {
        val room=Escape(11,{0});solveToDoor(room)
        val correct=channel(room);val wrong=if(correct=="A") "B" else "A"
        val ack=room.arm("bad",wrong)
        assertTrue("rejected" in ack.detail);assertEquals(585,room.status().secondsLeft)
        assertFailsWith<GameError> {room.escape()}
        assertEquals(ack,room.arm("bad",wrong));assertEquals(585,room.status().secondsLeft)
        room.arm("good",correct);assertTrue(room.escape())
    }
}
