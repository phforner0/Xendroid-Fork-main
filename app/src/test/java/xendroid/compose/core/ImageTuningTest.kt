package xendroid.compose.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTuningTest {
    @Test fun eachOptionCyclesBackToTheSettingsValue() {
        var t = ImageTuning()
        val aa = (1..4).map { t = t.nextAntialiasing(); t.antialiasing }
        assertEquals(listOf(0, 1, 2, -1), aa)
        t = ImageTuning()
        val sharp = (1..6).map { t = t.nextSharpness(); t.sharpness }
        assertEquals(listOf(0, 1, 2, 3, 4, -1), sharp)
        t = ImageTuning()
        val dither = (1..3).map { t = t.nextDither(); t.dither }
        assertEquals(listOf(1, 0, -1), dither)
    }

    @Test fun sharpnessMeansMoreCasAndLessFsrReduction() {
        val soft = ImageTuning(sharpness = 0)
        val max = ImageTuning(sharpness = 4)
        assertEquals(0f, soft.casSharpness)
        assertEquals(2f, soft.fsrSharpnessReduction)
        assertEquals(1f, max.casSharpness)
        assertEquals(0f, max.fsrSharpnessReduction)
        assertTrue(ImageTuning().casSharpness < 0 && ImageTuning().fsrSharpnessReduction < 0)
    }

    @Test fun keepsOnlyWhatWasChosenAsTheSettingsWriteIt() {
        assertEquals(emptyList<Pair<String, String>>(), ImageTuning().cvars(-1))
        assertEquals(
            listOf(
                "Display|postprocess_scaling_and_sharpening" to "fsr",
                "Display|postprocess_antialiasing" to "fxaa_extreme",
                "Display|postprocess_ffx_cas_additional_sharpness" to "0.75",
                "Display|postprocess_ffx_fsr_sharpness_reduction" to "0.2",
                "Display|postprocess_dither" to "false",
            ),
            ImageTuning(antialiasing = 2, sharpness = 3, dither = 0).cvars(2),
        )
        assertEquals(listOf("Display|postprocess_dither" to "true"), ImageTuning(dither = 1).cvars(-1))
    }
}
