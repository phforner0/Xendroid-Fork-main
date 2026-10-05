package xendroid.compose.data

import org.junit.Assert.assertEquals
import org.junit.Test
import xendroid.compose.data.ProfilePick.Decision

class ProfilePickTest {
    private val ana = PlayableProfile("E030000000000001", "Ana")
    private val bia = PlayableProfile("E030000000000002", "bia")
    private val caio = PlayableProfile("E030000000000003", "Caio")

    @Test fun oneProfilePlaysAtOnce() {
        assertEquals(Decision.Launch(ana.xuid, changes = false), ProfilePick.decide(listOf(ana), ana.xuid, askBeforePlaying = true))
        // The configured one is gone: the only profile left is signed in first.
        assertEquals(Decision.Launch(ana.xuid, changes = true), ProfilePick.decide(listOf(ana), "E0300000000000FF", true))
        assertEquals(Decision.Launch(null, changes = false), ProfilePick.decide(emptyList(), null, true))
    }

    @Test fun severalAskWithTheActiveOneFirst() {
        val asked = ProfilePick.decide(listOf(caio, bia, ana), bia.xuid.lowercase(), askBeforePlaying = true)
        assertEquals(Decision.Ask(listOf(bia, ana, caio), bia.xuid), asked)
        // No (valid) active profile: alphabetical, the first preselected.
        assertEquals(Decision.Ask(listOf(ana, bia, caio), ana.xuid), ProfilePick.decide(listOf(caio, bia, ana), null, true))
        // Duplicates from a damaged listing are shown once.
        assertEquals(2, (ProfilePick.decide(listOf(ana, ana, bia), null, true) as Decision.Ask).profiles.size)
    }

    @Test fun otherPlayersAreShownAndNotPickedByDefault() {
        // No active P1; Ana plays as P2: Bia is preselected, Ana is labelled.
        val slots = listOf(null, ana.xuid, null, null)
        assertEquals(Decision.Ask(listOf(bia, ana, caio), bia.xuid, mapOf(ana.xuid to 2)),
            ProfilePick.decide(listOf(caio, bia, ana), null, true, slots))
        assertEquals(Decision.Launch(bia.xuid, changes = true), ProfilePick.decide(listOf(ana, bia), null, false, slots))
        // Everyone already plays for another player: the first one anyway (choosing moves it).
        assertEquals(Decision.Launch(ana.xuid, changes = true),
            ProfilePick.decide(listOf(ana, bia), null, false, listOf(null, ana.xuid, bia.xuid, null)))
    }

    @Test fun notAskingPlaysTheActiveOne() {
        assertEquals(Decision.Launch(caio.xuid, changes = false), ProfilePick.decide(listOf(ana, caio), caio.xuid, askBeforePlaying = false))
        assertEquals(Decision.Launch(ana.xuid, changes = true), ProfilePick.decide(listOf(caio, ana), "missing", askBeforePlaying = false))
    }
}
