package ai.rever.boss.plugin.dynamic.blackout

import ai.rever.boss.plugin.dynamic.blackout.engine.*
import kotlin.test.*

class RulesTest {
    private fun order(
        seed: Long,
        round: Int,
        team: Team,
        action: Action,
        target: Subsystem = Subsystem.LIFE,
        output: Output = Output.NORMAL
    ): Orders {
        val c = Rules.circuit(seed, round, team)
        val route = c.routes.first {
            !it.broken && (!c.hot || it.insulated) && it.crossed == c.reversed && (output == Output.NORMAL || it.highOutput)
        }
        return Orders(
            HumanOrder(action, route.id, target),
            AgentOrder(
                if (c.hot) Thermal.COOL else Thermal.STANDARD,
                if (c.reversed) Polarity.INVERT else Polarity.DIRECT,
                output
            )
        )
    }

    private fun resolve(
        frame: Frame = Frame(),
        a: Action,
        b: Action,
        aTarget: Subsystem = Subsystem.LIFE,
        bTarget: Subsystem = Subsystem.LIFE,
        boost: Boolean = false
    ) = Rules.resolve(
        42, frame,
        order(42, frame.round, Team.BLUE, a, aTarget, if (boost) Output.BOOST else Output.NORMAL),
        order(42, frame.round, Team.ORANGE, b, bTarget)
    )

    @Test fun generationHasSolutionsAndEquivalentDifficulty() {
        for (seed in 0L..499L) for (round in 1..6) {
            val b = Rules.circuit(seed, round, Team.BLUE)
            val o = Rules.circuit(seed, round, Team.ORANGE)
            assertEquals(b.hot, o.hot); assertEquals(b.reversed, o.reversed)
            assertEquals(b.routes.map { it.copy(id = "") }.toSet(), o.routes.map { it.copy(id = "") }.toSet())
            for (team in Team.entries) for (output in Output.entries)
                assertTrue(Rules.succeeds(Rules.circuit(seed, round, team), order(seed, round, team, Action.FIRE, Subsystem.LIFE, output)))
        }
    }

    @Test fun damageLandsOnTheNamedCompartmentOnly() {
        val after = resolve(a = Action.FIRE, b = Action.HOLD, aTarget = Subsystem.REACTOR).after
        assertEquals(Rules.MAX_INTEGRITY - Rules.FIRE_DAMAGE, after.orange.integrity[Subsystem.REACTOR])
        assertEquals(Rules.MAX_INTEGRITY, after.orange.integrity[Subsystem.LIFE])
        assertEquals(Rules.MAX_INTEGRITY, after.orange.integrity[Subsystem.SHIELDS])
    }

    @Test fun barrierProtectsOnlyTheCompartmentItSitsOn() {
        val braced = resolve(a = Action.SHIELD, b = Action.HOLD, aTarget = Subsystem.LIFE).after
        assertEquals(Rules.SHIELD_GAIN, braced.blue.barrier[Subsystem.LIFE])

        val covered = Rules.resolve(
            42, braced.copy(round = 1),
            order(42, 1, Team.BLUE, Action.HOLD),
            order(42, 1, Team.ORANGE, Action.FIRE, Subsystem.LIFE)
        ).after
        assertEquals(Rules.MAX_INTEGRITY - (Rules.FIRE_DAMAGE - Rules.SHIELD_GAIN), covered.blue.integrity[Subsystem.LIFE])
        assertEquals(0, covered.blue.barrier[Subsystem.LIFE])

        val elsewhere = Rules.resolve(
            42, braced.copy(round = 1),
            order(42, 1, Team.BLUE, Action.HOLD),
            order(42, 1, Team.ORANGE, Action.FIRE, Subsystem.REACTOR)
        ).after
        assertEquals(Rules.MAX_INTEGRITY - Rules.FIRE_DAMAGE, elsewhere.blue.integrity[Subsystem.REACTOR])
        assertEquals(Rules.SHIELD_GAIN, elsewhere.blue.barrier[Subsystem.LIFE])
    }

    @Test fun boostedFireHitsHarderAndCostsMore() {
        val result = resolve(a = Action.FIRE, b = Action.HOLD, aTarget = Subsystem.LIFE, boost = true).after
        assertEquals(Rules.MAX_INTEGRITY - Rules.FIRE_DAMAGE_BOOST, result.orange.integrity[Subsystem.LIFE])
        assertEquals(Rules.START_ENERGY - 4 + Rules.ENERGY_FULL, result.blue.energy)
    }

