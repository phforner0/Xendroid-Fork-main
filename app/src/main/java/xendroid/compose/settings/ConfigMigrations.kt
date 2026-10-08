package xendroid.compose.settings

/**
 * The core's own changes of default values (its UPDATE_from_* table), applied to the global config
 * file the app keeps. The core applies them in memory at every boot to a file whose defaults_date
 * is older, but on Android it never writes the file (the app owns it), so the date never advanced:
 * a value the player set back to an old default was changed again at every boot. Applied here once
 * and dated, a value still at the old default takes the new one, and what the player sets later
 * stays - as SaveConfig does on desktop.
 */
object ConfigMigrations {

    const val SECTION = "Config"
    const val DATE_KEY = "defaults_date"

    /** One update, values as the config file holds them ("true"/"false", decimals, raw strings). */
    data class Update(
        val date: Long,
        val section: String,
        val name: String,
        val type: String,
        val oldDefault: String,
        val newDefault: String,
    )

    /** Rows from the core: date, section, name, type, old default, new default, tab-separated. */
    fun parse(rows: Array<String>?): List<Update> = rows.orEmpty().mapNotNull { row ->
        val f = row.split('\t')
        if (f.size != 6) return@mapNotNull null
        val date = f[0].toLongOrNull() ?: return@mapNotNull null
        if (f[3] !in TYPES) return@mapNotNull null
        Update(date, f[1], f[2], f[3], f[4], f[5])
    }

    /** What [plan] decided: the values to write, and the date the file gets. */
    data class Plan(val writes: List<Update>, val date: Long)

    /**
     * The core's rule (cvar::IConfigVarUpdate::ApplyUpdates): a file without a date gets no
     * updates; for each update newer than the file, a present value equal to the old default takes
     * the new one; an absent value already means the new default. [read] gives the value in the
     * file, or null. Null when nothing is to change.
     */
    fun plan(updates: List<Update>, read: (section: String, name: String) -> String?): Plan? {
        val fileDate = read(SECTION, DATE_KEY)?.trim()?.toLongOrNull() ?: return null
        if (fileDate <= 0) return null
        val newer = updates.filter { it.date > fileDate }
        if (newer.isEmpty()) return null
        val writes = newer.filter { u ->
            val value = read(u.section, u.name) ?: return@filter false
            sameValue(u.type, value, u.oldDefault) && !sameValue(u.type, value, u.newDefault)
        }
        // Later updates of the same option win, as in the core (applied in date order).
        val last = writes.associateBy { it.section to it.name }.values.toList()
        return Plan(last, maxOf(fileDate, newer.maxOf { it.date }))
    }

    private fun sameValue(type: String, a: String, b: String): Boolean = when (type) {
        "bool" -> a.trim().equals(b.trim(), ignoreCase = true)
        "int" -> a.trim().toLongOrNull()?.let { it == b.trim().toLongOrNull() } ?: false
        "double" -> a.trim().toDoubleOrNull()?.let { it == b.trim().toDoubleOrNull() } ?: false
        else -> a == b
    }

    /**
     * Whether [u]'s new value can be stored with its type: the native writer types a value by its
     * shape, so a string that looks like a bool or a number, or an int beyond int32, can't. Dating
     * the file past an update left unwritten would lose it, so then nothing is applied here and the
     * core goes on applying them in memory.
     */
    fun writable(u: Update): Boolean = when (u.type) {
        "bool" -> u.newDefault.trim().let { it == "true" || it == "false" }
        "int" -> u.newDefault.trim().toLongOrNull()?.let { it in Int.MIN_VALUE..Int.MAX_VALUE } ?: false
        "double" -> u.newDefault.trim().toDoubleOrNull() != null
        else -> u.newDefault != "true" && u.newDefault != "false" &&
            !u.newDefault.matches(Regex("-?[0-9]+(\\.[0-9]*)?"))
    }

    /** [text] with the updates applied and dated, or null when it needs no change. */
    fun migrate(text: String, updates: List<Update>): String? {
        val handle = ConfigHandle.openString(text)
        try {
            val plan = plan(updates) { section, name -> handle.getString(section, name) } ?: return null
            if (plan.date > Int.MAX_VALUE || !plan.writes.all(::writable)) return null
            for (u in plan.writes) {
                when (u.type) {
                    "bool" -> handle.putBool(u.section, u.name, u.newDefault.trim() == "true")
                    "int" -> handle.putInt(u.section, u.name, u.newDefault.trim().toInt())
                    "double" -> handle.putDouble(u.section, u.name, u.newDefault.trim().toDouble())
                    else -> handle.putString(u.section, u.name, u.newDefault)
                }
            }
            handle.putInt(SECTION, DATE_KEY, plan.date.toInt())
            return handle.closeString()
        } finally {
            handle.closeDiscard()
        }
    }

    private val TYPES = setOf("bool", "int", "double", "string")
}
