package xendroid.compose.companion

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionNetworkTest {
    private fun ip(text: String) = InetAddress.getByName(text)
    private fun candidate(name: String, address: String, up: Boolean = true, loopback: Boolean = false, virtual: Boolean = false) =
        LanCandidate(name, ip(address), up, loopback, virtual)

    @Test fun onlyPrivateIpv4Ranges() {
        listOf("10.0.0.5", "172.16.0.1", "172.31.255.254", "192.168.43.1").forEach { assertTrue(it, CompanionNetwork.isPrivateIpv4(ip(it))) }
        listOf("0.0.0.0", "127.0.0.1", "100.64.0.1", "100.127.255.1", "169.254.10.10", "172.32.0.1", "8.8.8.8", "fe80::1", "fd00::1")
            .forEach { assertFalse(it, CompanionNetwork.isPrivateIpv4(ip(it))) }
    }

    @Test fun wiFiIsPreferredAndMobileDataNeverChosen() {
        val chosen = CompanionNetwork.choose(listOf(
            candidate("rmnet_data0", "10.120.3.4"),                // carrier: private range, still not a LAN
            candidate("ccmni1", "10.0.0.9"),
            candidate("tun0", "10.8.0.2"),                         // VPN
            candidate("eth0", "192.168.0.20"),
            candidate("wlan0", "fe80::1234"),                      // IPv6 only on the Wi-Fi itself...
            candidate("wlan0", "192.168.1.20"),                    // ...and its IPv4
        ))
        assertEquals("wlan0", chosen?.interfaceName)
        assertEquals(ip("192.168.1.20"), chosen?.address)
        assertEquals("Wi-Fi", CompanionNetwork.kindOf("wlan0"))
    }

    @Test fun theHotspotWorksWithoutWiFi() {
        // Pixel's hotspot is wlan1; Samsung's swlan0; MediaTek's ap0.
        assertEquals("wlan1", CompanionNetwork.choose(listOf(candidate("rmnet0", "10.1.1.1"), candidate("wlan1", "192.168.43.1")))?.interfaceName)
        assertEquals("swlan0", CompanionNetwork.choose(listOf(candidate("swlan0", "192.168.43.1"), candidate("rmnet0", "10.1.1.1")))?.interfaceName)
        assertEquals("hotspot", CompanionNetwork.kindOf("ap0"))
        assertEquals("wlan0", CompanionNetwork.choose(listOf(candidate("wlan1", "192.168.43.1"), candidate("wlan0", "192.168.1.20")))?.interfaceName)
    }

    @Test fun downLoopbackVirtualAndPublicAddressesAreSkipped() {
        assertNull(CompanionNetwork.choose(listOf(
            candidate("wlan0", "192.168.1.20", up = false),
            candidate("lo", "127.0.0.1", loopback = true),
            candidate("wlan0:1", "192.168.1.21", virtual = true),
            candidate("wlan0", "100.64.3.3"),                      // carrier-grade NAT
            candidate("eth0", "203.0.113.7"),                      // public
            candidate("wlan0", "0.0.0.0"),
        )))
        assertNull(CompanionNetwork.choose(emptyList()))
        assertNull(CompanionNetwork.kindOf("rmnet_data1"))
    }
}
