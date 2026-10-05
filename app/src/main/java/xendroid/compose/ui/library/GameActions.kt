package xendroid.compose.ui.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.R
import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.core.EmuProcessLink
import xendroid.compose.core.LaunchOptions
import xendroid.compose.data.CollectionRefusedException
import xendroid.compose.data.Game
import xendroid.compose.data.GameCollections
import xendroid.compose.data.ProfilePick
import xendroid.compose.sessions.RunReport
import xendroid.compose.ui.compress.GameCompressViewModel
import xendroid.compose.ui.compress.GameCompressViewModel.CompressState
import xendroid.compose.ui.design.CompatTone
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdBar
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdMenuItem
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdStatusPill
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTextInput
import xendroid.compose.ui.design.XdToastState

/** The compatibility scale as the redesign paints it. */
fun CompatStatus?.tone(): CompatTone = when (this) {
    CompatStatus.PLAYABLE -> CompatTone.PLAYABLE
    CompatStatus.IN_GAME -> CompatTone.INGAME
    CompatStatus.INTRO -> CompatTone.INTRO
    CompatStatus.BOOTS -> CompatTone.BOOTS
    CompatStatus.NOTHING -> CompatTone.NOTHING
    null -> CompatTone.NONE
}

/**
 * What the library and the game sheet do with a game, with the questions on the way: Play (who
 * plays, content still on the disc), a cover, collections, a rating, compression, the shader
 * cache, the run report. One instance per screen; [GameActionDialogs] draws what it asks.
 */
