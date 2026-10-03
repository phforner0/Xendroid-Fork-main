package xendroid.compose.settings

import kotlin.math.abs

/**
 * Pure (JNI-free) helpers encoding the native `save_config_entry` type-inference
 * contract: the native side re-infers the TOML type from the string shape, so every
 * put must emit the canonical shape and every read must tolerate the round-tripped
 * shapes. Extracted here so the contract is unit-testable without the JNI boundary.
 */
object ConfigValueShape {
    fun bool(v: Boolean) = if (v) "true" else "false"
    fun int(v: Int) = v.toString()

    /** Always include a '.' so the value round-trips as a TOML double, never an int. */
    fun double(v: Double): String { val s = v.toString(); return if (s.contains('.')) s else "$s.0" }

    fun parseBool(raw: String?, def: Boolean) = when (raw) { "true" -> true; "false" -> false; else -> def }

    /** Native ints come back via std::to_string; tolerate a value that round-tripped
     *  as a double (e.g. "8.0"). */
    fun parseInt(raw: String?, def: Int) = raw?.toIntOrNull() ?: raw?.toDoubleOrNull()?.toInt() ?: def

    /** A list's stored value as one of its [options]: a number read back from the native
     *  side ("0.100000" for "0.1") is the option with the same value; anything else stays. */
    fun listOption(options: List<String>, raw: String?): String? {
        if (raw == null || raw in options) return raw
        val number = raw.toDoubleOrNull() ?: return raw
        return options.firstOrNull { o -> o.toDoubleOrNull()?.let { abs(it - number) < 1e-6 } == true } ?: raw
    }
}
