package xendroid.compose.companion

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/** One address of a network interface, as the device reports it. */
data class LanCandidate(
    val interfaceName: String,
    val address: InetAddress,
    val up: Boolean = true,
    val loopback: Boolean = false,
    val virtual: Boolean = false,
)

/**
 * Where the companion host listens (I07): one private IPv4 address of a local network that
 * other phones can join — Wi-Fi, the phone's own hotspot, Ethernet or USB/Bluetooth
 * tethering. Never every interface (0.0.0.0), never mobile data (a carrier can hand out
 * 10.x addresses too, which is why the interface kind matters and not only the address),
 * never VPN, carrier-grade NAT (100.64/10), link-local or loopback.
 */
object CompanionNetwork {
    /** Interface name prefixes by preference: Wi-Fi client first, then hotspot, Ethernet, tethering. */
    private val kinds = listOf(
        "wlan" to "Wi-Fi",
        "swlan" to "hotspot", "ap" to "hotspot", "softap" to "hotspot", "wifi" to "Wi-Fi",
        "eth" to "Ethernet",
        "rndis" to "USB tethering", "usb" to "USB tethering",
        "bt-pan" to "Bluetooth tethering",
    )

    /** 10/8, 172.16/12 and 192.168/16 only. */
    fun isPrivateIpv4(address: InetAddress): Boolean = address is Inet4Address && address.isSiteLocalAddress

    /** Index in [kinds], or -1 for interfaces companions must not use (mobile data, VPN, ...). */
    private fun rank(interfaceName: String): Int {
        val name = interfaceName.lowercase()
        return kinds.indexOfFirst { (prefix, _) -> name.startsWith(prefix) }
    }

    /** What the user calls the network behind [interfaceName] ("Wi-Fi", "hotspot", ...), or null when companions must not use it. */
    fun kindOf(interfaceName: String): String? = kinds.getOrNull(rank(interfaceName))?.second

    fun choose(candidates: List<LanCandidate>): LanCandidate? =
        candidates.filter { it.up && !it.loopback && !it.virtual && isPrivateIpv4(it.address) && rank(it.interfaceName) >= 0 }
            // wlan0 (the Wi-Fi client) before wlan1/wlan2, which carry the hotspot on Pixel and Xiaomi.
            .minWithOrNull(compareBy<LanCandidate>({ rank(it.interfaceName) }, { it.interfaceName }))

    /** The device's interface addresses; empty when Android refuses to list them. */
    fun candidates(): List<LanCandidate> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().flatMap { nif ->
            val up = runCatching { nif.isUp }.getOrDefault(false)
            val loopback = runCatching { nif.isLoopback }.getOrDefault(true)
            nif.inetAddresses.toList().map { LanCandidate(nif.name, it, up, loopback, nif.isVirtual) }
        }
    }.getOrDefault(emptyList())
}
