package xendroid.compose.ui.library

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import xendroid.compose.ui.library.CarouselNav.Command

class CarouselNavTest {
    @Test fun leftAndRightWalkTheGamesAndEveryGameStartsOnPlay() {
        var nav = CarouselNav()
        nav = nav.on(CarouselKey.LEFT, 10).first
        assertEquals(0, nav.game)                                       // no wrap at the start
        nav = nav.on(CarouselKey.DOWN, 10).first
        assertEquals(CarouselAction.DETAILS, nav.action)
        nav = nav.on(CarouselKey.RIGHT, 10).first
        assertEquals(CarouselNav(1, CarouselAction.PLAY), nav)          // a new column starts on Play
        nav = nav.on(CarouselKey.PAGE_RIGHT, 10).first
        assertEquals(6, nav.game)
        nav = nav.on(CarouselKey.PAGE_RIGHT, 10).first
        assertEquals(9, nav.game)                                       // no wrap at the end
        assertEquals(4, nav.on(CarouselKey.PAGE_LEFT, 10).first.game)
    }

    @Test fun theConfirmButtonDoesTheFocusedAction() {
        val play = CarouselNav(3)
        assertEquals(Command.Launch(3), play.on(CarouselKey.CONFIRM, 5).second)
        val details = play.on(CarouselKey.DOWN, 5).first
        assertEquals(Command.Details(3), details.on(CarouselKey.CONFIRM, 5).second)
        val favorite = details.on(CarouselKey.DOWN, 5).first
        assertEquals(CarouselAction.FAVORITE, favorite.on(CarouselKey.DOWN, 5).first.action)   // stops at the last
        assertEquals(Command.ToggleFavorite(3), favorite.on(CarouselKey.CONFIRM, 5).second)
        assertEquals(Command.ToggleFavorite(3), play.on(CarouselKey.FAVORITE, 5).second)       // Y anywhere
        assertEquals(Command.Details(3), play.on(CarouselKey.DETAILS, 5).second)               // X anywhere
    }

    @Test fun backReturnsToPlayAndOnPlayIsLeftToTheApp() {
        val favorite = CarouselNav(2, CarouselAction.FAVORITE)
        val (back, command) = favorite.on(CarouselKey.CANCEL, 5)
        assertEquals(CarouselNav(2), back)
        assertEquals(Command.None, command)
        assertEquals(Command.PassBack, back.on(CarouselKey.CANCEL, 5).second)
        assertEquals(Command.SwitchView, back.on(CarouselKey.SWITCH_VIEW, 5).second)
    }

    @Test fun tapsFocusAndASecondTapLaunches() {
        val nav = CarouselNav(1)
        val (focused, first) = nav.tap(4, 6)
        assertEquals(CarouselNav(4), focused)
        assertEquals(Command.None, first)
        assertEquals(Command.Launch(4), focused.tap(4, 6).second)
        assertEquals(Command.None, nav.tap(9, 6).second)                 // a cover that is gone
    }

    @Test fun aShrunkListKeepsTheFocusInside() {
        assertEquals(2, CarouselNav(8).clamped(3).game)
        assertEquals(Command.Launch(2), CarouselNav(8).on(CarouselKey.CONFIRM, 3).second)
        assertEquals(CarouselNav(), CarouselNav(4).clamped(0))
        assertEquals(Command.None, CarouselNav(4).on(CarouselKey.CONFIRM, 0).second)
    }

    @Test fun keysFollowTheMenuButtonLayout() {
        assertEquals(CarouselKey.CONFIRM, CarouselNav.keyOf(KeyEvent.KEYCODE_BUTTON_A, swapConfirm = false))
        assertEquals(CarouselKey.CANCEL, CarouselNav.keyOf(KeyEvent.KEYCODE_BUTTON_A, swapConfirm = true))
        assertEquals(CarouselKey.CONFIRM, CarouselNav.keyOf(KeyEvent.KEYCODE_BUTTON_B, swapConfirm = true))
        assertEquals(CarouselKey.CONFIRM, CarouselNav.keyOf(KeyEvent.KEYCODE_ENTER, swapConfirm = true))
        assertEquals(CarouselKey.PAGE_RIGHT, CarouselNav.keyOf(KeyEvent.KEYCODE_BUTTON_R1, false))
        assertEquals(CarouselKey.SWITCH_VIEW, CarouselNav.keyOf(KeyEvent.KEYCODE_BUTTON_SELECT, false))
        assertNull(CarouselNav.keyOf(KeyEvent.KEYCODE_VOLUME_UP, false))
    }
}
