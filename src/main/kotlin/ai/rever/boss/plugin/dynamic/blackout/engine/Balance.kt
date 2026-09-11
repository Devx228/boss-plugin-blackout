package ai.rever.boss.plugin.dynamic.blackout.engine

/**
 * A headless balance harness. It plays scripted crew against scripted crew across many
 * seeds and prints what actually happened, so the numbers quoted anywhere else in this
 * repository can be reproduced rather than asserted.
 *
 * This measures the scripted policies only. It says nothing about whether the game is fun,
 * how a language model plays the engineer's seat, or how a human pilot behaves under a clock.
 */
object Balance {

    data class Tally(
        var blue: Int = 0,
        var orange: Int = 0,
        var draw: Int = 0,
        var rounds: Int = 0,
        var lifeLost: Int = 0,
        var gridWins: Int = 0,
        var timeouts: Int = 0,
        val destroyed: MutableMap<Subsystem, Int> = Subsystem.entries.associateWith { 0 }.toMutableMap()
    ) {
        val played: Int get() = blue + orange + draw
        fun percent(count: Int) = if (played == 0) 0.0 else count * 100.0 / played
    }

    fun play(seed: Long, blueSkill: Difficulty, orangeSkill: Difficulty, tally: Tally) {
        var frame = Frame()
        var rounds = 0
        while (frame.outcome == "IN_PROGRESS") {
            val blue = Rival.orders(seed, frame, blueSkill, Team.BLUE)
            val orange = Rival.orders(seed, frame, orangeSkill, Team.ORANGE)
            frame = Rules.resolve(seed, frame, blue, orange).after
            rounds++
        }
        tally.rounds += rounds
        when (frame.outcome) {
            "BLUE" -> tally.blue++
            "ORANGE" -> tally.orange++
            else -> tally.draw++
        }
        val lifeGone = frame.blue.integrity[Subsystem.LIFE] == 0 || frame.orange.integrity[Subsystem.LIFE] == 0
        val gridTaken = frame.blue.relayPoints >= Rules.RELAY_TARGET || frame.orange.relayPoints >= Rules.RELAY_TARGET
        when {
            lifeGone -> tally.lifeLost++
            gridTaken -> tally.gridWins++
            else -> tally.timeouts++
        }
        listOf(frame.blue, frame.orange).forEach { ship ->
            Subsystem.entries.forEach { system ->
                if (ship.integrity[system] == 0) tally.destroyed[system] = tally.destroyed.getValue(system) + 1
            }
        }
    }

    fun report(seeds: Int = 2000): String = buildString {
        appendLine("BLACKOUT balance report, rules ${Rules.VERSION}, $seeds seeds per pairing.")
        appendLine("Scripted crew against scripted crew. No model and no human is involved.")
        appendLine()
        appendLine("PAIRING (blue vs orange)      BLUE%  ORANGE%  DRAW%  AVG ROUNDS  BY LIFE%  BY GRID%  TO ROUND ${Rules.MAX_ROUNDS}%")
        for (blueSkill in Difficulty.entries) for (orangeSkill in Difficulty.entries) {
            val tally = Tally()
            for (seed in 0 until seeds) play(seed.toLong(), blueSkill, orangeSkill, tally)
            appendLine(
                "%-28s %6.1f %8.1f %6.1f %11.2f %9.1f %9.1f %10.1f".format(
                    "${blueSkill.label} vs ${orangeSkill.label}",
                    tally.percent(tally.blue), tally.percent(tally.orange), tally.percent(tally.draw),
                    tally.rounds.toDouble() / tally.played,
                    tally.percent(tally.lifeLost), tally.percent(tally.gridWins), tally.percent(tally.timeouts)
                )
            )
        }
        appendLine()
        appendLine("Compartments left at zero integrity, across every pairing:")
        val overall = Tally()
        for (blueSkill in Difficulty.entries) for (orangeSkill in Difficulty.entries) {
            for (seed in 0 until seeds) play(seed.toLong(), blueSkill, orangeSkill, overall)
        }
        Subsystem.entries.forEach { system ->
            appendLine("  %-14s %6.1f%% of stations".format(system.label, overall.destroyed.getValue(system) * 50.0 / overall.played))
        }
        appendLine()
        appendLine("A pairing that reads close to 50/50 is symmetric, not balanced: both sides run")
        appendLine("the same policy. The signal to read is whether a stronger policy beats a weaker")
        appendLine("one, whether matches end before the round limit, and whether all three victory")
        appendLine("routes occur. None of this is a substitute for a human playtest.")
    }

    /** A round-by-round transcript of one pairing, for reading why a policy loses. */
    fun trace(seed: Long, blueSkill: Difficulty, orangeSkill: Difficulty): String = buildString {
        appendLine("Transcript: ${blueSkill.label} (blue) vs ${orangeSkill.label} (orange), seed $seed.")
        var frame = Frame()
        while (frame.outcome == "IN_PROGRESS") {
            val blue = Rival.orders(seed, frame, blueSkill, Team.BLUE)
            val orange = Rival.orders(seed, frame, orangeSkill, Team.ORANGE)
            appendLine(
                "  R${frame.round} blue e${frame.blue.energy} g${frame.blue.relayPoints} ${frame.blue.integrity} " +
                    "-> ${blue.human.action} ${blue.human.target} ${blue.agent.output}"
            )
            appendLine(
                "     orange e${frame.orange.energy} g${frame.orange.relayPoints} ${frame.orange.integrity} " +
                    "-> ${orange.human.action} ${orange.human.target} ${orange.agent.output}"
            )
            frame = Rules.resolve(seed, frame, blue, orange).after
        }
        appendLine("  Result ${frame.outcome} after round ${frame.round}.")
    }
}

fun main() {
    println(Balance.report())
    println()
    println(Balance.trace(0, Difficulty.OPERATOR, Difficulty.VETERAN))
}
