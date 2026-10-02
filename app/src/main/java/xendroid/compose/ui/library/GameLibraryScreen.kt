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

    val startGame: (Game) -> Unit = start@{ game ->
        if (preparingLaunch) return@start
        lastFocusedId = game.stableId
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
                LibraryUiState.Loading -> CircularProgressIndicator()
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
                        val visibleGames = remember(s.games, searchQuery, favoritesOnly, favorites, sort, activity) {
                            val query = searchQuery.trim()
                            val filtered = s.games.filter { game ->
                                (!favoritesOnly || isFavorite(game, favorites)) &&
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
                                FilterChip(
                                    selected = favoritesOnly,
                                    onClick = { favoritesOnly = !favoritesOnly },
                                    label = { Text("Favorites") },
                                )
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
                    modifier = Modifier.clickable(enabled = !preparingLaunch) { dismiss(); startGame(game) },
                )
                ListItem(
                    headlineContent = { Text(if (isFavorite(game, favorites)) "Remove from favorites" else "Add to favorites") },
                    modifier = Modifier.clickable { viewModel.toggleFavorite(game) },
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

                ListItem(
                    headlineContent = { Text("Game patches") },
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
