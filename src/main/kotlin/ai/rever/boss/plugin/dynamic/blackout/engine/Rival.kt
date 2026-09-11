package ai.rever.boss.plugin.dynamic.blackout.engine

import kotlinx.serialization.Serializable
import java.util.Random

/** Rival crew skill. Scripted, deterministic from the match seed, never inspects BLUE orders. */
@Serializable
enum class Difficulty(val label: String, val blurb: String) {
    CADET("CADET", "Rookie crew. Reads an instrument back wrong about one round in three, shoots at whatever is in front of it and never boosts."),
    OPERATOR("OPERATOR", "Competent crew. Configures correctly, works the weakest compartment and patches its own damage."),
    VETERAN("VETERAN", "Veteran crew. Starves your reactor, braces the compartment you are about to hit, denies the relay and takes lethal shots.")
}

/**
 * The scripted ORANGE crew.
 *
 * It sees only its own station, its own circuit and the public frame, exactly like a real
 * opposing crew would before simultaneous resolution. It never reads BLUE's pending orders
 * and never reads BLUE's private route map: everything below is derived from [Frame] and
 * from its own [Rules.circuit], both of which the real BLUE crew can also reason about.
 */
object Rival {

    fun orders(seed: Long, frame: Frame, difficulty: Difficulty, team: Team = Team.ORANGE): Orders {
        val me = frame.ship(team)
        val circuit = Rules.circuit(seed, frame.round, team)
        val rng = Random(seed xor (frame.round * 40503L) xor (difficulty.ordinal * 6364136L) xor (team.ordinal * 2654435L))

        var plan = plan(difficulty, frame, team, rng)
        var boost = difficulty == Difficulty.VETERAN && worthBoosting(plan, frame, team)
        while (plan.action != Action.HOLD && Rules.cost(plan.action, output(boost)) > me.energy) {
            if (boost) boost = false else plan = fallback(plan)
        }
        if (plan.action == Action.HOLD) boost = false

        val route = route(circuit, boost)
        var thermal = if (circuit.hot) Thermal.COOL else Thermal.STANDARD
        var polarity = if (circuit.reversed) Polarity.INVERT else Polarity.DIRECT
        // A rookie crew talks past each other: one instrument is read back wrong.
        if (difficulty == Difficulty.CADET && rng.nextInt(100) < 30) {
            if (rng.nextBoolean()) thermal = flip(thermal) else polarity = flip(polarity)
        }
        return Orders(HumanOrder(plan.action, route.id, plan.target), AgentOrder(thermal, polarity, output(boost)))
    }

    private data class Plan(val action: Action, val target: Subsystem)

    private fun output(boost: Boolean) = if (boost) Output.BOOST else Output.NORMAL
    private fun flip(t: Thermal) = if (t == Thermal.COOL) Thermal.STANDARD else Thermal.COOL
    private fun flip(p: Polarity) = if (p == Polarity.INVERT) Polarity.DIRECT else Polarity.INVERT

    /** Rules.circuit always leaves one intact, insulated, high-output solution, so this cannot fail. */
    private fun route(circuit: Circuit, boost: Boolean): Route = circuit.routes.first {
        !it.broken && (!circuit.hot || it.insulated) && it.crossed == circuit.reversed && (!boost || it.highOutput)
    }

    /** Dropping to a cheaper order rather than skipping the round entirely. */
    private fun fallback(plan: Plan): Plan = when (plan.action) {
        Action.FIRE -> Plan(Action.RELAY, Subsystem.RELAY)
        Action.SHIELD, Action.REPAIR, Action.RELAY -> Plan(Action.HOLD, plan.target)
        Action.HOLD -> plan
    }

    private fun plan(difficulty: Difficulty, frame: Frame, team: Team, rng: Random): Plan {
        val me = frame.ship(team)
        val foe = frame.ship(team.opponent())
        return when (difficulty) {
            Difficulty.CADET -> cadet(me, rng)
            Difficulty.OPERATOR -> operator(me, foe, frame.round)
            Difficulty.VETERAN -> veteran(me, foe, frame.round)
        }
    }

