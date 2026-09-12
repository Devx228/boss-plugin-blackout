package ai.rever.boss.plugin.dynamic.blackout.engine

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * How hard the shift is. The only levers are how long the batteries last and how many
 * compartments the pilot can physically reach before they die.
 */
@Serializable
enum class Difficulty(val label: String, val inspections: Int, val seconds: Int) {
    ROOKIE("ROOKIE", 5, 600),
    OPERATOR("OPERATOR", 4, 480),
    VETERAN("VETERAN", 3, 360)
}

@Serializable
enum class RecordKind { ROSTER, WORK, TELEMETRY, COMMS, SUPPLY }

/**
 * One line of the station archive. This is the archivist's half of the game and the pilot
 * never sees it. A record is a claim somebody entered, not a measurement of the present.
 */
@Serializable
data class Record(
    val time: String,
    val kind: RecordKind,
    val text: String,
    val site: String? = null,
    val crew: String? = null
)

/**
 * One compartment as the pilot finds it now. This is the pilot's half and the archivist
 * never sees it. [physical] is the present truth; [trace] is a personal item somebody left.
 */
@Serializable
data class Site(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val physical: String,
    val trace: String? = null
)

/**
 * A generated incident. Exactly one site carries work that was signed off and never done,
 * and exactly one crew member's belongings sit somewhere the roster does not put them.
 * Those two facts are held by different seats, which is the whole game.
 */
@Serializable
data class Case(
    val seed: Long,
    val sites: List<Site>,
    val crew: List<String>,
    val records: List<Record>,
    val faultSite: String,
    val culprit: String,
    val signedBy: String,
    val debrief: String
) {
    fun site(id: String): Site? = sites.firstOrNull { it.id == id }
}

/** A job that either got done or did not. The two states are what the pilot can tell apart. */
private data class Job(
    val place: String,
    val verb: String,
    val done: String,
    val undone: String,
    val routine: String
)

object CaseFile {

    const val BLACKOUT_TIME = "02:14"

    /** The pilot's standing brief. Short on purpose: nobody reads a manual during a demo. */
    const val BRIEF = "Main power failed at 02:14. You are aboard with a torch and only a few " +
        "compartments you can reach before the batteries die. Your archivist holds the station " +
        "records and cannot see anything you see. Find the compartment where the fault is, and name who caused it."

    /** The archivist's standing brief. Same length, other half of the split. */
    const val ARCHIVIST_BRIEF = "Main power failed at 02:14. You hold the station archive: the shift roster, " +
        "signed work orders, telemetry, comms and supply lines. You cannot see the station itself. Your pilot " +
        "is walking it with a torch and can reach only a few compartments. The archive records what people " +
        "claimed. Only the pilot can tell you what is actually there. Work out which signed-off job was never " +
        "really done, and which crew member was somewhere the roster does not put them."

    private val JOBS = listOf(
        Job(
            "Reactor Bay", "re-seat the primary control rod coupling",
            "The rod coupling is seated and torque-marked in fresh paint.",
            "The rod coupling is backed out two full turns. The torque paint is unbroken.",
            "The reactor bay is quiet. Rod position reads nominal on the mechanical gauge."
        ),
        Job(
            "Coolant Loop A", "close the isolation valve after the purge",
            "The isolation valve is closed and lockwired.",
            "The isolation valve stands wide open and the purge line is still bled.",
            "Loop A is cold to the touch. The sight glass is full."
        ),
        Job(
            "Main Bus Junction", "reset the main breaker after the load test",
            "The main breaker is closed and the lockout tag is signed and removed.",
            "The main breaker is still open. A lockout tag hangs off it, unsigned.",
            "The junction is tidy. Every bus tag is in its clip."
        ),
        Job(
            "Port Airlock", "replace the outer seal gasket",
            "A new gasket sits in the outer seal, dated today.",
            "The outer seal still carries the old gasket, cracked along one edge.",
            "The airlock is pressurised and the inner door indicator is green."
        ),
        Job(
            "Hydroponics", "restore the grow-bank supply feed",
            "The supply feed is reconnected and the banks are lit.",
            "The supply feed hangs disconnected. The banks are dark and the trays are cold.",
            "The banks are lit on battery. Water is circulating."
        ),
        Job(
            "Comms Mast", "swap the transponder module",
            "A new transponder module is racked and its packing seal is broken.",
            "The old transponder module is still racked. The replacement is sealed in its case.",
            "The mast feed is intact. The rack fans are spinning on reserve."
        ),
        Job(
            "Cargo Hold", "tension the loose pallet restraints",
            "Every pallet restraint is tensioned and pinned.",
            "Three pallet restraints hang loose across the deck.",
            "The hold is secure. Nothing has shifted."
        ),
        Job(
            "Crew Quarters", "clear the blocked return vent",
            "The return vent is clear and a new filter is fitted.",
            "The return vent is still packed solid. The new filter is unopened on the floor.",
            "Quarters are dark and empty. Air is moving normally."
        ),
        Job(
            "Medical Bay", "recharge the emergency oxygen bank",
            "The oxygen bank reads full and the log card is signed.",
            "The oxygen bank reads a quarter full. The log card is blank.",
            "The bay is sealed and cold. Cabinet seals are unbroken."
        ),
        Job(
            "Bridge", "reload the navigation checksum",
            "The navigation console carries today's checksum.",
            "The navigation console still carries last week's checksum.",
            "The bridge runs on reserve. Every console is on its standby page."
        )
    )

    private val CREW = listOf("VEGA", "RHODES", "OKONKWO", "SALTER", "IBARRA", "NOVAK", "TAM", "WHITFIELD")

