package xendroid.compose.gamepad

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xendroid.compose.gamepad.MenuButtons.Intent

class MenuButtonsTest {
    @Test fun aConfirmsAndBGoesBackUnlessSwapped() {
        assertEquals(Intent.CONFIRM, MenuButtons.intentOf(KeyEvent.KEYCODE_BUTTON_A, swapConfirm = false))
        assertEquals(Intent.CANCEL, MenuButtons.intentOf(KeyEvent.KEYCODE_BUTTON_B, swapConfirm = false))
        assertEquals(Intent.CANCEL, MenuButtons.intentOf(KeyEvent.KEYCODE_BUTTON_A, swapConfirm = true))
        assertEquals(Intent.CONFIRM, MenuButtons.intentOf(KeyEvent.KEYCODE_BUTTON_B, swapConfirm = true))
        // Keyboard keys never swap; Esc goes back (it used to activate the selection).
        for (swap in listOf(false, true)) {
            assertEquals(Intent.CONFIRM, MenuButtons.intentOf(KeyEvent.KEYCODE_ENTER, swap))
            assertEquals(Intent.CONFIRM, MenuButtons.intentOf(KeyEvent.KEYCODE_DPAD_CENTER, swap))
            assertEquals(Intent.CANCEL, MenuButtons.intentOf(KeyEvent.KEYCODE_ESCAPE, swap))
            assertEquals(Intent.PAGE_PREVIOUS, MenuButtons.intentOf(KeyEvent.KEYCODE_BUTTON_L1, swap))
            assertEquals(Intent.PAGE_NEXT, MenuButtons.intentOf(KeyEvent.KEYCODE_BUTTON_R1, swap))
            assertEquals(Intent.MENU, MenuButtons.intentOf(KeyEvent.KEYCODE_BUTTON_MODE, swap))
        }
        assertNull(MenuButtons.intentOf(KeyEvent.KEYCODE_BUTTON_X, false))
        assertNull(MenuButtons.intentOf(KeyEvent.KEYCODE_A, false))          // typing is not navigation
    }

    @Test fun focusScreensSeeAClickOrABack() {
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, MenuButtons.frontendKey(KeyEvent.KEYCODE_BUTTON_A, false))
        assertEquals(KeyEvent.KEYCODE_BACK, MenuButtons.frontendKey(KeyEvent.KEYCODE_BUTTON_B, false))
        assertEquals(KeyEvent.KEYCODE_BACK, MenuButtons.frontendKey(KeyEvent.KEYCODE_BUTTON_A, true))
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, MenuButtons.frontendKey(KeyEvent.KEYCODE_BUTTON_B, true))
        // Everything else reaches the screen as it is (Enter, D-pad, Y, letters...).
        for (key in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_ESCAPE)) {
            assertNull(MenuButtons.frontendKey(key, true))
        }
        assertEquals(-1, MenuButtons.direction(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(1, MenuButtons.direction(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(0, MenuButtons.direction(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test fun aHeldDirectionMovesAtAReadablePace() {
        val repeat = NavRepeat(initialDelayMs = 350, intervalMs = 120)
        // Key repeats arrive every 50 ms: a move at once, at the first one 350 ms on, then at the
        // first one 120 ms after each move.
        val moves = (0..700 step 50).filter { repeat.press(1, it.toLong()) }
        assertEquals(listOf(0, 350, 500, 650), moves)
        // Turning around moves at once and restarts the delay; letting go too.
        assertTrue(repeat.press(-1, 710))
        assertFalse(repeat.press(-1, 760))
        repeat.release()
        assertTrue(repeat.press(-1, 770))
        assertFalse(repeat.press(0, 800))
        assertTrue(repeat.press(-1, 810))
    }
}
