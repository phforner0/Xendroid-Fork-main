package xendroid.compose.core

import org.junit.Assert.*
import org.junit.Test

class FrameGenerationReadoutTest {
    @Test fun theMultipleHasToFitTheDisplay() {
        assertFalse(FrameGenerationReadout.overDisplay(30.0, 2, 60f))
        assertFalse(FrameGenerationReadout.overDisplay(30.5, 2, 60f))          // within the 2.5% slack
        assertTrue(FrameGenerationReadout.overDisplay(45.0, 2, 60f))
        assertTrue(FrameGenerationReadout.overDisplay(45.0, 3, 120f))
        assertFalse(FrameGenerationReadout.overDisplay(40.0, 3, 120f))
        assertFalse(FrameGenerationReadout.overDisplay(0.0, 2, 60f))           // no figures, no warning
        // The cap is GenerationCap's own.
        assertEquals(30, FrameGenerationReadout.cap(60f, 2))
        assertEquals(40, FrameGenerationReadout.cap(120f, 3))
        assertEquals(GenerationCap().prepare(0, 90f, 2), FrameGenerationReadout.cap(90f, 2))
    }

    @Test fun thePreviewSaysWhatTurningItOnWouldCost() {
        assertEquals("Win-FG 2×: 30 FPS × 2 = 60/s fits the 60 Hz display",
            FrameGenerationReadout.preview("Win-FG", 2, 30.0, 60f))
        assertEquals("Win-FG 2×: 45 FPS × 2 = 90/s is over the 60 Hz display; turning it on caps the game at 30 FPS " +
            "(base 30 → 60 frames/s submitted). A higher refresh rate avoids the cap.",
            FrameGenerationReadout.preview("Win-FG", 2, 45.0, 60f))
        assertNull(FrameGenerationReadout.preview("LSFG Native", 3, 0.0, 120f))  // paused before any frame
    }

    @Test fun whileItRunsBaseAndSubmittedAreApart() {
        assertEquals(listOf("Base 30 FPS → 60 frames/s submitted (30 generated; submitted, not proof of display)"),
            FrameGenerationReadout.running(30.0, 30.0, 2, 60f))
        val over = FrameGenerationReadout.running(45.0, 45.0, 2, 60f)
        assertEquals(2, over.size)
        assertTrue(over[1].startsWith("Over the display: 45 FPS × 2 = 90/s above 60 Hz"))
        assertTrue(over[1].endsWith("Limit the game to 30 FPS or raise the refresh rate."))
    }
}
