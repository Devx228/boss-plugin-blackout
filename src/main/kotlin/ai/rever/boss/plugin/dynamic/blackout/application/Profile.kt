package ai.rever.boss.plugin.dynamic.blackout.application

import ai.rever.boss.plugin.api.PluginStorageProvider
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local crew record. A practice logbook, not a ranking service. */
@Serializable
data class Profile(
    val solved: Int = 0,
    val failed: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val fastestSeconds: Int = 0
) {
    val played: Int get() = solved + failed

    fun record(outcome: String, secondsUsed: Int): Profile {
        if (outcome != "SOLVED" && outcome != "FAILED") return this
        val won = outcome == "SOLVED"
        val next = if (won) streak + 1 else 0
        return copy(
            solved = solved + if (won) 1 else 0,
            failed = failed + if (won) 0 else 1,
            streak = next,
            bestStreak = maxOf(bestStreak, next),
            fastestSeconds = when {
                !won -> fastestSeconds
                fastestSeconds == 0 -> secondsUsed
                else -> minOf(fastestSeconds, secondsUsed)
            }
        )
    }

    fun headline(): String = when {
        played == 0 -> "No cases closed yet."
        fastestSeconds > 0 -> "$solved solved, $failed lost. Best streak $bestStreak, fastest ${fastestSeconds / 60}m ${fastestSeconds % 60}s."
        else -> "$solved solved, $failed lost. Best streak $bestStreak."
    }
}

/** Reads and writes the logbook through plugin storage, tolerating an absent or broken store. */
class ProfileStore(private val storage: PluginStorageProvider?) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): Profile = guard(Profile()) {
        storage?.getJson(KEY)?.takeIf { it.isNotBlank() }?.let { json.decodeFromString<Profile>(it) } ?: Profile()
    }

    suspend fun save(profile: Profile): Boolean = guard(false) {
        storage?.putJson(KEY, json.encodeToString(profile)) ?: return@guard false
        true
    }

    suspend fun saveDebrief(caseId: String, debriefJson: String): Boolean = guard(false) {
        storage?.putJson("case.$caseId", debriefJson) ?: return@guard false
        true
    }

    private suspend fun <T> guard(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        fallback
    }

    private companion object { const val KEY = "profile" }
}
