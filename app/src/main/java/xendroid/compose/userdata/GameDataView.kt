package xendroid.compose.userdata

import java.io.File

/**
 * L07: one game's data gathered in one place for the file manager: its folders under content
 * (a profile's saves and data under that profile's XUID; DLC and title updates under the
 * console's own), its per-game config and its patch files. Only what [visible] allows (the
 * same rules as [UserDataFiles]); system titles (FFFE…) are left out. The provider shows these
 * as read-only gathering folders whose entries are the real files, so every change still goes
 * through the real folder's rules. Pure listing, tested on the JVM.
 */
class GameDataView(private val root: File, private val visible: (File) -> Boolean) {
    enum class Kind { PROFILE_DATA, CONSOLE_DATA, CONFIG, PATCH }

    /** [owner]: the XUID whose folder it is, for profile data. */
    data class Entry(val file: File, val kind: Kind, val owner: String? = null)

    private val content get() = File(root, "content")
    private val config get() = File(root, "config")
    private val patches get() = File(root, "patches")

    /** Title IDs with content folders or a per-game config here, sorted. */
    fun titles(limit: Int = MAX_TITLES): List<String> {
        val found = sortedSetOf<String>()
        for (owner in dirs(content)) {
            if (!XUID.matches(owner.name.uppercase())) continue
            dirs(owner).map { it.name.uppercase() }.filter(::isGameTitle).forEach { found += it }
        }
        config.listFiles().orEmpty().filter { it.isFile && visible(it) }.forEach { file ->
            file.name.removeSuffix(CONFIG_SUFFIX).takeIf { file.name.endsWith(CONFIG_SUFFIX) }?.uppercase()
                ?.takeIf(::isGameTitle)?.let { found += it }
        }
        return found.take(limit)
    }

    fun entries(titleId: String): List<Entry> {
        val title = titleId.uppercase()
        require(isGameTitle(title)) { "Not a game Title ID" }
        val entries = ArrayList<Entry>()
        for (owner in dirs(content).sortedBy { it.name }) {
            val xuid = owner.name.uppercase()
            if (!XUID.matches(xuid)) continue
            val folder = dirs(owner).firstOrNull { it.name.equals(title, ignoreCase = true) } ?: continue
            entries += if (xuid == CONSOLE_XUID) Entry(folder, Kind.CONSOLE_DATA) else Entry(folder, Kind.PROFILE_DATA, xuid)
        }
        config.listFiles().orEmpty()
            .firstOrNull { it.isFile && visible(it) && it.name.equals(title + CONFIG_SUFFIX, ignoreCase = true) }
            ?.let { entries += Entry(it, Kind.CONFIG) }
        patches.listFiles().orEmpty()
            .filter { it.isFile && visible(it) && it.name.uppercase().startsWith(title) && it.name.endsWith(".patch.toml") }
            .sortedBy { it.name.lowercase() }
            .forEach { entries += Entry(it, Kind.PATCH) }
        return entries
    }

    /** The game [file] belongs to in this view (one of its entries or inside one), or null. */
    fun titleOf(file: File): String? {
        val base = root.absoluteFile.toPath().normalize()
        val path = file.absoluteFile.toPath().normalize()
        if (!path.startsWith(base) || path == base || !visible(file)) return null
        val parts = base.relativize(path).map { it.toString() }
        return when (parts.first()) {
            "content" -> parts.getOrNull(2)?.uppercase()?.takeIf { XUID.matches(parts[1].uppercase()) && isGameTitle(it) }
            "config" -> parts.getOrNull(1)?.takeIf { parts.size == 2 && it.endsWith(CONFIG_SUFFIX) }
                ?.removeSuffix(CONFIG_SUFFIX)?.uppercase()?.takeIf(::isGameTitle)
            "patches" -> parts.getOrNull(1)?.takeIf { parts.size == 2 && it.endsWith(".patch.toml") }
                ?.take(8)?.uppercase()?.takeIf(::isGameTitle)
            else -> null
        }
    }

    private fun dirs(parent: File): List<File> =
        parent.listFiles().orEmpty().filter { it.isDirectory && visible(it) && !java.nio.file.Files.isSymbolicLink(it.toPath()) }

    companion object {
        const val MAX_TITLES = 500
        const val CONSOLE_XUID = "0000000000000000"
        private const val CONFIG_SUFFIX = ".config.toml"
        private val XUID = Regex("[0-9A-F]{16}")
        private val TITLE = Regex("[0-9A-F]{8}")

        /** A game's Title ID: 8 hex digits, not zero and not a system title (FFFE…, the dashboard's). */
        fun isGameTitle(id: String): Boolean = TITLE.matches(id) && id != "00000000" && !id.startsWith("FFFE")
    }
}
