package ai.rever.boss.plugin.dynamic.blackout.application

import ai.rever.boss.plugin.dynamic.blackout.engine.Case
import ai.rever.boss.plugin.dynamic.blackout.engine.CaseFile
import ai.rever.boss.plugin.dynamic.blackout.engine.Difficulty
import ai.rever.boss.plugin.dynamic.blackout.engine.Record
import kotlinx.serialization.Serializable
import java.util.UUID

class GameError(val code: String, override val message: String) : IllegalArgumentException(message)

@Serializable
data class Note(val role: String, val text: String)

@Serializable
data class Ack(val caseId: String, val revision: Long, val detail: String)

/** The archivist's verdict on one compartment, drawn on the pilot's map. */
@Serializable
enum class Verdict { SUSPECT, CLEAR }

@Serializable
data class Mark(val site: String, val verdict: Verdict, val reason: String)

/** A compartment as the pilot's map shows it. Findings appear only once it has been reached. */
@Serializable
data class SiteView(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val inspected: Boolean,
    val physical: String? = null,
    val trace: String? = null,
    val mark: Mark? = null
)

@Serializable
data class PilotView(
    val caseId: String,
    val revision: Long,
    val brief: String,
    val sites: List<SiteView>,
    val crew: List<String>,
    val inspectionsLeft: Int,
    val secondsLeft: Int,
    val outcome: String,
    val attemptsLeft: Int,
    val notes: List<Note>,
    val debrief: String? = null
)

/**
 * What the archivist is allowed to know. Compartment names, because it has to be able to
 * name one, and whatever the pilot has chosen to report. Never a physical state the pilot
 * has not passed on, and never the map.
 */
@Serializable
data class ArchivistView(
    val caseId: String,
    val revision: Long,
    val brief: String,
    val compartments: List<String>,
    val crew: List<String>,
    val reported: List<Finding>,
    val notes: List<Note>,
    val marks: List<Mark>,
    val inspectionsLeft: Int,
    val secondsLeft: Int,
    val outcome: String,
    val archiveQueriesLeft: Int,
    val messagesLeft: Int
)

/** Something the pilot saw and chose to pass on. This is the only channel into the agent. */
@Serializable
data class Finding(val compartment: String, val text: String)

@Serializable
data class Debrief(
    val caseId: String,
    val seed: Long,
    val outcome: String,
    val faultCompartment: String,
    val culprit: String,
    val explanation: String,
    val secondsUsed: Int,
    val inspectionsUsed: Int
)

/**
 * One incident, one pilot, one archivist. Every boundary that an agent can reach goes
 * through here, and the private half of the case never leaves through an archivist view.
 */
