package xendroid.compose.gamepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchCameraTest {
    /** Full deflection at 2 px per ms, no smoothing: deflection = speed / 2. */
    private fun camera() = TouchCamera(fullSpeedPxPerMs = 2f, smoothing = 1f, idleMs = 48)

    @Test fun fingerSpeedTurnsTheCameraAndStopsWhenItRests() {
        val cam = camera()
        assertTrue(cam.down(7, 100f, 100f, t = 0))
        assertEquals(Deflection(0.5f, 0f), cam.move(7, 116f, 100f, t = 16))        // 1 px/ms right
        assertEquals(Deflection(0f, -1f), cam.move(7, 116f, 36f, t = 32))           // 4 px/ms up: clamped
        val diagonal = cam.move(7, 180f, 100f, t = 48)!!                            // 4 px/ms both ways
        assertEquals(1f, kotlin.math.hypot(diagonal.x, diagonal.y), 1e-4f)          // inside the circle
        assertNull(cam.idle(t = 80))                                                // not quiet long enough
        assertEquals(Deflection(0f, 0f), cam.idle(t = 96))                          // the finger rests
        assertNull(cam.idle(t = 200))                                               // already at rest
        assertEquals(Deflection(0f, 0f), cam.up(7))
        assertFalse(cam.active)
    }

    @Test fun oneFingerDrivesItAndOthersAreIgnored() {
        val cam = camera()
        assertTrue(cam.down(1, 0f, 0f, 0))
        assertFalse(cam.down(2, 50f, 50f, 1))
        assertNull(cam.move(2, 90f, 90f, 10))
        assertNull(cam.up(2))
        assertTrue(cam.active)
        cam.reset()                                                                 // cancel, resize, menu
        assertFalse(cam.active)
        assertNull(cam.move(1, 10f, 0f, 20))
    }

    @Test fun smoothingAndTheArea() {
        val cam = TouchCamera(fullSpeedPxPerMs = 1f, smoothing = 0.5f)
        cam.down(1, 0f, 0f, 0)
        assertEquals(0.25f, cam.move(1, 5f, 0f, 10)!!.x, 1e-6f)                     // half of 0.5
        assertEquals(0.375f, cam.move(1, 10f, 0f, 20)!!.x, 1e-6f)
        assertTrue(TouchCamera.inArea(600f, width = 1000))
        assertFalse(TouchCamera.inArea(300f, width = 1000))                         // the movement side
        assertFalse(TouchCamera.inArea(10f, width = 0))
    }
}
