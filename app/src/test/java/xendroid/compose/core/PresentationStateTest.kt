package xendroid.compose.core

import org.junit.Assert.*
import org.junit.Test

class PresentationStateTest {
    @Test fun statusDistinguishesRequestedInterpolationFromPhysicalScanout() {
        val state = PresentationState.decode(longArrayOf(0, 1, 2, 0, 10, 2_500_000, 1, 120_000))
        assertEquals(2.5, state.gpuMs, 0.001)
        assertEquals(120f, state.hz)
        assertTrue(state.label.contains("requested 2×"))
        assertFalse(state.label.contains("displayed"))
        assertEquals(0L, state.lateSkips)
    }

    @Test fun lateSyntheticSkipsAreDecodedAndShown() {
        val state = PresentationState.decode(longArrayOf(0, 1, 2, 0, 10, -1_000_000, 0, 60_000, 0, 0, 0, 2, 7))
        assertEquals(7L, state.lateSkips)
        assertTrue(state.label.contains("7 late frames skipped"))
    }

    @Test fun syntheticSlotsAreDecodedAndOlderCoresLeaveThemAtZero() {
        assertEquals(42L, PresentationState.decode(longArrayOf(0, 1, 2, 0, 10, 2_500_000, 1, 120_000, 0, 0, 0, 2, 7, 42)).syntheticSlots)
        assertEquals(0L, PresentationState.decode(longArrayOf(0, 1, 2, 0, 10, 2_500_000, 1, 120_000, 0, 0, 0, 2, 7)).syntheticSlots)
        // An untimed pass is reported as unavailable, not as a stale figure.
        assertTrue(PresentationState.decode(longArrayOf(0, 1, 2, 0, 10, -1_000_000, 0, 60_000)).label.contains("GPU timing unavailable"))
    }

    @Test fun stateLabelIgnoresPerFrameFigures() {
        val a = PresentationState.decode(longArrayOf(0, 1, 2, 0, 10, 2_500_000, 1, 120_000, 1, 0, 0, 3, 0))
        val b = PresentationState.decode(longArrayOf(0, 1, 2, 0, 99, 3_100_000, 4, 120_000, 1, 0, 0, 3, 5))
        assertNotEquals(a.label, b.label)
        assertEquals("LSFG Native: active · requested 3×", a.stateLabel)
        assertEquals(a.stateLabel, b.stateLabel)
        val stopped = PresentationState.decode(longArrayOf(0, 1, 3, 5, 0, -1_000_000, 0, 60_000))
        assertEquals(stopped.label, stopped.stateLabel)
    }
}
