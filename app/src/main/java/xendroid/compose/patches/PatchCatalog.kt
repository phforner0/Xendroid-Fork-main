package xendroid.compose.patches

/**
 * L10: carrying the user's on/off choices from one version of a bundled patch file to the next.
 * Patches are matched by name (the n-th repeat of a name as "name#n"), never by position, since
 * a new catalog may add, drop or reorder them. Pure text in, text out: nothing is reserialized
 * (see [PatchTomlEditor]).
 */
object PatchCatalog {
    data class Rebase(val text: String, val keptOn: List<String>, val dropped: List<String>)

    private fun keyed(entries: List<PatchEntry>): List<Pair<String, PatchEntry>> {
        val seen = HashMap<String, Int>()
        return entries.map { entry ->
            val n = seen.merge(entry.name, 1, Int::plus)!!
            (if (n == 1) entry.name else "${entry.name}#$n") to entry
        }
    }

    private fun entriesOf(text: String): List<PatchEntry>? = PatchTomlParser.parse("", text)?.entries

    /** Keys of the patches switched on in [text]. */
    fun enabledKeys(text: String): Set<String> =
        keyed(entriesOf(text).orEmpty()).filter { it.second.isEnabled }.mapTo(LinkedHashSet()) { it.first }

    /** [text] with every patch switched off. */
    fun allOff(text: String): String =
        entriesOf(text).orEmpty().fold(text) { current, entry -> PatchTomlEditor.setEnabled(current, entry.index, false) }

    /** True when [a] and [b] differ at most in which patches are on. */
    fun onlySwitchesDiffer(a: String, b: String): Boolean = a == b || allOff(a) == allOff(b)

    /** [catalog] (a new bundled file) with [enabled] switched on where those patches still exist. */
    fun rebase(catalog: String, enabled: Set<String>): Rebase {
        var text = allOff(catalog)
        val kept = ArrayList<String>()
        for ((key, entry) in keyed(entriesOf(catalog).orEmpty())) {
            if (key !in enabled) continue
            text = PatchTomlEditor.setEnabled(text, entry.index, true)
            kept += key
        }
        return Rebase(text, kept, enabled.filter { it !in kept })
    }
}
