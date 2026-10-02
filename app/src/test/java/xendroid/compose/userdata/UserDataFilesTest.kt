package xendroid.compose.userdata

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xendroid.compose.archive.ContentBusyException
import xendroid.compose.archive.ContentLease

class UserDataFilesTest {
    @get:Rule val temp = TemporaryFolder()

    private val root by lazy { temp.newFolder("compose") }
    private val leaseDir by lazy { temp.newFolder("lease") }
    private val files by lazy { UserDataFiles(root) { ContentLease.acquire(leaseDir) } }

    private fun file(path: String, text: String = "x"): File =
        File(root, path).apply { parentFile!!.mkdirs(); writeText(text) }
    private fun id(path: String) = File(root, path).absolutePath

    @Test fun onlyTheRootAndWhatIsInsideItAreDocuments() {
        file("xenia-canary.config.toml")
        file("content/0000000000000000/4D5307E6/00000002/Pack/data.bin")
        val outside = temp.newFile("outside.txt")
        File(temp.root, "secret").mkdirs(); File(temp.root, "secret/key").writeText("k")
        Files.createSymbolicLink(File(root, "link").toPath(), File(temp.root, "secret").toPath())
        file("cache0/shader.bin")
        file("content/.xendroid-trash/content/x/manifest.json")

        assertEquals(root.absolutePath, files.fileFor(root.absolutePath).absolutePath)
        assertTrue(files.fileFor(id("content/0000000000000000/4D5307E6/00000002/Pack/data.bin")).isFile)
        for (refused in listOf(outside.absolutePath, "/etc/passwd", "relative/path", "${root.absolutePath}/../outside.txt",
                "${root.absolutePath}/content/../../outside.txt", id("link/key"), id("link"), id("cache0/shader.bin"),
                id("content/.xendroid-trash/content/x/manifest.json"), id("missing.toml"))) {
            assertThrows(refused, FileNotFoundException::class.java) { files.fileFor(refused) }
        }
    }

    @Test fun listingsHideCachesAndBookkeeping() {
        file("xe.log"); file("config/4D5307E6.config.toml"); file("cache/a"); file("cache1/b")
        file("content/.xendroid-trash/content/x/data"); file("content/0000000000000000/x")
        file("content/.content-session.lock")
        assertEquals(listOf("config", "content", "xe.log"), files.children(root.absolutePath).map { it.name })
        assertEquals(listOf("0000000000000000"), files.children(id("content")).map { it.name })
        assertThrows(FileNotFoundException::class.java) { files.children(id("xe.log")) }
    }

    @Test fun childhoodIsDecidedBySegmentsNotText() {
        file("a/x"); file("ab/y")
        assertTrue(files.isChild(root.absolutePath, id("a/x")))
        assertTrue(files.isChild(id("a"), id("a/x")))
        assertFalse(files.isChild(id("a"), id("ab/y")))
        assertFalse(files.isChild(id("a"), id("a")))
        assertFalse(files.isChild(id("a"), id("a/../ab/y")))
        assertFalse(files.isChild(root.absolutePath, "/etc/passwd"))
        assertFalse(files.isChild(root.absolutePath, id("cache/a")))
    }

    @Test fun changesUseCleanNamesAndNeverReplace() {
        file("saves/save.bin", "old")
        val parent = id("saves")
        assertEquals(id("saves/save (1).bin"), files.create(parent, "save.bin", directory = false))
        assertEquals(id("saves/Folder"), files.create(parent, "Folder", directory = true))
        assertEquals(id("saves/Folder (1)"), files.create(parent, "Folder", directory = true))
        assertEquals("old", File(root, "saves/save.bin").readText())
        for (bad in listOf("a/b", "..", ".", "", " ", ".xendroid-trash", "x".repeat(256))) {
            assertThrows(bad, FileNotFoundException::class.java) { files.create(parent, bad, directory = false) }
        }
        assertThrows(FileNotFoundException::class.java) { files.create(root.absolutePath, "cache0", directory = true) }
        assertThrows(FileNotFoundException::class.java) { files.rename(id("saves/save.bin"), "save (1).bin") }
        assertEquals(id("saves/renamed.bin"), files.rename(id("saves/save.bin"), "renamed.bin"))
        assertThrows(FileNotFoundException::class.java) { files.delete(root.absolutePath) }
        assertThrows(FileNotFoundException::class.java) { files.rename(root.absolutePath, "other") }
        files.delete(id("saves/Folder"))
        assertFalse(File(root, "saves/Folder").exists())
    }

    @Test fun nothingChangesWhileTheLeaseIsTaken() {
        file("saves/save.bin", "kept")
        ContentLease.acquire(leaseDir).use {   // a running game
            assertThrows(ContentBusyException::class.java) { files.create(id("saves"), "new.bin", directory = false) }
            assertThrows(ContentBusyException::class.java) { files.delete(id("saves/save.bin")) }
            assertThrows(ContentBusyException::class.java) { files.openForWriting(id("saves/save.bin")) }
            assertEquals("kept", files.fileFor(id("saves/save.bin")).readText())     // reading still works
        }
        // Writing holds the lease until the file is closed.
        val (writable, lease) = files.openForWriting(id("saves/save.bin"))
        writable.writeText("new")
        assertThrows(ContentBusyException::class.java) { files.create(id("saves"), "other.bin", directory = false) }
        lease.close()
        files.create(id("saves"), "other.bin", directory = false)
        assertEquals("new", File(root, "saves/save.bin").readText())
    }

    @Test fun copiesAndMovesStayInsideAndNeverGoIntoThemselves() {
        file("patches/a/one.toml", "1"); file("patches/a/inner/two.toml", "2"); file("target/keep.txt")
        File(temp.root, "secret").mkdirs(); File(temp.root, "secret/key").writeText("k")
        Files.createSymbolicLink(File(root, "patches/a/escape").toPath(), File(temp.root, "secret").toPath())
        assertThrows(FileNotFoundException::class.java) { files.copy(id("patches/a"), id("patches/a/inner")) }
        assertThrows(FileNotFoundException::class.java) { files.move(id("patches/a"), id("patches/a/inner")) }
        val copied = files.copy(id("patches/a"), id("target"))
        assertEquals(id("target/a"), copied)
        assertEquals("2", File(root, "target/a/inner/two.toml").readText())
        assertFalse(File(root, "target/a/escape").exists())                  // the link was not followed out
        assertEquals(id("target/a (1)"), files.copy(id("patches/a"), id("target")))
        assertEquals(id("target/one.toml"), files.move(id("patches/a/one.toml"), id("target")))
        assertFalse(File(root, "patches/a/one.toml").exists())
        assertThrows(FileNotFoundException::class.java) { files.move(id("target/keep.txt"), "/tmp") }
    }

    @Test fun searchIsBoundedAndSkipsWhatIsHidden() {
        file("content/save1.bin"); file("content/deep/save2.bin"); file("cache/save-shader.bin")
        file("content/.xendroid-trash/save3.bin"); for (i in 0 until 30) file("many/save-$i.bin")
        val found = files.search("SAVE")
        assertEquals(20, found.size)
        assertTrue(found.none { it.path.contains("/cache/") || it.path.contains(".xendroid-") })
        assertEquals(emptyList<File>(), files.search("  "))
        assertEquals(listOf("save1.bin"), files.search("save1").map { it.name })
        assertTrue(files.search("save", maxVisited = 3).size <= 3)
    }
}
