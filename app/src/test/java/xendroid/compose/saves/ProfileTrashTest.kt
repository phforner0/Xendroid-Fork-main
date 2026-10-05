package xendroid.compose.saves

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xendroid.compose.archive.ContentLease

class ProfileTrashTest {
    @get:Rule val folder = TemporaryFolder()
    private val xuid = "E000000000000001"
    private fun file(root: File, path: String, text: String) = File(root, path).apply { parentFile!!.mkdirs(); writeText(text) }

    private fun content(): File = folder.newFolder().also {
        file(it, "$xuid/FFFE07D1/00010000/$xuid/Account", "account")
        file(it, "$xuid/4D5309C9/00000001/slot/save.dat", "forza-save")
        file(it, "$xuid/4D5309C9/Headers/00000001/slot.header", "header")
        file(it, "$xuid/415607E6/00000001/game.sav", "other-save")
        file(it, "0000000000000000/4D5309C9/00000002/dlc/content", "dlc")
    }

    @Test fun summaryNamesEveryGameWhoseSavesWouldGo() {
        val summary = ProfileTrash(content()).summarize(xuid)
        assertEquals(listOf("415607E6", "4D5309C9"), summary.gameTitles.map { it.titleId })
        assertEquals(4, summary.files)
        assertEquals("account".length + "forza-save".length + "header".length + "other-save".length.toLong(), summary.bytes)
        assertFalse(summary.truncated)
    }

    @Test fun deleteIsAnAtomicMoveThatRestoresEverySave() {
        val root = content()
        val trash = ProfileTrash(root, clock = { 1_700_000_000_000 })
        val lease = folder.newFolder()
        val entry = ContentLease.acquire(lease).use { trash.moveToTrash(it, xuid) }
        assertFalse(File(root, xuid).exists())
        assertEquals("dlc", File(root, "0000000000000000/4D5309C9/00000002/dlc/content").readText())
        assertEquals(listOf(entry), trash.list())
        assertEquals("E000000000000001-1700000000000", entry.id)
        ContentLease.acquire(lease).use { assertEquals(xuid, trash.restore(it, entry.id)) }
        assertEquals("forza-save", File(root, "$xuid/4D5309C9/00000001/slot/save.dat").readText())
        assertEquals("account", File(root, "$xuid/FFFE07D1/00010000/$xuid/Account").readText())
        assertTrue(trash.list().isEmpty())
    }

    @Test fun restoreNeverMergesIntoARecreatedProfile() {
        val root = content()
        val trash = ProfileTrash(root)
        val lease = folder.newFolder()
        val entry = ContentLease.acquire(lease).use { trash.moveToTrash(it, xuid) }
        file(root, "$xuid/FFFE07D1/00010000/$xuid/Account", "new-account")
        ContentLease.acquire(lease).use { l ->
            assertThrows(IllegalArgumentException::class.java) { trash.restore(l, entry.id) }
        }
        assertEquals("new-account", File(root, "$xuid/FFFE07D1/00010000/$xuid/Account").readText())
        assertEquals(listOf(entry), trash.list())
    }

    @Test fun machineContentAndMalformedIdsAreRejected() {
        val root = content()
        val trash = ProfileTrash(root)
        val lease = folder.newFolder()
        ContentLease.acquire(lease).use { l ->
            assertThrows(IllegalArgumentException::class.java) { trash.moveToTrash(l, "0000000000000000") }
            assertThrows(IllegalArgumentException::class.java) { trash.moveToTrash(l, "../$xuid") }
            assertThrows(IllegalArgumentException::class.java) { trash.purge(l, "../../$xuid") }
        }
        assertTrue(File(root, "0000000000000000").isDirectory)
        assertTrue(File(root, xuid).isDirectory)
    }

    @Test fun purgeRemovesOnlyTheTrashedEntry() {
        val root = content()
        val trash = ProfileTrash(root, clock = { 1 })
        val lease = folder.newFolder()
        val entry = ContentLease.acquire(lease).use { trash.moveToTrash(it, xuid) }
        ContentLease.acquire(lease).use { trash.purge(it, entry.id) }
        assertTrue(trash.list().isEmpty())
        assertEquals("dlc", File(root, "0000000000000000/4D5309C9/00000002/dlc/content").readText())
    }
}
