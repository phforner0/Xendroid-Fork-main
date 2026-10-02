package xendroid.compose.ui.content

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
                        if (gameName.isNotBlank()) "Content · $gameName" else "Content",
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
                    IconButton(onClick = { picking = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Install content")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val trashCount = (listState as? ListState.Loaded)?.trashed?.size ?: 0
            val tabs = listOf("DLC", "Updates", if (trashCount > 0) "Trash ($trashCount)" else "Trash")
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
                                if (selectedTab == 0) "No DLC installed."
                                else "No title updates installed.",
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
            title = { Text("Remove content?") },
            text = {
                Text("Move “${s.item.displayName}” to the trash? The game stops seeing it; you can restore " +
                    "it from the Trash tab, or delete it for good there.")
            },
            confirmButton = { TextButton(onClick = { vm.delete(s.item) }) { Text("Move to trash") } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Cancel") } },
        )
        is DeleteState.TrashFull -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("The trash is full") },
            text = {
                Text("The trash holds ${humanReadableSize(s.used)} of ${humanReadableSize(s.quota)}, so " +
                    "“${s.item.displayName}” does not fit. Empty the trash (every game's) first, or delete this " +
                    "one for good now: that cannot be undone.")
            },
            confirmButton = { TextButton(onClick = { vm.deleteForGood(s.item) }) { Text("Delete for good") } },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::requestEmptyTrash) { Text("Empty trash") }
                    TextButton(onClick = vm::dismiss) { Text("Cancel") }
                }
            },
        )
        is DeleteState.ConfirmPurge -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Delete for good?") },
            text = { Text("“${s.entry.displayName}” (${humanReadableSize(s.entry.bytes)}) will be deleted. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.purge(s.entry) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Cancel") } },
        )
        DeleteState.ConfirmEmptyTrash -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Empty the trash?") },
            text = { Text("Every package in the trash, of every game, will be deleted. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.purge(null) }) { Text("Empty trash") } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Cancel") } },
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
                headlineContent = { Text("Trash: ${humanReadableSize(state.trashUsed)} of ${humanReadableSize(state.trashQuota)}") },
                supportingContent = {
                    Text("Removed DLC and title updates wait here until you restore them or delete them for good. " +
                        "Nothing is deleted on its own.")
                },
                trailingContent = if (state.trashUsed > 0) {
                    { TextButton(onClick = onEmpty) { Text("Empty") } }
                } else null,
            )
            HorizontalDivider()
        }
        if (state.trashed.isEmpty()) {
            item { Text("Nothing of this game is in the trash.", Modifier.padding(24.dp)) }
        }
        items(state.trashed, key = { it.id }) { entry ->
            ListItem(
                headlineContent = { Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text((if (entry.contentType == xendroid.compose.core.ContentPaths.TU_CONTENT_TYPE) "Title update" else "DLC") +
                        " · ${humanReadableSize(entry.bytes)} · removed " +
                        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(entry.deletedAt)))
                },
                trailingContent = {
                    Row {
                        TextButton(onClick = { onRestore(entry) }) { Text("Restore") }
                        TextButton(onClick = { onPurge(entry) }) { Text("Delete") }
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
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
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
