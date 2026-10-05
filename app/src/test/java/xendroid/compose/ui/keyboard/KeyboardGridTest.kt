package xendroid.compose.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test
import xendroid.compose.ui.keyboard.KeyboardGrid.Outcome
import xendroid.compose.ui.keyboard.KeyboardGrid.Shift

class KeyboardGridTest {
    private fun KeyboardGrid.pressed() = press().first
    /** Moves the highlight to the key labelled [label] on the current page. */
    private fun KeyboardGrid.at(label: String): KeyboardGrid {
        rows.forEachIndexed { r, row -> row.forEachIndexed { c, key -> if (key.label == label) return copy(row = r, col = c) } }
        error("no key $label")
    }

    @Test fun theHighlightWrapsAndKeepsItsColumn() {
        val grid = KeyboardGrid()                                    // starts on "q"
        assertEquals("q", grid.highlighted.label)
        assertEquals("p", grid.move(-1, 0).highlighted.label)        // wraps left
        assertEquals("1", grid.move(0, -1).highlighted.label)
        // Down to the 8-key command row keeps the nearest column, and up again too.
        val far = grid.at("p").move(0, 3)
        assertEquals(GridKey.Command.CANCEL, far.highlighted)
        assertEquals("i", far.move(0, 2).highlighted.label)
        assertEquals("8", far.move(0, 1).highlighted.label)
    }

    @Test fun lettersShiftOnceOrLockAndSymbolsHaveTheirPage() {
        var grid = KeyboardGrid()
        grid = grid.at("Shift").pressed()                            // once
        grid = grid.at("h").pressed().at("i").pressed()
        assertEquals("Hi", grid.text)
        grid = grid.at("Shift").pressed().at("Shift").pressed()      // locked
        assertEquals(Shift.LOCK, grid.shift)
        grid = grid.at("o").pressed().at("k").pressed()
        assertEquals("HiOK", grid.text)
        grid = grid.at("?123").pressed()
        assertEquals("!", grid.at("!").highlighted.label)
        grid = grid.at("!").pressed().at("Space").pressed()
        assertEquals("HiOK! ", grid.text)
        assertEquals(false, grid.togglePage().symbols)
    }

    @Test fun theLimitCountsUtf16AndPairsStayWhole() {
        var grid = KeyboardGrid(maxUnits = 3).type("ab")
        assertEquals("ab", grid.type("😀").text)                       // 2 units do not fit in 1
        grid = KeyboardGrid(maxUnits = 4).type("a😀b")
        assertEquals("a😀b", grid.text)
        grid = grid.caretLeft()                                       // before "b"
        grid = grid.backspace()                                       // deletes the whole emoji
        assertEquals("ab", grid.text)
        assertEquals(1, grid.caret)
        assertEquals(0, KeyboardGrid(text = "😀", caret = 2).caretLeft().caret)
        assertEquals(2, KeyboardGrid(text = "😀", caret = 0).caretRight().caret)
        // What the system keyboard typed is clamped the same way, caret off the middle of a pair.
        val typed = KeyboardGrid(maxUnits = 3).withText("ab😀c", newCaret = 3)
        assertEquals("ab", typed.text)
        assertEquals(0, KeyboardGrid().withText("😀x", newCaret = 1).caret)       // not inside the pair
    }

    @Test fun editingHappensAtTheCaretAndTheAnswerIsExplicit() {
        var grid = KeyboardGrid(text = "ac", caret = 1)
        grid = grid.type("b")
        assertEquals("abc", grid.text)
        assertEquals(2, grid.caret)
        assertEquals("ac", grid.backspace().text)
        assertEquals(Outcome.DONE, grid.at("Done").press().second)
        assertEquals(Outcome.CANCEL, grid.at("Cancel").press().second)
        assertEquals(Outcome.NONE, grid.at("a").press().second)
        assertEquals(grid, grid.copy(caret = 0).backspace().copy(caret = 2))  // nothing before the caret
    }

    @Test fun loneSurrogatesNeverReachTheGame() {
        assertEquals("a�b", KeyboardGrid.sanitize("a\uD83Db"))
        assertEquals("�x", KeyboardGrid.sanitize("\uDE00x"))
        assertEquals("ok😀", KeyboardGrid.sanitize("ok😀"))
        assertEquals("�", KeyboardGrid.sanitize("\uD83D"))
    }
}
