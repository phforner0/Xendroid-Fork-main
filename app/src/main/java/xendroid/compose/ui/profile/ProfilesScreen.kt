package xendroid.compose.ui.profile

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.edit
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import xendroid.compose.core.Gamertag
import xendroid.compose.core.ProfilePaths
import xendroid.compose.saves.ProfileContentSummary
import xendroid.compose.saves.TrashedProfile
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.ui.profile.ProfileManagerViewModel.ListState
import xendroid.compose.ui.profile.ProfileManagerViewModel.OpState
import xendroid.compose.ui.profile.ProfileManagerViewModel.ProfileEntry

// Top-level vals, so a hard cast on a key whose toml section moved would throw
// during class init, before anything can catch it.
private fun listChoice(key: String) =
    SettingsSchema.byKey[key] as? Setting.ListChoice

private val LANGUAGE_OPTIONS = listChoice("Console|user_language")?.options.orEmpty()
private val COUNTRY_OPTIONS = listChoice("Console|user_country")?.options.orEmpty()
private val DEFAULT_LANGUAGE =
    listChoice("Console|user_language")?.default?.toIntOrNull() ?: 1     // en
private val DEFAULT_COUNTRY =
    listChoice("Console|user_country")?.default?.toIntOrNull() ?: 103    // United States

private sealed interface Editing {
    data object None : Editing
    data object Create : Editing
    data class Rename(val entry: ProfileEntry) : Editing
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    vm: ProfileManagerViewModel,
    onBack: () -> Unit,
) {
    val listState by vm.listState.collectAsStateWithLifecycle()
    val opState by vm.opState.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing>(Editing.None) }
    var purgeTarget by remember { mutableStateOf<TrashedProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lib_menu_profiles)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { editing = Editing.Create }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pf_create))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (val s = listState) {
                ListState.Loading -> CircularProgressIndicator()
                is ListState.Error -> Text(s.message, Modifier.padding(24.dp))
                is ListState.Loaded ->
                    if (s.profiles.isEmpty() && trash.isEmpty()) {
                        Text(stringResource(R.string.pf_none),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(24.dp))
                    } else {
                        ProfileList(
                            profiles = s.profiles,
                            slots = s.slots,
                            onPlayer = vm::setPlayer,
                            trash = trash,
                            onSelect = vm::setActive,
                            onRename = { editing = Editing.Rename(it) },
                            onDelete = vm::requestDelete,
                            onRestore = { vm.restore(it.id) },
                            onPurge = { purgeTarget = it },
                        )
                    }
            }
        }
    }

    when (val e = editing) {
        Editing.None -> {}
        Editing.Create -> ProfileForm(
            initial = null,
            onDismiss = { editing = Editing.None },
            onSubmit = { tag, lang, country, avatar ->
                editing = Editing.None
                vm.create(tag, lang, country, avatar)
            },
        )
        is Editing.Rename -> ProfileForm(
            initial = e.entry,
            onDismiss = { editing = Editing.None },
            onSubmit = { tag, lang, country, avatar ->
                editing = Editing.None
                vm.rename(e.entry.xuid, tag, lang, country, avatar)
            },
        )
    }

    purgeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            title = { Text(stringResource(R.string.pf_purge_title)) },
            text = { Text(stringResource(R.string.pf_purge_text, target.xuid)) },
            confirmButton = {
                TextButton(onClick = { purgeTarget = null; vm.purge(target.id) }) { Text(stringResource(R.string.pf_purge)) }
            },
            dismissButton = { TextButton(onClick = { purgeTarget = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    when (val s = opState) {
        is OpState.ConfirmDelete -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.pf_trash_title)) },
            text = { Text(deleteSummaryText(s.entry, s.summary)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(s.entry.xuid) }) { Text(stringResource(R.string.pf_trash)) }
            },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.common_cancel)) } },
        )
        is OpState.Busy -> AlertDialog(
            onDismissRequest = {},
            title = { Text(s.message) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
        is OpState.Done -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.common_done)) },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.common_ok)) } },
        )
        is OpState.Failed -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text(stringResource(R.string.pf_failed)) },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.common_ok)) } },
        )
        OpState.Idle -> {}
    }
}

