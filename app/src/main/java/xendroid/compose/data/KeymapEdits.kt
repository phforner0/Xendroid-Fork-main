package xendroid.compose.data

/**
 * 15o (Bannerlator `4344ef49`, `4811d46c`): the rules of editing the controller mapping, over the
 * map of game button index to Android keycode (0 = unbound) that [KeymapStore] keeps. One key
 * drives one game button: the host looks a key up once, so a key bound twice would silently
 * leave one of the buttons dead. Pure, tested on the JVM.
 */
object KeymapEdits {
    /** What binding a key changes: the buttons to write, and the button it was taken from. */
    data class Bind(val changes: Map<Int, Int>, val swappedWith: Int?)

    /**
     * Binds [key] to button [index]. When another button already has [key], the two trade:
     * that button gets [index]'s previous key (or none), so nothing ends up bound twice.
     */
    fun bind(bindings: Map<Int, Int>, index: Int, key: Int): Bind {
        val previous = bindings[index] ?: 0
        if (key == 0 || key == previous) return Bind(if (key == previous) emptyMap() else mapOf(index to 0), null)
        val holder = bindings.entries.firstOrNull { it.key != index && it.value == key }?.key
        val changes = mutableMapOf(index to key)
        if (holder != null) changes[holder] = previous
        return Bind(changes, holder)
    }

    /** A, B, X and Y (indices 4-7). */
    private const val A = 4
    private const val B = 5
    private const val X = 6
    private const val Y = 7

    /**
     * For controllers laid out like Nintendo's (A on the right, B at the bottom): A trades keys
     * with B and X with Y, so the game's buttons follow the positions. Doing it again undoes it.
     */
    fun swapFaceButtons(bindings: Map<Int, Int>): Map<Int, Int> = mapOf(
        A to (bindings[B] ?: 0), B to (bindings[A] ?: 0),
        X to (bindings[Y] ?: 0), Y to (bindings[X] ?: 0),
    )

    /** Buttons that share a key with another button (left from before 15o, or edited by hand). */
    fun duplicates(bindings: Map<Int, Int>): Set<Int> =
        bindings.filterValues { it != 0 }.entries.groupBy({ it.value }, { it.key })
            .values.filter { it.size > 1 }.flatten().toSet()

    /** Buttons whose key differs from the controller's usual one. */
    fun changed(bindings: Map<Int, Int>): Set<Int> =
        GameButtons.ALL.filter { (bindings[it.index] ?: it.defaultAndroidKey) != it.defaultAndroidKey }.map { it.index }.toSet()
}