@Stable
class GameActions internal constructor(
    private val vm: GameLibraryViewModel,
    private val compressVm: GameCompressViewModel,
    private val context: Context,
    private val scope: CoroutineScope,
    private val toast: XdToastState,
    private val onInstallFromDisc: (String) -> Unit,
) {
    var preparing by mutableStateOf(false)
        private set
    internal var playAs by mutableStateOf<Pair<Game, ProfilePick.Decision.Ask>?>(null)
    /** Content still on the disc: the game, how many packages, and the launch it holds back. */
    internal var discInstall by mutableStateOf<Triple<Game, Int, LaunchOptions?>?>(null)
    internal var compressConfirm by mutableStateOf<Game?>(null)
    internal var cacheConfirm by mutableStateOf<Pair<Game, Long>?>(null)
    internal var rating by mutableStateOf<Game?>(null)
    internal var collections by mutableStateOf<Game?>(null)
    internal var report by mutableStateOf<RunReport?>(null)
    internal var reportBusy by mutableStateOf(false)
    internal var coverTarget: Game? = null
    internal var launchPicker: (() -> Unit)? = null

    /** Play: the file must still be there; who plays is asked when the profiles say so. With
     *  [options] ("Start with…"), they hold for this launch only and name who plays, if anyone. */
    fun play(game: Game, options: LaunchOptions? = null) {
        if (preparing || playAs != null) return
        // The list may be last time's (L09) or older than a file manager's change.
        if (!java.io.File(game.launchUri).exists()) {
            toast.show(context.getString(R.string.lib_game_moved, game.name))
            vm.refresh()
            return
        }
        if (options?.profileXuid != null) { prepareAndLaunch(game, options); return }
        scope.launch {
            val decision = runCatching { vm.profileDecision() }
                .onFailure { Log.w("GameLibrary", "Reading profiles failed", it) }.getOrNull()
            when (decision) {
                is ProfilePick.Decision.Ask -> { pendingOptions = options; playAs = game to decision }
                is ProfilePick.Decision.Launch ->
                    if (decision.changes && decision.xuid != null) {
                        runCatching { vm.playAs(decision.xuid, dontAskAgain = false) }
                        prepareAndLaunch(game, options)
                    } else prepareAndLaunch(game, options)
                null -> prepareAndLaunch(game, options)   // profiles unreadable: boot as configured
            }
        }
    }

    /** The options of the launch waiting on "who plays". */
    private var pendingOptions: LaunchOptions? = null

    internal fun playAsChosen(game: Game, xuid: String, dontAsk: Boolean) {
        playAs = null
        val options = pendingOptions.also { pendingOptions = null }
        scope.launch {
            runCatching { vm.playAs(xuid, dontAsk) }
                .onSuccess { prepareAndLaunch(game, options) }
                .onFailure { toast.show(context.getString(R.string.lib_profile_signin_failed, it.message)) }
        }
    }

    private fun prepareAndLaunch(game: Game, options: LaunchOptions?) {
        if (preparing) return
        preparing = true
        scope.launch {
            try {
                val pending = vm.uninstalledDiscContent(game)
                if (pending.isNotEmpty()) discInstall = Triple(game, pending.size, options) else launch(game, options)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("GameLibrary", "Preparing launch failed", e)
                toast.show(context.getString(R.string.lib_launch_prepare_failed))
            } finally { preparing = false }
        }
    }

    /** Reaps a stale :emu first (single-shot core); the new one links itself to the launcher. */
    internal fun launch(game: Game, options: LaunchOptions?) {
        scope.launch {
            val args = options?.let { runCatching { vm.launchArgs(it) }.onFailure { e -> Log.w("GameLibrary", "Launch options failed", e) }.getOrNull() }
            if (options != null && args == null) { toast.show(context.getString(R.string.lib_launch_prepare_failed)); return@launch }
            runCatching {
                EmuProcessLink.killStaleEmu(context)
                context.startActivity(vm.buildLaunchIntent(game, args.orEmpty()))
            }.onFailure { Log.w("GameLibrary", "Starting the game failed", it) }
        }
    }

    internal fun installFromDisc(game: Game) { discInstall = null; onInstallFromDisc(game.launchUri) }

    fun pickCover(game: Game) { coverTarget = game; launchPicker?.invoke() }

    internal fun coverPicked(uri: Uri?) {
        val game = coverTarget
        coverTarget = null
        if (uri != null && game != null) scope.launch {
            vm.setCustomCover(game, uri)
                .onSuccess { toast.show(context.getString(R.string.lib_cover_changed)) }
                .onFailure { toast.show(context.getString(R.string.lib_cover_failed, it.message)) }
        }
    }

    fun ownIcon(game: Game) { scope.launch { vm.clearCustomCover(game) } }
    fun editCollections(game: Game) { collections = game }
    fun rate(game: Game) { rating = game }
    fun compress(game: Game) { compressConfirm = game }
    fun clearShaderCache(game: Game, bytes: Long) { cacheConfirm = game to bytes }
    fun shortcut(game: Game) = vm.createShortcut(game)
    val canShortcut: Boolean get() = vm.canLaunchGames && vm.isPinShortcutSupported

    fun toggleFavorite(game: Game) = vm.toggleFavorite(game)

    /** The last run's report, reviewed in a sheet before it is shared. */
    fun showReport(report: RunReport) { this.report = report }

    internal fun shareReport(shown: RunReport) {
        reportBusy = true
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { xendroid.compose.sessions.SessionRuns.writeReport(context, shown) }
                context.startActivity(xendroid.compose.core.diagnosticsShareIntent(context, file))
                report = null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                toast.show(context.getString(R.string.lib_report_failed))
            } finally { reportBusy = false }
        }
    }

    internal fun clearCache(game: Game) {
        cacheConfirm = null
        scope.launch {
            val cleared = vm.clearShaderCache(game)
            toast.show(when {
                cleared == null -> context.getString(R.string.lib_shader_cache_busy)
                cleared.failed > 0 -> context.resources.getQuantityString(R.plurals.lib_shader_cache_failed, cleared.failed, cleared.failed)
                else -> context.getString(R.string.lib_shader_cache_cleared, android.text.format.Formatter.formatShortFileSize(context, cleared.bytes))
            })
        }
    }

    internal fun editCollectionsWith(game: Game, edit: (List<xendroid.compose.data.GameCollection>) -> List<xendroid.compose.data.GameCollection>) {
        scope.launch {
            vm.editCollections(edit).onFailure {
                val refused = it as? CollectionRefusedException
                toast.show(when (refused?.why) {
                    CollectionRefusedException.Why.NO_NAME -> context.getString(R.string.col_why_no_name)
                    CollectionRefusedException.Why.DUPLICATE -> context.getString(R.string.col_why_duplicate, refused.name)
                    CollectionRefusedException.Why.TOO_MANY -> context.getString(R.string.col_why_too_many, GameCollections.MAX_COLLECTIONS)
                    CollectionRefusedException.Why.FULL -> context.getString(R.string.col_why_full, refused.name, GameCollections.MAX_MEMBERS)
                    CollectionRefusedException.Why.INVALID_GAME -> context.getString(R.string.col_why_invalid)
                    null -> it.message ?: context.getString(R.string.lib_collections_failed)
                })
            }
        }
    }

    internal fun startCompress(game: Game) { compressConfirm = null; compressVm.compress(game.launchUri) }
    internal val compressor: GameCompressViewModel get() = compressVm
    internal val library: GameLibraryViewModel get() = vm
}

