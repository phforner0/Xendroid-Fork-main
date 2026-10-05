package xendroid.compose.ui.library

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import xendroid.compose.R

/** A browsable storage root: internal storage or a mounted removable volume. */
data class StorageRoot(val label: String, val dir: File, val removable: Boolean)

/**
 * Lote 6: paths said the way the phone's Files app says them: "Internal storage › Games › Xbox 360"
 * instead of /storage/emulated/0/Games/Xbox 360, a mounted SD card or USB drive by its name. A
 * path on no known volume (an SD card that is out, say) is shown as it is.
 */
object StoragePaths {
    /** Primary storage plus mounted removable volumes (API 30+; primary-only below). */
    fun roots(context: Context): List<StorageRoot> {
        val primary = Environment.getExternalStorageDirectory() ?: File("/")
        val roots = mutableListOf(StorageRoot(context.getString(R.string.browse_internal), primary, removable = false))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                for (volume in sm.storageVolumes) {
                    if (volume.isPrimary) continue
                    if (volume.state != Environment.MEDIA_MOUNTED && volume.state != Environment.MEDIA_MOUNTED_READ_ONLY) continue
                    val dir = volume.directory ?: continue
                    roots.add(StorageRoot(volume.getDescription(context) ?: dir.name, dir, removable = true))
                }
            } catch (_: Exception) {
            }
        }
        return roots
    }

    /** The root [path] lies in (the deepest one), or null. */
    fun rootOf(path: String, roots: List<StorageRoot>): StorageRoot? =
        roots.filter { inside(path, it.dir.absolutePath) }.maxByOrNull { it.dir.absolutePath.length }

    /** [path] with its root named: "Internal storage › Games › Xbox 360"; a root alone is its name. */
    fun display(path: String, roots: List<StorageRoot>): String {
        val primary = roots.firstOrNull { !it.removable }
        // /sdcard is the same place as the primary storage under another name.
        val real = if (primary != null && inside(path, "/sdcard")) primary.dir.absolutePath + path.removePrefix("/sdcard") else path
        val root = rootOf(real, roots) ?: return path
        val below = real.removePrefix(root.dir.absolutePath).split('/').filter { it.isNotEmpty() }
        return (listOf(root.label) + below).joinToString(" › ")
    }

    private fun inside(path: String, dir: String): Boolean = path == dir || path.startsWith(dir.trimEnd('/') + "/")
}

/** The storage roots, read once per screen. */
@Composable
fun rememberStorageRoots(): List<StorageRoot> {
    val context = LocalContext.current
    return remember { StoragePaths.roots(context) }
}

/** [path] as the phone's Files app says it ([StoragePaths.display]). */
@Composable
fun displayPath(path: String): String = StoragePaths.display(path, rememberStorageRoots())
