package xendroid.compose.gamepad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchSlideTest {
    private val density = Density(density = 1f, fontScale = 1f)
    private val size = IntSize(1000, 500)
    private val a = OnScreenControl.Button(ControlId.A, Kc.A, "A", 0.5f, 0.5f)           // center 500,250, radius 32
    private val b = OnScreenControl.Button(ControlId.B, Kc.B, "B", 0.6f, 0.5f)           // center 600,250
    private val dpad = OnScreenControl.Dpad(ControlId.DPAD, 0.2f, 0.5f)                   // center 200,250, half 72
    private val stick = OnScreenControl.AnalogStick(ControlId.RIGHT_STICK, false, 0.8f, 0.5f) // center 800,250
    private val layout = listOf(a, b, dpad, stick)

    @Test fun offIsHowTheControlsAlwaysBehaved() {
        val off = TouchSlide()
        assertFalse(off.any)
        layout.forEach { c ->
            assertFalse(off.canEnter(c)); assertFalse(off.releasesOnExit(c)); assertFalse(off.handsOver(c))
        }
        assertNull(TouchSlide.target(layout, Offset(500f, 250f), size, density, off, null, emptyList()))
    }

    @Test fun buttonsLetGoOnTheWayOutAndTheDpadOnlyHandsOver() {
        val slide = TouchSlide(buttons = true)
        assertTrue(slide.canEnter(a) && slide.canEnter(dpad))
        assertFalse(slide.canEnter(stick))
        assertTrue(slide.releasesOnExit(a))
        assertFalse(slide.releasesOnExit(dpad))
        assertTrue(slide.handsOver(dpad))
        // From A onto B: B is the target, A is not (it is the one being left).
        assertEquals(b, TouchSlide.target(layout, Offset(600f, 250f), size, density, slide, leaving = ControlId.A, held = listOf(ControlId.A)))
        assertNull(TouchSlide.target(layout, Offset(500f, 250f), size, density, slide, leaving = ControlId.A, held = emptyList()))
        // Onto the d-pad from a button.
        assertEquals(dpad, TouchSlide.target(layout, Offset(210f, 250f), size, density, slide, leaving = ControlId.A, held = emptyList()))
    }

    @Test fun sticksOnlyWhenAskedAndNeverOneAnotherFingerHolds() {
        val slide = TouchSlide(sticks = true)
        assertTrue(slide.canEnter(stick))
        assertFalse(slide.canEnter(a))
        assertEquals(stick, TouchSlide.target(layout, Offset(800f, 250f), size, density, slide, null, emptyList()))
        assertNull(TouchSlide.target(layout, Offset(800f, 250f), size, density, slide, null, listOf(ControlId.RIGHT_STICK)))
        // A button another finger holds can still be shared.
        assertEquals(a, TouchSlide.target(layout, Offset(500f, 250f), size, density, TouchSlide(buttons = true), null, listOf(ControlId.A)))
    }

    @Test fun leavingNeedsALittleMoreThanTheEdge() {
        assertFalse(TouchSlide.hasLeft(a, Offset(530f, 250f), size, density))   // inside the 32 px radius
        assertFalse(TouchSlide.hasLeft(a, Offset(534f, 250f), size, density))   // on the border, within 10%
        assertTrue(TouchSlide.hasLeft(a, Offset(540f, 250f), size, density))
        // The d-pad is its square: a diagonal corner is still on it.
        assertFalse(TouchSlide.hasLeft(dpad, Offset(270f, 320f), size, density))
        assertTrue(TouchSlide.hasLeft(dpad, Offset(275f, 250f), size, density))
    }

    @Test fun aSticksOwnDeadZone() {
        assertEquals(0.3f to 0.4f, TouchSlide.stickDeadZone(0.3f, 0.4f, 0f))
        assertEquals(0f to 0f, TouchSlide.stickDeadZone(0.1f, 0.1f, 0.2f))
        val (x, y) = TouchSlide.stickDeadZone(1f, 0f, 0.2f)
        assertEquals(1f, x, 1e-6f); assertEquals(0f, y, 1e-6f)              // full deflection stays at the ring
        val (hx, _) = TouchSlide.stickDeadZone(0.6f, 0f, 0.2f)
        assertEquals(0.5f, hx, 1e-6f)                                        // the rest spread over the range
        val (dx, dy) = TouchSlide.stickDeadZone(2f, 0f, 0.2f)
        assertEquals(1f, dx, 1e-6f); assertEquals(0f, dy, 1e-6f)
    }

    @Test fun aDpadsNeutralMiddle() {
        val emitter = GamepadEmitter { _, _, _ -> }
        assertEquals(setOf(Kc.DPAD_RIGHT), emitter.dpadSectors(0.4f, 0f))                 // a third by default
        assertEquals(emptySet<Int>(), emitter.dpadSectors(0.4f, 0f, neutral = 0.5f))
        assertEquals(setOf(Kc.DPAD_RIGHT), emitter.dpadSectors(0.6f, 0f, neutral = 0.5f))
    }

    @Test fun theDeadZoneIsSavedOnlyWhenItIsTheControlsOwn() {
        val dto = defaultLayout(true).toDto()
        assertTrue(dto.controls.all { it.deadZone == null })
        val tuned = defaultLayout(true).map {
            when (it) {
                is OnScreenControl.Dpad -> it.withLayout(deadZone = 0.5f)
                is OnScreenControl.AnalogStick -> it.withLayout(deadZone = 9f)   // clamped to the range
                else -> it
            }
        }.toDto()
        assertEquals(0.5f, tuned.controls.single { it.id == "DPAD" }.deadZone)
        assertEquals(0.5f, tuned.controls.single { it.id == "LEFT_STICK" }.deadZone)
        val back = tuned.applyTo(defaultLayout(true))
        assertEquals(0.5f, (back.single { it.id == ControlId.DPAD } as OnScreenControl.Dpad).deadZone)
        assertEquals(0.5f, (back.single { it.id == ControlId.RIGHT_STICK } as OnScreenControl.AnalogStick).deadZone)
        // A saved value outside the range is brought into it.
        val wild = OrientationLayoutDto(listOf(ControlLayoutDto("DPAD", 0.2f, 0.5f, deadZone = 0.01f))).applyTo(defaultLayout(true))
        assertEquals(OnScreenControl.Dpad.DEAD_ZONES.start, (wild.single { it.id == ControlId.DPAD } as OnScreenControl.Dpad).deadZone)
    }
}
