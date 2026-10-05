package xendroid.compose.gamepad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RumbleSettingsTest {
    private val pad = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"
    private val wheel = "ffffeeeeddddccccbbbbaaaa9999888877776666"

    @Test fun aControllerKeepsItsOwnIntensityOthersFollowTheDefault() {
        var settings = RumbleSettings(default = RumbleIntensity.MEDIUM)
        settings = settings.cycleDevice(pad)                                // default → Off
        assertEquals(RumbleIntensity.OFF, settings.forDevice(pad))
        assertEquals(RumbleIntensity.MEDIUM, settings.forDevice(wheel))
        assertEquals(RumbleIntensity.MEDIUM, settings.forDevice(null))
        repeat(3) { settings = settings.cycleDevice(pad) }                  // Low, Medium, High
        assertEquals(RumbleIntensity.HIGH, settings.forDevice(pad))
        settings = settings.cycleDevice(pad)                                // back to the default
        assertTrue(pad !in settings.perDevice)
        // The menu changes the default only; a controller with its own keeps it.
        settings = settings.cycleDevice(wheel).cycleDefault()
        assertEquals(RumbleIntensity.OFF, settings.forDevice(wheel))
        assertEquals(RumbleIntensity.HIGH, settings.forDevice(pad))
    }

    @Test fun rumbleIsReadOnlyWhenSomeControllerVibrates() {
        assertFalse(RumbleSettings(default = RumbleIntensity.OFF).anyOn)
        assertTrue(RumbleSettings(default = RumbleIntensity.OFF, perDevice = mapOf(pad to RumbleIntensity.LOW)).anyOn)
        assertTrue(RumbleSettings().anyOn)
    }

    @Test fun preferencesRoundTripAndStayBounded() {
        val settings = RumbleSettings(RumbleIntensity.LOW, mapOf(pad to RumbleIntensity.HIGH, wheel to RumbleIntensity.OFF))
        assertEquals(settings, RumbleSettings.decode("LOW", settings.encodeDevices()))
        // Unreadable or unknown values count as not set; the old single setting is the default.
        assertEquals(RumbleSettings(RumbleIntensity.HIGH), RumbleSettings.decode("HIGH", "{"))
        assertEquals(RumbleSettings(RumbleIntensity.MEDIUM, mapOf(pad to RumbleIntensity.LOW)),
            RumbleSettings.decode(null, """{"$pad":"LOW","$wheel":"TURBO"}"""))
        var many = RumbleSettings()
        repeat(RumbleSettings.MAX_DEVICES + 3) { many = many.cycleDevice("pad$it") }
        assertEquals(RumbleSettings.MAX_DEVICES, many.perDevice.size)
        assertTrue("pad0" !in many.perDevice && "pad${RumbleSettings.MAX_DEVICES + 2}" in many.perDevice)
    }
}
