package xendroid.compose.companion

import java.net.ConnectException
import java.net.InetAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import xendroid.compose.companion.CompanionHostControl.Status
import xendroid.compose.gamepad.ControllerSlots

/** The in-game switch with a real host and real phone clients over loopback. */
class CompanionHostControlTest {
    private val slots = ControllerSlots()
    private val inputs = CopyOnWriteArrayList<Pair<Int, PadKeys.KeyChange>>()
    private val hosts = CopyOnWriteArrayList<CompanionHost>()
    private val closeables = Collections.synchronizedList(mutableListOf<AutoCloseable>())
    private val loopback = LanCandidate("wlan0", InetAddress.getLoopbackAddress())

    private fun control(pick: () -> LanCandidate? = { loopback }) = CompanionHostControl(
        newHost = { lan ->
            CompanionHost(lan.address,
                claimSlot = { key, _ -> slots.connectRemote(key) },
                releaseSlot = { key, _ -> slots.disconnect(key) },
                onInput = { slot, change -> inputs += slot to change },
                heartbeatMs = 50).also { hosts += it }
        },
        pickNetwork = pick,
    ).also { closeables += it }

    private fun phone(host: CompanionHost, name: String = "Ana", rumble: MutableList<Pair<Int, Int>> = mutableListOf()) =
        CompanionClient("127.0.0.1", host.port, host.code, name, ByteArray(16) { 1 },
            onRumble = { left, right -> synchronized(rumble) { rumble += left to right } }).also { closeables += it }

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

    @Test fun turningOnShowsWhatTheOtherPhoneNeedsAndOffReleasesIt() {
        val control = control()
        assertEquals("Phone controllers · Off", control.label())
        assertNull(control.details())
        control.toggle()
        eventually("on") { control.status is Status.On }
        assertEquals("Phone controllers · On (activate to turn off)", control.label())
        val host = control.host!!
        val details = control.details()!!
        assertTrue(details, details.contains("${host.address.hostAddress}:${host.port}, code ${host.code} (Wi-Fi)"))
        assertTrue(details, details.contains("No phone connected yet"))

        val phone = phone(host)
        assertEquals(1, phone.connect())
        eventually("listed") { control.details()!!.contains("P2 Ana") }

        control.toggle()
        eventually("off") { control.status == Status.Off }
        eventually("phone told") { !phone.isOpen }
        assertEquals(listOf<String?>(null, null, null, null), slots.players)
        assertNull(control.host)
    }

    @Test fun withoutALocalNetworkItSaysSoAndCanBeRetried() {
        var network: LanCandidate? = null
        val control = control(pick = { network })
        control.toggle()
        eventually("failed") { control.status is Status.Failed }
        assertEquals("Phone controllers · Off: no Wi-Fi, hotspot or Ethernet network", control.label())
        assertNull(control.details())
        network = loopback
        control.toggle()
        eventually("on after retry") { control.status is Status.On }
    }

    @Test fun aSecondToggleWhileStartingIsIgnored() {
        val gate = CountDownLatch(1)
        val control = control(pick = { gate.await(3, TimeUnit.SECONDS); loopback })
        control.toggle()
        assertEquals(Status.Starting, control.status)
        assertEquals("Phone controllers · starting…", control.label())
        control.toggle()                                          // not queued as "turn off"
        gate.countDown()
        eventually("on") { control.status is Status.On }
        assertEquals(1, hosts.size)
    }

    @Test fun tickHoldsTheInputAndForwardsTheRumbleOfTheSlot() {
        val control = control()
        control.toggle()
        eventually("on") { control.status is Status.On }
        val rumble = mutableListOf<Pair<Int, Int>>()
        val phone = phone(control.host!!, rumble = rumble)
        assertEquals(1, phone.connect())
        val guest = longArrayOf(0, (300L shl 16) or 400L, 0, 0)

        control.tick(playing = true, rumble = guest)
        eventually("rumble") { synchronized(rumble) { rumble.lastOrNull() == (300 to 400) } }
        phone.update(PadState(buttons = 0x1000))
        eventually("A pressed") { inputs.lastOrNull() == (1 to PadKeys.KeyChange(4, true, -1)) }

        control.tick(playing = false, rumble = guest)             // the menu opened
        assertEquals(1 to PadKeys.KeyChange(4, false, -1), inputs.last())
        eventually("rumble stopped") { synchronized(rumble) { rumble.lastOrNull() == (0 to 0) } }

        control.tick(playing = true, rumble = guest)              // back in the game: A still held
        assertEquals(1 to PadKeys.KeyChange(4, true, -1), inputs.last())
        eventually("rumble again") { synchronized(rumble) { rumble.lastOrNull() == (300 to 400) } }
    }

    @Test fun endingTheGameWhileStartingLeavesNoListeningSocket() {
        val gate = CountDownLatch(1)
        val control = control(pick = { gate.await(3, TimeUnit.SECONDS); loopback })
        control.toggle()
        control.close(timeoutMs = 50)                             // the game exits mid-start
        gate.countDown()
        eventually("host created") { hosts.isNotEmpty() }
        val port = hosts.single().port
        eventually("closed again") {
            try { Socket("127.0.0.1", port).close(); false } catch (e: ConnectException) { true }
        }
        assertEquals(Status.Off, control.status)
        control.toggle()                                          // closed for good
        assertEquals(Status.Off, control.status)
    }
}
