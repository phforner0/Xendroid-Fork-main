package xendroid.compose.roadmap

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.sessions.RunState

/**
 * Roadmap item 5 (group C): with the game in the background, the system font size and display
 * size change (as in Settings → Display); back in the game, it is the same game process (no
 * restart of :emu) and the run is still running. The phone's own values come back afterwards.
 */
@RunWith(AndroidJUnit4::class)
class Item05BackgroundTest {
    @Test fun theGameSurvivesFontAndDisplaySizeChanges() {
        val game = GameSession.game()
        val fontScale = Device.shell("settings get system font_scale").trim()
        val density = Device.shell("wm density")
        val physical = Regex("Physical density: (\\d+)").find(density)?.groupValues?.get(1)?.toInt()
        val override = Regex("Override density: (\\d+)").find(density)?.groupValues?.get(1)
        GameSession(game).use { session ->
            session.start()
            session.awaitRunning()
            session.awaitFirstFrame()
            val pid = session.emuPid()
            session.device.pressHome()
            SystemClock.sleep(2_000)
            try {
                Device.shell("settings put system font_scale 1.3")
                physical?.let { Device.shell("wm density ${it * 11 / 10}") }
                SystemClock.sleep(4_000)
            } finally {
                if (fontScale == "null" || fontScale.isEmpty()) Device.shell("settings delete system font_scale")
                else Device.shell("settings put system font_scale $fontScale")
                if (override != null) Device.shell("wm density $override") else Device.shell("wm density reset")
            }
            SystemClock.sleep(3_000)
            session.start()
            session.play(8)
            assertEquals("the same game process", pid, session.emuPid())
            assertEquals(RunState.RUNNING, session.record()!!.state)
            session.exitByMenu()
        }
    }
}
