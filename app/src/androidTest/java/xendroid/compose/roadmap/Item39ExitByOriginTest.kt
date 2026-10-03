package xendroid.compose.roadmap

import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.MainActivity
import xendroid.compose.R
import xendroid.compose.sessions.RunState
import xendroid.compose.ui.library.ACTION_LAUNCH_GAME
import xendroid.compose.ui.library.EXTRA_GAME_URI

/**
 * Roadmap item 39 (U09, group C). Started from the library, Exit returns to the library. Started
 * from outside the app (the shell's `am start` with what ES-DE and Daijisho send: the component,
 * the action and `game_uri`, in its own task), "Cancel" on the loading label returns to where it
 * was — never to an empty XenDroid screen — and the run, external, says it was cancelled while
 * starting.
 *
 * Left for the phone: a real frontend (ES-DE/Daijisho) with the library open behind, and what
 * Recents shows.
 */
@RunWith(AndroidJUnit4::class)
class Item39ExitByOriginTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val pkg = Device.context.packageName

    @Test fun fromTheLibraryExitReturnsToTheLibrary() {
        val game = GameSession.game()
        Library(listOf(game.parentFile!!)).use { library ->
            val shown = library.viewModel().rescan("the game's folder").games.first { it.launchUri == game.path }
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(60_000) { compose.onAllNodesWithText(shown.name).fetchSemanticsNodes().isNotEmpty() }
                GameSession(game).use { session ->
                    compose.onAllNodesWithText(shown.name).onFirst().performClick()
                    // One profile here: no "Play as" question.
                    val run = session.awaitRunning()
                    assertEquals("library", run.launchSource)
                    session.awaitFirstFrame()
                    session.exitByMenu()
                    assertTrue("the library is not back",
                        session.device.wait(Until.hasObject(By.pkg(pkg).text(Device.string(R.string.lib_title))), 15_000) == true)
                }
            }
        }
    }

    @Test fun cancelWhileStartingFromOutsideGoesBack() {
        val game = GameSession.game()
        // Without the pipeline cache the loading lasts long enough to press Cancel.
        listOf("cache", "cache0", "cache1").forEach { java.io.File(Device.storageRoot, it).deleteRecursively() }
        GameSession(game).use { session ->
            val home = session.device.launcherPackageName
            session.device.pressHome()
            assertTrue("the launcher is not in front",
                session.device.wait(Until.hasObject(By.pkg(home).depth(0)), 10_000) == true)
            Device.grantAllFilesAccess()
            // As a frontend starts it (docs/frontend-integration.md): the component, the action
            // and the game_uri extra, from another app (the shell). A file:// Uri because the
            // shell splits the command on spaces and a game's name may have them.
            val started = Device.shell("am start -n $pkg/xendroid.compose.EmulatorHostActivity -a $ACTION_LAUNCH_GAME " +
                "--es $EXTRA_GAME_URI ${Uri.fromFile(game)}")
            // Cancel is on the label from the first moment; the run record begins once the game
            // data is ready, so wait for it as a player waits a little before giving up.
            runCatching { session.awaitRecord("the run to begin", 120_000) { true } }
                .onFailure { throw AssertionError("the game did not start (am start: ${started.trim()})", it) }
            session.find(session.text(R.string.common_cancel), timeoutMs = 60_000).click()
            Device.waitUntil("the game process to end", 30_000) { session.emuPid().isEmpty() }
            assertTrue("not back where it was ($home) but in ${session.device.currentPackageName}",
                session.device.wait(Until.hasObject(By.pkg(home).depth(0)), 15_000) == true)
            val run = session.awaitRecord("the cancelled run", 30_000) { it.state.final }
            assertTrue(run.launchSource, run.launchSource.startsWith("external"))
            assertEquals(RunState.ENDED, run.state)
            assertEquals("cancelled while starting", run.endReason)
        }
    }
}
