package xendroid.compose.data

import java.io.File

/**
 * L09: walks the game folders and lists what may be a game, before anything is extracted.
 * Plain files only, so the rules (and their cost on a big tree) are tested on the JVM:
 *  - a folder holding default.xex IS a game; its own subtree is game data, never walked;
 *  - hidden folders and GOD "<container>.data" payload folders are skipped;
 *  - canonical paths cut symlink loops, and one visited set spans every root, so a folder
 *    reachable twice (nested roots, links) is walked once;
 *  - [maxDepth] stops a pathological tree, [maxEntries] stops a folder that is not a game
 *    folder (the whole storage) with a partial result instead of minutes of walking;
 *  - [checkCancelled] runs before every entry and may throw to stop the walk.
 */
class LibraryWalker(
    private val maxDepth: Int = 12,
    private val maxEntries: Int = 100_000,
    private val checkCancelled: () -> Unit = {},
    private val onProgress: (entries: Int) -> Unit = {},
) {
    sealed interface Candidate {
        /** The file the game launches from (an ISO, a ZAR, an extensionless container, default.xex). */
        val file: File

        data class GameFile(override val file: File) : Candidate
        data class XexFolder(val dir: File, override val file: File) : Candidate
    }

    data class Result(
        val candidates: List<Candidate>,
        /** Roots that could be listed. */
        val scannedRoots: Int,
        /** Roots that could not be listed this time. */
        val missingRoots: List<String>,
        /** True when [maxEntries] stopped the walk: the list is partial. */
        val truncated: Boolean,
        val entries: Int,
    )

    private class Stop : RuntimeException(null, null, false, false)

    private var entries = 0
    private var truncated = false

    fun walk(roots: List<String>): Result {
        entries = 0
        truncated = false
        val out = ArrayList<Candidate>()
        val visited = HashSet<String>()
        val missing = ArrayList<String>()
        var scanned = 0
        try {
            for (path in roots) {
                checkCancelled()
                val root = File(path)
                val children = root.listFiles()
                if (children == null) { missing += path; continue }
                scanned++
                // A root already walked (listed twice, or inside an earlier one) is not walked again.
                val canonical = canonicalOf(root)
                if (canonical != null && !visited.add(canonical)) continue
                walkChildren(children, depth = 1, visited, out)
            }
        } catch (_: Stop) {
            truncated = true
        }
        onProgress(entries)
        return Result(out, scanned, missing, truncated, entries)
    }

    private fun walkChildren(children: Array<File>, depth: Int, visited: MutableSet<String>, out: MutableList<Candidate>) {
        for (child in children) {
            checkCancelled()
            if (++entries > maxEntries) { entries = maxEntries; throw Stop() }
            if (entries % PROGRESS_EVERY == 0) onProgress(entries)
            if (!child.isDirectory) {
                if (mayBeGame(child.name)) out += Candidate.GameFile(child)
                continue
            }
            if (child.name.startsWith(".") || child.name.endsWith(".data", ignoreCase = true)) continue
            val inside = child.listFiles() ?: continue
            val xex = inside.firstOrNull { it.isFile && it.name.equals("default.xex", ignoreCase = true) }
            if (xex != null) {
                out += Candidate.XexFolder(child, xex)
                continue
            }
            if (depth >= maxDepth) continue
            val canonical = canonicalOf(child) ?: continue
            if (!visited.add(canonical)) continue
            walkChildren(inside, depth + 1, visited, out)
        }
    }

    private fun canonicalOf(file: File): String? = runCatching { file.canonicalPath }.getOrNull()

    companion object {
        private const val PROGRESS_EVERY = 256

        /** ISO, ZAR or an extensionless file (a GOD or STFS container); the scan decides. */
        fun mayBeGame(name: String): Boolean = when (GameFormat.fromFileName(name)) {
            GameFormat.ISO, GameFormat.ZAR, GameFormat.GOD -> true
            else -> false
        }
    }
}
