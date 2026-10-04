package xendroid.compose.shots

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File

/** Phone sizes of the prints: a 20:9 phone in landscape and portrait (dp), at 1.5x, dark mode. */
object Phone {
    const val LAND = "w914dp-h411dp-land-night-hdpi"
    const val PORT = "w411dp-h914dp-port-night-hdpi"
}

/**
 * Renders what is on screen and, with `-Pscreenshots=<dir>`, writes it as `<dir>/<name>.png`.
 * Without the property the test still composes, lays out and draws the screen (a smoke test).
 */
@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.shot(name: String) {
    waitForIdle()
    val dir = System.getProperty("xendroid.screenshots")
    if (dir.isNullOrBlank()) {
        onRoot().assertExists()
        return
    }
    val file = File(dir, "$name.png").apply { parentFile?.mkdirs() }
    onRoot().captureRoboImage(file.absolutePath, RoborazziOptions(taskType = RoborazziTaskType.Record))
}
