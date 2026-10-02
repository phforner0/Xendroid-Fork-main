package xendroid.compose.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkTest {
    private fun run(label: Char, at: Long, fps: Int, driver: String = "Turnip 25", fg: Boolean = false,
                    temp: Float = 30f, seconds: Int = 60) =
        BenchRun(label, at, fps, fps - 5, 40, seconds, driver, fg, temp)

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
        assertTrue(text.contains("Drivers differ"))
        assertTrue(text.contains("Frame generation was on in some A runs"))
        assertTrue(text.contains("8 °C apart"))
        assertTrue(result.verdict.startsWith("Fix the warnings first"))
    }

    @Test fun disagreeingPairsAreNoResult() {
        val result = Benchmark.compare(listOf(run('A', 1, 30), run('B', 2, 33), run('B', 3, 27), run('A', 4, 29)))
        assertEquals(listOf(3, -2), result.pairDeltas)
        assertTrue(result.verdict.startsWith("Pairs disagree"))
        assertEquals("Not enough runs to compare.", Benchmark.compare(listOf(run('A', 1, 30))).verdict)
        assertTrue(Benchmark.compare(listOf(run('A', 1, 30), run('B', 2, 31))).warnings.first().startsWith("Run at least A B B A"))
    }
}
