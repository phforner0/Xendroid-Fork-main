package xendroid.compose.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xendroid.compose.R
import xendroid.compose.data.FolderGames
import xendroid.compose.data.Game
import xendroid.compose.ui.design.BadgeTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdBadge
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdEmpty
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSingleScreen
import xendroid.compose.ui.design.XdText

/**
 * L03, lote 6: the folders the library looks in, as a screen: how many games each holds, which
 * receives full-game installs (the first; "Install here" moves another to the top), which are not
 * available now; add one through the folder browser, look again. Removing one only stops looking
 * in it: no file is moved or deleted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameFoldersScreen(
    folders: List<String>,
    unavailable: List<String>,
    games: List<Game>,
    scanning: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onMakeInstallFolder: (String) -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit,
    /** Round 2: makes XenDroid/<games>, /TU and /DLC and adds them; null where it cannot. */
    onCreateStandard: (() -> Unit)? = null,
    /** The standard games folder, to tell whether it is in the list already. */
    standardGames: String? = null,
) {
    BackHandler(onBack = onBack)
    val c = Xd.colors
    // The library's games under each folder (its own walk found them); a folder inside another
    // is walked once, so its games count for both here.
    val counts = folders.associateWith { folder ->
        val root = folder.trimEnd('/') + "/"
        games.count { it.launchUri.startsWith(root) }
    }
    XdSingleScreen(
        title = stringResource(R.string.lib_menu_folders),
        subtitle = pluralStringResource(R.plurals.xd_fd_folders, folders.size, folders.size),
        onBack = onBack,
        headIcon = XdIcons.folder,
        actions = { XdButton(stringResource(R.string.folders_add), onAdd, kind = XdButtonKind.PRIMARY, size = XdButtonSize.SM, icon = XdIcons.plus) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val total = games.size
                Text(stringResource(R.string.xd_fd_summary, gamesText(total), pluralStringResource(R.plurals.xd_fd_folders, folders.size, folders.size))
                    .replaceFirstChar { it.uppercase() }, style = XdText.note, color = c.fg3, modifier = Modifier.weight(1f))
                XdButton(stringResource(if (scanning) R.string.xd_fd_scanning else R.string.xd_cm_look_again), onRescan,
                    kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.refresh, enabled = !scanning)
            }
            if (folders.isEmpty()) XdEmpty(stringResource(R.string.folders_none)) {
                XdButton(stringResource(R.string.folders_add), onAdd, kind = XdButtonKind.PRIMARY, size = XdButtonSize.SM, icon = XdIcons.plus)
            }
            else BoxWithConstraints {
                val narrow = maxWidth < 560.dp
                XdCard(Modifier.fillMaxWidth()) {
                    Column {
                        folders.forEachIndexed { i, folder ->
                            FolderRow(folder, install = i == 0, away = folder in unavailable, games = counts[folder] ?: 0, narrow = narrow,
                                onRemove = { onRemove(folder) }, onInstallHere = { onMakeInstallFolder(folder) })
                            if (i < folders.lastIndex && !c.controller) HorizontalDivider(thickness = 1.dp, color = c.line)
                        }
                    }
                }
            }
            if (onCreateStandard != null && standardGames != null && folders.none { it.trimEnd('/') == standardGames }) {
                XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_fd_std), icon = XdIcons.folder) {
                    Text(stringResource(R.string.xd_fd_std_note, File(standardGames).name), style = XdText.note, color = c.fg3)
                    XdButton(stringResource(R.string.xd_fd_std), onCreateStandard, size = XdButtonSize.SM, icon = XdIcons.plus)
                }
            }
            XdNote(stringResource(R.string.folders_note))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderRow(
    path: String,
    install: Boolean,
    away: Boolean,
    games: Int,
    narrow: Boolean,
    onRemove: () -> Unit,
    onInstallHere: () -> Unit,
) {
    val c = Xd.colors
    val actions: @Composable () -> Unit = {
        if (!install && !away) XdButton(stringResource(R.string.xd_fd_install_here), onInstallHere, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
        XdButton(stringResource(R.string.common_remove), onRemove, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(XdIcons.folder, null, Modifier.size(24.dp), tint = if (away) c.warn else c.fg3)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(displayPath(path), style = XdText.label, color = if (away) c.fg2 else c.fg)
            if (install || away) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (install) XdBadge(stringResource(R.string.folders_installs_here), tone = BadgeTone.ACCENT)
                if (away) XdBadge(stringResource(R.string.folders_unavailable), tone = BadgeTone.WARN)
            }
            Text(if (away) stringResource(R.string.xd_fd_unavailable_detail) else gamesText(games).replaceFirstChar { it.uppercase() },
                style = XdText.small, color = c.fg3)
            if (narrow) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
        if (!narrow) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

/** "3 jogos", or "nenhum jogo": Portuguese plural rules put 0 with 1 ("0 jogo"). */
@Composable
fun gamesText(n: Int): String = if (n == 0) stringResource(R.string.xd_fd_no_games) else pluralStringResource(R.plurals.xd_cm_games, n, n)

/** How many games [dir] holds, counted in the background (null while counting). */
@Composable
fun folderGameCount(dir: File): FolderGames.Count? {
    val count by produceState<FolderGames.Count?>(null, dir.absolutePath) {
        value = withContext(Dispatchers.IO) { runCatching { FolderGames.count(dir) }.getOrNull() }
    }
    return count
}
