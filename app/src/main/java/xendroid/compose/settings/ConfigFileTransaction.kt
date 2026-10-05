package xendroid.compose.settings

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/** Serialize read/modify/replace across threads AND the frontend/:emu processes. */
object ConfigFileTransaction {
    private val monitors = ConcurrentHashMap<String, Any>()

    /** A null result removes an empty override file. A failed transform leaves disk untouched. */
    fun update(file: File, transform: (String?) -> String?) {
        val target = file.canonicalFile
        val parent = requireNotNull(target.parentFile)
        synchronized(monitors.computeIfAbsent(target.path) { Any() }) {
            check(parent.isDirectory || parent.mkdirs()) { "Cannot create config directory" }
            // The lock has its own stable inode: the target is atomically replaced on commit.
            RandomAccessFile(File(parent, ".${target.name}.lock"), "rw").use { lockFile ->
                lockFile.channel.lock().use {
                    val original = if (target.exists()) target.readText() else null
                    val updated = transform(original)
                    if (updated == original) return
                    if (updated == null) {
                        check(!target.exists() || target.delete()) { "Cannot remove empty override" }
                        return
                    }
                    val staged = File.createTempFile(".${target.name}.", ".tmp", parent)
                    try {
                        FileOutputStream(staged).use { output ->
                            output.write(updated.toByteArray(Charsets.UTF_8))
                            output.fd.sync()
                        }
                        // No truncate-then-rewrite: malformed TOML, I/O or rename failure leaves
                        // the previous config available. Never fall back to deleting the target.
                        Files.move(staged.toPath(), target.toPath(),
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    } finally {
                        staged.delete()
                    }
                }
            }
        }
    }
}
