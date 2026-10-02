package xendroid.compose.saves

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SaveBackupStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val tid = "4D5309C9"
    private val id = "E000000000000001"
    private fun file(root: File, path: String, text: String): File = File(root, path).apply { parentFile!!.mkdirs(); writeText(text) }
    private fun source(): File = folder.newFolder().also {
        file(it, "$id/$tid/00000001/slot/save.dat", "save-original")
        file(it, "$id/$tid/Headers/00000001/slot.header", "save-header")
        file(it, "$id/FFFE07D1/00010000/$id/Account", "profile-account")
        file(it, "0000000000000000/$tid/00000002/dlc/content", "dlc-must-not-backup")
    }
    @Test fun roundTripKeepsXuidHeadersProfileAndDoesNotCopyDlc() {
        val input = source()
        val store = SaveBackupStore(input, folder.newFolder())
        val archive = store.export(tid, listOf(id), File(folder.root, "backup.zip"))
        val target = folder.newFolder()
        val dlc = file(target, "0000000000000000/$tid/00000002/dlc/content", "existing-dlc")
        val restore = SaveBackupStore(target, folder.newFolder())
        val prepared = restore.prepare(archive, tid)
        assertTrue(prepared.conflicts.isEmpty())
        restore.restore(prepared, overwrite = false)
        assertEquals("save-original", File(target, "$id/$tid/00000001/slot/save.dat").readText())
        assertEquals("save-header", File(target, "$id/$tid/Headers/00000001/slot.header").readText())
        assertEquals("profile-account", File(target, "$id/FFFE07D1/00010000/$id/Account").readText())
        assertEquals("existing-dlc", dlc.readText())
    }
    @Test fun failuresDuringSwapRollBackAllPreviouslyReplacedScopes() {
        val input = source()
        val archive = SaveBackupStore(input, folder.newFolder()).export(tid, listOf(id), File(folder.root, "rollback.zip"))
        val target = source()
        val old = file(target, "$id/$tid/00000001/slot/save.dat", "destination-before-restore")
        val restore = SaveBackupStore(target, folder.newFolder())
        val prepared = restore.prepare(archive, tid)
        assertThrows(java.io.IOException::class.java) {
            restore.restore(prepared, overwrite = true) { if (it == "after:0") throw java.io.IOException("disk full") }
        }
        assertEquals("destination-before-restore", old.readText())
        assertEquals("save-header", File(target, "$id/$tid/Headers/00000001/slot.header").readText())
    }
    @Test fun crashJournalCanBeRecoveredBeforeNextContentOperation() {
        val archive = SaveBackupStore(source(), folder.newFolder()).export(tid, listOf(id), File(folder.root, "crash.zip"))
        val target = source()
        val old = file(target, "$id/$tid/00000001/slot/save.dat", "old")
        val restore = SaveBackupStore(target, folder.newFolder())
        val prepared = restore.prepare(archive, tid)
        assertThrows(AssertionError::class.java) {
            restore.restore(prepared, overwrite = true) { if (it == "after:0") throw AssertionError("process death") }
        }
        restore.recoverTransactions()
        assertEquals("old", old.readText())
    }
    @Test fun wrongGameAndUnconfirmedConflictsNeverTouchDestination() {
        val archive = SaveBackupStore(source(), folder.newFolder()).export(tid, listOf(id), File(folder.root, "conflict.zip"))
        val target = source()
        val restore = SaveBackupStore(target, folder.newFolder())
        assertThrows(IllegalArgumentException::class.java) { restore.prepare(archive, "12345678") }
        val prepared = restore.prepare(archive, tid)
        assertThrows(IllegalArgumentException::class.java) { restore.restore(prepared, overwrite = false) }
        assertEquals("save-original", File(target, "$id/$tid/00000001/slot/save.dat").readText())
        restore.discard(prepared)
    }
    @Test fun fingerprintChangesOnlyWithTheSavesAnExportWouldInclude() {
        val root = source()
        val store = SaveBackupStore(root, folder.newFolder())
        val first = store.fingerprint(tid)
        assertEquals(first, store.fingerprint(tid))
        // DLC and other titles are not part of the backup.
        file(root, "0000000000000000/$tid/00000002/dlc/content", "dlc-changed-and-longer")
        file(root, "$id/415607E6/00000001/other.sav", "other game")
        assertEquals(first, store.fingerprint(tid))
        val save = File(root, "$id/$tid/00000001/slot/save.dat")
        save.writeText("save-progressed")
        val second = store.fingerprint(tid)
        assertNotEquals(first, second)
        check(save.setLastModified(save.lastModified() - 10_000))
        assertNotEquals(second, store.fingerprint(tid))
    }

    @Test fun identicalSnapshotsProduceIdenticalHashesForBackupDeduplication() {
        val store = SaveBackupStore(source(), folder.newFolder())
        val first = store.export(tid, listOf(id), File(folder.root, "first.zip"))
        val second = store.export(tid, listOf(id), File(folder.root, "second.zip"))
        assertEquals(xendroid.compose.archive.ArchiveFiles.sha256(first), xendroid.compose.archive.ArchiveFiles.sha256(second))
    }
    /** Relative path -> content of every file under [root], skipping the transaction area. */
    private fun tree(root: File): Map<String, String> = root.walkTopDown()
        .filter { it.isFile && !it.relativeTo(root).invariantSeparatorsPath.startsWith(".save-transactions") }
        .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }

    /** Device state with an existing save and profile but no headers: the restore swaps
     * two scopes that had data and one that did not. */
    private fun mixedTarget(): File = folder.newFolder().also {
        file(it, "$id/$tid/00000001/slot/save.dat", "device-save")
        file(it, "$id/$tid/00000001/slot/second.dat", "device-second")
        file(it, "$id/FFFE07D1/00010000/$id/Account", "device-profile")
        file(it, "0000000000000000/$tid/00000002/dlc/content", "device-dlc")
    }

    @Test fun processDeathAtEveryRestoreBoundaryRecoversTheExactPreviousTree() {
        val archive = SaveBackupStore(source(), folder.newFolder()).export(tid, listOf(id), File(folder.root, "every.zip"))
        val labels = (0 until 3).flatMap { listOf("before:$it", "after:$it") }
        for (label in labels) {
            val target = mixedTarget()
            val before = tree(target)
            val store = SaveBackupStore(target, folder.newFolder())
            val prepared = store.prepare(archive, tid)
            assertThrows(label, AssertionError::class.java) {
                store.restore(prepared, overwrite = true, overwriteProfiles = true) {
                    if (it == label) throw AssertionError("process death at $label")
                }
            }
            val report = store.recoverTransactions()
            assertTrue(label, report.clean)
            assertEquals(label, listOf(prepared.directory.name), report.rolledBack)
            assertEquals(label, before, tree(target))
            // Replaying recovery is a no-op.
            assertEquals(label, RecoveryReport(), store.recoverTransactions())
            assertEquals(label, before, tree(target))
        }
    }

    @Test fun processDeathInsideARollbackIsResumedOnTheNextRecovery() {
        val archive = SaveBackupStore(source(), folder.newFolder()).export(tid, listOf(id), File(folder.root, "nested.zip"))
        val target = mixedTarget()
        val before = tree(target)
        val lease = folder.newFolder()
        val dying = SaveBackupStore(target, lease, rollbackCheckpoint = {
            if (it == "rollback:1") throw AssertionError("process death inside rollback")
        })
        val prepared = dying.prepare(archive, tid)
        assertThrows(AssertionError::class.java) {
            dying.restore(prepared, overwrite = true, overwriteProfiles = true) {
                if (it == "after:2") throw java.io.IOException("disk full")
            }
        }
        assertTrue(File(prepared.directory, "journal.json").isFile)
        val report = SaveBackupStore(target, lease).recoverTransactions()
        assertTrue(report.clean)
        assertEquals(before, tree(target))
        assertFalse(prepared.directory.exists())
    }

    @Test fun damagedJournalIsReportedKeptAndDoesNotStopOtherRollbacks() {
        val archive = SaveBackupStore(source(), folder.newFolder()).export(tid, listOf(id), File(folder.root, "damaged.zip"))
        val target = mixedTarget()
        val before = tree(target)
        val store = SaveBackupStore(target, folder.newFolder())
        val prepared = store.prepare(archive, tid)
        val damaged = File(target, ".save-transactions/restore-damaged").apply { mkdirs() }
        file(damaged, "journal.json", "{ not json")
        file(damaged, "original/$id/$tid/00000001/slot/save.dat", "older-device-save")
        assertThrows(SaveRecoveryException::class.java) { store.restore(prepared, overwrite = true) }
        // The healthy preview was not touched by the failed recovery attempt.
        assertTrue(prepared.directory.isDirectory)
        assertEquals(before, tree(target))
        // Simulate an interrupted restore of the healthy transaction: journal written, nothing swapped.
        File(prepared.directory, "journal.json").writeText(
            """{"version":1,"committed":false,"swaps":[{"scope":"$id/$tid/00000001","hadOriginal":true}]}""")
        val interrupted = SaveBackupStore(target, folder.newFolder())
        val report = interrupted.recoverTransactions()
        assertEquals(listOf(prepared.directory.name), report.rolledBack)
        assertEquals(listOf(damaged.name), report.failures.map { it.transaction })
        assertTrue(damaged.isDirectory)
        assertEquals("older-device-save", File(damaged, "original/$id/$tid/00000001/slot/save.dat").readText())
        assertEquals(before, tree(target))
        assertThrows(SaveRecoveryException::class.java) { interrupted.export(tid, listOf(id), File(folder.root, "blocked.zip")) }
    }

    @Test fun onlyStaleUnconfirmedPreviewsAreDiscarded() {
        val archive = SaveBackupStore(source(), folder.newFolder()).export(tid, listOf(id), File(folder.root, "stale.zip"))
        val target = mixedTarget()
        val lease = folder.newFolder()
        val prepared = SaveBackupStore(target, lease).prepare(archive, tid)
        val modified = prepared.directory.lastModified()
        assertEquals(RecoveryReport(), SaveBackupStore(target, lease, clock = { modified + 60_000 }).recoverTransactions())
        assertTrue(prepared.directory.isDirectory)
        val later = SaveBackupStore(target, lease, clock = { modified + 25L * 60 * 60 * 1000 }).recoverTransactions()
        assertEquals(listOf(prepared.directory.name), later.discardedStages)
        assertFalse(prepared.directory.exists())
    }

    @Test fun profileReplacementIsOptInAndStagesAreVerifiedAgainAfterReview() {
        val archive = SaveBackupStore(source(), folder.newFolder()).export(tid, listOf(id), File(folder.root, "profile.zip"))
        val target = source()
        val profile = file(target, "$id/FFFE07D1/00010000/$id/Account", "existing-profile")
        val store = SaveBackupStore(target, folder.newFolder())
        val preview = store.prepare(archive, tid)
        store.restore(preview, overwrite = true, overwriteProfiles = false)
        assertEquals("existing-profile", profile.readText())
        val corrupt = store.prepare(archive, tid)
        File(corrupt.directory, "unpacked/payload/$id/$tid/00000001/slot/save.dat").writeText("tampered")
        assertThrows(IllegalArgumentException::class.java) { store.restore(corrupt, overwrite = true) }
        assertEquals("save-original", File(target, "$id/$tid/00000001/slot/save.dat").readText())
        store.discard(corrupt)
    }
}