@Composable
private fun deleteSummaryText(entry: ProfileEntry, summary: ProfileContentSummary): String {
    val name = entry.gamertag.ifBlank { entry.xuid }
    val games = summary.gameTitles
    val megabytes = "%.1f".format(summary.bytes / (1024.0 * 1024.0))
    val detail = if (games.isEmpty()) stringResource(R.string.pf_del_no_saves)
    else stringResource(R.string.pf_del_saves, games.size, games.joinToString(", ") { it.titleId }, summary.files, megabytes)
    return listOfNotNull(stringResource(R.string.pf_del_name, name), detail,
        stringResource(R.string.pf_del_partial).takeIf { summary.truncated }, stringResource(R.string.pf_del_trash)).joinToString(" ")
}

@Composable
private fun ProfileList(
    profiles: List<ProfileEntry>,
    slots: List<String?>,
    onPlayer: (slot: Int, xuid: String?) -> Unit,
    trash: List<TrashedProfile>,
    onSelect: (String) -> Unit,
    onRename: (ProfileEntry) -> Unit,
    onDelete: (ProfileEntry) -> Unit,
    onRestore: (TrashedProfile) -> Unit,
    onPurge: (TrashedProfile) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (profiles.size > 1) item(key = "ask") { AskBeforePlayingRow() }
        items(profiles, key = { it.xuid }) { p ->
            ListItem(
                leadingContent = { ProfileAvatar(p) },
                headlineContent = {
                    Text(p.gamertag.ifBlank { p.xuid },
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = (if (p.isActive) stringResource(R.string.pf_active_p1) else
                    xendroid.compose.data.ProfileSlots.otherPlayerOf(slots, p.xuid)?.let { stringResource(R.string.pf_signs_in_as_p, it) })
                    ?.let { label -> { Text(label) } },
                trailingContent = { RowMenu(p, onRename, onDelete) },
                modifier = Modifier.clickable { onSelect(p.xuid) },
            )
            HorizontalDivider()
        }
        if (profiles.size > 1) item(key = "players") { OtherPlayersSection(profiles, slots, onPlayer) }
        if (trash.isNotEmpty()) {
            item(key = "trash-header") {
                Text(stringResource(R.string.pf_trash_header),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
            }
            items(trash, key = { "trash-${it.id}" }) { t ->
                ListItem(
                    headlineContent = { Text(t.xuid) },
                    supportingContent = {
                        Text(stringResource(R.string.pf_removed_on, java.text.DateFormat.getDateTimeInstance(
                            java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(t.deletedAt))))
                    },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { onRestore(t) }) { Text(stringResource(R.string.prof_restore)) }
                            TextButton(onClick = { onPurge(t) }) { Text(stringResource(R.string.common_remove)) }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProfileAvatar(p: ProfileEntry) {
    Box(contentAlignment = Alignment.Center) {
        if (p.hasAvatar) {
            val ctx = LocalContext.current
            val model = remember(p.xuid) { ProfilePaths.tile64Path(p.xuid) }
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(model).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
        } else {
            Icon(Icons.Default.Person, contentDescription = null, Modifier.size(40.dp))
        }
        if (p.isActive) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.pf_active),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp).align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun RowMenu(
    p: ProfileEntry,
    onRename: (ProfileEntry) -> Unit,
    onDelete: (ProfileEntry) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.lib_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.pf_edit)) },
            onClick = { open = false; onRename(p) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_delete)) },
            onClick = { open = false; onDelete(p) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileForm(
    initial: ProfileEntry?,
    onDismiss: () -> Unit,
    onSubmit: (gamertag: String, language: Int, country: Int, avatar: Uri?) -> Unit,
) {
    var gamertag by rememberSaveable { mutableStateOf(initial?.gamertag ?: "") }
    var language by rememberSaveable { mutableIntStateOf(initial?.language ?: DEFAULT_LANGUAGE) }
    var country by rememberSaveable { mutableIntStateOf(initial?.country ?: DEFAULT_COUNTRY) }
    var avatar by remember { mutableStateOf<Uri?>(null) }

    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) avatar = uri }

    val valid = Gamertag.isValid(gamertag)
    val showError = gamertag.isNotEmpty() && !valid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.pf_create) else stringResource(R.string.pf_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    val current = avatar ?: initial?.takeIf { it.hasAvatar }
                        ?.let { Uri.fromFile(ProfilePaths.tile64Path(it.xuid)) }
                    val ctx = LocalContext.current
                    if (current != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(current).build(),
                            contentDescription = stringResource(R.string.pf_avatar),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(CircleShape).clickable {
                                pickAvatar.launch(PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                    } else {
                        Box(
                            Modifier.size(72.dp).clip(CircleShape)
                                .clickable {
                                    pickAvatar.launch(PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Person, contentDescription = stringResource(R.string.pf_pick_avatar),
                                Modifier.size(48.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = gamertag,
                    onValueChange = { if (it.length <= 15) gamertag = it },
                    label = { Text(stringResource(R.string.pf_gamertag)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) ({ Text(stringResource(R.string.pf_gamertag_rule)) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChoiceField(stringResource(R.string.pf_language), LANGUAGE_OPTIONS, language) { language = it }
                ChoiceField(stringResource(R.string.pf_region), COUNTRY_OPTIONS, country) { country = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSubmit(gamertag, language, country, avatar) },
            ) { Text(if (initial == null) stringResource(R.string.col_create) else stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun ChoiceField(
    label: String,
    options: List<xendroid.compose.settings.ListOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.value.toIntOrNull() == selected }?.label
        ?: selected.toString()
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(currentLabel, Modifier.weight(1f))
        }
    }
    if (open) {
        val listState = rememberLazyListState()
        val selectedIndex = options.indexOfFirst { it.value.toIntOrNull() == selected }
        LaunchedEffect(Unit) { if (selectedIndex > 0) listState.scrollToItem(selectedIndex) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(options, key = { it.value }) { opt ->
                        val value = opt.value.toIntOrNull()
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = value == selected,
                                onClick = { value?.let(onSelect); open = false },
                            ).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selected,
                                onClick = { value?.let(onSelect); open = false })
                            Spacer(Modifier.width(8.dp))
                            Text(opt.label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** U11: the profiles players 2–4 sign in with when a game starts (P1 is the active one). */
@Composable
private fun OtherPlayersSection(profiles: List<ProfileEntry>, slots: List<String?>, onPlayer: (Int, String?) -> Unit) {
    var picking by remember { mutableStateOf<Int?>(null) }
    Column {
        Text(stringResource(R.string.pf_other_players), style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
        for (slot in 1 until xendroid.compose.data.ProfileSlots.COUNT) {
            val xuid = slots.getOrNull(slot)
            ListItem(
                headlineContent = { Text(stringResource(R.string.pf_player_n, slot + 1)) },
                supportingContent = {
                    Text(profiles.firstOrNull { it.xuid.equals(xuid, ignoreCase = true) }?.gamertag?.ifBlank { null } ?: xuid
                        ?: stringResource(R.string.pf_nobody_signs_in))
                },
                modifier = Modifier.clickable { picking = slot },
            )
        }
        Text(stringResource(R.string.pf_players_note),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
    picking?.let { slot ->
        val p1 = slots.getOrNull(0)
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(stringResource(R.string.pf_player_signs_in_as, slot + 1)) },
            text = {
                Column {
                    ListItem(headlineContent = { Text(stringResource(R.string.pf_nobody)) },
                        modifier = Modifier.clickable { picking = null; onPlayer(slot, null) })
                    profiles.filterNot { it.xuid.equals(p1, ignoreCase = true) }.forEach { p ->
                        val other = xendroid.compose.data.ProfileSlots.otherPlayerOf(slots, p.xuid)?.takeIf { it != slot + 1 }
                        ListItem(
                            headlineContent = { Text(p.gamertag.ifBlank { p.xuid }) },
                            supportingContent = other?.let { { Text(stringResource(R.string.pf_moves_from, it)) } },
                            modifier = Modifier.clickable { picking = null; onPlayer(slot, p.xuid) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** U11: ask which profile plays before each game (the library's "Play as"), or always the active one. */
@Composable
private fun AskBeforePlayingRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(xendroid.compose.data.ProfilePick.PREFS, android.content.Context.MODE_PRIVATE) }
    var ask by remember { mutableStateOf(prefs.getBoolean(xendroid.compose.data.ProfilePick.ASK, true)) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.pf_ask)) },
        supportingContent = { Text(if (ask) stringResource(R.string.pf_ask_on) else stringResource(R.string.pf_ask_off)) },
        trailingContent = {
            androidx.compose.material3.Switch(checked = ask, onCheckedChange = {
                ask = it
                prefs.edit { putBoolean(xendroid.compose.data.ProfilePick.ASK, it) }
            })
        },
    )
}
