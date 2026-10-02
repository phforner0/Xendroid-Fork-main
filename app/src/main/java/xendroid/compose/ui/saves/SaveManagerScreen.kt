package xendroid.compose.ui.saves

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
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
            .onFailure { android.widget.Toast.makeText(context, context.getString(R.string.sv_folder_permission), android.widget.Toast.LENGTH_LONG).show() }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri, selectedIds, includeProfiles)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.import(uri) }
    val busy = operation is SaveManagerViewModel.Operation.Busy
    BackHandler(enabled = busy) { }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.sv_title, gameName.ifEmpty { vm.titleId })) }, navigationIcon = {
            IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(stringResource(R.string.sv_intro))
                Row {
                    Checkbox(checked = includeProfiles, onCheckedChange = { includeProfiles = it }, enabled = !busy)
                    Text(stringResource(R.string.sv_include_profile), Modifier.padding(top = 12.dp))
                }
            }
            items(profiles, key = { it.xuid }) { profile ->
                ListItem(headlineContent = { Text(profile.xuid) }, supportingContent = {
                    Text(stringResource(R.string.sv_profile_line, profile.saveFiles, profile.bytes / 1024) + if (profile.hasProfile) " · " + stringResource(R.string.sv_profile_available) else "")
                }, leadingContent = {
                    Checkbox(profile.xuid in selectedIds, onCheckedChange = { selected ->
                        selectedIds = if (selected) selectedIds + profile.xuid else selectedIds - profile.xuid
                    }, enabled = !busy)
                })
            }
            if (profiles.isEmpty()) item { Text(stringResource(R.string.sv_none)) }
            item {
                Button(enabled = selectedIds.isNotEmpty() && !busy, onClick = { create.launch("xendroid-saves-${vm.titleId}.zip") }) { Text(stringResource(R.string.sv_export)) }
                OutlinedButton(enabled = !busy, onClick = { pick.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text(stringResource(R.string.sv_import)) }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text(stringResource(R.string.sv_sync_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.sv_sync_note))
                OutlinedButton(enabled = !busy, onClick = { pickFolder.launch(null) }) { Text(stringResource(R.string.sv_sync_folder)) }
                if (syncConfigured) {
                    Row {
                        Checkbox(syncAutomatic, onCheckedChange = {
                            syncAutomatic = it; BackupSync.setAutomatic(context, vm.titleId, it)
                        }, enabled = !busy)
                        Text(stringResource(R.string.sv_sync_auto), Modifier.padding(top = 12.dp))
                    }
                    Button(enabled = !busy, onClick = vm::synchronize) { Text(stringResource(R.string.sv_sync_now)) }
                    OutlinedButton(enabled = !busy, onClick = { scope.launch {
                        runCatching { BackupSync.remoteBackups(context, vm.titleId) }
                            .onSuccess { remoteBackups = it }
                            .onFailure { android.widget.Toast.makeText(context, context.getString(R.string.sv_provider_list_failed), android.widget.Toast.LENGTH_LONG).show() }
                    } }) { Text(stringResource(R.string.sv_provider_restore)) }
                }
            }
        }
    }
    remoteBackups?.let { backups ->
        AlertDialog(onDismissRequest = { remoteBackups = null }, title = { Text(stringResource(R.string.sv_provider_backups)) }, text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (backups.isEmpty()) item { Text(stringResource(R.string.sv_provider_none)) }
                items(backups, key = { it.uri.toString() }) { file ->
                    TextButton(onClick = { remoteBackups = null; vm.import(file.uri) }) {
                        Text("${file.name?.take(48)}… · ${file.length() / 1024} KB")
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { remoteBackups = null }) { Text(stringResource(R.string.common_close)) } })
    }
    when (val op = operation) {
        is SaveManagerViewModel.Operation.Busy -> AlertDialog(onDismissRequest = {}, text = {
            Column { Text(op.message); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }, confirmButton = {})
        is SaveManagerViewModel.Operation.Result -> AlertDialog(onDismissRequest = vm::dismiss,
            title = { Text(if (op.success) stringResource(R.string.common_done) else stringResource(R.string.sv_failed)) }, text = { Text(op.message) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.common_ok)) } })
        is SaveManagerViewModel.Operation.Review -> AlertDialog(onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.sv_restore_title, op.backup.manifest.titleId)) }, text = {
                Column {
                    Text(stringResource(R.string.sv_restore_files, op.backup.manifest.files.size, op.backup.manifest.xuids.joinToString()))
                    Text(stringResource(R.string.sv_restore_conflicts, op.backup.conflicts.size))
                    Row {
                        Checkbox(overwriteProfiles, onCheckedChange = { overwriteProfiles = it })
                        Text(stringResource(R.string.sv_replace_profiles), Modifier.padding(top = 12.dp))
                    }
                }
            }, confirmButton = { TextButton(onClick = { vm.restore(overwriteProfiles) }) { Text(stringResource(R.string.prof_restore)) } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.common_cancel)) } })
        else -> Unit
    }
}
