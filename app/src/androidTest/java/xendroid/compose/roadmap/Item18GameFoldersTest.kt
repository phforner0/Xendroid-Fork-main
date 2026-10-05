package xendroid.compose.roadmap

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

/**
 * Roadmap item 18 (L03): several game folders, with the phone's `gameDir`. The same folder by
 * another path (/sdcard ↔ /storage/emulated/0) or a folder inside it lists nothing twice; an
 * empty folder adds nothing; a folder that goes away (here: an empty test folder deleted) shows
 * "1 game folder is not available now…" while the other games stay, and comes back when it is
 * there again; the folders dialog marks the first one "installs go here" and the missing one
 * "not available now"; removing the games folder takes its games off the list and leaves every
 * file where it was.
 *
 * Left for the phone: a real SD card taken out and put back (and its speed), revoking access.
 */
@RunWith(AndroidJUnit4::class)
class Item18GameFoldersTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(60_000) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    /** The same folder through the other name Android gives shared storage. */
    private fun aliasOf(dir: File): String? = when {
        dir.path.startsWith("/storage/emulated/0/") -> "/sdcard/" + dir.path.removePrefix("/storage/emulated/0/")
        dir.path.startsWith("/sdcard/") -> "/storage/emulated/0/" + dir.path.removePrefix("/sdcard/")
        else -> null
    }

    @Test fun foldersAddUpWithoutRepeatsAndOneAwayKeepsTheRest() {
        val games = GameRun.gameDir()
        val empty = GameRun.workDir("empty-folder").apply { deleteRecursively(); mkdirs() }
        try {
            Library(listOf(games)).use { library ->
                val vm = library.viewModel()
                val first = vm.rescan("the first scan of $games")
                assumeTrue("$games has no game the library recognizes", first.games.isNotEmpty())
                val keys = first.keys()

                // The same folder by another path, and a folder inside it: nothing twice.
                val overlapping = listOfNotNull(aliasOf(games), games.listFiles()?.firstOrNull { it.isDirectory && !it.isHidden }?.path)
                overlapping.forEach(library::add)
                val again = vm.rescan("with ${overlapping.size} overlapping folders")
                assertEquals(keys, again.keys())
                assertEquals("no game listed twice", first.games.size, again.games.size)
                overlapping.forEach(library::remove)

                // An empty folder adds nothing; once gone it is "not available now", and the rest stays.
                library.add(empty.path)
                assertEquals(keys, vm.rescan("with an empty folder").keys())
                assertTrue(empty.delete())
                val away = vm.rescan("with a folder gone")
                assertEquals(listOf(empty.path), away.unavailableRoots)
                assertEquals(keys, away.keys())
                ActivityScenario.launch(MainActivity::class.java).use {
                    val banner = Device.plural(R.plurals.lib_folders_unavailable, 1, 1)
                    waitFor(banner)
                    compose.onNodeWithText(banner).performClick()
                    waitFor(games.path)
                    compose.onNodeWithText(Device.string(R.string.folders_installs_here)).assertExists()
                    compose.onNodeWithText(Device.string(R.string.folders_unavailable)).assertExists()
                }
                assertTrue(empty.mkdirs())
                assertTrue(vm.rescan("with the folder back").unavailableRoots.isEmpty())

                // Removing the games folder: its games leave the list; its files stay.
                val files = games.list()!!.sorted()
                library.remove(games.path)
                assertTrue(vm.rescan("without the games folder").games.none { it.identityKey in keys })
                assertEquals(files, games.list()!!.sorted())
            }
        } finally {
            empty.deleteRecursively()
        }
    }
}
