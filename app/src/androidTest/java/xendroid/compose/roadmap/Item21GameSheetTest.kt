package xendroid.compose.roadmap

import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.MainActivity
import xendroid.compose.R
import xendroid.compose.bundle.DataBundleIo
import xendroid.compose.bundle.ImportPlan
import xendroid.compose.data.CollectionRefusedException
import xendroid.compose.data.GameCollection
import xendroid.compose.data.GameCollections
import xendroid.compose.patches.AssetPatchAssets
import xendroid.compose.patches.PatchPaths
import xendroid.compose.patches.PatchStore

/**
 * Roadmap item 21 (L06), with the phone's `gameDir`: the game sheet (long press) says "N of M
 * enabled" for the game's patches and the number follows a patch turned on; collections: "RPGs"
 * with a game, "rpgs" again refused in the shown language, another game added, the "All
 * collections" filter showing "RPGs (2)" and only those games, delete keeps the games; the
 * collection travels in the data bundle ("Collections: 1 new…") and comes back.
 *
 * Left for the phone: installed TU/DLC in "Manage content" (item 23 installs one), "Last played
 * as" (needs runs, group C), moving a file (the user's).
 */
@RunWith(AndroidJUnit4::class)
class Item21GameSheetTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = Device.context

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(60_000) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun patchesCountAndCollections() {
        val patchesDir = PatchPaths.patchesDir().apply { mkdirs() }
        val savedPatches = patchesDir.listFiles().orEmpty().filter { it.isFile }.associate { it.name to it.readBytes() }
        Library(listOf(GameRun.gameDir())).use { library ->
            val vm = library.viewModel()
            val games = vm.rescan("the games folder").games.filter { it.titleId != null }
            assumeTrue("no game with a Title ID in the games folder", games.isNotEmpty())
            runBlocking { vm.editCollections { emptyList() } }
            try {
                // The sheet's patch count follows a patch turned on.
                val store = PatchStore(AssetPatchAssets(context), patchesDir)
                val patched = games.firstOrNull { store.patchesForTitle(it.titleId!!).any { f -> f.entries.any { e -> !e.isEnabled } } }
                if (patched != null) {
                    val file = store.patchesForTitle(patched.titleId!!).first { f -> f.entries.any { !it.isEnabled } }
                    val total = store.patchesForTitle(patched.titleId).sumOf { it.entries.size }
                    val before = store.patchesForTitle(patched.titleId).sumOf { f -> f.entries.count { it.isEnabled } }
                    store.setEnabled(file.fileName, file.entries.first { !it.isEnabled }.index, true)
                    ActivityScenario.launch(MainActivity::class.java).use {
                        waitFor(patched.name)
                        compose.onAllNodesWithText(patched.name).onFirst().performTouchInput { longClick() }
                        waitFor(Device.string(R.string.lib_patches_enabled, before + 1, total))
                    }
                } else {
                    GameRun.note(21, "no game in the folder has bundled patches: the sheet's patch count was not checked")
                }

                // Collections.
                val first = games[0]
                assertTrue(runBlocking { vm.editCollections { GameCollections.create(it, "RPGs", first.identityKey) } }.isSuccess)
                val duplicate = runBlocking { vm.editCollections { GameCollections.create(it, "rpgs", first.identityKey) } }
                    .exceptionOrNull() as CollectionRefusedException
                assertEquals(CollectionRefusedException.Why.DUPLICATE, duplicate.why)
                val second = games.drop(1).firstOrNull { it.identityKey != first.identityKey }
                second?.let { other ->
                    assertTrue(runBlocking { vm.editCollections { GameCollections.setMember(it, "RPGs", other.identityKey, true) } }.isSuccess)
                }
                val members = listOfNotNull(first, second)
                val rpgs = runBlocking { vm.collections.first { c -> c.any { it.members.size == members.size } } }.single()
                assertEquals(GameCollection("RPGs", members.map { it.identityKey }), rpgs)
                val outside = games.firstOrNull { it.identityKey !in rpgs.members }
                ActivityScenario.launch(MainActivity::class.java).use {
                    waitFor(first.name)
                    compose.onNodeWithText(Device.string(R.string.lib_all_collections)).performClick()
                    compose.onNodeWithText("RPGs (${members.size})").performClick()
                    members.forEach { waitFor(it.name) }
                    outside?.let { other ->
                        compose.waitUntil(10_000) { compose.onAllNodesWithText(other.name).fetchSemanticsNodes().isEmpty() }
                    }
                }

                // The collection travels in the data bundle.
                val bundle = File(context.cacheDir, "roadmap-collections.zip").apply { delete() }
                runBlocking { DataBundleIo.export(context, Uri.fromFile(bundle)) }
                assertTrue(runBlocking { vm.editCollections { GameCollections.delete(it, "RPGs") } }.isSuccess)
                runBlocking { vm.collections.first { it.isEmpty() } }
                assertEquals(games.map { it.identityKey }.toSet(), vm.rescan("after deleting the collection").games
                    .filter { it.titleId != null }.map { it.identityKey }.toSet())
                val (incoming, plan) = runBlocking { DataBundleIo.preview(context, Uri.fromFile(bundle)) }
                assertTrue(plan.items.toString(), ImportPlan.Item.Collections(1, members.size) in plan.items)
                runBlocking { DataBundleIo.import(context, incoming) }
                assertEquals(rpgs, runBlocking { vm.collections.first { it.isNotEmpty() } }.single())
            } finally {
                runBlocking { vm.editCollections { emptyList() } }
                patchesDir.listFiles().orEmpty().filter { it.isFile }.forEach { f -> savedPatches[f.name]?.let(f::writeBytes) ?: f.delete() }
            }
        }
    }
}
