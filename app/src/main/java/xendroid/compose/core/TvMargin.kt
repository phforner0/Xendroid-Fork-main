package xendroid.compose.core

import kotlin.math.roundToInt

/**
 * 15h (Bannerlator's safe-area bars): a margin around the picture on a TV, for sets that
 * overscan (cut the edges of the image). The game's surface on the external display is inset by
 * this much of the screen on every side, and the presenter fits the picture inside, as after a
 * resize. Only the TV output; the handset never cuts. Pure, tested on the JVM.
 */
object TvMargin {
    /** What the in-game option cycles through, in percent of the screen per side. */
    val CHOICES = listOf(0f, 2.5f, 5f, 7.5f, 10f)
    const val MAX = 10f

    fun next(percent: Float): Float = CHOICES.firstOrNull { it > percent + 0.01f } ?: CHOICES.first()

    /** The inset per side, (horizontal, vertical) in pixels, for a [width] x [height] screen. */
    fun padding(width: Int, height: Int, percent: Float): Pair<Int, Int> {
        val p = percent.takeIf { it.isFinite() }?.coerceIn(0f, MAX) ?: 0f
        return (width.coerceAtLeast(0) * p / 100f).roundToInt() to (height.coerceAtLeast(0) * p / 100f).roundToInt()
    }
}