    /** A rookie fixes whatever hurts and otherwise shoots at life support. */
    private fun cadet(me: Ship, rng: Random): Plan {
        val hurt = weakest(me)
        return when {
            me.integrity[Subsystem.LIFE] <= Rules.IMPAIRED_AT && me.energy >= 2 -> Plan(Action.REPAIR, Subsystem.LIFE)
            me.integrity[hurt] <= 2 && me.energy >= 2 -> Plan(Action.REPAIR, hurt)
            me.energy >= 3 -> Plan(Action.FIRE, if (rng.nextInt(100) < 60) Subsystem.LIFE else randomSystem(rng))
            me.energy >= 2 -> Plan(Action.RELAY, Subsystem.RELAY)
            else -> Plan(Action.HOLD, Subsystem.LIFE)
        }
    }

    /** A competent crew works the enemy's weakest compartment and patches its own. */
    private fun operator(me: Ship, foe: Ship, round: Int): Plan {
        val prize = weakest(foe)
        val damage = Rules.fireDamage(round, false)
        return when {
            me.energy >= 3 && foe.integrity[Subsystem.LIFE] <= damage - foe.barrier[Subsystem.LIFE] ->
                Plan(Action.FIRE, Subsystem.LIFE)
            me.integrity[Subsystem.LIFE] <= Rules.IMPAIRED_AT && me.energy >= 2 -> Plan(Action.REPAIR, Subsystem.LIFE)
            downed(me) != null && me.energy >= 2 -> Plan(Action.REPAIR, downed(me)!!)
            me.energy >= 3 && foe.integrity[prize] <= damage -> Plan(Action.FIRE, prize)
            me.barrier[Subsystem.LIFE] == 0 && foe.energy >= 3 && me.energy >= 2 -> Plan(Action.SHIELD, Subsystem.LIFE)
            // Take the grid when the shooting is going nowhere.
            me.relayPoints >= foe.relayPoints && me.energy >= 5 && Rules.relayPush(me, false) > 0 ->
                Plan(Action.RELAY, Subsystem.RELAY)
            me.energy >= 3 -> Plan(Action.FIRE, prize)
            me.energy >= 2 -> Plan(Action.RELAY, Subsystem.RELAY)
            else -> Plan(Action.HOLD, Subsystem.LIFE)
        }
    }

    /**
     * A veteran plays the economy. It takes a lethal shot when one exists, otherwise it
     * starves the reactor early, braces the compartment it would attack in your place,
     * and refuses to let the relay race be decided against it.
     */
    private fun veteran(me: Ship, foe: Ship, round: Int): Plan {
        val lethalNormal = foe.integrity[Subsystem.LIFE] <= Rules.fireDamage(round, false) - foe.barrier[Subsystem.LIFE]
        val lethalBoost = foe.integrity[Subsystem.LIFE] <= Rules.fireDamage(round, true) - foe.barrier[Subsystem.LIFE]
        val exposed = me.integrity[Subsystem.LIFE] - me.barrier[Subsystem.LIFE] <= Rules.fireDamage(round, true)
        val guess = mostLikelyTarget(me)
        val endgame = round >= Rules.MAX_ROUNDS - 1
        val canPush = me.energy >= 2 && Rules.relayPush(me, false) > 0
        return when {
            // A shot that ends the match beats every other consideration.
            me.energy >= 3 && lethalNormal -> Plan(Action.FIRE, Subsystem.LIFE)
            me.energy >= 4 && lethalBoost -> Plan(Action.FIRE, Subsystem.LIFE)
            // Losing the grid loses the match. Shooting the mast cannot stop a push that is
            // already under way, because pushes are measured before the shots land, so the
            // only answer to a crew one point from the grid is to contest the push itself.
            foe.relayPoints >= Rules.RELAY_TARGET - 1 && canPush -> Plan(Action.RELAY, Subsystem.RELAY)
            me.relayPoints >= Rules.RELAY_TARGET - 1 && canPush -> Plan(Action.RELAY, Subsystem.RELAY)
            // Do not die to the obvious counter.
            exposed && me.barrier[Subsystem.LIFE] == 0 && Rules.barrierCap(me) > 0 && me.energy >= 2 ->
                Plan(Action.SHIELD, Subsystem.LIFE)
            me.integrity[Subsystem.LIFE] <= Rules.IMPAIRED_AT && me.energy >= 2 -> Plan(Action.REPAIR, Subsystem.LIFE)
            // An uncontested push is a free point. Never let one happen.
            foe.relayPoints > me.relayPoints && canPush -> Plan(Action.RELAY, Subsystem.RELAY)
            // Cripple the mast so later pushes are cheaper to beat.
            foe.relayPoints > me.relayPoints && me.energy >= 3 && foe.integrity[Subsystem.RELAY] > Rules.IMPAIRED_AT ->
                Plan(Action.FIRE, Subsystem.RELAY)
            // Cut the fuel line while there is still time for it to matter.
            round == 1 && me.energy >= 3 && foe.integrity[Subsystem.REACTOR] > Rules.IMPAIRED_AT ->
                Plan(Action.FIRE, Subsystem.REACTOR)
            // Rebuild the compartment whose loss is costing the most.
            downed(me) != null && me.energy >= 2 -> Plan(Action.REPAIR, downed(me)!!)
            me.integrity[Subsystem.REACTOR] <= Rules.IMPAIRED_AT && me.energy >= 2 -> Plan(Action.REPAIR, Subsystem.REACTOR)
            // A match that reaches the round limit is decided on grid points, then total
            // integrity, then barriers. Play for that instead of firing into a repair.
            endgame && me.energy >= 2 -> endgamePlan(me, foe, guess)
            me.energy >= 5 && me.barrier[guess] == 0 && Rules.barrierCap(me) > 0 -> Plan(Action.SHIELD, guess)
            me.energy >= 3 -> Plan(Action.FIRE, weakest(foe))
            me.energy >= 2 -> Plan(Action.RELAY, Subsystem.RELAY)
            else -> Plan(Action.HOLD, Subsystem.LIFE)
        }
    }

