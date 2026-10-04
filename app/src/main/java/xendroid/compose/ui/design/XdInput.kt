package xendroid.compose.ui.design

import android.content.Context
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/** How the screens are laid out: for fingers (B) or for a controller (C). */
enum class InputMode { TOUCH, CONTROLLER }

/** The player's choice in Settings → App → Interface. */
enum class InputModePref {
    /** Controller mode while a controller is connected, touch otherwise (the default). */
    AUTO,
    TOUCH,
    CONTROLLER;

    fun resolve(controllerConnected: Boolean): InputMode = when (this) {
        AUTO -> if (controllerConnected) InputMode.CONTROLLER else InputMode.TOUCH
        TOUCH -> InputMode.TOUCH
        CONTROLLER -> InputMode.CONTROLLER
    }

    companion object {
        fun parse(name: String?): InputModePref = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** Kept with the other looks of the app ([xendroid.compose.ui.theme.UiScaleStore]'s file). */
object InputModeStore {
    private const val PREFS = "ui_look"
    private const val KEY = "input_mode"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun read(prefs: SharedPreferences): InputModePref = InputModePref.parse(prefs.getString(KEY, null))
    fun read(context: Context): InputModePref = read(prefs(context))
    fun write(context: Context, pref: InputModePref) = prefs(context).edit { putString(KEY, pref.name) }
    fun isKey(key: String?) = key == KEY
}

val LocalInputMode = compositionLocalOf { InputMode.TOUCH }

/** Physical controllers (not the phone's own keys, not a virtual pad). */
object Gamepads {
    fun isController(device: InputDevice?): Boolean {
        if (device == null || device.isVirtual) return false
        val s = device.sources
        return s and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            s and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    fun anyConnected(): Boolean = InputDevice.getDeviceIds().any { isController(InputDevice.getDevice(it)) }
}

/** True while a controller is connected; follows connections and disconnections. */
@Composable
fun rememberControllerConnected(): Boolean {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(runCatching { Gamepads.anyConnected() }.getOrDefault(false)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        val listener = object : InputManager.InputDeviceListener {
            private fun update() { connected = runCatching { Gamepads.anyConnected() }.getOrDefault(false) }
            override fun onInputDeviceAdded(deviceId: Int) = update()
            override fun onInputDeviceRemoved(deviceId: Int) = update()
            override fun onInputDeviceChanged(deviceId: Int) = update()
        }
        manager?.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager?.unregisterInputDeviceListener(listener) }
    }
    return connected
}

/** The controller buttons the screens act on, beyond the focus moves and A/B that Compose and
 *  [xendroid.compose.MainActivity] already turn into clicks and back. */
enum class PadButton { X, Y, LB, RB, LT, RT, START, SELECT, L3, R3 }

/** The pad button a key-down stands for; null for any other key. Letters are never mapped:
 *  they would fire while typing in a search field. */
fun padButtonOf(event: androidx.compose.ui.input.key.KeyEvent): PadButton? {
    if (event.type != KeyEventType.KeyDown) return null
    return when (event.nativeKeyEvent.keyCode) {
        KeyEvent.KEYCODE_BUTTON_X -> PadButton.X
        KeyEvent.KEYCODE_BUTTON_Y -> PadButton.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> PadButton.LB
        KeyEvent.KEYCODE_BUTTON_R1 -> PadButton.RB
        KeyEvent.KEYCODE_BUTTON_L2 -> PadButton.LT
        KeyEvent.KEYCODE_BUTTON_R2 -> PadButton.RT
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> PadButton.START
        KeyEvent.KEYCODE_BUTTON_SELECT -> PadButton.SELECT
        KeyEvent.KEYCODE_BUTTON_THUMBL -> PadButton.L3
        KeyEvent.KEYCODE_BUTTON_THUMBR -> PadButton.R3
        else -> if (event.key == Key.PageUp) PadButton.LB else if (event.key == Key.PageDown) PadButton.RB else null
    }
}
