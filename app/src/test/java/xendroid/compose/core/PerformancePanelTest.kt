package xendroid.compose.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformancePanelTest {
    /** Cumulative counts as the core keeps them: [frames] frames of [ms] ms added to [before]. */
    private fun counts(before: LongArray = LongArray(251), vararg frames: Pair<Int, Long>): LongArray =
        before.copyOf().also { array -> frames.forEach { (ms, n) -> array[ms] += n } }

    @Test fun theDetailCyclesAndOldChoicesAreKept() {
        // From the full list: the panel, then FPS only, then back.
        assertEquals(listOf(HudDetail.PANEL, HudDetail.COMPACT, HudDetail.FULL),
            generateSequence(HudDetail.FULL) { it.next() }.drop(1).take(3).toList())
        assertEquals(HudDetail.COMPACT, HudDetail.parse(null, legacyCompact = true))
        assertEquals(HudDetail.FULL, HudDetail.parse(null, legacyCompact = false))
        assertEquals(HudDetail.PANEL, HudDetail.parse("panel", legacyCompact = true))
        assertEquals(HudDetail.FULL, HudDetail.parse("neon", legacyCompact = false))
    }

    @Test fun theLastSecondsOnlyCountWhatHappenedInThem() {
        val panel = PerformancePanel(windowSeconds = 3)
        assertNull(panel.recent())
        // A slow start (loading: 200 ms frames) and then a steady 60 FPS.
        var total = counts(LongArray(251), 199 to 10L)
        panel.frameTimes(total)
        repeat(5) {
            total = counts(total, 16 to 60L)
            panel.frameTimes(total)
        }
        val recent = panel.recent()!!
        assertEquals(3, panel.recentSeconds)
        assertEquals(180L, recent.frames)                 // three seconds, the loading frames gone
        assertEquals(17, recent.medianUnderMs)
        assertEquals(17, recent.p99UnderMs)
        assertFalse(recent.stutters)
    }

    @Test fun aHitchEveryFewFramesShowsAsStutter() {
        val panel = PerformancePanel()
        var total = LongArray(251)
        panel.frameTimes(total)
        repeat(4) {
            total = counts(total, 16 to 57L, 50 to 2L)     // two 50 ms hitches a second
            panel.frameTimes(total)
        }
        val recent = panel.recent()!!
        assertEquals(17, recent.medianUnderMs)
        assertEquals(51, recent.p99UnderMs)
        assertTrue(recent.stutters)
        assertEquals("<51 ms", PerformancePanel.bound(recent.p99UnderMs))
        assertEquals("≥250 ms", PerformancePanel.bound(250))      // the core's open last bucket
    }

    @Test fun pausedOrLoadingSaysNothing() {
        val panel = PerformancePanel()
        val still = counts(LongArray(251), 16 to 1000L)
        repeat(5) { panel.frameTimes(still) }               // the counts did not move
        assertNull(panel.recent())
        assertNull(PerformancePanel.pacing(List(251) { if (it == 33) 5L else 0L }))
        // Counts of another size (a different core) start over instead of mixing.
        panel.frameTimes(LongArray(10))
        assertEquals(0, panel.recentSeconds)
    }

    @Test fun changedSettingsPutPerformanceFirstAndDropTheDefaults() {
        val lines = listOf(
            "Content|license_mask = 1 (default 0)",
            "GPU|draw_resolution_scale_x = 2 (default 1) · this game",
            "GPU|draw_resolution_scale_y = 2 (default 1) · this game",
            "Kernel|apply_title_update = false (default true)",
            "Vulkan|turnip_debug = sysmem,nolrz (default sysmem)",
            "(more settings changed, not listed)",
        )
        val (shown, total) = PerformancePanel.changedSettings(lines, max = 3)
        assertEquals(5, total)
        assertEquals(listOf("draw_resolution_scale_x = 2 · this game", "draw_resolution_scale_y = 2 · this game",
            "turnip_debug = sysmem,nolrz"), shown)
        assertEquals(emptyList<String>() to 0, PerformancePanel.changedSettings(null))
    }

    @Test fun theResolutionScaleComesFromTheChangedSettings() {
        assertNull(PerformancePanel.resolutionScale(null))
        assertNull(PerformancePanel.resolutionScale(listOf("GPU|vsync = false (default true)")))
        assertEquals("2×2", PerformancePanel.resolutionScale(listOf(
            "GPU|draw_resolution_scale_x = 2 (default 1) · this game", "GPU|draw_resolution_scale_y = 2 (default 1)")))
        assertEquals("3×1", PerformancePanel.resolutionScale(listOf("GPU|draw_resolution_scale_x = 3 (default 1)")))
    }
}
