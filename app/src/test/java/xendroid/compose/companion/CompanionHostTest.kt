package xendroid.compose.companion

import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import xendroid.compose.gamepad.ControllerSlots

/** Real sockets over loopback: two (or more) test clients play against one host. */
class CompanionHostTest {
    private val slots = ControllerSlots()
    private val inputs = CopyOnWriteArrayList<Pair<Int, PadKeys.KeyChange>>()
    private val events = CopyOnWriteArrayList<String>()
    private val closeables = Collections.synchronizedList(mutableListOf<AutoCloseable>())

    private fun host(slotCount: ControllerSlots = slots, timeoutMs: Long = 400): CompanionHost =
        CompanionHost(
            bindAddress = InetAddress.getLoopbackAddress(),
            claimSlot = { key, _ -> slotCount.connectRemote(key) },
            releaseSlot = { key, _ -> slotCount.disconnect(key) },
            onInput = { slot, change -> inputs += slot to change },
            onEvent = { events += it },
            timeoutMs = timeoutMs,
            heartbeatMs = 50,
            handshakeTimeoutMs = 2_000,
        ).start().also { closeables += it }

    private fun client(
        host: CompanionHost, code: String = host.code, id: Int = 1,
        rumble: MutableList<Pair<Int, Int>> = mutableListOf(), closed: MutableList<String> = mutableListOf(),
    ) = CompanionClient("127.0.0.1", host.port, code, "Phone $id", ByteArray(16) { id.toByte() },
        onRumble = { l, r -> synchronized(rumble) { rumble += l to r } },
        onClosed = { synchronized(closed) { closed += it } },
    ).also { closeables += it }

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

    private fun changes(slot: Int) = inputs.filter { it.first == slot }.map { it.second }

    @Test fun twoPhonesJoinAsP2AndP3AndDriveTheirOwnSlots() {
        val host = host()
        val first = client(host, id = 1)
        val second = client(host, id = 2)
        assertEquals(1, first.connect())
        assertEquals(2, second.connect())
        assertEquals(listOf(null, "companion:", "companion:", null), slots.players.map { it?.substringBefore(':')?.plus(":") })

        first.update(PadState(buttons = 0x1000))                 // A on P2
        second.update(PadState(ly = 30000))                      // stick up on P3
        eventually("both inputs") { changes(1).isNotEmpty() && changes(2).isNotEmpty() }
        assertEquals(listOf(PadKeys.KeyChange(4, true, -1)), changes(1))
        assertEquals(listOf(PadKeys.KeyChange(19, true, 30000)), changes(2))
        eventually("both listed") { host.connected.size == 2 }
        assertEquals(listOf(1, 2), host.connected.map { it.slot })
        assertEquals(listOf("Phone 1", "Phone 2"), host.connected.map { it.name })
        assertTrue("no phone name in the run's events", events.none { "Phone" in it })
    }

    @Test fun aWrongCodeIsRefusedAndTakesNoSlot() {
        val host = host()
        val wrong = if (host.code == "000000") "000001" else "000000"
        try {
            client(host, code = wrong).connect()
            fail("a wrong code must be refused")
        } catch (e: CompanionRejected) {
            assertEquals(CompanionProtocol.REJECT_PROOF, e.reason)
        }
        assertEquals(listOf<String?>(null, null, null, null), slots.players)
        assertTrue(host.connected.isEmpty())
    }

    @Test fun pairingLocksAfterTooManyWrongCodes() {
        val host = host()
        val wrong = if (host.code == "000000") "000001" else "000000"
        repeat(CompanionHost.MAX_WRONG_CODES) {
            try { client(host, code = wrong, id = 10 + it).connect(); fail() } catch (e: CompanionRejected) {
                assertEquals(CompanionProtocol.REJECT_PROOF, e.reason)
            }
        }
        assertTrue(host.locked)
        try {
            client(host).connect()
            fail("even the right code is refused once pairing is locked")
        } catch (e: CompanionRejected) {
            assertEquals(CompanionProtocol.REJECT_CLOSED, e.reason)
        }
    }

