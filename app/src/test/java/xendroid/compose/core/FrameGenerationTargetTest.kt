package xendroid.compose.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xendroid.compose.core.FrameGenerationTarget.Plan
import xendroid.compose.core.FrameGenerationTarget.SCREEN

class FrameGenerationTargetTest {
    @Test fun theLowestMultiplierWhoseCapTheGameAllows() {
        // A game limited to 60 on a 120 Hz display: ×2 from 60.
        assertEquals(Plan(120, 2, 60, limitedByDisplay = false, belowTarget = false), FrameGenerationTarget.plan(120, 120f, 60))
        // Limited to 30: 60 and 40 are above its limit; ×4 from 30.
        assertEquals(Plan(120, 4, 30, false, false), FrameGenerationTarget.plan(120, 120f, 30))
        // Limited to 45: ×3 from 40 (capped from 45).
        assertEquals(Plan(120, 3, 40, false, false), FrameGenerationTarget.plan(120, 120f, 45))
        // 90 from a 60 FPS game: capped at 45, ×2.
        assertEquals(Plan(90, 2, 45, false, false), FrameGenerationTarget.plan(90, 120f, 60))
        // An unlimited game gets the ×2 cap.
        assertEquals(Plan(60, 2, 30, false, false), FrameGenerationTarget.plan(60, 60f, 0))
        assertEquals(120, FrameGenerationTarget.plan(120, 120f, 60)!!.output)
    }

    @Test fun neverAboveTheDisplay() {
        val onNinety = FrameGenerationTarget.plan(120, 90f, 60)!!
        assertEquals(Plan(90, 2, 45, limitedByDisplay = true, belowTarget = false), onNinety)
        // The display's own rate: 144 Hz from a 60 FPS game is ×3 from 48.
        assertEquals(Plan(144, 3, 48, false, false), FrameGenerationTarget.plan(SCREEN, 144f, 60))
        assertEquals(Plan(120, 2, 60, false, false), FrameGenerationTarget.plan(SCREEN, 119.88f, 60))
        assertEquals(165, FrameGenerationTarget.resolve(SCREEN, 165f))
        assertEquals(60, FrameGenerationTarget.resolve(120, 60f))
    }

    @Test fun aGameTooSlowStaysUnderTheTarget() {
        val plan = FrameGenerationTarget.plan(120, 120f, 20)!!
        assertEquals(Plan(120, 4, 20, false, belowTarget = true), plan)
        assertEquals(80, plan.output)
        assertTrue(plan.lowBase)
        assertFalse(FrameGenerationTarget.plan(120, 120f, 60)!!.lowBase)
    }

    @Test fun offIsTheMultiplier() {
        assertNull(FrameGenerationTarget.plan(FrameGenerationTarget.OFF, 120f, 60))
        assertNull(FrameGenerationTarget.plan(120, 0f, 60))
        assertEquals(0, FrameGenerationTarget.resolve(FrameGenerationTarget.OFF, 120f))
        assertEquals(listOf(60, 90, 120, SCREEN, FrameGenerationTarget.OFF),
            FrameGenerationTarget.CHOICES.map(FrameGenerationTarget::next))
        assertEquals(60, FrameGenerationTarget.next(1234))
    }

    @Test fun theExactCapComesOffLikeTheAutomaticOne() {
        val cap = GenerationCap()
        assertEquals(40, cap.prepareExact(current = 45, cap = 40))
        assertEquals(45, cap.playerLimit(40))
        // A new target re-plans from the player's limit, not from the automatic cap.
        assertEquals(30, cap.prepareExact(current = 40, cap = 30))
        assertEquals(45, cap.playerLimit(30))
        assertEquals(45, cap.restore(30))
        assertNull(cap.prepareExact(current = 60, cap = 60))
        assertEquals(60, cap.playerLimit(60))
        // Changed by the player meanwhile: their limit stays.
        cap.prepareExact(current = 60, cap = 45)
        assertNull(cap.restore(30))
    }
}
