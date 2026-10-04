package xendroid.compose.gamepad

/**
 * The touch editor's undo: the state before each change, the newest [limit] kept. A change that
 * leaves the state as it was adds nothing. Pure, tested on the JVM.
 */
class EditHistory<T>(private val limit: Int = 40) {
    private val stack = ArrayDeque<T>()

    val canUndo: Boolean get() = stack.isNotEmpty()
    val size: Int get() = stack.size

    /** [before] is the state a change is about to replace. */
    fun push(before: T) {
        if (stack.lastOrNull() == before) return
        stack.addLast(before)
        while (stack.size > limit) stack.removeFirst()
    }

    /** The state before the last change, which is forgotten; null when there is none. */
    fun undo(): T? = stack.removeLastOrNull()

    fun clear() = stack.clear()
}
