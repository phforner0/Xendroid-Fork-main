package xendroid.compose.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TvMarginTest {
    @Test fun theMarginCyclesAndInsetsEachSide() {
        assertEquals(listOf(2.5f, 5f, 7.5f, 10f, 0f), TvMargin.CHOICES.map(TvMargin::next))
        assertEquals(2.5f, TvMargin.next(1f))                       // an odd saved value moves to the next step
        assertEquals(0 to 0, TvMargin.padding(1920, 1080, 0f))
        assertEquals(96 to 54, TvMargin.padding(1920, 1080, 5f))
        assertEquals(384 to 216, TvMargin.padding(3840, 2160, 10f))
        // Never more than the maximum, never from a damaged value.
        assertEquals(192 to 108, TvMargin.padding(1920, 1080, 40f))
        assertEquals(0 to 0, TvMargin.padding(1920, 1080, Float.NaN))
        assertEquals(0 to 0, TvMargin.padding(-1, -1, 5f))
    }
}
