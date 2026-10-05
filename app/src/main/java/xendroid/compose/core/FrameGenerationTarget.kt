package xendroid.compose.core

import kotlin.math.roundToInt

/**
 * 15d: frame generation by an output target (Eden `df05d3de`: a target rate instead of a
 * multiplier) — 60, 90 or 120 frames per second, or the display's own rate. XenDroid's frame
 * generation thread paints a fixed number of outputs per real frame, so the target is met with
 * a whole multiplier and the game capped at target ÷ multiplier: the lowest multiplier (the
 * most real frames) whose cap the game's own limit allows. The pacer's adaptive mode (outputs
 * varying frame to frame) is not wired: a frame with no generated output would need the real
 * frame's path to feed the engine, which only a device can validate. Pure, tested on the JVM.
 */
object FrameGenerationTarget {
    /** "Off": the multiplier is chosen by hand. */
    const val OFF = 0
    /** The display's refresh rate, whatever it is now. */
    const val SCREEN = -1
    /** What the menu cycles through. */
    val CHOICES = listOf(OFF, 60, 90, 120, SCREEN)

    fun next(choice: Int): Int = CHOICES[(CHOICES.indexOf(choice).coerceAtLeast(0) + 1) % CHOICES.size]

    /**
     * [multiplier] outputs per real frame with the game capped at [cap] FPS: [output] frames per
     * second while the game holds the cap. [limitedByDisplay]: the target was above the display's
     * rate and was lowered to it. [belowTarget]: even ×4 needs a cap above the game's own limit,
     * so the output stays under the target. [lowBase]: under 30 real frames per second, where
     * generated frames show the most artifacts.
     */
    data class Plan(
        val target: Int,
        val multiplier: Int,
        val cap: Int,
        val limitedByDisplay: Boolean,
        val belowTarget: Boolean,
    ) {
        val output: Int get() = cap * multiplier
        val lowBase: Boolean get() = cap < 30
    }

    /** The target in frames per second for [choice] on a [displayHz] display; 0 when off. */
    fun resolve(choice: Int, displayHz: Float): Int {
        if (choice == OFF || displayHz <= 0f) return 0
        val display = displayHz.roundToInt()
        return if (choice == SCREEN) display else minOf(choice, display)
    }

    /**
     * The plan for [choice] on a [displayHz] display with the game limited to [gameLimit] FPS by
     * the player (0 = unlimited; never the cap frame generation itself applied). Null when off.
     */
    fun plan(choice: Int, displayHz: Float, gameLimit: Int): Plan? {
        val target = resolve(choice, displayHz)
        if (target <= 1) return null
        val limitedByDisplay = choice != SCREEN && choice > target
        val limit = if (gameLimit <= 0) Int.MAX_VALUE else gameLimit
        for (multiplier in 2..4) {
            val cap = target / multiplier
            if (cap in 1..limit) return Plan(target, multiplier, cap, limitedByDisplay, belowTarget = false)
        }
        return Plan(target, 4, limit.coerceAtLeast(1), limitedByDisplay, belowTarget = true)
    }
}
