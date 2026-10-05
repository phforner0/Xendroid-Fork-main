package xendroid.compose.data

/**
 * U11: the profile each player slot (P1–P4) signs in with when a game starts; the core reads
 * `logged_profile_slot_N_xuid` from the config's Profiles section at boot. A profile plays on
 * one slot at a time, a slot naming a profile that no longer exists signs in nobody, and P1 is
 * also what "Play as" picks before a game. Pure, so the rules are tested on the JVM.
 */
object ProfileSlots {
    const val COUNT = 4
    const val SECTION = "Profiles"

    fun key(slot: Int): String {
        require(slot in 0 until COUNT) { "No player slot $slot" }
        return "logged_profile_slot_${slot}_xuid"
    }

    /** Four entries, upper-cased, blanks as null. */
    fun normalize(slots: List<String?>): List<String?> =
        List(COUNT) { i -> slots.getOrNull(i)?.trim()?.uppercase()?.ifEmpty { null } }

    /** [slots] with [xuid] on [slot] (null = nobody); the slot it held before is freed. */
    fun assign(slots: List<String?>, slot: Int, xuid: String?): List<String?> {
        require(slot in 0 until COUNT) { "No player slot $slot" }
        val profile = xuid?.trim()?.uppercase()?.ifEmpty { null }
        return normalize(slots).mapIndexed { i, current ->
            when {
                i == slot -> profile
                profile != null && current == profile -> null
                else -> current
            }
        }
    }

    /** Slots that name a profile not in [existing], or one a lower slot already has, sign in nobody. */
    fun reconcile(slots: List<String?>, existing: Collection<String>): List<String?> {
        val known = existing.map { it.uppercase() }.toSet()
        val taken = HashSet<String>()
        return normalize(slots).map { xuid -> xuid?.takeIf { it in known && taken.add(it) } }
    }

    /** The slots whose value differs, to write: slot to XUID ("" = nobody). */
    fun changes(before: List<String?>, after: List<String?>): Map<Int, String> {
        val old = normalize(before)
        val new = normalize(after)
        return (0 until COUNT).filter { old[it] != new[it] }.associateWith { new[it].orEmpty() }
    }

    /** The player numbers (2–4) other than P1 that [xuid] plays as, for labels: "P3". */
    fun otherPlayerOf(slots: List<String?>, xuid: String): Int? =
        normalize(slots).withIndex().firstOrNull { it.index > 0 && it.value == xuid.uppercase() }?.let { it.index + 1 }
}
