package xendroid.compose.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xendroid.compose.archive.ArchiveFiles
import java.io.File

/** A title the library has seen (or that was played), kept after its file is gone. */
@Serializable
data class KnownTitle(
    val titleId: String,
    val name: String,
    val lastPath: String,
    val lastSeenAt: Long,
    /** "Remove from list": no longer reported as missing. Its history is untouched. */
    val hidden: Boolean = false,
)

/** A title played or seen before that this scan did not list. */
data class MissingTitle(val titleId: String, val name: String, val lastPath: String, val reason: MissingTitles.Reason)

/**
 * L06: what happened to the games the library knew. Pure, so the rules are tested on the JVM.
 * Only titles with a real Title ID are tracked: it is what play time, compatibility notes,
 * saves and covers are keyed by, and it survives a moved file.
 */
object MissingTitles {
    enum class Reason {
        /** The file is not at its last path and its folder is readable: moved or deleted. */
        FILE_GONE,
        /** Its game folder could not be read this time (SD card out, permission). */
        FOLDER_AWAY,
        /** The file is there but outside the game folders (folder removed, or launched by another app). */
        OUTSIDE_FOLDERS,
    }

    /**
     * Titles of [known] (the registry) and of [played] (title -> path of its last run, for
     * games played before the registry existed) that are not in [present], minus [hidden].
     */
    fun find(
        known: List<KnownTitle>,
        played: Map<String, String>,
        present: Set<String>,
        roots: List<String>,
        unavailableRoots: List<String>,
        exists: (String) -> Boolean,
    ): List<MissingTitle> {
        val byId = LinkedHashMap<String, KnownTitle>()
        known.forEach { byId[it.titleId] = it }
        played.forEach { (id, path) ->
            val title = CoverStore.normalize(id) ?: return@forEach
            if (title !in byId) byId[title] = KnownTitle(title, nameFromPath(path, title), path, lastSeenAt = 0)
        }
        return byId.values
            .filter { !it.hidden && it.titleId !in present }
            .map { MissingTitle(it.titleId, it.name, it.lastPath, reasonFor(it.lastPath, roots, unavailableRoots, exists)) }
            .sortedBy { it.name.lowercase() }
    }

    fun reasonFor(path: String, roots: List<String>, unavailableRoots: List<String>, exists: (String) -> Boolean): Reason {
        if (unavailableRoots.any { under(path, it) }) return Reason.FOLDER_AWAY
        if (!path.startsWith("/") || !exists(path)) return Reason.FILE_GONE
        return if (roots.any { under(path, it) }) Reason.FILE_GONE else Reason.OUTSIDE_FOLDERS
    }

    private fun under(path: String, root: String): Boolean = path.startsWith(root.trimEnd('/') + "/")

    /** A readable name for a title known only from a run's path. */
    fun nameFromPath(path: String, titleId: String): String {
        val file = File(path)
        val raw = if (file.name.equals("default.xex", ignoreCase = true)) file.parentFile?.name.orEmpty() else file.name
        val name = raw.replace(Regex("\\.(iso|zar|xex)$", RegexOption.IGNORE_CASE), "")
        // A GOD container is named by a hex hash, which tells the user nothing.
        return if (name.isBlank() || name.matches(Regex("[0-9A-Fa-f]{8,}"))) "Title $titleId" else name
    }
}

/**
 * The titles the library has seen, in app storage (`files/library/titles.json`). Written by
 * the scan (serialized by the repository) and by "Remove from list"; only the main process
 * uses it. Kept in memory after the first read; written only when something changed.
 */
class TitleRegistry(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxTitles: Int = 5000,
) {
    @Serializable
    private data class Snapshot(val version: Int = 1, val titles: List<KnownTitle> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(dir, "titles.json")
    private var titles: MutableMap<String, KnownTitle>? = null

    @Synchronized
    fun all(): List<KnownTitle> = loaded().values.toList()

    /** After a scan: every listed title is recorded (name and path refreshed) and shown again
     *  if it had been removed from the missing list. True when the file was rewritten. */
    @Synchronized
    fun record(games: List<Game>): Boolean {
        val map = loaded()
        val now = clock()
        var changed = false
        // Every disc of a set is listed; the first one stands for the title.
        val firsts = games.mapNotNull { game -> CoverStore.normalize(game.titleId)?.let { it to game } }
            .sortedWith(compareBy({ it.second.discNumber }, { it.second.launchUri }))
            .distinctBy { it.first }
        for ((id, game) in firsts) {
            val old = map[id]
            val name = game.name.take(200)
            val path = game.launchUri.take(4096)
            // lastSeenAt moves once a day at most, so an unchanged rescan writes nothing.
            if (old != null && !old.hidden && old.name == name && old.lastPath == path &&
                now - old.lastSeenAt < DAY_MS) continue
            map[id] = KnownTitle(id, name, path, now)
            changed = true
        }
        if (map.size > maxTitles) {
            map.values.sortedBy { it.lastSeenAt }.take(map.size - maxTitles).forEach { map.remove(it.titleId) }
            changed = true
        }
        if (changed) save(map)
        return changed
    }

    /** "Remove from list". A title known only from its runs is added so the choice is kept. */
    @Synchronized
    fun hide(title: MissingTitle) {
        val map = loaded()
        val id = CoverStore.normalize(title.titleId) ?: return
        val old = map[id] ?: KnownTitle(id, title.name.take(200), title.lastPath.take(4096), lastSeenAt = 0)
        if (old.hidden) return
        map[id] = old.copy(hidden = true)
        save(map)
    }

    private fun loaded(): MutableMap<String, KnownTitle> = titles ?: read().also { titles = it }

    private fun read(): MutableMap<String, KnownTitle> {
        val list = runCatching {
            if (!file.isFile || file.length() > MAX_FILE_BYTES) emptyList()
            else json.decodeFromString<Snapshot>(file.readText()).titles
        }.getOrDefault(emptyList())
        return list.mapNotNull { title -> CoverStore.normalize(title.titleId)?.let { it to title.copy(titleId = it) } }
            .toMap(LinkedHashMap())
    }

    private fun save(map: Map<String, KnownTitle>) =
        ArchiveFiles.atomicText(file, json.encodeToString(Snapshot(titles = map.values.toList())))

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_FILE_BYTES = 8L * 1024 * 1024
    }
}
