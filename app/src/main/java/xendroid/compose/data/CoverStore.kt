package xendroid.compose.data

import xendroid.compose.archive.ArchiveFiles
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * L05: covers keyed by the game's Title ID, in app storage rather than cacheDir, so they
 * survive moving or renaming the game file, a rescan and an OS cache clear, and one cover
 * serves every disc of a set. Per title: a copy of the game's own icon ("<ID>.png", taken
 * from the extraction cache during a scan) and the image the user picked ("<ID>.custom.png"),
 * which wins. Everything is local; nothing here downloads anything.
 */
class CoverStore(private val dir: File) {

    /** "<ID>:<size>" already compared this process: a rescan costs one stat per game. */
    private val kept = ConcurrentHashMap.newKeySet<String>()

    /** What a library tile shows: the user's cover, else [cachedIcon] (the icon extracted from
     *  this very file, while the cache still has it), else the copy kept for the title. */
    fun displayCover(titleId: String?, cachedIcon: File?): File? =
        customFor(titleId) ?: cachedIcon?.takeIf { it.isFile } ?: extractedFor(titleId)

    fun customFor(titleId: String?): File? = normalize(titleId)?.let(::custom)?.takeIf { it.isFile }

    fun extractedFor(titleId: String?): File? = normalize(titleId)?.let(::extracted)?.takeIf { it.isFile }

    /** Keeps a permanent copy of the icon a scan extracted for [titleId]. Copies when the copy
     *  is missing or differs in size; true when it wrote one. */
    fun rememberExtracted(titleId: String?, icon: File): Boolean {
        val id = normalize(titleId) ?: return false
        val size = icon.length()  // 0 when the file is gone
        if (size !in 1..MAX_ICON_BYTES) return false
        val key = "$id:$size"
        if (key in kept) return false
        val target = extracted(id)
        if (target.length() == size) {
            kept += key
            return false
        }
        val bytes = icon.readBytes()
        // Changed between the stat and the read, or not an image: try again on the next scan.
        if (bytes.size.toLong() != size || !isPng(bytes)) return false
        replace(target, bytes, durable = false)  // re-creatable from the game file
        kept += key
        return true
    }

    /** Stores the user's cover for [titleId]: a PNG the caller already decoded and resized. */
    fun setCustom(titleId: String?, png: ByteArray) {
        val id = requireNotNull(normalize(titleId)) { "This game has no Title ID" }
        require(png.size <= MAX_CUSTOM_BYTES) { "The cover is too large" }
        require(isPng(png)) { "The cover is not a PNG image" }
        replace(custom(id), png, durable = true)
    }

    /** Back to the game's own icon; true when a custom cover was removed. */
    fun clearCustom(titleId: String?): Boolean = normalize(titleId)?.let { custom(it).delete() } ?: false

    private fun extracted(id: String) = File(dir, "$id.png")
    private fun custom(id: String) = File(dir, "$id.custom.png")

    /** Readers see the old or the new file, never a partial one. */
    private fun replace(target: File, bytes: ByteArray, durable: Boolean) {
        if (durable) return ArchiveFiles.atomicBytes(target, bytes)
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create ${dir.name}" }
        val temp = File.createTempFile(".${target.name}.", ".tmp", dir)
        try {
            temp.writeBytes(bytes)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    companion object {
        /** Xbox 360 title icons are 64 px PNGs; anything far larger is not one. */
        const val MAX_ICON_BYTES = 1L * 1024 * 1024
        /** A [CoverPolicy.TARGET_SIDE] RGBA PNG stays well under this. */
        const val MAX_CUSTOM_BYTES = 2 * 1024 * 1024
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val TITLE_ID = Regex("[0-9A-F]{8}")

        /** A real Title ID, upper-case: 8 hex digits and not the 00000000 placeholder. */
        fun normalize(titleId: String?): String? =
            titleId?.trim()?.uppercase()?.takeIf { it.matches(TITLE_ID) && it != "00000000" }

        fun isPng(bytes: ByteArray): Boolean =
            bytes.size > PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }
    }
}

/** L05: limits for an image the user picks as a cover. */
object CoverPolicy {
    const val MAX_INPUT_BYTES = 32L * 1024 * 1024
    const val MAX_SIDE = 16_384
    /** Longest side kept: sharp in the library tiles and the game sheet, small on disk. */
    const val TARGET_SIDE = 512

    fun validate(width: Int, height: Int) {
        require(width > 0 && height > 0) { "The file is not a supported image" }
        require(width <= MAX_SIDE && height <= MAX_SIDE) {
            "The image is too large (${width}×$height; limit $MAX_SIDE px per side)"
        }
    }

    /** Size of the stored cover: aspect ratio kept, longest side at most [target], never
     *  enlarged, never below 1 px. */
    fun scaledSize(width: Int, height: Int, target: Int = TARGET_SIDE): Pair<Int, Int> {
        validate(width, height)
        val longest = maxOf(width, height)
        if (longest <= target) return width to height
        val scale = target.toDouble() / longest
        return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
    }
}
