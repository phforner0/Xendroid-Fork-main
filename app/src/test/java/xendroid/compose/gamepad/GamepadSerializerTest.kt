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
    @Test fun corruptedLayoutIsNotSilentlyOverwritten() = runTest {
        try { GamepadConfigSerializer.readFrom(ByteArrayInputStream("{invalid".toByteArray())); fail("Expected parse error") }
        catch (expected: kotlinx.serialization.SerializationException) { }
    }
}
