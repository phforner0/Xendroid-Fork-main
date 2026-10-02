package xendroid.compose.companion

import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The game's "Phone controllers" switch (I07). Off at every boot; turned on, it opens a
 * [CompanionHost] on the device's LAN address. Starting and stopping run in order on one
 * worker thread: Android forbids sockets on its main thread, and a host must be fully
 * closed (its phones released, their slots freed) before another one hands slots out.
 *
 * [onChange] runs on any thread after the status changes.
 */
class CompanionHostControl(
    private val newHost: (LanCandidate) -> CompanionHost,
    private val pickNetwork: () -> LanCandidate? = { CompanionNetwork.choose(CompanionNetwork.candidates()) },
    private val onChange: () -> Unit = {},
) : Closeable {
    sealed interface Status {
        data object Off : Status
        data object Starting : Status
        data class On(val host: CompanionHost, val network: String) : Status
        data object Stopping : Status
        /** Could not start: [why] (with its [detail]) for the menu's language, [reason] in English. */
        data class Failed(val why: Why, val detail: String = "") : Status {
            enum class Why(val english: String) { NO_NETWORK("no Wi-Fi, hotspot or Ethernet network"), CANNOT_LISTEN("could not listen (%s)") }
            val reason: String get() = why.english.format(detail)
        }
    }

    @Volatile var status: Status = Status.Off
        private set
    private var closed = false
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "companion-control").apply { isDaemon = true }
    }
    private val forwarder = CompanionRumbleForwarder()

    val host: CompanionHost? get() = (status as? Status.On)?.host

    /** Turns phone controllers on or off; ignored while a previous switch is still in progress. */
    fun toggle() {
        synchronized(this) {
            if (closed) return
            when (val current = status) {
                is Status.On -> {
                    status = Status.Stopping
                    worker.execute { current.host.close(); finish(Status.Stopping, Status.Off) }
                }
                Status.Off, is Status.Failed -> {
                    status = Status.Starting
                    worker.execute { start() }
                }
                Status.Starting, Status.Stopping -> return
            }
        }
        onChange()
    }

    private fun start() {
        val lan = runCatching(pickNetwork).getOrNull()
        if (lan == null) {
            finish(Status.Starting, Status.Failed(Status.Failed.Why.NO_NETWORK))
            return
        }
        val host = try {
            newHost(lan).start()
        } catch (e: Exception) {
            finish(Status.Starting, Status.Failed(Status.Failed.Why.CANNOT_LISTEN, e.message ?: e.javaClass.simpleName))
            return
        }
        // Closed (the game ended) while starting: never leave a listening socket behind.
        if (!finish(Status.Starting, Status.On(host, CompanionNetwork.kindOf(lan.interfaceName) ?: "local network"))) host.close()
    }

    /** Moves [from] -> [to]; false when something else (close) changed the status meanwhile. */
    private fun finish(from: Status, to: Status): Boolean {
        synchronized(this) {
            if (closed || status != from) return false
            status = to
        }
        onChange()
        return true
    }

    /**
     * Every 50 ms while the game runs: holds the phones' input while the game is not
     * [playing] (menu, pause, background) and forwards the guest [rumble] per slot.
     */
    fun tick(playing: Boolean, rumble: LongArray?) {
        val current = host ?: return
        current.holdInput(!playing)
        val phones = current.connected.associate { it.slot to it.connection }
        forwarder.update(phones, if (playing) rumble else null) { slot, left, right -> current.sendRumble(slot, left, right) }
    }

    /** The menu action's text. */
    fun label(): String = when (val s = status) {
        Status.Off -> "Phone controllers · Off"
        Status.Starting -> "Phone controllers · starting…"
        Status.Stopping -> "Phone controllers · turning off…"
        is Status.Failed -> "Phone controllers · Off: ${s.reason}"
        is Status.On -> "Phone controllers · On (activate to turn off)"
    }

    /** While on: what the other phones need (address, code) and who is playing. */
    fun details(): String? {
        val on = status as? Status.On ?: return null
        val host = on.host
        val players = host.connected.joinToString(" · ") { player ->
            "P${player.slot + 1} ${player.name}" + (player.latencyMs?.let { " ($it ms)" } ?: "")
        }
        return buildString {
            append("On the other phone: Library → ⋮ → Use this phone as a controller → ")
            append("${host.address.hostAddress}:${host.port}, code ${host.code} (${on.network}).")
            append('\n')
            append(if (players.isEmpty()) "No phone connected yet. Phones play as P2–P4." else players)
            if (host.locked) append("\nPairing locked after ${CompanionHost.MAX_WRONG_CODES} wrong codes: turn off and on for a new code.")
        }
    }

    /** Ends companion mode for good (the game is closing); waits up to [timeoutMs] for the phones to be told. */
    fun close(timeoutMs: Long) {
        val pending = synchronized(this) {
            if (closed) return
            closed = true
            val current = status
            status = Status.Off
            worker.submit { (current as? Status.On)?.host?.close() }
        }
        runCatching { pending.get(timeoutMs, TimeUnit.MILLISECONDS) }
        worker.shutdown()
    }

    override fun close() = close(500)
}
