package xendroid.compose.settings

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Round 2: what the in-game menu changes, kept for the game as it happens (the player asked that a
 * session's changes survive it). Each key the session touches remembers what the game's own
 * settings held before, so the session's changes can be undone, or made every game's.
 *
 * Keys are config keys ("Section|name", written to the game's TOML the way the Settings screens
 * write them) or app keys ([APP] + name: this app's own per-game preferences, such as the display
 * mode, which the core has no setting for). Pure over [Store], tested on the JVM.
 */
class InGameChanges(private val store: Store) {
    interface Store {
        /** The game's own raw value of [key]; null when it has none (it inherits the global one). */
        fun gameValue(key: String): String?
        /** Writes [values] as the game's own; a null value removes it (the game inherits again). */
        fun writeGame(values: Map<String, String?>)
        /** Writes [values] as every game's (the global config and preferences). */
        fun writeGlobal(values: Map<String, String>)
    }

    private val before = LinkedHashMap<String, String?>()
    private val now = LinkedHashMap<String, String?>()

    /** The keys this session changed, with their values now (null: back to inheriting). */
    val changed: Map<String, String?> get() = now.filter { (key, value) -> before[key] != value }

    /** How many settings this session changed (one changed and changed back does not count). */
    val count: Int get() = changed.size

    /** What [key] held for the game when this session first changed it; [key] untouched: its value now. */
    fun original(key: String): String? = if (key in before) before[key] else store.gameValue(key)

    /** [key] is now [raw] (null: inherit); written for the game at once when [keep]. */
    fun change(key: String, raw: String?, keep: Boolean) {
        if (key !in before) before[key] = store.gameValue(key)
        now[key] = raw
        if (keep) store.writeGame(mapOf(key to raw))
    }

    /** The keep switch turned on: what this session changed is written for the game now. */
    fun keepAll() {
        if (now.isNotEmpty()) store.writeGame(now.toMap())
    }

    /** The game's own settings as they were before this session; returns each key with the value it is back to. */
    fun undo(): Map<String, String?> {
        val restored = before.toMap()
        if (restored.isNotEmpty()) store.writeGame(restored)
        before.clear()
        now.clear()
        return restored
    }

    /** This session's values become every game's, and this game stops keeping its own copy of them. */
    fun makeGlobal(): Map<String, String?> {
        val values = changed
        val global = values.filterValues { it != null }.mapValues { it.value!! }
        if (global.isNotEmpty()) store.writeGlobal(global)
        if (values.isNotEmpty()) store.writeGame(values.keys.associateWith { null })
        before.clear()
        now.clear()
        return values
    }

    companion object {
        /** The prefix of this app's own per-game preferences among the keys. */
        const val APP = "app:"
        const val DISPLAY_MODE = APP + "display_mode"
        const val COLOR_FILTER = APP + "color_filter"
        const val REFRESH_HZ = APP + "refresh_hz"
    }
}

/**
 * Round 2: the in-game menu's own preferences. Every game's: pause when the menu opens, keep the
 * menu's changes for the game, the "how to open the menu" tip once. Per game, as [InGameChanges]'s
 * app keys: "<name>@<TITLEID>", over the global "<name>" that "Use in every game" writes.
 */
class InGamePrefs(private val prefs: SharedPreferences) {
    var pauseOnOpen: Boolean
        get() = prefs.getBoolean(PAUSE_ON_OPEN, true)
        set(value) = prefs.edit { putBoolean(PAUSE_ON_OPEN, value) }

    var autoSave: Boolean
        get() = prefs.getBoolean(AUTO_SAVE, true)
        set(value) = prefs.edit { putBoolean(AUTO_SAVE, value) }

    /** True the first time it is asked, false after: the tip shows once per install. */
    fun firstOpenTip(): Boolean {
        if (prefs.getBoolean(OPEN_TIP, false)) return false
        prefs.edit { putBoolean(OPEN_TIP, true) }
        return true
    }

    /** An app key ([InGameChanges.APP] + name) for [titleId]: the game's own, else every game's. */
    fun value(key: String, titleId: String?): String? {
        val name = key.removePrefix(InGameChanges.APP)
        return titleId?.let { prefs.getString("$name@$it", null) } ?: prefs.getString(name, null)
    }

    fun gameValue(key: String, titleId: String): String? = prefs.getString(key.removePrefix(InGameChanges.APP) + "@" + titleId, null)

    fun writeGame(titleId: String, values: Map<String, String?>) = prefs.edit {
        values.forEach { (key, value) ->
            val name = key.removePrefix(InGameChanges.APP) + "@" + titleId
            if (value == null) remove(name) else putString(name, value)
        }
    }

    fun writeGlobal(values: Map<String, String>) = prefs.edit {
        values.forEach { (key, value) -> putString(key.removePrefix(InGameChanges.APP), value) }
    }

    companion object {
        const val FILE = "ingame"
        private const val PAUSE_ON_OPEN = "pause_on_open"
        private const val AUTO_SAVE = "auto_save"
        private const val OPEN_TIP = "open_tip_shown"
    }
}

/** [InGameChanges.Store] over the game's TOML ([ConfigStore]) and [InGamePrefs]. Call off the main thread. */
class InGameChangesStore(private val config: ConfigStore, private val prefs: InGamePrefs, private val titleId: String) : InGameChanges.Store {
    override fun gameValue(key: String): String? {
        if (key.startsWith(InGameChanges.APP)) return prefs.gameValue(key, titleId)
        val (section, name) = key.split('|', limit = 2)
        val handle = config.openGameConfig(titleId)
        return try { handle.getString(section, name) } finally { handle.closeDiscard() }
    }

    override fun writeGame(values: Map<String, String?>) {
        val (app, cvars) = values.entries.partition { it.key.startsWith(InGameChanges.APP) }
        if (app.isNotEmpty()) prefs.writeGame(titleId, app.associate { it.key to it.value })
        if (cvars.isNotEmpty()) config.editGameConfig(titleId) { handle ->
            cvars.forEach { (key, raw) ->
                val (section, name) = key.split('|', limit = 2)
                if (raw == null) handle.remove(section, name) else handle.putSetting(SettingsSchema.byKey.getValue(key), raw)
            }
        }
    }

    override fun writeGlobal(values: Map<String, String>) {
        val (app, cvars) = values.entries.partition { it.key.startsWith(InGameChanges.APP) }
        if (app.isNotEmpty()) prefs.writeGlobal(app.associate { it.key to it.value })
        if (cvars.isNotEmpty()) config.editLiveConfig { handle ->
            cvars.forEach { (key, raw) -> handle.putSetting(SettingsSchema.byKey.getValue(key), raw) }
        }
    }
}
