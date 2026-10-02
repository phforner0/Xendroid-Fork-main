package xendroid.compose.gamepad

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GamepadSerializerTest {
    @Test fun layoutRoundTripKeepsEditsAndWritesMultiProcessFormatVersion() = runTest {
        val cfg = GamepadConfigDto(globals = GamepadGlobalsDto(opacity = 0.4f, autoHideSeconds = 3f),
            landscape = OrientationLayoutDto(listOf(ControlLayoutDto("A", 0.6f, 0.7f))))
        val output = ByteArrayOutputStream()
        GamepadConfigSerializer.writeTo(cfg, output)
        assertEquals(cfg.copy(version = 2), GamepadConfigSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
    }
    @Test fun touchCameraSettingsAreKeptAndOlderFilesGetTheDefaults() = runTest {
        val cfg = GamepadConfigDto(globals = GamepadGlobalsDto(cameraSensitivity = 1.5f, cameraAreaStart = 0.6f))
        val output = ByteArrayOutputStream()
        GamepadConfigSerializer.writeTo(cfg, output)
        assertEquals(cfg.copy(version = 2), GamepadConfigSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
        // A file from before U07's settings: the camera turns and sits as it always did.
        val older = GamepadConfigSerializer.readFrom(ByteArrayInputStream(
            """{"version":2,"globals":{"enabled":true,"opacity":0.5,"autoHideSeconds":8.0,"hapticsEnabled":false}}""".toByteArray()))
        assertEquals(1f, older.globals.cameraSensitivity, 0f)
        assertEquals(TouchCamera.DEFAULT_AREA_START, older.globals.cameraAreaStart, 0f)
    }
    @Test fun corruptedLayoutIsNotSilentlyOverwritten() = runTest {
        try { GamepadConfigSerializer.readFrom(ByteArrayInputStream("{invalid".toByteArray())); fail("Expected parse error") }
        catch (expected: kotlinx.serialization.SerializationException) { }
    }
}
