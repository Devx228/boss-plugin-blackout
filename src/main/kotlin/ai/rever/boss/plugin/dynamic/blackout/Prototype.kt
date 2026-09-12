package ai.rever.boss.plugin.dynamic.blackout

import ai.rever.boss.plugin.dynamic.blackout.application.Session
import ai.rever.boss.plugin.dynamic.blackout.engine.Difficulty
import ai.rever.boss.plugin.dynamic.blackout.ui.BlackoutBoard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay

/**
 * Standalone Compose harness. No BOSS server, no host gateway and no stand-in agent, so the
 * archivist's seat stays empty here: the board renders and the pilot half is driveable.
 *
 * Pass -Dblackout.smokeSeconds=N to open the window, open a case, and close again. That
 * turns "does the board actually render" into a pass or fail rather than a screenshot
 * someone has to look at.
 */
fun main() = application {
    val session = remember { Session() }
    val smoke = System.getProperty("blackout.smokeSeconds")?.toIntOrNull()

    Window(onCloseRequest = { session.dispose(); exitApplication() }, title = "BLACKOUT - UI harness") {
        BlackoutBoard(session, null)
        if (smoke != null) {
            LaunchedEffect(Unit) {
                // Render the opening screen, then a live case, then leave.
                delay(1_500)
                session.start(Difficulty.OPERATOR)
                delay(smoke * 1_000L)
                println("BLACKOUT smoke test: board rendered, case ${session.game?.id} opened, closing.")
                session.dispose()
                exitApplication()
            }
        }
    }
}
