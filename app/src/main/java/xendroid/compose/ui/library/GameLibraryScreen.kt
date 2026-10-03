package xendroid.compose.ui.library

import androidx.compose.ui.res.pluralStringResource
import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import xendroid.compose.core.AllFilesAccess
import xendroid.compose.core.EmuProcessLink
import xendroid.compose.sessions.describeAudio
import xendroid.compose.sessions.describeFrameTimes
import xendroid.compose.sessions.describeFrameGeneration
import xendroid.compose.sessions.describeRun
import xendroid.compose.sessions.formatPlayTime
import xendroid.compose.data.Game
import xendroid.compose.data.isFavorite
import xendroid.compose.data.GameFormat
import xendroid.compose.ui.compress.GameCompressViewModel
import xendroid.compose.ui.compress.GameCompressViewModel.CompressState
import xendroid.compose.ui.userdata.openUserData
import xendroid.compose.updater.CooldownDialog
import xendroid.compose.updater.getRemainingCooldown
import xendroid.compose.updater.LatestVersionDialog
import xendroid.compose.updater.UpdateDialog
import xendroid.compose.updater.UpdateResult
import xendroid.compose.updater.checkForUpdates
import xendroid.compose.updater.shouldCheckForUpdates
import xendroid.compose.updater.saveLastCheck


