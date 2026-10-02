package xendroid.compose.companion

import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import xendroid.compose.companion.CompanionPadLink.State
import xendroid.compose.companion.CompanionPadLink.Why
import xendroid.compose.gamepad.ControllerSlots
import xendroid.compose.gamepad.Kc

class CompanionTargetTest {
    @Test fun addressesAreIpv4AndAPort() {
        assertEquals(CompanionTarget("192.168.1.20", 41234), CompanionTarget.parse(" 192.168.1.20:41234 "))
        assertEquals("10.0.0.1:1", CompanionTarget.parse("10.0.0.1:1").toString())
        listOf("", "192.168.1.20", "192.168.1.20:", "192.168.1.256:80", "192.168.1.20:0", "192.168.1.20:65536",
            "phone.local:4000", "[fe80::1]:4000", "1.2.3:80", "1.2.3.4:80:90").forEach { assertNull(it, CompanionTarget.parse(it)) }
    }

    @Test fun codesAreSixDigits() {
        assertTrue(CompanionTarget.isCode("012345"))
        assertTrue(CompanionTarget.isCode(" 999999 "))
        listOf("12345", "1234567", "12345a", "").forEach { assertTrue(it, !CompanionTarget.isCode(it)) }
    }
}

/** The phone side's state with a real host over loopback. */
class CompanionPadLinkTest {
    private val slots = ControllerSlots()
    private val inputs = CopyOnWriteArrayList<Pair<Int, PadKeys.KeyChange>>()
    private val rumble = CopyOnWriteArrayList<Long>()
    private val closeables = Collections.synchronizedList(mutableListOf<AutoCloseable>())

    private fun host() = CompanionHost(InetAddress.getLoopbackAddress(),
        claimSlot = { key, _ -> slots.connectRemote(key) },
        releaseSlot = { key, _ -> slots.disconnect(key) },
        onInput = { slot, change -> inputs += slot to change },
        heartbeatMs = 50).start().also { closeables += it }

    private fun link(id: Byte = 1) = CompanionPadLink(
        newClient = { target, code, name, onRumble, onClosed ->
            CompanionClient(target.host, target.port, code, name, ByteArray(16) { id }, onRumble, onClosed).also { closeables += it }
        },
        onRumble = { rumble += it },
    )

    @After fun tearDown() {
        closeables.reversed().forEach { runCatching { it.close() } }
    }

    private fun eventually(what: String, timeoutMs: Long = 3_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (check()) return
            Thread.sleep(10)
        }
        fail("timed out waiting for: $what")
    }

    @Test fun malformedAddressOrCodeNeverConnects() {
        val link = link()
        link.connect("192.168.1.20", "123456", "Ana")
        assertEquals(State.Idle(Why(Why.Kind.BAD_ADDRESS)), link.state.value)
        link.connect("127.0.0.1:4000", "12345", "Ana")
        assertEquals(State.Idle(Why(Why.Kind.BAD_CODE)), link.state.value)
    }

    @Test fun thePadPlaysItsSlotAndLeavingReleasesIt() {
        val host = host()
        val link = link()
        link.connect("127.0.0.1:${host.port}", host.code, "  ")
        assertEquals(State.Playing(1), link.state.value)
        assertEquals("Phone", host.connected.single().name)

        link.key(Kc.A, true, Kc.VALUE_UNUSED)
        link.key(Kc.LTHUMB_RIGHT, false, 0)                      // the emitter's stick: release one half...
        link.key(Kc.LTHUMB_LEFT, true, -20000)                   // ...press the other with its value
        eventually("A and the stick") { inputs.size == 2 }
        assertEquals(listOf(1 to PadKeys.KeyChange(4, true, -1), 1 to PadKeys.KeyChange(16, true, -20000)), inputs.toList())

        link.refresh()
        eventually("latency") { link.refresh(); (link.state.value as? State.Playing)?.latencyMs != null }

        link.leave()
        assertEquals(State.Idle(), link.state.value)
        eventually("released") { inputs.size == 4 }
        assertEquals(setOf(PadKeys.KeyChange(4, false, -1), PadKeys.KeyChange(16, false, 0)), inputs.drop(2).map { it.second }.toSet())
        eventually("slot freed") { slots.players.all { it == null } }
        link.key(Kc.B, true, Kc.VALUE_UNUSED)                    // not connected: goes nowhere
        assertEquals(4, inputs.size)
    }

    @Test fun aWrongCodeSaysSo() {
        val host = host()
        val link = link()
        link.connect("127.0.0.1:${host.port}", if (host.code == "000000") "000001" else "000000", "Ana")
        assertEquals(State.Idle(Why(Why.Kind.REJECTED, "127.0.0.1:${host.port}", code = CompanionProtocol.REJECT_PROOF)), link.state.value)
    }

    @Test fun nobodyListeningSaysWhatToCheck() {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }  // free, then closed
        val link = link()
        link.connect("127.0.0.1:$port", "123456", "Ana")
        assertEquals(State.Idle(Why(Why.Kind.UNREACHABLE, "127.0.0.1:$port")), link.state.value)
    }

    @Test fun theGameEndingIsShownAndStopsTheRumble() {
        val host = host()
        val link = link()
        link.connect("127.0.0.1:${host.port}", host.code, "Ana")
        host.sendRumble(1, 100, 200)
        eventually("rumble") { rumble.lastOrNull() == (100L shl 16) or 200L }
        host.close()
        eventually("idle") { link.state.value is State.Idle }
        assertEquals(link.state.value.toString(), Why.Kind.DISCONNECTED, (link.state.value as State.Idle).why?.kind)
        assertEquals(0L, rumble.last())
        // And it can join again (a new game, a new code).
        val again = host()
        link.connect("127.0.0.1:${again.port}", again.code, "Ana")
        assertEquals(State.Playing(1), link.state.value)
    }
}
