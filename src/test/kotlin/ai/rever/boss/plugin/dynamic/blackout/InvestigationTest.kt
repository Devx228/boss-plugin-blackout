package ai.rever.boss.plugin.dynamic.blackout

import ai.rever.boss.plugin.dynamic.blackout.application.GameError
import ai.rever.boss.plugin.dynamic.blackout.application.Investigation
import ai.rever.boss.plugin.dynamic.blackout.application.Verdict
import ai.rever.boss.plugin.dynamic.blackout.engine.Difficulty
import ai.rever.boss.plugin.dynamic.blackout.engine.RecordKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvestigationTest {

    private var now = 0L
    private fun game(difficulty: Difficulty = Difficulty.OPERATOR, seed: Long = 42L) =
        Investigation(seed, difficulty, clock = { now })

    private fun tick(seconds: Int) { now += seconds * 1000L }

    @Test
    fun `walking is limited and spent walks stay spent`() {
        val g = game(Difficulty.VETERAN)
        val ids = g.case.sites.map { it.id }
        repeat(Difficulty.VETERAN.inspections) { g.inspect(ids[it]) }
        assertEquals(0, g.pilotView().inspectionsLeft)
        assertFailsWith<GameError> { g.inspect(ids.last()) }
        // Re-selecting somewhere already searched is free, not a fifth walk.
        g.inspect(ids[0])
        assertEquals(0, g.pilotView().inspectionsLeft)
    }

    @Test
    fun `a compartment shows nothing until it has been walked to`() {
        val g = game()
        val target = g.case.sites.first { it.trace != null }
        val before = g.pilotView().sites.first { it.id == target.id }
        assertFalse(before.inspected)
        assertNull(before.physical)
        assertNull(before.trace)

        g.inspect(target.id)
        val after = g.pilotView().sites.first { it.id == target.id }
        assertTrue(after.inspected)
        assertEquals(target.physical, after.physical)
        assertEquals(target.trace, after.trace)
    }

    @Test
    fun `the archivist never sees a finding the pilot has not sent`() {
        val g = game()
        val fault = g.case.site(g.case.faultSite)!!
        g.inspect(fault.id)

        val silent = Json.encodeToString(g.observe())
        assertFalse(silent.contains(fault.physical), "the archivist read a compartment the pilot never reported")
        fault.trace?.let { assertFalse(silent.contains(it), "the archivist saw a personal item nobody reported") }

        g.report(fault.id)
        tick(2)
        val told = Json.encodeToString(g.observe())
        assertTrue(told.contains(fault.physical), "reporting a finding did not reach the archivist")
    }

    @Test
    fun `the archivist never receives the map or the answer`() {
        val g = game()
        val view = Json.encodeToString(g.observe())
        // Compartment names are fair game; positions, physical states and the solution are not.
        g.case.sites.forEach { site ->
            assertFalse(view.contains(site.physical), "a physical state leaked into the archivist view")
        }
        assertFalse(view.contains("\"x\""), "the archivist was handed map coordinates")
        assertFalse(view.contains(g.case.debrief), "the archivist was handed the debrief")
    }

    @Test
    fun `reporting the same compartment twice does not spam the channel`() {
        val g = game()
        val site = g.case.sites.first()
        g.inspect(site.id)
        g.report(site.id)
        g.report(site.id)
        assertEquals(1, g.observe().reported.size)
    }

    @Test
    fun `a compartment cannot be reported before it is searched`() {
        val g = game()
        assertFailsWith<GameError> { g.report(g.case.sites.last().id) }
    }

    @Test
    fun `the right call closes the case`() {
        val g = game()
        assertTrue(g.accuse(g.case.faultSite, g.case.culprit))
        assertEquals("SOLVED", g.pilotView().outcome)
        assertTrue(g.finished())
        assertEquals(g.case.culprit, g.debrief().culprit)
    }

    @Test
    fun `a wrong call costs a minute and the second one ends it`() {
        val g = game()
        val wrongSite = g.case.sites.first { it.id != g.case.faultSite }.id
        val before = g.pilotView().secondsLeft

        assertFalse(g.accuse(wrongSite, g.case.culprit))
        val after = g.pilotView()
        assertEquals("IN_PROGRESS", after.outcome)
        assertEquals(1, after.attemptsLeft)
        assertTrue(after.secondsLeft <= before - 60, "a wrong call did not cost battery")

        assertFalse(g.accuse(wrongSite, g.case.culprit))
        assertEquals("FAILED", g.pilotView().outcome)
    }

    @Test
    fun `naming the right compartment with the wrong crew member is still wrong`() {
        val g = game()
        val innocent = g.case.crew.first { it != g.case.culprit }
        assertFalse(g.accuse(g.case.faultSite, innocent))
        assertEquals("IN_PROGRESS", g.pilotView().outcome)
    }

    @Test
    fun `a case that is not called in time is lost`() {
        val g = game(Difficulty.VETERAN)
        tick(Difficulty.VETERAN.seconds + 1)
        assertEquals("FAILED", g.pilotView().outcome)
        assertFailsWith<GameError> { g.inspect(g.case.sites.first().id) }
    }

    @Test
    fun `a closed case stays closed under every further call`() {
        val g = game()
        g.accuse(g.case.faultSite, g.case.culprit)
        assertFailsWith<GameError> { g.inspect(g.case.sites.first().id) }
        assertFailsWith<GameError> { g.say("anyone there") }
        assertFailsWith<GameError> { g.archive("ALL") }
        assertFailsWith<GameError> { g.message("r1", "hello") }
        assertFailsWith<GameError> { g.mark("r2", g.case.sites.first().name, Verdict.SUSPECT, "why") }
        assertEquals("SOLVED", g.pilotView().outcome)
    }

    @Test
    fun `pausing stops the battery and resuming starts it again`() {
        val g = game()
        val start = g.pilotView().secondsLeft
        g.pause()
        tick(120)
        assertEquals(start, g.pilotView().secondsLeft, "the battery drained while the case was paused")
        g.resume()
        tick(30)
        assertTrue(g.pilotView().secondsLeft <= start - 30)
    }

    // ---- archivist surface ---------------------------------------------------------------

    @Test
    fun `observe is rate limited to once a second`() {
        val g = game()
        g.observe()
        assertFailsWith<GameError> { g.observe() }
        tick(2)
        g.observe()
    }

    @Test
    fun `ALL returns the whole archive and a term narrows it`() {
        val g = game()
        val all = g.archive("ALL")
        assertEquals(g.case.records.size, all.size)
        val roster = g.archive("roster")
        assertTrue(roster.isNotEmpty())
        assertTrue(roster.size < all.size, "a search term returned the whole archive")
    }

    @Test
    fun `the archive search budget runs out`() {
        val g = game()
        repeat(10) { g.archive("ALL") }
        assertFailsWith<GameError> { g.archive("ALL") }
    }

    @Test
    fun `a mark lands on the pilot's map`() {
        val g = game()
        val site = g.case.sites.first()
        g.mark("r1", site.name.lowercase(), Verdict.SUSPECT, "Work was signed off here overnight.")
        val drawn = g.pilotView().sites.first { it.id == site.id }.mark
        assertEquals(Verdict.SUSPECT, drawn?.verdict)
        assertEquals("Work was signed off here overnight.", drawn?.reason)
    }

    @Test
    fun `a mark on a compartment that does not exist is refused`() {
        val g = game()
        assertFailsWith<GameError> { g.mark("r1", "Engine Room 7", Verdict.CLEAR, "nope") }
    }

    @Test
    fun `retrying with the same request id does not spend the budget twice`() {
        val g = game()
        val first = g.message("same-id", "check the breaker")
        val again = g.message("same-id", "check the breaker")
        assertEquals(first, again)
        assertEquals(1, g.pilotView().notes.count { it.role == "ARCHIVIST" })
    }

    @Test
    fun `the same request id with different content is refused`() {
        val g = game()
        g.message("dup", "first line")
        assertFailsWith<GameError> { g.message("dup", "different line") }
    }

    @Test
    fun `messages are budgeted and validated`() {
        val g = game()
        assertFailsWith<GameError> { g.message("bad id with spaces", "hello") }
        assertFailsWith<GameError> { g.message("ok1", "") }
        assertFailsWith<GameError> { g.message("ok2", "x".repeat(501)) }
        repeat(14) { g.message("m$it", "line $it") }
        assertFailsWith<GameError> { g.message("m99", "one too many") }
    }

    /**
     * The end-to-end claim: a crew that plays legally can win. The archivist half uses only
     * the archive, the pilot half only walks and reports, and the answer falls out of putting
     * the two together. Nothing here reads the solution.
     */
    @Test
    fun `a competent crew solves the case using only what each seat may see`() {
        (1L..200L).forEach { seed ->
            now = 0L
            val g = Investigation(seed, Difficulty.VETERAN, clock = { now })

            // Archivist: read the archive. It knows where work was signed and who was rostered.
            val archive = g.archive("ALL")
            val worked = archive.filter { it.kind == RecordKind.WORK }.mapNotNull { it.site }
            val roster = archive.filter { it.kind == RecordKind.ROSTER }
                .mapNotNull { r -> r.crew?.let { it to r.site } }.toMap()
            assertTrue(
                worked.size <= Difficulty.VETERAN.inspections,
                "seed $seed signs off more jobs than the pilot can ever walk to"
            )

            // Pilot: walk only where the archivist pointed, and report what is there.
            worked.forEach { g.inspect(it); g.report(it) }

            // Together: the damning item is the one whose owner the roster puts somewhere else.
            val names = g.pilotView().sites.associate { it.name to it.id }
            val found = g.observe().reported.mapNotNull { finding ->
                val here = names.getValue(finding.compartment)
                val owner = g.case.crew.firstOrNull { finding.text.contains(it) }
                owner?.takeIf { roster[it] != here }?.let { here to it }
            }
            assertEquals(1, found.size, "seed $seed does not resolve to a single suspect")
            val (place, who) = found.single()
            assertTrue(g.accuse(place, who), "seed $seed is not solvable by playing it properly")
            assertEquals("SOLVED", g.pilotView().outcome)
        }
    }

    @Test
    fun `both seats see the same conversation`() {
        val g = game()
        g.say("the breaker is still open")
        g.message("r1", "then that is our fault")
        val pilot = g.pilotView().notes
        val archivist = g.observe().notes
        assertEquals(pilot, archivist)
        assertEquals(listOf("PILOT", "ARCHIVIST"), pilot.map { it.role })
    }
}
