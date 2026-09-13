package ai.rever.boss.plugin.dynamic.blackout

import ai.rever.boss.plugin.dynamic.blackout.application.Session
import ai.rever.boss.plugin.dynamic.blackout.ui.EscapeBoard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
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

    Window(onCloseRequest = { session.dispose(); exitApplication() }, title = "BLACKOUT - UI harness",
        state = rememberWindowState(width = 1120.dp, height = 900.dp)) {
        EscapeBoard(session, null)
        if (smoke != null) {
            LaunchedEffect(Unit) {
                // Render the opening screen, then a live case, then leave.
                delay(1_500)
                session.startEscape(seed = 42)
                delay(smoke * 1_000L)
                System.getProperty("blackout.capturePath")?.let { destination ->
                    window.toFront()
                    window.requestFocus()
                    delay(700)
                    check(window.isFocused) { "Test window is obscured; refusing to capture another application." }
                    // Capture only this explicitly launched test window, never the user's desktop.
                    val location = window.locationOnScreen
                    val bounds = java.awt.Rectangle(location.x, location.y, window.width, window.height)
                    val screenshot = java.awt.Robot().createScreenCapture(bounds)
                    javax.imageio.ImageIO.write(screenshot, "png", java.io.File(destination))
                }
                println("BLACKOUT smoke test: escape board rendered, room ${session.escape?.id} opened, closing.")
                session.dispose()
                exitApplication()
            }
        }
    }
}
