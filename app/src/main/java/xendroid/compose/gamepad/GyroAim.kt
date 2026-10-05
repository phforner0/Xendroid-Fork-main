package xendroid.compose.gamepad

/**
 * When the gyroscope turns the camera (Bannerlator 7cf04b57): always, or only while P1 holds
 * LT or LB, the way Xbox 360 shooters aim down sights with the left trigger. Let go and the
 * camera stops at once (the right stick centers), so tilting the phone between aims does nothing.
 */
enum class GyroAim(private val activator: Int?) {
    ALWAYS(null),
    WHILE_LT(SlotInputRouter.KC_TRIGGER_L),
    WHILE_LB(GYRO_KC_SHOULDER_L);

    /** Whether the gyroscope aims now, given which of P1's guest buttons are held. */
    fun active(holding: (Int) -> Boolean): Boolean = activator?.let(holding) ?: true

    fun next(): GyroAim = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The guest's left shoulder (LB) key code, as the host activity sends it. */
        const val KC_SHOULDER_L = GYRO_KC_SHOULDER_L

        fun parse(name: String?): GyroAim = entries.firstOrNull { it.name == name } ?: ALWAYS
    }
}

private const val GYRO_KC_SHOULDER_L = 10
