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

/** Why a bundle is refused: [why] for the screen to say in its language (U02), the message in
 *  English for logs and tests; [detail] is the entry, format version or Title ID it names. */
class BundleException(val why: Why, val detail: String = "") : Exception(why.english.format(detail)) {
    enum class Why(val english: String) {
        TOO_MANY_ENTRIES("Too many entries"),
        UNEXPECTED_ENTRY("Unexpected entry \"%s\": not a XenDroid data bundle"),
        DUPLICATE_ENTRY("Duplicate entry %s"),
        ENTRY_TOO_LARGE("%s is too large"),
        TOO_LARGE("Bundle is too large"),
        NO_MANIFEST("Not a XenDroid data bundle (no manifest)"),
        DAMAGED_MANIFEST("Damaged manifest"),
        NOT_A_BUNDLE("Not a XenDroid data bundle"),
        NEWER("Made by a newer XenDroid (format %s); update the app first"),
        ENTRIES_MISMATCH("Entries do not match the manifest"),
        CHECKSUM("%s is damaged (checksum)"),
        DAMAGED_LIBRARY("Damaged library preferences"),
        DAMAGED_ENTRY("%s is damaged"),
        NOT_UTF8("%s is not UTF-8 text"),
        CANNOT_WRITE("Cannot write to the chosen file"),
        CANNOT_READ("Cannot read the chosen file"),
        BAD_GLOBAL_TOML("The emulator settings in the bundle are not valid TOML"),
        BAD_GAME_TOML("The settings of %s in the bundle are not valid TOML"),
        BAD_LAYOUT("The touch control layout in the bundle is damaged"),
    }
}

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

/** What an import would change, for the user to confirm first: [items] for the screen to say
 *  in its language (U02), [lines] the same in English, for logs and tests. */
data class ImportPlan(val items: List<Item>, val changes: Int) {
    val lines: List<String> get() = items.map { it.english }

    sealed interface Item {
        val english: String