@Composable
fun rememberGameActions(vm: GameLibraryViewModel, compressVm: GameCompressViewModel, onInstallFromDisc: (String) -> Unit): GameActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalXdToast.current
    val actions = remember(vm, compressVm) { GameActions(vm, compressVm, context, scope, toast, onInstallFromDisc) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { actions.coverPicked(it) }
    actions.launchPicker = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    return actions
}

/** The sheets [actions] asks for: who plays, disc content, collections, rating, compression, cache, report. */
@Composable
fun GameActionDialogs(actions: GameActions) {
    val c = Xd.colors
    actions.playAs?.let { (game, ask) ->
        PlayAsDialog(
            profiles = ask.profiles, preselected = ask.preselected, otherPlayers = ask.otherPlayers,
            onPlay = { xuid, dontAsk -> actions.playAsChosen(game, xuid, dontAsk) },
            onDismiss = { actions.playAs = null },
            gameName = game.name,
        )
    }
    actions.discInstall?.let { (game, count, options) ->
        XdSheet(onDismiss = { actions.discInstall = null }, title = stringResource(R.string.lib_install_disc_title),
            actions = {
                XdButton(stringResource(R.string.lib_boot_anyway), { actions.discInstall = null; actions.launch(game, options) }, kind = XdButtonKind.GHOST)
                XdButton(stringResource(R.string.lib_install), { actions.installFromDisc(game) }, kind = XdButtonKind.PRIMARY)
            }) {
            Text(pluralStringResource(R.plurals.lib_install_disc_text, count, count), style = XdText.bodySm, color = c.fg2)
        }
    }
    actions.collections?.let { game -> CollectionsSheet(actions, game) }
    actions.rating?.let { game ->
        val details by actions.library.details.collectAsStateWithLifecycle()
        RatingSheet(details?.takeIf { it.identityKey == game.identityKey }?.compatibility?.latest?.status,
            onDismiss = { actions.rating = null },
            onSave = { status, note -> actions.rating = null; actions.library.rateCompatibility(game, status, note) })
    }
    actions.cacheConfirm?.let { (game, bytes) ->
        val context = LocalContext.current
        XdSheet(onDismiss = { actions.cacheConfirm = null }, title = stringResource(R.string.lib_shader_cache_title),
            actions = {
                XdButton(stringResource(R.string.common_cancel), { actions.cacheConfirm = null }, kind = XdButtonKind.GHOST)
                XdButton(stringResource(R.string.lib_shader_cache_action), { actions.clearCache(game) }, kind = XdButtonKind.PRIMARY)
            }) {
            Text(stringResource(R.string.lib_shader_cache_text, android.text.format.Formatter.formatShortFileSize(context, bytes)),
                style = XdText.bodySm, color = c.fg2)
        }
    }
    actions.compressConfirm?.let { game ->
        XdSheet(onDismiss = { actions.compressConfirm = null }, title = stringResource(R.string.lib_compress_title),
            actions = {
                XdButton(stringResource(R.string.common_cancel), { actions.compressConfirm = null }, kind = XdButtonKind.GHOST)
                XdButton(stringResource(R.string.lib_compress_action), { actions.startCompress(game) }, kind = XdButtonKind.PRIMARY)
            }) {
            Text(stringResource(R.string.lib_compress_text), style = XdText.bodySm, color = c.fg2)
        }
    }
    val compress by actions.compressor.state.collectAsStateWithLifecycle()
    when (val s = compress) {
        is CompressState.Busy -> XdSheet(onDismiss = {}, title = s.message, dismissible = false) {
            if (s.progress >= 0f) {
                XdBar(s.progress)
                Text(stringResource(R.string.lib_compress_progress, (s.progress * 100).toInt()), style = XdText.small, color = c.fg3)
            } else {
                XdBar(0.15f)
                Text(stringResource(R.string.lib_may_take_while), style = XdText.small, color = c.fg3)
            }
        }
        // Dismissing keeps it: a stray tap outside must never delete the .iso.
        is CompressState.ConfirmDelete -> XdSheet(onDismiss = actions.compressor::keepIso, title = stringResource(R.string.lib_delete_iso_title),
            actions = {
                XdButton(stringResource(R.string.lib_keep_it), actions.compressor::keepIso, kind = XdButtonKind.GHOST)
                XdButton(stringResource(R.string.lib_delete_iso), actions.compressor::deleteIso, kind = XdButtonKind.DANGER)
            }) {
            Text(stringResource(R.string.lib_delete_iso_text, s.zarName, s.isoName, formatSize(s.isoBytes)), style = XdText.bodySm, color = c.fg2)
        }
        is CompressState.Done -> XdSheet(onDismiss = { actions.compressor.dismiss(); actions.library.refresh() }, title = stringResource(R.string.common_done),
            actions = { XdButton(stringResource(R.string.common_ok), { actions.compressor.dismiss(); actions.library.refresh() }, kind = XdButtonKind.PRIMARY) }) {
            XdNote(s.message, tone = NoteTone.OK)
        }
        is CompressState.Failed -> XdSheet(onDismiss = actions.compressor::dismiss, title = stringResource(R.string.common_failed),
            actions = { XdButton(stringResource(R.string.common_ok), actions.compressor::dismiss, kind = XdButtonKind.PRIMARY) }) {
            XdNote(s.message, tone = NoteTone.ERROR)
        }
        else -> {}
    }
    actions.report?.let { shown ->
        RunReportDialog(shown, actions.reportBusy, onShare = { actions.shareReport(shown) }, onDismiss = { actions.report = null })
    }
}

