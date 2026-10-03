package xendroid.compose.sessions

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import kotlinx.serialization.Serializable

/** The crashing thread of a native crash, as the platform's tombstone saw it (kept with the run). */
@Serializable
data class NativeBacktrace(
    /** "GPU Commands (tid 4321)". */
    val thread: String? = null,
    /** "SIGSEGV (SEGV_MAPERR) at 0x0000000000000010". */
    val signal: String? = null,
    /** The platform's own reading of the fault, e.g. "null pointer dereference". */
    val cause: String? = null,
    val abortMessage: String? = null,
    /** "#00 pc 00000000001a2b3c  libe.so (_ZN2xe3gpu…+40) (BuildId: …)", innermost first. */
    val frames: List<String> = emptyList(),
)

/**
 * The crashing thread's backtrace out of an Android tombstone: what
 * ApplicationExitInfo.getTraceInputStream gives for a native crash from API 31, a protobuf
 * of system/core/debuggerd/proto/tombstone.proto. A minimal wire-format reader takes only
 * the fields used here and skips the rest (memory maps, logs, other threads' registers).
 * A cut or damaged tombstone keeps what was read before the damage; nothing ever throws.
 * File names keep their last component only (the install path carries random folders);
 * function names are as the unwinder found them (C++ ones mangled: c++filt reads them).
 */
object Tombstones {
    /** The read stops here; the crashing thread comes long before the memory maps and logs. */
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_FRAMES = 32
    private const val MAX_TEXT = 200

