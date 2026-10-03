package xendroid.compose.roadmap

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.MainActivity
import xendroid.compose.R
import xendroid.compose.sessions.RunState

/**
 * Roadmap item 39 (U09, group C). Started from the library, Exit returns to the library. Started
 * from outside the app (the shell's `am start`, as a frontend or a shortcut does: its own task,
 * ACTION_VIEW), "Cancel" on the loading label returns to where it was — never to an empty
 * XenDroid screen — and the run says it was cancelled while starting.
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
            session.device.pressHome()
            val home = session.device.currentPackageName
            Device.grantAllFilesAccess()
            // A file:// Uri: the shell splits on spaces, a game's name may have them.
            Device.shell("am start -a android.intent.action.VIEW -n $pkg/xendroid.compose.EmulatorHostActivity -d ${android.net.Uri.fromFile(game)}")
            session.find(session.text(R.string.common_cancel), timeoutMs = 120_000).click()
            Device.waitUntil("the game process to end", 30_000) { session.emuPid().isEmpty() }
            val now = session.device.currentPackageName
            assertNotEquals("an empty XenDroid screen is left", pkg, now)
            assertEquals(home, now)
            val run = session.awaitRecord("the cancelled run", 30_000) { it.state.final }
            assertEquals(RunState.ENDED, run.state)
            assertEquals("cancelled while starting", run.endReason)
        }
    }
}
