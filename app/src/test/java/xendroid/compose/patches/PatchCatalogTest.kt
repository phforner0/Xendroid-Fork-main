package xendroid.compose.patches

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val HEADER = "title_name = \"Halo 3\"\ntitle_id = \"4D5307E6\"\nhash = \"ABCDEF0123456789\"\n"

private fun patch(name: String, value: String, enabled: Boolean = false) =
    "\n[[patch]]\n    name = \"$name\"\n    author = \"someone\"\n    is_enabled = $enabled\n\n" +
        "    [[patch.be32]]\n        address = 0x82000000 # keep this comment\n        value = $value\n"

/** The catalog this app version bundles, and the next one (reordered, one dropped, one added). */
private val V1 = HEADER + patch("60 FPS", "0x1") + patch("No HUD", "0x0")
private val V2 = HEADER + patch("Widescreen", "0x2") + patch("60 FPS", "0x3")

private class FakeAssets(var files: Map<String, String>) : PatchAssets {
    override fun list(): List<String> = files.keys.toList()
    override fun read(fileName: String): String = files.getValue(fileName)
}

class PatchCatalogTest {
    @Test fun choicesFollowPatchesByNameIntoANewCatalog() {
        val mine = PatchTomlEditor.setEnabled(PatchTomlEditor.setEnabled(V1, 0, true), 1, true)
        assertEquals(setOf("60 FPS", "No HUD"), PatchCatalog.enabledKeys(mine))
        val rebased = PatchCatalog.rebase(V2, PatchCatalog.enabledKeys(mine))
        assertEquals(listOf("60 FPS"), rebased.keptOn)
        assertEquals(listOf("No HUD"), rebased.dropped)
        val entries = PatchTomlParser.parse("x", rebased.text)!!.entries
        assertEquals(listOf("Widescreen" to false, "60 FPS" to true), entries.map { it.name to it.isEnabled })
        // Only the switch lines differ from the new catalog; its values and comments are kept.
        assertEquals(PatchTomlEditor.setEnabled(V2, 1, true), rebased.text)
        assertTrue(rebased.text.contains("value = 0x3") && rebased.text.contains("# keep this comment"))
    }

    @Test fun repeatedNamesAreKeptApart() {
        val text = HEADER + patch("Fix", "0x1") + patch("Fix", "0x2")
        val secondOn = PatchTomlEditor.setEnabled(text, 1, true)
        assertEquals(setOf("Fix#2"), PatchCatalog.enabledKeys(secondOn))
        assertEquals(listOf(false, true), PatchTomlParser.parse("x", PatchCatalog.rebase(text, setOf("Fix#2")).text)!!
            .entries.map { it.isEnabled })
    }

    @Test fun switchesAreToldApartFromRealEdits() {
        val toggled = PatchTomlEditor.setEnabled(V1, 1, true)
        assertTrue(PatchCatalog.onlySwitchesDiffer(toggled, V1))
        assertFalse(PatchCatalog.onlySwitchesDiffer(toggled.replace("value = 0x1", "value = 0x9"), V1))
        assertFalse(PatchCatalog.onlySwitchesDiffer(V2, V1))
    }
}

class PatchStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val name = "4D5307E6 - Halo 3.patch.toml"
    private val dir by lazy { temp.newFolder("patches") }
    private val assets = FakeAssets(mapOf(name to V1))
    private val store by lazy { PatchStore(assets, dir) }

    private fun onDisk() = File(dir, name).readText()
    private fun file() = store.patchesForTitle("4d5307e6").single()

    @Test fun toggleIsAtomicAndKeepsItsBaseOutOfTheEmulatorsWay() {
        assertEquals(listOf(false, false), file().entries.map { it.isEnabled })
        store.setEnabled(name, 0, true)
        assertEquals(listOf(true, false), file().entries.map { it.isEnabled })
        assertNull(file().update)
        val loaded = dir.list()!!.filter { Regex("^[A-Fa-f0-9]{8}.*\\.patch\\.toml$").matches(it) }
        assertEquals(listOf(name), loaded)                                     // what the core loads
        assertTrue(dir.list()!!.none { it.endsWith(".tmp") })
        assertEquals(V1, File(dir, "$name.base").readText())
    }

    @Test fun aNewCatalogIsTakenByItselfWhenOnlySwitchesChanged() {
        store.setEnabled(name, 0, true)
        store.setEnabled(name, 1, true)
        assets.files = mapOf(name to V2)                                       // the app was updated
        val updated = file()
        assertEquals(listOf("Widescreen" to false, "60 FPS" to true), updated.entries.map { it.name to it.isEnabled })
        assertEquals(PatchUpdate(pending = false, keptOn = listOf("60 FPS"), dropped = listOf("No HUD"), canUndo = true),
            updated.update)
        // Undo: the old file is back and the update is not applied again by itself.
        store.undoUpdate(name)
        assertEquals(listOf("60 FPS" to true, "No HUD" to true), file().entries.map { it.name to it.isEnabled })
        assertNull(file().update)
        assertFalse(File(dir, "$name.prev").exists())
    }

    @Test fun aFileChangedByHandWaitsForTheUser() {
        store.setEnabled(name, 0, true)
        File(dir, name).writeText(onDisk().replace("value = 0x0", "value = 0x7"))   // edited by hand
        val handEdited = onDisk()
        assets.files = mapOf(name to V2)
        val waiting = file()
        assertEquals(handEdited, onDisk())                                    // untouched
        assertEquals(PatchUpdate(pending = true, keptOn = listOf("60 FPS"), dropped = emptyList(), canUndo = false),
            waiting.update)
        store.keepMine(name)
        assertNull(file().update)
        assertEquals(handEdited, onDisk())
        // A later catalog asks again; this time the user updates, and can still go back.
        val v3 = V2.replace("value = 0x3", "value = 0x4")
        assets.files = mapOf(name to v3)
        assertTrue(file().update!!.pending)
        store.applyUpdate(name)
        assertEquals(PatchTomlEditor.setEnabled(v3, 1, true), onDisk())
        assertTrue(file().update!!.canUndo)
        store.undoUpdate(name)
        assertEquals(handEdited, onDisk())
        store.dismissUpdate(name)
    }

    @Test fun copiesFromBeforeTheBaseExistedAreSettled() {
        // Toggled by an older version (no base) against the catalog still bundled: adopted quietly.
        File(dir, name).writeText(PatchTomlEditor.setEnabled(V1, 1, true))
        assertNull(file().update)
        assertEquals(V1, File(dir, "$name.base").readText())
        // Toggled by an older version against an older catalog: cannot tell switches from edits.
        File(dir, "$name.base").delete()
        assets.files = mapOf(name to V2)
        assertTrue(file().update!!.pending)
    }

    @Test fun everyToggledCopyIsBroughtUpBeforeALaunch() {
        val other = "4D5307E6 - Halo 3 TU.patch.toml"
        assets.files = mapOf(name to V1, other to V1)
        store.setEnabled(name, 0, true)
        store.setEnabled(other, 0, true)
        File(dir, other).writeText(onDisk().replace("value = 0x1", "value = 0x5"))   // edited by hand
        File(dir, "99999999 - Mine.patch.toml").writeText("custom")                // not bundled: never touched
        assets.files = mapOf(name to V2, other to V2)
        assertEquals(1, store.syncAll())
        assertEquals(PatchTomlEditor.setEnabled(V2, 1, true), onDisk())
        assertTrue(File(dir, other).readText().contains("value = 0x5"))
        assertEquals("custom", File(dir, "99999999 - Mine.patch.toml").readText())
        assertEquals(0, store.syncAll())
    }

    @Test fun theUsersOwnFilesAreCheckedKeptApartAndStartOff() {
        val mod = HEADER + patch("Faster", "0x1", enabled = true)
        val stored = store.importUserPatch("4d5307e6", "My mod!.patch.toml", mod)
        assertEquals("4D5307E6 - mine - My mod.patch.toml", stored)
        assertEquals("4D5307E6 - mine - My mod (1).patch.toml", store.importUserPatch("4D5307E6", "My mod!.patch.toml", mod))
        val files = store.patchesForTitle("4D5307E6")
        assertEquals(listOf(false, true, true), files.map { it.mine })
        fun mod() = store.patchesForTitle("4D5307E6").single { it.fileName == stored }
        assertEquals(listOf(false), mod().entries.map { it.isEnabled })                // opt-in: imported off
        store.setEnabled(stored, 0, true)
        assertEquals(listOf(true), mod().entries.map { it.isEnabled })
        // The catalog's update machinery never touches them, and they are what the core loads.
        assets.files = mapOf(name to V2)
        store.syncAll()
        assertTrue(File(dir, stored).readText().contains("is_enabled = true"))
        assertTrue(Regex("^[A-Fa-f0-9]{8}.*\\.patch\\.toml$").matches(stored))
        // Refused: another title, a file that would crash the core, a catalog file to remove.
        assertTrue(runCatching { store.importUserPatch("41560817", "x", mod) }.exceptionOrNull()!!.message!!.contains("4D5307E6"))
        val crash = runCatching { store.importUserPatch("4D5307E6", "x", HEADER + "\n[[patch]]\nname = \"y\"\n[[patch.be32]]\nvalue = 1\n") }
        assertTrue(crash.exceptionOrNull() is PatchFileException)
        assertTrue(runCatching { store.removeUserPatch(name) }.isFailure)
        store.removeUserPatch(stored)
        assertFalse(File(dir, stored).exists())
    }

    @Test fun anUpdateKilledBeforeItsBaseIsSettledOnTheNextRead() {
        store.setEnabled(name, 0, true)
        assets.files = mapOf(name to V2)
        // Killed after the old copy and the new file were written, before the base.
        File(dir, "$name.prev").writeText(onDisk())
        File(dir, name).writeText(PatchCatalog.rebase(V2, setOf("60 FPS")).text)
        val settled = file()
        assertEquals(V2, File(dir, "$name.base").readText())
        assertEquals(listOf("Widescreen" to false, "60 FPS" to true), settled.entries.map { it.name to it.isEnabled })
        assertFalse(settled.update!!.pending)
    }
}
