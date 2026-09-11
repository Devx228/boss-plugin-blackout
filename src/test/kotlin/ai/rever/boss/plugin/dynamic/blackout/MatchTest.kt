package ai.rever.boss.plugin.dynamic.blackout

import ai.rever.boss.plugin.dynamic.blackout.application.*
import ai.rever.boss.plugin.dynamic.blackout.engine.*
import ai.rever.boss.plugin.dynamic.blackout.mcp.BlackoutTools
import kotlinx.serialization.json.Json
import kotlin.test.*

class MatchTest {
    private val config = AgentOrder(Thermal.STANDARD, Polarity.DIRECT, Output.NORMAL)

    @Test fun bothCommitOrdersProduceSameResult() {
        val a = Match(42, { 0 }, "a")
        val b = Match(42, { 0 }, "b")
        a.commitHuman(1, HumanOrder(Action.FIRE, "A", Subsystem.REACTOR)); a.commitAgent(1, "x", config)
        b.commitAgent(1, "x", config); b.commitHuman(1, HumanOrder(Action.FIRE, "A", Subsystem.REACTOR))
        assertEquals(a.publicView().blue, b.publicView().blue)
        assertEquals(a.publicView().orange, b.publicView().orange)
        assertEquals(2, a.publicView().round)
    }

    @Test fun retriesAreIdempotentEvenAfterResolution() {
        val m = Match(42, { 0 })
        m.commitHuman(1, HumanOrder(Action.HOLD, "A"))
        val ack = m.commitAgent(1, "one", config)
        assertEquals(ack, m.commitAgent(1, "one", config))
        assertEquals("REQUEST_CONFLICT", assertFailsWith<GameError> { m.scan(1, "one") }.code)
        assertEquals(2, m.publicView().round)
    }

    @Test fun staleAndLockedOrdersAreRejectedWithoutMutation() {
        val m = Match(42, { 0 })
        val before = m.publicView()
        assertFailsWith<GameError> { m.commitAgent(0, "bad", config) }
        assertEquals(before, m.publicView())
        m.commitAgent(1, "a", config)
        assertEquals("ALREADY_COMMITTED", assertFailsWith<GameError> { m.commitAgent(1, "b", config) }.code)
    }

    @Test fun invalidRouteIsRejected() {
        val m = Match(42, { 0 })
        assertEquals("INVALID_ROUTE", assertFailsWith<GameError> { m.commitHuman(1, HumanOrder(Action.FIRE, "Z")) }.code)
        assertNull(m.humanView().humanOrder)
    }

    @Test fun incompleteRoundHoldsAndTwoConsecutiveForfeit() {
        var now = 0L
        val m = Match(42, { now })
        m.commitHuman(1, HumanOrder(Action.FIRE, "A"))
        now = Rules.ROUND_SECONDS * 1000L; m.tick()
        assertEquals(2, m.publicView().round)
        now = 2 * Rules.ROUND_SECONDS * 1000L; m.tick()
        assertEquals("ORANGE", m.publicView().outcome)
        assertTrue(m.replay().rounds.last().events.any { "forfeit" in it })
    }

    @Test fun pauseFreezesDeadlineAndResolution() {
        var now = 0L
        val m = Match(42, { now })
        now = 20_000; m.pause(); now = 200_000; m.tick()
        assertEquals(Rules.ROUND_SECONDS - 20, m.publicView().remainingSeconds)
        m.commitHuman(1, HumanOrder(Action.HOLD, "A")); m.commitAgent(1, "a", config)
        assertEquals(1, m.publicView().round)
        m.resume()
        assertEquals(2, m.publicView().round)
        assertTrue(m.publicView().practice)
    }

    @Test fun budgetsAndViewsProtectPrivateFields() {
        var now = 0L
        val m = Match(42, { now })
        val initial = Json.encodeToString(AgentView.serializer(), m.observe())
        assertFalse(initial.contains("routes"), "the engineer must never receive the route map")
        assertFalse(initial.contains("insulated"))
        assertFalse(initial.contains("seed"))
        assertFalse(initial.contains("blueOrder"))
        assertFailsWith<GameError> { m.observe() }
        now = 1000
        m.scan(1, "scan")
        assertNotNull(m.observe().scannedPolarity)
        assertEquals("BUDGET_EXHAUSTED", assertFailsWith<GameError> { m.scan(1, "scan2") }.code)
        repeat(6) { m.message(1, "m$it", "hello") }
        assertFailsWith<GameError> { m.message(1, "m7", "hello") }
        assertFailsWith<GameError> { m.message(1, "long", "x".repeat(501)) }
    }

    @Test fun thePilotSeesCoarseRivalStatusAndTheEngineerSeesTheNumbers() {
        val m = Match(42, { 0 })
        val pilot = m.humanView().public
        assertNull(pilot.orange.of(Subsystem.LIFE).integrity, "the pilot must not read exact rival integrity")
        assertNull(pilot.orange.of(Subsystem.LIFE).barrier)
        assertEquals("ONLINE", pilot.orange.of(Subsystem.LIFE).status)
        assertEquals(Rules.MAX_INTEGRITY, pilot.blue.of(Subsystem.LIFE).integrity, "the pilot reads their own station in full")

        val engineer = m.observe().public
        assertEquals(Rules.MAX_INTEGRITY, engineer.orange.of(Subsystem.LIFE).integrity, "the sweep carries rival figures")
        assertEquals(0, engineer.orange.of(Subsystem.LIFE).barrier)
    }

