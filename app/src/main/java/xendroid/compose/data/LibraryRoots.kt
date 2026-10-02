package xendroid.compose.data

/**
 * L03: the folders the library scans (real paths, All Files Access). Storage SAF tree URIs
 * are not scanned: the core reads games with mmap/seek on real paths, and a URI is never
 * turned into a path blindly; a frontend's content:// launch keeps its own grant path.
 */
object LibraryRoots {
    /** Stored one per line: a path cannot contain a newline on Android. */
    fun encode(roots: List<String>): String = roots.joinToString("\n")

    /** The stored list; an install from before L03 had a single folder, which becomes the first. */
    fun decode(stored: String?, legacySingle: String?): List<String> {
        val list = stored?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
        return (list ?: listOfNotNull(legacySingle?.trim()?.takeIf { it.isNotEmpty() })).distinct()
    }

    fun add(roots: List<String>, path: String): List<String> =
        if (path.isBlank() || path in roots) roots else roots + path

    fun remove(roots: List<String>, path: String): List<String> = roots - path

    /**
     * Which roots to walk: [scan] in the user's order; [covered] lie inside another root,
     * whose walk already reaches them (walking both would list their games twice);
     * [unavailable] are missing or unreadable now (the others are still scanned).
     */
    data class Plan(val scan: List<String>, val covered: List<String>, val unavailable: List<String>)

    fun plan(roots: List<String>, canonical: (String) -> String?, readable: (String) -> Boolean): Plan {
        val unavailable = mutableListOf<String>()
        val resolved = mutableListOf<Pair<String, String>>()
        for (root in roots) {
            val path = canonical(root)
            if (path == null || !readable(root)) unavailable += root else resolved += root to path.trimEnd('/')
        }
        val covered = resolved.filter { (root, path) ->
            resolved.any { (other, otherPath) ->
                other != root && (path.startsWith("$otherPath/") || (path == otherPath && resolved.indexOfFirst { it.first == other } < resolved.indexOfFirst { it.first == root }))
            }
        }.map { it.first }
        return Plan(resolved.map { it.first }.filter { it !in covered }, covered, unavailable)
    }
}
