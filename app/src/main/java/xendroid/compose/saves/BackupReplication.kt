package xendroid.compose.saves

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import xendroid.compose.archive.ArchiveFiles

interface BackupReplicaSink {
    fun exists(name: String): Boolean
    fun read(name: String): InputStream
    fun create(name: String): OutputStream
    fun removeOwnFailedUpload(name: String)
}

/** Immutable replicas, named by title and SHA-256. Never overwrite remote saves or
 * silently resolve a conflict; restoration always uses the ordinary preview flow. */
object BackupReplication {
    fun publish(titleId: String, archive: File, sink: BackupReplicaSink, checkCancelled: () -> Unit = {}): String {
        require(titleId.matches(Regex("[0-9A-F]{8}")) && archive.length() <= 512L * 1024 * 1024)
        val digest = ArchiveFiles.sha256(archive)
        val name = "xendroid-saves-$titleId-$digest.zip"
        if (sink.exists(name)) {
            require(sink.read(name).use { boundedHash(it, archive.length(), checkCancelled) } == digest) { "Remote backup with matching name is damaged" }
            return name
        }
        try {
            sink.create(name).use { output -> archive.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkCancelled()
                    val n = input.read(buffer); if (n < 0) break
                    output.write(buffer, 0, n)
                }
            } }
            require(sink.read(name).use { boundedHash(it, archive.length(), checkCancelled) } == digest) { "Uploaded backup could not be verified" }
            return name
        } catch (e: Exception) {
            runCatching { sink.removeOwnFailedUpload(name) }
            throw e
        }
    }

    private fun boundedHash(input: InputStream, expectedSize: Long, cancel: () -> Unit): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            cancel(); val n = input.read(buffer); if (n < 0) break
            bytes += n; require(bytes <= expectedSize) { "Remote backup exceeds expected size" }
            digest.update(buffer, 0, n)
        }
        require(bytes == expectedSize) { "Remote backup is incomplete" }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
