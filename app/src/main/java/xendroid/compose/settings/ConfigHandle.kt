package xendroid.compose.settings

import xendroid.emulator.Emulator

/** Build a "Section|name" tag (single '|' separator the native side splits on). */
fun tag(section: String, name: String): String = "$section|$name"

/**
 * Single-use wrapper over [Emulator.Config], parsed from TOML text. [closeString]
 * returns the serialized TOML; [closeDiscard] only frees it. Closing deletes the
 * native table — the handle is dangling afterwards, so this class guards against
 * reuse and double-close. Disk writes belong to [ConfigStore]'s locked, atomic
 * edits, never to a handle.
 */
class ConfigHandle private constructor(
    private val config: Emulator.Config,
) {
    @Volatile private var closed = false

    private fun checkOpen() = check(!closed) { "ConfigHandle already closed (single-use)" }

    fun getString(section: String, name: String): String? {
        checkOpen()
        return config.load_config_entry(tag(section, name))  // null = unset
    }

    fun getBool(section: String, name: String, def: Boolean): Boolean =
        ConfigValueShape.parseBool(getString(section, name), def)

    /** Native ints come back via std::to_string; tolerate a trailing ".0..0" from
     *  a value that round-tripped as double. */
    fun getInt(section: String, name: String, def: Int): Int =
        ConfigValueShape.parseInt(getString(section, name), def)

    fun getDouble(section: String, name: String, def: Double): Double =
        getString(section, name)?.toDoubleOrNull() ?: def

    // ---- Type-preserving puts (emit the canonical shape the native re-infers) ----

    fun putBool(section: String, name: String, v: Boolean) =
        save(section, name, ConfigValueShape.bool(v))

    /** Caller guarantees [v] fits int32; values beyond that store as a STRING natively. */
    fun putInt(section: String, name: String, v: Int) =
        save(section, name, ConfigValueShape.int(v))

    /** Always include a '.' so it round-trips as a TOML double, never an int. */
    fun putDouble(section: String, name: String, v: Double) =
        save(section, name, ConfigValueShape.double(v))

    /** Enums / paths / list values: stored verbatim as a string. */
    fun putString(section: String, name: String, v: String) = save(section, name, v)

    fun remove(section: String, name: String) {
        checkOpen()
        config.remove_config_entry(tag(section, name))
    }

    fun isEmpty(): Boolean {
        checkOpen()
        return config.is_empty()
    }

    fun putSetting(s: Setting, raw: String) = when (s) {
        is Setting.Bool -> putBool(s.section, s.name, ConfigValueShape.parseBool(raw, s.default))
        is Setting.IntRange -> putInt(s.section, s.name, ConfigValueShape.parseInt(raw, s.default))
        is Setting.ListChoice -> putString(s.section, s.name, raw)
        is Setting.Action -> putString(s.section, s.name, raw)
    }

    private fun save(section: String, name: String, value: String) {
        checkOpen()
        config.save_config_entry(tag(section, name), value)
    }

    /** Serialize + free. Returns TOML text. */
    fun closeString(): String {
        check(!closed) { "already closed" }
        closed = true
        return config.close_config()
    }

    /** Free without serializing or writing - for read-only use of any handle. */
    fun closeDiscard() {
        if (closed) return
        closed = true
        config.free_config()
    }

    companion object {
        /** @throws Emulator.ConfigFileException on a parse error. */
        @Throws(Emulator.ConfigFileException::class)
        fun openString(tomlText: String): ConfigHandle =
            ConfigHandle(Emulator.Config.open_config_from_string(tomlText))
    }
}
