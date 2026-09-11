package ai.rever.boss.plugin.dynamic.blackout.application

import ai.rever.boss.plugin.dynamic.blackout.engine.Difficulty
import java.security.SecureRandom

/** One local human + real-agent seat. The shared BOSS registry cannot identify callers. */
class Session {
    @Volatile var match: Match? = null
        private set
    @Volatile var disposed = false
        private set

    /** Set once by the plugin when a host AI gateway is available. Null in the standalone harness. */
    @Volatile var crew: EngineerCrew? = null

    @Synchronized fun start(tutorial: Boolean = false, difficulty: Difficulty = Difficulty.OPERATOR): Match {
        check(!disposed)
        check(match == null || match!!.publicView().outcome != "IN_PROGRESS") { "Finish or abandon the active match first." }
        crew?.reset()
        return Match(SecureRandom().nextLong(), tutorial = tutorial, difficulty = difficulty).also { match = it }
    }
    @Synchronized fun current(): Match {
        if (disposed) throw GameError("UNAVAILABLE", "Plugin is disabled.")
        return match ?: throw GameError("NO_MATCH", "Ask the human to start a local duel in BLACKOUT.")
    }
    @Synchronized fun dispose() { disposed = true; crew?.stop(); match?.interrupt() }
}
