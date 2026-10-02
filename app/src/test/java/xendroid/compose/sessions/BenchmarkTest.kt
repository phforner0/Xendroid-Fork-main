package xendroid.compose.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkTest {
    private fun run(label: Char, at: Long, fps: Int, driver: String = "Turnip 25", fg: Boolean = false,
                    temp: Float = 30f, seconds: Int = 60, limits: List<Int> = emptyList(), hz: List<Int> = emptyList(),
                    cap: Boolean? = null) =
        BenchRun(label, at, fps, fps - 5, 40, seconds, driver, fg, temp, fpsLimits = limits, displayHz = hz, refreshCap = cap)

    @Test fun abbaIsTheOrderThatCancelsWarmingUp() {
        assertTrue(Benchmark.isBalanced("ABBA"))
        assertTrue(Benchmark.isBalanced("ABBAABBA"))
        assertFalse(Benchmark.isBalanced("AABB"))
        assertFalse(Benchmark.isBalanced("ABAB"))
        assertFalse(Benchmark.isBalanced("ABB"))
        assertEquals("ABBAAB", Benchmark.abbaPlan(6))
    }

    @Test fun aCleanAbbaGivesPairedDifferences() {
        val result = Benchmark.compare(listOf(run('A', 1, 30), run('B', 2, 36), run('B', 3, 35), run('A', 4, 28)))
        assertEquals("ABBA", result.order)
        assertEquals(emptyList<String>(), result.warnings)
        assertEquals(listOf(6, 7), result.pairDeltas)                 // (A1,B2) and (A4,B3)
        assertEquals("B is faster in every pair (+6 to +7 FPS median).", result.verdict)
        assertEquals(29.0, result.a!!.medianFps, 0.0)
        assertEquals(35.5, result.b!!.medianFps, 0.0)
    }

    @Test fun whatWasNotFixedIsSaidBeforeAnyResult() {
        val result = Benchmark.compare(listOf(
            run('A', 1, 30, driver = "Turnip 25", temp = 30f), run('A', 2, 31, fg = true),
            run('B', 3, 40, driver = "Adreno 762", temp = 38f, seconds = 12), run('B', 4, 41)))
        val text = result.warnings.joinToString("\n")
        assertTrue(text.contains("Order AABB"))
        assertTrue(text.contains("sampled only 12 s"))
        assertTrue(text.contains("The driver differs among the B runs (Adreno 762 / Turnip 25)"))
        assertTrue(text.contains("The frame generation differs among the A runs (off / on)"))
        assertTrue(text.contains("8 °C apart"))
        assertTrue(result.verdict.startsWith("Fix the warnings first"))
    }

    @Test fun theOneChangeMayBeTheDriverOrTheFpsLimitButNotBoth() {
        // The driver is the change: each side ran one driver, nothing else differs.
        val drivers = Benchmark.compare(listOf(run('A', 1, 30), run('B', 2, 34, driver = "Adreno 762"),
            run('B', 3, 33, driver = "Adreno 762"), run('A', 4, 29)))
        assertEquals(emptyList<String>(), drivers.warnings)
        assertEquals(listOf("driver (Turnip 25 → Adreno 762)"), drivers.changed)
        assertEquals("B is faster in every pair (+4 to +4 FPS median).", drivers.verdict)
        // The FPS limit as the change, recorded per run.
        val limits = Benchmark.compare(listOf(run('A', 1, 30, limits = listOf(30), hz = listOf(120), cap = true),
            run('B', 2, 55, limits = listOf(60), hz = listOf(120), cap = true),
            run('B', 3, 54, limits = listOf(60), hz = listOf(120), cap = true),
            run('A', 4, 30, limits = listOf(30), hz = listOf(120), cap = true)))
        assertEquals(emptyList<String>(), limits.warnings)
        assertEquals(listOf("FPS limit (30 FPS → 60 FPS)"), limits.changed)
        // Two things at once: no result.
        val both = Benchmark.compare(listOf(run('A', 1, 30, limits = listOf(30)), run('B', 2, 55, driver = "Adreno 762", limits = listOf(60)),
            run('B', 3, 54, driver = "Adreno 762", limits = listOf(60)), run('A', 4, 30, limits = listOf(30))))
        assertTrue(both.warnings.single().startsWith("A and B differ in more than one thing: driver (Turnip 25 → Adreno 762); FPS limit (30 FPS → 60 FPS)"))
        assertTrue(both.verdict.startsWith("Fix the warnings first"))
    }

    @Test fun pacingThatMovedDuringARunOrBetweenRunsOfASideIsSaid() {
        val result = Benchmark.compare(listOf(run('A', 1, 30, limits = listOf(60, 30), hz = listOf(120)),
            run('B', 2, 31, limits = listOf(60), hz = listOf(120)), run('B', 3, 31, limits = listOf(60), hz = listOf(60)),
            run('A', 4, 30, limits = listOf(60), hz = listOf(120))))
        val text = result.warnings.joinToString("\n")
        assertTrue(text.contains("changed during a run (A)"))
        assertTrue(text.contains("The FPS limit differs among the A runs (changing (60 FPS / 30 FPS) / 60 FPS)"))
        assertTrue(text.contains("The display refresh differs among the B runs (120 Hz / 60 Hz)"))
        // Runs from before pacing was recorded are not held against each other.
        val old = Benchmark.compare(listOf(run('A', 1, 30), run('B', 2, 31, limits = listOf(60)), run('B', 3, 31), run('A', 4, 30)))
        assertEquals(emptyList<String>(), old.warnings)
    }

    @Test fun disagreeingPairsAreNoResult() {
        val result = Benchmark.compare(listOf(run('A', 1, 30), run('B', 2, 33), run('B', 3, 27), run('A', 4, 29)))
        assertEquals(listOf(3, -2), result.pairDeltas)
        assertTrue(result.verdict.startsWith("Pairs disagree"))
        assertEquals("Not enough runs to compare.", Benchmark.compare(listOf(run('A', 1, 30))).verdict)
        assertTrue(Benchmark.compare(listOf(run('A', 1, 30), run('B', 2, 31))).warnings.first().startsWith("Run at least A B B A"))
    }
}
