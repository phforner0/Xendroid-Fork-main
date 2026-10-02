package xendroid.compose.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionRumbleForwarderTest {
    private val forwarder = CompanionRumbleForwarder()
    private val sent = mutableListOf<Triple<Int, Int, Int>>()
    private fun update(phones: Map<Int, Long>, state: LongArray?) =
        forwarder.update(phones, state) { slot, left, right -> sent += Triple(slot, left, right) }
    private fun motors(left: Int, right: Int) = (left.toLong() shl 16) or right.toLong()

    @Test fun onlyChangesAreSentToThePhonePlayingTheSlot() {
        val state = longArrayOf(motors(9, 9), 0, motors(65535, 1000), 0)
        update(mapOf(2 to 7L), state)
        update(mapOf(2 to 7L), state)                                   // unchanged: nothing more
        assertEquals(listOf(Triple(2, 65535, 1000)), sent)              // P1's rumble is not a phone's
        update(mapOf(2 to 7L), longArrayOf(0, 0, 0, 0))
        assertEquals(Triple(2, 0, 0), sent.last())
        assertEquals(2, sent.size)
    }

    @Test fun aSilentSlotSendsNothingToANewPhone() {
        update(mapOf(1 to 3L), longArrayOf(0, 0, 0, 0))
        assertEquals(emptyList<Triple<Int, Int, Int>>(), sent)
    }

    @Test fun aReconnectedPhoneIsToldAgainAndAPausedGameStopsEveryone() {
        val state = longArrayOf(0, motors(500, 0), motors(0, 800), 0)
        update(mapOf(1 to 1L, 2 to 2L), state)
        // The P2 phone reconnects (new connection, same slot): it starts silent, so resend.
        update(mapOf(1 to 5L, 2 to 2L), state)
        assertEquals(listOf(Triple(1, 500, 0), Triple(2, 0, 800), Triple(1, 500, 0)), sent)
        sent.clear()
        update(mapOf(1 to 5L, 2 to 2L), null)                          // menu, pause or background
        assertEquals(setOf(Triple(1, 0, 0), Triple(2, 0, 0)), sent.toSet())
        sent.clear()
        update(mapOf(1 to 5L, 2 to 2L), null)
        assertEquals(emptyList<Triple<Int, Int, Int>>(), sent)
    }

    @Test fun aPhoneThatLeftIsForgotten() {
        update(mapOf(3 to 4L), longArrayOf(0, 0, 0, motors(1, 1)))
        update(emptyMap(), longArrayOf(0, 0, 0, motors(1, 1)))
        update(mapOf(3 to 4L), longArrayOf(0, 0, 0, motors(1, 1)))     // back on the same connection id: told again
        assertEquals(listOf(Triple(3, 1, 1), Triple(3, 1, 1)), sent)
    }
}
