package xendroid.compose.companion

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class CompanionRejected(val reason: Int) : IOException(
    when (reason) {
        CompanionProtocol.REJECT_PROOF -> "Wrong code"
        CompanionProtocol.REJECT_FULL -> "All player slots are taken"
        CompanionProtocol.REJECT_VERSION -> "The game runs another XenDroid version"
        CompanionProtocol.REJECT_CLOSED -> "Pairing is closed on the game (turn phone controllers off and on there for a new code)"
        else -> "Refused ($reason)"
    })

/**
 * The phone-as-controller side (I06): joins a host with the code it shows, then streams
 * full pad states. Answers the host's pings (which keeps the connection alive and lets
 * the host measure latency) and plays the host's rumble through [onRumble].
 *
 * [connect] blocks: call it off Android's main thread. [update] never blocks: a sender
 * thread writes the newest state (Android forbids socket writes on the main thread).
 */
class CompanionClient(
    private val host: String,
    private val port: Int,
    private val code: String,
    private val name: String,
    /** Stable per install, so a reconnect gets the same player slot back. */
    private val clientId: ByteArray,
    private val onRumble: (left: Int, right: Int) -> Unit = { _, _ -> },
    private val onClosed: (reason: String) -> Unit = {},
) : Closeable {
    private val socket = Socket()
    private val writeLock = Any()
    private val open = AtomicBoolean(false)
    private val latest = AtomicReference(PadState.RELEASED)
    private val wake = Semaphore(0)

    /** Connects and pairs; returns the player slot (1..3 = P2..P4). */
    fun connect(timeoutMs: Int = 3_000): Int {
        val (input, slot) = try {
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            val input = socket.getInputStream().buffered()
            val challenge = CompanionCodec.read(input) as? CompanionMessage.Challenge
                ?: throw CompanionProtocolException("Not a XenDroid game")
            if (challenge.version != CompanionProtocol.VERSION) throw CompanionRejected(CompanionProtocol.REJECT_VERSION)
            write(CompanionMessage.Hello(CompanionProtocol.VERSION, clientId, name,
                CompanionProtocol.proof(code, challenge.nonce, clientId)))
            when (val reply = CompanionCodec.read(input)) {
                is CompanionMessage.Welcome -> input to reply.slot
                is CompanionMessage.Reject -> throw CompanionRejected(reply.reason)
                else -> throw CompanionProtocolException("Unexpected reply")
            }
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
        socket.soTimeout = 0
        open.set(true)
        thread(name = "companion-client-read", isDaemon = true) { readLoop(input) }
        thread(name = "companion-client-send", isDaemon = true) { sendLoop() }
        return slot
    }

    /** The pad's newest state; only changes reach the network. */
    fun update(pad: PadState) {
        latest.set(pad)
        wake.release()
    }

    val isOpen: Boolean get() = open.get()

    private fun write(message: CompanionMessage) {
        synchronized(writeLock) { CompanionCodec.write(socket.getOutputStream(), message) }
    }

    private fun sendLoop() {
        var sent: PadState? = null
        var seq = 0L
        try {
            while (true) {
                wake.acquire()
                wake.drainPermits()
                if (!open.get()) break
                val pad = latest.get()
                if (pad != sent) {
                    write(CompanionMessage.State(++seq, pad))
                    sent = pad
                }
            }
            // Leaving on purpose: the host releases this pad on BYE.
            runCatching { write(CompanionMessage.Bye) }
            runCatching { socket.close() }
        } catch (e: Exception) {
            finish(e.message ?: "connection lost")
        }
    }

    private fun readLoop(input: InputStream) {
        val reason = try {
            while (true) {
                when (val message = CompanionCodec.read(input)) {
                    is CompanionMessage.Ping -> write(CompanionMessage.Pong(message.time))
                    is CompanionMessage.Rumble -> onRumble(message.left, message.right)
                    CompanionMessage.Bye -> break
                    else -> {}
                }
            }
            "the game closed the connection"
        } catch (e: Exception) {
            e.message ?: "connection lost"
        }
        finish(reason)
    }

    /** The connection ended without the user leaving. */
    private fun finish(reason: String) {
        if (!open.getAndSet(false)) return
        wake.release()
        runCatching { socket.close() }
        onRumble(0, 0)
        onClosed(reason)
    }

    override fun close() {
        if (open.getAndSet(false)) {
            wake.release()  // the sender says goodbye and closes the socket
            onRumble(0, 0)
            onClosed("you left")
        } else {
            runCatching { socket.close() }  // also aborts a connect in progress
        }
    }
}
