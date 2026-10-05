package xendroid.compose.gamepad

import kotlin.math.roundToInt

/** U08/I04: how strongly a controller plays the guest's rumble; Off never vibrates. */
enum class RumbleIntensity(val label: String, val scale: Float) {
    OFF("Off", 0f), LOW("Low", 0.35f), MEDIUM("Medium", 0.7f), HIGH("High", 1f);

    fun next(): RumbleIntensity = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(name: String?): RumbleIntensity = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/**
 * Android vibration amplitude (0 = stop, 1..255) for one slot's guest rumble [state]
 * (left motor shl 16 or right motor, 0..65535 each): one actuator, so the stronger
 * motor wins; any non-zero request stays perceptible (at least 1).
 */
fun rumbleAmplitude(state: Long, intensity: RumbleIntensity): Int {
    if (intensity == RumbleIntensity.OFF) return 0
    val left = (state shr 16) and 0xFFFF
    val right = state and 0xFFFF
    val strongest = maxOf(left, right)
    if (strongest == 0L) return 0
    return (strongest / 65535f * 255f * intensity.scale).roundToInt().coerceIn(1, 255)
}
