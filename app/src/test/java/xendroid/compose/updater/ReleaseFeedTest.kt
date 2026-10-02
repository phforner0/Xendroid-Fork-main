package xendroid.compose.updater

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReleaseFeedTest {
    @get:Rule val folder = TemporaryFolder()

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun apk(name: String = "XenDroid_Release_abc1234.apk", sha256: String? = "0".repeat(64)) =
        ReleaseAsset(name, 45_000_000, "https://github.com/x/$name", sha256)
    private fun release(code: Int, prerelease: Boolean = false, draft: Boolean = false, assets: List<ReleaseAsset> = listOf(apk())) =
        FeedRelease("XenDroid-v$code-abc12${code % 10}0", "build $code", "notes", "https://github.com/r/$code", prerelease, draft, assets)

    @Test fun digestsAreOnlyAcceptedInGithubsFormat() {
        assertEquals("ab".repeat(32), parseAssetDigest("sha256:" + "AB".repeat(32)))
        assertNull(parseAssetDigest("md5:abc"))
        assertNull(parseAssetDigest("sha256:xyz"))
        assertNull(parseAssetDigest(null))
    }

    @Test fun theApkAssetIsTheReleaseApk() {
        val notes = ReleaseAsset("notes.txt", 10, "https://x", null)
        assertEquals("XenDroid_Release_abc1234.apk", apkAsset(release(5, assets = listOf(notes, apk())))!!.name)
        // Two APKs without the release name: ambiguous, nothing is offered.
        assertNull(apkAsset(release(5, assets = listOf(apk("a.apk"), apk("b.apk")))))
        assertEquals("only.apk", apkAsset(release(5, assets = listOf(apk("only.apk"))))!!.name)
        assertNull(apkAsset(release(5, assets = listOf(notes))))
    }

    @Test fun theNewestEligibleReleaseOfTheChannelIsOffered() {
        val feed = listOf(release(10), release(12, prerelease = true), release(11), release(13, draft = true))
        assertEquals(11, chooseUpdate(feed, UpdateChannel.STABLE, 9, "abc", null)!!.tag!!.versionCode)
        assertEquals(12, chooseUpdate(feed, UpdateChannel.PREVIEW, 9, "abc", null)!!.tag!!.versionCode)
        assertNull(chooseUpdate(feed, UpdateChannel.OFF, 9, "abc", null))
        // Skipping 11 hides it and anything older; a newer release would still come.
        assertNull(chooseUpdate(feed, UpdateChannel.STABLE, 9, "abc", skippedVersionCode = 11))
        assertEquals(12, chooseUpdate(feed, UpdateChannel.PREVIEW, 9, "abc", skippedVersionCode = 11)!!.tag!!.versionCode)
        // Local builds (version code 1) and releases without an APK are never offered.
        assertNull(chooseUpdate(feed, UpdateChannel.STABLE, 1, "abc", null))
        assertNull(chooseUpdate(listOf(release(20, assets = emptyList())), UpdateChannel.STABLE, 9, "abc", null))
        // Old tags without a version code are never offered.
        assertNull(chooseUpdate(listOf(release(20).copy(tagName = "XenDroid-abc1234")), UpdateChannel.STABLE, 9, "x", null))
    }

    @Test fun aDownloadIsKeptOnlyWhenSizeAndHashMatch() {
        val data = ByteArray(300_000) { (it % 251).toByte() }
        val out = File(folder.root, "updates/update.apk")
        var progress = 0L
        val kept = downloadVerified(ByteArrayInputStream(data), data.size.toLong(), sha(data), out) { progress = it }
        assertArrayEquals(data, kept.readBytes())
        assertEquals(data.size.toLong(), progress)

        fun fails(input: ByteArray, size: Long, sha256: String, message: String) {
            out.delete()
            val e = assertThrows(UpdateDownloadException::class.java) { downloadVerified(ByteArrayInputStream(input), size, sha256, out) }
            assertTrue(e.message, e.message!!.startsWith(message))
            assertFalse(out.exists())
            assertEquals(emptyList<String>(), out.parentFile!!.list()!!.filter { it.endsWith(".part") })
        }
        fails(data.copyOf(1000), data.size.toLong(), sha(data), "The download stopped early")
        fails(data + byteArrayOf(1), data.size.toLong(), sha(data), "The download is larger")
        fails(data.copyOf().also { it[5] = 99 }, data.size.toLong(), sha(data), "The download does not match")
        fails(data, data.size.toLong(), "nope", "The release publishes no SHA-256")
        fails(data, 0, sha(data), "Unexpected update size")
        val cancelled = assertThrows(UpdateDownloadException::class.java) {
            downloadVerified(ByteArrayInputStream(data), data.size.toLong(), sha(data), out, isCancelled = { true })
        }
        assertEquals("Download cancelled", cancelled.message)
    }
}
