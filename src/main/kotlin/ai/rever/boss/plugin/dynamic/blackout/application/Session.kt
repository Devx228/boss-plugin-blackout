package ai.rever.boss.plugin.dynamic.blackout.application

import java.security.SecureRandom

/** One local human + companion seat. The BOSS registry cannot identify individual callers. */
class Session {
    @Volatile var escape: Escape? = null
        private set

    @Synchronized fun startEscape(seed: Long? = null): Escape {
        check(!disposed)
        partner?.reset()
        escape?.interrupt()
        return Escape(seed ?: SecureRandom().nextLong()).also { escape = it }
    }

    @Synchronized fun currentEscape(): Escape {
        if (disposed) throw GameError("UNAVAILABLE", "Plugin is disabled.")
        return escape ?: throw GameError("NO_ROOM", "Ask the human to enter the room in BLACKOUT.")
    }
    @Volatile var disposed = false
        private set

    /** Set once by the plugin when a host AI gateway is available. Null in the standalone harness. */
    @Volatile var partner: Companion? = null

    @Synchronized fun dispose() {
        disposed = true
        partner?.stop()
        escape?.interrupt()
    }
}