    @Test fun aPhoneBeyondTheFreeSlotsIsRefused() {
        val two = ControllerSlots(slotCount = 2)                 // P1 + one remote slot
        val host = host(two)
        assertEquals(1, client(host, id = 1).connect())
        try {
            client(host, id = 2).connect()
            fail("no free slot")
        } catch (e: CompanionRejected) {
            assertEquals(CompanionProtocol.REJECT_FULL, e.reason)
        }
    }

    @Test fun leavingReleasesWhatThePhoneHeldAndFreesItsSlot() {
        val host = host()
        val phone = client(host)
        phone.connect()
        phone.update(PadState(buttons = 0x1000, rt = 255))
        eventually("pressed") { changes(1).size == 2 }
        phone.close()
        // The "left" event is the last step of a release.
        eventually("released") { events.any { "left: phone left" in it } }
        assertEquals(listOf(PadKeys.KeyChange(4, false, -1), PadKeys.KeyChange(15, false, -1)), changes(1).drop(2))
        assertTrue(host.connected.isEmpty())
        assertEquals(listOf<String?>(null, null, null, null), slots.players)
    }

    @Test fun aSilentPhoneIsReleasedAfterTheTimeoutAndOldStatesAreIgnored() {
        val host = host(timeoutMs = 300)
        // A raw client: pairs, then never answers pings.
        val socket = Socket("127.0.0.1", host.port).also { closeables += it }
        val input = socket.getInputStream().buffered()
        val challenge = CompanionCodec.read(input) as CompanionMessage.Challenge
        val id = ByteArray(16) { 9 }
        CompanionCodec.write(socket.getOutputStream(), CompanionMessage.Hello(1, id, "raw", CompanionProtocol.proof(host.code, challenge.nonce, id)))
        assertEquals(CompanionMessage.Welcome(1), CompanionCodec.read(input))

        CompanionCodec.write(socket.getOutputStream(), CompanionMessage.State(5, PadState(buttons = 0x1000)))
        CompanionCodec.write(socket.getOutputStream(), CompanionMessage.State(3, PadState(buttons = 0x2000)))   // older: ignored
        eventually("A pressed") { changes(1).isNotEmpty() }
        eventually("timed out") { events.any { "no data for" in it } }
        assertEquals(listOf(PadKeys.KeyChange(4, true, -1), PadKeys.KeyChange(4, false, -1)), changes(1))
        assertTrue(host.connected.isEmpty())
        assertEquals(listOf<String?>(null, null, null, null), slots.players)
        drainUntilBye(input)
    }

    private fun drainUntilBye(input: InputStream) {
        while (true) {
            val message = try { CompanionCodec.read(input) } catch (e: CompanionProtocolException) { return }
            if (message == CompanionMessage.Bye) return
        }
    }

    @Test fun reconnectingReplacesTheOldConnectionAndStartsReleased() {
        val host = host()
        val closed = mutableListOf<String>()
        val old = client(host, id = 3, closed = closed)
        assertEquals(1, old.connect())
        old.update(PadState(buttons = 0x1000))
        eventually("pressed") { changes(1).size == 1 }

        val again = client(host, id = 3)                        // same phone, new connection
        assertEquals(1, again.connect())
        eventually("old connection closed") { synchronized(closed) { closed.isNotEmpty() } }
        assertEquals(PadKeys.KeyChange(4, false, -1), changes(1)[1])
        assertEquals(1, host.connected.size)

        again.update(PadState(buttons = 0x2000))
        eventually("B from the new connection") { changes(1).size == 3 }
        assertEquals(PadKeys.KeyChange(5, true, -1), changes(1)[2])
    }

