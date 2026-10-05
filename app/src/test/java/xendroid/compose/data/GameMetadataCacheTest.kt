package xendroid.compose.data

import xendroid.compose.data.GameMetadataCache.Decision
import xendroid.compose.data.GameMetadataCache.Entry
import xendroid.compose.data.GameMetadataCache.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xendroid.compose.data.GameMetadataCache
import java.io.File

/**
 * Unit tests for the pure HIT/MISS decision (the bug-prone core: file-change
 * invalidation, the unreliable 0/-1 SAF signature, and partial cache clears) plus
 * a load/save/put round-trip. No SAF/JNI/Android: pure JVM.
 */
class GameMetadataCacheTest {

    @get:Rule val tmp = TemporaryFolder()

    private val freshEntry = Entry(name = "Halo 3", iconCacheName = "abc.png", sizeBytes = 100, lastModified = 200)
    private val matchingSig = Signature(sizeBytes = 100, lastModified = 200)
    private val iconExists: (String) -> Boolean = { true }
    private val iconMissing: (String) -> Boolean = { false }

    // ---- Signature.cacheable: only BOTH fields > 0 is trustworthy ----

    @Test fun signatureCacheableOnlyWhenBothPositive() {
        assertTrue(Signature(100, 200).cacheable)
        assertFalse(Signature(0, 200).cacheable)
        assertFalse(Signature(100, 0).cacheable)
        assertFalse(Signature(-1, 200).cacheable)
        assertFalse(Signature(100, -1).cacheable)
        assertFalse(Signature(0, 0).cacheable)
    }

    // ---- decide(): HIT path ----

    @Test fun hitWhenEntryAndSignatureMatchAndIconExists() {
        val d = GameMetadataCache.decide(freshEntry, matchingSig, iconExists)
        assertEquals(Decision.Hit("Halo 3", "abc.png"), d)
    }

    @Test fun hitWithNullIconSkipsIconExistenceCheck() {
        val noIcon = freshEntry.copy(iconCacheName = null)
        // iconMissing must never be consulted for a null-icon entry.
        val d = GameMetadataCache.decide(noIcon, matchingSig, iconMissing)
        assertEquals(Decision.Hit("Halo 3", null), d)
    }

    // ---- decide(): MISS paths ----

    @Test fun missWhenNoEntry() {
        assertEquals(Decision.Miss, GameMetadataCache.decide(null, matchingSig, iconExists))
    }

    @Test fun missWhenSizeChanged() {
        val d = GameMetadataCache.decide(freshEntry, Signature(101, 200), iconExists)
        assertEquals(Decision.Miss, d)
    }

    @Test fun missWhenLastModifiedChanged() {
        val d = GameMetadataCache.decide(freshEntry, Signature(100, 201), iconExists)
        assertEquals(Decision.Miss, d)
    }

    @Test fun missWhenSignatureNotCacheableEvenIfFieldsEqualCachedZero() {
        // A 0-signature is non-cacheable: must MISS even if the cached entry (defensively)
        // happened to carry the same 0 values -- we can't trust a 0/-1 signal.
        val zeroEntry = Entry("X", "i.png", sizeBytes = 0, lastModified = 0)
        val d = GameMetadataCache.decide(zeroEntry, Signature(0, 0), iconExists)
        assertEquals(Decision.Miss, d)
    }

    @Test fun missWhenIconFileVanished() {
        // Partial cache clear: entry + signature fresh, but the icon File is gone.
        val d = GameMetadataCache.decide(freshEntry, matchingSig, iconMissing)
        assertEquals(Decision.Miss, d)
    }

    // ---- put(): a non-cacheable signature is never stored ----

    @Test fun putSkipsNonCacheableSignature() {
        val cache = GameMetadataCache(tmp.newFolder())
        cache.put("uri", "Name", "i.png", Signature(0, 5))
        assertNull(cache.get("uri"))
        cache.put("uri2", "Name", "i.png", Signature(5, 0))
        assertNull(cache.get("uri2"))
    }

    @Test fun putStoresCacheableSignature() {
        val cache = GameMetadataCache(tmp.newFolder())
        cache.put("uri", "Name", "i.png", Signature(7, 9))
        assertEquals(Entry("Name", "i.png", 7, 9), cache.get("uri"))
    }

    // ---- load/save round-trip survives a fresh instance over the same dir ----

