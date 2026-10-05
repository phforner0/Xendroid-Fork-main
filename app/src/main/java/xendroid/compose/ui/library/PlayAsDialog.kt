package xendroid.compose.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xendroid.compose.R
import xendroid.compose.core.ProfilePaths
import xendroid.compose.data.PlayableProfile
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdSwitch
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.focusRing
import xendroid.compose.ui.profile.ProfileAvatar

/**
 * U11: which local profile signs in (P1) for this game: tap one and the game starts as it. Saves
 * and achievements stay with each profile's XUID; nothing is moved. "Don't ask again" keeps
 * playing as the chosen one (it can be turned back on in Profiles).
 */
@Composable
fun PlayAsDialog(
    profiles: List<PlayableProfile>,
    preselected: String,
    onPlay: (xuid: String, dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
    otherPlayers: Map<String, Int> = emptyMap(),
    gameName: String? = null,
) {
    val c = Xd.colors
    var dontAsk by remember { mutableStateOf(false) }
    XdSheet(onDismiss = onDismiss, title = stringResource(R.string.playas_title), subtitle = gameName) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            profiles.forEach { profile ->
                val chosen = profile.xuid.equals(preselected, ignoreCase = true)
                val player = otherPlayers[profile.xuid.uppercase()]
                val hasAvatar = remember(profile.xuid) { runCatching { ProfilePaths.tile64Path(profile.xuid).isFile }.getOrDefault(false) }
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().focusRing(shape).clip(shape)
                        .clickable(role = Role.Button) { onPlay(profile.xuid, dontAsk) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileAvatar(profile.xuid, profile.gamertag, hasAvatar, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(profile.gamertag.ifBlank { profile.xuid }, style = XdText.label, color = c.fg)
                        Text(when {
                            chosen -> stringResource(R.string.pf_active_p1)
                            player != null -> stringResource(R.string.playas_moves, player)
                            else -> profile.xuid
                        }, style = XdText.small, color = if (chosen) c.acc else c.fg3)
                    }
                    Icon(XdIcons.play, null, Modifier.size(18.dp), tint = if (chosen) c.acc else c.fg3)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            XdSwitch(dontAsk, { dontAsk = it })
            Text(stringResource(R.string.playas_dont_ask), style = XdText.body, color = c.fg)
        }
        XdNote(stringResource(R.string.playas_note))
    }
}
