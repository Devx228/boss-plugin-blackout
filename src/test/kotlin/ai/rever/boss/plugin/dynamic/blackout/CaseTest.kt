package ai.rever.boss.plugin.dynamic.blackout

import ai.rever.boss.plugin.dynamic.blackout.engine.CaseFile
import ai.rever.boss.plugin.dynamic.blackout.engine.Difficulty
import ai.rever.boss.plugin.dynamic.blackout.engine.RecordKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The generator has to produce a case that is solvable together and unsolvable apart. These
 * tests are the only thing standing between that claim and wishful thinking.
 */
class CaseTest {

    private val seeds = (1L..400L).toList()

    @Test
    fun `every case has exactly one fault and one culprit`() {
        seeds.forEach { seed ->
            val case = CaseFile.generate(seed)
            assertNotNull(case.site(case.faultSite), "seed $seed has a fault site that is not on the map")
            assertTrue(case.culprit in case.crew, "seed $seed blames somebody who is not aboard")
            assertTrue(case.signedBy in case.crew, "seed $seed has a signer who is not aboard")
            assertTrue(case.culprit != case.signedBy, "seed $seed blames the signer, which makes the case trivial")
            assertEquals(8, case.sites.size)
            assertEquals(4, case.crew.size)
        }
    }

    @Test
    fun `exactly two personal items are left behind and one of them is the culprit's`() {
        seeds.forEach { seed ->
            val case = CaseFile.generate(seed)
            val traces = case.sites.filter { it.trace != null }
            assertEquals(2, traces.size, "seed $seed does not leave exactly two personal items")
            val atFault = traces.single { it.id == case.faultSite }
            assertTrue(
                atFault.trace!!.contains(case.culprit),
                "seed $seed does not put the culprit's property at the fault"
            )
            val decoy = traces.single { it.id != case.faultSite }
            assertTrue(
                case.crew.any { decoy.trace!!.contains(it) },
                "seed $seed leaves an item belonging to nobody aboard"
            )
        }
    }

    @Test
    fun `three compartments carry signed work and each signer was rostered there`() {
        seeds.forEach { seed ->
            val case = CaseFile.generate(seed)
            val work = case.records.filter { it.kind == RecordKind.WORK }
            assertEquals(3, work.size, "seed $seed does not sign off exactly three jobs")
            assertTrue(work.any { it.site == case.faultSite }, "seed $seed never signed off the job that failed")

            val roster = case.records.filter { it.kind == RecordKind.ROSTER }.associate { it.site to it.crew }
            work.forEach { order ->
                assertEquals(
                    roster[order.site], order.crew,
                    "seed $seed signs a job by somebody the roster puts elsewhere, which lets the archive solve it alone"
                )
            }
        }
    }

    @Test
    fun `the archive alone cannot pick the fault`() {
        // Every signed job looks equally legitimate on paper. If one of them stood out in the
        // records, the pilot would be decoration.
        seeds.forEach { seed ->
            val case = CaseFile.generate(seed)
            val work = case.records.filter { it.kind == RecordKind.WORK }
            val shapes = work.map { order ->
                order.text.substringAfter("signed off:").substringBefore(" in ").trim().isNotEmpty() &&
                    order.text.endsWith("Marked complete.")
            }
            assertTrue(shapes.all { it }, "seed $seed writes work orders in different shapes")
            assertEquals(
                1, work.map { it.text.substringAfterLast(". ") }.distinct().size,
                "seed $seed lets the wording of one work order give the answer away"
            )
        }
    }

    @Test
    fun `the station alone cannot name the culprit`() {
        // The only thing that makes one personal item damning is the roster, which lives in
        // the archive. Without it both items look identical to the pilot.
        seeds.forEach { seed ->
            val case = CaseFile.generate(seed)
            val traces = case.sites.filter { it.trace != null }
            val owners = traces.map { trace -> case.crew.first { trace.trace!!.contains(it) } }
            assertEquals(2, owners.distinct().size, "seed $seed leaves both items for the same person")
            assertTrue(
                case.sites.none { it.physical.contains(case.culprit) },
                "seed $seed names the culprit in a physical description, which skips the archive"
            )
        }
    }

    @Test
    fun `no difficulty lets the pilot reach the whole station`() {
        // If the pilot could search everything, they would never need to be told where to look.
        Difficulty.entries.forEach { level ->
            assertTrue(
                level.inspections < CaseFile.generate(1L).sites.size,
                "${level.label} can reach every compartment, so the archivist is optional"
            )
        }
    }

    @Test
    fun `the badge network is always recorded as down`() {
        // This is the in-fiction reason the archive cannot answer "who was where" by itself.
        seeds.take(50).forEach { seed ->
            val case = CaseFile.generate(seed)
            assertTrue(
                case.records.any { it.text.contains("Badge reader network") },
                "seed $seed leaves the badge network up, which would hand the archivist the culprit"
            )
        }
    }

    @Test
    fun `the same seed always builds the same case`() {
        seeds.take(50).forEach { seed ->
            val a = CaseFile.generate(seed)
            val b = CaseFile.generate(seed)
            assertEquals(a.faultSite, b.faultSite)
            assertEquals(a.culprit, b.culprit)
            assertEquals(a.sites, b.sites)
            assertEquals(a.records, b.records)
        }
    }

    @Test
    fun `different seeds do not all produce the same answer`() {
        val cases = seeds.map { CaseFile.generate(it) }
        assertTrue(cases.map { it.faultSite }.distinct().size > 4, "the fault is barely moving between seeds")
        assertTrue(cases.map { it.culprit }.distinct().size > 4, "the culprit is barely moving between seeds")
    }
}
