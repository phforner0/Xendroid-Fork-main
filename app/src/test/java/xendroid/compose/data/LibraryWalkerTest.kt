package xendroid.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

class LibraryWalkerTest {
    @get:Rule val temp = TemporaryFolder()

    private fun file(path: String): File = File(temp.root, path).apply { parentFile!!.mkdirs(); createNewFile() }
    private fun dir(path: String) = File(temp.root, path).path
    private fun names(result: LibraryWalker.Result) =
        result.candidates.map { it.file.relativeTo(temp.root).invariantSeparatorsPath }.sorted()

    @Test fun findsWhatMayBeAGameAndSkipsTheRest() {
        file("g/Halo 3.iso"); file("g/Forza.ZAR"); file("g/readme.txt"); file("g/sub/deeper/Gears.iso")
        file("g/Braid/default.xex"); file("g/Braid/media/inner.iso")             // game data: not walked
        file("g/4D5307E6/00007000/0123ABCD")                                       // GOD container
        file("g/4D5307E6/00007000/0123ABCD.data/Data0000")                         // its payload: skipped
        file("g/.thumbnails/x.iso")
        val result = LibraryWalker().walk(listOf(dir("g")))
        assertEquals(listOf("g/4D5307E6/00007000/0123ABCD", "g/Braid/default.xex", "g/Forza.ZAR", "g/Halo 3.iso",
            "g/sub/deeper/Gears.iso"), names(result))
        val xex = result.candidates.filterIsInstance<LibraryWalker.Candidate.XexFolder>().single()
        assertEquals("Braid", xex.dir.name)
        assertFalse(result.truncated)
    }

    @Test fun tenThousandEntriesAreWalkedOnceEvenWithOverlappingRoots() {
        // 100 folders × 100 files; every tenth is an ISO.
        for (d in 0 until 100) for (f in 0 until 100) file("big/d$d/f$f" + if (f % 10 == 0) ".iso" else ".bin")
        val progress = ArrayList<Int>()
        val started = System.nanoTime()
        val result = LibraryWalker(onProgress = { progress += it })
            .walk(listOf(dir("big"), dir("big/d5"), dir("big")))
        val ms = (System.nanoTime() - started) / 1_000_000
        println("LibraryWalker: ${result.entries} entries, ${result.candidates.size} candidates in $ms ms")
        assertEquals(1000, result.candidates.size)
        assertEquals(1000, result.candidates.map { it.file.path }.toSet().size)
        assertEquals(10_100, result.entries)       // 100 folders + 10,000 files, each once
        assertEquals(3, result.scannedRoots)
        assertTrue(progress.size >= 10_100 / 256 && progress.last() == 10_100)
    }

    @Test fun aFolderBiggerThanTheQuotaGivesAPartialList() {
        for (f in 0 until 1000) file("huge/f$f.iso")
        val result = LibraryWalker(maxEntries = 300).walk(listOf(dir("huge")))
        assertTrue(result.truncated)
        assertEquals(300, result.entries)
        assertEquals(300, result.candidates.size)
    }

    @Test fun cancellingStopsTheWalk() {
        for (f in 0 until 100) file("c/f$f.iso")
        var checks = 0
        assertThrows(CancellationException::class.java) {
            LibraryWalker(checkCancelled = { if (++checks > 10) throw CancellationException("left the library") })
                .walk(listOf(dir("c")))
        }
        assertEquals(11, checks)
    }

    @Test fun linksAndDepthCannotRunAway() {
        file("l/a/x.iso")
        Files.createSymbolicLink(File(temp.root, "l/a/loop").toPath(), File(temp.root, "l").toPath())
        assertEquals(listOf("l/a/x.iso"), names(LibraryWalker().walk(listOf(dir("l")))))
        file("deep/1/2/3/4/x.iso")
        assertEquals(emptyList<String>(), names(LibraryWalker(maxDepth = 3).walk(listOf(dir("deep")))))
        assertEquals(listOf("deep/1/2/3/4/x.iso"), names(LibraryWalker(maxDepth = 5).walk(listOf(dir("deep")))))
    }

    @Test fun anUnreadableRootDoesNotStopTheOthers() {
        file("ok/a.iso")
        val result = LibraryWalker().walk(listOf(dir("gone"), dir("ok")))
        assertEquals(1, result.scannedRoots)
        assertEquals(listOf(dir("gone")), result.missingRoots)
        assertEquals(listOf("ok/a.iso"), names(result))
    }

    @Test fun movingAFileElsewhereIsSeenAtItsNewPlace() {
        val iso = file("m/old/Halo 3.iso")
        assertEquals(listOf("m/old/Halo 3.iso"), names(LibraryWalker().walk(listOf(dir("m")))))
        File(temp.root, "m/new").mkdirs()
        assertTrue(iso.renameTo(File(temp.root, "m/new/Halo 3.iso")))
        assertEquals(listOf("m/new/Halo 3.iso"), names(LibraryWalker().walk(listOf(dir("m")))))
        File(temp.root, "m/new/Halo 3.iso").delete()
        assertEquals(emptyList<String>(), names(LibraryWalker().walk(listOf(dir("m")))))
    }
}
