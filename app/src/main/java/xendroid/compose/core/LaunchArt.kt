package xendroid.compose.core

import java.io.File
import xendroid.compose.data.CoverStore

/**
 * 15e: the picture behind the loading screen (Bannerlator `e2acda52`): the cover the library
 * passed with the launch, or, once the game's Title ID is known (a shortcut or another app
 * started it), the cover the library keeps for that title. The game activity is exported, so
 * a path that came with an intent is used only when it is one of this app's own files.
 */
object LaunchArt {
    const val MAX_BYTES = 8L * 1024 * 1024

    fun fromExtra(path: String?, roots: List<File>): File? {
        if (path.isNullOrBlank()) return null
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val inside = roots.any { root ->
            runCatching { file.path.startsWith(root.canonicalFile.path + File.separator) }.getOrDefault(false)
        }
        return file.takeIf { inside && it.isFile && it.length() in 1..MAX_BYTES }
    }

    fun forTitle(covers: CoverStore, titleId: String?): File? =
        covers.displayCover(titleId, null)?.takeIf { it.length() in 1..MAX_BYTES }
}