    @Test fun thePilotNeverSeesTheEngineersConfiguration() {
        val m = Match(42, { 0 })
        m.commitAgent(1, "x", AgentOrder(Thermal.COOL, Polarity.INVERT, Output.BOOST))
        val view = m.humanView()
        assertTrue(view.agentLocked)
        assertEquals(Output.BOOST, view.agentOutput, "output is disclosed because it changes the cost")
        val json = Json.encodeToString(HumanView.serializer(), view)
        assertFalse(json.contains("COOL"), "thermal must stay with the engineer")
        assertFalse(json.contains("INVERT"), "polarity must stay with the engineer")
    }

    @Test fun everyCompartmentCanBeTargeted() {
        Subsystem.entries.forEach { system ->
            val m = Match(42, { 0 })
            val (pilot, engineer) = solved(42, 1, Action.FIRE, system)
            m.commitHuman(1, pilot)
            m.commitAgent(1, "a", engineer)
            val hit = m.lastRound()!!.after.orange.integrity[system]
            assertTrue(hit < Rules.MAX_INTEGRITY, "$system should have taken damage")
        }
    }

    @Test fun replayIsTerminalOnlyAndReproducesRounds() {
        var now = 0L
        val m = Match(42, { now })
        assertFailsWith<GameError> { m.replay() }
        repeat(Rules.MAX_ROUNDS) {
            if (m.publicView().outcome == "IN_PROGRESS") {
                val round = m.publicView().round
                m.commitHuman(round, HumanOrder(Action.HOLD, "A"))
                m.commitAgent(round, "a$round", config)
                now += 1000
            }
        }
        val replay = Json.decodeFromString(Replay.serializer(), m.replayJson())
        assertEquals(Rules.VERSION, replay.rulesVersion)
        replay.rounds.forEach { assertEquals(it, Rules.resolve(replay.seed, it.before, it.blueOrder, it.orangeOrder)) }
        assertEquals(replay.finalFrame, replay.rounds.last().after)
        assertFalse(m.replayJson().contains("messages"), "crew chatter stays out of the replay file")
    }

    @Test fun holdCannotUseBoostInEitherSubmissionOrder() {
        val a = Match(42, { 0 })
        a.commitHuman(1, HumanOrder(Action.HOLD, "A"))
        assertFailsWith<GameError> { a.commitAgent(1, "x", config.copy(output = Output.BOOST)) }
        assertFalse(a.humanView().agentLocked)

        val b = Match(42, { 0 })
        b.commitAgent(1, "x", config.copy(output = Output.BOOST))
        assertFailsWith<GameError> { b.commitHuman(1, HumanOrder(Action.HOLD, "A")) }
        assertNull(b.humanView().humanOrder)
    }

    @Test fun toolsRejectSelectorsMalformedTypesAndOldMatch() {
        val s = Session()
        val tools = BlackoutTools(s)
        assertTrue(tools.call("observe", "{}").isError)
        val m = s.start()
        for (raw in listOf("[]", "garbage", "{\"team\":\"ORANGE\"}", "{\"role\":\"HUMAN\"}")) {
            assertTrue(tools.call("observe", raw).isError)
        }
        val badRound = "{\"matchId\":\"${m.id}\",\"round\":1.2,\"requestId\":\"x\"}"
        assertTrue(tools.call("scan", badRound).isError)
        assertTrue(tools.call("scan", badRound.replace("1.2", "\"1\"")).isError)
        assertTrue(tools.call("scan", badRound.replace(m.id, "wrong").replace("1.2", "1")).isError)
        assertFalse(tools.call("scan", badRound.replace("1.2", "1")).isError)
        s.dispose()
        assertTrue(tools.call("observe", "{}").isError)
    }

    @Test fun gatewayToolSpecsMatchTheMcpTools() {
        val tools = BlackoutTools(Session())
        val mcp = tools.tools()
        val gateway = tools.aiTools()
        assertEquals(mcp.size, gateway.size)
        mcp.zip(gateway).forEach { (a, b) ->
            assertEquals(a.name, b.name)
            assertEquals(a.description, b.description)
            assertEquals(a.inputSchema, b.inputSchema, "the in-app seat must not get a different schema")
        }
        assertTrue(mcp.all { it.name.startsWith(BlackoutTools.PREFIX) })
    }

    @Test fun terminalStateIsImmutable() {
        val m = Match(42, { 0 })
        m.interrupt()
        val before = m.publicView()
        assertFailsWith<GameError> { m.commitAgent(1, "x", config) }
        m.tick(); m.pause(); m.resume(); m.interrupt()
        assertEquals(before, m.publicView())
    }

    @Test fun aSecondMatchCannotStartWhileOneIsRunning() {
        val s = Session()
        s.start()
        assertFailsWith<IllegalStateException> { s.start() }
        s.current().interrupt()
        s.start()
    }

    /** Solve the round the way a coordinated crew would, from the public generator. */
    private fun solved(seed: Long, round: Int, action: Action, target: Subsystem): Pair<HumanOrder, AgentOrder> {
        val circuit = Rules.circuit(seed, round, Team.BLUE)
        val route = circuit.routes.first { !it.broken && (!circuit.hot || it.insulated) && it.crossed == circuit.reversed }
        return HumanOrder(action, route.id, target) to AgentOrder(
            if (circuit.hot) Thermal.COOL else Thermal.STANDARD,
            if (circuit.reversed) Polarity.INVERT else Polarity.DIRECT,
            Output.NORMAL
        )
    }
}
