package xendroid.compose.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoverStoreTest {
    @get:Rule val temp = TemporaryFolder()

    private fun png(vararg body: Int): ByteArray =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + body.map { it.toByte() }.toByteArray()

    private fun icon(name: String, bytes: ByteArray): File = temp.newFile(name).apply { writeBytes(bytes) }

    @Test fun onlyRealTitleIdsAreKeys() {
        assertEquals("4D5307E6", CoverStore.normalize("4d5307e6"))
        assertEquals("4D5307E6", CoverStore.normalize(" 4D5307E6 "))
        assertNull(CoverStore.normalize("00000000"))
        assertNull(CoverStore.normalize("4D5307E"))
        assertNull(CoverStore.normalize("4D5307E6A"))
        assertNull(CoverStore.normalize("4D5307G6"))
        assertNull(CoverStore.normalize(null))
        val store = CoverStore(temp.newFolder("covers"))
        assertFalse(store.rememberExtracted("00000000", icon("a.png", png(1))))
        assertThrows(IllegalArgumentException::class.java) { store.setCustom(null, png(1)) }
        assertFalse(store.clearCustom("nope"))
    }

    @Test fun theOwnIconOutlivesTheCacheAndTheFileName() {
        val dir = temp.newFolder("covers")
        val store = CoverStore(dir)
        val cached = icon("cache-of-old-path.png", png(1, 2, 3))
        assertTrue(store.rememberExtracted("4d5307e6", cached))
        // A rescan of the unchanged library does not copy again.
        assertFalse(store.rememberExtracted("4D5307E6", cached))
        // The OS cleared cacheDir and the ISO was renamed: the tile still has the title's icon.
        cached.delete()
        val shown = store.displayCover("4D5307E6", cachedIcon = File(temp.root, "cache-of-new-path.png"))
        assertArrayEquals(png(1, 2, 3), shown!!.readBytes())
        // Every disc of the set shares it.
        assertEquals(shown, store.displayCover("4D5307E6", cachedIcon = null))
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun aChangedIconIsCopiedAgainByTheNextProcess() {
        val dir = temp.newFolder("covers")
        CoverStore(dir).rememberExtracted("4D5307E6", icon("v1.png", png(1)))
        val next = CoverStore(dir)
        assertFalse(next.rememberExtracted("4D5307E6", icon("same.png", png(1))))
        assertTrue(next.rememberExtracted("4D5307E6", icon("v2.png", png(1, 2))))
        assertArrayEquals(png(1, 2), next.extractedFor("4D5307E6")!!.readBytes())
    }

    @Test fun nothingButASmallPngIsKept() {
        val store = CoverStore(temp.newFolder("covers"))
        assertFalse(store.rememberExtracted("4D5307E6", icon("text.png", "not an image".toByteArray())))
        assertFalse(store.rememberExtracted("4D5307E6", File(temp.root, "missing.png")))
        assertFalse(store.rememberExtracted("4D5307E6", icon("empty.png", ByteArray(0))))
        val huge = icon("huge.png", png() + ByteArray(CoverStore.MAX_ICON_BYTES.toInt()))
        assertFalse(store.rememberExtracted("4D5307E6", huge))
        assertNull(store.extractedFor("4D5307E6"))
        assertThrows(IllegalArgumentException::class.java) { store.setCustom("4D5307E6", "jpeg?".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) {
            store.setCustom("4D5307E6", png() + ByteArray(CoverStore.MAX_CUSTOM_BYTES))
        }
        assertNull(store.customFor("4D5307E6"))
    }

    @Test fun theUsersCoverWinsUntilTheyGoBack() {
        val dir = temp.newFolder("covers")
        val store = CoverStore(dir)
        val cached = icon("cached.png", png(7))
        store.rememberExtracted("4D5307E6", cached)
        assertEquals(cached, store.displayCover("4D5307E6", cached))

        store.setCustom("4d5307e6", png(9, 9))
        assertArrayEquals(png(9, 9), store.displayCover("4D5307E6", cached)!!.readBytes())
        store.setCustom("4D5307E6", png(8))
        assertArrayEquals(png(8), store.customFor("4D5307E6")!!.readBytes())
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })

        assertTrue(store.clearCustom("4D5307E6"))
        assertFalse(store.clearCustom("4D5307E6"))
        assertEquals(cached, store.displayCover("4D5307E6", cached))
        // Another title is untouched by all of it.
        assertNull(store.displayCover("41560817", cachedIcon = null))
    }

    @Test fun aPickedImageIsShrunkKeepingItsShape() {
        assertEquals(512 to 288, CoverPolicy.scaledSize(1920, 1080))
        assertEquals(366 to 512, CoverPolicy.scaledSize(1000, 1400))
        assertEquals(512 to 512, CoverPolicy.scaledSize(4096, 4096))
        assertEquals(300 to 420, CoverPolicy.scaledSize(300, 420))   // never enlarged
        assertEquals(512 to 1, CoverPolicy.scaledSize(16_000, 3))     // never below 1 px
        assertThrows(IllegalArgumentException::class.java) { CoverPolicy.scaledSize(-1, -1) }  // not an image
        assertThrows(IllegalArgumentException::class.java) { CoverPolicy.scaledSize(20_000, 100) }
    }
}
