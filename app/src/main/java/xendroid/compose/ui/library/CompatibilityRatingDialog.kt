package xendroid.compose.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xendroid.compose.compatibility.CompatStatus

/** The user's own result for a game; touch and D-pad/A both select through `selectable`. */
@Composable
fun CompatibilityRatingDialog(
    current: CompatStatus?,
    onDismiss: () -> Unit,
    onSave: (CompatStatus, String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(current) }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How does it run on this build?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CompatStatus.entries.forEach { status ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = selected == status, onClick = { selected = status })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == status, onClick = { selected = status })
                        Text(status.label)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 500) note = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Saved on this device with the current build and the driver of the last run.")
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = { selected?.let { onSave(it, note) } }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
