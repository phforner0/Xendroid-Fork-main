package xendroid.compose.settings

/**
 * The row-editing contract the setting rows call into. Extracted so both the global
 * [SettingsViewModel] and the per-game [GameSettingsViewModel] can drive the same
 * [xendroid.compose.ui.settings.SettingRow] composables. The method set is exactly
 * the calls already present in SettingRows.kt.
 */
/** Writes [raw] through the typed setter of [s]'s kind (a value from a list, a preset, an undo). */
fun SettingsHost.setRaw(s: Setting, raw: String) = when (s) {
    is Setting.Bool -> onBoolChanged(s, ConfigValueShape.parseBool(raw, s.default))
    is Setting.IntRange -> onIntChanged(s, ConfigValueShape.parseInt(raw, s.default).coerceIn(s.min, s.max))
    is Setting.ListChoice -> onListChanged(s, raw)
    is Setting.Text -> onTextChanged(s, raw)
    is Setting.Action -> onDriverPathChanged(s, raw)
}

interface SettingsHost {
    /** Stable persistence scope for frontend metadata such as driver selection history. */
    val persistenceKey: String get() = "global"
    fun contract(s: Setting): SettingContract = settingContract(s,
        if (persistenceKey == "global") "globally" else "for this game", isCustomDriverSupported)
    val isCustomDriverSupported: Boolean
    fun currentBool(s: Setting.Bool): Boolean
    fun onBoolChanged(s: Setting.Bool, v: Boolean)
    fun currentInt(s: Setting.IntRange): Int
    fun onIntChanged(s: Setting.IntRange, v: Int)
    fun currentListValue(s: Setting.ListChoice): String
    fun onListChanged(s: Setting.ListChoice, value: String)
    fun currentDriverPath(s: Setting.Action): String
    fun onDriverPathChanged(s: Setting.Action, value: String)
    fun currentText(s: Setting.Text): String = s.default
    fun onTextChanged(s: Setting.Text, value: String) {}
}
