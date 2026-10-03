package xendroid.compose.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShaderCachesTest {
    @get:Rule val folder = TemporaryFolder()

    private fun file(root: File, path: String, bytes: Int = 10): File =
        File(root, path).apply { parentFile.mkdirs(); writeBytes(ByteArray(bytes)) }

    @Test fun theCacheRootFollowsTheCore() {
        val storage = File("/storage/emulated/0/Android/data/x/files")
        assertEquals(File(storage, "cache"), ShaderCaches.cacheRoot(storage, null))
        assertEquals(File(storage, "cache"), ShaderCaches.cacheRoot(storage, "\"\""))
        assertEquals(File(storage, "mine"), ShaderCaches.cacheRoot(storage, "\"mine\""))
        assertEquals(File("/sdcard/xcache"), ShaderCaches.cacheRoot(storage, "/sdcard/xcache"))
    }

    @Test fun clearingRemovesOnlyThisGamesShadersAndPipelines() {
        val cache = folder.newFolder("cache")
        val mine = listOf(
            file(cache, "shaders/shareable/4D5309C9.xsh", 100),
            file(cache, "shaders/shareable/4D5309C9.fbo.vk.xpso", 20),
            file(cache, "shaders/shareable/4D5309C9.fsi.vk.xpso", 30),
            file(cache, "shaders/local/4D5309C9.vk.bin", 400),
            file(cache, "shaders/local/4D5309C9.5143-44050A00-0123.vk.bin", 50),   // another driver's, kept aside
        )
        val kept = listOf(
            file(cache, "shaders/shareable/41560817.xsh"),                       // another game
            file(cache, "shaders/shareable/4D5309C9X.xsh"),                      // not this ID
            file(cache, "shaders/local/41560817.vk.bin"),
            file(folder.root, "cache0/4D5309C9/data.bin"),                       // the guest's own cache partition
        )
        File(cache, "shaders/shareable/4D5309C9.folder").mkdirs()               // a folder is not a cache file
        val outside = file(folder.root, "elsewhere/secret.bin")
        Files.createSymbolicLink(File(cache, "shaders/local/4D5309C9.link.bin").toPath(), outside.toPath())

        assertEquals(mine.sortedBy { it.path }, ShaderCaches.files(cache, "4d5309c9"))
        val cleared = ShaderCaches.clear(cache, "4D5309C9")
        assertEquals(ShaderCaches.Cleared(files = 5, bytes = 600, failed = 0), cleared)
        assertTrue(mine.none { it.exists() })
        assertTrue(kept.all { it.exists() })
        assertTrue(outside.exists())                                             // the link was not followed
        assertEquals(emptyList<File>(), ShaderCaches.files(cache, "4D5309C9"))
        assertEquals(ShaderCaches.Cleared(0, 0, 0), ShaderCaches.clear(folder.newFolder("empty"), "4D5309C9"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aTitleIdThatCouldEscapeIsRefused() {
        ShaderCaches.files(folder.root, "../../x")
    }
}
