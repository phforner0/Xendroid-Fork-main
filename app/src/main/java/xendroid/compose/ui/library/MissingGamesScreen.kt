package xendroid.compose.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import xendroid.compose.R
import xendroid.compose.data.MissingTitle
import xendroid.compose.data.MissingTitles
import xendroid.compose.sessions.TitleActivity
import xendroid.compose.sessions.formatPlayTime
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
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
 * L06, lote 6: the games played or seen before that the last scan did not list, as a screen:
 * the kept cover, why it is missing, how much it was played, the path it had. A game outside the
 * game folders can have its folder added; one that moved can be pointed to where it is now.
 * "Remove from the list" only hides it here: play time, compatibility notes, saves and cover are
 * kept, and it comes back by itself when the file is found again. No file is touched.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MissingGamesScreen(
    missing: List<MissingTitle>,
    activity: Map<String, TitleActivity>,
    coverOf: (String) -> Any,
    onRemove: (MissingTitle) -> Unit,
    onAddFolderOf: (MissingTitle) -> Unit,
    onFind: (MissingTitle) -> Unit,
    onFolders: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val c = Xd.colors
    XdSingleScreen(
        title = stringResource(R.string.missing_title),
        subtitle = gamesText(missing.size),
        onBack = onBack,
        headIcon = XdIcons.inbox,
        actions = { XdButton(stringResource(R.string.lib_menu_folders), onFolders, kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.folder) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (missing.isEmpty()) XdEmpty(stringResource(R.string.missing_none))
            else BoxWithConstraints {
                val narrow = maxWidth < 560.dp
                XdCard(Modifier.fillMaxWidth()) {
                    Column {
                        missing.forEachIndexed { i, title ->
                            MissingRow(title, activity[title.titleId], coverOf, narrow, onRemove, onAddFolderOf, onFind)
                            if (i < missing.lastIndex && !c.controller) HorizontalDivider(thickness = 1.dp, color = c.line)
                        }
                    }
                }
            }
            XdNote(stringResource(R.string.missing_note))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingRow(
    title: MissingTitle,
    played: TitleActivity?,
    coverOf: (String) -> Any,
    narrow: Boolean,
    onRemove: (MissingTitle) -> Unit,
    onAddFolderOf: (MissingTitle) -> Unit,
    onFind: (MissingTitle) -> Unit,
) {
    val c = Xd.colors
    val actions: @Composable () -> Unit = {
        when (title.reason) {
            MissingTitles.Reason.OUTSIDE_FOLDERS -> XdButton(stringResource(R.string.xd_ms_add_folder), { onAddFolderOf(title) }, size = XdButtonSize.SM)
            MissingTitles.Reason.FILE_GONE -> XdButton(stringResource(R.string.xd_ms_find), { onFind(title) }, size = XdButtonSize.SM)
            MissingTitles.Reason.FOLDER_AWAY -> Unit
        }
        XdButton(stringResource(R.string.xd_ms_remove), { onRemove(title) }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AsyncImage(coverOf(title.titleId), null, Modifier.width(44.dp).aspectRatio(0.75f).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title.name, style = XdText.label, color = c.fg)
            XdNote(reasonText(title.reason), tone = if (title.reason == MissingTitles.Reason.FOLDER_AWAY) NoteTone.WARN else NoteTone.MUTED)
            played?.let {
                Text(pluralStringResource(R.plurals.missing_played, it.runs, formatPlayTime(it.playedMs), it.runs, title.titleId),
                    style = XdText.small, color = c.fg3)
            }
            Text(displayPath(title.lastPath), style = XdText.small, color = c.fg3)
            if (narrow) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
        if (!narrow) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

@Composable
private fun reasonText(reason: MissingTitles.Reason): String = when (reason) {
    MissingTitles.Reason.FILE_GONE -> stringResource(R.string.missing_file_gone)
    MissingTitles.Reason.FOLDER_AWAY -> stringResource(R.string.missing_folder_away)
    MissingTitles.Reason.OUTSIDE_FOLDERS -> stringResource(R.string.missing_outside)
}
