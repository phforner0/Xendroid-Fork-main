package xendroid.compose.updater

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** R02: Stable follows the latest release, Preview also takes prereleases, Off never checks. */
enum class UpdateChannel(val label: String) { STABLE("Stable"), PREVIEW("Preview"), OFF("Off") }

data class ReleaseAsset(val name: String, val size: Long, val url: String, val sha256: String?)

data class FeedRelease(
    val tagName: String,
    val title: String?,
    val notes: String?,
    val pageUrl: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<ReleaseAsset>,
) {
    val tag: ReleaseTag? get() = parseReleaseTag(tagName)
}

/** GitHub publishes asset digests as "sha256:<hex>"; anything else counts as absent. */
fun parseAssetDigest(digest: String?): String? =
    digest?.trim()?.lowercase()?.takeIf { it.matches(Regex("sha256:[0-9a-f]{64}")) }?.removePrefix("sha256:")

/** The installable APK of a release: CI uploads exactly one `XenDroid_Release_<sha>.apk`. */
fun apkAsset(release: FeedRelease): ReleaseAsset? {
    val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) && it.size > 0 }
    return apks.singleOrNull { it.name.startsWith("XenDroid_Release_") } ?: apks.singleOrNull()
}

/**
 * The release to offer, or null: newest by version code within the [channel], newer
 * than the installed build, not [skippedVersionCode] or older, and with an APK. A
 * release without a version code (old tag format) is never offered.
 */
fun chooseUpdate(releases: List<FeedRelease>, channel: UpdateChannel, installedVersionCode: Int,
                 installedCommit: String, skippedVersionCode: Int?): FeedRelease? {
    if (channel == UpdateChannel.OFF) return null
    return releases
        .filter { !it.draft && (channel == UpdateChannel.PREVIEW || !it.prerelease) }
        .mapNotNull { release -> release.tag?.let { tag -> release to tag } }
        .filter { (release, tag) ->
            isUpdateFor(tag, installedVersionCode, installedCommit) && apkAsset(release) != null &&
                (skippedVersionCode == null || (tag.versionCode ?: 0) > skippedVersionCode)
        }
        .maxByOrNull { (_, tag) -> tag.versionCode ?: 0 }?.first
}

/** Why a download cannot be used: [reason] and its [args] for the screen to say in its language
 *  (U02); the English text is the message (logs, tests). */
class UpdateDownloadException(val reason: Reason, vararg val args: Long) :
    Exception(reason.english.format(*args.toTypedArray())) {
    enum class Reason(val english: String) {
        NO_APK("The release has no APK"),
        NO_SHA256("The release publishes no SHA-256 for its APK"),
        HTTP("Download failed (HTTP %d)"),
        EMPTY("Empty download"),
        UNEXPECTED_SIZE("Unexpected update size (%d bytes)"),
        CANCELLED("Download cancelled"),
        TOO_LARGE("The download is larger than the release says"),
        STOPPED_EARLY("The download stopped early (%d of %d bytes)"),
        MISMATCH("The download does not match the release's SHA-256"),
        NOT_KEPT("Could not keep the download"),
    }
}

/**
 * R03: copies [input] to [destination] only if it is exactly [expectedSize] bytes with
 * the SHA-256 [expectedSha256]: written to a temp file, hashed while streaming, renamed
 * at the end. Anything else leaves no file behind. [onProgress] gets bytes so far.
 */
fun downloadVerified(input: InputStream, expectedSize: Long, expectedSha256: String, destination: File,
                     maxBytes: Long = 256L * 1024 * 1024, isCancelled: () -> Boolean = { false },
                     onProgress: (Long) -> Unit = {}): File {
    if (expectedSize <= 0 || expectedSize > maxBytes) throw UpdateDownloadException(UpdateDownloadException.Reason.UNEXPECTED_SIZE, expectedSize)
    if (!expectedSha256.matches(Regex("[0-9a-f]{64}"))) throw UpdateDownloadException(UpdateDownloadException.Reason.NO_SHA256)
    destination.parentFile?.mkdirs()
    val temporary = File(destination.parentFile, ".${destination.name}.part")
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        temporary.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (isCancelled()) throw UpdateDownloadException(UpdateDownloadException.Reason.CANCELLED)
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > expectedSize) throw UpdateDownloadException(UpdateDownloadException.Reason.TOO_LARGE)
                digest.update(buffer, 0, n)
                out.write(buffer, 0, n)
                onProgress(total)
            }
            out.fd.sync()
        }
        if (total != expectedSize) throw UpdateDownloadException(UpdateDownloadException.Reason.STOPPED_EARLY, total, expectedSize)
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expectedSha256) throw UpdateDownloadException(UpdateDownloadException.Reason.MISMATCH)
        destination.delete()
        if (!temporary.renameTo(destination)) throw UpdateDownloadException(UpdateDownloadException.Reason.NOT_KEPT)
        return destination
    } finally {
        temporary.delete()
    }
}
