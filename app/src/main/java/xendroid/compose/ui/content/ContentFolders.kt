package xendroid.compose.ui.content

import android.content.Context
import androidx.core.content.edit

/**
 * Round 2: folders where title updates and DLC wait to be installed, besides Downloads. They are
 * searched with their subfolders (see [ContentCatalog.packagesUnder]); the order is the player's.
 */
object ContentFolders {
    private const val PREFS = "content_folders"
    private const val KEY = "folders"

    fun read(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun add(context: Context, path: String) {
        val clean = path.trimEnd('/').ifEmpty { "/" }
        write(context, (read(context) + clean).distinct())
    }

    fun remove(context: Context, path: String) = write(context, read(context) - path)

    private fun write(context: Context, folders: List<String>) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, folders.joinToString("\n")) }
}