    private fun endgamePlan(me: Ship, foe: Ship, guess: Subsystem): Plan {
        val patchable = Subsystem.entries.filter { me.integrity[it] < Rules.MAX_INTEGRITY }
        return when {
            me.relayPoints < foe.relayPoints && Rules.relayPush(me, false) > 0 -> Plan(Action.RELAY, Subsystem.RELAY)
            patchable.isNotEmpty() -> Plan(Action.REPAIR, patchable.minByOrNull { me.integrity[it] }!!)
            Rules.barrierCap(me) > me.barrier[guess] -> Plan(Action.SHIELD, guess)
            Rules.relayPush(me, false) > 0 -> Plan(Action.RELAY, Subsystem.RELAY)
            else -> Plan(Action.HOLD, Subsystem.LIFE)
        }
    }

    /** The compartment an opponent gains most from removing next. */
    private fun mostLikelyTarget(me: Ship): Subsystem {
        if (me.integrity[Subsystem.LIFE] <= Rules.FIRE_DAMAGE_BOOST) return Subsystem.LIFE
        return Subsystem.entries.filter { me.integrity[it] > 0 }.minByOrNull { me.integrity[it] } ?: Subsystem.LIFE
    }

    private fun weakest(ship: Ship): Subsystem =
        Subsystem.entries.filter { ship.integrity[it] > 0 }.minByOrNull { ship.integrity[it] } ?: Subsystem.LIFE

    private fun downed(ship: Ship): Subsystem? =
        Subsystem.entries.firstOrNull { ship.integrity[it] == 0 && it != Subsystem.LIFE }

    private fun randomSystem(rng: Random) = Subsystem.entries[rng.nextInt(Subsystem.entries.size)]

    private fun worthBoosting(plan: Plan, frame: Frame, team: Team): Boolean {
        val me = frame.ship(team)
        if (plan.action == Action.HOLD) return false
        if (Rules.cost(plan.action, Output.BOOST) > me.energy) return false
        return when (plan.action) {
            Action.FIRE -> frame.ship(team.opponent()).integrity[plan.target] in 1..Rules.FIRE_DAMAGE_BOOST || me.energy >= 8
            Action.RELAY -> me.energy >= 4
            Action.SHIELD -> me.energy >= 6 && Rules.barrierCap(me) >= Rules.SHIELD_GAIN_BOOST
            else -> me.energy >= 8
        }
    }
}
