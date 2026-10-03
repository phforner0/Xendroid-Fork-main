package xendroid.compose.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.edit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 15l (Bannerlator's UI scale, Eden's font size): how much larger or smaller the app draws its
 * screens ([size]) and, on top of that, its text ([text]), both as factors of what the system
 * gives. Only the steps in [SIZES] and [TEXTS] exist, so a damaged value snaps to the nearest one.
 */
data class UiScale(val size: Float = 1f, val text: Float = 1f) {
    fun larger(): UiScale = copy(size = step(SIZES, size, +1))
    fun smaller(): UiScale = copy(size = step(SIZES, size, -1))
    fun largerText(): UiScale = copy(text = step(TEXTS, text, +1))
    fun smallerText(): UiScale = copy(text = step(TEXTS, text, -1))

    companion object {
        val SIZES = listOf(0.85f, 0.9f, 1f, 1.1f, 1.2f, 1.3f)
        val TEXTS = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f)
        val DEFAULT = UiScale()

        /** The saved factors, each brought to its nearest step (1 when it is not a number). */
        fun parse(size: Float?, text: Float?) = UiScale(snap(SIZES, size), snap(TEXTS, text))

        private fun snap(steps: List<Float>, value: Float?): Float =
            value?.takeIf { it.isFinite() && it > 0f }?.let { v -> steps.minBy { abs(it - v) } } ?: 1f

        /** The next step up or down, staying at the ends (a "+" at the largest does nothing). */
        private fun step(steps: List<Float>, value: Float, by: Int): Float =
            steps[(steps.indexOf(snap(steps, value)) + by).coerceIn(0, steps.lastIndex)]
    }
}

/**
 * The density the app draws with for a [UiScale]. The rules are pure so the JVM tests hold them:
 * - larger never leaves the window's shorter side under [MIN_SHORT_SIDE_DP] (the narrowest
 *   phone layout Android designs for), so a 130 % on a small phone gives what fits;
 * - the text factor multiplies the system's font size but the two together stay within
 *   [MIN_FONT_SCALE]..[MAX_FONT_SCALE] (Android 14's largest is 2);
 * - text keeps the system's own conversion (non-linear from Android 14, where large headings
 *   grow less than body text), with the factor applied after it.
 */
object UiScaling {
    const val MIN_SHORT_SIDE_DP = 320f
    const val MIN_FONT_SCALE = 0.7f
    const val MAX_FONT_SCALE = 2f

    /** The size factor that fits a window whose shorter side is [shortSideDp] (system dp). */
    fun fittingSize(size: Float, shortSideDp: Float): Float =
        if (size <= 1f || shortSideDp <= 0f) size else min(size, max(1f, shortSideDp / MIN_SHORT_SIDE_DP))

    /** The text factor that keeps the system's [systemFontScale] times it in range. */
    fun fittingText(text: Float, systemFontScale: Float): Float = when {
        systemFontScale <= 0f -> text
        text > 1f -> min(text, max(1f, MAX_FONT_SCALE / systemFontScale))
        text < 1f -> max(text, min(1f, MIN_FONT_SCALE / systemFontScale))
        else -> text
    }

    /** [base] as the system gives it, scaled by [scale] for a window with that shorter side. */
    fun density(base: Density, scale: UiScale, shortSideDp: Float): Density {
        val size = fittingSize(scale.size, shortSideDp)
        val text = fittingText(scale.text, base.fontScale)
        return if (size == 1f && text == 1f) base else ScaledDensity(base, size, text)
    }

    private fun Density.spToDp(sp: TextUnit): Dp = sp.toDp()
    private fun Density.dpToSp(dp: Dp): TextUnit = dp.toSp()

    private class ScaledDensity(private val base: Density, private val size: Float, private val text: Float) : Density {
        override val density: Float = base.density * size
        override val fontScale: Float = base.fontScale * text
        override fun TextUnit.toDp(): Dp = base.spToDp(this) * text
        override fun Dp.toSp(): TextUnit = base.dpToSp(this / text)
        override fun equals(other: Any?) = other is ScaledDensity && other.base == base && other.size == size && other.text == text
        override fun hashCode() = (base.hashCode() * 31 + size.hashCode()) * 31 + text.hashCode()
    }
}

/**
 * Where the scale is kept: the app's own preferences, read by the frontend (which follows each
 * change through [prefs]) and by the game process when a game starts (its in-game menu and panels
 * are drawn with it). Touch controls, the HUD and the touch editor keep their own sizes.
 */
object UiScaleStore {
    private const val PREFS = "ui_look"
    private const val SIZE = "size"
    private const val TEXT = "text"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(prefs: SharedPreferences): UiScale =
        UiScale.parse(prefs.getFloat(SIZE, 1f), prefs.getFloat(TEXT, 1f))

    fun read(context: Context): UiScale = read(prefs(context))

    fun write(context: Context, scale: UiScale) {
        prefs(context).edit(commit = true) { putFloat(SIZE, scale.size); putFloat(TEXT, scale.text) }
    }
}
