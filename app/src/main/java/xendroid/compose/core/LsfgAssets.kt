package xendroid.compose.core

import android.content.Context
import android.net.Uri
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import xendroid.compose.archive.ArchiveFiles
import xendroid.compose.archive.ContentLease

/** Proprietary data is supplied only by the user. Never package DLL/cache in assets. */
object LsfgAssets {
    private const val COMPILER = "banner-15d4f73c-dxbc-v1"
    /** (path, length, mtime, checksum) last proven valid: opening the in-game menu would
     *  otherwise hash up to 64 MB every time. Any change of the file re-verifies it. */
    @Volatile private var verified: List<Any>? = null

    fun directory(context: Context): File = File(context.filesDir, "lsfg-native")
    fun cache(context: Context): File? {
        val root = directory(context)
        val id = File(root, "selected").takeIf { it.isFile }?.readText()?.trim() ?: return null
        if (!id.matches(Regex("[0-9a-f]{64}"))) return null
        val file = File(root, "$id/$COMPILER.cache")
        val checksum = File(root, "$id/cache.sha256").takeIf { it.isFile }?.readText()?.trim() ?: return null
        if (!file.isFile || file.length() !in 1..64L * 1024 * 1024) return null
        val key = listOf(file.path, file.length(), file.lastModified(), checksum)
        if (verified == key) return file
        return file.takeIf { ArchiveFiles.sha256(it) == checksum }?.also { verified = key }
    }

    suspend fun import(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        val root = directory(context).apply { mkdirs() }
        ContentLease.acquire(File(context.filesDir, "lsfg-import-lease")).use {
            val temp = File.createTempFile("dll-", ".tmp", root)
            try {
                context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024); var bytes = 0L
                    while (true) {
                        coroutine.ensureActive()
                        val n = input.read(buffer); if (n < 0) break
                        bytes += n; require(bytes <= 64L * 1024 * 1024) { "DLL exceeds import limit" }
                        output.write(buffer, 0, n)
                    }
                } } ?: error("Cannot read the DLL")
                val id = ArchiveFiles.sha256(temp)
                val target = File(root, id).apply { mkdirs() }
                val cache = File(target, "$COMPILER.cache")
                EmulatorRuntime.ensureLoaded()
                val result = EmulatorRuntime.emulator!!.build_lsfg_cache(temp.path, cache.path)
                require(result == 0) { "LSFG DLL extraction/translation failed (status $result)" }
                coroutine.ensureActive()
                ArchiveFiles.atomicText(File(target, "cache.sha256"), ArchiveFiles.sha256(cache))
                ArchiveFiles.atomicText(File(root, "selected"), id)
                cache
            } finally { temp.delete() }
        }
    }

    /** Imported shaders are private runtime data; clearing never removes source code. */
    fun clear(context: Context) {
        verified = null
        ContentLease.acquire(File(context.filesDir, "lsfg-import-lease")).use { ArchiveFiles.deleteTree(directory(context)) }
    }
}
