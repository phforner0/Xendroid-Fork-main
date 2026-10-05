package xendroid.compose.sessions

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class TombstonesTest {
    /** Just enough protobuf writing to build tombstone.proto messages. */
    private class Proto {
        private val out = ByteArrayOutputStream()
        private fun varint(value: Long) {
            var v = value
            while (v and 0x7fL.inv() != 0L) { out.write(((v and 0x7f) or 0x80).toInt()); v = v ushr 7 }
            out.write(v.toInt())
        }
        private fun tag(field: Int, type: Int) = varint(((field shl 3) or type).toLong())
        fun int(field: Int, value: Long) = apply { tag(field, 0); varint(value) }
        fun bytes(field: Int, value: ByteArray) = apply { tag(field, 2); varint(value.size.toLong()); out.write(value) }
        fun str(field: Int, value: String) = bytes(field, value.toByteArray())
        fun msg(field: Int, value: Proto) = bytes(field, value.toBytes())
        fun fixed64(field: Int, value: Long) = apply { tag(field, 1); repeat(8) { out.write(((value ushr (8 * it)) and 0xff).toInt()) } }
        fun fixed32(field: Int, value: Int) = apply { tag(field, 5); repeat(4) { out.write((value ushr (8 * it)) and 0xff) } }
        fun toBytes(): ByteArray = out.toByteArray()
    }

    private val lib = "/data/app/~~Zx9q==/xendroid.compose-Ab3d==/lib/arm64/libe.so"

    private fun frame(relPc: Long, function: String?, offset: Long, file: String, buildId: String?) = Proto()
        .int(1, relPc).int(2, relPc + 0x7000000000).int(3, 0x7ff0001000)
        .also { if (function != null) it.str(4, function).int(5, offset) }
        .str(6, file).int(7, 0)
        .also { if (buildId != null) it.str(8, buildId) }

    private fun thread(id: Long, name: String, frames: List<Proto>) = Proto()
        .int(1, id).str(2, name)
        .msg(3, Proto().str(1, "x0").int(2, 0x10))                             // a register
        .also { t -> frames.forEach { t.msg(4, it) } }
        .int(6, 0)

    private fun tombstone(crashingFrames: List<Proto>) = Proto()
        .int(1, 3)                                                             // arch: arm64
        .str(2, "Xiaomi/fingerprint:15/user")
        .int(5, 4300).int(6, 4321).int(7, 10123)
        .str(9, "xendroid.compose:emu")
        .msg(10, Proto().int(1, 11).str(2, "SIGSEGV").int(3, 1).str(4, "SEGV_MAPERR").int(8, 1).int(9, 0x10))
        .str(14, "")
        .msg(15, Proto().str(1, "null pointer dereference"))
        .msg(16, Proto().int(1, 4300).msg(2, thread(4300, "main", listOf(frame(0x99, "nativePollOnce", 4, "/system/lib64/libandroid_runtime.so", null)))))
        .msg(16, Proto().int(1, 4321).msg(2, thread(4321, "GPU Commands", crashingFrames)))
        .msg(17, Proto().int(1, 0x7000000000).str(7, lib))                   // a memory mapping
        .msg(18, Proto().str(1, "main").msg(2, Proto().str(7, "pedro@example.com signed in")))  // logcat
        .fixed64(30, 1L).fixed32(31, 7)                                        // fields this reader does not know
        .toBytes()

    private val frames = listOf(
        frame(0x1a2b3c, "_ZN2xe3gpu6vulkan23VulkanCommandProcessor13IssueDrawEv", 40, lib, "deadbeefcafe"),
        frame(0x4f5c8, "abort", 168, "/apex/com.android.runtime/lib64/bionic/libc.so", null),
        frame(0x1234, null, 0, "[anon:xenia-code-cache]", null),
    )

    @Test fun theCrashingThreadIsReadWithoutPathsOrOtherThreads() {
        val crash = Tombstones.parse(tombstone(frames))!!
        assertEquals("GPU Commands (tid 4321)", crash.thread)
        assertEquals("SIGSEGV (SEGV_MAPERR) at 0x0000000000000010", crash.signal)
        assertEquals("null pointer dereference", crash.cause)
        assertNull(crash.abortMessage)                                         // empty is no message
        assertEquals(listOf(
            "#00 pc 00000000001a2b3c  libe.so (_ZN2xe3gpu6vulkan23VulkanCommandProcessor13IssueDrawEv+40) (BuildId: deadbeefcafe)",
            "#01 pc 000000000004f5c8  libc.so (abort+168)",
            "#02 pc 0000000000001234  [anon:xenia-code-cache]",
        ), crash.frames)
        val all = crash.toString()
        assertFalse("/data/app" in all || "pedro" in all || "nativePollOnce" in all)
        assertEquals("GPU Commands (tid 4321) · SIGSEGV (SEGV_MAPERR) at 0x0000000000000010 · null pointer dereference",
            describeNativeCrash(crash))
    }

    @Test fun aLongBacktraceKeepsItsInnermostFrames() {
        val crash = Tombstones.parse(tombstone(frames), maxFrames = 2)!!
        assertEquals(3, crash.frames.size)
        assertEquals("(1 more frame)", crash.frames.last())
        assertEquals("(2 more frames)", Tombstones.parse(tombstone(frames), maxFrames = 1)!!.frames.last())
    }

    @Test fun cutOrDamagedTombstonesNeverThrow() {
        val bytes = tombstone(frames)
        for (size in 0..bytes.size) Tombstones.parse(bytes.copyOf(size))         // every prefix
        // Cut inside the logs: the crash is still there.
        val cut = Tombstones.parse(bytes.copyOf(bytes.size - 20))!!
        assertEquals("GPU Commands (tid 4321)", cut.thread)
        assertEquals(3, cut.frames.size)
        assertNull(Tombstones.parse(ByteArray(0)))
        val random = Random(42)
        repeat(200) { Tombstones.parse(random.nextBytes(random.nextInt(1, 512))) }
        assertNull(Tombstones.parse(byteArrayOf(0x0b, 0x01)))                    // a group: unsupported
    }

    @Test fun theReadStopsAtItsBound() {
        val big = ByteArray(Tombstones.MAX_BYTES + 100) { 1 }
        assertEquals(Tombstones.MAX_BYTES, Tombstones.read(ByteArrayInputStream(big)).size)
        assertEquals(10, Tombstones.read(ByteArrayInputStream(ByteArray(10))).size)
    }
}