    @Test fun repairsResolveBeforeDamageAndCap() {
        // Patching first leaves 2 + 3 - 4 = 1. Taking the hit first would floor at zero
        // and then repair to 3, so the surviving figure proves the phase order.
        val hurt = Frame(blue = Ship(integrity = Grid.of(Rules.MAX_INTEGRITY).with(Subsystem.REACTOR, 2)))
        val result = resolve(hurt, Action.REPAIR, Action.FIRE, Subsystem.REACTOR, Subsystem.REACTOR).after
        assertEquals(2 + Rules.REPAIR_GAIN - Rules.FIRE_DAMAGE, result.blue.integrity[Subsystem.REACTOR])

        val nearlyWhole = Frame(blue = Ship(integrity = Grid.of(Rules.MAX_INTEGRITY).with(Subsystem.REACTOR, Rules.MAX_INTEGRITY - 1)))
        val capped = resolve(nearlyWhole, Action.REPAIR, Action.HOLD, Subsystem.REACTOR).after
        assertEquals(Rules.MAX_INTEGRITY, capped.blue.integrity[Subsystem.REACTOR])
    }

    @Test fun losingLifeSupportEndsTheMatch() {
        val doomed = Frame(orange = Ship(integrity = Grid.of(Rules.MAX_INTEGRITY).with(Subsystem.LIFE, 4)))
        assertEquals("BLUE", resolve(doomed, Action.FIRE, Action.HOLD, Subsystem.LIFE).after.outcome)
    }

    @Test fun mutualLossIsADraw() {
        val both = Frame(
            blue = Ship(integrity = Grid.of(Rules.MAX_INTEGRITY).with(Subsystem.LIFE, 4)),
            orange = Ship(integrity = Grid.of(Rules.MAX_INTEGRITY).with(Subsystem.LIFE, 4))
        )
        assertEquals("DRAW", resolve(both, Action.FIRE, Action.FIRE, Subsystem.LIFE, Subsystem.LIFE).after.outcome)
    }

    @Test fun aDamagedReactorStarvesTheCrew() {
        val whole = Ship()
        val hurt = Ship(integrity = Grid.of(Rules.MAX_INTEGRITY).with(Subsystem.REACTOR, Rules.IMPAIRED_AT))
        val dead = Ship(integrity = Grid.of(Rules.MAX_INTEGRITY).with(Subsystem.REACTOR, 0))
        assertEquals(Rules.ENERGY_FULL, Rules.energyIncome(whole))
        assertEquals(Rules.ENERGY_IMPAIRED, Rules.energyIncome(hurt))
        assertEquals(0, Rules.energyIncome(dead))
    }

    @Test fun aDamagedShieldArrayCapsBarriers() {
        val hurt = Frame(blue = Ship(integrity = Grid.of(Rules.MAX_INTEGRITY).with(Subsystem.SHIELDS, Rules.IMPAIRED_AT)))
        val result = resolve(hurt, Action.SHIELD, Action.HOLD, Subsystem.LIFE).after
        assertEquals(Rules.MAX_BARRIER / 2, result.blue.barrier[Subsystem.LIFE])

        val down = Frame(blue = Ship(integrity = Grid.of(Rules.MAX_INTEGRITY).with(Subsystem.SHIELDS, 0)))
        assertEquals(0, resolve(down, Action.SHIELD, Action.HOLD, Subsystem.LIFE).after.blue.barrier[Subsystem.LIFE])
    }

    @Test fun aDamagedMastLosesTheGridContest() {
        val hurt = Frame(blue = Ship(integrity = Grid.of(Rules.MAX_INTEGRITY).with(Subsystem.RELAY, Rules.IMPAIRED_AT)))
        // A normal push from a damaged mast drops to zero and cannot score.
        assertEquals(0, resolve(hurt, Action.RELAY, Action.HOLD, Subsystem.RELAY).after.blue.relayPoints)
        // An intact mast scores against a crew that is doing something else.
        assertEquals(1, resolve(a = Action.RELAY, b = Action.HOLD).after.blue.relayPoints)
    }

    @Test fun equalPushesScoreNothingAndBoostBreaksTheTie() {
        assertEquals(0, resolve(a = Action.RELAY, b = Action.RELAY).after.blue.relayPoints)
        assertEquals(1, resolve(a = Action.RELAY, b = Action.RELAY, boost = true).after.blue.relayPoints)
    }

    @Test fun threeGridPointsWin() {
        val ahead = Frame(blue = Ship(relayPoints = Rules.RELAY_TARGET - 1))
        assertEquals("BLUE", resolve(ahead, Action.RELAY, Action.HOLD).after.outcome)
    }

    @Test fun failingLifeSupportBleedsAnotherCompartmentAndReplaysTheSame() {
        val failing = Frame(blue = Ship(integrity = Grid.of(Rules.MAX_INTEGRITY).with(Subsystem.LIFE, Rules.IMPAIRED_AT)))
        val first = resolve(failing, Action.HOLD, Action.HOLD)
        val second = resolve(failing, Action.HOLD, Action.HOLD)
        assertEquals(first, second)
        assertTrue(first.events.any { "bleeds" in it })
    }

