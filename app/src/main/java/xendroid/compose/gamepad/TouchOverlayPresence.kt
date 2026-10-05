package xendroid.compose.gamepad

import kotlin.math.abs

/**
 * The on-screen controls step aside while a physical controller plays P1, the rule Eden
 * ships (e4dccd5a): that controller's first button, or a stick or trigger pushed past half,
 * hides them; they come back when it disconnects. Touching the screen does not bring them
 * back, so touch and a controller work together. Two differences: a controller playing
 * P2–P4 never hides P1's controls (Bannerlator f76b16e1), and showing them from the menu
 * keeps them until that controller goes (Eden hides them again at the next press).
 * The setting that shows the controls at all still decides first; this only hides.
 */
class TouchOverlayPresence {
    /** True while a controller's use keeps the controls hidden. */
    var hiddenByController = false
        private set
    private var keptByUser = false

    /** A press or a push from a physical controller playing [player] (0 = P1); true when it hid the controls. */
    fun controllerInput(enabled: Boolean, player: Int): Boolean {
        if (!enabled || player != 0 || keptByUser || hiddenByController) return false
        hiddenByController = true
        return true
    }

    /** The menu showed the controls: a controller hiding them is overruled until it disconnects. */
    fun shownByUser() {
        if (hiddenByController) keptByUser = true
        hiddenByController = false
    }

    /** P1 has no physical controller any more; true when this brought the controls back. */
    fun controllerGone(): Boolean {
        val wasHidden = hiddenByController
        hiddenByController = false
        keptByUser = false
        return wasHidden
    }

    /** The option was turned off: nothing stays hidden. */
    fun disabled() {
        hiddenByController = false
        keptByUser = false
    }

    companion object {
        /** A stick, trigger or hat this far from rest is use; a worn stick's drift is not. */
        const val PUSH = 0.5f

        fun pushed(vararg axes: Float): Boolean = axes.any { abs(it) > PUSH }
    }
}