    private val ITEMS = listOf(
        "a toolkit stencilled", "a thermal mug taped with", "a jacket with the name tab",
        "a data slate still logged in as", "a pair of work gloves marked", "a hand torch scratched with",
        "a ration tin signed", "a headset labelled"
    )

    /** Eight fixed berths, two rows either side of a central spine. The map shape never changes. */
    private val SLOTS = listOf(
        0.11f to 0.30f, 0.37f to 0.20f, 0.63f to 0.20f, 0.89f to 0.30f,
        0.11f to 0.70f, 0.37f to 0.80f, 0.63f to 0.80f, 0.89f to 0.70f
    )

    fun generate(seed: Long): Case {
        val rng = Random(seed)
        val jobs = JOBS.shuffled(rng).take(SLOTS.size)
        val crew = CREW.shuffled(rng).take(4)
        val items = ITEMS.shuffled(rng)

        // Three compartments were worked during the night. The first of them is the one that lied.
        val worked = SLOTS.indices.shuffled(rng).take(3)
        val faultIndex = worked[0]

        // The signer put their name on the fault's work order and was genuinely rostered there,
        // so the archive alone shows nothing wrong with it. The culprit was rostered elsewhere.
        val signedBy = crew[0]
        val culprit = crew[1]
        val honest = listOf(crew[2], crew[3])

        // The signer and the two honest workers sit on the three worked compartments. The
        // culprit sits on a compartment nobody touched, which is what makes their trace damning.
        val roster = linkedMapOf(
            faultIndex to signedBy,
            worked[1] to honest[0],
            worked[2] to honest[1]
        )
        val idle = SLOTS.indices.first { it !in worked }
        roster[idle] = culprit

        val sites = SLOTS.mapIndexed { index, slot ->
            val job = jobs[index]
            val physical = when (index) {
                faultIndex -> job.undone
                worked[1], worked[2] -> job.done
                else -> job.routine
            }
            // Two personal items are lying about. Only one of them is somewhere its owner was
            // not rostered, and only the archivist's roster can tell the two apart.
            val trace = when (index) {
                faultIndex -> items[0] + " " + culprit
                worked[2] -> items[1] + " " + honest[1]
                else -> null
            }
            Site("L" + (index + 1), job.place, slot.first, slot.second, physical, trace)
        }

        val records = buildRecords(rng, sites, roster, worked, faultIndex, signedBy, jobs)

        val debrief = culprit + " signed the " + sites[faultIndex].name + " work order under " + signedBy +
            "'s credentials and never did the job. It was to " + jobs[faultIndex].verb + ". Because it was " +
            "never done, main power dropped at " + BLACKOUT_TIME + ". The roster had " + culprit + " in " +
            sites[idle].name + " at the time, but they left a personal item behind in " + sites[faultIndex].name + "."

        return Case(seed, sites, crew, records, sites[faultIndex].id, culprit, signedBy, debrief)
    }

    private fun buildRecords(
        rng: Random,
        sites: List<Site>,
        roster: Map<Int, String>,
        worked: List<Int>,
        faultIndex: Int,
        signedBy: String,
        jobs: List<Job>
    ): List<Record> {
        val records = mutableListOf<Record>()

        roster.forEach { (index, member) ->
            records += Record(
                "02:00", RecordKind.ROSTER,
                "Shift roster 02:00 to 02:30 places " + member + " in " + sites[index].name + ".",
                sites[index].id, member
            )
        }

        // Every worked compartment carries an identical-looking signed work order. The archive
        // cannot tell which of them was actually carried out. Only the pilot's eyes can.
        val times = mutableListOf<String>()
        while (times.size < worked.size) {
            val minute = rng.nextInt(30, 130)
            val stamp = "0" + (1 + minute / 60) + ":" + (minute % 60).toString().padStart(2, '0')
            if (stamp !in times) times += stamp
        }
        times.sort()
        worked.forEachIndexed { slot, index ->
            val signer = if (index == faultIndex) signedBy else roster.getValue(index)
            records += Record(
                times[slot], RecordKind.WORK,
                signer + " signed off: " + jobs[index].verb + " in " + sites[index].name + ". Marked complete.",
                sites[index].id, signer
            )
        }

        // The badge network is dead on purpose. It is the in-fiction reason the archive can never
        // answer "who was where" by itself, and therefore the reason the pilot's eyes matter.
        records += Record(
            "01:00", RecordKind.TELEMETRY,
            "Badge reader network dropped to standby to save load. No personnel movements recorded after this point."
        )
        records += Record(
            BLACKOUT_TIME, RecordKind.TELEMETRY,
            "Main bus voltage collapsed to zero across all frames. No cause recorded."
        )
        records += Record("02:16", RecordKind.COMMS, "Distress beacon armed automatically on battery.")

        val noise = listOf(
            Record("01:42", RecordKind.COMMS, "Outbound relay test to Gateway, acknowledged, no faults."),
            Record("23:10", RecordKind.SUPPLY, "Consumables transfer from Hold 2 completed and signed."),
            Record("01:58", RecordKind.TELEMETRY, "Bus B temperature rose four degrees, then settled."),
            Record("00:35", RecordKind.SUPPLY, "Spares requisition raised for seal stock. Not yet filled."),
            Record("02:09", RecordKind.TELEMETRY, "Hull stress nominal on all frames."),
            Record("00:50", RecordKind.COMMS, "Routine check-in with Gateway. Nothing to report.")
        )
        records += noise.shuffled(rng).take(4)

        return records.sortedBy { it.time }
    }
}
