package xendroid.compose.companion

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Companion controller protocol, version 1 (I06–I09): another phone on the same LAN
 * plays as P2–P4. Framing over one TCP connection: u16 length, u8 type, payload.
 *
 *  host  -> CHALLENGE(version, nonce[16])
 *  client-> HELLO(version, clientId[16], name, proof[32] = HMAC-SHA256(code, tag | nonce | clientId))
 *  host  -> WELCOME(slot) | REJECT(reason)
 *  client-> STATE(seq, buttons, lt, rt, lx, ly, rx, ry)   full pad state, XInput layout
 *  host  -> PING(t) / client -> PONG(t)                    heartbeat and latency
 *  host  -> RUMBLE(left, right)                            the guest's rumble for that slot
 *  either-> BYE
 *
 * The pairing code never travels: only a proof bound to the host's fresh nonce.
 */
object CompanionProtocol {
    const val VERSION = 1
    const val MAX_FRAME = 512
    private const val PROOF_TAG = "xendroid-companion-v1"

    const val CHALLENGE = 1
    const val HELLO = 2
    const val WELCOME = 3
    const val REJECT = 4
    const val STATE = 5
    const val PING = 6
    const val PONG = 7
    const val RUMBLE = 8
    const val BYE = 9

    /** Reasons sent with REJECT. */
    const val REJECT_VERSION = 1
    const val REJECT_PROOF = 2
    const val REJECT_FULL = 3
    const val REJECT_CLOSED = 4

    private val random = SecureRandom()

    fun newCode(): String = (0 until 6).joinToString("") { random.nextInt(10).toString() }
    fun newNonce(): ByteArray = ByteArray(16).also(random::nextBytes)

    fun proof(code: String, nonce: ByteArray, clientId: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(code.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(PROOF_TAG.toByteArray(Charsets.UTF_8))
        mac.update(nonce)
        mac.update(clientId)
        return mac.doFinal()
    }

    fun proofMatches(code: String, nonce: ByteArray, clientId: ByteArray, proof: ByteArray): Boolean =
        MessageDigest.isEqual(proof(code, nonce, clientId), proof)
}

/** Full pad state in XInput's layout: buttons bitmask, triggers 0..255, sticks -32768..32767 (Y up positive). */
data class PadState(
    val buttons: Int = 0,
    val lt: Int = 0,
    val rt: Int = 0,
    val lx: Int = 0,
    val ly: Int = 0,
    val rx: Int = 0,
    val ry: Int = 0,
) {
    companion object { val RELEASED = PadState() }
}

sealed interface CompanionMessage {
    data class Challenge(val version: Int, val nonce: ByteArray) : CompanionMessage
    data class Hello(val version: Int, val clientId: ByteArray, val name: String, val proof: ByteArray) : CompanionMessage
    data class Welcome(val slot: Int) : CompanionMessage
    data class Reject(val reason: Int) : CompanionMessage
    data class State(val seq: Long, val pad: PadState) : CompanionMessage
    data class Ping(val time: Long) : CompanionMessage
    data class Pong(val time: Long) : CompanionMessage
    data class Rumble(val left: Int, val right: Int) : CompanionMessage
    data object Bye : CompanionMessage
}

class CompanionProtocolException(message: String) : IOException(message)

/** Encodes and decodes frames; one instance per direction of a connection. */
object CompanionCodec {
    fun write(out: OutputStream, message: CompanionMessage) {
        val body = ByteArrayOutputStream()
        val data = DataOutputStream(body)
        val type = when (message) {
            is CompanionMessage.Challenge -> { data.writeShort(message.version); data.write(message.nonce.copyOf(16)); CompanionProtocol.CHALLENGE }
            is CompanionMessage.Hello -> {
                data.writeShort(message.version)
                data.write(message.clientId.copyOf(16))
                val name = message.name.toByteArray(Charsets.UTF_8).let { if (it.size > 32) it.copyOf(32) else it }
                data.writeByte(name.size)
                data.write(name)
                data.write(message.proof.copyOf(32))
                CompanionProtocol.HELLO
            }
            is CompanionMessage.Welcome -> { data.writeByte(message.slot); CompanionProtocol.WELCOME }
            is CompanionMessage.Reject -> { data.writeByte(message.reason); CompanionProtocol.REJECT }
            is CompanionMessage.State -> {
                data.writeInt(message.seq.toInt())
                with(message.pad) {
                    data.writeShort(buttons); data.writeByte(lt); data.writeByte(rt)
                    data.writeShort(lx); data.writeShort(ly); data.writeShort(rx); data.writeShort(ry)
                }
                CompanionProtocol.STATE
            }
            is CompanionMessage.Ping -> { data.writeLong(message.time); CompanionProtocol.PING }
            is CompanionMessage.Pong -> { data.writeLong(message.time); CompanionProtocol.PONG }
            is CompanionMessage.Rumble -> { data.writeShort(message.left); data.writeShort(message.right); CompanionProtocol.RUMBLE }
            CompanionMessage.Bye -> CompanionProtocol.BYE
        }
        val payload = body.toByteArray()
        val frame = DataOutputStream(out)
        frame.writeShort(payload.size + 1)
        frame.writeByte(type)
        frame.write(payload)
        frame.flush()
    }

