package xendroid.compose.data

import android.content.Context
import android.util.Log
import xendroid.compose.core.ContentPaths
import xendroid.compose.core.GameMetadataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Backstop for a pathological tree; symlink loops are already cut by canonical paths. */
private const val MAX_SCAN_DEPTH = 12

/** Discovers games by walking the real-path games dir and every subdirectory beneath it. */
class GameLibraryRepository(
    private val appContext: Context,
    private val prefs: PreferencesStore,
    private val metadata: GameMetadataSource,
    private val iconCache: IconCache,
    private val metadataCache: GameMetadataCache,
    private val covers: CoverStore,
    private val titles: TitleRegistry,
) {
    private val tag = "GameLibraryRepo"

    /** Serializes scans so two overlapping refresh()es don't both cold-extract and race on
     *  the cache file; the second runs cheap against the now-warm cache. */
    private val scanMutex = Mutex()

    sealed interface ScanResult {
        data object NoFolder : ScanResult
        data object PermissionLost : ScanResult
        /** [unavailableRoots]: folders that could not be read this time (the others were scanned). */
        data class Games(val games: List<Game>, val unavailableRoots: List<String> = emptyList()) : ScanResult
    }

    /** Validates the path is a readable directory FIRST so a later scan can't fail
     *  PermissionLost on an unreadable dir; then adds it to the library's folders (L03). */
    suspend fun addGameDirPath(path: String): Boolean = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.isDirectory || dir.listFiles() == null) {
            Log.w(tag, "addGameDirPath rejected (not a readable dir): $path")
            return@withContext false
        }
        prefs.addGameDirPath(path)
        true
    }

    /** Stops scanning [path]; nothing in it is deleted. */
    suspend fun removeGameDirPath(path: String) = withContext(Dispatchers.IO) { prefs.removeGameDirPath(path) }

    suspend fun gameDirPaths(): List<String> = withContext(Dispatchers.IO) {
        prefs.gameDirPaths.firstOrNull().orEmpty()
    }

    /** L06: titles seen by earlier scans, or played ([played]: title -> path of its last run),
     *  that [games] does not list, each with the reason. */
    suspend fun missingTitles(games: List<Game>, unavailable: List<String>, played: Map<String, String>): List<MissingTitle> =
        withContext(Dispatchers.IO) {
            val present = games.mapNotNullTo(HashSet()) { CoverStore.normalize(it.titleId) }
            MissingTitles.find(titles.all(), played, present, prefs.gameDirPaths.firstOrNull().orEmpty(), unavailable) {
                File(it).exists()
            }
        }

    /** "Remove from list": play time, compatibility notes, saves and covers stay. */
    suspend fun hideMissingTitle(title: MissingTitle) = withContext(Dispatchers.IO) { titles.hide(title) }

    suspend fun scan(): ScanResult = withContext(Dispatchers.IO) {
        scanMutex.withLock { scanLocked() }
    }

    private suspend fun scanLocked(): ScanResult {
        val roots = prefs.gameDirPaths.firstOrNull().orEmpty()
        if (roots.isEmpty()) return ScanResult.NoFolder
        val plan = LibraryRoots.plan(roots, canonical = { canonicalOf(File(it)) },
            readable = { File(it).let { dir -> dir.isDirectory && dir.listFiles() != null } })
        if (plan.scan.isEmpty()) return ScanResult.PermissionLost
        return scanRealPathsLocked(plan.scan, plan.unavailable)
    }

    /** Resolve a game's title id for the per-game config path (boot-free).
     *  MUST run off the main thread: these mmap the file. */
    suspend fun readTitleId(ctx: Context, game: Game): String? = withContext(Dispatchers.IO) {
        game.titleId?.takeIf { it.isNotBlank() && it != "00000000" }?.let { return@withContext it }
        when (game.format) {
            GameFormat.GOD -> metadata.readGodPath(game.launchUri)?.titleId
            GameFormat.STFS -> metadata.readContentHeader(game.launchUri)?.titleId
            GameFormat.ISO, GameFormat.XEX_FOLDER, GameFormat.ZAR ->
                metadata.readTitleIdPath(game.launchUri, game.format)
        }
    }

    private fun signatureOf(file: File): GameMetadataCache.Signature =
        GameMetadataCache.Signature(sizeBytes = file.length(), lastModified = file.lastModified())

    /** Cache lookup for the extracting branches: a fresh Hit (signature matches + icon
     *  File survives), else null so the caller extracts. */
    private fun metadataCacheHit(
        launchUri: String,
        signature: GameMetadataCache.Signature,
    ): GameMetadataCache.Decision.Hit? {
        val decision = GameMetadataCache.decide(
            cached = metadataCache.get(launchUri),
            signature = signature,
            iconFileExists = { iconCache.fileFor(it).exists() },
        )
        return decision as? GameMetadataCache.Decision.Hit
    }

    private data class Extracted(
        val name: String,
        val iconCacheName: String?,
        val titleId: String?,
        val mediaId: String?,
        val discNumber: Int = 0,
        val discCount: Int = 0,
    )

    /** Cache wrapper for the always-producing extracting branches (ISO, XEX_FOLDER, ZAR):
     *  reuse a fresh hit, else run [extract], cache it, and build the Game. */
    private inline fun cachedOrExtract(
        launchUri: String,
        signature: GameMetadataCache.Signature,
        format: GameFormat,
        extract: () -> Extracted,
    ): Game {
        metadataCacheHit(launchUri, signature)?.let {
            return Game(launchUri, it.name, format, it.iconCacheName, it.titleId, it.mediaId,
                        it.discNumber, it.discCount)
        }
        val e = extract()
        metadataCache.put(launchUri, e.name, e.iconCacheName, signature, e.titleId, e.mediaId,
                          format, e.discNumber, e.discCount)
        return Game(launchUri, e.name, format, e.iconCacheName, e.titleId, e.mediaId,
                    e.discNumber, e.discCount)
    }

    /** Walk each games folder and every subdirectory, classify each entry, sort. A root that
     *  stopped being listable since planning joins [unavailable]; none listable at all
     *  (grant revoked) -> [ScanResult.PermissionLost]; an unreadable subdirectory is skipped.
     *  One visited set across roots, and one entry per launch path. */
    private fun scanRealPathsLocked(roots: List<String>, unavailable: List<String>): ScanResult {
        // Load the extraction cache once; mutate during classify; persist once after.
        metadataCache.load()
        val games = ArrayList<Game>()
        val visited = HashSet<String>()
        val missing = unavailable.toMutableList()
        var scanned = 0
        for (dirPath in roots) {
            val root = File(dirPath)
            val children = root.listFiles()
            if (children == null) { missing += dirPath; continue }
            scanned++
            canonicalOf(root)?.let { visited.add(it) }
            collectGames(children, depth = 1, visited, games)
        }
        if (scanned == 0) return ScanResult.PermissionLost
        val unique = games.distinctBy { it.launchUri }.sortedBy { it.name.lowercase() }
        // What this scan saw stays cached, and so do the entries of a folder that is only away
        // (an SD card out): they are reused, not re-extracted, when it comes back.
        val away = missing.map { it.trimEnd('/') + "/" }
        metadataCache.retainOnly(unique.mapTo(HashSet()) { it.launchUri }) { key -> away.any(key::startsWith) }
        metadataCache.save()
        keepCovers(unique)
        runCatching { titles.record(unique) }.onFailure { Log.w(tag, "Recording the library's titles failed", it) }
        return ScanResult.Games(unique, missing)
    }

    /** L05: a copy of each title's own icon outside cacheDir, keyed by Title ID, so the tile
     *  keeps it after a cache clear or a move. A failure only costs that copy. */
    private fun keepCovers(games: List<Game>) {
        for (game in games) {
            val icon = game.iconCacheName?.let(iconCache::fileFor) ?: continue
            runCatching { covers.rememberExtracted(game.titleId, icon) }
                .onFailure { Log.w(tag, "Keeping the cover of ${game.titleId} failed", it) }
        }
    }

    /** Classify [children], then descend into every subdirectory that is not itself a game.
     *  [visited] holds canonical paths so a symlink loop cannot spin forever. */
    private fun collectGames(
        children: Array<File>,
        depth: Int,
        visited: MutableSet<String>,
        out: MutableList<Game>,
    ) {
        for (child in children) {
            if (!child.isDirectory) {
                classifyFile(child)?.let { out.add(it) }
                continue
            }
            if (!isScannableDir(child)) continue
            val entries = child.listFiles() ?: continue
            // A folder holding default.xex IS the game; its own subtree is game data.
            val xex = entries.firstOrNull {
                it.isFile && it.name.equals("default.xex", ignoreCase = true)
            }
            if (xex != null) {
                out.add(xexFolderGame(child, xex))
                continue
            }
            if (depth >= MAX_SCAN_DEPTH) {
                Log.w(tag, "scan depth $MAX_SCAN_DEPTH reached, not descending: ${child.absolutePath}")
                continue
            }
            val canonical = canonicalOf(child) ?: continue
            if (!visited.add(canonical)) continue
            collectGames(entries, depth + 1, visited, out)
        }
    }

    /** Skips hidden dirs and GOD "<container>.data" payload dirs, whose extensionless
     *  Data#### files would each be probed as a container. */
    private fun isScannableDir(dir: File): Boolean =
        !dir.name.startsWith(".") && !dir.name.endsWith(".data", ignoreCase = true)

    private fun canonicalOf(file: File): String? = runCatching { file.canonicalPath }.getOrNull()

    /** XEX folder: launch the default.xex child, fall back to the folder name. Signature is
     *  off default.xex, whose bytes are what extraction reads. */
    private fun xexFolderGame(dir: File, xex: File): Game {
        val xexPath = xex.absolutePath
        return cachedOrExtract(xexPath, signatureOf(xex), GameFormat.XEX_FOLDER) {
            val meta = metadata.readXexMetaPath(xexPath, GameFormat.XEX_FOLDER)
            val displayName = meta?.name?.takeIf { it.isNotEmpty() } ?: dir.name
            val iconName = meta?.iconPng?.let { iconCache.write(xexPath, it) }
            Extracted(displayName, iconName, meta?.titleId, meta?.mediaId,
                      meta?.discNumber ?: 0, meta?.discCount ?: 0)
        }
    }

    /** One file -> a Game, or null if ignored/unparseable. */
    private fun classifyFile(child: File): Game? {
        val name = child.name
        return when (GameFormat.fromFileName(name)) {
            GameFormat.ISO, GameFormat.ZAR -> {
                val fmt = GameFormat.fromFileName(name)!!
                val path = child.absolutePath
                cachedOrExtract(path, signatureOf(child), fmt) {
                    val meta = metadata.readXexMetaPath(path, fmt)
                    val displayName = meta?.name?.takeIf { it.isNotEmpty() }
                        ?: fmt.displayNameFor(name)
                    val iconName = meta?.iconPng?.let { iconCache.write(path, it) }
                    Extracted(displayName, iconName, meta?.titleId, meta?.mediaId,
                          meta?.discNumber ?: 0, meta?.discCount ?: 0)
                }
            }
            GameFormat.GOD -> classifyExtensionless(child, name)
            GameFormat.STFS, GameFormat.XEX_FOLDER, null -> null
        }
    }

    /** An extensionless file: a GOD container, an STFS launchable-game container, or neither.
     *  Distinguishes "not a game" (null) from a cache miss, and carries the format through the
     *  cache so a hit rebuilds the right Game. GOD MUST be probed before STFS (see below). */
    private fun classifyExtensionless(child: File, name: String): Game? {
        val path = child.absolutePath
        metadataCacheHit(path, signatureOf(child))?.let { hit ->
            val fmt = hit.format ?: GameFormat.GOD   // legacy entries predate STFS -> GOD
            return Game(path, hit.name, fmt, hit.iconCacheName, hit.titleId, hit.mediaId,
                        hit.discNumber, hit.discCount)
        }
        // The type gate runs before the GOD probe, not just in the STFS branch below: add-on
        // content (DLC, title updates, profiles, saves) is an STFS container too, so the GOD
        // reader parses it happily and would publish it as a game. An unreadable header is not
        // a rejection - it only means the type is unknown, so those still fall through.
        val header = metadata.readContentHeader(path)
        if (header != null && !ContentPaths.isLaunchableGameType(header.contentType)) return null
        metadata.readGodPath(path)?.let { meta ->
            val displayName = meta.name.ifEmpty { name }
            val iconName = meta.iconPng?.let { iconCache.write(path, it) }
            metadataCache.put(path, displayName, iconName, signatureOf(child), meta.titleId, meta.mediaId,
                              GameFormat.GOD, meta.discNumber, meta.discCount)
            return Game(path, displayName, GameFormat.GOD, iconName, meta.titleId, meta.mediaId,
                        meta.discNumber, meta.discCount)
        }
        // GOD is probed first (above) because a GOD container also parses as a content package.
        if (header == null) return null
        val displayName = header.displayName.ifBlank { name }
        val iconName = header.iconPng?.let { iconCache.write(path, it) }
        metadataCache.put(path, displayName, iconName, signatureOf(child), header.titleId, null, GameFormat.STFS)
        return Game(path, displayName, GameFormat.STFS, iconName, header.titleId, mediaId = null)
    }
}
