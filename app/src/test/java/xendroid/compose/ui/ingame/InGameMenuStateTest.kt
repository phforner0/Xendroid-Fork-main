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

    @Test fun playerModeHidesDeveloperActionsFromNavigation() {
        val player = InGameMenuState(developer = false).show(pause = true)
        val graphics = player.actions(InGamePage.GRAPHICS)
        assertTrue(graphics.none { it in developerActions })
        assertTrue(InGameAction.WINFG !in graphics && InGameAction.PERFORMANCE_HINTS !in graphics)
        assertTrue(InGameAction.FPS_60 in graphics && InGameAction.SCALING_EFFECT in graphics)
        assertEquals(graphics.size, player.count)
        // Wrapping navigation stays inside the shown actions.
        assertEquals(graphics.last(), player.move(-1).action)
        assertEquals(InGameMenuState().actions(InGamePage.GRAPHICS), inGamePageActions.getValue(InGamePage.GRAPHICS))
        assertTrue(player.actions(InGamePage.HUD).none { it == InGameAction.HUD_HOST_SUBMISSIONS })
        assertTrue(InGameAction.PHONE_CONTROLLERS in player.actions(InGamePage.CONTROLS))
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
