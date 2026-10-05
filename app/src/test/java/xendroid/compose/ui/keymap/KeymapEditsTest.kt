package xendroid.compose.ui.keymap

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xendroid.compose.data.GameButtons
import xendroid.compose.data.KeymapEdits

class KeymapEditsTest {
    private val usual = GameButtons.ALL.associate { it.index to it.defaultAndroidKey }

    @Test fun aFreeKeyIsJustBound() {
        val bind = KeymapEdits.bind(usual, index = 4, key = KeyEvent.KEYCODE_SPACE)
        assertEquals(mapOf(4 to KeyEvent.KEYCODE_SPACE), bind.changes)
        assertNull(bind.swappedWith)
    }

    @Test fun aKeyAnotherButtonHadTradesPlaces() {
        // Giving A the key of B: B takes A's old key, so no key drives two buttons.
        val bind = KeymapEdits.bind(usual, index = 4, key = KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(mapOf(4 to KeyEvent.KEYCODE_BUTTON_B, 5 to KeyEvent.KEYCODE_BUTTON_A), bind.changes)
        assertEquals(5, bind.swappedWith)
        val after = usual + bind.changes
        assertTrue(KeymapEdits.duplicates(after).isEmpty())
        // From an unbound button, the other one is left unbound.
        val fromUnbound = KeymapEdits.bind(usual + (8 to 0), index = 8, key = KeyEvent.KEYCODE_BUTTON_START)
        assertEquals(mapOf(8 to KeyEvent.KEYCODE_BUTTON_START, 9 to 0), fromUnbound.changes)
    }

    @Test fun theSameKeyChangesNothing() {
        assertEquals(emptyMap<Int, Int>(), KeymapEdits.bind(usual, 4, KeyEvent.KEYCODE_BUTTON_A).changes)
    }

    @Test fun nintendoLayoutSwapsAndSwapsBack() {
        val swapped = usual + KeymapEdits.swapFaceButtons(usual)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, swapped[4])
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, swapped[5])
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, swapped[6])
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, swapped[7])
        assertEquals(setOf(4, 5, 6, 7), KeymapEdits.changed(swapped))
        assertEquals(usual, swapped + KeymapEdits.swapFaceButtons(swapped))
    }

    @Test fun sharedKeysAndChangesAreFound() {
        val twice = usual + (10 to KeyEvent.KEYCODE_BUTTON_R1)          // LB bound to RB's key
        assertEquals(setOf(10, 11), KeymapEdits.duplicates(twice))
        assertEquals(setOf(10), KeymapEdits.changed(twice))
        // Unbound buttons share nothing.
        assertTrue(KeymapEdits.duplicates(usual + (0 to 0) + (1 to 0)).isEmpty())
        assertTrue(KeymapEdits.changed(usual).isEmpty())
    }

    @Test fun everyGameButtonIsDrawnOnceAndNoneCoversAnother() {
        val spots = DrawnController.SPOTS
        assertEquals(GameButtons.ALL.map { it.index }.toSet(), spots.map { it.index }.toSet())
        assertEquals(spots.size, spots.map { it.index }.toSet().size)
        spots.forEach { spot ->
            // Inside the drawing, and its own center finds it.
            assertTrue("${spot.index} inside", spot.x - spot.w / 2 >= 0f && spot.x + spot.w / 2 <= 1f &&
                spot.y - spot.h / 2 >= 0f && spot.y + spot.h / 2 <= 1f)
            assertEquals(spot.index, DrawnController.at(spot.x, spot.y))
        }
        // No two buttons overlap: sample each one's area and ask who is there.
        for (spot in spots) for (i in -4..4) for (j in -4..4) {
            val x = spot.x + spot.w / 2 * i / 4.5f
            val y = spot.y + spot.h / 2 * j / 4.5f
            if (spot.contains(x, y)) assertEquals("(${x}, ${y})", listOf(spot.index), spots.filter { it.contains(x, y) }.map { it.index })
        }
        // Between the buttons there is nothing to press.
        assertNull(DrawnController.at(0.5f, 0.6f))
        assertEquals("A", DrawnController.mark(4))
        assertEquals("", DrawnController.mark(1))
    }
}
