package xendroid.compose.roadmap

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import xendroid.compose.data.MissingTitle
import xendroid.compose.data.MissingTitles
import xendroid.compose.ui.library.GameLibraryViewModel

/**
 * Roadmap item 20 (L06), on the `scratchDir` copy of a small game (never the real collection):
 * renamed out of the pattern (".bak") it leaves the list and the library says "1 game you played
 * or added is no longer in the library. Review", whose dialog says "File not found…" and the old
 * path; named back, it returns. A folder that is not there (the scratch folder renamed away)
 * keeps its games out of that notice, only in ⋮ → "Games no longer in the library" as "Its game
 * folder is not available now"; a folder taken out of the library makes them "outside your game
 * folders"; "Remove" hides one from the list. No file is deleted.
 *
 * Left for the phone: play time and report surviving (needs a run, group C), a real SD card.
 */
@RunWith(AndroidJUnit4::class)
class Item20MissingGamesTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(60_000) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun GameLibraryViewModel.missingTitle(title: String, reason: MissingTitles.Reason): MissingTitle =
        Device.await(missing, "$title missing ($reason)") { list -> list.any { it.titleId == title && it.reason == reason } }
            .single { it.titleId == title }

    @Test fun aGameThatLeftIsSaidAndComesBack() {
        val scratch = GameRun.scratchDir()
        Library(listOf(scratch)).use { library ->
            val vm = library.viewModel()
            val game = vm.rescan("the scratch folder").games
                .firstOrNull { File(it.launchUri).parentFile == scratch && it.titleId != null }
            assumeTrue("no game with a Title ID directly in $scratch", game != null)
            val file = File(game!!.launchUri)
            val title = game.titleId!!.uppercase()
            val renamed = File(file.path + ".bak")
            try {
                assertTrue(file.renameTo(renamed))
                assertTrue(vm.rescan("after renaming the game").games.none { it.launchUri == file.path })
                val gone = vm.missingTitle(title, MissingTitles.Reason.FILE_GONE)
                assertEquals(file.path, gone.lastPath)
                ActivityScenario.launch(MainActivity::class.java).use {
                    val banner = Device.plural(R.plurals.lib_games_gone, 1, 1)
                    waitFor(banner)
                    compose.onNodeWithText(banner).performClick()
                    waitFor(Device.string(R.string.missing_title))
                    compose.onNodeWithText(Device.string(R.string.missing_file_gone), substring = true).assertExists()
                    compose.onNodeWithText(file.path, substring = true).assertExists()
                }
            } finally {
                if (renamed.exists()) renamed.renameTo(file)
            }
            assertTrue(vm.rescan("after naming it back").games.any { it.launchUri == file.path })
            Device.await(vm.missing, "$title back in the library") { list -> list.none { it.titleId == title } }

            // The whole folder away (an SD card out) while another folder is there: the folder
            // notice and the menu's list say it, not the "no longer in the library" notice.
            val other = GameRun.workDir("available-folder").apply { mkdirs() }
            library.add(other.path)
            val away = File(scratch.path + "-away")
            try {
                assertTrue(scratch.renameTo(away))
                assertEquals(listOf(scratch.path), vm.rescan("with the folder away").unavailableRoots)
                vm.missingTitle(title, MissingTitles.Reason.FOLDER_AWAY)
                ActivityScenario.launch(MainActivity::class.java).use {
                    waitFor(Device.plural(R.plurals.lib_folders_unavailable, 1, 1))
                    compose.onAllNodesWithText(Device.plural(R.plurals.lib_games_gone, 1, 1)).fetchSemanticsNodes()
                        .let { assertTrue("a folder away is not in the notice", it.isEmpty()) }
                    compose.onNodeWithContentDescription(Device.string(R.string.lib_more)).performClick()
                    compose.onNodeWithText(Device.string(R.string.lib_menu_missing, 1)).performScrollTo().performClick()
                    waitFor(Device.string(R.string.missing_folder_away), substring = true)
                }
            } finally {
                if (away.exists()) away.renameTo(scratch)
                library.remove(other.path)
                other.deleteRecursively()
            }

            // A folder taken out of the library: "outside your game folders"; Remove hides it.
            library.remove(scratch.path)
            vm.rescan("without the scratch folder")
            val outside = vm.missingTitle(title, MissingTitles.Reason.OUTSIDE_FOLDERS)
            vm.hideMissing(outside)
            Device.await(vm.missing, "$title hidden") { list -> list.none { it.titleId == title } }
            assertTrue("no file is deleted", file.isFile)
        }
    }
}
