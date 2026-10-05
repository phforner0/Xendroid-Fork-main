package xendroid.compose.settings

import android.content.Context
import androidx.core.content.edit

/**
 * L02: how much the interface shows. Player keeps the settings a player changes (resolution,
 * frame limit, language, driver, controls) and hides engine internals and experiments;
 * Developer shows everything. A mode never lifts a build gate: frame generation stays
 * debug-build only in either mode.
 */
enum class UiMode(val label: String) {
    PLAYER("Player"), DEVELOPER("Developer");

    companion object {
        fun parse(name: String?): UiMode? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The chosen mode, in the app's own preferences. The game process reads it when it starts
 * (each game is a new process), so a change applies from the next game on; no live IPC.
 */
object UiModeStore {
    private const val PREFS = "ui_mode"
    private const val KEY = "mode"

    /** Until the user picks one (the first-run assistant asks), nothing is hidden. */
    val DEFAULT = UiMode.DEVELOPER

    fun read(context: Context): UiMode =
        UiMode.parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)) ?: DEFAULT

    fun isChosen(context: Context): Boolean =
        UiMode.parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)) != null

    fun write(context: Context, mode: UiMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, mode.name) }
    }
}
