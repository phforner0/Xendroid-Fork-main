package xendroid.compose.ui.library

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * L03: the folders the library scans. Removing one only stops scanning it: no file is
 * moved or deleted. The first folder receives full-game installs from "Install content".
 */
@Composable
fun GameFoldersDialog(
    folders: List<String>,
    unavailable: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lib_menu_folders)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (folders.isEmpty()) Text(stringResource(R.string.folders_none))
                folders.forEachIndexed { index, folder ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(folder, style = MaterialTheme.typography.bodyMedium)
                            val notes = listOfNotNull(
                                stringResource(R.string.folders_installs_here).takeIf { index == 0 },
                                stringResource(R.string.folders_unavailable).takeIf { folder in unavailable },
                            )
                            if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onRemove(folder) }) { Text(stringResource(R.string.common_remove)) }
                    }
                }
                Text(stringResource(R.string.folders_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text(stringResource(R.string.folders_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