    @Test fun finalRoundTiebreakersAndExactDraw() {
        val ahead = Frame(round = Rules.MAX_ROUNDS, blue = Ship(relayPoints = 1))
        assertEquals("BLUE", resolve(ahead, Action.HOLD, Action.HOLD).after.outcome)

        val stronger = Frame(round = Rules.MAX_ROUNDS, blue = Ship(integrity = Grid.of(Rules.MAX_INTEGRITY).with(Subsystem.REACTOR, 4)))
        assertEquals("ORANGE", resolve(stronger, Action.HOLD, Action.HOLD).after.outcome)

        assertEquals("DRAW", resolve(Frame(round = Rules.MAX_ROUNDS), Action.HOLD, Action.HOLD).after.outcome)
    }

    @Test fun incorrectConfigurationSpendsEnergyButDoesNothing() {
        val good = order(42, 1, Team.BLUE, Action.FIRE, Subsystem.LIFE)
        val wrong = good.copy(agent = good.agent.copy(thermal = if (good.agent.thermal == Thermal.COOL) Thermal.STANDARD else Thermal.COOL))
        val result = Rules.resolve(42, Frame(), wrong, null)
        assertEquals(Rules.MAX_INTEGRITY, result.after.orange.integrity[Subsystem.LIFE])
        assertEquals(Rules.START_ENERGY - 3 + Rules.ENERGY_FULL, result.after.blue.energy)
        assertTrue(result.events.any { "failed circuit" in it })
    }

    @Test fun unaffordableOrdersAndTerminalResolutionReject() {
        assertFailsWith<IllegalArgumentException> { resolve(Frame(blue = Ship(energy = 0)), Action.FIRE, Action.HOLD) }
        assertFailsWith<IllegalArgumentException> { resolve(Frame(outcome = "DRAW"), Action.HOLD, Action.HOLD) }
    }

    @Test fun boundsHoldAcrossSeededStrategySimulation() {
        var matches = 0
        for (seed in 0L..99L) for (strategy in listOf(Action.FIRE, Action.SHIELD, Action.REPAIR, Action.RELAY)) {
            var frame = Frame()
            var rounds = 0
            while (frame.outcome == "IN_PROGRESS") {
                fun choose(team: Team): Orders {
                    val proposed = if (team == Team.BLUE) strategy else Action.entries[((seed + frame.round) % 5).toInt()]
                    val affordable = if (Rules.cost(proposed, Output.NORMAL) <= frame.ship(team).energy) proposed else Action.HOLD
                    val target = Subsystem.entries[((seed + frame.round + team.ordinal) % 4).toInt()]
                    return order(seed, frame.round, team, affordable, target)
                }
                frame = Rules.resolve(seed, frame, choose(Team.BLUE), choose(Team.ORANGE)).after
                listOf(frame.blue, frame.orange).forEach { ship ->
                    Subsystem.entries.forEach { system ->
                        assertTrue(ship.integrity[system] in 0..Rules.MAX_INTEGRITY, "integrity out of range")
                        assertTrue(ship.barrier[system] in 0..Rules.MAX_BARRIER, "barrier out of range")
                    }
                    assertTrue(ship.energy in 0..Rules.MAX_ENERGY)
                    assertTrue(ship.relayPoints in 0..Rules.RELAY_TARGET)
                }
                rounds++
                assertTrue(rounds <= Rules.MAX_ROUNDS, "a match must terminate within the round limit")
            }
            matches++
        }
        assertEquals(400, matches)
    }

    @Test fun everyRivalDifficultyProducesAffordableLegalOrders() {
        for (seed in 0L..199L) for (level in Difficulty.entries) {
            var frame = Frame()
            while (frame.outcome == "IN_PROGRESS") {
                val rival = Rival.orders(seed, frame, level)
                assertTrue(Rules.cost(rival.human.action, rival.agent.output) <= frame.orange.energy, "rival overspent on $level")
                assertTrue(rival.human.route in listOf("A", "B", "C", "D"))
                if (rival.human.action == Action.HOLD) assertEquals(Output.NORMAL, rival.agent.output)
                frame = Rules.resolve(seed, frame, null, rival).after
            }
        }
    }

    @Test fun aVeteranConfiguresCorrectlyAndACadetSometimesDoesNot() {
        var veteranFailures = 0
        var cadetFailures = 0
        for (seed in 0L..299L) {
            val frame = Frame()
            val veteran = Rival.orders(seed, frame, Difficulty.VETERAN)
            val cadet = Rival.orders(seed, frame, Difficulty.CADET)
            val circuit = Rules.circuit(seed, frame.round, Team.ORANGE)
            if (!Rules.succeeds(circuit, veteran)) veteranFailures++
            if (!Rules.succeeds(circuit, cadet)) cadetFailures++
        }
        assertEquals(0, veteranFailures, "a veteran crew should never misconfigure")
        assertTrue(cadetFailures > 0, "a cadet crew should sometimes misconfigure")
    }
}
