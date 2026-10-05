package xendroid.compose.roadmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.Utils
import xendroid.compose.patches.AssetPatchAssets
import xendroid.compose.patches.GamePatchesViewModel
import xendroid.compose.patches.GamePatchesViewModel.UiState
import xendroid.compose.patches.PatchPaths
import xendroid.compose.patches.PatchStore
import xendroid.compose.patches.PatchVersion
import xendroid.compose.sessions.SessionRuns

/**
 * Roadmap item 25 (L10, group C), on a game of `gameDir` (or `-e game`) that has bundled patches.
 * Before the game ever ran here, Game patches cannot tell the version ("Play the game once…").
 * With a patch of each file on, one run: the run records the game's module hashes, the file
 * whose hash is the game's is "For the version you last played", any other "For another version",
 * and xe.log shows "Patcher: Applying patch for" (patches of the game's version applied).
 *
 * Left for the phone: an APK update keeping a patch on, the "Updated to the patches of this app
 * version" notice (needs two app versions), the patch's effect in the game.
 */
@RunWith(AndroidJUnit4::class)
class Item25PatchVersionTest {
    private val context = Device.context

    @Test fun oneRunTellsWhichPatchFileIsForTheGame() {
        val game = GameSession.game()
        val title = Library(listOf(game.parentFile!!)).use { library ->
            library.viewModel().rescan("the game's folder").games.firstOrNull { it.launchUri == game.path }?.titleId
        }
        assumeTrue("the game's Title ID is not readable", title != null)
        val dir = PatchPaths.patchesDir().apply { mkdirs() }
        val saved = dir.listFiles().orEmpty().filter { it.isFile }.associate { it.name to it.readBytes() }
        val store = PatchStore(AssetPatchAssets(context), dir)
        val files = store.patchesForTitle(title!!).filter { !it.mine }
        assumeTrue("no bundled patches for $title", files.isNotEmpty())
        try {
            if (SessionRuns.store().lastRun(title) == null) {
                val before = Device.await(GamePatchesViewModel(title, store, context).state, "patches listed") { it is UiState.Loaded } as UiState.Loaded
                assertFalse("the version is known before any run", before.versionKnown)
            }
            files.forEach { file -> file.entries.firstOrNull()?.let { store.setEnabled(file.fileName, it.index, true) } }
            val run = GameSession(game).use { session ->
                session.start()
                session.awaitFirstFrame()
                session.play(20)
                session.exitByMenu()
                session.awaitRecord("the run to finish", 60_000) { it.state.final }
            }
            assertTrue("the run recorded no module hash", run.moduleHashes.isNotEmpty())
            val after = Device.await(GamePatchesViewModel(title, store, context).state, "patches listed again") {
                it is UiState.Loaded && it.versionKnown
            } as UiState.Loaded
            val mine = after.versions.filterValues { it == PatchVersion.Match.YOURS }.keys
            GameRun.note(25, "$title hashes ${run.moduleHashes}: for this version ${mine.toList()}, " +
                "other ${after.versions.filterValues { it == PatchVersion.Match.OTHER }.keys.toList()}")
            val log = File(Utils.get_log_file_path()).takeIf { it.isFile }?.readText().orEmpty()
            if (mine.isNotEmpty()) {
                assertTrue("no \"Patcher: Applying patch for\" in xe.log", log.contains("Patcher: Applying patch for"))
            } else {
                assertEquals("files for another version", files.size, after.versions.count { it.value == PatchVersion.Match.OTHER })
            }
        } finally {
            dir.listFiles().orEmpty().filter { it.isFile }.forEach { f -> saved[f.name]?.let(f::writeBytes) ?: f.delete() }
        }
    }
}
