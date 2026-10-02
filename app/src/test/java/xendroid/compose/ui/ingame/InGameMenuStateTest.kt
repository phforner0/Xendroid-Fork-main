package xendroid.compose.ui.ingame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InGameMenuStateTest {
    @Test fun reopeningRestoresLastPageWithoutReopeningQuitConfirmation() {
        val session = InGameMenuState().show(pause = true).changePage(-1).move(2).askToQuit()
        val reopened = session.hide().show(pause = false)
        assertEquals(InGamePage.SESSION, reopened.page)
        assertEquals(InGameAction.QUIT, reopened.action)
        assertFalse(reopened.confirmingQuit)
        assertFalse(reopened.pausedByMenu)
    }
    @Test fun navigationRetainsFocusPerPageAcrossOpenings() {
        val menu = InGameMenuState().show(pause = true).move(3)
        assertEquals(InGameAction.FPS_60, menu.action)
        val hud = menu.changePage(1).move(1)
        assertEquals(InGamePage.HUD, hud.page)
        assertEquals(InGameAction.HUD_STYLE, hud.action)
        val graphics = hud.changePage(-1)
        assertEquals(3, graphics.selected)
        assertEquals(1, graphics.changePage(1).selected)
        assertEquals(3, graphics.hide().show(pause = false).selected)
        assertFalse(graphics.hide().show(pause = false).pausedByMenu)
    }

    @Test fun quitConfirmationCannotSwitchTabsAndCanBeCancelled() {
        val menu = InGameMenuState().show(pause = true)
            .changePage(-1).move(2).askToQuit()
        assertEquals(InGameAction.QUIT, menu.cancelQuit().action)
        assertEquals(InGamePage.SESSION, menu.changePage(1).page)
        assertEquals(1, menu.move(1).selected)
        assertTrue(menu.hide().show(pause = false).open)
        assertFalse(menu.hide().show(pause = false).confirmingQuit)
    }
}
