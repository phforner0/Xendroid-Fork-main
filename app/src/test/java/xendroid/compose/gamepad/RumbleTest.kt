package xendroid.compose.gamepad

import org.junit.Assert.assertEquals
import org.junit.Test

class RumbleTest {
    private fun state(left: Int, right: Int) = (left.toLong() shl 16) or right.toLong()

    @Test fun theStrongerMotorScaledByTheIntensityDrivesTheActuator() {
        assertEquals(255, rumbleAmplitude(state(65535, 0), RumbleIntensity.HIGH))
        assertEquals(255, rumbleAmplitude(state(0, 65535), RumbleIntensity.HIGH))
        assertEquals(179, rumbleAmplitude(state(65535, 1000), RumbleIntensity.MEDIUM))   // 255 * 0.7
        assertEquals(45, rumbleAmplitude(state(32767, 0), RumbleIntensity.LOW))          // 127.5 * 0.35
        assertEquals(1, rumbleAmplitude(state(1, 0), RumbleIntensity.LOW))               // still felt
        assertEquals(0, rumbleAmplitude(state(0, 0), RumbleIntensity.HIGH))
        assertEquals(0, rumbleAmplitude(state(65535, 65535), RumbleIntensity.OFF))
    }

    @Test fun intensityCyclesAndParsesWithMediumAsDefault() {
        assertEquals(RumbleIntensity.HIGH, RumbleIntensity.MEDIUM.next())
        assertEquals(RumbleIntensity.OFF, RumbleIntensity.HIGH.next())
        assertEquals(RumbleIntensity.LOW, RumbleIntensity.parse("LOW"))
        assertEquals(RumbleIntensity.MEDIUM, RumbleIntensity.parse(null))
        assertEquals(RumbleIntensity.MEDIUM, RumbleIntensity.parse("loud"))
    }
}
