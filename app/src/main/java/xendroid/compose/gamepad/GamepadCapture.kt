package xendroid.compose.gamepad

import android.view.InputEvent

/**
 * U05: while the controller test is open, the main activity hands it every input event
 * first and raw (no A/B translation); events it takes reach no other screen. Main thread only.
 */
object GamepadCapture {
    @Volatile var listener: ((InputEvent) -> Boolean)? = null
}
