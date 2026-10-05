package xendroid.compose.companion

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * The game side of the companion protocol (I07/I08). Listens on the one LAN address it
 * is given (never every interface) while the user keeps companion mode on; a phone that
 * proves the code shown on the host gets a free player slot P2..P4 (never P1) and its
 * full pad states become that slot's input. A phone silent for [timeoutMs] has its input
 * released and its slot freed; a phone that reconnects starts from a released pad, so
 * no old button comes back. After [MAX_WRONG_CODES] wrong codes pairing locks until
 * companion mode is turned off and on (a new code), which bounds guessing.
 *
 * Callbacks run on the host's socket threads, never on Android's main thread.
 */
class CompanionHost(
    bindAddress: InetAddress,
    private val claimSlot: (clientKey: String, name: String) -> Int?,
    private val releaseSlot: (clientKey: String, slot: Int) -> Unit,
    private val onInput: (slot: Int, change: PadKeys.KeyChange) -> Unit,
    private val onEvent: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = 1_000,
    private val heartbeatMs: Long = 250,
    private val handshakeTimeoutMs: Int = 4_000,
    port: Int = 0,
) : Closeable {
    /** Shown to the user; a new one every time companion mode is turned on. */
    val code: String = CompanionProtocol.newCode()
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(bindAddress, port))
    }
    val address: InetAddress = server.inetAddress
    val port: Int = server.localPort
    private val running = AtomicBoolean(true)
    private val clients = ConcurrentHashMap<String, Client>()
    /** Joins, replacements and shutdown happen one at a time. */
    private val joinLock = Any()
    private val pairLock = Any()
    private var wrongCodes = 0
    private val handshakes = AtomicInteger(0)
    /** Rumble is sent off the caller's thread: Android forbids socket writes on the main thread. */
    private val rumbleSender = Executors.newSingleThreadExecutor { r -> Thread(r, "companion-rumble").apply { isDaemon = true } }
    /** See [holdInput]. */
    @Volatile private var inputHeld = false
    private val holdLock = Any()

    /** [connection] is unique per accepted phone connection in this process (a reconnect gets a new one). */
    data class ClientInfo(val slot: Int, val name: String, val latencyMs: Long?, val connection: Long)

    private inner class Client(val key: String, val name: String, val slot: Int, val socket: Socket) {
        val connection = connections.incrementAndGet()
        @Volatile var lastHeard: Long = clock()
        @Volatile var latencyMs: Long? = null
        var lastSeq = -1L
        /** The phone's newest state, and what the guest has (released while input is held). */
        private var latest = PadState.RELEASED
        private var applied = PadState.RELEASED
        private val writeLock = Any()
        private val closed = AtomicBoolean(false)

        fun send(message: CompanionMessage) {
            synchronized(writeLock) { CompanionCodec.write(socket.getOutputStream(), message) }
        }

        /** Must hold this client's lock. */
        private fun apply(next: PadState) {
            PadKeys.diff(applied, next).forEach { onInput(slot, it) }
            applied = next
        }

        /** Applies a new state unless the client is already gone (no key pressed after its release) or input is held. */
        fun update(next: PadState) {
            synchronized(this) {
                if (closed.get()) return
                latest = next
                if (!inputHeld) apply(next)
            }
        }

        fun hold(held: Boolean) {
            synchronized(this) {
                if (closed.get()) return
                apply(if (held) PadState.RELEASED else latest)
            }
        }

        /** Releases what this phone held, frees its slot once, closes the socket. */
        fun close(reason: String) {
            if (!closed.compareAndSet(false, true)) return
            synchronized(this) { apply(PadState.RELEASED) }
            clients.remove(key, this)
            releaseSlot(key, slot)
            runCatching { send(CompanionMessage.Bye) }
            runCatching { socket.close() }
            onEvent("phone controller P${slot + 1} left: $reason")
        }
    }

    val connected: List<ClientInfo>
        get() = clients.values.map { ClientInfo(it.slot, it.name, it.latencyMs, it.connection) }.sortedBy { it.slot }

    /**
     * While held (the game's menu is open, it is paused or in the background) every phone's
     * input is released in the guest and new states are only remembered, like the menu
     * isolates local controllers; releasing the hold applies each phone's current state,
     * so a stick still pushed keeps working without being moved again.
     */
    fun holdInput(held: Boolean) {
        synchronized(holdLock) {
            if (inputHeld == held) return
            inputHeld = held
            clients.values.forEach { it.hold(held) }
        }
    }

    /** True once too many wrong codes were tried: only a new code (off and on) pairs again. */
    val locked: Boolean get() = synchronized(pairLock) { wrongCodes >= MAX_WRONG_CODES }

    fun start(): CompanionHost {
        thread(name = "companion-accept", isDaemon = true) {
            while (running.get()) {
                val socket = try { server.accept() } catch (e: IOException) { break }
                if (handshakes.incrementAndGet() > MAX_HANDSHAKES) {
                    handshakes.decrementAndGet()
                    runCatching { socket.close() }
                    continue
                }
                thread(name = "companion-client", isDaemon = true) { serve(socket) }
            }
        }
        thread(name = "companion-heartbeat", isDaemon = true) {
            while (running.get()) {
                try { Thread.sleep(heartbeatMs) } catch (e: InterruptedException) { break }
                val now = clock()
                for (client in clients.values) {
                    val silent = now - client.lastHeard
                    // The phone shows the round trip the host measured (I09).
                    val rtt = client.latencyMs?.coerceAtMost(Short.MAX_VALUE.toLong())?.toInt() ?: -1
                    if (silent > timeoutMs) client.close("no data for $silent ms")
                    else runCatching { client.send(CompanionMessage.Ping(now, rtt)) }.onFailure { client.close("send failed") }
                }
            }
        }
        return this
    }

    /** The guest's rumble for [slot] (0..65535 per motor), forwarded to the phone playing it (I09). */
    fun sendRumble(slot: Int, left: Int, right: Int) {
        val client = clients.values.firstOrNull { it.slot == slot } ?: return
        runCatching {
            rumbleSender.execute { runCatching { client.send(CompanionMessage.Rumble(left, right)) } }
        }
    }

    private fun serve(socket: Socket) {
        var handshaking = true
        var client: Client? = null
        fun handshakeDone() { if (handshaking) { handshaking = false; handshakes.decrementAndGet() } }
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = handshakeTimeoutMs
            val input = socket.getInputStream().buffered()
            val nonce = CompanionProtocol.newNonce()
            CompanionCodec.write(socket.getOutputStream(), CompanionMessage.Challenge(CompanionProtocol.VERSION, nonce))
            val hello = CompanionCodec.read(input) as? CompanionMessage.Hello
                ?: throw CompanionProtocolException("Expected HELLO")
            fun reject(reason: Int, why: String) {
                runCatching { CompanionCodec.write(socket.getOutputStream(), CompanionMessage.Reject(reason)) }
                onEvent("phone controller refused: $why")
                socket.close()
            }
            if (hello.version != CompanionProtocol.VERSION) return reject(CompanionProtocol.REJECT_VERSION, "protocol ${hello.version}")
            val verdict = synchronized(pairLock) {
                when {
                    wrongCodes >= MAX_WRONG_CODES -> CompanionProtocol.REJECT_CLOSED
                    !CompanionProtocol.proofMatches(code, nonce, hello.clientId, hello.proof) -> {
                        wrongCodes++
                        CompanionProtocol.REJECT_PROOF
                    }
                    else -> 0
                }
            }
            when (verdict) {
                CompanionProtocol.REJECT_CLOSED -> return reject(verdict, "pairing locked after $MAX_WRONG_CODES wrong codes")
                CompanionProtocol.REJECT_PROOF -> return reject(verdict, "wrong code")
            }
            val key = KEY_PREFIX + UUID.nameUUIDFromBytes(hello.clientId)
            val name = hello.name.filter { it >= ' ' }.trim().take(32).ifBlank { "Phone" }
            val joined = synchronized(joinLock) {
                if (!running.get()) return reject(CompanionProtocol.REJECT_CLOSED, "companion mode off")
                // The same phone reconnecting replaces its old connection (released first).
                clients[key]?.close("reconnected")
                val slot = claimSlot(key, name) ?: return reject(CompanionProtocol.REJECT_FULL, "no free player slot")
                Client(key, name, slot, socket).also { clients[key] = it }
            }
            client = joined
            handshakeDone()
            socket.soTimeout = 0  // liveness comes from the heartbeat from now on
            joined.send(CompanionMessage.Welcome(joined.slot))
            // Not the phone's name: like a Bluetooth name it can carry its owner's name.
            onEvent("phone controller joined as P${joined.slot + 1}")
            while (running.get()) {
                when (val message = CompanionCodec.read(input)) {
                    is CompanionMessage.State -> {
                        joined.lastHeard = clock()
                        // TCP keeps order; the sequence guards a confused or replaying client.
                        if (message.seq > joined.lastSeq) {
                            joined.lastSeq = message.seq
                            joined.update(message.pad)
                        }
                    }
                    is CompanionMessage.Pong -> {
                        val now = clock()
                        joined.lastHeard = now
                        joined.latencyMs = (now - message.time).takeIf { it >= 0 }
                    }
                    CompanionMessage.Bye -> { joined.close("phone left"); return }
                    else -> throw CompanionProtocolException("Unexpected frame from a phone")
                }
            }
        } catch (e: Exception) {
            val current = client
            if (current != null) current.close(e.message ?: e.javaClass.simpleName) else runCatching { socket.close() }
        } finally {
            handshakeDone()
        }
    }

    override fun close() {
        synchronized(joinLock) {
            if (!running.compareAndSet(true, false)) return
            runCatching { server.close() }
            clients.values.toList().forEach { it.close("companion mode off") }
        }
        rumbleSender.shutdown()
    }

    companion object {
        const val MAX_WRONG_CODES = 10
        const val MAX_HANDSHAKES = 4
        /** Player keys of companion phones in ControllerSlots. */
        const val KEY_PREFIX = "companion:"
        private val connections = AtomicLong()
    }
}