class Investigation(
    seed: Long,
    val difficulty: Difficulty = Difficulty.OPERATOR,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    val id: String = UUID.randomUUID().toString()
) {
    val case: Case = CaseFile.generate(seed)

    private var revision = 0L
    private var deadline = clock() + difficulty.seconds * 1000L
    private var pausedAt: Long? = null
    private var outcome = "IN_PROGRESS"
    private val inspected = linkedSetOf<String>()
    private val reported = mutableListOf<Finding>()
    private val notes = mutableListOf<Note>()
    private val marks = linkedMapOf<String, Mark>()
    private var attempts = 0
    private var archiveQueries = 0
    private var messages = 0
    private var lastObserve = Long.MIN_VALUE
    private val requests = mutableMapOf<String, Pair<String, Ack>>()

    // ---- pilot side -------------------------------------------------------------------

    @Synchronized fun pilotView(): PilotView {
        tick()
        return PilotView(
            caseId = id,
            revision = revision,
            brief = CaseFile.BRIEF,
            sites = case.sites.map { site ->
                val seen = site.id in inspected
                SiteView(
                    site.id, site.name, site.x, site.y, seen,
                    if (seen) site.physical else null,
                    if (seen) site.trace else null,
                    marks[site.id]
                )
            },
            crew = case.crew,
            inspectionsLeft = difficulty.inspections - inspected.size,
            secondsLeft = secondsLeft(),
            outcome = outcome,
            attemptsLeft = MAX_ATTEMPTS - attempts,
            notes = notes.toList(),
            debrief = if (finished()) case.debrief else null
        )
    }

    /** Walk to a compartment and look at it. This is the scarce resource. */
    @Synchronized fun inspect(siteId: String) {
        tick(); active()
        if (siteId in inspected) return
        if (inspected.size >= difficulty.inspections) fail("NO_INSPECTIONS", "No reachable compartments left.")
        val site = case.site(siteId) ?: fail("UNKNOWN_SITE", "No such compartment.")
        inspected += site.id
        revision++
    }

    /** Pass a finding to the archivist. Nothing reaches the agent until the pilot sends it. */
    @Synchronized fun report(siteId: String) {
        tick(); active()
        val site = case.site(siteId) ?: fail("UNKNOWN_SITE", "No such compartment.")
        if (site.id !in inspected) fail("NOT_INSPECTED", "Reach the compartment before reporting it.")
        val body = buildString {
            append(site.physical)
            site.trace?.let { append(" Also lying there: ").append(it).append(".") }
        }
        if (reported.none { it.compartment == site.name }) {
            reported += Finding(site.name, body)
            notes += Note("PILOT", site.name + ": " + body)
            revision++
        }
    }

    @Synchronized fun say(text: String) {
        tick(); active(); validate(text)
        notes += Note("PILOT", text)
        revision++
    }

    /** The pilot's one call. Two attempts; the first miss costs a minute of battery. */
    @Synchronized fun accuse(siteId: String, culprit: String): Boolean {
        tick(); active()
        val site = case.site(siteId) ?: fail("UNKNOWN_SITE", "No such compartment.")
        if (culprit !in case.crew) fail("UNKNOWN_CREW", "No such crew member.")
        attempts++
        val right = site.id == case.faultSite && culprit == case.culprit
        if (right) {
            outcome = "SOLVED"
        } else if (attempts >= MAX_ATTEMPTS) {
            outcome = "FAILED"
        } else {
            deadline -= WRONG_CALL_PENALTY_MS
            notes += Note("SYSTEM", "That call was wrong. A minute of battery is gone.")
        }
        revision++
        return right
    }

    // ---- archivist side ---------------------------------------------------------------

    @Synchronized fun observe(): ArchivistView {
        tick()
        val now = clock()
        if (lastObserve != Long.MIN_VALUE && now - lastObserve < 1000) fail("RATE_LIMIT", "Observe at most once per second.")
        lastObserve = now
        return ArchivistView(
            caseId = id,
            revision = revision,
            brief = CaseFile.ARCHIVIST_BRIEF,
            compartments = case.sites.map { it.name },
            crew = case.crew,
            reported = reported.toList(),
            notes = notes.toList(),
            marks = marks.values.toList(),
            inspectionsLeft = difficulty.inspections - inspected.size,
            secondsLeft = secondsLeft(),
            outcome = outcome,
            archiveQueriesLeft = MAX_ARCHIVE_QUERIES - archiveQueries,
            messagesLeft = MAX_MESSAGES - messages
        )
    }

    /**
     * Search the archive. "ALL" returns everything, because the archive by itself can never
     * decide a case: it is read-only and carries a budget only to stop a runaway loop.
     */
    @Synchronized fun archive(query: String): List<Record> {
        tick(); active()
        if (query.isBlank() || query.length > 120) fail("INVALID_QUERY", "Send 1-120 characters.")
        if (archiveQueries >= MAX_ARCHIVE_QUERIES) fail("BUDGET_EXHAUSTED", "Archive query budget is spent.")
        archiveQueries++
        revision++
        val needle = query.trim().lowercase()
        if (needle == "all" || needle == "*") return case.records
        return case.records.filter { record ->
            val site = record.site?.let { id -> case.site(id)?.name }.orEmpty()
            listOf(record.text, record.kind.name, record.time, site, record.crew.orEmpty())
                .any { it.lowercase().contains(needle) }
        }
    }

    @Synchronized fun message(requestId: String, text: String): Ack = mutate(requestId, "message:" + text) {
        validate(text)
        if (messages >= MAX_MESSAGES) fail("BUDGET_EXHAUSTED", "Message budget is spent.")
        messages++
        notes += Note("ARCHIVIST", text)
        "Delivered to the pilot"
    }

    /** Draw a verdict on the pilot's map. The agent's reasoning, made visible. */
    @Synchronized fun mark(requestId: String, compartment: String, verdict: Verdict, reason: String): Ack =
        mutate(requestId, "mark:" + compartment + verdict) {
            validate(reason)
            val site = case.sites.firstOrNull { it.name.equals(compartment.trim(), ignoreCase = true) }
                ?: fail("UNKNOWN_SITE", "No compartment called that. Use the names from observe.")
            marks[site.id] = Mark(site.id, verdict, reason.trim())
            site.name + " marked " + verdict
        }

    // ---- lifecycle ---------------------------------------------------------------------

    @Synchronized fun tick() {
        if (outcome == "IN_PROGRESS" && pausedAt == null && clock() >= deadline) {
            outcome = "FAILED"
            notes += Note("SYSTEM", "The batteries are dead.")
            revision++
        }
    }

    @Synchronized fun pause() {
        if (outcome == "IN_PROGRESS" && pausedAt == null) { pausedAt = clock(); revision++ }
    }

    @Synchronized fun resume() {
        pausedAt?.let { deadline += clock() - it; pausedAt = null; revision++ }
    }

    @Synchronized fun interrupt() {
        if (outcome == "IN_PROGRESS") { outcome = "INTERRUPTED"; revision++ }
    }

    @Synchronized fun finished(): Boolean = outcome != "IN_PROGRESS"

    @Synchronized fun debrief(): Debrief = Debrief(
        caseId = id,
        seed = case.seed,
        outcome = outcome,
        faultCompartment = case.site(case.faultSite)?.name.orEmpty(),
        culprit = case.culprit,
        explanation = case.debrief,
        secondsUsed = difficulty.seconds - secondsLeft(),
        inspectionsUsed = inspected.size
    )

    // ---- internals ----------------------------------------------------------------------

    private fun secondsLeft(): Int {
        if (finished()) return 0
        val remaining = deadline - (pausedAt ?: clock())
        return ((remaining.coerceAtLeast(0) + 999) / 1000).toInt()
    }

    /**
     * Retrying a call with the same request ID returns the first answer instead of spending
     * the budget twice. A model that loses a response must be able to ask again safely.
     */
    private fun mutate(requestId: String, payload: String, change: () -> String): Ack {
        if (!requestId.matches(REQUEST_ID)) fail("INVALID_REQUEST_ID", "Use 1-64 letters, digits, _ or -.")
        requests[requestId]?.let {
            if (it.first != payload) fail("REQUEST_CONFLICT", "That request ID was used with different content.")
            return it.second
        }
        tick(); active()
        val detail = change()
        revision++
        val ack = Ack(id, revision, detail)
        requests[requestId] = payload to ack
        return ack
    }

    private fun validate(text: String) {
        if (text.isBlank() || text.length > 500 || text.any { it.isISOControl() && it != '\n' })
            fail("INVALID_TEXT", "Send 1-500 characters of plain text without control characters.")
    }

    private fun active() { if (finished()) fail("CASE_CLOSED", "This case is closed.") }

    private fun fail(code: String, message: String): Nothing = throw GameError(code, message)

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val MAX_ARCHIVE_QUERIES = 10
        const val MAX_MESSAGES = 14
        const val WRONG_CALL_PENALTY_MS = 60_000L
        val REQUEST_ID = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
