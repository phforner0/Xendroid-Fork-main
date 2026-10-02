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
        assertEquals(InGameAction.DISPLAY_INTEGER, menu.action)
        val system = menu.changePage(1).move(3)
        assertEquals(InGamePage.SYSTEM, system.page)
        assertEquals(InGameAction.FPS_60, system.action)
        val graphics = system.changePage(-1)
        assertEquals(3, graphics.selected)
        assertEquals(3, graphics.changePage(1).selected)
        assertEquals(3, graphics.hide().show(pause = false).selected)
        assertFalse(graphics.hide().show(pause = false).pausedByMenu)
    }

    /** U01: nothing gets lost in the regrouping, and nothing shows twice. */
    @Test fun everyOptionLivesOnExactlyOneTab() {
        val placed = inGamePageActions.values.flatten()
        assertEquals(placed.size, placed.toSet().size)
        assertEquals(InGameAction.entries.toSet() - InGameAction.MORE_OPTIONS, placed.toSet())
        assertTrue(advancedActions.all { it in placed })
        // The tabs hold what the plan gives them.
        assertTrue(inGamePageActions.getValue(InGamePage.GRAPHICS).containsAll(listOf(InGameAction.DISPLAY_FIT, InGameAction.WINFG,
            InGameAction.DRIVER_INFO)))
        assertTrue(inGamePageActions.getValue(InGamePage.SYSTEM).containsAll(listOf(InGameAction.FPS_60,
            InGameAction.SUSTAINED_PERFORMANCE, InGameAction.PERFORMANCE_HUD)))
        assertTrue(inGamePageActions.getValue(InGamePage.CONTROLS).containsAll(listOf(InGameAction.EDIT_TOUCH_LAYOUT,
            InGameAction.PHONE_CONTROLLERS, InGameAction.GYRO_CAMERA)))
        assertTrue(inGamePageActions.getValue(InGamePage.SESSION).containsAll(listOf(InGameAction.RESUME,
            InGameAction.SHARE_LOGS, InGameAction.QUIT)))
    }

    @Test fun moreOptionsOpenInPlaceAndPerTab() {
        val menu = InGameMenuState().show(pause = true)
        val closed = menu.actions(InGamePage.SYSTEM)
        assertEquals(InGameAction.MORE_OPTIONS, closed.last())
        assertTrue(closed.none { it in advancedActions })
        val toggle = closed.indexOf(InGameAction.MORE_OPTIONS)
        val open = menu.changePage(1).select(toggle).toggleAdvanced()
        assertEquals(InGameAction.MORE_OPTIONS, open.action)                       // still on the toggle
        assertEquals(toggle, open.selected)
        assertEquals(closed, open.actions().take(closed.size))                      // common ones unchanged
        assertTrue(InGameAction.HUD_CPU in open.actions() && InGameAction.SAVE_GLOBAL_FPS in open.actions())
        assertEquals(9, open.advancedCount())                                      // global save, power, 6 HUD items
        assertEquals(closed.size + 9, open.count)
        // Other tabs keep theirs closed; closing again hides them.
        assertTrue(open.actions(InGamePage.GRAPHICS).none { it in advancedActions })
        assertEquals(closed, open.toggleAdvanced().actions())
        // A selection inside the advanced list does not point past the end once closed.
        val deep = open.select(open.count - 1)
        assertEquals(toggle, deep.toggleAdvanced().selected)
    }

    @Test fun playerModeHidesDeveloperActionsFromNavigation() {
        val player = InGameMenuState(developer = false).show(pause = true)
        val graphics = player.actions(InGamePage.GRAPHICS)
        assertTrue(graphics.none { it in developerActions })
        assertTrue(InGameAction.WINFG !in graphics && InGameAction.PERFORMANCE_HINTS !in graphics)
        assertTrue(InGameAction.DISPLAY_FIT in graphics && InGameAction.SCALING_EFFECT in graphics && InGameAction.DRIVER_INFO in graphics)
        assertEquals(graphics.size, player.count)
        // Wrapping navigation stays inside the shown actions.
        assertEquals(graphics.last(), player.move(-1).action)
        // Open, the advanced list still has no developer option: only stretch and the color filter.
        val graphicsOpen = player.toggleAdvanced()
        assertEquals(2, graphicsOpen.advancedCount())
        assertTrue(graphicsOpen.actions().none { it in developerActions })
        assertTrue(InGameAction.STRETCH in graphicsOpen.actions())
        assertTrue(player.actions(InGamePage.SYSTEM).none { it == InGameAction.HUD_HOST_SUBMISSIONS })
        assertTrue(InGameAction.PHONE_CONTROLLERS in player.actions(InGamePage.CONTROLS))
        // Session's only advanced option is a developer one: no "More options" there.
        assertTrue(InGameAction.MORE_OPTIONS !in player.actions(InGamePage.SESSION))
        assertTrue(InGameAction.MORE_OPTIONS in InGameMenuState().actions(InGamePage.SESSION))
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
