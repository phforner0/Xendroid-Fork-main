package xendroid.compose.compatibility

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xendroid.compose.archive.ArchiveFiles
import xendroid.compose.archive.withDirectoryLock
import xendroid.compose.driver.DriverIdentity

/** What the user saw. Chosen by the user only: reaching a menu is never "playable". */
enum class CompatStatus(val label: String) {
    NOTHING("Doesn't boot"),
    BOOTS("Boots, no picture"),
    INTRO("Intro or menus only"),
    IN_GAME("In-game with problems"),
    PLAYABLE("Playable"),
}

/** One result, with the build and driver it was observed on (results do not carry over). */
@Serializable
data class CompatibilityReport(
    val status: CompatStatus,
    val note: String = "",
    val build: String,
    val gpu: String,
    val driverKey: String? = null,
    val driverLabel: String? = null,
    val createdAt: Long,
    /** Edition (Media ID) and disc the result was seen on, when the image header states them. */
    val mediaId: String? = null,
    val disc: Int? = null,
)

@Serializable
data class TitleCompatibility(val version: Int = 1, val titleId: String, val reports: List<CompatibilityReport> = emptyList()) {
    val latest: CompatibilityReport? get() = reports.firstOrNull()
}

/**
 * Local Compatibility Center (C03, first version): the user's own results per Title ID,
 * newest first and bounded. No network, no catalog: a remote catalog (C04) is a
 * separate, signed and versioned source.
 */
class CompatibilityStore(
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxReports: Int = 20,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val titlePattern = Regex("[0-9A-F]{8}")

    private fun file(titleId: String): File {
        val title = titleId.uppercase()
        require(titlePattern.matches(title) && title != "00000000") { "Invalid Title ID" }
        return File(root, "$title.json")
    }

    fun get(titleId: String): TitleCompatibility {
        val file = file(titleId)
        return withDirectoryLock(root) { read(file) } ?: TitleCompatibility(titleId = titleId.uppercase())
    }

    fun report(titleId: String, status: CompatStatus, note: String, build: String, gpu: String,
               driver: DriverIdentity?, mediaId: String? = null, disc: Int = 0): TitleCompatibility {
        val file = file(titleId)
        val report = CompatibilityReport(status, note.trim().take(500), build.take(128), gpu.take(128),
            driver?.key, driver?.label?.take(200), clock(),
            mediaId = mediaId?.uppercase()?.takeIf { titlePattern.matches(it) && it != "00000000" },
            disc = disc.takeIf { it in 1..99 })
        return withDirectoryLock(root) {
            val current = read(file) ?: TitleCompatibility(titleId = titleId.uppercase())
            current.copy(reports = (listOf(report) + current.reports).take(maxReports)).also {
                ArchiveFiles.atomicText(file, json.encodeToString(it))
            }
        }
    }

    /** Every title with results, by Title ID (a data bundle's export). Damaged files are skipped. */
    fun all(): Map<String, TitleCompatibility> = withDirectoryLock(root) {
        root.listFiles { f -> f.name.matches(Regex("[0-9A-F]{8}\\.json")) }.orEmpty()
            .mapNotNull { f -> read(f)?.let { f.name.take(8) to it.copy(titleId = f.name.take(8)) } }
            .filter { it.first != "00000000" }.toMap()
    }

    /** Replaces a title's results with an already merged list (a data bundle's import). */
    fun replace(titleId: String, compatibility: TitleCompatibility) {
        val file = file(titleId)
        val bounded = compatibility.copy(titleId = titleId.uppercase(), reports = compatibility.reports.take(maxReports))
        withDirectoryLock(root) { ArchiveFiles.atomicText(file, json.encodeToString(bounded)) }
    }

    private fun read(file: File): TitleCompatibility? = runCatching {
        if (!file.isFile || file.length() > 256 * 1024) return null
        json.decodeFromString<TitleCompatibility>(file.readText())
    }.getOrNull()
}
