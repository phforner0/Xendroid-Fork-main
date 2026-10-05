package xendroid.compose.patches

/**
 * L10: whether a patch file is for the player's version of the game. A file lists the module
 * hashes it was written for, and the core applies it only to a game that loaded one of them
 * (usually the main executable). The game's hashes come from its last finished run here;
 * none means not known yet (never played here, or played before they were recorded).
 */
object PatchVersion {
    enum class Match { YOURS, OTHER, UNKNOWN }

    fun match(fileHashes: List<String>, gameHashes: List<String>): Match {
        val game = gameHashes.mapNotNull(::value).toSet()
        val file = fileHashes.mapNotNull(::value)
        if (game.isEmpty() || file.isEmpty()) return Match.UNKNOWN
        return if (file.any { it in game }) Match.YOURS else Match.OTHER
    }

    /** Up to 16 hex digits, any case, leading zeros optional (as patch files write them). */
    private fun value(hash: String): ULong? = hash.trim().removePrefix("0x").removePrefix("0X")
        .takeIf { it.length in 1..16 && it.all { c -> c in '0'..'9' || c.lowercaseChar() in 'a'..'f' } }
        ?.toULong(16)
}
