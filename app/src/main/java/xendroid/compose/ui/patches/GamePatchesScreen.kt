package xendroid.compose.ui.patches

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xendroid.compose.patches.GamePatchesViewModel
import xendroid.compose.patches.PatchEntry
import xendroid.compose.patches.PatchFile

/**
 * Lists the bundled patches for one game, grouped by file. Each `[[patch]]` is a row with a
 * Switch that toggles its on-disk `is_enabled` (effective on the next launch).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamePatchesScreen(
    vm: GamePatchesViewModel,
    gameName: String,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    // L11: the user's own .patch.toml (no reliable MIME type for TOML, so any file; it is checked).
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importUserPatch(uri)
    }
    var removing by remember { mutableStateOf<PatchFile?>(null) }
    message?.let { text ->
        AlertDialog(onDismissRequest = vm::clearMessage, text = { Text(text) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text(stringResource(R.string.common_ok)) } })
    }
    removing?.let { file ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.pt_remove_title, file.variantLabel)) },
            text = { Text(stringResource(R.string.pt_remove_note)) },
            confirmButton = { TextButton(onClick = { removing = null; vm.removeUserPatch(file) }) { Text(stringResource(R.string.common_remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (gameName.isNotBlank()) stringResource(R.string.pt_title_game, gameName) else stringResource(R.string.pt_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { pickFile.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pt_add))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (val s = state) {
                GamePatchesViewModel.UiState.Loading -> CircularProgressIndicator()
                GamePatchesViewModel.UiState.Empty ->
                    Text(
                        stringResource(R.string.pt_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                    )
                is GamePatchesViewModel.UiState.Error ->
                    Text(s.message, modifier = Modifier.padding(24.dp))
                is GamePatchesViewModel.UiState.Loaded ->
                    PatchList(files = s.files, conflicts = s.conflicts, versions = s.versions, versionKnown = s.versionKnown,
                        onToggle = vm::toggle, onUpdate = vm::update,
                        onRemove = { removing = it })
            }
        }
    }
}

@Composable
private fun PatchList(
    files: List<PatchFile>,
    conflicts: List<xendroid.compose.patches.PatchConflict>,
    versions: Map<String, xendroid.compose.patches.PatchVersion.Match>,
    versionKnown: Boolean,
    onToggle: (PatchFile, PatchEntry, Boolean) -> Unit,
    onUpdate: (PatchFile, GamePatchesViewModel.UpdateAction) -> Unit,
    onRemove: (PatchFile) -> Unit,
) {
    val showHeaders = files.size > 1
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(R.string.pt_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (!versionKnown) {
            item(key = "version-unknown") {
                Text(
                    stringResource(R.string.pt_version_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        if (conflicts.isNotEmpty()) {
            item(key = "conflicts") {
                OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(R.string.pt_conflicts) + "\n" + conflicts.map {
                            stringResource(R.string.pt_conflict_line, it.first, it.second, "0x%08X".format(it.address))
                        }.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
        for (file in files) {
            if (showHeaders || file.mine) {
                item(key = "hdr:${file.fileName}") {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (file.mine) stringResource(R.string.pt_added_by_you, file.variantLabel) else file.variantLabel,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (file.mine) TextButton(onClick = { onRemove(file) }) { Text(stringResource(R.string.common_remove)) }
                    }
                }
            }
            // L10: whether this file is for the version the player last played.
            when (versions[file.fileName]) {
                xendroid.compose.patches.PatchVersion.Match.YOURS -> item(key = "ver:${file.fileName}") {
                    VersionLine(stringResource(R.string.pt_version_yours), MaterialTheme.colorScheme.primary)
                }
                xendroid.compose.patches.PatchVersion.Match.OTHER -> item(key = "ver:${file.fileName}") {
                    VersionLine(stringResource(R.string.pt_version_other), MaterialTheme.colorScheme.error)
                }
                else -> {}
            }
            file.update?.let { update ->
                item(key = "upd:${file.fileName}") { CatalogUpdateCard(update) { onUpdate(file, it) } }
            }
            items(file.entries, key = { "${file.fileName}#${it.index}" }) { entry ->
                PatchRow(
                    entry = entry,
                    onCheckedChange = { checked -> onToggle(file, entry, checked) },
                )
            }
            item(key = "div:${file.fileName}") { HorizontalDivider() }
        }
    }
}

@Composable
private fun VersionLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
}

/** L10: news about this file's bundled catalog: applied by itself (undo) or waiting (preview). */
@Composable
private fun CatalogUpdateCard(update: xendroid.compose.patches.PatchUpdate, onAction: (GamePatchesViewModel.UpdateAction) -> Unit) {
    val gone = update.dropped.takeIf { it.isNotEmpty() }?.let { " " + stringResource(R.string.pt_dropped, it.joinToString(", ")) } ?: ""
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (update.pending) {
                    stringResource(R.string.pt_update_pending, update.keptOn.size) + gone
                } else {
                    stringResource(R.string.pt_updated, update.keptOn.size) + gone
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (update.pending) {
                    TextButton(onClick = { onAction(GamePatchesViewModel.UpdateAction.KEEP_MINE) }) { Text(stringResource(R.string.pt_keep_mine)) }
                    TextButton(onClick = { onAction(GamePatchesViewModel.UpdateAction.APPLY) }) { Text(stringResource(R.string.pt_update)) }
                } else {
                    if (update.canUndo) {
                        TextButton(onClick = { onAction(GamePatchesViewModel.UpdateAction.UNDO) }) { Text(stringResource(R.string.pt_undo)) }
                    }
                    TextButton(onClick = { onAction(GamePatchesViewModel.UpdateAction.DISMISS) }) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}

@Composable
private fun PatchRow(entry: PatchEntry, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(entry.name) },
        supportingContent = {
            val sub = listOfNotNull(
                entry.desc?.takeIf { it.isNotBlank() },
                entry.author?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.pt_by, it) },
            ).joinToString("\n")
            if (sub.isNotBlank()) Text(sub)
        },
        trailingContent = {
            Switch(checked = entry.isEnabled, onCheckedChange = onCheckedChange)
        },
    )
}
