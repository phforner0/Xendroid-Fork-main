package xendroid.compose.ui.library

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xendroid.compose.sessions.RunEventLog
import xendroid.compose.sessions.describeEvent

/** The last run's flight recorder (C01): host events in order, as last saved by the game process. */
@Composable
fun RunTimelineDialog(log: RunEventLog, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lib_timeline)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (log.dropped > 0) Text(stringResource(R.string.timeline_dropped, log.dropped), style = MaterialTheme.typography.bodySmall)
                log.events.forEach { Text(describeEvent(it), style = MaterialTheme.typography.bodySmall) }
                Text("Saved every 30 s and on important events: after a crash the last seconds can be missing.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
