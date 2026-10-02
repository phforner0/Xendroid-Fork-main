package xendroid.compose.companion

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import xendroid.compose.gamepad.GamepadEmitter

class CompanionProtocolTest {
    private fun roundTrip(message: CompanionMessage): CompanionMessage {
        val out = ByteArrayOutputStream()
        CompanionCodec.write(out, message)
        val input = ByteArrayInputStream(out.toByteArray())
        return CompanionCodec.read(input).also { assertEquals("one frame, fully read", -1, input.read()) }
    }

    private fun assertRejected(bytes: ByteArray, why: String) {
        try {
            CompanionCodec.read(ByteArrayInputStream(bytes))
            fail("expected a protocol error: $why")
        } catch (e: CompanionProtocolException) {
            // expected
        }
    }

    @Test fun everyFrameSurvivesARoundTrip() {
        val nonce = ByteArray(16) { it.toByte() }
        val challenge = roundTrip(CompanionMessage.Challenge(1, nonce)) as CompanionMessage.Challenge
        assertEquals(1, challenge.version)
        assertArrayEquals(nonce, challenge.nonce)

        val id = ByteArray(16) { (it * 3).toByte() }
        val proof = ByteArray(32) { (255 - it).toByte() }
        val hello = roundTrip(CompanionMessage.Hello(1, id, "Ana's phone", proof)) as CompanionMessage.Hello
        assertEquals("Ana's phone", hello.name)
        assertArrayEquals(id, hello.clientId)
        assertArrayEquals(proof, hello.proof)

        assertEquals(CompanionMessage.Welcome(3), roundTrip(CompanionMessage.Welcome(3)))
        assertEquals(CompanionMessage.Reject(CompanionProtocol.REJECT_FULL), roundTrip(CompanionMessage.Reject(CompanionProtocol.REJECT_FULL)))
        val pad = PadState(buttons = 0xF3FF, lt = 255, rt = 1, lx = -32768, ly = 32767, rx = -1, ry = 0)
        assertEquals(CompanionMessage.State(0xFFFFFFFFL, pad), roundTrip(CompanionMessage.State(0xFFFFFFFFL, pad)))
        assertEquals(CompanionMessage.Ping(1_700_000_000_123L), roundTrip(CompanionMessage.Ping(1_700_000_000_123L)))
        assertEquals(CompanionMessage.Pong(42L), roundTrip(CompanionMessage.Pong(42L)))
        assertEquals(CompanionMessage.Rumble(65535, 0), roundTrip(CompanionMessage.Rumble(65535, 0)))
        assertEquals(CompanionMessage.Bye, roundTrip(CompanionMessage.Bye))
    }

    @Test fun aLongNameIsCutToThirtyTwoBytes() {
        val hello = roundTrip(CompanionMessage.Hello(1, ByteArray(16), "x".repeat(50), ByteArray(32))) as CompanionMessage.Hello
        assertEquals("x".repeat(32), hello.name)
    }

    @Test fun malformedFramesAreRefused() {
        assertRejected(byteArrayOf(), "closed stream")
        assertRejected(byteArrayOf(0, 0), "empty frame")
        assertRejected(byteArrayOf(0x02, 0x01, 1), "frame over the limit")
        assertRejected(byteArrayOf(0, 1, 77), "unknown type")
        assertRejected(byteArrayOf(0, 3, CompanionProtocol.STATE.toByte(), 0, 0), "short STATE")
        assertRejected(byteArrayOf(0, 1, CompanionProtocol.PING.toByte()), "PING without its time")
        // HELLO claiming a 33-byte name.
        val hello = ByteArray(1 + 2 + 16 + 1).also { it[0] = CompanionProtocol.HELLO.toByte(); it[19] = 33 }
        assertRejected(byteArrayOf(0, hello.size.toByte()) + hello, "name too long")
    }

