package xendroid.compose.bundle

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xendroid.compose.compatibility.CompatibilityReport
import xendroid.compose.compatibility.TitleCompatibility
import xendroid.compose.data.GameCollection
import xendroid.compose.data.GameCollections

/**
 * Settings and library metadata that move between installs or devices (L08, first
 * version): the global and per-game emulator configs, the touch-control layout,
 * favorites, collections (L06) and library sort, and the user's compatibility results.
 *
 * Deliberately NOT in a bundle: saves and profiles (their own manifest and lease),
 * game images and their paths, driver packages, LSFG data, logs and play history
 * (device-specific measurements).
 */
data class DataBundle(
    val globalConfig: String? = null,
    /** Title ID (8 upper-case hex digits) -> TOML text. */
    val gameConfigs: Map<String, String> = emptyMap(),
    /** GamepadConfigDto as JSON. */
    val gamepadLayout: String? = null,
    val favorites: Set<String> = emptySet(),
    val librarySort: String? = null,
    val compatibility: Map<String, TitleCompatibility> = emptyMap(),
    val collections: List<GameCollection> = emptyList(),
)

class BundleException(message: String) : Exception(message)

@Serializable
data class BundleEntry(val path: String, val sha256: String, val size: Long)

@Serializable
data class BundleManifest(
    val format: String,
    val version: Int,
    val createdAt: Long,
    val app: String,
    val entries: List<BundleEntry>,
)

@Serializable
private data class LibraryPreferences(
    val favorites: List<String> = emptyList(),
    val sort: String? = null,
    /** Added after the first bundles (same format version: older apps ignore it, older bundles lack it). */
    val collections: List<GameCollection> = emptyList(),
)

/** What an import would change, for the user to confirm first. */
data class ImportPlan(val lines: List<String>, val changes: Int)

object DataBundles {
    const val FORMAT = "xendroid-data-bundle"
    const val VERSION = 1
    private const val MAX_ENTRIES = 4096
    private const val MAX_ENTRY_BYTES = 1L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 32L * 1024 * 1024
    private const val MAX_COMPAT_REPORTS = 20

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val title = Regex("[0-9A-F]{8}")
    private val gameConfigPath = Regex("config/games/([0-9A-F]{8})\\.config\\.toml")
    private val compatPath = Regex("compatibility/([0-9A-F]{8})\\.json")
    private const val GLOBAL = "config/global.toml"
    private const val LAYOUT = "gamepad/layout.json"
    private const val LIBRARY = "library/preferences.json"
    private const val MANIFEST = "manifest.json"

    fun write(output: OutputStream, bundle: DataBundle, app: String, now: Long) {
        val files = linkedMapOf<String, ByteArray>()
        bundle.globalConfig?.let { files[GLOBAL] = it.toByteArray(Charsets.UTF_8) }
        bundle.gameConfigs.toSortedMap().forEach { (id, text) ->
            require(title.matches(id)) { "Invalid Title ID $id" }
            files["config/games/$id.config.toml"] = text.toByteArray(Charsets.UTF_8)
        }
        bundle.gamepadLayout?.let { files[LAYOUT] = it.toByteArray(Charsets.UTF_8) }
        if (bundle.favorites.isNotEmpty() || bundle.librarySort != null || bundle.collections.isNotEmpty()) {
            files[LIBRARY] = json.encodeToString(LibraryPreferences.serializer(),
                LibraryPreferences(bundle.favorites.sorted(), bundle.librarySort, bundle.collections)).toByteArray(Charsets.UTF_8)
        }
        bundle.compatibility.toSortedMap().forEach { (id, compat) ->
            require(title.matches(id)) { "Invalid Title ID $id" }
            files["compatibility/$id.json"] = json.encodeToString(TitleCompatibility.serializer(), compat).toByteArray(Charsets.UTF_8)
        }
        val manifest = BundleManifest(FORMAT, VERSION, now, app.take(128),
            files.map { (path, bytes) -> BundleEntry(path, sha256(bytes), bytes.size.toLong()) })
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(BundleManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            files.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    /** Reads and validates a bundle: known entries only, bounded, checksums as the manifest says. */
    fun read(input: InputStream): DataBundle {
        val files = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                if (files.size >= MAX_ENTRIES) throw BundleException("Too many entries")
                val name = entry.name
                if (name != MANIFEST && name != GLOBAL && name != LAYOUT && name != LIBRARY &&
                    !gameConfigPath.matches(name) && !compatPath.matches(name)) {
                    throw BundleException("Unexpected entry \"${name.take(80)}\": not a XenDroid data bundle")
                }
                if (name in files) throw BundleException("Duplicate entry $name")
                val bytes = readBounded(zip, MAX_ENTRY_BYTES) ?: throw BundleException("$name is too large")
                total += bytes.size
                if (total > MAX_TOTAL_BYTES) throw BundleException("Bundle is too large")
                files[name] = bytes
            }
        }
        val manifestBytes = files.remove(MANIFEST) ?: throw BundleException("Not a XenDroid data bundle (no manifest)")
        val manifest = runCatching { json.decodeFromString(BundleManifest.serializer(), text(MANIFEST, manifestBytes)) }
            .getOrElse { throw BundleException("Damaged manifest") }
        if (manifest.format != FORMAT) throw BundleException("Not a XenDroid data bundle")
        if (manifest.version > VERSION) throw BundleException("Made by a newer XenDroid (format ${manifest.version}); update the app first")
        val listed = manifest.entries.associateBy { it.path }
        if (listed.keys != files.keys) throw BundleException("Entries do not match the manifest")
        files.forEach { (path, bytes) ->
            if (listed.getValue(path).sha256 != sha256(bytes)) throw BundleException("$path is damaged (checksum)")
        }
        val library = files[LIBRARY]?.let {
            runCatching { json.decodeFromString(LibraryPreferences.serializer(), text(LIBRARY, it)) }
                .getOrElse { throw BundleException("Damaged library preferences") }
        }
        return DataBundle(
            globalConfig = files[GLOBAL]?.let { text(GLOBAL, it) },
            gameConfigs = files.mapNotNull { (path, bytes) ->
                gameConfigPath.matchEntire(path)?.let { it.groupValues[1] to text(path, bytes) }
            }.toMap(),
            gamepadLayout = files[LAYOUT]?.let { text(LAYOUT, it) },
            favorites = library?.favorites.orEmpty().filter { it.length <= 4096 }.toSet(),
            librarySort = library?.sort?.take(64),
            collections = GameCollections.merged(emptyList(), library?.collections.orEmpty()),
            compatibility = files.mapNotNull { (path, bytes) ->
                compatPath.matchEntire(path)?.let { match ->
                    val compat = runCatching { json.decodeFromString(TitleCompatibility.serializer(), text(path, bytes)) }
                        .getOrElse { throw BundleException("$path is damaged") }
                    match.groupValues[1] to compat.copy(titleId = match.groupValues[1])
                }
            }.toMap(),
        )
    }

