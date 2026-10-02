package xendroid.compose.ui.library

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
                .onSuccess { Toast.makeText(context, "Cover changed", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Could not use that image: ${it.message}", Toast.LENGTH_LONG).show() }
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
                Toast.makeText(context, "Could not prepare this game for launch", Toast.LENGTH_LONG).show()
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
                        .onFailure { Toast.makeText(context, "Could not sign that profile in: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            },
            onDismiss = { playAs = null },
        )
    }

    val startGame: (Game) -> Unit = start@{ game ->
        if (preparingLaunch || playAs != null) return@start
        // The list may be last time's (L09) or older than a file manager's change.
        if (!java.io.File(game.launchUri).exists()) {
            Toast.makeText(context, "${game.name} is not where it was; checking the folders again", Toast.LENGTH_LONG).show()
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
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // Only offered where All Files Access exists (API 30+); on API 29 the
                        // empty state explains why.
                        if (AllFilesAccess.isSupported) {
                            DropdownMenuItem(
                                text = { Text("Add game folder") },
                                onClick = { menuOpen = false; startRealPathMode() },
                            )
                            DropdownMenuItem(
                                text = { Text("Game folders") },
                                onClick = { menuOpen = false; viewModel.loadFolders(); foldersOpen = true },
                            )
                        }
                        if (missing.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Games no longer in the library (${missing.size})") },
                                onClick = { menuOpen = false; missingOpen = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Install content") },
                            onClick = { menuOpen = false; onOpenInstallContent() },
                        )
                        DropdownMenuItem(
                            text = { Text("Profiles") },
                            onClick = { menuOpen = false; onOpenProfiles() },
                        )
                        DropdownMenuItem(text = { Text("Diagnostics") }, onClick = {
                            menuOpen = false; onOpenDiagnostics(null)
                        })
                        DropdownMenuItem(
                            text = { Text("Key mapping") },
                            onClick = { menuOpen = false; onOpenKeymap() },
                        )
                        DropdownMenuItem(
                            text = { Text("Touch controls") },
                            onClick = { menuOpen = false; onOpenTouchControls() },
                        )
                        DropdownMenuItem(
                            text = { Text("Use this phone as a controller") },
                            onClick = { menuOpen = false; onOpenPhoneController() },
                        )
                        DropdownMenuItem(
                            text = { Text("Test controllers") },
                            onClick = { menuOpen = false; onOpenControllerTest() },
                        )
                        DropdownMenuItem(
                            text = { Text("Compare runs") },
                            onClick = { menuOpen = false; onOpenBenchmark() },
                        )
                        DropdownMenuItem(
                            text = { Text("Setup assistant") },
                            onClick = { menuOpen = false; assistantOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Open user data") },
                            onClick = {
                                menuOpen = false
                                openUserData(context)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            onClick = { menuOpen = false; onOpenAbout() },
                        )

                        DropdownMenuItem(
                            text = { Text("Check for Updates") },
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
            val setFolderLabel = if (allFilesGranted) "Set game folder" else "Grant All Files Access"
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
                        EmptyMessage("No game folder set", setFolderLabel,
                            onAction = startRealPathMode)
                    else
                        EmptyMessage(
                            "Setting a game folder requires Android 11 or newer.",
                            "OK", onAction = {})
                LibraryUiState.PermissionLost ->
                    EmptyMessage("Folder access lost", setFolderLabel,
                        onAction = startRealPathMode)
                is LibraryUiState.Error ->
                    EmptyMessage(s.message, "Retry", onAction = { viewModel.refresh() })
                is LibraryUiState.Loaded ->
                    if (s.games.isEmpty())
                        EmptyMessage("No games in this folder", "Choose another",
                            onAction = startRealPathMode)
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
                                    Text("The scan stopped after 100,000 files, so this list is partial: a game folder " +
                                        "seems to hold more than games. Manage folders")
                                }
                            }
                            if (s.unavailableRoots.isNotEmpty()) {
                                TextButton(onClick = { viewModel.loadFolders(); foldersOpen = true },
                                    modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text("${s.unavailableRoots.size} game folder(s) not available now " +
                                        "(SD card or permission?): their games are hidden. Manage folders")
                                }
                            }
                            val gone = missing.count { it.reason != xendroid.compose.data.MissingTitles.Reason.FOLDER_AWAY }
                            if (gone > 0) {
                                TextButton(onClick = { missingOpen = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text("$gone game(s) you played or added are no longer in the library. Review")
                                }
                            }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Search games or Title ID") },
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
                                        label = { Text("Favorites") },
                                    )
                                    if (collections.isNotEmpty()) {
                                        var collectionMenu by remember { mutableStateOf(false) }
                                        Box {
                                            TextButton(onClick = { collectionMenu = true }) {
                                                Text(shownCollection?.name ?: "All collections")
                                            }
                                            DropdownMenu(expanded = collectionMenu, onDismissRequest = { collectionMenu = false }) {
                                                DropdownMenuItem(text = { Text("All games") }, onClick = {
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
                                    TextButton(onClick = { sortMenu = true }) { Text(sort.label) }
                                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                        LibrarySort.entries.forEach { option ->
                                            DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                                viewModel.setSort(option); sortMenu = false
                                            })
                                        }
                                    }
                                }
                            }
                            if (preparingLaunch) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (visibleGames.isEmpty()) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    EmptyMessage("No games match your search", "Clear search") {
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
            title = { Text("Install disc") },
            text = {
                Text("This disc carries $count content package(s) the game installs before " +
                     "it will run. Install them now, or boot the disc anyway?")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDiscInstall = null
                    onInstallFromDisc(game.launchUri)
                }) { Text("Install") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDiscInstall = null
                    launchGame(context, viewModel, game)
                }) { Text("Boot anyway") }
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
            val failed: (Throwable) -> Unit = { Toast.makeText(context, it.message ?: "Could not change collections", Toast.LENGTH_LONG).show() }
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
                        Toast.makeText(context, "Could not create the run report", Toast.LENGTH_LONG).show()
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
                            Text("Reading title id…")
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
                            contentDescription = "Cover",
                            modifier = Modifier.size(72.dp),
                        )
                    },
                    headlineContent = {
                        Text(game.name, style = MaterialTheme.typography.titleLarge)
                        if (game.isMultiDisc) {
                            Text(
                                "Disc ${game.discNumber} of ${game.discCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    supportingContent = if (game.titleId != null || game.mediaId != null || statusContent != null) {
                        {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                game.titleId?.let { Text("Title ID: $it") }
                                game.mediaId?.let { Text("Media ID: $it") }
                                game.titleId?.uppercase()?.let { activity[it] }?.let { played ->
                                    Text("Last played " + java.text.DateFormat.getDateTimeInstance(
                                        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                                        .format(java.util.Date(played.lastPlayedAt)) +
                                        " · ${formatPlayTime(played.playedMs)} in ${played.runs} session(s)")
                                }
                                statusContent?.invoke()
                            }
                        }
                    } else {
                        null
                    },
                )

                ListItem(
                    headlineContent = { Text("Play") },
                    supportingContent = activeProfile?.let { name -> { Text("Signs in as $name") } },
                    modifier = Modifier.clickable(enabled = !preparingLaunch) { dismiss(); startGame(game) },
                )
                ListItem(
                    headlineContent = { Text(if (isFavorite(game, favorites)) "Remove from favorites" else "Add to favorites") },
                    modifier = Modifier.clickable { viewModel.toggleFavorite(game) },
                )
                val inCollections = xendroid.compose.data.GameCollections.namesOf(collections, game.identityKey)
                ListItem(
                    headlineContent = { Text("Collections") },
                    supportingContent = { Text(inCollections.joinToString(", ").ifEmpty { "Not in a collection" }) },
                    modifier = Modifier.clickable { collectionsOpen = true },
                )
                if (xendroid.compose.data.CoverStore.normalize(game.titleId) != null) {
                    ListItem(
                        headlineContent = { Text("Change cover") },
                        supportingContent = { Text("Kept for this title on every disc, even if the file moves") },
                        modifier = Modifier.clickable {
                            coverTarget = game
                            pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    val custom = remember(game.identityKey, coverRevision) { viewModel.hasCustomCover(game) }
                    if (custom) {
                        ListItem(
                            headlineContent = { Text("Use the game's own icon") },
                            modifier = Modifier.clickable { scope.launch { viewModel.clearCustomCover(game) } },
                        )
                    }
                }
                details?.takeIf { it.identityKey == game.identityKey && it.titleId != null }?.let { info ->
                    val latest = info.compatibility?.latest
                    ListItem(
                        headlineContent = { Text("Compatibility: ${latest?.status?.label ?: "not rated"}") },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                latest?.let { report ->
                                    Text("Your result on " + java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                                        .format(java.util.Date(report.createdAt)) +
                                        " · build ${report.build} · ${report.driverLabel ?: report.gpu}" +
                                        listOfNotNull(report.mediaId?.let { "media $it" }, report.disc?.let { "disc $it" })
                                            .joinToString("") { " · $it" })
                                    if (report.note.isNotBlank()) Text(report.note)
                                }
                                info.lastRun?.let { run ->
                                    Text("Last run: ${describeRun(run)}")
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
                                Text("Rate it yourself: results are kept per build and driver, never guessed.")
                            }
                        },
                        modifier = Modifier.clickable { ratingOpen = true },
                    )
                    info.catalog?.let { catalog -> CatalogResultsItem(catalog) { viewModel.refreshCatalog(game) } }
                    info.lastRunEvents?.takeIf { it.events.isNotEmpty() }?.let { log ->
                        ListItem(
                            headlineContent = { Text("Last run timeline") },
                            supportingContent = { Text("${log.events.size} events: lifecycle, pauses, stalls, heat, controllers, errors") },
                            modifier = Modifier.clickable { timelineOpen = true },
                        )
                    }
                    info.lastRun?.let { run ->
                        ListItem(
                            headlineContent = { Text("Share last run report") },
                            supportingContent = { Text("Review what it contains first; you choose where it goes") },
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
                    headlineContent = { Text("Per-game settings") },
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
                    headlineContent = { Text("Game patches") },
                    supportingContent = shown?.patchesTotal?.let { total ->
                        { Text("${shown.patchesEnabled ?: 0} of $total enabled") }
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
                    headlineContent = { Text("Manage content") },
                    supportingContent = shown?.updates?.let { updates ->
                        {
                            Text(listOf(
                                if (updates.isEmpty()) "No title update" else "Title update: ${updates.joinToString(", ")}",
                                when (val dlc = shown.dlcCount ?: 0) { 0 -> "no DLC"; 1 -> "1 DLC"; else -> "$dlc DLC" },
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
                    headlineContent = { Text("Saves · backup and restore") },
                    modifier = Modifier.clickable(enabled = perGameEnabled) { viewModel.requestSaves(game) },
                )
                ListItem(headlineContent = { Text("Last sessions · diagnostics") },
                    modifier = Modifier.clickable(enabled = perGameEnabled) { viewModel.requestDiagnostics(game) })

                if (game.format == GameFormat.ISO) {
                    ListItem(
                        headlineContent = { Text("Compress to .zar") },
                        modifier = Modifier.clickable {
                            compressConfirmFor = game
                            pendingGame = null
                            viewModel.clearTitleIdRequest()
                        },
                    )
                }

                if (viewModel.canLaunchGames && viewModel.isPinShortcutSupported) {
                    ListItem(
                        headlineContent = { Text("Create shortcut") },
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
            title = { Text("Compress to .zar?") },
            text = {
                Text(
                    "This packs the disc into a smaller .zar. The original .iso is left alone " +
                        "until the .zar is created and verified, and you are asked before it is " +
                        "deleted. The game stays in your library.")
            },
            confirmButton = {
                TextButton(onClick = {
                    compressConfirmFor = null
                    compressVm.compress(game.launchUri)
                }) { Text("Compress") }
            },
            dismissButton = {
                TextButton(onClick = { compressConfirmFor = null }) { Text("Cancel") }
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
                        Text("${(s.progress * 100).toInt()}%  ·  this may take a while.")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("This may take a while.")
                    }
                }
            },
            confirmButton = {},
        )
        is CompressState.ConfirmDelete -> AlertDialog(
            // Dismissing keeps it: a stray tap outside must never delete the .iso.
            onDismissRequest = compressVm::keepIso,
            title = { Text("Delete the original .iso?") },
            text = {
                Text(
                    "“${s.zarName}” was created and verified. Deleting “${s.isoName}” " +
                        "frees ${formatBytes(s.isoBytes)}.")
            },
            confirmButton = {
                TextButton(onClick = compressVm::deleteIso) { Text("Delete .iso") }
            },
            dismissButton = { TextButton(onClick = compressVm::keepIso) { Text("Keep it") } },
        )
        is CompressState.Done -> AlertDialog(
            onDismissRequest = { compressVm.dismiss(); viewModel.refresh() },
            title = { Text("Done") },
            text = { Text(s.message) },
            confirmButton = {
                TextButton(onClick = { compressVm.dismiss(); viewModel.refresh() }) { Text("OK") }
            },
        )
        is CompressState.Failed -> AlertDialog(
            onDismissRequest = compressVm::dismiss,
            title = { Text("Failed") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = compressVm::dismiss) { Text("OK") } },
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
                "Disc ${game.discNumber} of ${game.discCount}",
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
                p.reading != null -> "Reading ${p.reading} (${p.checked + 1} of ${p.candidates})"
                p.candidates > 0 -> "Checking ${p.checked} of ${p.candidates} files"
                else -> "Looking through ${p.entries} files…"
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = viewModel::stopScan) { Text("Stop") }
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
        confirmButton = { TextButton(onClick = onQuit) { Text("Quit") } },
        title = { Text("Unsupported device") },
        text = { Text("This device has no Vulkan GPU; the emulator cannot run.") },
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

/** C04: what the signed catalog says about the game, one line per build/GPU/driver, this phone's first. */
@Composable
private fun CatalogResultsItem(catalog: GameLibraryViewModel.CatalogView, onRefresh: () -> Unit) {
    ListItem(
        headlineContent = { Text("Catalog results") },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val copy = catalog.copy
                if (copy == null) {
                    Text("No catalog downloaded yet.")
                } else {
                    Text("Signed catalog, publication ${copy.payload.sequence}, downloaded " +
                        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(copy.fetchedAt)) +
                        if (copy.freshness == xendroid.compose.compatibility.CompatCatalog.Freshness.STALE) "; out of date, refresh it" else "")
                    if (catalog.results.isEmpty()) Text("No results for this game.")
                    catalog.results.take(4).forEach { setup ->
                        Text((if (setup.thisSetup) "This build and GPU" else "Build ${setup.build} · ${setup.gpu}") +
                            (if (setup.driver.isNotEmpty()) " · ${setup.driver}" else "") + ": ${setup.summary} (${setup.latestDate})")
                    }
                    if (catalog.results.size > 4) Text("${catalog.results.size - 4} other setup(s).")
                    if (catalog.results.any { !it.thisSetup }) Text("Results of other builds, GPUs or drivers may not hold here.")
                }
                catalog.message?.let { Text(it) }
            }
        },
        trailingContent = {
            TextButton(onClick = onRefresh, enabled = !catalog.refreshing) { Text(if (catalog.refreshing) "Downloading…" else "Refresh") }
        },
    )
}
