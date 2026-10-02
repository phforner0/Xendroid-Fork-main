package xendroid.compose.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xendroid.compose.core.FrameGenerationGovernor.Verdict

class FrameGenerationGovernorTest {
    private val governor = FrameGenerationGovernor(holdSeconds = 3)
    private var slots = 0L
    private var late = 0L

    /** 30 FPS at 120 Hz with 2× unless told otherwise; [newSlots]/[newLate] are this second's growth. */
    private fun second(active: Boolean = true, fps: Double = 30.0, hz: Float = 120f, multiplier: Int = 2,
                       gpuMs: Double = 3.0, newSlots: Long = 30, newLate: Long = 0, thermal: Int = 0): FrameGenerationGovernor.Assessment? {
        slots += newSlots
        late += newLate
        return governor.sample(FrameGenerationGovernor.Second(active, hz, multiplier, fps, gpuMs, slots, late, thermal))
    }

    @Test fun turningItOnIsJudgedAtOnceAndOffEndsIt() {
        assertNull(second(active = false))                       // already off
        assertEquals("fits · generation 3.0 ms of 16.7 ms between outputs", second()!!.text)
        assertNull(second())                                     // unchanged verdicts are not repeated
        assertEquals(Verdict.OFF, second(active = false)!!.verdict)
    }

    @Test fun oneSlowSecondDoesNotFlipTheVerdict() {
        second()
        assertNull(second(gpuMs = 15.0))                         // 15 ms of 16.7: over 80% of the interval
        assertNull(second(gpuMs = 15.0))
        assertNull(second(gpuMs = 3.0))                          // recovered before holding 3 s
        assertEquals(Verdict.FITS, governor.current.verdict)
        assertNull(second(gpuMs = 15.0))
        assertNull(second(gpuMs = 15.0))
        val over = second(gpuMs = 15.0)!!
        assertEquals(Verdict.GPU_OVER_BUDGET, over.verdict)
        assertEquals("generation over budget · generation 15.0 ms of 16.7 ms between outputs", over.text)
    }

    @Test fun lateSlotsThermalAndCadenceAreReported() {
        second()
        repeat(2) { assertNull(second(newSlots = 30, newLate = 10)) }
        assertEquals("synthetic outputs late · 10 of 30 slots skipped late", second(newSlots = 30, newLate = 10)!!.text)
        repeat(2) { assertNull(second(thermal = 3)) }
        assertEquals(Verdict.THERMAL, second(thermal = 3)!!.verdict)
        repeat(2) { assertNull(second(fps = 60.0, hz = 90f)) }
        assertEquals("more outputs than the display shows · 60 FPS × 2 over 90 Hz", second(fps = 60.0, hz = 90f)!!.text)
    }

    @Test fun anUntimedPassIsNotCalledOverBudget() {
        assertEquals("fits · GPU time not measured", second(gpuMs = -1.0)!!.text)
        // A few late slots are noise: under one in five does not count.
        repeat(5) { assertNull(second(newSlots = 30, newLate = 5)) }
        assertEquals(Verdict.FITS, governor.current.verdict)
    }
}
