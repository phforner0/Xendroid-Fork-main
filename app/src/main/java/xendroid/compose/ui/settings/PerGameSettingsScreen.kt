package xendroid.compose.ui.settings

import androidx.compose.ui.res.pluralStringResource
import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import xendroid.compose.settings.GameSettingsViewModel
import xendroid.compose.settings.SettingsCategory
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.settings.UiModeStore
import androidx.compose.ui.platform.LocalContext

/**
 * The per-game override editor: the same two-level INDEX -> DETAIL shape as
 * [SettingsScreen], but each detail row is an [OverrideRow] (a leading switch that
 * overrides/inherits the key), the index "changed" count is the overridden count, and
 * the header shows the game name. The override config is SPARSE — only toggled-on keys
 * are patched into the file on flush, leaving non-schema overrides intact.
 * A title id is keyed PER GAME (not per file), so this applies to every copy of the game.
 */
@Composable
fun PerGameSettingsScreen(
    vm: GameSettingsViewModel,
    gameName: String,
    onBack: () -> Unit,
) {
    val overrides by vm.overrides.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val profiles by vm.profilesState.collectAsStateWithLifecycle()
    val preview by vm.profilePreview.collectAsStateWithLifecycle()
    val profileMessage by vm.profileMessage.collectAsStateWithLifecycle()
    val community by vm.communityState.collectAsStateWithLifecycle()
    val shareStart by vm.shareStart.collectAsStateWithLifecycle()

    // Durable flush on pause; re-open on resume. Dispose flush = backstop. (Mirrors SettingsScreen.)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> vm.flush()
                Lifecycle.Event.ON_RESUME -> vm.onResume()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); vm.flush() }
    }

    var selected by remember { mutableStateOf<SettingsCategory?>(null) }
    if (!ready) {
        ConfigLoadNotice(error?.let { stringResource(R.string.set_game_config_failed) }, vm::onResume, onBack)
        return
    }
    if (error != null) {
        AlertDialog(
            onDismissRequest = vm::clearError,
            text = { Text(stringResource(R.string.set_game_config_failed)) },
            confirmButton = { TextButton(onClick = { vm.clearError(); vm.flush() }) { Text(stringResource(R.string.set_retry_save)) } },
            dismissButton = { TextButton(onClick = vm::clearError) { Text(stringResource(R.string.common_close)) } },
        )
    }
    preview?.let { ProfilePreviewDialog(it, onConfirm = vm::confirmPreview, onDismiss = vm::dismissPreview) }
    val communityNow = community
    if (shareStart != null && communityNow != null) {
        CommunityShareDialog(communityNow.server, draftOf = vm::draftShare, onShare = vm::share, onDismiss = vm::dismissShare)
    }
    profileMessage?.let { message ->
        AlertDialog(
            onDismissRequest = vm::clearProfileMessage,
            text = { Text(profileMessageText(message)) },
            confirmButton = { TextButton(onClick = vm::clearProfileMessage) { Text(stringResource(R.string.common_ok)) } },
        )
    }
    val section = selected
    if (section == null) {
        PerGameIndex(
            gameName = gameName,
            categories = SettingsSchema.categoriesFor(UiModeStore.read(LocalContext.current)),
            overriddenCountOf = { cat -> cat.settings.count { overrides.containsKey(it.key) } },
            onOpen = { selected = it },
            onBack = { vm.flush(); onBack() },
            header = if (profiles.isEmpty && communityNow == null) null else {
                {
                    Column {
                        if (!profiles.isEmpty) {
                            RecommendedProfilesCard(profiles, vm::previewProfile, vm::previewRestore,
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        // 15b: shown in builds that name a community server; it asks nothing until searched.
                        communityNow?.let { state ->
                            CommunityConfigsCard(state, canApply = profiles.applied == null, onSearch = vm::searchCommunity,
                                onPreview = vm::previewProfile, onVote = vm::voteCommunity, onDelete = vm::deleteShared,
                                onShare = vm::prepareShare, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
            },
        )
    } else {
        BackHandler { selected = null }
        PerGameCategoryDetail(
            category = section,
            overrides = overrides,
            vm = vm,
            onBack = { selected = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerGameIndex(
    gameName: String,
    categories: List<SettingsCategory>,
    overriddenCountOf: (SettingsCategory) -> Int,
    onOpen: (SettingsCategory) -> Unit,
    onBack: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (gameName.isNotEmpty()) stringResource(R.string.set_game_title, gameName) else stringResource(R.string.lib_per_game_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            // Header note: title id is keyed per game, so overrides apply to every copy.
            item {
                Text(
                    stringResource(R.string.set_game_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
            }
            header?.let { item(key = "recommended") { it() } }
            items(categories, key = { it.title }) { cat ->
                val overridden = overriddenCountOf(cat)
                ListItem(
                    headlineContent = { Text(categoryTitle(cat)) },
                    supportingContent = {
                        Text(buildString {
                            append(pluralStringResource(R.plurals.set_count, cat.settings.size, cat.settings.size))
                            if (overridden > 0) append("  ·  " + pluralStringResource(R.plurals.set_overridden, overridden, overridden))
                        })
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.browse_open))
                    },
                    modifier = Modifier.clickable { onOpen(cat) },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerGameCategoryDetail(
    category: SettingsCategory,
    overrides: Map<String, String>,
    vm: GameSettingsViewModel,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryTitle(category)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.set_back_sections))
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(category.settings, key = { it.key }) { setting ->
                OverrideRow(
                    host = vm,
                    s = setting,
                    overrideValue = overrides[setting.key],
                    onOverrideToggle = { vm.setOverride(setting, it) },
                )
                HorizontalDivider()
            }
        }
    }
}

/** U02: what a recommended-settings action did, in the shown language. */
@Composable
private fun profileMessageText(message: GameSettingsViewModel.ProfileMessage): String = when (message) {
    GameSettingsViewModel.ProfileMessage.RestoreFirst -> stringResource(R.string.prof_msg_restore_first)
    is GameSettingsViewModel.ProfileMessage.Applied ->
        pluralStringResource(R.plurals.prof_msg_applied, message.count, message.name, message.count)
    is GameSettingsViewModel.ProfileMessage.Restored -> stringResource(
        if (message.changedSince) R.string.prof_msg_restored_kept else R.string.prof_msg_restored, message.name)
    GameSettingsViewModel.ProfileMessage.Stale -> stringResource(R.string.prof_msg_stale)
    is GameSettingsViewModel.ProfileMessage.Shared -> stringResource(R.string.comm_msg_shared, message.name)
    GameSettingsViewModel.ProfileMessage.Deleted -> stringResource(R.string.comm_msg_deleted)
}
