package xendroid.compose.data

import java.io.File

/**
 * Round 2: the folders the app suggests on the phone's storage: XenDroid/<games> (the word in
 * the app's language), XenDroid/TU and XenDroid/DLC. Creating them twice is harmless; null when
 * one cannot be created (no storage access, read-only volume).
 */
object StandardFolders {
    class Made(val games: File, val updates: File, val dlc: File) {
        val all: List<File> get() = listOf(games, updates, dlc)
    }

    fun under(root: File, gamesName: String): Made {
        val base = File(root, "XenDroid")
        return Made(File(base, gamesName), File(base, "TU"), File(base, "DLC"))
    }

    fun create(root: File, gamesName: String): Made? =
        under(root, gamesName).takeIf { made -> made.all.all { it.isDirectory || it.mkdirs() } }
}
