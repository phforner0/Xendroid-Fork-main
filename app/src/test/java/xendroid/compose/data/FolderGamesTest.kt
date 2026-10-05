package xendroid.compose.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderGamesTest {
    private fun touch(parent: File, path: String) = File(parent, path).apply { parentFile?.mkdirs(); writeBytes(ByteArray(16)) }

    /** A disc image with the XDVDFS magic at sector 32 of a game partition starting at [partition]. */
    private fun disc(parent: File, path: String, partition: Long = 0) = File(parent, path).apply {
        parentFile?.mkdirs()
        RandomAccessFile(this, "rw").use { it.seek(partition + 32 * 2048); it.write("MICROSOFT*XBOX*MEDIA".toByteArray()) }
    }

    @Test fun countsWhatIsSurelyAGame() {
        val root = Files.createTempDirectory("games").toFile()
        disc(root, "Halo 3.iso")
        disc(root, "Forza/Forza Horizon.ISO", partition = 0x0000FB20)
        touch(root, "PS2/God of War.iso")                     // another console's disc
        touch(root, "GTA IV.zar")
        touch(root, "Viva Pinata/default.xex")
        touch(root, "Viva Pinata/media/movie.bik")            // game data, never walked
        touch(root, "XBLA/584108FF/000D0000/0123456789ABCDEF") // an arcade container in its type folder
        touch(root, "Downloads/TU13_4D5307E6")                // extensionless, but not a game container
        touch(root, "notes.txt")
        val count = FolderGames.count(root)
        assertEquals(5, count.games)
        assertFalse(count.partial)
        root.deleteRecursively()
    }

    @Test fun onlyAnXboxDiscCountsAsADisc() {
        val root = Files.createTempDirectory("games").toFile()
        assertTrue(FolderGames.isXboxDisc(disc(root, "a.iso")))
        assertTrue(FolderGames.isXboxDisc(disc(root, "b.iso", partition = 0x00020600)))
        assertFalse(FolderGames.isXboxDisc(touch(root, "c.iso")))
        assertFalse(FolderGames.isXboxDisc(File(root, "missing.iso")))
        root.deleteRecursively()
    }

    @Test fun aHugeFolderGivesAPartialCount() {
        val root = Files.createTempDirectory("games").toFile()
        repeat(30) { disc(root, "Game $it.iso") }
        val count = FolderGames.count(root, maxEntries = 10)
        assertTrue(count.partial)
        assertTrue(count.games in 1..10)
        root.deleteRecursively()
    }
}
