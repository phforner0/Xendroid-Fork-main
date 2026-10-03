package xendroid.compose.gamepad

import org.junit.Assert.*
import org.junit.Test

class TouchOverlayPresenceTest {
    @Test fun p1sControllerHidesTheControlsOnceAndItsDisconnectionBringsThemBack() {
        val presence = TouchOverlayPresence()
        assertTrue(presence.controllerInput(enabled = true, player = 0))
        assertTrue(presence.hiddenByController)
        assertFalse(presence.controllerInput(enabled = true, player = 0))   // already hidden: nothing new
        assertTrue(presence.controllerGone())
        assertFalse(presence.hiddenByController)
        assertFalse(presence.controllerGone())                              // nothing was hidden
    }

    @Test fun anotherPlayersControllerOrTheOptionOffHidesNothing() {
        val presence = TouchOverlayPresence()
        assertFalse(presence.controllerInput(enabled = true, player = 1))
        assertFalse(presence.controllerInput(enabled = false, player = 0))
        assertFalse(presence.hiddenByController)
        presence.controllerInput(enabled = true, player = 0)
        presence.disabled()                                                 // turning the option off shows them
        assertFalse(presence.hiddenByController)
    }

    @Test fun showingThemFromTheMenuHoldsUntilTheControllerGoes() {
        val presence = TouchOverlayPresence()
        presence.controllerInput(enabled = true, player = 0)
        presence.shownByUser()
        assertFalse(presence.hiddenByController)
        assertFalse(presence.controllerInput(enabled = true, player = 0))  // the user's choice stands
        presence.controllerGone()
        assertTrue(presence.controllerInput(enabled = true, player = 0))   // a new start after it left
    }

    @Test fun showingThemWithNoControllerHidingThemKeepsTheRuleArmed() {
        val presence = TouchOverlayPresence()
        presence.shownByUser()                                              // e.g. the setting turned back on
        assertTrue(presence.controllerInput(enabled = true, player = 0))
    }

    @Test fun onlyAPushPastHalfCountsAsUse() {
        assertFalse(TouchOverlayPresence.pushed(0.12f, -0.2f, 0f, 0.5f))   // drift and rest
        assertTrue(TouchOverlayPresence.pushed(0f, -0.8f))
        assertTrue(TouchOverlayPresence.pushed(0f, 0f, 1f))                 // hat or trigger
    }
}
