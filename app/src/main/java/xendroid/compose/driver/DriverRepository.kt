package xendroid.compose.driver

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class DriverInfo(
    val name: String,
    val version: String,
    val url: String,
    val sha256: String = "",
    /** 15p: the GitHub repository ("owner/repo") the release came from, and when it was published (ISO). */
    val source: String = "",
    val publishedAt: String = "",
)

/** 15p: what the sources gave, and the ones that could not be read (source to reason). */
data class DriverListing(val drivers: List<DriverInfo>, val failures: List<Pair<String, String>>)

/** GitHub release assets may publish a digest; absent/malformed values are not verified. */
fun githubAssetSha256(digest: String?): String? =
    digest?.takeIf { it.matches(Regex("(?i)sha256:[0-9a-f]{64}")) }
        ?.substringAfter(':')

object DriverRepository {

    /** 15p: every source's releases, in the order of [sources]; one that fails does not stop the others. */
    suspend fun loadDrivers(sources: List<String> = listOf(DriverSources.DEFAULT)): DriverListing = withContext(Dispatchers.IO) {
        val drivers = mutableListOf<DriverInfo>()
        val failures = mutableListOf<Pair<String, String>>()
        for (source in sources) {
            currentCoroutineContext().ensureActive()
            runCatching { DriverReleases.parse(fetch(DriverSources.releasesApi(source)), source) }
                .onSuccess { drivers += it }
                .onFailure { failures += source to (it.message ?: it.javaClass.simpleName) }
        }
        DriverListing(drivers, failures)
    }

    private fun fetch(api: String): String {
        val connection = URL(api).openConnection() as HttpURLConnection

        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "XenDroid")

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("GitHub API HTTP ${connection.responseCode}")
            }
            // A releases page is small; a source that answers with megabytes is not one.
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    out.write(buffer, 0, count)
                    require(out.size() <= 4 * 1024 * 1024) { "release list over 4 MB" }
                }
                out.toByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadDriver(
        context: Context,
        driver: DriverInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {

        val directory = File(context.cacheDir, "driver_downloads")

        if (!directory.exists() && !directory.mkdirs()) {
            throw Exception("Unable to create download directory")
        }

        val safeName = "${driver.version}-${driver.name}".replace(
            Regex("[^A-Za-z0-9._-]"),
            "_"
        ).take(140)

        val file = File(directory, safeName)
        val tmp = File.createTempFile(".download-", ".zip", directory)
        val coroutine = currentCoroutineContext()

        val connection = URL(driver.url).openConnection() as HttpURLConnection

        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "XenDroid")

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Download HTTP ${connection.responseCode}")
            }

            val total = connection.contentLengthLong

            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    var downloaded = 0L

                    while (true) {
                        coroutine.ensureActive()
                        val count = input.read(buffer)

                        if (count == -1) {
                            break
                        }

                        output.write(buffer, 0, count)
                        downloaded += count
                        require(downloaded <= 64L * 1024 * 1024) { "Driver download exceeds 64 MB" }

                        if (total > 0) {
                            onProgress(
                                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            )
                        }
                    }
                }
            }

            if (driver.sha256.isNotBlank()) {
                val actualHash = sha256(tmp)

                if (!actualHash.equals(driver.sha256, ignoreCase = true)) {
                    throw Exception("SHA-256 verification failed")
                }
            }

            java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            file
        } finally {
            tmp.delete()
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        file.inputStream().use { input ->
            val buffer = ByteArray(16384)

            while (true) {
                val count = input.read(buffer)

                if (count == -1) {
                    break
                }

                digest.update(buffer, 0, count)
            }
        }

        return digest.digest().joinToString("") {
            "%02x".format(it)
        }
    }
}