@Composable
private fun CollectionsSheet(actions: GameActions, game: Game) {
    val c = Xd.colors
    val collections by actions.library.collections.collectAsStateWithLifecycle()
    var newName by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    XdSheet(onDismiss = { actions.collections = null }, title = stringResource(R.string.lib_collections), subtitle = game.name,
        actions = { XdButton(stringResource(R.string.xd_done), { actions.collections = null }, kind = XdButtonKind.PRIMARY) }) {
        if (collections.isEmpty()) Text(stringResource(R.string.col_none), style = XdText.bodySm, color = c.fg3)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (collection in collections) {
                val member = game.identityKey in collection.members
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XdMenuItem(
                        collection.name,
                        onClick = { actions.editCollectionsWith(game) { GameCollections.setMember(it, collection.name, game.identityKey, !member) } },
                        icon = if (member) XdIcons.check else XdIcons.plus,
                        subtitle = pluralStringResource(R.plurals.xd_lib_games, collection.members.size, collection.members.size),
                        tint = if (member) c.acc else null,
                        modifier = Modifier.weight(1f),
                    )
                    xendroid.compose.ui.design.XdIconButton(XdIcons.trash, stringResource(R.string.common_delete), { confirmDelete = collection.name })
                }
            }
        }
        if (collections.size < GameCollections.MAX_COLLECTIONS) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XdTextInput(newName, { newName = it.take(GameCollections.MAX_NAME) }, Modifier.weight(1f),
                    placeholder = stringResource(R.string.col_new), mono = false, width = 400.dp)
                XdButton(stringResource(R.string.col_create), {
                    actions.editCollectionsWith(game) { GameCollections.create(it, newName, game.identityKey) }
                    newName = ""
                }, enabled = GameCollections.cleanName(newName) != null, icon = XdIcons.plus)
            }
        }
        Text(stringResource(R.string.col_note), style = XdText.small, color = c.fg3)
    }
    confirmDelete?.let { name ->
        XdSheet(onDismiss = { confirmDelete = null }, title = stringResource(R.string.col_delete_title, name),
            actions = {
                XdButton(stringResource(R.string.common_cancel), { confirmDelete = null }, kind = XdButtonKind.GHOST)
                XdButton(stringResource(R.string.common_delete), {
                    confirmDelete = null
                    actions.editCollectionsWith(game) { GameCollections.delete(it, name) }
                }, kind = XdButtonKind.DANGER)
            }) { Text(stringResource(R.string.col_delete_text), style = XdText.bodySm, color = c.fg2) }
    }
}

