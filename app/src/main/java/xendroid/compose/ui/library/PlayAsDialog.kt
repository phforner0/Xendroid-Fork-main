package xendroid.compose.ui.library

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
) {
    var chosen by remember { mutableStateOf(preselected) }
    var dontAsk by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play as") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                profiles.forEach { profile ->
                    Row(Modifier.fillMaxWidth().clickable { chosen = profile.xuid }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen == profile.xuid, onClick = { chosen = profile.xuid })
                        Text(profile.gamertag.ifBlank { profile.xuid })
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { dontAsk = !dontAsk }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                    Text("Don't ask again", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Each profile keeps its own saves.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onPlay(chosen, dontAsk) }) { Text("Play") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
