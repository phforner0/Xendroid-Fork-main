package xendroid.compose.roadmap

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.core.StorageAccess

/**
 * Roadmap item 2 (group C): a game started while a save/content job (an automatic backup) holds
 * the storage lease. A short job is waited for and the game boots normally; one that holds it
 * past the wait gets the dialog saying why ("Another save, profile or content operation is still
 * using the game data…") and nothing boots.
 *
 * Left for the phone: the real automatic backup racing a launch, the "Waiting for a save…" toast
 * (toasts are not readable by the test).
 */
@RunWith(AndroidJUnit4::class)
class Item02LeaseTest {
    @Test fun aShortJobIsWaitedForAndALongOneIsExplained() {
        val game = GameSession.game()
        GameSession(game).use { session ->
            val job = StorageAccess.acquire()
            try {
                session.start()
                SystemClock.sleep(4_000)
            } finally {
                job.close()
            }
            session.awaitRunning()
            session.exitByMenu()
        }

        GameSession(game).use { session ->
            StorageAccess.acquire().use {
                session.start()
                session.find(session.text(R.string.host_launch_failed), timeoutMs = 90_000)
                assertTrue(session.has(session.text(R.string.host_storage_busy)))
                session.device.findObject(By.res("android:id/button1")).click()
                Device.waitUntil("the game process to end", 30_000) { session.emuPid().isEmpty() }
            }
            assertNull("no run began without the storage", session.record())
        }
    }
}
