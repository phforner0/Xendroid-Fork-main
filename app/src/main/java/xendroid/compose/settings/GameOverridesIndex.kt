package xendroid.compose.settings

/**
 * The games with values of their own (the per-game TOML files next to the global config), so a
 * global setting can say "3 games use another value" and list them, and the Settings summary and
 * the Drivers area can name the games that change something. Reads only the schema's keys.
 */
class GameOverridesIndex(private val store: ConfigStore) {
    /** Title ID to its own values (key to raw), for every game file with at least one. */
    fun read(): Map<String, Map<String, String>> {
        val dir = store.perGameConfigFile("00000000").parentFile ?: return emptyMap()
        val titles = dir.listFiles().orEmpty().mapNotNull { FILE.matchEntire(it.name)?.groupValues?.get(1) }.sorted()
        return titles.associateWith { id ->
            runCatching {
                val handle = store.openGameConfig(id)
                try {
                    SettingsSchema.allSettings.mapNotNull { s -> handle.getString(s.section, s.name)?.let { s.key to it } }.toMap()
                } finally { handle.closeDiscard() }
            }.getOrDefault(emptyMap())
        }.filterValues { it.isNotEmpty() }
    }

    companion object {
        private val FILE = Regex("([0-9A-F]{8})\\.config\\.toml")

        /** Setting key to the Title IDs with their own value of it. */
        fun byKey(games: Map<String, Map<String, String>>): Map<String, List<String>> =
            games.flatMap { (title, values) -> values.keys.map { it to title } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, titles) -> titles.sorted() }
    }
}
