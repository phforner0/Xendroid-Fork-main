package xendroid.compose.data

/** A local profile a game can sign in with (P1). */
data class PlayableProfile(val xuid: String, val gamertag: String)

/**
 * U11: which profile signs in before a game boots. One profile plays at once; with several, the
 * player picks (the active one comes first and is preselected) unless they chose not to be
 * asked, and then the active one plays. An active XUID that no longer exists (profile deleted
 * or moved to the trash) counts as none, so a game never boots signed in to nothing by mistake.
 */
object ProfilePick {
    /** Preferences of the choice: "ask" before each game (default on). */
    const val PREFS = "profile_pick"
    const val ASK = "ask"

    sealed interface Decision {
        /** Boot now as [xuid]; [changes] = it differs from the configured one, so write it first. */
        data class Launch(val xuid: String?, val changes: Boolean) : Decision
        /** Ask; [profiles] in the order to show, [preselected] highlighted. */
        data class Ask(val profiles: List<PlayableProfile>, val preselected: String) : Decision
    }

    fun decide(profiles: List<PlayableProfile>, activeXuid: String?, askBeforePlaying: Boolean): Decision {
        val sorted = profiles.distinctBy { it.xuid.uppercase() }.sortedBy { it.gamertag.lowercase() }
        val active = sorted.firstOrNull { it.xuid.equals(activeXuid, ignoreCase = true) }
        return when {
            sorted.isEmpty() -> Decision.Launch(null, changes = false)
            sorted.size == 1 -> Decision.Launch(sorted.single().xuid, changes = active == null)
            askBeforePlaying -> {
                val first = active ?: sorted.first()
                Decision.Ask(listOf(first) + (sorted - first), first.xuid)
            }
            else -> Decision.Launch((active ?: sorted.first()).xuid, changes = active == null)
        }
    }
}
