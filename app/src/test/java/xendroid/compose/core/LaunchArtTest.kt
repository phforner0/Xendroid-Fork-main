package xendroid.compose.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xendroid.compose.data.CoverStore
import xendroid.compose.ui.ingame.BOOT_STILL_WORKING_SECONDS
import xendroid.compose.ui.ingame.BootStatus
import xendroid.compose.ui.ingame.bootProgress

class LaunchArtTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun onlyThisAppsOwnFilesFromALaunch() {
        val files = folder.newFolder("files")
        val cache = folder.newFolder("cache")
        val cover = File(files, "covers/4D5307E6.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val icon = File(cache, "game_icons/x.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        val elsewhere = File(folder.newFolder("other"), "secret.png").apply { writeBytes(byteArrayOf(1)) }
        val roots = listOf(files, cache)
        assertEquals(cover.canonicalFile, LaunchArt.fromExtra(cover.path, roots))
        assertEquals(icon.canonicalFile, LaunchArt.fromExtra(icon.path, roots))
        assertNull(LaunchArt.fromExtra(elsewhere.path, roots))
        // A way out of the folder by "..", a missing or empty file, nothing at all.
        assertNull(LaunchArt.fromExtra(File(files, "../other/secret.png").path, roots))
        assertNull(LaunchArt.fromExtra(File(files, "covers/none.png").path, roots))
        assertNull(LaunchArt.fromExtra(File(files, "covers").path, roots))
        assertNull(LaunchArt.fromExtra(File(files, "empty.png").apply { writeBytes(ByteArray(0)) }.path, roots))
        assertNull(LaunchArt.fromExtra(null, roots))
        assertNull(LaunchArt.fromExtra("", roots))
    }

    @Test fun theTitlesCoverOnceKnown() {
        val dir = folder.newFolder("covers")
        val covers = CoverStore(dir)
        assertNull(LaunchArt.forTitle(covers, "4D5307E6"))
        File(dir, "4D5307E6.png").writeBytes(byteArrayOf(1, 2))
        assertEquals(File(dir, "4D5307E6.png"), LaunchArt.forTitle(covers, "4d5307e6"))
        // The player's own cover wins, as in the library.
        File(dir, "4D5307E6.custom.png").writeBytes(byteArrayOf(3))
        assertEquals(File(dir, "4D5307E6.custom.png"), LaunchArt.forTitle(covers, "4D5307E6"))
        assertNull(LaunchArt.forTitle(covers, null))
        assertNull(LaunchArt.forTitle(covers, "00000000"))
    }

    @Test fun theBarMovesAStepPerStage() {
        val steps = BootStatus.Stage.entries.map(::bootProgress)
        assertEquals(steps.sorted(), steps)
        assertTrue(steps.all { it > 0f && it < 1f })
        assertEquals(4, steps.toSet().size)
        assertTrue(BOOT_STILL_WORKING_SECONDS in 10L..30L)
    }
}