/** A scan shorter than this shows no progress row. */
private const val SCAN_PROGRESS_DELAY_MS = 700L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameLibraryScreen(
    viewModel: GameLibraryViewModel,
    onOpenSettings: () -> Unit,
    onOpenKeymap: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenTouchControls: () -> Unit,
    onOpenPerGameSettings: (titleId: String, gameName: String, format: GameFormat, launchUri: String) -> Unit,
    onOpenGamePatches: (titleId: String, gameName: String) -> Unit,
    onOpenContentManager: (titleId: String, gameName: String) -> Unit,
    onOpenSaves: (titleId: String, gameName: String) -> Unit,
    onOpenDiagnostics: (String?) -> Unit,
    onOpenInstallContent: () -> Unit,
    onInstallFromDisc: (String) -> Unit,
    compressVm: GameCompressViewModel,
    onOpenPhoneController: () -> Unit = {},
    onOpenControllerTest: () -> Unit = {},
    onOpenBenchmark: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }

    var pendingGame by remember { mutableStateOf<Game?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // A disc whose content is not installed yet; the launch waits on the answer.
    var pendingDiscInstall by remember { mutableStateOf<Pair<Game, Int>?>(null) }
    var compressConfirmFor by remember { mutableStateOf<Game?>(null) }
    val compressState by compressVm.state.collectAsStateWithLifecycle()
    val titleIdState by viewModel.titleIdState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val coverRevision by viewModel.coverRevision.collectAsStateWithLifecycle()
    // L05: the game whose cover is being picked; a result after a recreation has none and is dropped.
    var coverTarget by remember { mutableStateOf<Game?>(null) }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val game = coverTarget
        coverTarget = null
        if (uri != null && game != null) scope.launch {
            viewModel.setCustomCover(game, uri)
                .onSuccess { Toast.makeText(context, context.getString(R.string.lib_cover_changed), Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, context.getString(R.string.lib_cover_failed, it.message), Toast.LENGTH_LONG).show() }
        }
    }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    // L06: show one of the user's collections (by name); a deleted one shows everything.
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    var collectionFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var lastFocusedId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusRestoreTick by remember { mutableIntStateOf(0) }
    var preparingLaunch by remember { mutableStateOf(false) }
    var searchHasFocus by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    var showBrowser by remember { mutableStateOf(false) }
    var allFilesGranted by remember { mutableStateOf(AllFilesAccess.isGranted()) }
    // Not-yet-granted sends the user to Settings; the grant returns no result, so it is
    // observed on the next ON_START.
    val startRealPathMode: () -> Unit = {
        if (AllFilesAccess.isGranted()) showBrowser = true
        else AllFilesAccess.requestAccess(context)
    }

    // Re-scan on return to foreground to pick up games added while backgrounded. The
    // ViewModel's init does the first cold-start load, so the first ON_START is skipped.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var firstStart = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                allFilesGranted = AllFilesAccess.isGranted()
                if (firstStart) firstStart = false else viewModel.refresh()
            }
            if (event == Lifecycle.Event.ON_RESUME) focusRestoreTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showBrowser) {
        FolderBrowserScreen(
            onFolderChosen = { path ->
                showBrowser = false
                viewModel.onRealPathFolderPicked(path)
            },
            onCancel = { showBrowser = false },
        )
        return
    }

    // L03: the library's game folders (add, remove; files are never touched).
    var foldersOpen by remember { mutableStateOf(false) }
    if (foldersOpen) {
        val folders by viewModel.folders.collectAsStateWithLifecycle()
        GameFoldersDialog(
            folders = folders,
            unavailable = (state as? LibraryUiState.Loaded)?.unavailableRoots.orEmpty(),
            onAdd = { foldersOpen = false; startRealPathMode() },
            onRemove = viewModel::removeFolder,
            onDismiss = { foldersOpen = false },
        )
    }

    // L06: games played or seen before that this scan did not list.
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    var missingOpen by remember { mutableStateOf(false) }
    if (missingOpen) {
        MissingGamesDialog(
            missing = missing,
            activity = activity,
            coverOf = viewModel::coverOfTitle,
            onRemove = viewModel::hideMissing,
            onManageFolders = { missingOpen = false; viewModel.loadFolders(); foldersOpen = true },
            onDismiss = { missingOpen = false },
        )
    }

    // L01: once, until finished or skipped; reopened from the menu. Not over the no-Vulkan
    // gate, which already explains why games cannot run.
    var assistantOpen by rememberSaveable { mutableStateOf(!FirstRunStore.done(context)) }
    if (assistantOpen && state != LibraryUiState.NoVulkan) {
        FirstRunAssistant(
            folderReady = state is LibraryUiState.Loaded,
            onChooseFolder = startRealPathMode,
            onOpenProfiles = onOpenProfiles,
            onClose = {
                FirstRunStore.markDone(context)
                assistantOpen = false
            },
        )
    }

    // U11: "Play as" when several profiles exist; the launch waits on the answer.
    var playAs by remember { mutableStateOf<Pair<Game, xendroid.compose.data.ProfilePick.Decision.Ask>?>(null) }
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val prepareAndLaunch: (Game) -> Unit = prepare@{ game ->
        if (preparingLaunch) return@prepare
        preparingLaunch = true
        scope.launch {
            try {
                val pending = viewModel.uninstalledDiscContent(game)
                if (pending.isNotEmpty()) pendingDiscInstall = game to pending.size
                else launchGame(context, viewModel, game)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("GameLibrary", "Preparing launch failed", e)
                Toast.makeText(context, context.getString(R.string.lib_launch_prepare_failed), Toast.LENGTH_LONG).show()
            } finally { preparingLaunch = false }
        }
    }
    playAs?.let { (game, ask) ->
        PlayAsDialog(
            profiles = ask.profiles,
            preselected = ask.preselected,
            otherPlayers = ask.otherPlayers,
            onPlay = { xuid, dontAsk ->
                playAs = null
                scope.launch {
                    runCatching { viewModel.playAs(xuid, dontAsk) }
                        .onSuccess { prepareAndLaunch(game) }
                        .onFailure { Toast.makeText(context, context.getString(R.string.lib_profile_signin_failed, it.message), Toast.LENGTH_LONG).show() }
                }
            },
            onDismiss = { playAs = null },
        )
    }

    val startGame: (Game) -> Unit = start@{ game ->
        if (preparingLaunch || playAs != null) return@start
        // The list may be last time's (L09) or older than a file manager's change.
        if (!java.io.File(game.launchUri).exists()) {
            Toast.makeText(context, context.getString(R.string.lib_game_moved, game.name), Toast.LENGTH_LONG).show()
            viewModel.refresh()
            return@start
        }
        lastFocusedId = game.stableId
        scope.launch {
            val decision = runCatching { viewModel.profileDecision() }
                .onFailure { Log.w("GameLibrary", "Reading profiles failed", it) }
                .getOrNull()
            when (decision) {
                is xendroid.compose.data.ProfilePick.Decision.Ask -> playAs = game to decision
                is xendroid.compose.data.ProfilePick.Decision.Launch ->
                    if (decision.changes && decision.xuid != null) {
                        runCatching { viewModel.playAs(decision.xuid, dontAskAgain = false) }
                        prepareAndLaunch(game)
                    } else prepareAndLaunch(game)
                null -> prepareAndLaunch(game)   // profiles unreadable: boot as configured
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lib_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.lib_settings))
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.lib_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // Only offered where All Files Access exists (API 30+); on API 29 the
                        // empty state explains why.
                        if (AllFilesAccess.isSupported) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lib_menu_add_folder)) },
                                onClick = { menuOpen = false; startRealPathMode() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lib_menu_folders)) },
                                onClick = { menuOpen = false; viewModel.loadFolders(); foldersOpen = true },
                            )
                        }
                        if (missing.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lib_menu_missing, missing.size)) },
                                onClick = { menuOpen = false; missingOpen = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lib_menu_install_content)) },
                            onClick = { menuOpen = false; onOpenInstallContent() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lib_menu_profiles)) },
                            onClick = { menuOpen = false; onOpenProfiles() },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.lib_menu_diagnostics)) }, onClick = {
                            menuOpen = false; onOpenDiagnostics(null)
                        })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lib_menu_keymap)) },
                            onClick = { menuOpen = false; onOpenKeymap() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lib_menu_touch)) },
                            onClick = { menuOpen = false; onOpenTouchControls() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lib_menu_phone_controller)) },
                            onClick = { menuOpen = false; onOpenPhoneController() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lib_menu_test_controllers)) },
                            onClick = { menuOpen = false; onOpenControllerTest() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lib_menu_compare_runs)) },
                            onClick = { menuOpen = false; onOpenBenchmark() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lib_menu_setup)) },
                            onClick = { menuOpen = false; assistantOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lib_menu_user_data)) },
                            onClick = {
                                menuOpen = false
                                openUserData(context)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lib_menu_about)) },
                            onClick = { menuOpen = false; onOpenAbout() },
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lib_menu_updates)) },
                            onClick = {
                                menuOpen = false

                                checkForUpdatesClicked(
                                    context = context,
                                    scope = scope,
                                    onResult = { updateResult = it }
                                )
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val setFolderLabel = if (allFilesGranted) stringResource(R.string.lib_set_folder) else stringResource(R.string.lib_grant_access)
            when (val s = state) {
                LibraryUiState.NoVulkan ->
                    NoVulkanDialog(onQuit = { (context as? Activity)?.finish() })
                LibraryUiState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    ScanProgressRow(viewModel)
                }
                // All Files Access is API 30+; on API 29 there is no games path at all.
                LibraryUiState.NoFolder ->
                    if (AllFilesAccess.isSupported)
                        EmptyMessage(stringResource(R.string.lib_no_folder), setFolderLabel,
                            onAction = startRealPathMode)
                    else
                        EmptyMessage(
                            stringResource(R.string.lib_needs_android11),
                            stringResource(R.string.common_ok), onAction = {})
                LibraryUiState.PermissionLost ->
                    EmptyMessage(stringResource(R.string.lib_access_lost), setFolderLabel,
                        onAction = startRealPathMode)
                is LibraryUiState.Error ->
                    EmptyMessage(s.message, stringResource(R.string.common_retry), onAction = { viewModel.refresh() })
                is LibraryUiState.Loaded ->
                    if (s.games.isEmpty())
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // A folder away or games gone explain an empty list too (only folder on an SD card that is out).
                            LibraryNotices(s, missing, onFolders = { viewModel.loadFolders(); foldersOpen = true },
                                onMissing = { missingOpen = true })
                            EmptyMessage(stringResource(R.string.lib_no_games), stringResource(R.string.lib_choose_another),
                                onAction = startRealPathMode)
                        }
                    else {
                        val shownCollection = collectionFilter?.let { xendroid.compose.data.GameCollections.find(collections, it) }
                        val visibleGames = remember(s.games, searchQuery, favoritesOnly, favorites, sort, activity, shownCollection) {
                            val query = searchQuery.trim()
                            val members = shownCollection?.members?.toHashSet()
                            val filtered = s.games.filter { game ->
                                (!favoritesOnly || isFavorite(game, favorites)) &&
                                    (members == null || game.identityKey in members) &&
                                    (query.isEmpty() || game.name.contains(query, ignoreCase = true) ||
                                        game.titleId?.contains(query, ignoreCase = true) == true)
                            }
                            when (sort) {
                                LibrarySort.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                                LibrarySort.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
                                LibrarySort.FORMAT -> filtered.sortedWith(compareBy({ it.format.name }, { it.name.lowercase() }))
                                LibrarySort.RECENT -> sortByRecent(filtered, activity)
                            }
                        }
                        Column(Modifier.fillMaxSize()) {
                            ScanProgressRow(viewModel)
                            if (s.truncated) {
                                TextButton(onClick = { viewModel.loadFolders(); foldersOpen = true },
                                    modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(stringResource(R.string.lib_scan_truncated))
                                }
                            }
                            LibraryNotices(s, missing, onFolders = { viewModel.loadFolders(); foldersOpen = true },
                                onMissing = { missingOpen = true })
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text(stringResource(R.string.lib_search)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    .onFocusChanged { searchHasFocus = it.isFocused },
                            )
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilterChip(
                                        selected = favoritesOnly,
                                        onClick = { favoritesOnly = !favoritesOnly },
                                        label = { Text(stringResource(R.string.lib_favorites)) },
                                    )
                                    if (collections.isNotEmpty()) {
                                        var collectionMenu by remember { mutableStateOf(false) }
                                        Box {
                                            TextButton(onClick = { collectionMenu = true }) {
                                                Text(shownCollection?.name ?: stringResource(R.string.lib_all_collections))
                                            }
                                            DropdownMenu(expanded = collectionMenu, onDismissRequest = { collectionMenu = false }) {
                                                DropdownMenuItem(text = { Text(stringResource(R.string.lib_all_games)) }, onClick = {
                                                    collectionFilter = null; collectionMenu = false
                                                })
                                                collections.forEach { collection ->
                                                    DropdownMenuItem(text = { Text("${collection.name} (${collection.members.size})") }, onClick = {
                                                        collectionFilter = collection.name; collectionMenu = false
                                                    })
                                                }
                                            }
                                        }
                                    }
                                }
                                var sortMenu by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { sortMenu = true }) { Text(sortText(sort)) }
                                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                        LibrarySort.entries.forEach { option ->
                                            DropdownMenuItem(text = { Text(sortText(option)) }, onClick = {
                                                viewModel.setSort(option); sortMenu = false
                                            })
                                        }
                                    }
                                }
                            }
                            if (preparingLaunch) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (visibleGames.isEmpty()) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    EmptyMessage(stringResource(R.string.lib_no_match), stringResource(R.string.lib_clear_search)) {
                                        searchQuery = ""
                                        favoritesOnly = false
                                        collectionFilter = null
                                    }
                                }
                            } else {
                                GameGrid(
                                    games = visibleGames,
                                    viewModel = viewModel,
                                    coverRevision = coverRevision,
                                    modifier = Modifier.weight(1f),
                                    gridState = gridState,
                                    favorites = favorites,
                                    restoreFocus = focusRestoreTick,
                                    focusAllowed = !searchHasFocus && pendingGame == null && pendingDiscInstall == null,
                                    lastFocusedId = lastFocusedId,
                                    onFocused = { lastFocusedId = it },
                                    onLaunch = startGame,
                                    onLongPress = { lastFocusedId = it.stableId; pendingGame = it },
                                )
                            }
                        }
                    }
            }
        }
        }
    }

   when (val result = updateResult) {
        is UpdateResult.Available -> {
            UpdateDialog(
                release = result.release,
                onDismiss = { updateResult = null }
            )
        }

        is UpdateResult.Latest -> {
            LatestVersionDialog(
                commitHash = result.commitHash,
                onDismiss = { updateResult = null }
            )
        }

        is UpdateResult.Cooldown -> {
            CooldownDialog(
                remainingMillis = result.remainingMillis,
                onDismiss = { updateResult = null }
            )
        }

        null -> {}
    }

    pendingDiscInstall?.let { (game, count) ->
        AlertDialog(
            onDismissRequest = { pendingDiscInstall = null },
            title = { Text(stringResource(R.string.lib_install_disc_title)) },
            text = {
                Text(pluralStringResource(R.plurals.lib_install_disc_text, count, count))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDiscInstall = null
                    onInstallFromDisc(game.launchUri)
                }) { Text(stringResource(R.string.lib_install)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDiscInstall = null
                    launchGame(context, viewModel, game)
                }) { Text(stringResource(R.string.lib_boot_anyway)) }
            },
        )
    }

    pendingGame?.let { game ->
        val dismiss: () -> Unit = {
            pendingGame = null; viewModel.clearTitleIdRequest(); viewModel.clearDetails(); focusRestoreTick++
        }
        val sheetState = rememberModalBottomSheetState()
        val details by viewModel.details.collectAsStateWithLifecycle()
        var ratingOpen by remember(game.identityKey) { mutableStateOf(false) }
        var collectionsOpen by remember(game.identityKey) { mutableStateOf(false) }
        if (collectionsOpen) {
            val failed: (Throwable) -> Unit = {
                val refused = it as? xendroid.compose.data.CollectionRefusedException
                Toast.makeText(context, when (refused?.why) {
                    xendroid.compose.data.CollectionRefusedException.Why.NO_NAME -> context.getString(R.string.col_why_no_name)
                    xendroid.compose.data.CollectionRefusedException.Why.DUPLICATE -> context.getString(R.string.col_why_duplicate, refused.name)
                    xendroid.compose.data.CollectionRefusedException.Why.TOO_MANY ->
                        context.getString(R.string.col_why_too_many, xendroid.compose.data.GameCollections.MAX_COLLECTIONS)
                    xendroid.compose.data.CollectionRefusedException.Why.FULL ->
                        context.getString(R.string.col_why_full, refused.name, xendroid.compose.data.GameCollections.MAX_MEMBERS)
                    xendroid.compose.data.CollectionRefusedException.Why.INVALID_GAME -> context.getString(R.string.col_why_invalid)
                    null -> it.message ?: context.getString(R.string.lib_collections_failed)
                }, Toast.LENGTH_LONG).show()
            }
            CollectionsDialog(
                gameName = game.name,
                gameKey = game.identityKey,
                collections = collections,
                onSetMember = { name, member ->
                    scope.launch {
                        viewModel.editCollections { xendroid.compose.data.GameCollections.setMember(it, name, game.identityKey, member) }
                            .onFailure(failed)
                    }
                },
                onCreate = { name ->
                    scope.launch {
                        viewModel.editCollections { xendroid.compose.data.GameCollections.create(it, name, game.identityKey) }
                            .onFailure(failed)
                    }
                },
                onDelete = { name ->
                    scope.launch {
                        viewModel.editCollections { xendroid.compose.data.GameCollections.delete(it, name) }.onFailure(failed)
                    }
                },
                onDismiss = { collectionsOpen = false },
            )
        }
        var timelineOpen by remember(game.identityKey) { mutableStateOf(false) }
        LaunchedEffect(game.identityKey) { viewModel.loadDetails(game) }
        if (ratingOpen) {
            CompatibilityRatingDialog(
                current = details?.compatibility?.latest?.status,
                onDismiss = { ratingOpen = false },
                onSave = { status, note -> ratingOpen = false; viewModel.rateCompatibility(game, status, note) },
            )
        }
        val timeline = details?.takeIf { it.identityKey == game.identityKey }?.lastRunEvents
        if (timelineOpen && timeline != null) RunTimelineDialog(timeline, onDismiss = { timelineOpen = false })
        var report by remember(game.identityKey) { mutableStateOf<xendroid.compose.sessions.RunReport?>(null) }
        var reportBusy by remember(game.identityKey) { mutableStateOf(false) }
        report?.let { shown ->
            RunReportDialog(shown, reportBusy, onShare = {
                reportBusy = true
                scope.launch {
                    try {
                        val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            xendroid.compose.sessions.SessionRuns.writeReport(context, shown)
                        }
                        context.startActivity(xendroid.compose.core.diagnosticsShareIntent(context, file))
                        report = null
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Toast.makeText(context, context.getString(R.string.lib_report_failed), Toast.LENGTH_LONG).show()
                    } finally {
                        reportBusy = false
                    }
                }
            }, onDismiss = { report = null })
        }
        ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = sheetState,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // The title-id status line shows ONLY while resolving or on error.
                val statusContent: (@Composable () -> Unit)? = when (val st = titleIdState) {
                    is TitleIdState.Loading -> ({
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.lib_reading_title_id))
                        }
                    })
                    is TitleIdState.Error -> ({ Text(st.message) })
                    else -> null
                }
                val cover = remember(game.identityKey, coverRevision) { viewModel.iconFileOrFallback(game) }
                ListItem(
                    leadingContent = {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(cover).build(),
                            contentDescription = stringResource(R.string.lib_cover),
                            modifier = Modifier.size(72.dp),
                        )
                    },
                    headlineContent = {
                        Text(game.name, style = MaterialTheme.typography.titleLarge)
                        if (game.isMultiDisc) {
                            Text(
                                stringResource(R.string.lib_disc_of, game.discNumber, game.discCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    supportingContent = if (game.titleId != null || game.mediaId != null || statusContent != null) {
                        {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                game.titleId?.let { Text(stringResource(R.string.lib_title_id, it)) }
                                game.mediaId?.let { Text(stringResource(R.string.lib_media_id, it)) }
                                game.titleId?.uppercase()?.let { activity[it] }?.let { played ->
                                    Text(pluralStringResource(R.plurals.lib_last_played, played.runs, java.text.DateFormat.getDateTimeInstance(
                                        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                                        .format(java.util.Date(played.lastPlayedAt)), formatPlayTime(played.playedMs), played.runs))
                                }
                                statusContent?.invoke()
                            }
                        }
                    } else {
                        null
                    },
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.lib_play)) },
                    supportingContent = activeProfile?.let { name -> { Text(stringResource(R.string.lib_signs_in_as, name)) } },
                    modifier = Modifier.clickable(enabled = !preparingLaunch) { dismiss(); startGame(game) },
                )
                ListItem(
                    headlineContent = { Text(if (isFavorite(game, favorites)) stringResource(R.string.lib_unfavorite) else stringResource(R.string.lib_favorite)) },
                    modifier = Modifier.clickable { viewModel.toggleFavorite(game) },
                )
                val inCollections = xendroid.compose.data.GameCollections.namesOf(collections, game.identityKey)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.lib_collections)) },
                    supportingContent = { Text(inCollections.joinToString(", ").ifEmpty { stringResource(R.string.lib_no_collection) }) },
                    modifier = Modifier.clickable { collectionsOpen = true },
                )
                if (xendroid.compose.data.CoverStore.normalize(game.titleId) != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.lib_change_cover)) },
                        supportingContent = { Text(stringResource(R.string.lib_change_cover_note)) },
                        modifier = Modifier.clickable {
                            coverTarget = game
                            pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    val custom = remember(game.identityKey, coverRevision) { viewModel.hasCustomCover(game) }
                    if (custom) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lib_own_icon)) },
                            modifier = Modifier.clickable { scope.launch { viewModel.clearCustomCover(game) } },
                        )
                    }
                }
                details?.takeIf { it.identityKey == game.identityKey && it.titleId != null }?.let { info ->
                    val latest = info.compatibility?.latest
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.lib_compatibility, latest?.status?.let { compatStatusText(it) } ?: stringResource(R.string.lib_not_rated))) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                latest?.let { report ->
                                    val media = report.mediaId?.let { stringResource(R.string.lib_media_short, it) }
                                    val disc = report.disc?.let { stringResource(R.string.lib_disc_short, it) }
                                    Text(stringResource(R.string.lib_your_result, java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                                        .format(java.util.Date(report.createdAt)), report.build, report.driverLabel ?: report.gpu) +
                                        listOfNotNull(media, disc).joinToString("") { " · $it" })
                                    if (report.note.isNotBlank()) Text(report.note)
                                }
                                info.lastProfile?.let { Text(stringResource(R.string.lib_last_profile, it)) }
                                info.lastRun?.let { run ->
                                    Text("Last run: ${describeRun(run)}")
                                    run.nativeBacktrace?.let { crash ->
                                        Text("Native crash: ${xendroid.compose.sessions.describeNativeCrash(crash)}")
                                        crash.frames.firstOrNull()?.let { Text("Top frame: $it") }
                                    }
                                    run.performance?.let { perf ->
                                        val median = perf.fpsPercentile(0.5)
                                        val low = perf.fpsPercentile(0.05)
                                        if (median != null && low != null) {
                                            Text("Guest FPS over 1-second windows: median $median, 5th percentile $low " +
                                                "(${perf.sampledSeconds} s sampled)")
                                        }
                                        describeFrameTimes(perf)?.let { Text("Guest frame time: $it") }
                                        perf.firstFrameSeconds?.let { Text("First frame after $it s") }
                                        describeAudio(perf)?.let { Text("Audio · $it") }
                                        describeFrameGeneration(perf)?.let { Text("Frame generation (experimental) · $it") }
                                        perf.pipelineCreations?.takeIf { it > 0 }?.let { count ->
                                            Text("Pipelines created: $count, %.1f s spent creating them"
                                                .format((perf.pipelineCreationMs ?: 0L) / 1000.0))
                                        }
                                    }
                                    run.driver?.let { Text("Driver: ${it.label}") }
                                }
                                Text(stringResource(R.string.lib_rate_note))
                            }
                        },
                        modifier = Modifier.clickable { ratingOpen = true },
                    )
                    info.catalog?.let { catalog -> CatalogResultsItem(catalog) { viewModel.refreshCatalog(game) } }
                    info.lastRunEvents?.takeIf { it.events.isNotEmpty() }?.let { log ->
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lib_timeline)) },
                            supportingContent = { Text(pluralStringResource(R.plurals.lib_timeline_note, log.events.size, log.events.size)) },
                            modifier = Modifier.clickable { timelineOpen = true },
                        )
                    }
                    info.lastRun?.let { run ->
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lib_share_report)) },
                            supportingContent = { Text(stringResource(R.string.lib_share_report_note)) },
                            modifier = Modifier.clickable {
                                report = xendroid.compose.sessions.RunReports.build(run, info.lastRunEvents,
                                    info.compatibility?.reports.orEmpty(),
                                    xendroid.compose.sessions.SessionRuns.reportDevice(xendroid.compose.BuildConfig.VERSION_NAME),
                                    System.currentTimeMillis())
                            },
                        )
                    }
                }

                val perGameEnabled = titleIdState !is TitleIdState.Loading
                ListItem(
                    headlineContent = { Text(stringResource(R.string.lib_per_game_settings)) },
                    colors = if (perGameEnabled) {
                        ListItemDefaults.colors()
                    } else {
                        ListItemDefaults.colors(
                            headlineColor =
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    },
                    modifier = Modifier.clickable(enabled = perGameEnabled) {
                        viewModel.requestPerGameSettings(game)
                    },
                )

                val shown = details?.takeIf { it.identityKey == game.identityKey }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.lib_patches)) },
                    supportingContent = shown?.patchesTotal?.let { total ->
                        { Text(stringResource(R.string.lib_patches_enabled, shown.patchesEnabled ?: 0, total)) }
                    },
                    colors = if (perGameEnabled) {
                        ListItemDefaults.colors()
                    } else {
                        ListItemDefaults.colors(
                            headlineColor =
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    },
                    modifier = Modifier.clickable(enabled = perGameEnabled) {
                        viewModel.requestGamePatches(game)
                    },
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.lib_manage_content)) },
                    supportingContent = shown?.updates?.let { updates ->
                        {
                            Text(listOf(
                                if (updates.isEmpty()) stringResource(R.string.lib_no_tu) else stringResource(R.string.lib_tu, updates.joinToString(", ")),
                                when (val dlc = shown.dlcCount ?: 0) { 0 -> stringResource(R.string.lib_dlc_none); else -> stringResource(R.string.lib_dlc_count, dlc) },
                            ).joinToString(" · "))
                        }
                    },
                    colors = if (perGameEnabled) {
                        ListItemDefaults.colors()
                    } else {
                        ListItemDefaults.colors(
                            headlineColor =
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    },
                    modifier = Modifier.clickable(enabled = perGameEnabled) {
                        viewModel.requestContentManager(game)
                    },
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.lib_saves)) },
                    modifier = Modifier.clickable(enabled = perGameEnabled) { viewModel.requestSaves(game) },
                )
                ListItem(headlineContent = { Text(stringResource(R.string.lib_sessions)) },
                    modifier = Modifier.clickable(enabled = perGameEnabled) { viewModel.requestDiagnostics(game) })

                if (game.format == GameFormat.ISO) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.lib_compress)) },
                        modifier = Modifier.clickable {
                            compressConfirmFor = game
                            pendingGame = null
                            viewModel.clearTitleIdRequest()
                        },
                    )
                }

                if (viewModel.canLaunchGames && viewModel.isPinShortcutSupported) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.lib_shortcut)) },
                        modifier = Modifier.clickable {
                            viewModel.createShortcut(game)
                            dismiss()
                        },
                    )
                }
            }
        }
    }

    LaunchedEffect(titleIdState) {
        (titleIdState as? TitleIdState.Resolved)?.let { r ->
            when (r.action) {
                GameAction.PER_GAME_SETTINGS ->
                    onOpenPerGameSettings(r.titleId, r.game.name, r.game.format, r.game.launchUri)
                GameAction.GAME_PATCHES ->
                    onOpenGamePatches(r.titleId, r.game.name)
                GameAction.MANAGE_CONTENT ->
                    onOpenContentManager(r.titleId, r.game.name)
                GameAction.SAVES -> onOpenSaves(r.titleId, r.game.name)
                GameAction.DIAGNOSTICS -> onOpenDiagnostics(r.titleId)
            }
            pendingGame = null
            viewModel.clearTitleIdRequest()
        }
    }

    compressConfirmFor?.let { game ->
        AlertDialog(
            onDismissRequest = { compressConfirmFor = null },
            title = { Text(stringResource(R.string.lib_compress_title)) },
            text = {
                Text(stringResource(R.string.lib_compress_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    compressConfirmFor = null
                    compressVm.compress(game.launchUri)
                }) { Text(stringResource(R.string.lib_compress_action)) }
            },
            dismissButton = {
                TextButton(onClick = { compressConfirmFor = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    when (val s = compressState) {
        is CompressState.Busy -> AlertDialog(
            onDismissRequest = {},   // not cancelable while running
            title = { Text(s.message) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.lib_compress_progress, (s.progress * 100).toInt()))
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.lib_may_take_while))
                    }
                }
            },
            confirmButton = {},
        )
        is CompressState.ConfirmDelete -> AlertDialog(
            // Dismissing keeps it: a stray tap outside must never delete the .iso.
            onDismissRequest = compressVm::keepIso,
            title = { Text(stringResource(R.string.lib_delete_iso_title)) },
            text = {
                Text(stringResource(R.string.lib_delete_iso_text, s.zarName, s.isoName, formatBytes(s.isoBytes)))
            },
            confirmButton = {
                TextButton(onClick = compressVm::deleteIso) { Text(stringResource(R.string.lib_delete_iso)) }
            },
            dismissButton = { TextButton(onClick = compressVm::keepIso) { Text(stringResource(R.string.lib_keep_it)) } },
        )
        is CompressState.Done -> AlertDialog(
            onDismissRequest = { compressVm.dismiss(); viewModel.refresh() },
            title = { Text(stringResource(R.string.common_done)) },
            text = { Text(s.message) },
            confirmButton = {
                TextButton(onClick = { compressVm.dismiss(); viewModel.refresh() }) { Text(stringResource(R.string.common_ok)) }
            },
        )
        is CompressState.Failed -> AlertDialog(
            onDismissRequest = compressVm::dismiss,
            title = { Text(stringResource(R.string.common_failed)) },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = compressVm::dismiss) { Text(stringResource(R.string.common_ok)) } },
        )
        else -> {}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameGrid(
    games: List<Game>,
    viewModel: GameLibraryViewModel,
    coverRevision: Int,
    onLaunch: (Game) -> Unit,
    onLongPress: (Game) -> Unit,
    gridState: LazyGridState,
    favorites: Set<String>,
    restoreFocus: Int,
    focusAllowed: Boolean,
    lastFocusedId: String?,
    onFocused: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inputMode = LocalInputModeManager.current.inputMode
    val requesters = remember(games.map { it.stableId }) {
        games.associate { it.stableId to FocusRequester() }
    }
    LaunchedEffect(restoreFocus, inputMode, focusAllowed) {
        if (inputMode == InputMode.Keyboard && focusAllowed && games.isNotEmpty()) {
            val index = games.indexOfFirst { it.stableId == lastFocusedId }.coerceAtLeast(0)
            gridState.scrollToItem(index)
            withFrameNanos { }
            runCatching { requesters.getValue(games[index].stableId).requestFocus() }
                .onFailure { Log.d("GameLibrary", "Focus target no longer attached", it) }
        }
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(games, key = { it.stableId }) { game ->
            GameCell(game, viewModel, onLaunch, onLongPress,
                favorite = isFavorite(game, favorites),
                coverRevision = coverRevision,
                focusRequester = requesters.getValue(game.stableId),
                onFocused = { onFocused(game.stableId) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCell(
    game: Game,
    viewModel: GameLibraryViewModel,
    onLaunch: (Game) -> Unit,
    onLongPress: (Game) -> Unit,
    favorite: Boolean,
    coverRevision: Int,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
) {
    val context = LocalContext.current
    // Once per cell (and per cover change): the File stats must not run on every
    // recomposition while scrolling.
    val iconModel = remember(game.stableId, coverRevision) { viewModel.iconFileOrFallback(game) }
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(8.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused; if (focused) onFocused() }
            .border(
                2.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .onPreviewKeyEvent { event ->
                val key = event.nativeKeyEvent.keyCode
                if (key in listOf(AndroidKeyEvent.KEYCODE_BUTTON_A, AndroidKeyEvent.KEYCODE_BUTTON_X,
                        AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_BUTTON_Y)) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        when (key) {
                            AndroidKeyEvent.KEYCODE_BUTTON_A -> onLaunch(game)
                            AndroidKeyEvent.KEYCODE_BUTTON_Y -> viewModel.toggleFavorite(game)
                            else -> onLongPress(game)
                        }
                    }
                    true // consume both down and up, avoiding duplicate Compose clicks
                } else false
            }
            .combinedClickable(onClick = { onLaunch(game) }, onLongClick = { onLongPress(game) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(iconModel)
                .build(),
            contentDescription = game.name,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (favorite) "★ ${game.name}" else game.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // A set shares one title, so the tiles would otherwise be identical.
        if (game.isMultiDisc) {
            Text(
                stringResource(R.string.lib_disc_of, game.discNumber, game.discCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** L09: what the scan is doing, with "Stop"; nothing when no scan runs, and nothing for the
 *  first moments of one, so a quick rescan on return to the app does not shift the grid. */
@Composable
private fun ScanProgressRow(viewModel: GameLibraryViewModel) {
    val progress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val scanning = progress != null
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(scanning) {
        shown = false
        if (scanning) {
            kotlinx.coroutines.delay(SCAN_PROGRESS_DELAY_MS)
            shown = true
        }
    }
    val p = progress?.takeIf { shown } ?: return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            when {
                p.reading != null -> stringResource(R.string.lib_scan_reading, p.reading, p.checked + 1, p.candidates)
                p.candidates > 0 -> pluralStringResource(R.plurals.lib_scan_checking, p.candidates, p.checked, p.candidates)
                else -> pluralStringResource(R.plurals.lib_scan_looking, p.entries, p.entries)
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = viewModel::stopScan) { Text(stringResource(R.string.lib_stop)) }
    }
}

/** Game folders not available now (L03) and games no longer in the library (L06). */
@Composable
private fun LibraryNotices(
    s: LibraryUiState.Loaded,
    missing: List<xendroid.compose.data.MissingTitle>,
    onFolders: () -> Unit,
    onMissing: () -> Unit,
) {
    if (s.unavailableRoots.isNotEmpty()) {
        TextButton(onClick = onFolders, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(pluralStringResource(R.plurals.lib_folders_unavailable, s.unavailableRoots.size, s.unavailableRoots.size))
        }
    }
    val gone = missing.count { it.reason != xendroid.compose.data.MissingTitles.Reason.FOLDER_AWAY }
    if (gone > 0) {
        TextButton(onClick = onMissing, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(pluralStringResource(R.plurals.lib_games_gone, gone, gone))
        }
    }
}

@Composable
private fun EmptyMessage(
    text: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun NoVulkanDialog(onQuit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onQuit,
        confirmButton = { TextButton(onClick = onQuit) { Text(stringResource(R.string.lib_quit)) } },
        title = { Text(stringResource(R.string.lib_unsupported)) },
        text = { Text(stringResource(R.string.lib_no_vulkan)) },
    )
}

fun checkForUpdatesClicked(
    context: Context,
    scope: CoroutineScope,
    onResult: (UpdateResult) -> Unit
) {
    scope.launch {
        if (!shouldCheckForUpdates(context)) {
            Log.d("Updater", "Skipping update check")
            onResult(UpdateResult.Cooldown(getRemainingCooldown(context)))
            return@launch
        }

        try {
            val result = checkForUpdates(context)
            saveLastCheck(context)
            onResult(result)
        } catch (e: Exception) {
            Log.e("Updater", "Failed to check updates", e)
        }
    }
}

/** Same shape as the content-install formatter, which is private to that file. */
private fun formatBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val u = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = -1
    do { v /= 1024.0; i++ } while (v >= 1024.0 && i < u.lastIndex)
    return "%.1f %s".format(v, u[i])
}

/** Reap any stale/orphaned :emu first (single-shot core). The new :emu links itself to the
 *  launcher by binding MainAliveService, so nothing rides on the Intent. */
private fun launchGame(context: Context, viewModel: GameLibraryViewModel, game: Game) {
    runCatching {
        EmuProcessLink.killStaleEmu(context)
        context.startActivity(viewModel.buildLaunchIntent(game))
    }
}

/** U02: the sort order as shown (the enum's own label stays the stable English name). */
@Composable
private fun sortText(sort: LibrarySort): String = when (sort) {
    LibrarySort.NAME_ASC -> stringResource(R.string.lib_sort_name_asc)
    LibrarySort.NAME_DESC -> stringResource(R.string.lib_sort_name_desc)
    LibrarySort.FORMAT -> stringResource(R.string.lib_sort_format)
    LibrarySort.RECENT -> stringResource(R.string.lib_sort_recent)
}

/** U02: a compatibility result as shown; reports keep the English label. */
@Composable
fun compatStatusText(status: xendroid.compose.compatibility.CompatStatus): String = when (status) {
    xendroid.compose.compatibility.CompatStatus.NOTHING -> stringResource(R.string.compat_nothing)
    xendroid.compose.compatibility.CompatStatus.BOOTS -> stringResource(R.string.compat_boots)
    xendroid.compose.compatibility.CompatStatus.INTRO -> stringResource(R.string.compat_intro)
    xendroid.compose.compatibility.CompatStatus.IN_GAME -> stringResource(R.string.compat_in_game)
    xendroid.compose.compatibility.CompatStatus.PLAYABLE -> stringResource(R.string.compat_playable)
}

/** C04: what the signed catalog says about the game, one line per build/GPU/driver, this phone's first. */
@Composable
private fun CatalogResultsItem(catalog: GameLibraryViewModel.CatalogView, onRefresh: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.lib_catalog)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val copy = catalog.copy
                if (copy == null) {
                    Text(stringResource(R.string.lib_catalog_none))
                } else {
                    Text(stringResource(R.string.lib_catalog_copy, copy.payload.sequence,
                        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(copy.fetchedAt))) +
                        if (copy.freshness == xendroid.compose.compatibility.CompatCatalog.Freshness.STALE) stringResource(R.string.lib_catalog_stale) else "")
                    if (catalog.results.isEmpty()) Text(stringResource(R.string.lib_catalog_empty))
                    catalog.results.take(4).forEach { setup ->
                        Text((if (setup.thisSetup) stringResource(R.string.lib_catalog_this) else stringResource(R.string.lib_catalog_setup, setup.build, setup.gpu)) +
                            (if (setup.driver.isNotEmpty()) " · ${setup.driver}" else "") + ": ${setup.summary} (${setup.latestDate})")
                    }
                    if (catalog.results.size > 4) Text(pluralStringResource(R.plurals.lib_catalog_more, catalog.results.size - 4, catalog.results.size - 4))
                    if (catalog.results.any { !it.thisSetup }) Text(stringResource(R.string.lib_catalog_note))
                }
                catalog.message?.let { Text(it) }
            }
        },
        trailingContent = {
            TextButton(onClick = onRefresh, enabled = !catalog.refreshing) { Text(if (catalog.refreshing) stringResource(R.string.lib_catalog_downloading) else stringResource(R.string.common_refresh)) }
        },
    )
}