    /**
     * The state after importing [incoming] over [current]: configs and the layout in the
     * bundle replace the current ones (others are kept), favorites and collection members
     * are added, never removed, and compatibility results are merged per title, newest first.
     */
    fun merged(current: DataBundle, incoming: DataBundle): DataBundle = DataBundle(
        globalConfig = incoming.globalConfig ?: current.globalConfig,
        gameConfigs = current.gameConfigs + incoming.gameConfigs,
        gamepadLayout = incoming.gamepadLayout ?: current.gamepadLayout,
        favorites = current.favorites + incoming.favorites,
        librarySort = incoming.librarySort ?: current.librarySort,
        compatibility = (current.compatibility.keys + incoming.compatibility.keys).associateWith { id ->
            val reports = (current.compatibility[id]?.reports.orEmpty() + incoming.compatibility[id]?.reports.orEmpty())
                .distinct().sortedByDescending(CompatibilityReport::createdAt).take(MAX_COMPAT_REPORTS)
            TitleCompatibility(titleId = id, reports = reports)
        },
        collections = GameCollections.merged(current.collections, incoming.collections),
    )

    fun plan(current: DataBundle, incoming: DataBundle): ImportPlan {
        val after = merged(current, incoming)
        val lines = mutableListOf<String>()
        var changes = 0
        fun note(changed: Boolean, text: String) { if (changed) changes++; lines += text }

        when {
            incoming.globalConfig == null -> lines += "Emulator settings: not in the bundle, kept"
            incoming.globalConfig == current.globalConfig -> lines += "Emulator settings: unchanged"
            else -> note(true, "Emulator settings: replaced (${lineDifference(current.globalConfig, incoming.globalConfig)} lines differ)")
        }
        val added = incoming.gameConfigs.keys - current.gameConfigs.keys
        val replaced = incoming.gameConfigs.filter { (id, text) -> id in current.gameConfigs && current.gameConfigs[id] != text }.keys
        note(added.isNotEmpty() || replaced.isNotEmpty(),
            "Per-game settings: ${added.size} new, ${replaced.size} replaced, " +
                "${incoming.gameConfigs.size - added.size - replaced.size} unchanged, " +
                "${(current.gameConfigs.keys - incoming.gameConfigs.keys).size} kept")
        when {
            incoming.gamepadLayout == null -> lines += "Touch controls: not in the bundle, kept"
            incoming.gamepadLayout == current.gamepadLayout -> lines += "Touch controls: unchanged"
            else -> note(true, "Touch controls: replaced")
        }
        val newFavorites = after.favorites.size - current.favorites.size
        note(newFavorites > 0, "Favorites: ${if (newFavorites > 0) "$newFavorites added" else "nothing new"}, none removed")
        if (after.librarySort != current.librarySort) note(true, "Library sort: ${after.librarySort}")
        if (incoming.collections.isNotEmpty()) {
            val before = GameCollections.merged(emptyList(), current.collections)
            val created = after.collections.count { GameCollections.find(before, it.name) == null }
            val added = after.collections.sumOf { it.members.size } - before.sumOf { it.members.size }
            note(created > 0 || added > 0, "Collections: $created new, $added game(s) added, none removed")
        }
        val newResults = after.compatibility.values.sumOf { it.reports.size } -
            current.compatibility.values.sumOf { it.reports.size }
        note(newResults > 0, "Compatibility results: ${maxOf(newResults, 0)} added")
        lines += "Kept from this device: storage folders, custom driver, saves, profiles, games and play history"
        return ImportPlan(lines, changes)
    }

    /** Lines present in one text and not the other (multiset), for the preview only. */
    private fun lineDifference(a: String?, b: String): Int {
        val left = a.orEmpty().lines().groupingBy { it }.eachCount().toMutableMap()
        var unmatched = 0
        b.lines().forEach { line ->
            val n = left[line] ?: 0
            if (n > 0) left[line] = n - 1 else unmatched++
        }
        return unmatched + left.values.sum()
    }

    private fun readBounded(input: InputStream, limit: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > limit) return null
            out.write(buffer, 0, n)
        }
    }

    private fun text(path: String, bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        throw BundleException("$path is not UTF-8 text")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
