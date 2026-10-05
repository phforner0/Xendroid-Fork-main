package xendroid.compose.gamepad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * 15f (Bannerlator `72db7f4d`): a finger that slides between the on-screen controls without
 * lifting. With [buttons], a button lets go once its finger slides off it, and the control the
 * finger slides onto (a button or the d-pad) is pressed; the d-pad hands over only when another
 * control is under the finger (a thumb drifting past its edge keeps it). With [sticks], a free
 * finger sliding onto a stick takes it. Both off is how the controls always behaved. Pure, so
 * the rules are tested on the JVM.
 */
data class TouchSlide(val buttons: Boolean = false, val sticks: Boolean = false) {
    val any: Boolean get() = buttons || sticks

    /** A finger may press [c] by sliding onto it. */
    fun canEnter(c: OnScreenControl): Boolean = when (c) {
        is OnScreenControl.Button, is OnScreenControl.Dpad -> buttons
        is OnScreenControl.AnalogStick -> sticks
    }

    /** The finger holding [c] lets go of it when it slides off, onto something or not. */
    fun releasesOnExit(c: OnScreenControl): Boolean = buttons && c is OnScreenControl.Button

    /** The finger holding [c] lets go of it only for another control under the finger. */
    fun handsOver(c: OnScreenControl): Boolean = buttons && c is OnScreenControl.Dpad

    companion object {
        /** A little past the edge before a slide counts as leaving (no flicker on the border). */
        const val EXIT_MARGIN = 1.1f

        /** Whether [pos] has left [c]: past its grab radius (with [EXIT_MARGIN]); for the d-pad,
         *  outside its square, where it presses nothing. */
        fun hasLeft(c: OnScreenControl, pos: Offset, size: IntSize, density: Density): Boolean {
            val center = controlCenterPx(c, size)
            return if (c is OnScreenControl.Dpad) {
                val half = with(density) { c.baseSizeDp.dp.toPx() } / 2f * c.scale
                kotlin.math.abs(pos.x - center.x) > half || kotlin.math.abs(pos.y - center.y) > half
            } else {
                hypot(pos.x - center.x, pos.y - center.y) > controlRadiusPx(c, density) * EXIT_MARGIN
            }
        }

        /** The control a sliding finger at [pos] presses: one it may enter, not [leaving], and
         *  not a stick another finger holds (two fingers can share a button, not a stick). */
        fun target(
            layout: List<OnScreenControl>, pos: Offset, size: IntSize, density: Density,
            slide: TouchSlide, leaving: ControlId?, held: Collection<ControlId>,
        ): OnScreenControl? = hitTest(
            layout.filter { c -> c.id != leaving && slide.canEnter(c) && !(c is OnScreenControl.AnalogStick && c.id in held) },
            pos, size, density,
        )

        /**
         * 15f: a stick's own dead zone: travel under [deadZone] (a fraction of the ring) moves
         * nothing, and the rest is spread over the whole range, so full deflection stays at the
         * ring. 0 leaves the vector as it is (the game's dead zone still applies after).
         */
        fun stickDeadZone(dx: Float, dy: Float, deadZone: Float): Pair<Float, Float> {
            if (deadZone <= 0f) return dx to dy
            val length = hypot(dx, dy)
            if (length <= deadZone) return 0f to 0f
            val scaled = (minOf(length, 1f) - deadZone) / (1f - deadZone)
            return dx / length * scaled to dy / length * scaled
        }
    }
}
