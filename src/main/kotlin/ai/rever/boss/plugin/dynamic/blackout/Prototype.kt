package ai.rever.boss.plugin.dynamic.blackout

import ai.rever.boss.plugin.dynamic.blackout.application.Session
import ai.rever.boss.plugin.dynamic.blackout.ui.BlackoutBoard
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/** Optional standalone UI harness; no BOSS server or fake agent is started. */
fun main() = application {
    val session = androidx.compose.runtime.remember { Session() }
    Window(onCloseRequest = { session.dispose(); exitApplication() }, title = "BLACKOUT • UI harness") {
        BlackoutBoard(session, null)
    }
}
