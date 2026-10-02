package xendroid.compose.ui.content

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xendroid.compose.ui.content.ContentManagerViewModel.DeleteState
import xendroid.compose.ui.content.ContentManagerViewModel.ListState
import xendroid.compose.ui.library.FolderBrowserScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentManagerScreen(
    vm: ContentManagerViewModel,
    gameName: String,
    onBack: () -> Unit,
) {
    val listState by vm.listState.collectAsStateWithLifecycle()
    val installState by vm.state.collectAsStateWithLifecycle()
    val deleteState by vm.deleteState.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    if (picking) {
        FolderBrowserScreen(
            onFileChosen = { path ->
                picking = false
                vm.install(path)
            },
            onCancel = { picking = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (gameName.isNotBlank()) stringResource(R.string.cm_title_game, gameName) else stringResource(R.string.cm_title),
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
                    IconButton(onClick = { picking = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.lib_menu_install_content))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val trashCount = (listState as? ListState.Loaded)?.trashed?.size ?: 0
            val tabs = listOf("DLC", stringResource(R.string.cm_tab_updates), if (trashCount > 0) stringResource(R.string.cm_tab_trash_n, trashCount) else stringResource(R.string.cm_tab_trash))
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) },
                    )
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (val s = listState) {
                    ListState.Loading -> CircularProgressIndicator()
                    is ListState.Error -> Text(s.message, modifier = Modifier.padding(24.dp))
                    is ListState.Loaded -> if (selectedTab == 2) {
                        TrashList(s, onRestore = vm::restore, onPurge = vm::requestPurge, onEmpty = vm::requestEmptyTrash)
                    } else {
                        val items = if (selectedTab == 0) s.dlc else s.updates
                        if (items.isEmpty()) {
                            Text(
                                if (selectedTab == 0) stringResource(R.string.cm_no_dlc)
                                else stringResource(R.string.cm_no_tu),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(24.dp),
                            )
                        } else {
                            ContentList(items = items, onDelete = vm::requestDelete)
                        }
                    }
                }
            }
        }
    }

    ContentInstallDialogs(
        state = installState,
        onDismiss = vm::dismiss,
        onConfirmOverwrite = vm::confirmOverwrite,
    )

    when (val s = deleteState) {
        is DeleteState.Confirm -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.cm_remove_title)) },
            text = {
                Text(stringResource(R.string.cm_remove_text, s.item.displayName))
            },
            confirmButton = { TextButton(onClick = { vm.delete(s.item) }) { Text(stringResource(R.string.pf_trash)) } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.common_cancel)) } },
        )
        is DeleteState.TrashFull -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.cm_trash_full)) },
            text = {
                Text(stringResource(R.string.cm_trash_full_text, humanReadableSize(s.used), humanReadableSize(s.quota), s.item.displayName))
            },
            confirmButton = { TextButton(onClick = { vm.deleteForGood(s.item) }) { Text(stringResource(R.string.cm_delete_for_good)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::requestEmptyTrash) { Text(stringResource(R.string.cm_empty_trash)) }
                    TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.common_cancel)) }
                }
            },
        )
        is DeleteState.ConfirmPurge -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.cm_delete_for_good_title)) },
            text = { Text(stringResource(R.string.cm_delete_for_good_text, s.entry.displayName, humanReadableSize(s.entry.bytes))) },
            confirmButton = { TextButton(onClick = { vm.purge(s.entry) }) { Text(stringResource(R.string.common_delete)) } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.common_cancel)) } },
        )
        DeleteState.ConfirmEmptyTrash -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.cm_empty_title)) },
            text = { Text(stringResource(R.string.cm_empty_text)) },
            confirmButton = { TextButton(onClick = { vm.purge(null) }) { Text(stringResource(R.string.cm_empty_trash)) } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.common_cancel)) } },
        )
        DeleteState.Idle -> Unit
    }
}

/** L12: this game's packages in the trash, and how full the trash (every game's) is. */
@Composable
private fun TrashList(
    state: ListState.Loaded,
    onRestore: (xendroid.compose.saves.TrashedContent) -> Unit,
    onPurge: (xendroid.compose.saves.TrashedContent) -> Unit,
    onEmpty: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.cm_trash_usage, humanReadableSize(state.trashUsed), humanReadableSize(state.trashQuota))) },
                supportingContent = {
                    Text(stringResource(R.string.cm_trash_note))
                },
                trailingContent = if (state.trashUsed > 0) {
                    { TextButton(onClick = onEmpty) { Text(stringResource(R.string.cm_empty)) } }
                } else null,
            )
            HorizontalDivider()
        }
        if (state.trashed.isEmpty()) {
            item { Text(stringResource(R.string.cm_trash_none), Modifier.padding(24.dp)) }
        }
        items(state.trashed, key = { it.id }) { entry ->
            ListItem(
                headlineContent = { Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(stringResource(R.string.cm_trash_entry, if (entry.contentType == xendroid.compose.core.ContentPaths.TU_CONTENT_TYPE) "Title update" else "DLC",
                        humanReadableSize(entry.bytes),
                        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(entry.deletedAt))))
                },
                trailingContent = {
                    Row {
                        TextButton(onClick = { onRestore(entry) }) { Text(stringResource(R.string.prof_restore)) }
                        TextButton(onClick = { onPurge(entry) }) { Text(stringResource(R.string.common_delete)) }
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ContentList(
    items: List<ContentManagerViewModel.ContentEntry>,
    onDelete: (ContentManagerViewModel.ContentEntry) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.pkgDir }) { item ->
            ListItem(
                headlineContent = {
                    Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = { Text(humanReadableSize(item.size)) },
                trailingContent = {
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_remove))
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

private fun humanReadableSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}
