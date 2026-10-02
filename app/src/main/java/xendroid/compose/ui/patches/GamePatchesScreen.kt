package xendroid.compose.ui.patches

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
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } })
    }
    removing?.let { file ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove \"${file.variantLabel}\"?") },
            text = { Text("The file you added is deleted from the patches folder; the game's own patches stay.") },
            confirmButton = { TextButton(onClick = { removing = null; vm.removeUserPatch(file) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (gameName.isNotBlank()) "Patches · $gameName" else "Patches",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { pickFile.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add a patch file")
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
                        "No bundled patches for this game. + adds a .patch.toml of your own.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                    )
                is GamePatchesViewModel.UiState.Error ->
                    Text(s.message, modifier = Modifier.padding(24.dp))
                is GamePatchesViewModel.UiState.Loaded ->
                    PatchList(files = s.files, conflicts = s.conflicts, onToggle = vm::toggle, onUpdate = vm::update,
                        onRemove = { removing = it })
            }
        }
    }
}

@Composable
private fun PatchList(
    files: List<PatchFile>,
    conflicts: List<xendroid.compose.patches.PatchConflict>,
    onToggle: (PatchFile, PatchEntry, Boolean) -> Unit,
    onUpdate: (PatchFile, GamePatchesViewModel.UpdateAction) -> Unit,
    onRemove: (PatchFile) -> Unit,
) {
    val showHeaders = files.size > 1
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "Patches apply on the next launch of this game, and only if they match your game's version.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (conflicts.isNotEmpty()) {
            item(key = "conflicts") {
                OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        "These are on together and change the same memory; which one wins is not defined, so " +
                            "turn one of each pair off:\n" + conflicts.joinToString("\n") {
                                "• ${it.first} × ${it.second} at 0x%08X".format(it.address)
                            },
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
                            if (file.mine) "${file.variantLabel} · added by you" else file.variantLabel,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (file.mine) TextButton(onClick = { onRemove(file) }) { Text("Remove") }
                    }
                }
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

/** L10: news about this file's bundled catalog: applied by itself (undo) or waiting (preview). */
@Composable
private fun CatalogUpdateCard(update: xendroid.compose.patches.PatchUpdate, onAction: (GamePatchesViewModel.UpdateAction) -> Unit) {
    val gone = update.dropped.takeIf { it.isNotEmpty() }?.let { " No longer in it, so now off: ${it.joinToString(", ")}." } ?: ""
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (update.pending) {
                    "This app has a newer version of these patches, but this file was changed by hand. Updating " +
                        "keeps on what you had on (${update.keptOn.size}) and saves your file to undo.$gone"
                } else {
                    "Updated to the patches of this app version; what you had on stays on (${update.keptOn.size}).$gone"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (update.pending) {
                    TextButton(onClick = { onAction(GamePatchesViewModel.UpdateAction.KEEP_MINE) }) { Text("Keep mine") }
                    TextButton(onClick = { onAction(GamePatchesViewModel.UpdateAction.APPLY) }) { Text("Update") }
                } else {
                    if (update.canUndo) {
                        TextButton(onClick = { onAction(GamePatchesViewModel.UpdateAction.UNDO) }) { Text("Undo") }
                    }
                    TextButton(onClick = { onAction(GamePatchesViewModel.UpdateAction.DISMISS) }) { Text("OK") }
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
                entry.author?.takeIf { it.isNotBlank() }?.let { "by $it" },
            ).joinToString("\n")
            if (sub.isNotBlank()) Text(sub)
        },
        trailingContent = {
            Switch(checked = entry.isEnabled, onCheckedChange = onCheckedChange)
        },
    )
}
