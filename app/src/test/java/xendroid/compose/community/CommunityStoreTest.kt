package xendroid.compose.community

import java.io.File
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CommunityStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private var now = 1_000L
    private fun store(dir: File = File(folder.root, "community")) = CommunityStore(dir, clock = { now })
    private val token = "T".repeat(32)

    @Test fun theSecretIsMadeOnceAndKept() {
        val first = store().secret()
        assertEquals(32, first.size)
        assertArrayEquals(first, store().secret())
        assertTrue(File(folder.root, "community/secret").readText().matches(Regex("[0-9a-f]{64}")))
        // A damaged file is replaced, not trusted.
        File(folder.root, "community/secret").writeText("zz")
        assertFalse(first.contentEquals(CommunityStore(File(folder.root, "community"), random = SecureRandom()).secret()))
    }

    @Test fun ownUploadsKeepTheirDeleteToken() {
        val s = store()
        s.addUpload("4d5307e6", "Steady 30", CommunityConfigs.Receipt("aaaaaaaa", token))
        now = 2_000L
        s.addUpload("415607E6", "Other", CommunityConfigs.Receipt("bbbbbbbb", token))
        assertEquals(listOf(OwnUpload("aaaaaaaa", "4D5307E6", "Steady 30", token, 1_000L),
            OwnUpload("bbbbbbbb", "415607E6", "Other", token, 2_000L)), store().uploads())
        s.removeUpload("aaaaaaaa")
        assertEquals(listOf("bbbbbbbb"), store().uploads().map { it.configId })
        s.removeUpload("unknown1")
        assertEquals(1, store().uploads().size)
    }

    @Test fun votesAreRememberedAndBounded() {
        val s = store()
        s.setVote("aaaaaaaa", 1)
        s.setVote("bbbbbbbb", -1)
        s.setVote("aaaaaaaa", 0)
        assertEquals(mapOf("bbbbbbbb" to -1), store().votes())
        repeat(CommunityStore.MAX_VOTES + 5) { s.setVote("v%07d".format(it), 1) }
        val votes = store().votes()
        assertEquals(CommunityStore.MAX_VOTES, votes.size)
        assertTrue("v%07d".format(CommunityStore.MAX_VOTES + 4) in votes)
        assertFalse("bbbbbbbb" in votes)
    }

    @Test fun theLastListIsKeptPerGame() {
        val s = store()
        assertNull(s.cached("4D5307E6"))
        now = 5_000L
        s.cache("4d5307e6", "{\"x\":1}")
        assertEquals(CachedList("4D5307E6", 5_000L, "{\"x\":1}"), store().cached("4D5307E6"))
        assertNull(store().cached("415607E6"))
        try {
            s.cache("../../x", "{}")
            throw AssertionError("a path was accepted as a Title ID")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test fun damagedFilesReadAsNothing() {
        val dir = File(folder.root, "community").apply { mkdirs() }
        File(dir, "uploads.json").writeText("[{\"configId\":\"../x\",\"titleId\":\"4D5307E6\",\"name\":\"n\",\"deleteToken\":\"$token\",\"uploadedAt\":1}]")
        File(dir, "votes.json").writeText("not json")
        assertEquals(emptyList<OwnUpload>(), store().uploads())
        assertEquals(emptyMap<String, Int>(), store().votes())
    }
}
