package xendroid.compose.roadmap

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.patches.AssetPatchAssets
import xendroid.compose.patches.GamePatchesViewModel
import xendroid.compose.patches.GamePatchesViewModel.UiState
import xendroid.compose.patches.PatchFileCheck
import xendroid.compose.patches.PatchPaths
import xendroid.compose.patches.PatchStore
import xendroid.compose.ui.patches.GamePatchesScreen
import xendroid.compose.ui.theme.xendroidTheme

/**
 * Roadmap item 26 (L11): a game's own patch file, added as from the "+" picker (a content:// Uri
 * with its name) to a game of the bundled catalog. It is added with its patches off and shown as
 * "added by you"; the same file again becomes "(1)"; a file of another game is refused naming its
 * Title ID; a write without `address` is refused with its line, and nothing refused reaches the
 * patches folder. With a catalog patch and the user's on, writing the same address, the screen
 * warns with the address; turning one off clears it; "Remove" deletes only the user's file.
 *
 * Left for the phone: playing with the patch on and the "Applying patch" line in xe.log (group C).
 */
@RunWith(AndroidJUnit4::class)
class Item26UserPatchesTest {
    @get:Rule val compose = createComposeRule()

    private val context = Device.context
    private lateinit var dir: File
    private lateinit var before: Map<String, ByteArray>
    private lateinit var catalogName: String
    private lateinit var title: String
    private lateinit var hash: String
    private var catalogIndex = 0
    private var address = 0L
    private lateinit var vm: GamePatchesViewModel

    @Before fun setUp() {
        Device.requireTestPackage()
        dir = PatchPaths.patchesDir().apply { mkdirs() }
        before = dir.listFiles().orEmpty().filter { it.isFile }.associate { it.name to it.readBytes() }
        // A catalog file with hashes and a patch that writes: the user's file will write there too.
        val assets = AssetPatchAssets(context)
        val (name, spec) = assets.list().asSequence()
            .mapNotNull { n -> runCatching { n to PatchFileCheck.read(assets.read(n)) }.getOrNull() }
            .first { (_, s) -> s.hashes.isNotEmpty() && s.patches.any { it.writes.isNotEmpty() } }
        catalogName = name
        title = spec.titleId
        hash = spec.hashes.first()
        catalogIndex = spec.patches.indexOfFirst { it.writes.isNotEmpty() }
        address = spec.patches[catalogIndex].writes.first().address
        vm = GamePatchesViewModel(title, PatchStore(assets, dir), context)
    }

    @After fun tearDown() {
        dir.listFiles().orEmpty().filter { it.isFile }.forEach { f -> before[f.name]?.let(f::writeBytes) ?: f.delete() }
    }

    private fun userPatch(titleId: String = title, withAddress: Boolean = true): String = buildString {
        appendLine("title_name = \"Roadmap\"")
        appendLine("title_id = \"$titleId\"")
        appendLine("hash = \"$hash\"")
        appendLine()
        appendLine("[[patch]]")
        appendLine("    name = \"Roadmap same address\"")
        appendLine("    author = \"roadmap test\"")
        appendLine("    is_enabled = true")
        appendLine("    [[patch.be32]]")
        if (withAddress) appendLine("        address = 0x%08X".format(address))
        appendLine("        value = 0x60000000")
    }

    private fun import(name: String, text: String): String {
        vm.clearMessage()
        vm.importUserPatch(Device.pickedFile(name, text.toByteArray()).second)
        return Device.await(vm.message, "importing $name") { it != null }!!
    }

    private fun loaded(what: String, matches: (UiState.Loaded) -> Boolean): UiState.Loaded =
        Device.await(vm.state, what) { it is UiState.Loaded && matches(it) } as UiState.Loaded

    @Test fun addRefuseWarnAndRemove() {
        val stored = "$title - mine - roadmap-60fps.patch.toml"
        assertEquals(Device.string(R.string.pt_added, stored), import("roadmap-60fps.patch.toml", userPatch()))
        assertTrue(File(dir, stored).readText().contains("is_enabled = false"))
        val mine = loaded("the file listed as the user's") { s -> s.files.any { it.fileName == stored } }
            .files.single { it.fileName == stored }
        assertTrue(mine.mine)
        assertTrue("its patches start off", mine.entries.none { it.isEnabled })

        assertEquals(Device.string(R.string.pt_added, "$title - mine - roadmap-60fps (1).patch.toml"),
            import("roadmap-60fps.patch.toml", userPatch()))
        val other = if (title == "4D5309C9") "415607E6" else "4D5309C9"
        assertEquals(Device.string(R.string.pt_not_added, "This file is for title $other, not this game ($title)"),
            import("other-game.patch.toml", userPatch(titleId = other)))
        val noAddress = userPatch(withAddress = false)
        val line = noAddress.lines().indexOfFirst { it.trim() == "[[patch.be32]]" } + 1
        assertEquals(Device.string(R.string.pt_not_added, "Line $line: the write has no address"),
            import("no-address.patch.toml", noAddress))
        assertEquals(setOf(stored, "$title - mine - roadmap-60fps (1).patch.toml"),
            dir.listFiles().orEmpty().map { it.name }.filter { it.contains(" - mine - ") }.toSet())

        // A catalog patch and the user's, both on, write the same address: the screen warns.
        val state = loaded("files listed") { true }
        val catalog = state.files.single { it.fileName == catalogName }
        vm.toggle(catalog, catalog.entries[catalogIndex], true)
        vm.toggle(mine, mine.entries[0], true)
        val conflict = loaded("the conflict") { it.conflicts.isNotEmpty() }.conflicts.single()
        assertEquals(address, conflict.address)
        compose.setContent { xendroidTheme { GamePatchesScreen(vm, gameName = "Roadmap", onBack = {}) } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(Device.string(R.string.pt_conflicts), substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(Device.string(R.string.pt_conflict_line, conflict.first, conflict.second, "0x%08X".format(address)),
            substring = true).assertExists()
        // The user's file comes after the catalog's patches: scrolled to (a lazy list has nothing
        // composed below the screen, which the first run on a phone hit).
        val addedByYou = Device.string(R.string.pt_added_by_you, mine.variantLabel)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(addedByYou))
        compose.onNodeWithText(addedByYou).assertExists()
        vm.toggle(mine, mine.entries[0], false)
        loaded("no conflict") { it.conflicts.isEmpty() }

        vm.clearMessage()
        vm.removeUserPatch(mine)
        assertEquals(Device.string(R.string.pt_removed, mine.variantLabel), Device.await(vm.message, "removing") { it != null })
        assertFalse(File(dir, stored).exists())
        assertTrue("the catalog's copy stays", File(dir, catalogName).isFile)
    }
}
