package xendroid.compose.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreReportTest {
    private val sample = """
        CPU [cortex-x4*1+cortex-a720*3+cortex-a720*4(armv9.2-a)]:
            * fp
            * asimd
            * atomics
            * sve2
            * i8mm

        GPU [Adreno (TM) 825(Vulkan: 1.3.284)]:
            * VK_KHR_swapchain
            * VK_EXT_robustness2
    """.trimIndent()

    @Test fun readsBothBlocks() {
        val r = CoreReport.parse(sample)!!
        assertEquals(listOf("Cortex-X4" to 1, "Cortex-A720" to 7), r.cores)
        assertEquals("Cortex-X4 ×1 + Cortex-A720 ×7", r.coresLine)
        assertEquals("armv9.2-a", r.isa)
        assertEquals(listOf("fp", "asimd", "atomics", "sve2", "i8mm"), r.cpuFeatures)
        assertEquals(listOf("sve2", "i8mm", "atomics"), r.notableFeatures)
        assertEquals("Adreno (TM) 825", r.gpu)
        assertEquals("1.3.284", r.vulkan)
        assertEquals(listOf("VK_KHR_swapchain", "VK_EXT_robustness2"), r.extensions)
        assertEquals(emptyList<String>(), r.notes)
    }

    @Test fun keepsWhatItCannotRead() {
        val r = CoreReport.parse("CPU [kryo*8()]:\n    * fp\n\nGPU: Vulkan unavailable")!!
        assertEquals(listOf("Kryo" to 8), r.cores)
        assertNull(r.isa)
        assertNull(r.gpu)
        assertEquals(listOf("GPU: Vulkan unavailable"), r.notes)
    }

    @Test fun nothingToRead() {
        assertNull(CoreReport.parse(null))
        assertNull(CoreReport.parse("  "))
    }
}
