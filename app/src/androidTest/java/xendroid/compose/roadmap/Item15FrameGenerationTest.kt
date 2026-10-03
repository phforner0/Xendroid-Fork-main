package xendroid.compose.roadmap

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.sessions.describeFrameGeneration
import xendroid.compose.settings.UiMode
import xendroid.compose.settings.UiModeStore

/**
 * Roadmap item 15 (F08, group C), a smoke run only: frame generation stays experimental, off by
 * default and debug-only (the test package is a debug build). Menu → Graphics → "Win-FG 2×" on:
 * the menu's state line reads "Win-FG: active · requested 2× · X ms GPU" (or "GPU timing
 * unavailable", or says why it stopped), never a frozen number; after a minute the run record
 * has the frame generation line when it was active. The line seen goes to the results file.
 *
 * Left for the phone: whether the picture is better or worse (visual), LSFG with the user's DLL,
 * the budget verdict against stutter, Forza as the negative control.
 */
@RunWith(AndroidJUnit4::class)
class Item15FrameGenerationTest {
    @Test fun winFgTurnsOnAndItsNumbersReachTheRun() {
        val game = GameSession.game()
        UiModeStore.write(Device.context, UiMode.DEVELOPER)
        val run = GameSession(game).use { session ->
            session.start()
            session.awaitFirstFrame()
            session.openMenu()
            session.page(R.string.menu_tab_graphics)
            session.find(session.text(R.string.menu_winfg, session.text(R.string.menu_off))).click()
            SystemClock.sleep(5_000)
            val first = session.device.wait(Until.findObject(By.textStartsWith("Win-FG:")), 10_000)?.text
            SystemClock.sleep(3_000)
            val second = session.device.findObject(By.textStartsWith("Win-FG:"))?.text
            GameRun.note(15, "menu state: \"$first\" then \"$second\"")
            assertNotNull("no Win-FG state line in the menu", first)
            if (first!!.contains(" ms GPU") && second?.contains(" ms GPU") == true) {
                assertTrue("the GPU time did not move: $first", first != second || first.contains("unavailable"))
            }
            session.closeMenu()
            session.play(60)
            session.exitByMenu()
            session.awaitRecord("the run to finish", 60_000) { it.state.final }
        }
        val line = run.performance?.let(::describeFrameGeneration)
        GameRun.note(15, "run: ${line ?: "no frame generation line (it never became active)"}")
    }
}
