package ai.rever.boss.plugin.dynamic.blackout.engine

import kotlinx.serialization.Serializable
import java.util.Random

@Serializable enum class Team { BLUE, ORANGE; fun opponent() = if (this == BLUE) ORANGE else BLUE }
@Serializable enum class Action { FIRE, SHIELD, REPAIR, RELAY, HOLD }
@Serializable enum class Thermal { STANDARD, COOL }
@Serializable enum class Polarity { DIRECT, INVERT }
@Serializable enum class Output { NORMAL, BOOST }

/**
 * The four compartments of a station. Every shot names one, so an attack is a decision
 * about what to take away from the other crew rather than a number going down.
 */
@Serializable
enum class Subsystem(val label: String, val role: String) {
    REACTOR("REACTOR", "Supplies energy between rounds"),
    SHIELDS("SHIELD ARRAY", "Raises the barriers that absorb hits"),
    RELAY("RELAY MAST", "Drives the push for grid control"),
    LIFE("LIFE SUPPORT", "Lose it and the crew is out")
}

/** One value per compartment. Used for both integrity and standing barriers. */
@Serializable
data class Grid(val reactor: Int, val shields: Int, val relay: Int, val life: Int) {
    operator fun get(system: Subsystem): Int = when (system) {
        Subsystem.REACTOR -> reactor
        Subsystem.SHIELDS -> shields
        Subsystem.RELAY -> relay
        Subsystem.LIFE -> life
    }
    fun with(system: Subsystem, value: Int): Grid = when (system) {
        Subsystem.REACTOR -> copy(reactor = value)
        Subsystem.SHIELDS -> copy(shields = value)
        Subsystem.RELAY -> copy(relay = value)
        Subsystem.LIFE -> copy(life = value)
    }
    val total: Int get() = reactor + shields + relay + life
    companion object {
        fun of(value: Int) = Grid(value, value, value, value)
    }
}

@Serializable
data class Ship(
    val integrity: Grid = Grid.of(Rules.MAX_INTEGRITY),
    val barrier: Grid = Grid.of(0),
    val energy: Int = Rules.START_ENERGY,
    val relayPoints: Int = 0
) {
    fun status(system: Subsystem): String = when {
        integrity[system] == 0 -> "DOWN"
        integrity[system] <= Rules.IMPAIRED_AT -> "DAMAGED"
        else -> "ONLINE"
    }
}

@Serializable data class Route(val id: String, val insulated: Boolean, val crossed: Boolean, val broken: Boolean, val highOutput: Boolean)
@Serializable data class Circuit(val hot: Boolean, val reversed: Boolean, val routes: List<Route>)
@Serializable data class HumanOrder(val action: Action, val route: String, val target: Subsystem = Subsystem.LIFE)
@Serializable data class AgentOrder(val thermal: Thermal, val polarity: Polarity, val output: Output)
@Serializable data class Orders(val human: HumanOrder, val agent: AgentOrder)
@Serializable data class Frame(val round: Int = 1, val blue: Ship = Ship(), val orange: Ship = Ship(), val outcome: String = "IN_PROGRESS") {
    fun ship(team: Team) = if (team == Team.BLUE) blue else orange
}
@Serializable data class Resolution(val before: Frame, val after: Frame, val blueOrder: Orders?, val orangeOrder: Orders?, val events: List<String>)

object Rules {
    const val VERSION = "2.0"
    const val MAX_ROUNDS = 6
    const val MAX_INTEGRITY = 8
    const val MAX_BARRIER = 6
    const val MAX_ENERGY = 10
    const val START_ENERGY = 6
    const val RELAY_TARGET = 3
    const val ROUND_SECONDS = 120

    /** At or below this integrity a compartment still runs, but badly. */
    const val IMPAIRED_AT = 3

    const val FIRE_DAMAGE = 4
    const val FIRE_DAMAGE_BOOST = 6
    const val SHIELD_GAIN = 3
    const val SHIELD_GAIN_BOOST = 5
    const val REPAIR_GAIN = 3
    const val REPAIR_GAIN_BOOST = 5
    const val ENERGY_FULL = 3
    const val ENERGY_IMPAIRED = 1

    /**
     * The blackout deepens. From the third round the grid is unstable enough that every
     * weapon draws harder, so a stalemate between two careful crews cannot run forever.
     */
    fun escalation(round: Int): Int = ((round - 1) / 2).coerceAtMost(2)

