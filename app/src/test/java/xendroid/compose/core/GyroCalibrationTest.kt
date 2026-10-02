package xendroid.compose.core

import org.junit.Assert.*
import org.junit.Test

class GyroCalibrationTest {
    @Test fun stationaryBiasIsRemovedButMovingSamplesCannotCalibrate() {
        val c = GyroCalibration()
        repeat(15) { assertFalse(c.observe(floatArrayOf(0.02f, -0.03f, 0.01f))) }
        assertTrue(c.observe(floatArrayOf(0.02f, -0.03f, 0.01f)))
        assertEquals(0f, c.corrected(0.02f, 0), 0.0001f)
        assertEquals(0.5f, c.corrected(0.47f, 1), 0.0001f)
        c.reset()
        repeat(20) { assertFalse(c.observe(floatArrayOf(0.5f, 0.1f, 0f))) }
        assertEquals(0f, c.corrected(0.5f, 0), 0.0001f)
    }
}