    /** Reads one frame; throws on a closed stream, an oversized or a malformed frame. */
    fun read(input: InputStream): CompanionMessage {
        val frame = DataInputStream(input)
        val length = try { frame.readUnsignedShort() } catch (e: EOFException) { throw CompanionProtocolException("Connection closed") }
        if (length < 1 || length > CompanionProtocol.MAX_FRAME) throw CompanionProtocolException("Bad frame length $length")
        val bytes = ByteArray(length)
        frame.readFully(bytes)
        val data = DataInputStream(bytes.inputStream(1, length - 1))
        fun need(n: Int) { if (length - 1 < n) throw CompanionProtocolException("Short frame") }
        return when (bytes[0].toInt() and 0xFF) {
            CompanionProtocol.CHALLENGE -> { need(18); CompanionMessage.Challenge(data.readUnsignedShort(), ByteArray(16).also(data::readFully)) }
            CompanionProtocol.HELLO -> {
                need(19)
                val version = data.readUnsignedShort()
                val id = ByteArray(16).also(data::readFully)
                val nameLength = data.readUnsignedByte()
                if (nameLength > 32) throw CompanionProtocolException("Name too long")
                need(19 + nameLength + 32)
                val name = String(ByteArray(nameLength).also(data::readFully), Charsets.UTF_8)
                CompanionMessage.Hello(version, id, name, ByteArray(32).also(data::readFully))
            }
            CompanionProtocol.WELCOME -> { need(1); CompanionMessage.Welcome(data.readUnsignedByte()) }
            CompanionProtocol.REJECT -> { need(1); CompanionMessage.Reject(data.readUnsignedByte()) }
            CompanionProtocol.STATE -> {
                need(16)
                val seq = data.readInt().toLong() and 0xFFFFFFFFL
                CompanionMessage.State(seq, PadState(data.readUnsignedShort(), data.readUnsignedByte(), data.readUnsignedByte(),
                    data.readShort().toInt(), data.readShort().toInt(), data.readShort().toInt(), data.readShort().toInt()))
            }
            CompanionProtocol.PING -> { need(8); CompanionMessage.Ping(data.readLong()) }
            CompanionProtocol.PONG -> { need(8); CompanionMessage.Pong(data.readLong()) }
            CompanionProtocol.RUMBLE -> { need(4); CompanionMessage.Rumble(data.readUnsignedShort(), data.readUnsignedShort()) }
            CompanionProtocol.BYE -> CompanionMessage.Bye
            else -> throw CompanionProtocolException("Unknown frame type ${bytes[0].toInt() and 0xFF}")
        }
    }
}

/**
 * Turns successive full pad states into the guest key events the Android driver takes
 * (same indices and conventions as the controller path: a stick direction key carries
 * the signed value; Y up is positive). Only changes are emitted.
 */
object PadKeys {
    data class KeyChange(val key: Int, val pressed: Boolean, val value: Int)

