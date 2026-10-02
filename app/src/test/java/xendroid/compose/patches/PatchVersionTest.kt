package xendroid.compose.patches

import org.junit.Assert.assertEquals
import org.junit.Test
import xendroid.compose.patches.PatchVersion.Match

class PatchVersionTest {
    private val game = listOf("0000A1B2C3D4E5F6", "1122334455667788")    // the executable, then a DLL

    @Test fun aFileIsForTheGameWhenOneOfItsHashesWasLoaded() {
        assertEquals(Match.YOURS, PatchVersion.match(listOf("A1B2C3D4E5F6"), game))          // leading zeros optional
        assertEquals(Match.YOURS, PatchVersion.match(listOf("ffffffffffffffff", "1122334455667788"), game))
        assertEquals(Match.YOURS, PatchVersion.match(listOf("0xa1b2c3d4e5f6"), game))         // any case, 0x
        assertEquals(Match.OTHER, PatchVersion.match(listOf("A1B2C3D4E5F7"), game))
    }

    @Test fun withoutTheGamesHashesOrUsableFileHashesNothingIsSaid() {
        assertEquals(Match.UNKNOWN, PatchVersion.match(listOf("A1B2C3D4E5F6"), emptyList()))
        assertEquals(Match.UNKNOWN, PatchVersion.match(emptyList(), game))
        assertEquals(Match.UNKNOWN, PatchVersion.match(listOf("not-a-hash", "11223344556677889"), game))
    }
}
