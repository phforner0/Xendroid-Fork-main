package xendroid.compose.ui.library

import android.view.KeyEvent
import xendroid.compose.gamepad.MenuButtons

/** What the carousel lists under the focused cover, top to bottom (the XMB's column). */
enum class CarouselAction { PLAY, DETAILS, FAVORITE }

/** A key as the carousel reads it. */
enum class CarouselKey { LEFT, RIGHT, UP, DOWN, PAGE_LEFT, PAGE_RIGHT, CONFIRM, CANCEL, FAVORITE, DETAILS, SWITCH_VIEW }

/**
 * The library as a bar of covers (Bannerlator's XMB 16c292d2, Eden's carousel 61ffb309):
 * left/right walk the games, LB/RB jump [PAGE], up/down walk the focused game's actions
 * (Play first), A does the action, B goes back to Play (and on Play is not the carousel's:
 * Back works as everywhere), Y toggles the favorite, X or Menu opens the details, View/Select
 * switches back to the grid. Pure: the screen feeds keys and taps and draws [game] and [action].
 */
data class CarouselNav(val game: Int = 0, val action: CarouselAction = CarouselAction.PLAY) {
    sealed interface Command {
        data object None : Command
        data class Launch(val index: Int) : Command
        data class Details(val index: Int) : Command
        data class ToggleFavorite(val index: Int) : Command
        data object SwitchView : Command
        /** B on Play: not the carousel's key. */
        data object PassBack : Command
    }

    fun clamped(count: Int): CarouselNav = if (count <= 0) CarouselNav() else copy(game = game.coerceIn(0, count - 1))

    /** A tap on cover [index]: the focused one launches, another one is focused. */
    fun tap(index: Int, count: Int): Pair<CarouselNav, Command> {
        if (index !in 0 until count) return this to Command.None
        return if (index == game) this to Command.Launch(index) else CarouselNav(index) to Command.None
    }

    fun on(key: CarouselKey, count: Int): Pair<CarouselNav, Command> {
        if (count <= 0) return CarouselNav() to (if (key == CarouselKey.SWITCH_VIEW) Command.SwitchView else Command.None)
        val at = clamped(count)
        val actions = CarouselAction.entries
        return when (key) {
            CarouselKey.LEFT -> at.moveTo(at.game - 1, count) to Command.None
            CarouselKey.RIGHT -> at.moveTo(at.game + 1, count) to Command.None
            CarouselKey.PAGE_LEFT -> at.moveTo(at.game - PAGE, count) to Command.None
            CarouselKey.PAGE_RIGHT -> at.moveTo(at.game + PAGE, count) to Command.None
            CarouselKey.UP -> at.copy(action = actions[(at.action.ordinal - 1).coerceAtLeast(0)]) to Command.None
            CarouselKey.DOWN -> at.copy(action = actions[(at.action.ordinal + 1).coerceAtMost(actions.size - 1)]) to Command.None
            CarouselKey.CONFIRM -> at to when (at.action) {
                CarouselAction.PLAY -> Command.Launch(at.game)
                CarouselAction.DETAILS -> Command.Details(at.game)
                CarouselAction.FAVORITE -> Command.ToggleFavorite(at.game)
            }
            CarouselKey.CANCEL ->
                if (at.action != CarouselAction.PLAY) at.copy(action = CarouselAction.PLAY) to Command.None
                else at to Command.PassBack
            CarouselKey.FAVORITE -> at to Command.ToggleFavorite(at.game)
            CarouselKey.DETAILS -> at to Command.Details(at.game)
            CarouselKey.SWITCH_VIEW -> at to Command.SwitchView
        }
    }

    /** Another game starts on Play, as a new column does in the XMB. */
    private fun moveTo(index: Int, count: Int): CarouselNav {
        val target = index.coerceIn(0, count - 1)
        return if (target == game) this else CarouselNav(target)
    }

    companion object {
        const val PAGE = 5

        /** The carousel's reading of a key (U04: A/B follow the swap setting); null = not its key. */
        fun keyOf(keyCode: Int, swapConfirm: Boolean): CarouselKey? = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> CarouselKey.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> CarouselKey.RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> CarouselKey.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> CarouselKey.DOWN
            KeyEvent.KEYCODE_BUTTON_L1 -> CarouselKey.PAGE_LEFT
            KeyEvent.KEYCODE_BUTTON_R1 -> CarouselKey.PAGE_RIGHT
            KeyEvent.KEYCODE_BUTTON_Y -> CarouselKey.FAVORITE
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_MENU -> CarouselKey.DETAILS
            KeyEvent.KEYCODE_BUTTON_SELECT -> CarouselKey.SWITCH_VIEW
            else -> when (MenuButtons.intentOf(keyCode, swapConfirm)) {
                MenuButtons.Intent.CONFIRM -> CarouselKey.CONFIRM
                MenuButtons.Intent.CANCEL -> CarouselKey.CANCEL
                else -> null
            }
        }
    }
}