    private const val UNUSED = -1
    // XInput button bit -> guest key index (xendroid_emu.cpp key_maps order).
    private val buttons = listOf(
        0x0004 to 0, 0x0001 to 1, 0x0008 to 2, 0x0002 to 3,          // D-pad left, up, right, down
        0x1000 to 4, 0x2000 to 5, 0x4000 to 6, 0x8000 to 7,          // A, B, X, Y
        0x0020 to 8, 0x0010 to 9, 0x0100 to 10, 0x0200 to 11,        // Back, Start, LB, RB
        0x0040 to 12, 0x0080 to 13,                                    // LS, RS
    )

    fun diff(previous: PadState, next: PadState): List<KeyChange> {
        val changes = mutableListOf<KeyChange>()
        for ((bit, key) in buttons) {
            val was = previous.buttons and bit != 0
            val now = next.buttons and bit != 0
            if (was != now) changes += KeyChange(key, now, UNUSED)
        }
        trigger(previous.lt, next.lt, 14, changes)
        trigger(previous.rt, next.rt, 15, changes)
        axis(previous.lx, next.lx, negKey = 16, posKey = 18, changes)
        axis(previous.ly, next.ly, negKey = 17, posKey = 19, changes)
        axis(previous.rx, next.rx, negKey = 20, posKey = 22, changes)
        axis(previous.ry, next.ry, negKey = 21, posKey = 23, changes)
        return changes
    }

    /**
     * The inverse of [diff]: folds one key event of the on-screen pad (GamepadEmitter's
     * codes and values) into a full state, so the companion phone can send whole states.
     * Releasing one half of an axis only zeroes it while that half is the active one.
     */
    fun apply(state: PadState, key: Int, pressed: Boolean, value: Int): PadState {
        buttons.firstOrNull { it.second == key }?.let { (bit, _) ->
            return state.copy(buttons = if (pressed) state.buttons or bit else state.buttons and bit.inv())
        }
        fun half(current: Int, negative: Boolean): Int = when {
            pressed -> value.coerceIn(-32768, 32767)
            negative && current < 0 -> 0
            !negative && current > 0 -> 0
            else -> current
        }
        return when (key) {
            14 -> state.copy(lt = if (pressed) 255 else 0)
            15 -> state.copy(rt = if (pressed) 255 else 0)
            16 -> state.copy(lx = half(state.lx, negative = true))
            18 -> state.copy(lx = half(state.lx, negative = false))
            17 -> state.copy(ly = half(state.ly, negative = true))
            19 -> state.copy(ly = half(state.ly, negative = false))
            20 -> state.copy(rx = half(state.rx, negative = true))
            22 -> state.copy(rx = half(state.rx, negative = false))
            21 -> state.copy(ry = half(state.ry, negative = true))
            23 -> state.copy(ry = half(state.ry, negative = false))
            else -> state
        }
    }

    private fun trigger(previous: Int, next: Int, key: Int, changes: MutableList<KeyChange>) {
        val was = previous > 127
        val now = next > 127
        if (was != now) changes += KeyChange(key, now, UNUSED)
    }

    /** A negative value goes on [negKey], a positive one on [posKey], zero releases both. */
    private fun axis(previous: Int, next: Int, negKey: Int, posKey: Int, changes: MutableList<KeyChange>) {
        if (previous == next) return
        when {
            next < 0 -> { if (previous > 0) changes += KeyChange(posKey, false, 0); changes += KeyChange(negKey, true, next) }
            next > 0 -> { if (previous < 0) changes += KeyChange(negKey, false, 0); changes += KeyChange(posKey, true, next) }
            else -> changes += KeyChange(if (previous < 0) negKey else posKey, false, 0)
        }
    }
}
