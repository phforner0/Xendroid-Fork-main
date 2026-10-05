package xendroid.compose.ui.library

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import xendroid.compose.R
import xendroid.compose.ui.design.BadgeTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdBadge
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdChip
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdSingleScreen
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTextInput
import xendroid.compose.ui.design.focusRing

/**
 * Lote 6: the app's own folder browser (java.io.File; the SAF picker is refused by some ROMs),
 * for REAL-PATH (All Files Access) mode. The storage roots as chips (internal storage, SD cards
 * and USB, API 30+), the path as steps to go back several levels at once, "Up", the folders with
 * how many games each holds (counted in the background), and at the bottom how many games this
 * folder holds with "Use this folder".
 *
 * [title] and [hint] say what is being chosen when it is not a games folder or a package file.
 *
 * Two modes (the caller picks via which callback it passes):
 *  - folder-pick: [onFolderChosen] non-null -> "Use this folder" confirms the current directory;
 *  - file-pick: [onFileChosen] non-null -> files are listed too and tapping one returns its
 *    absolute path (used by "Install content" to pick a package).
 *
 * listFiles() may return null for an unreadable directory (e.g. Android/data) even with All
 * Files Access: that is "no entries", never a crash. The system back (and a controller's B)
 * goes one folder up, and out of the browser from its top.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderBrowserScreen(
    onFolderChosen: ((path: String) -> Unit)? = null,
    onFileChosen: ((path: String) -> Unit)? = null,
    onCancel: () -> Unit,
    start: File? = null,
    title: String? = null,
    hint: String? = null,
) {
    val context = LocalContext.current
    val c = Xd.colors
    val roots = rememberStorageRoots()
    var current by remember { mutableStateOf(start?.takeIf { it.isDirectory } ?: roots.first().dir) }
    val root = roots.filter { current.absolutePath.startsWith(it.dir.absolutePath) }.maxByOrNull { it.dir.absolutePath.length } ?: roots.first()
    val atRoot = current.absolutePath == root.dir.absolutePath

    // Round 2: "New folder" names one here; the list is read again once it exists.
    var naming by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    val subDirs = remember(current.absolutePath, refresh) {
        current.listFiles()?.filter { it.isDirectory && !it.isHidden }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    val files = remember(current.absolutePath) {
        if (onFileChosen == null) emptyList()
        else current.listFiles()?.filter { it.isFile && !it.isHidden }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    val goUp: () -> Unit = { if (atRoot) onCancel() else current = current.parentFile ?: root.dir }
    BackHandler(onBack = goUp)
    if (naming) NewFolderSheet(current, onDismiss = { naming = false }) { made -> naming = false; refresh++; current = made }

    // The path from the root: the root's name, then each folder below it.
    val crumbs = remember(current.absolutePath, root) {
        val below = current.absolutePath.removePrefix(root.dir.absolutePath).split('/').filter { it.isNotEmpty() }
        listOf(root.label to root.dir) + below.runningFold(root.dir) { dir, name -> File(dir, name) }.drop(1).map { it.name to it }
    }
    val picksFile = onFileChosen != null && onFolderChosen == null
    // The place is in the steps below the title; the subtitle says what to do.
    XdSingleScreen(title = title ?: stringResource(if (picksFile) R.string.xd_br_title_file else R.string.fr_choose_folder),
        subtitle = hint ?: stringResource(if (picksFile) R.string.xd_br_pick_note else R.string.xd_br_folder_note),
        onBack = onCancel, headIcon = XdIcons.folder, scroll = false, showNav = false) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (roots.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                roots.forEach { r -> XdChip(r.label, r == root, { current = r.dir }, icon = if (r.removable) XdIcons.sd else XdIcons.phone) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                crumbs.forEachIndexed { i, (name, dir) ->
                    if (i > 0) Icon(XdIcons.chevR, null, Modifier.size(14.dp), tint = c.fg3)
                    val shape = RoundedCornerShape(8.dp)
                    Text(name, style = XdText.labelSm, color = if (i == crumbs.lastIndex) c.fg else c.fg2, maxLines = 1,
                        modifier = Modifier.focusRing(shape).clip(shape).clickable(role = Role.Button) { current = dir }
                            .padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.s1)) {
                if (!atRoot) item(key = "..") { BrowserRow(XdIcons.back, stringResource(R.string.browse_up), null, divider = true) { goUp() } }
                items(subDirs, key = { it.absolutePath }) { dir ->
                    val count = folderGameCount(dir)
                    BrowserRow(XdIcons.folder, dir.name, divider = true, badge = count?.takeIf { it.games > 0 }?.let { n ->
                        if (n.partial) stringResource(R.string.xd_fd_games_more, n.games) else pluralStringResource(R.plurals.xd_cm_games, n.games, n.games)
                    }) { current = dir }
                }
                if (onFileChosen != null) items(files, key = { it.absolutePath }) { file ->
                    BrowserRow(XdIcons.box, file.name, detail = Formatter.formatShortFileSize(context, file.length()), divider = true) {
                        onFileChosen(file.absolutePath)
                    }
                }
                if (subDirs.isEmpty() && files.isEmpty()) item(key = "empty") {
                    Text(stringResource(if (onFolderChosen != null) R.string.browse_no_subfolders else R.string.browse_nothing),
                        style = XdText.note, color = c.fg3, modifier = Modifier.padding(16.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // How many games this folder holds matters when choosing a games folder, not a package file.
                if (picksFile) Box(Modifier.weight(1f)) else {
                    val here = folderGameCount(current)
                    Text(when {
                        here == null -> stringResource(R.string.xd_fd_counting)
                        here.games == 0 -> stringResource(R.string.xd_br_none_here)
                        else -> stringResource(R.string.xd_br_here, if (here.partial) stringResource(R.string.xd_fd_games_more, here.games)
                            else pluralStringResource(R.plurals.xd_cm_games, here.games, here.games))
                    }, style = XdText.note, color = c.fg3, modifier = Modifier.weight(1f))
                }
                // Round 2: a folder that does not exist yet, made here and opened.
                if (onFolderChosen != null) XdButton(stringResource(R.string.xd_br_new_folder), { naming = true }, kind = XdButtonKind.GHOST,
                    icon = XdIcons.plus, enabled = current.canWrite())
                XdButton(stringResource(R.string.common_cancel), onCancel, kind = XdButtonKind.GHOST)
                if (onFolderChosen != null) XdButton(stringResource(R.string.browse_use_folder), { onFolderChosen(current.absolutePath) },
                    kind = XdButtonKind.PRIMARY, icon = XdIcons.check)
            }
        }
    }
}

/** Round 2: names a new folder inside [parent] and makes it; [onMade] opens it. */
@Composable
private fun NewFolderSheet(parent: File, onDismiss: () -> Unit, onMade: (File) -> Unit) {
    val c = Xd.colors
    var name by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<Int?>(null) }
    val create = {
        val clean = name.trim()
        when {
            clean.isEmpty() -> {}
            '/' in clean || clean == "." || clean == ".." -> problem = R.string.xd_br_new_folder_bad_name
            else -> {
                val dir = File(parent, clean)
                if (dir.isDirectory || dir.mkdirs()) onMade(dir) else problem = R.string.xd_br_new_folder_failed
            }
        }
    }
    XdSheet(onDismiss = onDismiss, title = stringResource(R.string.xd_br_new_folder_title, parent.name.ifEmpty { parent.path }), actions = {
        XdButton(stringResource(R.string.common_cancel), onDismiss, kind = XdButtonKind.GHOST)
        XdButton(stringResource(R.string.xd_br_new_folder_create), create, kind = XdButtonKind.PRIMARY, icon = XdIcons.check,
            enabled = name.isNotBlank())
    }) {
        XdTextInput(name, { name = it; problem = null }, placeholder = stringResource(R.string.xd_br_new_folder_name), width = 320.dp)
        problem?.let { Text(stringResource(it), style = XdText.note, color = c.errText) }
    }
}

@Composable
private fun BrowserRow(icon: ImageVector, name: String, detail: String? = null, divider: Boolean, badge: String? = null, onClick: () -> Unit) {
    val c = Xd.colors
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (c.controller) 54.dp else 48.dp).focusRing(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = c.fg3)
            Text(name, style = XdText.label, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (badge != null) XdBadge(badge, tone = BadgeTone.ACCENT)
            if (detail != null) Text(detail, style = XdText.small, color = c.fg3)
        }
        if (divider) HorizontalDivider(Modifier.padding(start = 50.dp), thickness = 1.dp, color = c.line)
    }
}