    fun fireDamage(round: Int, boost: Boolean): Int =
        (if (boost) FIRE_DAMAGE_BOOST else FIRE_DAMAGE) + escalation(round)

    fun cost(action: Action, output: Output): Int = when (action) {
        Action.HOLD -> 0
        Action.FIRE -> 3
        else -> 2
    } + if (action != Action.HOLD && output == Output.BOOST) 1 else 0

    /** A damaged reactor starves the crew of choices long before the station is lost. */
    fun energyIncome(ship: Ship): Int = when {
        ship.integrity[Subsystem.REACTOR] == 0 -> 0
        ship.integrity[Subsystem.REACTOR] <= IMPAIRED_AT -> ENERGY_IMPAIRED
        else -> ENERGY_FULL
    }

    /** A wrecked shield array cannot hold a barrier however much power it is given. */
    fun barrierCap(ship: Ship): Int = when {
        ship.integrity[Subsystem.SHIELDS] == 0 -> 0
        ship.integrity[Subsystem.SHIELDS] <= IMPAIRED_AT -> MAX_BARRIER / 2
        else -> MAX_BARRIER
    }

    /** A broken mast cannot win a contest it would otherwise have taken. */
    fun relayPush(ship: Ship, boost: Boolean): Int {
        val base = if (boost) 2 else 1
        val penalty = when {
            ship.integrity[Subsystem.RELAY] == 0 -> 2
            ship.integrity[Subsystem.RELAY] <= IMPAIRED_AT -> 1
            else -> 0
        }
        return (base - penalty).coerceAtLeast(0)
    }

    fun circuit(seed: Long, round: Int, team: Team): Circuit {
        val condition = Random(seed xor (round * 7919L))
        val hot = condition.nextBoolean()
        val reversed = condition.nextBoolean()
        val random = Random(seed xor (round * 104729L) xor (team.ordinal * 15485863L))
        // Preserve all four physical combinations. The required insulated route
        // is always intact and high-output; only ineligible routes may be broken.
        val routes = (0..3).map { n ->
            val insulated = n and 1 != 0
            val crossed = n and 2 != 0
            val guaranteed = insulated && crossed == reversed
            Route("", insulated, crossed, !guaranteed && crossed != reversed && condition.nextBoolean(), guaranteed || condition.nextBoolean())
        }.toMutableList()
        java.util.Collections.shuffle(routes, random)
        return Circuit(hot, reversed, routes.mapIndexed { i, route -> route.copy(id = ('A' + i).toString()) })
    }

    fun succeeds(circuit: Circuit, order: Orders): Boolean {
        if (order.human.action == Action.HOLD) return true
        val route = circuit.routes.find { it.id == order.human.route } ?: return false
        return !route.broken && (!circuit.hot || route.insulated) && route.crossed == circuit.reversed &&
            (order.agent.thermal == Thermal.COOL) == circuit.hot &&
            (order.agent.polarity == Polarity.INVERT) == circuit.reversed &&
            (order.agent.output == Output.NORMAL || route.highOutput)
    }