    /** At most [MAX_BYTES] of [input]; a longer tombstone is parsed from its start. */
    fun read(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() < MAX_BYTES) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES - out.size()))
            if (count < 0) break
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    /** Null when the bytes hold neither a signal nor the crashing thread. */
    fun parse(bytes: ByteArray, maxFrames: Int = MAX_FRAMES): NativeBacktrace? {
        var tid = -1L
        var signal: String? = null
        var cause: String? = null
        var abort: String? = null
        val threads = mutableListOf<Pair<Long, Wire>>()
        runCatching {
            val top = Wire(bytes, 0, bytes.size)
            while (top.more) {
                val (field, type) = top.tag()
                when {
                    field == 6 && type == VARINT -> tid = top.varint()
                    field == 10 && type == LEN -> signal = signalOf(top.message())
                    field == 14 && type == LEN -> abort = text(top.string())
                    field == 15 && type == LEN -> { val c = causeOf(top.message()); if (cause == null) cause = c }
                    // map<uint32, Thread>: entries of {1: id, 2: Thread}; only the crashing one is read.
                    field == 16 && type == LEN -> threadEntry(top.message())?.let(threads::add)
                    else -> top.skip(type)
                }
            }
        }
        val crashing = threads.firstOrNull { it.first == tid }?.second?.let { runCatching { threadOf(it, maxFrames) }.getOrNull() }
        if (signal == null && crashing == null) return null
        return NativeBacktrace(
            thread = crashing?.first?.let { name -> "$name (tid $tid)" } ?: tid.takeIf { it >= 0 }?.let { "tid $it" },
            signal = signal, cause = cause, abortMessage = abort, frames = crashing?.second.orEmpty(),
        )
    }

    private fun signalOf(wire: Wire): String? {
        var number = 0L; var name: String? = null; var code: String? = null
        var hasAddress = false; var address = 0L
        while (wire.more) {
            val (field, type) = wire.tag()
            when {
                field == 1 && type == VARINT -> number = wire.varint()
                field == 2 && type == LEN -> name = text(wire.string())
                field == 4 && type == LEN -> code = text(wire.string())
                field == 8 && type == VARINT -> hasAddress = wire.varint() != 0L
                field == 9 && type == VARINT -> address = wire.varint()
                else -> wire.skip(type)
            }
        }
        val head = name ?: if (number > 0) "signal $number" else return null
        return head + (code?.let { " ($it)" } ?: "") + (if (hasAddress) " at 0x%016x".format(Locale.ROOT, address) else "")
    }

    private fun causeOf(wire: Wire): String? {
        while (wire.more) {
            val (field, type) = wire.tag()
            if (field == 1 && type == LEN) return text(wire.string()) else wire.skip(type)
        }
        return null
    }

    private fun threadEntry(wire: Wire): Pair<Long, Wire>? {
        var id: Long? = null; var thread: Wire? = null
        while (wire.more) {
            val (field, type) = wire.tag()
            when {
                field == 1 && type == VARINT -> id = wire.varint()
                field == 2 && type == LEN -> thread = wire.message()
                else -> wire.skip(type)
            }
        }
        return if (id != null && thread != null) id to thread else null
    }

    /** (name, frames) of one Thread message. */
    private fun threadOf(wire: Wire, maxFrames: Int): Pair<String?, List<String>> {
        var name: String? = null
        val frames = mutableListOf<String>()
        var more = 0
        while (wire.more) {
            val (field, type) = wire.tag()
            when {
                field == 2 && type == LEN -> name = text(wire.string())
                field == 4 && type == LEN -> {
                    val frame = wire.message()
                    if (frames.size < maxFrames) frames += frameOf(frame, frames.size) else more++
                }
                else -> wire.skip(type)
            }
        }
        if (more > 0) frames += if (more == 1) "(1 more frame)" else "($more more frames)"
        return name to frames
    }

    private fun frameOf(wire: Wire, index: Int): String {
        var relPc = 0L; var function: String? = null; var offset = 0L; var file: String? = null; var buildId: String? = null
        while (wire.more) {
            val (field, type) = wire.tag()
            when {
                field == 1 && type == VARINT -> relPc = wire.varint()
                field == 4 && type == LEN -> function = text(wire.string())
                field == 5 && type == VARINT -> offset = wire.varint()
                field == 6 && type == LEN -> file = text(wire.string().substringAfterLast('/'))
                field == 8 && type == LEN -> buildId = text(wire.string())
                else -> wire.skip(type)
            }
        }
        return "#%02d pc %016x  %s".format(Locale.ROOT, index, relPc, file ?: "<unknown>") +
            (function?.let { " ($it+$offset)" } ?: "") + (buildId?.let { " (BuildId: $it)" } ?: "")
    }

    /** Printable and bounded; empty becomes null. */
    private fun text(raw: String): String? = raw.filter { it >= ' ' }.take(MAX_TEXT).ifBlank { null }

    private const val VARINT = 0
    private const val I64 = 1
    private const val LEN = 2
    private const val I32 = 5

    /** Protocol buffers wire format over bytes[pos, end); every read is bounds-checked. */
    private class Wire(private val bytes: ByteArray, private var pos: Int, private val end: Int) {
        val more: Boolean get() = pos < end

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                check(pos < end && shift < 64) { "bad varint" }
                val b = bytes[pos++].toInt()
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }

        fun tag(): Pair<Int, Int> {
            val tag = varint()
            check((tag ushr 3) in 1L..Int.MAX_VALUE.toLong()) { "bad tag" }
            return (tag ushr 3).toInt() to (tag and 7).toInt()
        }

        private fun length(): Int {
            val length = varint()
            check(length >= 0 && length <= end - pos) { "bad length" }
            return length.toInt()
        }

        fun message(): Wire {
            val length = length()
            return Wire(bytes, pos, pos + length).also { pos += length }
        }

        fun string(): String {
            val length = length()
            return String(bytes, pos, length, Charsets.UTF_8).also { pos += length }
        }

        fun skip(type: Int) {
            val count = when (type) {
                VARINT -> { varint(); 0 }
                I64 -> 8
                LEN -> length()
                I32 -> 4
                else -> error("unsupported wire type $type")
            }
            check(count <= end - pos) { "truncated" }
            pos += count
        }
    }
}

/** "GPU Commands (tid 4321) · SIGSEGV (SEGV_MAPERR) at 0x… · null pointer dereference". */
fun describeNativeCrash(crash: NativeBacktrace): String =
    listOfNotNull(crash.thread, crash.signal, crash.cause).joinToString(" · ").ifEmpty { "no details" }
