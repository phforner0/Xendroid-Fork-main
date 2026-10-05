package xendroid.compose.data

import org.junit.Assert.*
import org.junit.Test

class GameIdentityTest {
    private val forza = Game("/storage/emulated/0/ROMs/Forza.iso", "Forza", GameFormat.ISO,
        titleId = "4d5309c9", mediaId = "abcdef12", discNumber = 1, discCount = 1)

    @Test fun identitySurvivesMovingTheFileButNotAnotherDisc() {
        val moved = forza.copy(launchUri = "/storage/ABCD-1234/Games/Forza Horizon.iso", name = "Forza Horizon")
        assertEquals(forza.identityKey, moved.identityKey)
        assertEquals("title:4D5309C9:ABCDEF12:1", forza.identityKey)
        assertNotEquals(forza.identityKey, forza.copy(discNumber = 2).identityKey)
        // No usable Title ID: the URI is the only identity.
        assertEquals("uri:/x.iso", Game("/x.iso", "X", GameFormat.ISO, titleId = "00000000").identityKey)
        assertEquals("uri:/y.zar", Game("/y.zar", "Y", GameFormat.ZAR).identityKey)
    }

    @Test fun legacyUriFavoritesStillCountAndAreReplacedOnToggle() {
        val legacy = setOf(forza.launchUri, "uri:/other.iso")
        assertTrue(isFavorite(forza, legacy))
        val removed = toggledFavorites(forza, legacy)
        assertEquals(setOf("uri:/other.iso"), removed)
        val added = toggledFavorites(forza, removed)
        assertEquals(setOf("uri:/other.iso", forza.identityKey), added)
        assertTrue(isFavorite(forza.copy(launchUri = "/new/place.iso"), added))
    }
}
