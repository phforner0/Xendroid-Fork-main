package xendroid.compose.core

import org.junit.Assert.*
import org.junit.Test

class BatteryReadoutTest {
    @Test fun currentIsReadInMicroOrMilliamperesWhateverTheSign() {
        assertEquals(1.85, BatteryReadout.amps(-1_850_000)!!, 1e-9)        // µA, as documented
        assertEquals(1.85, BatteryReadout.amps(1_850)!!, 1e-9)             // mA, as many OEMs report
        assertNull(BatteryReadout.amps(0))
        assertNull(BatteryReadout.amps(Long.MIN_VALUE))                    // not supported
        assertNull(BatteryReadout.amps(Int.MIN_VALUE.toLong()))
    }

    @Test fun chargeAndVoltageAcceptTheirOtherUnits() {
        assertEquals(2.5, BatteryReadout.ampHours(2_500_000)!!, 1e-9)      // µAh
        assertEquals(2.5, BatteryReadout.ampHours(2_500)!!, 1e-9)          // mAh
        assertNull(BatteryReadout.ampHours(Long.MIN_VALUE))
        assertEquals(3.85, BatteryReadout.volts(3_850)!!, 1e-9)            // mV
        assertEquals(3.85, BatteryReadout.volts(3_850_000)!!, 1e-9)        // µV
        assertEquals(4.0, BatteryReadout.volts(4)!!, 1e-9)                 // whole volts
        assertNull(BatteryReadout.volts(0))
        assertNull(BatteryReadout.volts(12_000))
        // The OEM that reports mA no longer reads 0.0 W.
        assertEquals(7.12, BatteryReadout.watts(-1_850, 3_850)!!, 0.01)
        assertEquals(7.12, BatteryReadout.watts(-1_850_000, 3_850)!!, 0.01)
        assertNull(BatteryReadout.watts(Long.MIN_VALUE, 3_850))
    }

    @Test fun theLineSaysWhatIsKnown() {
        assertEquals("PWR 7.4 W · BAT 62% ~1 h 40 min", BatteryReadout.line(7.42, 62, false, 100, null))
        assertEquals("PWR 7.4 W · BAT 62%", BatteryReadout.line(7.42, 62, false, null, null))
        assertEquals("PWR N/A · BAT 62% ~35 min", BatteryReadout.line(null, 62, false, 35, null))
        assertEquals("PWR plugged in · BAT 62% full in 45 min", BatteryReadout.line(9.0, 62, true, 100, 45))
        assertEquals("PWR plugged in · BAT 62%", BatteryReadout.line(9.0, 62, true, null, null))
        assertEquals("PWR 7.4 W", BatteryReadout.line(7.42, null, false, 100, null))
        assertEquals("3 h 05 min", BatteryReadout.duration(185))
    }

    @Test fun timeLeftComesFromTheChargeAgainstASmoothedCurrent() {
        val estimate = BatteryTimeEstimate()
        // 2.5 Ah left at 2 A: 75 minutes.
        assertEquals(75, estimate.sample(0, 62, 2_500_000, -2_000_000, pluggedIn = false))
        // One second at 4 A barely moves it (10 s smoothing): a loading spike does not swing it.
        val afterSpike = estimate.sample(1_000, 62, 2_500_000, -4_000_000, pluggedIn = false)!!
        assertTrue(afterSpike in 65..74)
        // A charger stops the estimate and starts it over.
        assertNull(estimate.sample(2_000, 62, 2_500_000, 1_000_000, pluggedIn = true))
        assertEquals(75, estimate.sample(3_000, 62, 2_500_000, -2_000_000, pluggedIn = false))
    }

    @Test fun withoutAChargeCounterThePercentageDropIsUsed() {
        val estimate = BatteryTimeEstimate()
        assertNull(estimate.sample(0, 80, Long.MIN_VALUE, -2_000_000, pluggedIn = false))
        assertNull(estimate.sample(60_000, 79, Long.MIN_VALUE, -2_000_000, pluggedIn = false))  // one point: too little
        // Two points in 4 minutes: 2 min per point, 78 points left.
        assertEquals(156, estimate.sample(240_000, 78, Long.MIN_VALUE, -2_000_000, pluggedIn = false))
        assertNull(estimate.sample(300_000, 90, Long.MIN_VALUE, 0, pluggedIn = false))          // rose: start over
    }

    @Test fun anIdleReadingIsNotAnEstimate() {
        // 4 Ah at 20 mA would be 200 hours.
        assertNull(BatteryTimeEstimate().sample(0, 90, 4_000_000, -20_000, pluggedIn = false))
    }
}
