package xendroid.compose.ui.ingame

import android.content.Context
import xendroid.compose.R
import xendroid.compose.companion.CompanionHost
import xendroid.compose.companion.CompanionHostControl.Status

/* U02: the in-game menu's phone controllers line and its details, in the shown language
 * (CompanionHostControl's own English texts stay for logs and tests). */

fun phoneControllersLabel(context: Context, status: Status): String = when (status) {
    Status.Off -> context.getString(R.string.phone_ctl_off)
    Status.Starting -> context.getString(R.string.phone_ctl_starting)
    Status.Stopping -> context.getString(R.string.phone_ctl_stopping)
    is Status.Failed -> context.getString(R.string.phone_ctl_failed, when (status.why) {
        Status.Failed.Why.NO_NETWORK -> context.getString(R.string.phone_ctl_no_network)
        Status.Failed.Why.CANNOT_LISTEN -> context.getString(R.string.phone_ctl_cannot_listen, status.detail)
    })
    is Status.On -> context.getString(R.string.phone_ctl_on)
}

/** While on: what the other phones need (address, code) and who is playing. */
fun phoneControllersDetails(context: Context, status: Status): String? {
    val on = status as? Status.On ?: return null
    val host = on.host
    val players = host.connected.joinToString(" · ") { player ->
        "P${player.slot + 1} ${player.name}" + (player.latencyMs?.let { " ($it ms)" } ?: "")
    }
    return buildString {
        append(context.getString(R.string.phone_ctl_how, "${host.address.hostAddress}:${host.port}", host.code, networkName(context, on.network)))
        append('\n')
        append(if (players.isEmpty()) context.getString(R.string.phone_ctl_nobody) else players)
        if (host.locked) append('\n').append(context.getString(R.string.phone_ctl_locked, CompanionHost.MAX_WRONG_CODES))
    }
}

/** The kinds CompanionNetwork names; Wi-Fi and Ethernet read the same in both languages. */
private fun networkName(context: Context, network: String): String = when (network) {
    "hotspot" -> context.getString(R.string.phone_ctl_net_hotspot)
    "USB tethering" -> context.getString(R.string.phone_ctl_net_usb)
    "Bluetooth tethering" -> context.getString(R.string.phone_ctl_net_bluetooth)
    "local network" -> context.getString(R.string.phone_ctl_net_local)
    else -> network
}
