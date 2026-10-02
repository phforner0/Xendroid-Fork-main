package xendroid.compose.data

/** A local profile a game can sign in with (P1). */
data class PlayableProfile(val xuid: String, val gamertag: String)

/**
 * U11: which profile signs in as P1 before a game boots. With several, the player picks (the
 * active one comes first and is preselected) unless they chose not to be asked, and then the
 * active one plays. An active XUID that no longer exists (profile deleted or moved to the
 * trash) counts as none, so a game never boots signed in to nothing by mistake; then a profile
 * no other player ([slots], P2–P4) signs in with is preferred, as picking one moves it to P1.
 */
object ProfilePick {
    /** Preferences of the choice: "ask" before each game (default on). */
    const val PREFS = "profile_pick"
    const val ASK = "ask"

    sealed interface Decision {
        /** Boot now as [xuid]; [changes] = it differs from the configured one, so write it first. */
        data class Launch(val xuid: String?, val changes: Boolean) : Decision
        /** Ask; [profiles] in the order to show, [preselected] highlighted; [otherPlayers]:
         *  XUID (upper case) to the player (2–4) it signs in as now. */
        data class Ask(val profiles: List<PlayableProfile>, val preselected: String,
                       val otherPlayers: Map<String, Int> = emptyMap()) : Decision
    }

    fun decide(profiles: List<PlayableProfile>, activeXuid: String?, askBeforePlaying: Boolean,
               slots: List<String?> = emptyList()): Decision {
        val sorted = profiles.distinctBy { it.xuid.uppercase() }.sortedBy { it.gamertag.lowercase() }
        val active = sorted.firstOrNull { it.xuid.equals(activeXuid, ignoreCase = true) }
        val fallback = sorted.firstOrNull { ProfileSlots.otherPlayerOf(slots, it.xuid) == null } ?: sorted.firstOrNull()
        return when {
            sorted.isEmpty() -> Decision.Launch(null, changes = false)
            sorted.size == 1 -> Decision.Launch(sorted.single().xuid, changes = active == null)
            askBeforePlaying -> {
                val first = active ?: fallback!!
                Decision.Ask(listOf(first) + (sorted - first), first.xuid,
                    sorted.mapNotNull { p -> ProfileSlots.otherPlayerOf(slots, p.xuid)?.let { p.xuid.uppercase() to it } }.toMap())
            }
            else -> Decision.Launch((active ?: fallback!!).xuid, changes = active == null)
        }
    }
}