    @Test fun theProofBindsTheCodeTheNonceAndTheClient() {
        val nonce = CompanionProtocol.newNonce()
        val id = ByteArray(16) { 7 }
        val proof = CompanionProtocol.proof("123456", nonce, id)
        assertEquals(32, proof.size)
        assertTrue(CompanionProtocol.proofMatches("123456", nonce, id, proof))
        assertFalse(CompanionProtocol.proofMatches("123457", nonce, id, proof))
        assertFalse(CompanionProtocol.proofMatches("123456", CompanionProtocol.newNonce(), id, proof))
        assertFalse(CompanionProtocol.proofMatches("123456", nonce, ByteArray(16) { 8 }, proof))
        assertFalse(CompanionProtocol.proofMatches("123456", nonce, id, proof.copyOf(31)))
    }

    @Test fun codesAreSixDigits() {
        repeat(50) { assertTrue(CompanionProtocol.newCode().matches(Regex("[0-9]{6}"))) }
    }

    @Test fun buttonsAndTriggersBecomeTransitions() {
        val a = PadState(buttons = 0x1000)
        assertEquals(listOf(PadKeys.KeyChange(4, true, -1)), PadKeys.diff(PadState.RELEASED, a))
        assertEquals(emptyList<PadKeys.KeyChange>(), PadKeys.diff(a, a))
        assertEquals(listOf(PadKeys.KeyChange(4, false, -1), PadKeys.KeyChange(9, true, -1)),
            PadKeys.diff(a, PadState(buttons = 0x0010)))
        // Triggers press past half way, like a controller's.
        assertEquals(emptyList<PadKeys.KeyChange>(), PadKeys.diff(PadState.RELEASED, PadState(lt = 127)))
        assertEquals(listOf(PadKeys.KeyChange(15, true, -1)), PadKeys.diff(PadState.RELEASED, PadState(rt = 200)))
    }

    @Test fun sticksFollowTheOnScreenPadConventions() {
        // Up is positive in XInput and goes to key 19 with the value, like P1's path.
        assertEquals(listOf(PadKeys.KeyChange(19, true, 20000)), PadKeys.diff(PadState.RELEASED, PadState(ly = 20000)))
        // Crossing the center releases the other half first.
        assertEquals(listOf(PadKeys.KeyChange(18, false, 0), PadKeys.KeyChange(16, true, -9000)),
            PadKeys.diff(PadState(lx = 5000), PadState(lx = -9000)))
        assertEquals(listOf(PadKeys.KeyChange(21, false, 0)), PadKeys.diff(PadState(ry = -100), PadState.RELEASED))
    }

    @Test fun applyingTheOnScreenPadsEventsRebuildsTheSameKeys() {
        // Whatever GamepadEmitter sends, the state built by apply must diff back to the
        // same guest keys the emitter drove: what P1's path would have done.
        val pressed = sortedMapOf<Int, Int>()
        var state = PadState.RELEASED
        val emitter = GamepadEmitter { key, down, value ->
            val before = state
            state = PadKeys.apply(state, key, down, value)
            for (change in PadKeys.diff(before, state)) {
                if (change.pressed) pressed[change.key] = change.value else pressed.remove(change.key)
            }
        }
        emitter.pressDigital(4)
        emitter.stick(isLeft = true, dxN = 0.5f, dyN = -1f)       // up and right
        emitter.applyDpad(emptySet(), setOf(1, 2))
        emitter.pressDigital(15)
        // (0.5, -1) is clamped to the unit circle: (0.447, 0.894) of full deflection.
        assertEquals(mapOf(1 to -1, 2 to -1, 4 to -1, 15 to -1, 18 to 14653, 19 to 29307), pressed.toMap())
        assertEquals(0x1000 or 0x0001 or 0x0008, state.buttons)

        emitter.stick(isLeft = true, dxN = -1f, dyN = 0f)          // hard left: Y released
        assertEquals(-32768, state.lx)
        assertEquals(0, state.ly)
        assertEquals(-32768, pressed[16])
        assertFalse(18 in pressed || 19 in pressed)

        emitter.releaseAll()
        assertEquals(PadState.RELEASED, state)
        assertTrue(pressed.isEmpty())
    }

    @Test fun releasingTheInactiveHalfOfAnAxisKeepsTheActiveOne() {
        val left = PadKeys.apply(PadState.RELEASED, 16, true, -20000)
        assertEquals(-20000, PadKeys.apply(left, 18, false, 0).lx)
        assertEquals(0, PadKeys.apply(left, 16, false, 0).lx)
        assertEquals(left, PadKeys.apply(left, 99, true, 5))
    }
}
