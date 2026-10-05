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
    /** Round 2: the content folder it was found under; null for Downloads. */
    val folder: String? = null,
    /** Round 2: a package of the same name is installed for its title already. */
    val installed: Boolean = false,
    /** Round 2: a title update's version, from its header ("1.0.3.0"). */
    val version: String? = null,
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

    /**
     * Round 2: packages in [dir] and its subfolders, [maxDepth] levels down, skipping hidden
     * folders and Android's app data; at most [maxEntries] entries are looked at, so a whole
     * card stays cheap. In name order.
     */
    fun packagesUnder(dir: File?, maxDepth: Int = 6, maxEntries: Int = 4000): List<File> {
        dir ?: return emptyList()
        val found = mutableListOf<File>()
        var seen = 0
        val queue = ArrayDeque(listOf(dir to 0))
        while (queue.isNotEmpty() && seen < maxEntries) {
            val (folder, depth) = queue.removeFirst()
            for (f in folder.listFiles().orEmpty().sortedBy { it.name.lowercase() }) {
                if (++seen > maxEntries) break
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    if (depth < maxDepth && !(depth == 0 && f.name == "Android")) queue.addLast(f to depth + 1)
                } else if (looksLikePackage(f)) found += f
            }
        }
        return found
    }

    /** Free space where packages are installed; null when unknown. */
    fun freeBytes(contentRoot: File = ContentPaths.contentRoot()): Long? {
        var probe: File? = contentRoot
        while (probe != null && !probe.exists()) probe = probe.parentFile
        return probe?.usableSpace?.takeIf { it > 0 }
    }
}