/** "How does this game run?": the five results as pills, a note; kept with this build and driver. */
@Composable
fun RatingSheet(current: CompatStatus?, onDismiss: () -> Unit, onSave: (CompatStatus, String) -> Unit) {
    val c = Xd.colors
    var selected by rememberSaveable { mutableStateOf(current) }
    var note by rememberSaveable { mutableStateOf("") }
    XdSheet(onDismiss = onDismiss, title = stringResource(R.string.rate_title), subtitle = stringResource(R.string.xd_rate_sub),
        actions = {
            XdButton(stringResource(R.string.common_cancel), onDismiss, kind = XdButtonKind.GHOST)
            XdButton(stringResource(R.string.common_save), { selected?.let { onSave(it, note) } }, kind = XdButtonKind.PRIMARY, enabled = selected != null)
        }) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (status in CompatStatus.entries.reversed()) RatingRow(status, selected == status) { selected = status }
        }
        XdTextInput(note, { if (it.length <= 500) note = it }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.rate_notes),
            mono = false, width = 520.dp)
        Text(stringResource(R.string.rate_note), style = XdText.small, color = c.fg3)
    }
}

@Composable
private fun RatingRow(status: CompatStatus, selected: Boolean, onClick: () -> Unit) {
    val c = Xd.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        XdMenuItem(compatStatusText(status), onClick = onClick, icon = if (selected) XdIcons.checkCircle else XdIcons.disc,
            tint = if (selected) c.acc else null, modifier = Modifier.weight(1f))
        XdStatusPill(compatShortText(status), status.tone())
    }
}

/** The short name of a rating (cards, grid). */
@Composable
fun compatShortText(status: CompatStatus?): String = when (status) {
    null -> stringResource(R.string.xd_compat_none)
    CompatStatus.PLAYABLE -> stringResource(R.string.compat_playable)
    CompatStatus.IN_GAME -> stringResource(R.string.xd_compat_ingame_short)
    CompatStatus.INTRO -> stringResource(R.string.xd_compat_intro_short)
    CompatStatus.BOOTS -> stringResource(R.string.xd_compat_boots_short)
    CompatStatus.NOTHING -> stringResource(R.string.xd_compat_nothing_short)
}

internal fun formatSize(b: Long): String {
    if (b < 1024) return "$b B"
    val u = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = -1
    do { v /= 1024.0; i++ } while (v >= 1024.0 && i < u.lastIndex)
    return "%.1f %s".format(v, u[i])
}
