package xendroid.compose.driver

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import xendroid.compose.archive.ArchiveFiles

/** One installed driver package: its library, what its metadata calls it, and when it came. */
data class InstalledDriverPackage(
    val dir: File,
    val library: File,
    val name: String,
    val version: String?,
    val installedAt: Long,
    /** Installed by [DriverPackageInstaller] (content-addressed, with its file list); false for
     *  a folder from the older importer. */
    val verifiedLayout: Boolean,
    val bytes: Long,
)

/**
 * The driver packages under the drivers folder, for the driver choices ("Start with…", the game's
 * driver, the Drivers area). Reads only small metadata files; a folder without a library is
 * skipped (half-installed or foreign). The library path is what the Vulkan|vulkan_lib_path setting
 * holds.
 */
object InstalledDrivers {
    private const val MARKER = "xendroid-installation.json"
    private const val META = "meta.json"
    private val json = Json { ignoreUnknownKeys = true }
    private val names = ConcurrentHashMap<String, String>()

    fun list(root: File): List<InstalledDriverPackage> = root.listFiles().orEmpty()
        .filter { it.isDirectory && !it.name.startsWith(".") }
        .mapNotNull { dir -> runCatching { read(dir) }.getOrNull() }
        .sortedWith(compareByDescending<InstalledDriverPackage> { it.installedAt }.thenBy { it.name.lowercase() })

    /** The package in [dir], or null when it holds no library. */
    fun read(dir: File): InstalledDriverPackage? {
        val marker = File(dir, MARKER)
        val meta = readJson(File(dir, META), 64 * 1024)
        val installation = readJson(marker, 1024 * 1024)
        val libraryName = installation?.string("library") ?: meta?.string("libraryName")
            ?: dir.walkTopDown().maxDepth(3).filter { it.isFile && it.extension == "so" }.singleOrNull()?.relativeTo(dir)?.invariantSeparatorsPath
            ?: return null
        val library = runCatching { ArchiveFiles.resolve(dir, libraryName) }.getOrNull()?.takeIf { it.isFile } ?: return null
        val name = meta?.string("name")?.trim()?.ifEmpty { null }
            ?: dir.name.takeUnless { it.matches(HASHED) } ?: library.nameWithoutExtension
        val version = meta?.string("driverVersion")?.trim()?.ifEmpty { null }
        val bytes = dir.walkTopDown().maxDepth(4).filter { it.isFile }.sumOf { it.length() }
        return InstalledDriverPackage(dir, library, name.take(80), version?.take(80),
            (if (marker.isFile) marker else library).lastModified(), installation != null, bytes)
    }

    /**
     * The name of the driver at [path] for the rows ("Turnip v25.3.0"): its package's metadata,
     * else its folder (unless that is a hash), else the file. Remembered per path.
     */
    fun nameOf(path: String, root: File?): String? {
        if (path.isBlank()) return null
        names[path]?.let { return it }
        val file = File(path)
        val dir = generateSequence(file.parentFile) { it.parentFile }.firstOrNull { it.parentFile == root } ?: file.parentFile
        val name = dir?.let { d -> runCatching { read(d) }.getOrNull()?.let { p -> listOfNotNull(p.name, p.version).joinToString(" ") } }
            ?: file.parentFile?.name?.takeUnless { it.isBlank() || it == "driver" || it.matches(HASHED) }
            ?: file.name
        names[path] = name
        return name
    }

    /** Drops the remembered names (a package was installed or removed). */
    fun forget() = names.clear()

    private val HASHED = Regex("[0-9a-f]{64}(-.+)?")

    private fun readJson(file: File, max: Long): JsonObject? =
        if (!file.isFile || file.length() !in 1..max) null
        else runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()

    private fun JsonObject.string(key: String): String? = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}
