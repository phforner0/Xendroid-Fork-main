package xendroid.compose.core

import java.io.File
import java.nio.file.Files

/**
 * One game's shader and pipeline caches, as the core lays them out under its cache root
 * (gpu/shader_storage.h, vulkan_pipeline_cache.cc): shaders/shareable/<ID>.xsh (the guest's
 * shader microcode), shaders/shareable/<ID>.fsi|fbo.vk.xpso (pipeline descriptions) and
 * shaders/local/<ID>.vk.bin, with other drivers' copies kept aside as <ID>.<tag>.vk.bin.
 * Clearing removes only those files: they are rebuilt on the next start. The guest's own
 * cache partitions (cache0/cache1) hold game data, not shaders, and are never touched.
 */
object ShaderCaches {
    private val titlePattern = Regex("[0-9A-F]{8}")

    /** The core's cache root (xendroid_emu.cpp): <storage root>/cache, or Storage|cache_root,
     *  a relative one under the storage root. */
    fun cacheRoot(storageRoot: File, configured: String?): File {
        val value = configured?.trim()?.removeSurrounding("\"")?.trim().orEmpty()
        if (value.isEmpty()) return File(storageRoot, "cache")
        val path = File(value)
        return if (path.isAbsolute) path else File(storageRoot, value)
    }

    /** The title's cache files, regular files only (never a link out of the cache). */
    fun files(cacheRoot: File, titleId: String): List<File> {
        val id = titleId.uppercase()
        require(titlePattern.matches(id)) { "Invalid Title ID" }
        val shaders = File(cacheRoot, "shaders")
        return listOf("shareable", "local").flatMap { folder ->
            File(shaders, folder).listFiles().orEmpty().filter { file ->
                file.name.startsWith("$id.") && !Files.isSymbolicLink(file.toPath()) && file.isFile
            }
        }.sortedBy { it.path }
    }

    data class Cleared(val files: Int, val bytes: Long, val failed: Int)

    fun clear(cacheRoot: File, titleId: String): Cleared {
        var removed = 0
        var bytes = 0L
        var failed = 0
        for (file in files(cacheRoot, titleId)) {
            val size = file.length()
            if (file.delete()) {
                removed++
                bytes += size
            } else {
                failed++
            }
        }
        return Cleared(removed, bytes, failed)
    }
}
