package xendroid.compose.gamepad

import kotlin.math.hypot

/** Right-stick deflection, -1..1 per axis, screen space (y down positive), inside the unit circle. */
data class Deflection(val x: Float, val y: Float)

/**
 * U07: a free part of the screen used like a touchpad for the right stick (camera/aim): finger
 * speed becomes deflection, as a mouse does, and it falls back to zero as soon as the finger
 * rests ([idle]), lifts or is cancelled. One finger drives it at a time. Opt-in; buttons, the
 * D-pad, the sticks and the menu handle keep their touches (the overlay asks this only for
 * touches no control took). Times are passed in, so the feel is tested.
 */
class TouchCamera(
    /** Finger speed, in px per ms, that gives full deflection. */
    private val fullSpeedPxPerMs: Float,
    /** Weight of the newest sample (1 = no smoothing). */
    private val smoothing: Float = 0.5f,
    /** No movement for this long means the finger rests: no more turning. */
    val idleMs: Long = 48,
) {
    private var pointer: Long? = null
    private var lastX = 0f
    private var lastY = 0f
    private var lastT = 0L
    private var sx = 0f
    private var sy = 0f

    val active: Boolean get() = pointer != null

    /** A touch no control took landed in the area; true when it now drives the camera. */
    fun down(id: Long, x: Float, y: Float, t: Long): Boolean {
        if (pointer != null) return false
        pointer = id
        lastX = x; lastY = y; lastT = t
        sx = 0f; sy = 0f
        return true
    }

    /** The deflection to send after this move; null when [id] is not the camera finger. */
    fun move(id: Long, x: Float, y: Float, t: Long): Deflection? {
        if (id != pointer) return null
        val dt = (t - lastT).coerceAtLeast(1L).toFloat()
        val vx = (x - lastX) / dt / fullSpeedPxPerMs
        val vy = (y - lastY) / dt / fullSpeedPxPerMs
        sx += smoothing * (vx - sx)
        sy += smoothing * (vy - sy)
        lastX = x; lastY = y; lastT = t
        return clamp(sx, sy)
    }

    /** No event since [lastT]: once [idleMs] have passed, a resting finger stops the turn. */
    fun idle(t: Long): Deflection? {
        if (pointer == null || t - lastT < idleMs || (sx == 0f && sy == 0f)) return null
        sx = 0f; sy = 0f
        return Deflection(0f, 0f)
    }

    /** Lift or cancel of [id]: zero, and the finger stops driving the camera. */
    fun up(id: Long): Deflection? {
        if (id != pointer) return null
        reset()
        return Deflection(0f, 0f)
    }

    fun reset() {
        pointer = null
        sx = 0f; sy = 0f
    }

    private fun clamp(x: Float, y: Float): Deflection {
        val length = hypot(x, y)
        return if (length > 1f) Deflection(x / length, y / length) else Deflection(x, y)
    }

    companion object {
        /** The area: the right part of the screen (the left is the movement side). */
        fun inArea(x: Float, width: Int, startFraction: Float = 0.45f): Boolean = width > 0 && x >= width * startFraction
    }
}
