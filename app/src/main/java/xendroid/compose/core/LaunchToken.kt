package xendroid.compose.core

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A secret only this app knows, so the :emu host can tell a launch from the library (which may
 * carry [LaunchOptions]) from one another app or a shortcut sent: the activity is exported for
 * shortcuts, and an intent's referrer can be forged. Kept in a private file (both processes read
 * it); never put in a shortcut, so a launcher never learns it.
 */
object LaunchToken {
    private const val NAME = "launch.token"

    /** The token, made on first use. */
    @Synchronized
    fun get(filesDir: File): String {
        val file = File(filesDir, NAME)
        read(file)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        val tmp = File(filesDir, "$NAME.tmp")
        tmp.writeText(token)
        if (!tmp.renameTo(file)) { tmp.delete(); return read(file) ?: error("Launch token not written") }
        return token
    }

    /** True when [candidate] is this app's token (compared in constant time). */
    fun matches(filesDir: File, candidate: String?): Boolean {
        if (candidate.isNullOrEmpty()) return false
        val token = read(File(filesDir, NAME)) ?: return false
        return MessageDigest.isEqual(token.toByteArray(), candidate.toByteArray())
    }

    private fun read(file: File): String? = runCatching {
        if (!file.isFile || file.length() != 64L) null else file.readText().takeIf { it.matches(Regex("[0-9a-f]{64}")) }
    }.getOrNull()
}
