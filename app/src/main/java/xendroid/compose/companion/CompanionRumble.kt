package xendroid.compose.companion

/**
 * I09: sends each companion phone the guest rumble of the slot it plays, only when it
 * changes (the host polls every 50 ms; the phone keeps vibrating until told otherwise).
 * Every connection starts from silence (a phone that reconnects is told again); when the
 * game stops playing (menu, pause, background) every phone that was vibrating is stopped.
 */
class CompanionRumbleForwarder {
    /** Slot -> (connection, motors last sent to it). */
    private val sent = HashMap<Int, Pair<Long, Long>>()

    /**
     * [phones] the connection playing each slot (see [CompanionHost.ClientInfo]); [state]
     * the guest rumble per slot (left motor shl 16 or right motor, 0..65535 each), or null
     * while the game is not playing.
     */
    fun update(phones: Map<Int, Long>, state: LongArray?, send: (slot: Int, left: Int, right: Int) -> Unit) {
        sent.keys.retainAll(phones.keys)
        for ((slot, connection) in phones) {
            val motors = state?.getOrNull(slot) ?: 0L
            val previous = sent[slot]?.takeIf { it.first == connection }?.second ?: 0L
            if (motors != previous) send(slot, ((motors shr 16) and 0xFFFF).toInt(), (motors and 0xFFFF).toInt())
            sent[slot] = connection to motors
        }
    }
}
