package xendroid.compose.ui.keyboard

/** One key of the on-screen keyboard: a character, or a command. */
sealed interface GridKey {
    val label: String

    data class Char(val lower: String, val upper: String = lower) : GridKey {
        override val label: String get() = lower
    }

    enum class Command(override val label: String) : GridKey {
        SHIFT("Shift"), PAGE("?123"), SPACE("Space"), BACKSPACE("⌫"), LEFT("←"), RIGHT("→"), DONE("Done"), CANCEL("Cancel")
    }
}

/**
 * U10: the guest's text prompt typed with a controller (or by touch on the grid): a highlight
 * moved by D-pad/stick over a letter page and a symbol page, and the Xbox 360 shortcuts
 * (X delete, Y space, LB/RB cursor, L3 shift, R3 symbols, Start done). Immutable, like the
 * menu state, so every rule is tested on the JVM. Text is counted in UTF-16 units as the
 * guest's buffer is, and a surrogate pair is never split.
 */
data class KeyboardGrid(
    val text: String = "",
    val caret: Int = text.length,
    val maxUnits: Int = Int.MAX_VALUE,
    val symbols: Boolean = false,
    val shift: Shift = Shift.OFF,
    val row: Int = 1,
    val col: Int = 0,
) {
    enum class Shift { OFF, ONCE, LOCK }
    enum class Outcome { NONE, DONE, CANCEL }

    val rows: List<List<GridKey>> get() = if (symbols) SYMBOL_ROWS else LETTER_ROWS
    val highlighted: GridKey get() = rows[row][col]

    /** Moves the highlight; rows and columns wrap, and a shorter row keeps the nearest column. */
    fun move(dx: Int, dy: Int): KeyboardGrid {
        val r = Math.floorMod(row + dy, rows.size)
        val width = rows[r].size
        val c = if (dy != 0) col.coerceAtMost(width - 1) else Math.floorMod(col + dx, width)
        return copy(row = r, col = c)
    }

    /** Activates the highlighted key; [Outcome] says when the prompt is answered. */
    fun press(): Pair<KeyboardGrid, Outcome> = when (val key = highlighted) {
        is GridKey.Char -> type(if (shift == Shift.OFF) key.lower else key.upper) to Outcome.NONE
        GridKey.Command.SHIFT -> toggleShift() to Outcome.NONE
        GridKey.Command.PAGE -> togglePage() to Outcome.NONE
        GridKey.Command.SPACE -> type(" ") to Outcome.NONE
        GridKey.Command.BACKSPACE -> backspace() to Outcome.NONE
        GridKey.Command.LEFT -> caretLeft() to Outcome.NONE
        GridKey.Command.RIGHT -> caretRight() to Outcome.NONE
        GridKey.Command.DONE -> this to Outcome.DONE
        GridKey.Command.CANCEL -> this to Outcome.CANCEL
    }

    /** Inserts at the caret if it fits in [maxUnits]; a one-shot shift is used up by a letter. */
    fun type(chars: String): KeyboardGrid {
        if (text.length + chars.length > maxUnits) return this
        val next = copy(text = text.substring(0, caret) + chars + text.substring(caret), caret = caret + chars.length)
        return if (shift == Shift.ONCE && chars.any(Char::isLetter)) next.copy(shift = Shift.OFF) else next
    }

    /** Deletes the character before the caret (a whole surrogate pair). */
    fun backspace(): KeyboardGrid {
        if (caret == 0) return this
        val start = text.offsetByCodePoints(caret, -1)
        return copy(text = text.removeRange(start, caret), caret = start)
    }

    fun caretLeft(): KeyboardGrid = if (caret == 0) this else copy(caret = text.offsetByCodePoints(caret, -1))
    fun caretRight(): KeyboardGrid = if (caret >= text.length) this else copy(caret = text.offsetByCodePoints(caret, 1))

    /** Off → once → locked → off. */
    fun toggleShift(): KeyboardGrid = copy(shift = Shift.entries[(shift.ordinal + 1) % Shift.entries.size])

    /** Letters ⇄ symbols; the highlight stays on the same spot of the other page. */
    fun togglePage(): KeyboardGrid {
        val other = !symbols
        val width = (if (other) SYMBOL_ROWS else LETTER_ROWS)[row].size
        return copy(symbols = other, col = col.coerceAtMost(width - 1))
    }

    /** What the system keyboard (touch) typed: clamped to [maxUnits] like the grid. */
    fun withText(newText: String, newCaret: Int = newText.length): KeyboardGrid {
        val clamped = clampToUtf16Units(newText, maxUnits)
        return copy(text = clamped, caret = newCaret.coerceIn(0, clamped.length).let { safeOffset(clamped, it) })
    }

    companion object {
        private fun chars(row: String) = row.map { c -> GridKey.Char(c.toString(), c.uppercase()) }
        /** The command row, the same on both pages (the page key reads "?123" or "ABC"). */
        private val COMMANDS: List<GridKey> = GridKey.Command.entries
        val LETTER_ROWS: List<List<GridKey>> = listOf(
            chars("1234567890"), chars("qwertyuiop"), chars("asdfghjkl@"), chars("zxcvbnm,.-"), COMMANDS)
        val SYMBOL_ROWS: List<List<GridKey>> = listOf(
            chars("1234567890"), chars("!#$%&*()_+"), chars("=/\\|[]{};:"), chars("'\"<>?~`^.,"), COMMANDS)

        /** The caret never sits inside a surrogate pair. */
        private fun safeOffset(text: String, offset: Int): Int =
            if (offset in 1 until text.length && Character.isLowSurrogate(text[offset]) &&
                Character.isHighSurrogate(text[offset - 1])) offset - 1 else offset

        /** A lone surrogate (typed or pasted) becomes U+FFFD: the guest gets valid UTF-16, so
         *  valid UTF-8 on the way through JNI. */
        fun sanitize(text: String): String = buildString(text.length) {
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> {
                        append(c).append(text[i + 1]); i += 2; continue
                    }
                    Character.isSurrogate(c) -> append('�')
                    else -> append(c)
                }
                i++
            }
        }
    }
}
