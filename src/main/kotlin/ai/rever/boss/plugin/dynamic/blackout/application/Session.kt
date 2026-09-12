package ai.rever.boss.plugin.dynamic.blackout.application

import ai.rever.boss.plugin.dynamic.blackout.engine.Difficulty
import java.security.SecureRandom

/** One local pilot seat plus one archivist seat. The BOSS registry cannot identify callers. */
class Session {
    @Volatile var game: Investigation? = null
        private set

    @Volatile var disposed = false
        private set

    /** Set once by the plugin when a host AI gateway is available. Null in the standalone harness. */
    @Volatile var archivist: Archivist? = null

    @Synchronized fun start(difficulty: Difficulty = Difficulty.OPERATOR, seed: Long? = null): Investigation {
        check(!disposed)
        archivist?.reset()
        val chosen = seed ?: SecureRandom().nextLong()
        return Investigation(chosen, difficulty).also { game = it }
    }

    @Synchronized fun current(): Investigation {
        if (disposed) throw GameError("UNAVAILABLE", "Plugin is disabled.")
        return game ?: throw GameError("NO_CASE", "Ask the pilot to open a case in BLACKOUT.")
    }

    @Synchronized fun dispose() {
        disposed = true
        archivist?.stop()
        game?.interrupt()
    }
}
