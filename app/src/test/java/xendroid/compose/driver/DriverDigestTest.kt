package xendroid.compose.driver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriverDigestTest {
    @Test fun parsesOnlyFullGithubSha256Digests() {
        val hash = "a9".repeat(32)
        assertEquals(hash, githubAssetSha256("sha256:$hash"))
        assertEquals(hash, githubAssetSha256("SHA256:$hash"))
        assertNull(githubAssetSha256("sha256:${hash.dropLast(2)}"))
        assertNull(githubAssetSha256("sha512:$hash"))
        assertNull(githubAssetSha256("sha256:$hash trailing"))
        assertNull(githubAssetSha256(null))
    }
}
