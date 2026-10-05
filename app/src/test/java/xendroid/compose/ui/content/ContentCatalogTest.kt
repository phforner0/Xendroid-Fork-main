package xendroid.compose.ui.content

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentCatalogTest {
    private fun dir(): File = Files.createTempDirectory("content").toFile()

    private fun file(parent: File, name: String, magic: String, length: Long, modified: Long = 0): File =
        File(parent, name).apply {
            RandomAccessFile(this, "rw").use { it.setLength(length); it.seek(0); it.write(magic.toByteArray(Charsets.ISO_8859_1)) }
            if (modified > 0) setLastModified(modified)
        }

    @Test fun findsTheTitlesWithDlcOrTitleUpdates() {
        val root = dir()
        File(root, "0000000000000000/4D5307E6/00000002/MAPPACK").mkdirs()
        File(root, "0000000000000000/4d5309c9/000B0000/TU3").mkdirs()
        File(root, "0000000000000000/5454082B/00000001").mkdirs()               // saves only
        File(root, "0000000000000000/not-a-title/00000002").mkdirs()
        File(root, "E03000000A1B2C3D/4E4D0859/00000002").mkdirs()               // a profile's, not the machine's
        assertEquals(listOf("4D5307E6", "4D5309C9"), ContentCatalog.titlesWithContent(root))
        assertEquals(emptyList<String>(), ContentCatalog.titlesWithContent(File(root, "missing")))
        root.deleteRecursively()
    }

    @Test fun tellsAPackageByItsFirstBytes() {
        val root = dir()
        assertTrue(ContentCatalog.looksLikePackage(file(root, "dlc.con", "CON ", 0xB000)))
        assertTrue(ContentCatalog.looksLikePackage(file(root, "tu", "LIVE", 0x20000)))
        assertTrue(ContentCatalog.looksLikePackage(file(root, "pack.pirs", "PIRS", 0xB000)))
        assertFalse(ContentCatalog.looksLikePackage(file(root, "saves.zip", "PK\u0003\u0004", 0x20000)))
        assertFalse(ContentCatalog.looksLikePackage(file(root, "tiny.con", "CON ", 100)))
        assertFalse(ContentCatalog.looksLikePackage(File(root, "absent")))
        root.deleteRecursively()
    }

    @Test fun listsTheNewestPackagesOfAFolderOnly() {
        val root = dir()
        file(root, "old.con", "CON ", 0xB000, modified = 1_000_000)
        file(root, "new.live", "LIVE", 0xB000, modified = 3_000_000)
        file(root, "photo.jpg", "ÿØÿà", 0xB000, modified = 2_000_000)
        file(root, ".hidden.con", "CON ", 0xB000, modified = 4_000_000)
        File(root, "sub").mkdirs(); file(File(root, "sub"), "inner.con", "CON ", 0xB000)
        assertEquals(listOf("new.live", "old.con"), ContentCatalog.packagesIn(root).map { it.name })
        // Only the newest files are opened.
        assertEquals(listOf("new.live"), ContentCatalog.packagesIn(root, look = 2).map { it.name })
        assertEquals(emptyList<File>(), ContentCatalog.packagesIn(null))
        root.deleteRecursively()
    }
}
