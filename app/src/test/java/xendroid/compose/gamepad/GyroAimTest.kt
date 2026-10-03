package xendroid.compose.gamepad

import org.junit.Assert.*
import org.junit.Test

class GyroAimTest {
    @Test fun theGyroscopeAimsOnlyWhileTheChosenButtonIsHeld() {
        val nothingHeld: (Int) -> Boolean = { false }
        val ltHeld: (Int) -> Boolean = { it == SlotInputRouter.KC_TRIGGER_L }
        val lbHeld: (Int) -> Boolean = { it == GyroAim.KC_SHOULDER_L }
        assertTrue(GyroAim.ALWAYS.active(nothingHeld))
        assertFalse(GyroAim.WHILE_LT.active(nothingHeld))
        assertTrue(GyroAim.WHILE_LT.active(ltHeld))
        assertFalse(GyroAim.WHILE_LT.active(lbHeld))                       // the other button does not count
        assertTrue(GyroAim.WHILE_LB.active(lbHeld))
        assertFalse(GyroAim.WHILE_LB.active(ltHeld))
    }

    @Test fun theMenuCyclesAndAnUnknownSettingIsAlways() {
        assertEquals(GyroAim.WHILE_LT, GyroAim.ALWAYS.next())
        assertEquals(GyroAim.WHILE_LB, GyroAim.WHILE_LT.next())
        assertEquals(GyroAim.ALWAYS, GyroAim.WHILE_LB.next())
        assertEquals(GyroAim.WHILE_LT, GyroAim.parse("WHILE_LT"))
        assertEquals(GyroAim.ALWAYS, GyroAim.parse("bogus"))
        assertEquals(GyroAim.ALWAYS, GyroAim.parse(null))
    }
}