    fun resolve(seed: Long, frame: Frame, blue: Orders?, orange: Orders?): Resolution {
        require(frame.outcome == "IN_PROGRESS") { "Match finished" }
        val orders = listOf(blue, orange)
        val old = listOf(frame.blue, frame.orange)
        val next = old.toMutableList()
        val shots = arrayOfNulls<Pair<Subsystem, Int>>(2)
        val push = IntArray(2)
        val events = mutableListOf<String>()

        // Phase one: spend energy, check the circuit, apply defensive work.
        Team.entries.forEach { team ->
            val index = team.ordinal
            val order = orders[index]
            if (order == null) {
                events += "$team: incomplete orders; HOLD, no energy spent."
                return@forEach
            }
            require(order.human.action != Action.HOLD || order.agent.output == Output.NORMAL)
            val spend = cost(order.human.action, order.agent.output)
            require(spend <= old[index].energy) { "Insufficient energy" }
            var ship = old[index].copy(energy = old[index].energy - spend)
            val boost = order.agent.output == Output.BOOST
            val ok = succeeds(circuit(seed, frame.round, team), order)
            events += "$team: ${describe(order)}; cost $spend; ${if (ok) "coordinated" else "failed circuit"}."
            if (ok) when (order.human.action) {
                Action.FIRE -> shots[1 - index] = order.human.target to fireDamage(frame.round, boost)
                Action.SHIELD -> {
                    val target = order.human.target
                    val raised = (ship.barrier[target] + if (boost) SHIELD_GAIN_BOOST else SHIELD_GAIN).coerceAtMost(barrierCap(ship))
                    ship = ship.copy(barrier = ship.barrier.with(target, raised))
                    events += "$team: ${target.label} barrier now $raised."
                }
                Action.REPAIR -> {
                    val target = order.human.target
                    val restored = (ship.integrity[target] + if (boost) REPAIR_GAIN_BOOST else REPAIR_GAIN).coerceAtMost(MAX_INTEGRITY)
                    ship = ship.copy(integrity = ship.integrity.with(target, restored))
                    events += "$team: ${target.label} repaired to $restored."
                }
                Action.RELAY -> push[index] = relayPush(ship, boost)
                Action.HOLD -> Unit
            }
            next[index] = ship
        }

        // Phase two: both shots land at once, against the barrier on the named compartment.
        for (index in 0..1) {
            val shot = shots[index] ?: continue
            val (target, damage) = shot
            val ship = next[index]
            val absorbed = minOf(ship.barrier[target], damage)
            val before = ship.integrity[target]
            val after = (before - damage + absorbed).coerceAtLeast(0)
            next[index] = ship.copy(
                integrity = ship.integrity.with(target, after),
                barrier = ship.barrier.with(target, ship.barrier[target] - absorbed)
            )
            events += "${Team.entries[index]} ${target.label}: incoming $damage, barrier absorbed $absorbed, integrity $before to $after." +
                if (after == 0 && before > 0) " ${target.label} is offline." else ""
        }

        // Phase three: a failing life support bleeds the rest of the station. Which
        // compartment gives way is fixed by the seed, so a replay reproduces it exactly.
        for (index in 0..1) {
            val ship = next[index]
            if (ship.integrity[Subsystem.LIFE] !in 1..IMPAIRED_AT) continue
            val order = ((seed xor (frame.round * 2654435761L)) ushr (index * 8)).toInt()
            val system = Subsystem.entries[Math.floorMod(order, Subsystem.entries.size)]
            val before = ship.integrity[system]
            if (before == 0) continue
            next[index] = ship.copy(integrity = ship.integrity.with(system, before - 1))
            events += "${Team.entries[index]}: life support failing; ${system.label} bleeds to ${before - 1}."
        }

        // Phase four: the stronger push takes a grid point; an equal push takes nothing.
        for (index in 0..1) {
            if (push[index] > 0 && push[index] > push[1 - index]) {
                next[index] = next[index].copy(relayPoints = next[index].relayPoints + 1)
                events += "${Team.entries[index]}: relay push ${push[index]} beats ${push[1 - index]}; grid point taken."
            }
        }

        val b = next[0]
        val o = next[1]
        val result = when {
            b.integrity[Subsystem.LIFE] == 0 && o.integrity[Subsystem.LIFE] == 0 -> "DRAW"
            b.integrity[Subsystem.LIFE] == 0 -> "ORANGE"
            o.integrity[Subsystem.LIFE] == 0 -> "BLUE"
            b.relayPoints >= RELAY_TARGET -> "BLUE"
            o.relayPoints >= RELAY_TARGET -> "ORANGE"
            frame.round >= MAX_ROUNDS -> {
                val cmp = compareValuesBy(b, o, { it.relayPoints }, { it.integrity.total }, { it.barrier.total })
                if (cmp == 0) "DRAW" else if (cmp > 0) "BLUE" else "ORANGE"
            }
            else -> "IN_PROGRESS"
        }
        if (result == "IN_PROGRESS") for (index in 0..1) {
            val income = energyIncome(next[index])
            next[index] = next[index].copy(energy = (next[index].energy + income).coerceAtMost(MAX_ENERGY))
        }
        return Resolution(
            frame,
            Frame(if (result == "IN_PROGRESS") frame.round + 1 else frame.round, next[0], next[1], result),
            blue, orange, events
        )
    }

    private fun describe(order: Orders): String {
        val where = when (order.human.action) {
            Action.FIRE -> " at enemy ${order.human.target.label}"
            Action.SHIELD, Action.REPAIR -> " on own ${order.human.target.label}"
            else -> ""
        }
        return "${order.human.action} ${order.agent.output}$where via route ${order.human.route}, " +
            "${order.agent.thermal}/${order.agent.polarity}"
    }
}
