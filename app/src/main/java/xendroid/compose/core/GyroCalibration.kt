package xendroid.compose.core

import kotlin.math.abs

/** Stationary bias estimator. Moving samples reset calibration, never become a
 * camera offset. Values are angular velocities (rad/s), not device orientation. */
class GyroCalibration {
    private val sums = FloatArray(3)
    private val bias = FloatArray(3)
    private var samples = 0
    var ready = false
        private set
    fun reset() { sums.fill(0f); samples = 0; ready = false }
    fun observe(values: FloatArray): Boolean {
        require(values.size >= 3)
        if (ready) return true
        if (values.take(3).any { !it.isFinite() || abs(it) > 0.15f }) {
            sums.fill(0f); samples = 0; return false
        }
        for (i in 0..2) sums[i] += values[i]
        if (++samples >= 16) { for (i in 0..2) bias[i] = sums[i] / samples; ready = true }
        return ready
    }
    fun corrected(value: Float, axis: Int): Float = if (ready && value.isFinite()) value - bias[axis] else 0f
}
