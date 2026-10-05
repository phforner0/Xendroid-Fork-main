package xendroid.compose.settings

import android.content.Context
import android.preference.PreferenceManager
import android.util.Log

/** A controller is attached right now, as the app's controller mode counts one
 *  ([xendroid.compose.ui.design.Gamepads]: virtual devices and the phone's own keys excluded). */
fun hasPhysicalController(): Boolean = xendroid.compose.ui.design.Gamepads.anyConnected()

/**
 * Writes HID|show_touch_overlay once per install, from whether a controller was attached:
 * with one, the overlay starts hidden, without one it starts shown. The cvar's own default
 * cannot express that, and re-deriving it every launch would fight the user's own choice
 * whenever a controller was plugged in or out. Call off the main thread, after the native
 * library is loaded.
 */
fun seedTouchOverlayDefault(context: Context, store: ConfigStore) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    if (prefs.getBoolean(KEY_SEEDED, false)) return
    val show = !hasPhysicalController()
    // Seeded before by the old check, which took a phone's own keys for a controller and hid the
    // overlay: with no controller attached now, the overlay is shown again, once.
    if (prefs.getBoolean(KEY_SEEDED_V1, false) && !show) {
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        return
    }
    runCatching {
        store.editLiveConfig { it.putBool("HID", "show_touch_overlay", show) }
    }.onFailure {
        // Leave the flag unset so the next launch retries rather than silently keeping
        // the cvar default.
        Log.w("TouchOverlay", "seeding show_touch_overlay failed", it)
        return
    }
    prefs.edit().putBoolean(KEY_SEEDED, true).apply()
}

private const val KEY_SEEDED = "touch_overlay_default_seeded_v2"
private const val KEY_SEEDED_V1 = "touch_overlay_default_seeded"
