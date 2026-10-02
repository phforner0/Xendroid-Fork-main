package xendroid.compose.archive

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveFilesTest {
    @get:Rule val folder = TemporaryFolder()
    private fun archive(vararg entries: Pair<String, ByteArray>): File = folder.newFile().also { f ->
        ZipOutputStream(f.outputStream()).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
    }
    @Test fun traversalAndCaseCollisionsAreRejectedBeforeTheyCanEscape() {
        for (entries in listOf(arrayOf("../outside" to byteArrayOf(1)),
            arrayOf("dir/file" to byteArrayOf(1), "DIR/FILE" to byteArrayOf(2)))) {
            val dest = folder.newFolder()
            assertThrows(IllegalArgumentException::class.java) { ArchiveFiles.unpack(archive(*entries), dest, ArchiveLimits()) }
            assertFalse(File(folder.root, "outside").exists())
        }
    }
    @Test fun actualDecodedBytesAndEntryCountAreBounded() {
        val archive = archive("large" to ByteArray(4096))
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveFiles.unpack(archive, folder.newFolder(), ArchiveLimits(entryBytes = 256))
        }
    }
    @Test fun destinationLinksAreNeverFollowed() {
        val dir = folder.newFolder()
        val outside = folder.newFolder()
        Files.createSymbolicLink(File(dir, "link").toPath(), outside.toPath())
        assertThrows(IllegalArgumentException::class.java) { ArchiveFiles.resolve(dir, "link/file") }
        ArchiveFiles.deleteTree(dir)
        assertTrue(outside.isDirectory)
    }
    @Test fun contentLeasePreventsTwoConcurrentOwners() {
        val root = folder.newFolder()
        ContentLease.acquire(root).use {
            assertThrows(ContentBusyException::class.java) { ContentLease.acquire(root) }
        }
        ContentLease.acquire(root).use { }
    }

    @Test fun busyLeaseIsRetriedUntilReleasedWithoutBusyWaiting() = kotlinx.coroutines.test.runTest {
        var clock = 0L
        val sleeps = mutableListOf<Long>()
        var busyNotices = 0
        var attempts = 0
        val result = acquireWithRetry(timeoutMs = 1_000, pollMs = 250, now = { clock },
            sleep = { sleeps.add(it); clock += it }, onBusy = { busyNotices++ }) {
            if (++attempts < 3) throw ContentBusyException() else "lease"
        }
        assertEquals("lease", result)
        assertEquals(listOf(250L, 250L), sleeps)
        assertEquals(1, busyNotices)
    }

    @Test fun busyLeaseGivesUpAtTheDeadlineAndOtherErrorsAreNotRetried() = kotlinx.coroutines.test.runTest {
        var clock = 0L
        var attempts = 0
        assertThrows(ContentBusyException::class.java) {
            kotlinx.coroutines.runBlocking {
                acquireWithRetry(timeoutMs = 600, pollMs = 250, now = { clock }, sleep = { clock += it }) {
                    attempts++; throw ContentBusyException()
                }
            }
        }
        // 0, 250, 500 and the final attempt at the 600 ms deadline.
        assertEquals(4, attempts)
        attempts = 0
        assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                acquireWithRetry<Unit>(timeoutMs = 600, now = { clock }, sleep = { clock += it }) {
                    attempts++; throw java.io.IOException("disk")
                }
            }
        }
        assertEquals(1, attempts)
    }
}
