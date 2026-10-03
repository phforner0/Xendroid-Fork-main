package xendroid.compose.ui.library

import androidx.compose.ui.res.pluralStringResource
import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import xendroid.compose.data.MissingTitle
import xendroid.compose.data.MissingTitles
import xendroid.compose.sessions.TitleActivity
import xendroid.compose.sessions.formatPlayTime

/**
 * L06: games played or seen before that the last scan did not list. "Remove from list" only
 * hides one here; its play time, compatibility notes, saves and cover are kept, and it comes
 * back by itself when the file is found again.
 */
@Composable
fun MissingGamesDialog(
    missing: List<MissingTitle>,
    activity: Map<String, TitleActivity>,
    coverOf: (String) -> Any,
    onRemove: (MissingTitle) -> Unit,
    onManageFolders: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.missing_title)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (missing.isEmpty()) Text(stringResource(R.string.missing_none))
                missing.forEach { title ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(coverOf(title.titleId)).build(),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(title.name, style = MaterialTheme.typography.bodyLarge)
                            Text(reasonText(title.reason), style = MaterialTheme.typography.bodySmall)
                            Text(title.lastPath, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            activity[title.titleId]?.let { played ->
                                Text(pluralStringResource(R.plurals.missing_played, played.runs, formatPlayTime(played.playedMs), played.runs, title.titleId),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(onClick = { onRemove(title) }) { Text(stringResource(R.string.common_remove)) }
                    }
                }
                Text(stringResource(R.string.missing_note),
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
        dismissButton = { TextButton(onClick = onManageFolders) { Text(stringResource(R.string.lib_menu_folders)) } },
    )
}

@Composable
private fun reasonText(reason: MissingTitles.Reason): String = when (reason) {
    MissingTitles.Reason.FILE_GONE -> stringResource(R.string.missing_file_gone)
    MissingTitles.Reason.FOLDER_AWAY -> stringResource(R.string.missing_folder_away)
    MissingTitles.Reason.OUTSIDE_FOLDERS -> stringResource(R.string.missing_outside)
}
