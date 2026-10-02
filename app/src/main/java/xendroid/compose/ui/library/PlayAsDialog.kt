package xendroid.compose.ui.library

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xendroid.compose.data.PlayableProfile

/**
 * U11: which local profile signs in (P1) for this game. Saves and achievements stay with each
 * profile's XUID; nothing is moved. "Don't ask again" keeps playing as the chosen one (it can be
 * turned back on in Profiles).
 */
@Composable
fun PlayAsDialog(
    profiles: List<PlayableProfile>,
    preselected: String,
    onPlay: (xuid: String, dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
    otherPlayers: Map<String, Int> = emptyMap(),
) {
    var chosen by remember { mutableStateOf(preselected) }
    var dontAsk by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playas_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                profiles.forEach { profile ->
                    Row(Modifier.fillMaxWidth().clickable { chosen = profile.xuid }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen == profile.xuid, onClick = { chosen = profile.xuid })
                        Text(profile.gamertag.ifBlank { profile.xuid } +
                            (otherPlayers[profile.xuid.uppercase()]?.let { " · P$it" } ?: ""))
                    }
                }
                otherPlayers[chosen.uppercase()]?.let { player ->
                    Text(stringResource(R.string.playas_moves, player),
                        style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth().clickable { dontAsk = !dontAsk }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                    Text(stringResource(R.string.playas_dont_ask), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.playas_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onPlay(chosen, dontAsk) }) { Text(stringResource(R.string.lib_play)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
