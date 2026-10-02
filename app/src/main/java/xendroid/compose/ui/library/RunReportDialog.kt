package xendroid.compose.ui.library

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
import xendroid.compose.sessions.RunReport
import xendroid.compose.sessions.RunReports

/** C06: the user sees what a run report contains before anything leaves the device. */
@Composable
fun RunReportDialog(report: RunReport, busy: Boolean, onShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share last run report") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RunReports.preview(report).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("Paths, accounts and addresses are removed; logs are not included (Diagnostics shares those). " +
                    "Nothing is sent until you pick where to share it.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onShare) { Text("Share") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
