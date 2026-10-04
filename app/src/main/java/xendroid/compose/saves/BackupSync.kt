package xendroid.compose.saves

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import xendroid.compose.core.StorageAccess

/** SAF supports local or installed cloud providers; the user chooses the provider.
 * No credentials, vendor API or network upload is enabled unless explicitly configured. */
object BackupSync {
    fun configure(context: Context, title: String, uri: Uri) {
        require(title.matches(Regex("[0-9A-F]{8}")))
        context.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        // A new folder needs its own verified copy: forget what was published elsewhere.
        context.getSharedPreferences("backup-sync", Context.MODE_PRIVATE).edit().putString("tree_$title", uri.toString())
            .remove("published_fp_$title").apply()
    }
    fun configured(context: Context, title: String): Uri? = context.getSharedPreferences("backup-sync", Context.MODE_PRIVATE)
        .getString("tree_$title", null)?.let(Uri::parse)
    fun automatic(context: Context, title: String): Boolean = context.getSharedPreferences("backup-sync", Context.MODE_PRIVATE).getBoolean("auto_$title", false)
    fun setAutomatic(context: Context, title: String, enabled: Boolean) {
        context.getSharedPreferences("backup-sync", Context.MODE_PRIVATE).edit().putBoolean("auto_$title", enabled).apply()
    }

    suspend fun publish(context: Context, title: String): String = withContext(Dispatchers.IO) {
        val uri = configured(context, title) ?: error("Choose a backup folder first")
        val tree = DocumentFile.fromTreeUri(context, uri) ?: error("Backup folder unavailable")
        require(tree.canRead() && tree.canWrite()) { "Backup folder permission lost" }
        val coroutine = currentCoroutineContext()
        val store = StorageAccess.saveStore()
        val ids = store.profiles(title).map { it.xuid }
        val archive = File.createTempFile("backup-sync-", ".zip", context.cacheDir)
        val created = mutableMapOf<String, DocumentFile>()
        try {
            store.export(title, ids, archive, includeProfiles = true) { coroutine.ensureActive() }
            val sink = object : BackupReplicaSink {
                override fun exists(name: String) = tree.findFile(name) != null
                override fun read(name: String) = context.contentResolver.openInputStream(
                    (created[name] ?: tree.findFile(name) ?: error("Remote backup missing")).uri) ?: error("Remote backup not readable")
                override fun create(name: String): java.io.OutputStream {
                    require(tree.findFile(name) == null) { "Remote backup appeared concurrently" }
                    val document = tree.createFile("application/zip", name) ?: error("Cannot create backup")
                    created[name] = document
                    return context.contentResolver.openOutputStream(document.uri, "w") ?: error("Cannot upload backup")
                }
                override fun removeOwnFailedUpload(name: String) { created.remove(name)?.delete() }
            }
            BackupReplication.publish(title, archive, sink) { coroutine.ensureActive() }
                .also { context.getSharedPreferences("backup-sync", Context.MODE_PRIVATE).edit().putLong("published_at_$title", System.currentTimeMillis()).apply() }
        } finally { archive.delete() }
    }

    /** When a backup of [title] last reached the folder (this app's own record), or null. */
    fun lastPublished(context: Context, title: String): Long? =
        context.getSharedPreferences("backup-sync", Context.MODE_PRIVATE).getLong("published_at_$title", 0L).takeIf { it > 0L }

    suspend fun remoteBackups(context: Context, title: String): List<DocumentFile> = withContext(Dispatchers.IO) {
        val uri = configured(context, title) ?: return@withContext emptyList()
        DocumentFile.fromTreeUri(context, uri)?.listFiles().orEmpty().filter {
            it.isFile && it.name?.matches(Regex("xendroid-saves-$title-[0-9a-f]{64}\\.zip")) == true
        }.sortedByDescending { it.lastModified() }.take(32)
    }

    /** Called when frontend returns; ContentLease prevents reading a live guest's saves.
     * A title whose saves did not change since its last verified copy is skipped: no
     * export, no lease, no full read-back of the remote file on every library visit. */
    suspend fun publishAutomatic(context: Context) {
        val prefs = context.getSharedPreferences("backup-sync", Context.MODE_PRIVATE)
        val titles = prefs.all.keys.filter { it.matches(Regex("auto_[0-9A-F]{8}")) && prefs.getBoolean(it, false) }
        for (key in titles) {
            currentCoroutineContext().ensureActive()
            val title = key.removePrefix("auto_")
            runCatching {
                val fingerprint = withContext(Dispatchers.IO) { StorageAccess.saveStore().fingerprint(title) }
                if (prefs.getString("published_fp_$title", null) == fingerprint) return@runCatching
                publish(context, title)
                prefs.edit().putString("published_fp_$title", fingerprint).apply()
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Log.w("BackupSync", "Automatic backup not completed; local saves are unchanged", it)
            }
        }
    }
}