        enum class Part(val english: String) { SETTINGS("Emulator settings"), TOUCH("Touch controls") }
        /** The part is not in the bundle: this device's stays. */
        data class Absent(val part: Part) : Item {
            override val english: String get() = "${part.english}: not in the bundle, kept"
        }
        data class Unchanged(val part: Part) : Item {
            override val english: String get() = "${part.english}: unchanged"
        }
        /** [differ]: lines that differ, for the emulator settings. */
        data class Replaced(val part: Part, val differ: Int? = null) : Item {
            override val english: String get() = "${part.english}: replaced" +
                (differ?.let { " ($it ${if (it == 1) "line differs" else "lines differ"})" } ?: "")
        }
        data class GameSettings(val added: Int, val replaced: Int, val unchanged: Int, val kept: Int) : Item {
            override val english: String get() = "Per-game settings: $added new, $replaced replaced, $unchanged unchanged, $kept kept"
        }
        data class Favorites(val added: Int) : Item {
            override val english: String get() = "Favorites: ${if (added > 0) "$added added" else "nothing new"}, none removed"
        }
        /** [sort]: the library order as stored (a LibrarySort name). */
        data class Sort(val sort: String?) : Item {
            override val english: String get() = "Library sort: $sort"
        }
        data class Collections(val created: Int, val games: Int) : Item {
            override val english: String get() = "Collections: $created new, $games ${if (games == 1) "game" else "games"} added, none removed"
        }
        data class Results(val added: Int) : Item {
            override val english: String get() = "Compatibility results: $added added"
        }
        data object Kept : Item {
            override val english: String get() = "Kept from this device: storage folders, custom driver, saves, profiles, games and play history"
        }
    }
}

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
                if (files.size >= MAX_ENTRIES) throw BundleException(BundleException.Why.TOO_MANY_ENTRIES)
                val name = entry.name
                if (name != MANIFEST && name != GLOBAL && name != LAYOUT && name != LIBRARY &&
                    !gameConfigPath.matches(name) && !compatPath.matches(name)) {
                    throw BundleException(BundleException.Why.UNEXPECTED_ENTRY, name.take(80))
                }
                if (name in files) throw BundleException(BundleException.Why.DUPLICATE_ENTRY, name)
                val bytes = readBounded(zip, MAX_ENTRY_BYTES) ?: throw BundleException(BundleException.Why.ENTRY_TOO_LARGE, name)
                total += bytes.size
                if (total > MAX_TOTAL_BYTES) throw BundleException(BundleException.Why.TOO_LARGE)
                files[name] = bytes
            }
        }
        val manifestBytes = files.remove(MANIFEST) ?: throw BundleException(BundleException.Why.NO_MANIFEST)
        val manifest = runCatching { json.decodeFromString(BundleManifest.serializer(), text(MANIFEST, manifestBytes)) }
            .getOrElse { throw BundleException(BundleException.Why.DAMAGED_MANIFEST) }
        if (manifest.format != FORMAT) throw BundleException(BundleException.Why.NOT_A_BUNDLE)
        if (manifest.version > VERSION) throw BundleException(BundleException.Why.NEWER, "${manifest.version}")
        val listed = manifest.entries.associateBy { it.path }
        if (listed.keys != files.keys) throw BundleException(BundleException.Why.ENTRIES_MISMATCH)
        files.forEach { (path, bytes) ->
            if (listed.getValue(path).sha256 != sha256(bytes)) throw BundleException(BundleException.Why.CHECKSUM, path)
        }
        val library = files[LIBRARY]?.let {
            runCatching { json.decodeFromString(LibraryPreferences.serializer(), text(LIBRARY, it)) }
                .getOrElse { throw BundleException(BundleException.Why.DAMAGED_LIBRARY) }
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
                        .getOrElse { throw BundleException(BundleException.Why.DAMAGED_ENTRY, path) }
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
        val items = mutableListOf<ImportPlan.Item>()
        var changes = 0
        fun note(changed: Boolean, item: ImportPlan.Item) { if (changed) changes++; items += item }

        val settings = ImportPlan.Item.Part.SETTINGS
        when {
            incoming.globalConfig == null -> items += ImportPlan.Item.Absent(settings)
            incoming.globalConfig == current.globalConfig -> items += ImportPlan.Item.Unchanged(settings)
            else -> note(true, ImportPlan.Item.Replaced(settings, lineDifference(current.globalConfig, incoming.globalConfig)))
        }
        val added = incoming.gameConfigs.keys - current.gameConfigs.keys
        val replaced = incoming.gameConfigs.filter { (id, text) -> id in current.gameConfigs && current.gameConfigs[id] != text }.keys
        note(added.isNotEmpty() || replaced.isNotEmpty(), ImportPlan.Item.GameSettings(added.size, replaced.size,
            incoming.gameConfigs.size - added.size - replaced.size, (current.gameConfigs.keys - incoming.gameConfigs.keys).size))
        val touch = ImportPlan.Item.Part.TOUCH
        when {
            incoming.gamepadLayout == null -> items += ImportPlan.Item.Absent(touch)
            incoming.gamepadLayout == current.gamepadLayout -> items += ImportPlan.Item.Unchanged(touch)
            else -> note(true, ImportPlan.Item.Replaced(touch))
        }
        val newFavorites = after.favorites.size - current.favorites.size
        note(newFavorites > 0, ImportPlan.Item.Favorites(maxOf(newFavorites, 0)))
        if (after.librarySort != current.librarySort) note(true, ImportPlan.Item.Sort(after.librarySort))
        if (incoming.collections.isNotEmpty()) {
            val before = GameCollections.merged(emptyList(), current.collections)
            val created = after.collections.count { GameCollections.find(before, it.name) == null }
            val games = after.collections.sumOf { it.members.size } - before.sumOf { it.members.size }
            note(created > 0 || games > 0, ImportPlan.Item.Collections(created, games))
        }
        val newResults = after.compatibility.values.sumOf { it.reports.size } -
            current.compatibility.values.sumOf { it.reports.size }
        note(newResults > 0, ImportPlan.Item.Results(maxOf(newResults, 0)))
        items += ImportPlan.Item.Kept
        return ImportPlan(items, changes)
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
        throw BundleException(BundleException.Why.NOT_UTF8, path)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
