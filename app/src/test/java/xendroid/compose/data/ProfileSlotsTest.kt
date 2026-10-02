package xendroid.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSlotsTest {
    private val ana = "E030000000000001"
    private val bia = "E030000000000002"
    private val caio = "E030000000000003"

    @Test fun theCoreReadsOneKeyPerPlayer() {
        assertEquals("logged_profile_slot_0_xuid", ProfileSlots.key(0))
        assertEquals("logged_profile_slot_3_xuid", ProfileSlots.key(3))
        assertTrue(runCatching { ProfileSlots.key(4) }.isFailure)
        assertEquals(listOf(ana, null, null, null), ProfileSlots.normalize(listOf(ana.lowercase(), " ", null)))
    }

    @Test fun aProfilePlaysForOnePlayerAtATime() {
        val slots = listOf(ana, bia, null, null)
        assertEquals(listOf(ana, bia, caio, null), ProfileSlots.assign(slots, 2, caio.lowercase()))
        // Bia moves from P2 to P4; P2 signs in nobody.
        assertEquals(listOf(ana, null, null, bia), ProfileSlots.assign(slots, 3, bia))
        // Picking P2's profile for P1 ("Play as") frees P2 and replaces Ana.
        assertEquals(listOf(bia, null, null, null), ProfileSlots.assign(slots, 0, bia))
        assertEquals(listOf(ana, null, null, null), ProfileSlots.assign(slots, 1, null))
        assertEquals(mapOf(1 to "", 3 to bia), ProfileSlots.changes(slots, ProfileSlots.assign(slots, 3, bia)))
        assertTrue(ProfileSlots.changes(slots, slots.map { it?.lowercase() }).isEmpty())
    }

    @Test fun goneOrRepeatedProfilesSignInNobody() {
        // Caio went to the trash; Ana was written twice by hand: the lower slot keeps her.
        assertEquals(listOf(ana, null, bia, null), ProfileSlots.reconcile(listOf(ana, caio, bia, ana), listOf(ana.lowercase(), bia)))
        assertEquals(List(4) { null }, ProfileSlots.reconcile(listOf(ana, bia, null, null), emptyList()))
        assertEquals(3, ProfileSlots.otherPlayerOf(listOf(ana, null, bia, null), bia.lowercase()))
        assertNull(ProfileSlots.otherPlayerOf(listOf(ana, null, bia, null), ana))       // P1 is not "another player"
    }
}
