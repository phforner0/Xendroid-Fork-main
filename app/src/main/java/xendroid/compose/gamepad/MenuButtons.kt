package xendroid.compose.gamepad

import android.content.Context
import android.view.KeyEvent
import androidx.core.content.edit

/**
 * U04: what a controller or keyboard key means in the app's own menus: the library, the
 * in-game menu, the touch layout editor. Never applied to the game's input. With [swapConfirm]
 * B confirms and A goes back (the Nintendo layout); keyboard keys never swap.
 */
object MenuButtons {
    enum class Intent { CONFIRM, CANCEL, PREVIOUS, NEXT, PAGE_PREVIOUS, PAGE_NEXT, MENU }

    fun intentOf(keyCode: Int, swapConfirm: Boolean): Intent? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> if (swapConfirm) Intent.CANCEL else Intent.CONFIRM
        KeyEvent.KEYCODE_BUTTON_B -> if (swapConfirm) Intent.CONFIRM else Intent.CANCEL
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Intent.CONFIRM
        KeyEvent.KEYCODE_ESCAPE -> Intent.CANCEL
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> Intent.PREVIOUS
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> Intent.NEXT
        KeyEvent.KEYCODE_BUTTON_L1 -> Intent.PAGE_PREVIOUS
        KeyEvent.KEYCODE_BUTTON_R1 -> Intent.PAGE_NEXT
        KeyEvent.KEYCODE_BUTTON_MODE -> Intent.MENU
        else -> null
    }

    /** For screens driven by Compose focus: the key a controller's A or B stands for (click or
     *  back); null for every other key, which passes unchanged. */
    fun frontendKey(keyCode: Int, swapConfirm: Boolean): Int? = when (intentOf(keyCode, swapConfirm)) {
        Intent.CONFIRM -> if (keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_BUTTON_B) KeyEvent.KEYCODE_DPAD_CENTER else null
        Intent.CANCEL -> if (keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_BUTTON_B) KeyEvent.KEYCODE_BACK else null
        else -> null
    }

    /** -1, 1, or 0 when [keyCode] is not a direction. */
    fun direction(keyCode: Int): Int = when (intentOf(keyCode, swapConfirm = false)) {
        Intent.PREVIOUS -> -1
        Intent.NEXT -> 1
        else -> 0
    }
}

/**
 * U04: a held direction moves once at once, again after [initialDelayMs], then every
 * [intervalMs]: lists scroll at a pace the eye can follow instead of the key repeat rate (or
 * not at all, as a held stick did). Time is passed in, so the pace is tested.
 */
class NavRepeat(private val initialDelayMs: Long = 350, private val intervalMs: Long = 120) {
    private var direction = 0
    private var nextAt = Long.MAX_VALUE

    /** [dir] (-1 or 1) is held at [nowMs] (a key down or repeat, or a poll while a stick is
     *  pushed); true when the selection should move now. */
    fun press(dir: Int, nowMs: Long): Boolean {
        if (dir == 0) { release(); return false }
        if (dir != direction) {
            direction = dir
            nextAt = nowMs + initialDelayMs
            return true
        }
        if (nowMs < nextAt) return false
        nextAt = nowMs + intervalMs
        return true
    }

    fun release() {
        direction = 0
        nextAt = Long.MAX_VALUE
    }
}

/** U04: the menu button layout, an app preference (read once per game process). */
object MenuButtonPrefs {
    private const val PREFS = "controller_nav"
    private const val SWAP = "swap_confirm"

    fun swapConfirm(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SWAP, false)

    fun setSwapConfirm(context: Context, swap: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(SWAP, swap) }
    }
}
