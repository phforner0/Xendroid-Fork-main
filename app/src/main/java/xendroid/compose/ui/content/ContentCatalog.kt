package xendroid.compose.ui.content

import java.io.File
import xendroid.compose.core.ContentPaths

/** A content package lying outside the content folder (Downloads), with what its header says. */
data class FoundPackage(
    val path: String,
    val fileName: String,
    /** 8-char hex, or null when the header names no title. */
    val titleId: String?,
    val contentType: Int,
    val displayName: String,
    val size: Long,
    val modified: Long,
) {
    val isGame: Boolean get() = ContentPaths.isLaunchableGameType(contentType)
}

/**
 * Where the Content area finds things: the titles with DLC or title updates installed (the core
 * lists one title at a time) and the packages waiting in a folder such as Downloads. Reads names
 * and a few header bytes only; the package itself is read by the core when it is installed.
 */
object ContentCatalog {
    private val TITLE = Regex("[0-9A-Fa-f]{8}")
    private val TYPES = listOf(ContentPaths.DLC_CONTENT_TYPE, ContentPaths.TU_CONTENT_TYPE).map { "%08X".format(it) }

    /** Title IDs (uppercase, sorted) with a DLC or title update folder under [contentRoot]. */
    fun titlesWithContent(contentRoot: File): List<String> =
        File(contentRoot, ContentPaths.MACHINE_XUID).listFiles().orEmpty()
            .filter { it.isDirectory && it.name.matches(TITLE) }
            .filter { dir -> TYPES.any { File(dir, it).isDirectory } }
            .map { it.name.uppercase() }
            .distinct()
            .sorted()

    /** STFS packages begin with one of these: console-signed, Xbox Live, or offline (PIRS). */
    private val MAGICS = listOf("CON ", "LIVE", "PIRS").map { it.toByteArray(Charsets.US_ASCII) }

    /** Smaller than its own header and first hash table: not a package. */
    const val MIN_PACKAGE_BYTES = 0xB000L

    fun looksLikePackage(file: File): Boolean {
        if (!file.isFile || file.length() < MIN_PACKAGE_BYTES) return false
        val head = ByteArray(4)
        val read = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(-1)
        return read == 4 && MAGICS.any { it.contentEquals(head) }
    }

    /** Packages in [dir] itself (not its subfolders), newest first; only the [look] newest files
     *  are opened, so a crowded Downloads stays cheap. */
    fun packagesIn(dir: File?, look: Int = 80): List<File> =
        dir?.listFiles().orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") }
            .sortedByDescending { it.lastModified() }
            .take(look)
            .filter(::looksLikePackage)

    /** Free space where packages are installed; null when unknown. */
    fun freeBytes(contentRoot: File = ContentPaths.contentRoot()): Long? {
        var probe: File? = contentRoot
        while (probe != null && !probe.exists()) probe = probe.parentFile
        return probe?.usableSpace?.takeIf { it > 0 }
    }
}
