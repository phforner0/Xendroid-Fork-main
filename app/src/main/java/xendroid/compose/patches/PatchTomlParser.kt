package xendroid.compose.patches

/**
 * Display-only reader for .patch.toml header fields. Never reserializes (the files carry
 * comments and memory-write tables we must preserve — see [PatchTomlEditor]); reads only the
 * header keys and, per `[[patch]]`, name/desc/author/is_enabled. Sub-tables are ignored.
 */
object PatchTomlParser {

    fun parse(fileName: String, text: String): PatchFile? {
        var titleName = ""
        var titleId: String? = null
        var hashes: List<String> = emptyList()
        // Round 2: what the header's comments say about the version ("# TU1" after a name or a hash).
        val versionNotes = mutableListOf<String>()
        var inHashes = false

        val entries = mutableListOf<PatchEntry>()
        var inPatch = false
        var curName = ""
        var curDesc: String? = null
        var curAuthor: String? = null
        var curEnabled = false

        fun flush() {
            if (inPatch) {
                entries.add(
                    PatchEntry(entries.size, curName, curDesc, curAuthor, curEnabled)
                )
            }
            curName = ""; curDesc = null; curAuthor = null; curEnabled = false
        }

        for (raw in text.split("\n")) {
            val line = raw.trim()
            // A hash array over several lines: one quoted hash per line, "]" closes it.
            if (inHashes) {
                if (!line.startsWith("#")) {
                    hashes = hashes + Regex("\"([^\"]*)\"").findAll(line.substringBefore('#')).map { it.groupValues[1] }
                    comment(line)?.let(versionNotes::add)
                }
                if (line.substringBefore('#').contains(']')) inHashes = false
                continue
            }
            // Prefix match: headers may carry a trailing comment; `[[patch.` sub-tables don't match.
            if (line.startsWith("[[patch]]")) {
                flush()
                inPatch = true
                continue
            }
            if (line.startsWith("[[patch.") || line.startsWith("[patch")) continue
            if (!line.contains('=')) continue
            val key = line.substringBefore('=').trim()
            val rhs = line.substringAfter('=').trim()
            if (!inPatch) {
                when (key) {
                    "title_name" -> { titleName = parseString(rhs); comment(rhs)?.let(versionNotes::add) }
                    "title_id" -> titleId = parseString(rhs)
                    "hash" -> {
                        hashes = parseStringOrArray(rhs)
                        comment(rhs)?.let(versionNotes::add)
                        if (rhs.startsWith("[") && !rhs.substringBefore('#').contains(']')) inHashes = true
                    }
                }
            } else {
                when (key) {
                    "name" -> curName = parseString(rhs)
                    "desc" -> curDesc = parseString(rhs)
                    "author" -> curAuthor = parseString(rhs)
                    "is_enabled" -> curEnabled = rhs.startsWith("true")
                }
            }
        }
        flush()

        val id = titleId ?: return null
        return PatchFile(
            fileName = fileName,
            titleName = titleName,
            titleId = id,
            hashes = hashes,
            variantLabel = variantLabel(fileName, titleName),
            entries = entries,
            versionLabel = versionLabel(fileName, versionNotes),
        )
    }

    /** The comment after a value (outside its quotes), or null. */
    private fun comment(rhs: String): String? {
        var quoted = false
        rhs.forEachIndexed { i, ch ->
            if (ch == '"') quoted = !quoted
            if (ch == '#' && !quoted) return rhs.substring(i + 1).trim().ifEmpty { null }
        }
        return null
    }

    private val TU = Regex("""\bTU\s*#?\s*(\d+)\b""", RegexOption.IGNORE_CASE)
    private val TITLE_UPDATE = Regex("""\btitle\s+update\s*#?\s*(\d+)\b""", RegexOption.IGNORE_CASE)

    /** "TU 2" from the file name ("Undertow (TU2)") or the header's comments; null when none says. */
    internal fun versionLabel(fileName: String, notes: List<String>): String? =
        (listOf(fileName) + notes).firstNotNullOfOrNull { text ->
            (TU.find(text) ?: TITLE_UPDATE.find(text))?.groupValues?.get(1)?.toIntOrNull()?.let { "TU $it" }
        }

    /** `"foo"` -> `foo`; tolerates a trailing inline `# comment`. */
    private fun parseString(rhs: String): String {
        val s = rhs.trim()
        if (s.startsWith("\"")) {
            val end = s.indexOf('"', 1)
            if (end > 0) return unescape(s.substring(1, end))
        }
        // Unquoted fallback: take up to a comment marker.
        return s.substringBefore('#').trim()
    }

    /** `"a"` -> [a]; `["a", "b"]` -> [a, b]. */
    private fun parseStringOrArray(rhs: String): List<String> {
        val s = rhs.trim()
        if (!s.startsWith("[")) return listOf(parseString(s)).filter { it.isNotEmpty() }
        return Regex("\"([^\"]*)\"").findAll(s).map { it.groupValues[1] }.toList()
    }

    private fun unescape(s: String): String = s.replace("\\\"", "\"").replace("\\\\", "\\")

    /** Filename `<TITLEID> - <rest>.patch.toml` -> `<rest>`; fallback to title name / base. */
    private fun variantLabel(fileName: String, titleName: String): String {
        val base = fileName.removeSuffix(".patch.toml")
        val dash = base.indexOf(" - ")
        val rest = if (dash >= 0) base.substring(dash + 3).trim() else ""
        return when {
            rest.isNotEmpty() -> rest
            titleName.isNotEmpty() -> titleName
            else -> base
        }
    }
}
