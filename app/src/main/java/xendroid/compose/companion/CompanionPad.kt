package xendroid.compose.companion

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The game's address as its menu shows it, typed on the phone that controls ("192.168.1.20:41234"). */
data class CompanionTarget(val host: String, val port: Int) {
    override fun toString() = "$host:$port"

    companion object {
        private val form = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3}):(\d{1,5})""")
        private val code = Regex("[0-9]{6}")

        /** An IPv4 literal and a port; null for anything else (names are never looked up). */
        fun parse(text: String): CompanionTarget? {
            val match = form.matchEntire(text.trim()) ?: return null
            val octets = match.groupValues.subList(1, 5).map(String::toInt)
            val port = match.groupValues[5].toInt()
            if (octets.any { it > 255 } || port !in 1..65535) return null
            return CompanionTarget(octets.joinToString("."), port)
        }

        fun isCode(text: String): Boolean = code.matches(text.trim())
    }
}

/**
 * The phone-as-controller side (I06) behind its screen: one connection at a time, the
 * on-screen pad's key events folded into full states ([PadKeys.apply]), the game's
 * rumble passed on, and a state the UI can show. [connect] blocks: call it off Android's
 * main thread; everything else returns at once.
 */
class CompanionPadLink(
    private val newClient: (target: CompanionTarget, code: String, name: String,
                            onRumble: (Int, Int) -> Unit, onClosed: (String) -> Unit) -> CompanionClient,
    /** The game's rumble for this phone's slot: left motor shl 16 or right motor; 0 stops. */
    private val onRumble: (motors: Long) -> Unit = {},
) {
    sealed interface State {
        /** Not connected; [message] says why the last attempt or connection ended. */
        data class Idle(val message: String? = null) : State
        data object Connecting : State
        /** [slot] 1..3 = P2..P4; [latencyMs] the round trip the game measured. */
        data class Playing(val slot: Int, val latencyMs: Int? = null) : State
    }

    private val mutableState = MutableStateFlow<State>(State.Idle())
    val state: StateFlow<State> = mutableState

    private var client: CompanionClient? = null
    private var pad = PadState.RELEASED

    fun connect(address: String, code: String, name: String) {
        val target = CompanionTarget.parse(address)
            ?: return idle("Type the address the game shows, like 192.168.1.20:41234")
        if (!CompanionTarget.isCode(code)) return idle("The code is the 6 digits the game shows")
        val created = synchronized(this) {
            if (client != null) return
            var self: CompanionClient? = null
            val made = newClient(target, code.trim(), name.trim().ifEmpty { "Phone" },
                { left, right -> onRumble((left.toLong() shl 16) or right.toLong()) },
                { reason -> self?.let { ended(it, reason) } })
            self = made
            client = made
            pad = PadState.RELEASED
            mutableState.value = State.Connecting
            made
        }
        try {
            val slot = created.connect()
            synchronized(this) { if (client === created) mutableState.value = State.Playing(slot) }
        } catch (e: Exception) {
            synchronized(this) {
                if (client !== created) return  // the user left while connecting
                client = null
                mutableState.value = State.Idle(describe(e, target))
            }
        }
    }

    /** One event of the on-screen pad (GamepadEmitter's codes and values). */
    fun key(key: Int, pressed: Boolean, value: Int) {
        synchronized(this) {
            pad = PadKeys.apply(pad, key, pressed, value)
            client?.update(pad)
        }
    }

    /** Picks up the latest latency; the screen calls it every second. */
    fun refresh() {
        synchronized(this) {
            val playing = mutableState.value as? State.Playing ?: return
            val latency = client?.latencyMs
            if (latency != playing.latencyMs) mutableState.value = playing.copy(latencyMs = latency)
        }
    }

    /** Leaves the game (or stops connecting); the game releases this phone's buttons at once. */
    fun leave() {
        val leaving = synchronized(this) {
            val current = client ?: return
            client = null
            pad = PadState.RELEASED
            mutableState.value = State.Idle()
            current
        }
        leaving.close()
    }

    private fun ended(which: CompanionClient, reason: String) {
        synchronized(this) {
            if (client !== which) return
            client = null
            pad = PadState.RELEASED
            mutableState.value = State.Idle("Disconnected: $reason")
        }
    }

    private fun idle(message: String) {
        synchronized(this) { if (client == null) mutableState.value = State.Idle(message) }
    }

    private fun describe(e: Exception, target: CompanionTarget): String = when (e) {
        is CompanionRejected -> e.message.orEmpty()
        is ConnectException, is SocketTimeoutException, is NoRouteToHostException ->
            "Could not reach the game at $target. Both phones must be on the same Wi-Fi or hotspot, " +
                "with Phone controllers on in the game's menu (a new address each time it is turned on)."
        is CompanionProtocolException -> "Not a XenDroid game at $target (${e.message})"
        else -> "Connection failed: ${e.message ?: e.javaClass.simpleName}"
    }
}
