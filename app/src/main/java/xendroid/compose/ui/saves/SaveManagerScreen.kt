package xendroid.compose.ui.saves

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.launch
import xendroid.compose.saves.BackupSync

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveManagerScreen(vm: SaveManagerViewModel, gameName: String, onBack: () -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    var includeProfiles by rememberSaveable { mutableStateOf(true) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var overwriteProfiles by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var syncConfigured by remember { mutableStateOf(BackupSync.configured(context, vm.titleId) != null) }
    var syncAutomatic by remember { mutableStateOf(BackupSync.automatic(context, vm.titleId)) }
    var remoteBackups by remember { mutableStateOf<List<DocumentFile>?>(null) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching { BackupSync.configure(context, vm.titleId, uri); syncConfigured = true }
            .onFailure { android.widget.Toast.makeText(context, "Cannot persist backup folder permission", android.widget.Toast.LENGTH_LONG).show() }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri, selectedIds, includeProfiles)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.import(uri) }
    val busy = operation is SaveManagerViewModel.Operation.Busy
    BackHandler(enabled = busy) { }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Saves · ${gameName.ifEmpty { vm.titleId }}") }, navigationIcon = {
            IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text("Backups preserve save headers and original profile XUIDs. Close the running game before backing up or restoring.")
                Row {
                    Checkbox(checked = includeProfiles, onCheckedChange = { includeProfiles = it }, enabled = !busy)
                    Text("Include associated profile", Modifier.padding(top = 12.dp))
                }
            }
            items(profiles, key = { it.xuid }) { profile ->
                ListItem(headlineContent = { Text(profile.xuid) }, supportingContent = {
                    Text("${profile.saveFiles} files · ${profile.bytes / 1024} KB${if (profile.hasProfile) " · profile available" else ""}")
                }, leadingContent = {
                    Checkbox(profile.xuid in selectedIds, onCheckedChange = { selected ->
                        selectedIds = if (selected) selectedIds + profile.xuid else selectedIds - profile.xuid
                    }, enabled = !busy)
                })
            }
            if (profiles.isEmpty()) item { Text("No saves found for this game.") }
            item {
                Button(enabled = selectedIds.isNotEmpty() && !busy, onClick = { create.launch("xendroid-saves-${vm.titleId}.zip") }) { Text("Export selected profiles") }
                OutlinedButton(enabled = !busy, onClick = { pick.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Import backup") }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("Optional backup synchronization", style = MaterialTheme.typography.titleMedium)
                Text("Choose a local or cloud folder provided by Android. Uploads are immutable; conflicts are restored only after your confirmation.")
                OutlinedButton(enabled = !busy, onClick = { pickFolder.launch(null) }) { Text("Choose synchronization folder") }
                if (syncConfigured) {
                    Row {
                        Checkbox(syncAutomatic, onCheckedChange = {
                            syncAutomatic = it; BackupSync.setAutomatic(context, vm.titleId, it)
                        }, enabled = !busy)
                        Text("Back up on return to the library (game closed)", Modifier.padding(top = 12.dp))
                    }
                    Button(enabled = !busy, onClick = vm::synchronize) { Text("Synchronize now") }
                    OutlinedButton(enabled = !busy, onClick = { scope.launch {
                        runCatching { BackupSync.remoteBackups(context, vm.titleId) }
                            .onSuccess { remoteBackups = it }
                            .onFailure { android.widget.Toast.makeText(context, "Cannot list provider backups", android.widget.Toast.LENGTH_LONG).show() }
                    } }) { Text("Restore from provider") }
                }
            }
        }
    }
    remoteBackups?.let { backups ->
        AlertDialog(onDismissRequest = { remoteBackups = null }, title = { Text("Provider backups") }, text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (backups.isEmpty()) item { Text("No verified backup filenames found for this title") }
                items(backups, key = { it.uri.toString() }) { file ->
                    TextButton(onClick = { remoteBackups = null; vm.import(file.uri) }) {
                        Text("${file.name?.take(48)}… · ${file.length() / 1024} KB")
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { remoteBackups = null }) { Text("Close") } })
    }
    when (val op = operation) {
        is SaveManagerViewModel.Operation.Busy -> AlertDialog(onDismissRequest = {}, text = {
            Column { Text(op.message); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }, confirmButton = {})
        is SaveManagerViewModel.Operation.Result -> AlertDialog(onDismissRequest = vm::dismiss,
            title = { Text(if (op.success) "Done" else "Save operation failed") }, text = { Text(op.message) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text("OK") } })
        is SaveManagerViewModel.Operation.Review -> AlertDialog(onDismissRequest = vm::dismiss,
            title = { Text("Restore ${op.backup.manifest.titleId}?") }, text = {
                Column {
                    Text("${op.backup.manifest.files.size} files · original XUIDs: ${op.backup.manifest.xuids.joinToString()}")
                    Text("${op.backup.conflicts.size} existing save/profile directories. Current saves will be backed up before replacement.")
                    Row {
                        Checkbox(overwriteProfiles, onCheckedChange = { overwriteProfiles = it })
                        Text("Replace existing profile data too", Modifier.padding(top = 12.dp))
                    }
                }
            }, confirmButton = { TextButton(onClick = { vm.restore(overwriteProfiles) }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Cancel") } })
        else -> Unit
    }
}
