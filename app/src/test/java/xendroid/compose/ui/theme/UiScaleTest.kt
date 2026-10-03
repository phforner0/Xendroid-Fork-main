package xendroid.compose.ui.theme

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UiScaleTest {
    @Test fun savedValuesSnapToTheSteps() {
        assertEquals(UiScale(), UiScale.parse(null, null))
        assertEquals(UiScale(1.1f, 1.15f), UiScale.parse(1.12f, 1.2f))
        assertEquals(UiScale(1.3f, 0.85f), UiScale.parse(9f, 0.1f))
        assertEquals(UiScale(), UiScale.parse(Float.NaN, -2f))
    }

    @Test fun plusAndMinusStopAtTheEnds() {
        assertEquals(1.1f, UiScale().larger().size)
        assertEquals(0.9f, UiScale().smaller().size)
        assertEquals(1.3f, UiScale(1.3f).larger().size)
        assertEquals(0.85f, UiScale(0.85f).smaller().size)
        assertEquals(UiScale(1f, 1.15f), UiScale().largerText())
        assertEquals(UiScale(1f, 1.5f), UiScale(1f, 1.5f).largerText())
        assertEquals(UiScale(1f, 0.85f), UiScale().smallerText().smallerText())
    }

    @Test fun largerNeverLeavesTheShorterSideUnder320dp() {
        // A 360 dp phone: 130 % would leave 277 dp, so it stops at 112.5 % (320 dp).
        assertEquals(1.125f, UiScaling.fittingSize(1.3f, 360f), 1e-6f)
        // A tablet takes the whole step.
        assertEquals(1.3f, UiScaling.fittingSize(1.3f, 800f), 1e-6f)
        // A window already under 320 dp is never made larger, only smaller.
        assertEquals(1f, UiScaling.fittingSize(1.2f, 280f), 1e-6f)
        assertEquals(0.85f, UiScaling.fittingSize(0.85f, 280f), 1e-6f)
    }

    @Test fun textStaysWithinWhatAndroidAllows() {
        assertEquals(1.5f, UiScaling.fittingText(1.5f, 1f), 1e-6f)
        assertEquals(2f / 1.5f, UiScaling.fittingText(1.5f, 1.5f), 1e-6f)  // 1.5 × 1.33 = 2
        assertEquals(1f, UiScaling.fittingText(1.3f, 2f), 1e-6f)            // already the largest
        assertEquals(0.85f, UiScaling.fittingText(0.85f, 1f), 1e-6f)
        assertEquals(1f, UiScaling.fittingText(0.85f, 0.7f), 1e-6f)          // already the smallest
    }

    @Test fun theDensityScalesSizesAndText() {
        val phone = Density(density = 3f, fontScale = 1f)
        assertSame(phone, UiScaling.density(phone, UiScale(), 400f))
        val scaled = UiScaling.density(phone, UiScale(1.2f, 1.15f), 400f)
        assertEquals(3.6f, scaled.density, 1e-5f)
        assertEquals(1.15f, scaled.fontScale, 1e-5f)
        with(scaled) {
            assertEquals(36f, 10.dp.toPx(), 1e-4f)
            // 10 sp: 10 dp at the system's scale, times the text factor, at the larger density.
            assertEquals(10f * 1.15f * 3.6f, 10.sp.toPx(), 1e-3f)
            assertEquals(10f, 10.sp.toDp().toSp().value, 1e-4f)
        }
    }

    @Test fun textKeepsTheSystemsOwnConversion() {
        // A system that grows large text less than small text (Android 14's non-linear scaling).
        val nonLinear = object : Density {
            override val density = 2f
            override val fontScale = 1.5f
            override fun TextUnit.toDp(): Dp = Dp(if (value > 20f) value * 1.2f else value * 1.5f)
            override fun Dp.toSp(): TextUnit = (if (value > 24f) value / 1.2f else value / 1.5f).sp
        }
        val scaled = UiScaling.density(nonLinear, UiScale(1f, 1.15f), 400f)
        with(scaled) {
            assertEquals(Dp(10f * 1.5f * 1.15f), 10.sp.toDp())
            assertEquals(Dp(30f * 1.2f * 1.15f), 30.sp.toDp())
        }
    }
}
