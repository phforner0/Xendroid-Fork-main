package xendroid.compose.settings

import android.util.Log
import java.io.File

/**
 * The SPARSE per-game override store for a single title id. Native applies
 * <app_data_dir>/config/<TITLE_ID>.config.toml as an overlay on top of the global
 * config at boot (config.cc LoadGameConfig/ReadGameConfig — only physically present
 * keys override). Flush patches only edited keys into the latest native TOML table,
 * preserving unknown cvars, arrays and nested tables as well as other writers' edits.
 */
class GameSettingsRepository(private val store: ConfigStore, val titleId: String) {
    val persistenceKey: String get() = "game:${titleId.uppercase()}"

    /** key -> override raw String (only the keys the user explicitly set for THIS game). */
    private val overrides = mutableMapOf<String, String>()
    /** Null is an explicit removal, not "missing from the editor schema". */
    private val pending = mutableMapOf<String, String?>()
    /** key -> effective global value (the inherited value), captured once at open. */
    private var globalValues: Map<String, String?> = emptyMap()
    private var opened = false

    val isCustomDriverSupported: Boolean
        get() = runCatching { File("/dev/kgsl-3d0").exists() }.getOrDefault(false)

    @Synchronized
    fun open() {
        if (opened) return
        // Never reset a malformed override. Report it; an editor cannot safely patch it.
        val h = store.openGameConfig(titleId)
        val loaded = mutableMapOf<String, String>()
        try {
            SettingsSchema.allSettings.forEach { s ->
                h.getString(s.section, s.name)?.let { loaded[s.key] = it }
            }
        } finally {
            h.closeDiscard()
        }
        overrides.clear()
        overrides.putAll(loaded)
        // 2) snapshot the global effective values (the INHERITED display). Must not throw -- if it
        //    did, globalValues would stay empty and every row would show the schema default instead
        //    of the real global value.
        runCatching {
            val snap = store.openLiveSnapshot()
            globalValues = SettingsSchema.allSettings.associate { s ->
                s.key to snap.getString(s.section, s.name)
            }
            snap.closeDiscard()
        }.onFailure { Log.w("GameSettingsRepo", "global snapshot failed", it) }
        opened = true
    }

    @Synchronized
    fun ensureOpen() { if (!opened) open() }

    @Synchronized
    fun reload() {
        if (pending.isEmpty()) opened = false
        ensureOpen()
    }

    @Synchronized
    fun isOverridden(s: Setting): Boolean = overrides.containsKey(s.key)

    /** Raw override value for [s] if overridden, else null. Lets the per-game observable carry the
     *  value so a value-only edit changes the snapshot (containsKey still means "overridden"). */
    @Synchronized
    fun rawOverride(s: Setting): String? = overrides[s.key]

    private fun inheritedRaw(s: Setting): String =
        globalValues[s.key] ?: schemaDefaultString(s)

    // ---- typed reads: override value if present, else inherited ----
    @Synchronized
    fun boolOf(s: Setting.Bool): Boolean =
        ConfigValueShape.parseBool(overrides[s.key] ?: inheritedRaw(s), s.default)
    @Synchronized
    fun intOf(s: Setting.IntRange): Int =
        ConfigValueShape.parseInt(overrides[s.key] ?: inheritedRaw(s), s.default)
    @Synchronized
    fun listValueOf(s: Setting.ListChoice): String =
        ConfigValueShape.listOption(s.options.map { it.value }, overrides[s.key] ?: inheritedRaw(s)) ?: s.default
    @Synchronized
    fun driverPathOf(s: Setting.Action): String = overrides[s.key] ?: inheritedRaw(s)
    @Synchronized
    fun textOf(s: Setting.Text): String = overrides[s.key] ?: inheritedRaw(s)
    @Synchronized
    fun inheritedLabel(s: Setting): String = labelFor(s, inheritedRaw(s))

    // ---- writes: mutate the in-memory map only; persisted on flush ----
    @Synchronized
    private fun set(s: Setting, raw: String) {
        ensureOpen()
        overrides[s.key] = raw
        pending[s.key] = raw
    }
    fun setBool(s: Setting.Bool, v: Boolean) = set(s, ConfigValueShape.bool(v))
    fun setInt(s: Setting.IntRange, v: Int) {
        set(s, ConfigValueShape.int(v.coerceIn(s.min, s.max)))
    }
    fun setListValue(s: Setting.ListChoice, value: String) = set(s, value)
    fun setDriverPath(s: Setting.Action, value: String) = set(s, value)
    fun setText(s: Setting.Text, value: String) = set(s, value)

    /** Turn override ON: seed from the current inherited value. OFF: drop the key. */
    @Synchronized
    fun setOverride(s: Setting, on: Boolean) {
        ensureOpen()
        if (on) {
            if (!overrides.containsKey(s.key)) set(s, inheritedRaw(s))
        } else {
            overrides.remove(s.key)
            pending[s.key] = null
        }
    }

    /** Patches are cleared only after a successful atomic commit; errors are retryable. */
    @Synchronized
    fun flush() {
        if (!opened || pending.isEmpty()) return
        store.editGameConfig(titleId) { handle ->
            for (s in SettingsSchema.allSettings) {
                if (pending.containsKey(s.key)) {
                    val raw = pending[s.key]
                    if (raw == null) handle.remove(s.section, s.name)
                    else handle.putSetting(s, raw)
                }
            }
        }
        pending.clear()
        reload()
    }

    /** C05: this game's own values (key -> raw), as a profile plan compares them. */
    @Synchronized
    fun overrideValues(): Map<String, String> { ensureOpen(); return overrides.toMap() }

    /** C05: the global values this game follows where it has none of its own. */
    @Synchronized
    fun inheritedValues(): Map<String, String?> { ensureOpen(); return SettingsSchema.allSettings.associate { it.key to inheritedRaw(it) } }

    /** A reviewed plan no longer matches the file: someone changed this game's settings since. */
    class StalePlanException : IllegalStateException("This game's settings changed since the preview")

    /**
     * C05: writes a reviewed profile plan (key -> raw, null = remove) under the file's lock.
     * [expected] is this game's value of each key when the plan was shown; if the file says
     * otherwise now, nothing is written. Unsaved edits are saved first, so they are never lost
     * and the plan never silently overwrites them.
     */
    @Synchronized
    fun applyPlan(expected: Map<String, String?>, writes: Map<String, String?>) {
        flush()
        store.editGameConfig(titleId) { handle ->
            for ((key, value) in expected) {
                val s = SettingsSchema.byKey[key] ?: throw StalePlanException()
                if (handle.getString(s.section, s.name) != value) throw StalePlanException()
            }
            for ((key, raw) in writes) {
                val s = SettingsSchema.byKey[key] ?: throw StalePlanException()
                if (raw == null) handle.remove(s.section, s.name) else handle.putSetting(s, raw)
            }
        }
        opened = false
        ensureOpen()
    }

    private fun schemaDefaultString(s: Setting): String = when (s) {
        is Setting.Bool       -> if (s.default) "true" else "false"
        is Setting.IntRange   -> s.default.toString()
        is Setting.ListChoice -> s.default
        is Setting.Action     -> s.default
        is Setting.Text       -> s.default
    }

    private fun labelFor(s: Setting, raw: String): String = when (s) {
        is Setting.ListChoice -> s.options.firstOrNull { it.value == raw }?.label
            ?: raw.ifEmpty { "(default)" }
        else -> raw
    }
}