    @Test fun rumbleReachesThePhonePlayingThatSlot() {
        val host = host()
        val rumbleOne = mutableListOf<Pair<Int, Int>>()
        val rumbleTwo = mutableListOf<Pair<Int, Int>>()
        client(host, id = 1, rumble = rumbleOne).connect()
        client(host, id = 2, rumble = rumbleTwo).connect()
        host.sendRumble(2, 1000, 65535)
        eventually("rumble on P3's phone") { synchronized(rumbleTwo) { rumbleTwo.isNotEmpty() } }
        assertEquals(listOf(1000 to 65535), synchronized(rumbleTwo) { rumbleTwo.toList() })
        assertTrue(synchronized(rumbleOne) { rumbleOne.isEmpty() })
    }

    @Test fun heldInputIsReleasedAndTheCurrentStateComesBackAfterwards() {
        val host = host(timeoutMs = 10_000)
        val socket = Socket("127.0.0.1", host.port).also { closeables += it }
        val input = socket.getInputStream().buffered()
        val out = socket.getOutputStream()
        val challenge = CompanionCodec.read(input) as CompanionMessage.Challenge
        val id = ByteArray(16) { 4 }
        CompanionCodec.write(out, CompanionMessage.Hello(1, id, "raw", CompanionProtocol.proof(host.code, challenge.nonce, id)))
        assertEquals(CompanionMessage.Welcome(1), CompanionCodec.read(input))
        // The host handles a phone's frames in order: once it measured this PONG, it has seen every frame before it.
        fun sync(markMs: Long) {
            CompanionCodec.write(out, CompanionMessage.Pong(System.currentTimeMillis() - markMs))
            eventually("host caught up") { (host.connected.singleOrNull()?.latencyMs ?: 0) >= markMs }
        }

        CompanionCodec.write(out, CompanionMessage.State(1, PadState(buttons = 0x1000, lx = -20000)))
        sync(10_000)
        assertEquals(listOf(PadKeys.KeyChange(4, true, -1), PadKeys.KeyChange(16, true, -20000)), changes(1))

        host.holdInput(true)                                     // the game's menu opened
        assertEquals(listOf(PadKeys.KeyChange(4, false, -1), PadKeys.KeyChange(16, false, 0)), changes(1).drop(2))
        CompanionCodec.write(out, CompanionMessage.State(2, PadState(buttons = 0x2000, lx = -20000)))
        sync(20_000)
        assertEquals("nothing reaches the guest while held", 4, changes(1).size)

        host.holdInput(false)                                    // back to the game: B and the stick, as held now
        assertEquals(listOf(PadKeys.KeyChange(5, true, -1), PadKeys.KeyChange(16, true, -20000)), changes(1).drop(4))
        host.holdInput(false)
        assertEquals(6, changes(1).size)
    }

    @Test fun heartbeatsMeasureLatency() {
        val host = host()
        client(host).connect()
        eventually("latency measured") { host.connected.singleOrNull()?.latencyMs != null }
    }

    @Test fun turningCompanionModeOffReleasesEveryPhone() {
        val host = host()
        val closedOne = mutableListOf<String>()
        val phone = client(host, id = 1, closed = closedOne)
        phone.connect()
        phone.update(PadState(lx = -20000))
        eventually("stick left") { changes(1).isNotEmpty() }
        host.close()
        assertEquals(PadKeys.KeyChange(16, false, 0), changes(1).last())
        assertEquals(listOf<String?>(null, null, null, null), slots.players)
        eventually("phone told") { synchronized(closedOne) { closedOne.isNotEmpty() } }
        assertTrue(!phone.isOpen)
    }

    @Test fun idleHandshakesAreCapped() {
        val host = host()
        val idle = (1..CompanionHost.MAX_HANDSHAKES).map { Socket("127.0.0.1", host.port).also { s -> closeables += s } }
        idle.forEach { assertTrue(CompanionCodec.read(it.getInputStream()) is CompanionMessage.Challenge) }
        val extra = Socket("127.0.0.1", host.port).also { closeables += it }
        extra.soTimeout = 2_000
        try {
            CompanionCodec.read(extra.getInputStream())
            fail("a handshake over the cap is closed at once")
        } catch (e: java.io.IOException) {
            // closed without a challenge (EOF or reset)
            assertTrue(e !is java.net.SocketTimeoutException)
        }
    }
}
