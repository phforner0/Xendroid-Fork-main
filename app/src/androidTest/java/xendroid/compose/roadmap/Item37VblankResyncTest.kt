package xendroid.compose.roadmap

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.Utils

/**
 * Roadmap item 37 (K11, group C), the part a test can see: with the guest display refresh cap on
 * (the default), the game goes to Home for ~10 s and comes back; it goes on without racing to
 * "catch up", and xe.log says "Guest vblank resynced after a stall (1 so far, …)". The FPS of
 * the same scene against another build is for the phone (the numbers go to the results file).
 */
@RunWith(AndroidJUnit4::class)
class Item37VblankResyncTest {
    @Test fun aLongStallResyncsInsteadOfCatchingUp() {
        val game = GameSession.game()
        val log = File(Utils.get_log_file_path())
        val run = GameSession(game).use { session ->
            session.start()
            session.awaitFirstFrame()
            session.play(20)
            session.device.pressHome()
            SystemClock.sleep(10_000)
            session.start()
            session.play(20)
            session.exitByMenu()
            session.awaitRecord("the run to finish", 60_000) { it.state.final }
        }
        // Read after the exit: the log is complete once the game process ended.
        val resync = log.takeIf { it.isFile }?.readLines().orEmpty().lastOrNull { it.contains("Guest vblank resynced after a stall") }
        GameRun.note(37, "xe.log: ${resync ?: "no resync line"}")
        assertTrue("no \"Guest vblank resynced after a stall\" in xe.log", resync != null)
        run.performance?.let { perf ->
            GameRun.note(37, "${run.titleId}: FPS median ${perf.fpsPercentile(0.5)}, 5th percentile ${perf.fpsPercentile(0.05)}")
        }
    }
}
