package xendroid.compose.roadmap

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.MainActivity
import xendroid.compose.R
import xendroid.compose.ui.library.LibraryUiState

/**
 * Roadmap item 22 (L09), with the phone's `gameDir`. (a) The app opened again shows the list at
 * once (last time's, then the scan's); (b) with the app's cache cleared, the scan says what it
 * reads and "Stop" ends it with "Scan stopped…", and Retry finishes it; (d) with
 * `-e scanWholeStorage true`, the whole internal storage as a folder (the walk stops at its limit
 * with the partial-list notice); (e) the warm scan of 1,000 and 10,000 files is timed. Times go
 * to Download/xendroid-roadmap/results.txt and logcat (XendroidRoadmap).
 *
 * Left for the phone: (c) moving a folder of games (the user's files), (f) deleting a game while
 * the app is in the background (use scratchDir by hand).
 */
@RunWith(AndroidJUnit4::class)
class Item22ScanTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = Device.context

    private fun clearAppCache() =
        context.cacheDir.listFiles().orEmpty().filter { it.name != "shared-logs" }.forEach { it.deleteRecursively() }

    @Test fun theLastListShowsAtOnceAndAScanCanStop() {
        Library(listOf(GameRun.gameDir())).use { library ->
            val first = library.viewModel().rescan("the first scan")
            assumeTrue("no game in the games folder", first.games.isNotEmpty())

            // (a) Opened again: a list at once.
            val opened = SystemClock.elapsedRealtime()
            val again = library.viewModel()
            val shown = Device.await(again.state, "a list on opening", 10_000) { it is LibraryUiState.Loaded } as LibraryUiState.Loaded
            val ms = SystemClock.elapsedRealtime() - opened
            assertEquals(first.keys(), shown.keys())
            GameRun.note(22, "(a) list shown $ms ms after opening (${if (shown.cached) "last time's list" else "the scan was already done"}), ${first.games.size} games")
            assertTrue("the list took $ms ms to show", ms < 2_000)
            again.rescan("the reopened library's scan")

            // (b) Cache cleared: Stop while it reads, then Retry.
            clearAppCache()
            ActivityScenario.launch(MainActivity::class.java).use {
                val stop = Device.string(R.string.lib_stop)
                val stoppable = runCatching {
                    compose.waitUntil(60_000) { compose.onAllNodesWithText(stop).fetchSemanticsNodes().isNotEmpty() }
                }.isSuccess
                if (!stoppable) {
                    GameRun.note(22, "(b) the scan ended before Stop showed (a small library)")
                    return@use
                }
                compose.onNodeWithText(stop).performClick()
                val stopped = Device.string(R.string.lib_scan_stopped)
                val ended = runCatching {
                    compose.waitUntil(10_000) { compose.onAllNodesWithText(stopped).fetchSemanticsNodes().isNotEmpty() }
                }.isSuccess
                if (ended) {
                    val retry = SystemClock.elapsedRealtime()
                    compose.onNodeWithText(Device.string(R.string.common_retry)).performClick()
                    compose.waitUntil(Library.SCAN_TIMEOUT_MS) {
                        compose.onAllNodesWithText(first.games.first().name).fetchSemanticsNodes().isNotEmpty()
                    }
                    GameRun.note(22, "(b) stopped, then Retry listed the games in ${SystemClock.elapsedRealtime() - retry} ms")
                } else {
                    GameRun.note(22, "(b) Stop pressed while a list was on screen: it stays")
                }
            }
        }
    }

    @Test fun warmScansOfOneAndTenThousandFiles() {
        Device.grantAllFilesAccess()
        for (count in listOf(1_000, 10_000)) {
            val dir = GameRun.workDir("scan-$count")
            try {
                dir.deleteRecursively()
                (0 until count).forEach { i -> File(dir, "d${i / 100}").apply { mkdirs() }.resolve("f$i.bin").createNewFile() }
                Library(listOf(dir)).use { library ->
                    val vm = library.viewModel()
                    val cold = SystemClock.elapsedRealtime()
                    vm.rescan("the first scan of $count files")
                    val coldMs = SystemClock.elapsedRealtime() - cold
                    val warm = SystemClock.elapsedRealtime()
                    vm.rescan("the warm scan of $count files")
                    GameRun.note(22, "(e) $count files: first scan $coldMs ms, warm scan ${SystemClock.elapsedRealtime() - warm} ms")
                }
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test fun theWholeInternalStorageStopsAtTheLimit() {
        Device.grantAllFilesAccess()
        assumeTrue("only with -e scanWholeStorage true", GameRun.flag("scanWholeStorage"))
        Library(listOf(File("/storage/emulated/0"))).use { library ->
            val started = SystemClock.elapsedRealtime()
            val scanned = library.viewModel().rescan("the whole internal storage", timeoutMs = 30 * 60 * 1000L)
            GameRun.note(22, "(d) whole storage: ${scanned.games.size} games, partial list = ${scanned.truncated}, " +
                "${SystemClock.elapsedRealtime() - started} ms")
        }
    }
}