    @Test fun saveThenLoadRoundTrips() {
        val dir = tmp.newFolder()
        GameMetadataCache(dir).apply {
            load()
            put("uri-a", "Game A", "a.png", Signature(1, 2))
            put("uri-b", "Game B", null, Signature(3, 4))
            save()
        }
        val reloaded = GameMetadataCache(dir).apply { load() }
        assertEquals(Entry("Game A", "a.png", 1, 2), reloaded.get("uri-a"))
        assertEquals(Entry("Game B", null, 3, 4), reloaded.get("uri-b"))
    }

    @Test fun loadOnMissingFileIsColdNotCrash() {
        val cache = GameMetadataCache(tmp.newFolder())
        cache.load()
        assertNull(cache.get("anything"))
    }

    // ---- L09: moved files, instant list ----

    @Test fun aMovedOrRenamedFolderReusesTheExtraction() {
        val dir = tmp.newFolder()
        GameMetadataCache(dir).apply {
            load()
            put("/g/Halo 3.iso", "Halo 3", "h.png", Signature(10, 20), "4D5307E6", null, GameFormat.ISO)
            put("/g/Braid/default.xex", "Braid", null, Signature(5, 6), "58410A1A", null, GameFormat.XEX_FOLDER)
            put("/g/Other/default.xex", "Other", null, Signature(7, 8), "11111111", null, GameFormat.XEX_FOLDER)
            save()
        }
        val cache = GameMetadataCache(dir).apply { load() }
        assertEquals("Halo 3", cache.movedFrom("/sd/Xbox/Halo 3.iso", Signature(10, 20))?.name)
        assertEquals("Halo 3", cache.movedFrom("/sd/Xbox/halo 3.ISO", Signature(10, 20))?.name)
        assertNull(cache.movedFrom("/sd/Xbox/Halo 3.iso", Signature(10, 21)))      // modified: read again
        assertNull(cache.movedFrom("/sd/Xbox/Halo 3.iso", Signature(0, 20)))       // untrustworthy
        assertEquals("Braid", cache.movedFrom("/sd/Braid/default.xex", Signature(5, 6))?.name)
        assertNull(cache.movedFrom("/sd/Renamed/default.xex", Signature(5, 6)))    // can't tell which game
        // The scan re-keys it to the new path; the old one goes with retainOnly.
        cache.put("/sd/Xbox/Halo 3.iso", "Halo 3", "h.png", Signature(10, 20), "4D5307E6", null, GameFormat.ISO)
        cache.retainOnly(setOf("/sd/Xbox/Halo 3.iso"))
        assertNull(cache.get("/g/Halo 3.iso"))
        assertEquals(Decision.Hit("Halo 3", "h.png", "4D5307E6", null, GameFormat.ISO),
            GameMetadataCache.decide(cache.get("/sd/Xbox/Halo 3.iso"), Signature(10, 20), iconExists))
    }

    @Test fun lastTimesListIsReadWithoutDisturbingAScan() {
        val dir = tmp.newFolder()
        val cache = GameMetadataCache(dir).apply {
            load()
            put("/g/b.iso", "Banjo", null, Signature(1, 2), "11111111", null, GameFormat.ISO)
            put("/g/A/default.xex", "Alan", "a.png", Signature(3, 4), "22222222", "M1", GameFormat.XEX_FOLDER, 1, 2)
            put("/other/c.iso", "Crackdown", null, Signature(5, 6), null, null, GameFormat.ISO)
            put("/g/legacy", "Legacy GOD", null, Signature(7, 8))
            save()
        }
        cache.put("/g/new.iso", "Not saved yet", null, Signature(9, 9))   // a running scan's working set
        val games = GameMetadataCache.gamesUnder(cache.peek(), listOf("/g/"))
        assertEquals(listOf("Alan", "Banjo", "Legacy GOD"), games.map { it.name })
        assertEquals(Game("/g/A/default.xex", "Alan", GameFormat.XEX_FOLDER, "a.png", "22222222", "M1", 1, 2), games[0])
        assertEquals(GameFormat.GOD, games[2].format)
        assertEquals("Not saved yet", cache.get("/g/new.iso")?.name)        // peek left it alone
        assertEquals(emptyMap<String, Entry>(), GameMetadataCache(tmp.newFolder()).peek())
    }

    @Test fun loadOnCorruptFileIsColdNotCrash() {
        val dir = tmp.newFolder()
        File(dir, GameMetadataCache.FILE_NAME).writeText("{ not valid json ]")
        val cache = GameMetadataCache(dir)
        cache.load()  // must not throw
        assertNull(cache.get("anything"))
    }
}
