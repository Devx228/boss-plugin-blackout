package ai.rever.boss.plugin.dynamic.blackout.application

import ai.rever.boss.plugin.api.PluginStorageProvider
import ai.rever.boss.plugin.dynamic.blackout.engine.Difficulty
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local crew record. Competitive play would need a server; this is a practice logbook. */
@Serializable
data class Profile(
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val rounds: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val clearedVeteran: Boolean = false
) {
    val played: Int get() = wins + losses + draws

    fun record(outcome: String, roundsPlayed: Int, difficulty: Difficulty, practice: Boolean): Profile {
        if (practice || outcome == "INTERRUPTED" || outcome == "IN_PROGRESS") return this
        val won = outcome == "BLUE"
        val next = if (won) streak + 1 else 0
        return copy(
            wins = wins + if (won) 1 else 0,
            losses = losses + if (outcome == "ORANGE") 1 else 0,
            draws = draws + if (outcome == "DRAW") 1 else 0,
            rounds = rounds + roundsPlayed,
            streak = next,
            bestStreak = maxOf(bestStreak, next),
            clearedVeteran = clearedVeteran || (won && difficulty == Difficulty.VETERAN)
        )
    }

    fun headline(): String = when {
        played == 0 -> "No ranked duels logged yet."
        else -> "$wins won / $losses lost / $draws drawn over $played duels. Best streak $bestStreak."
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

    suspend fun saveReplay(matchId: String, replayJson: String): Boolean = guard(false) {
        storage?.putJson("replay.$matchId", replayJson) ?: return@guard false
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
