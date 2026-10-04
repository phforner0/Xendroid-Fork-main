package xendroid.compose.ui.saves

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import xendroid.compose.R
import xendroid.compose.saves.BackupSync
import xendroid.compose.saves.SaveEntry
import xendroid.compose.saves.SaveProfile
import xendroid.compose.ui.design.BadgeTone
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdBadge
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdChip
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdKv
import xendroid.compose.ui.design.XdListRow
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSection
import xendroid.compose.ui.design.XdSectionedScreen
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdSheetOption
import xendroid.compose.ui.design.XdSwitch
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.focusRing
import xendroid.compose.ui.profile.ProfileAvatar

object SavesSections {
    const val HERE = "here"
    const val SYNC = "sync"
}

/**
 * A game's saves (lote 4): by profile, with gamertag and avatar, size and last save, and the saves
 * by the names the game gave them; export the chosen ones, import a backup; the optional sync
 * folder in its own section, with its backups listed; a review before anything is restored.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveManagerScreen(vm: SaveManagerViewModel, gameName: String, onBack: () -> Unit, art: Any? = null, initialSection: String? = null) {
    val context = LocalContext.current
    val toast = LocalXdToast.current
    val scope = rememberCoroutineScope()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val owners by vm.owners.collectAsStateWithLifecycle()
    val saves by vm.saves.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    var includeProfiles by rememberSaveable { mutableStateOf(true) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var opened by rememberSaveable { mutableStateOf(listOf<String>()) }
    var overwriteProfiles by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(initialSection ?: SavesSections.HERE) }
    var folder by remember { mutableStateOf(BackupSync.configured(context, vm.titleId)) }
    var syncAutomatic by remember { mutableStateOf(BackupSync.automatic(context, vm.titleId)) }
    var remoteBackups by remember { mutableStateOf<List<DocumentFile>?>(null) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching { BackupSync.configure(context, vm.titleId, uri); folder = uri; remoteBackups = null }
            .onSuccess { toast.show(context.getString(R.string.xd_sv_folder_chosen)) }
            .onFailure { toast.show(context.getString(R.string.sv_folder_permission)) }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri, selectedIds, includeProfiles)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.import(uri) }
    val busy = operation is SaveManagerViewModel.Operation.Busy
    BackHandler(enabled = busy) { }
    val title = gameName.ifEmpty { vm.titleId }

    val sections = listOf(
        XdSection(SavesSections.HERE, stringResource(R.string.xd_sv_sec_here), XdIcons.save, badge = profiles.size.takeIf { it > 0 }?.toString(),
            heading = stringResource(R.string.xd_sv_here_title)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                XdNote(stringResource(R.string.sv_intro), tone = NoteTone.INFO)
                if (profiles.isEmpty()) Text(stringResource(R.string.sv_none), style = XdText.note, color = Xd.colors.fg3)
                else XdCard(Modifier.fillMaxWidth()) {
                    profiles.forEachIndexed { i, p ->
                        SaveOwnerRow(p, owners[p.xuid], saves[p.xuid].orEmpty(), selected = p.xuid in selectedIds, open = p.xuid in opened,
                            enabled = !busy, last = i == profiles.lastIndex,
                            onSelect = { selectedIds = if (p.xuid in selectedIds) selectedIds - p.xuid else selectedIds + p.xuid },
                            onOpen = { opened = if (p.xuid in opened) opened - p.xuid else opened + p.xuid })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    XdChip(stringResource(R.string.sv_include_profile), includeProfiles, { includeProfiles = !includeProfiles },
                        icon = if (includeProfiles) XdIcons.check else XdIcons.plus)
                    XdButton(if (selectedIds.isEmpty()) stringResource(R.string.sv_export) else stringResource(R.string.xd_sv_export_n, selectedIds.size),
                        { create.launch("xendroid-saves-${vm.titleId}.zip") }, kind = XdButtonKind.PRIMARY, size = XdButtonSize.SM, icon = XdIcons.upload,
                        enabled = selectedIds.isNotEmpty() && !busy)
                    XdButton(stringResource(R.string.sv_import), { pick.launch(arrayOf("application/zip", "application/octet-stream")) },
                        kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.download, enabled = !busy)
                }
            }
        },
        XdSection(SavesSections.SYNC, stringResource(R.string.xd_sv_sec_sync), XdIcons.cloud, heading = stringResource(R.string.sv_sync_title)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                XdNote(stringResource(R.string.sv_sync_note), tone = NoteTone.INFO)
                val name = remember(folder) { folder?.let { runCatching { DocumentFile.fromTreeUri(context, it)?.name }.getOrNull() ?: it.lastPathSegment } }
                XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_sv_folder), icon = XdIcons.cloud) {
                    if (folder == null) Text(stringResource(R.string.xd_sv_no_folder), style = XdText.body, color = Xd.colors.fg2)
                    else {
                        Text(name.orEmpty(), style = XdText.label, color = Xd.colors.fg)
                        val last = BackupSync.lastPublished(context, vm.titleId)
                        XdKv(listOf(stringResource(R.string.xd_sv_last_sync) to (last?.let {
                            DateUtils.getRelativeDateTimeString(context, it, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
                        } ?: stringResource(R.string.xd_sv_never))))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        XdButton(stringResource(if (folder == null) R.string.sv_sync_folder else R.string.xd_sv_change_folder), { pickFolder.launch(null) },
                            kind = if (folder == null) XdButtonKind.SECONDARY else XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.folder, enabled = !busy)
                        if (folder != null) XdButton(stringResource(R.string.sv_sync_now), vm::synchronize, size = XdButtonSize.SM, icon = XdIcons.refresh, enabled = !busy)
                    }
                }
                if (folder != null) {
                    XdSheetOption(stringResource(R.string.xd_sv_auto), subtitle = stringResource(R.string.xd_sv_auto_desc)) {
                        XdSwitch(syncAutomatic, { syncAutomatic = it; BackupSync.setAutomatic(context, vm.titleId, it) }, enabled = !busy)
                    }
                    XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_sv_backups), icon = XdIcons.layers,
                        trailing = remoteBackups?.size?.toString()) {
                        val list = remoteBackups
                        when {
                            list == null -> XdButton(stringResource(R.string.xd_sv_list_backups), {
                                scope.launch {
                                    runCatching { BackupSync.remoteBackups(context, vm.titleId) }
                                        .onSuccess { remoteBackups = it }
                                        .onFailure { toast.show(context.getString(R.string.sv_provider_list_failed)) }
                                }
                            }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM, enabled = !busy)
                            list.isEmpty() -> Text(stringResource(R.string.sv_provider_none), style = XdText.note, color = Xd.colors.fg3)
                            else -> list.forEachIndexed { i, file ->
                                XdListRow(file.name?.take(64).orEmpty(), subtitle = Formatter.formatShortFileSize(context, file.length()),
                                    icon = XdIcons.zip, divider = i < list.lastIndex) {
                                    XdButton(stringResource(R.string.xd_sv_restore_ellipsis), { vm.import(file.uri) }, size = XdButtonSize.SM, enabled = !busy)
                                }
                            }
                        }
                    }
                }
            }
        },
    )
    val count = pluralStringResource(R.plurals.xd_sv_profiles, profiles.size, profiles.size)
    XdSectionedScreen(
        title = stringResource(R.string.sv_title, title), sections = sections, selected = section, onSelect = { section = it },
        onBack = if (busy) null else onBack, subtitle = "${vm.titleId} · $count", art = art, headIcon = XdIcons.save,
        lead = art?.let { a -> { AsyncImage(a, null, Modifier.width(40.dp).aspectRatio(0.75f).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop) } },
    )

    when (val op = operation) {
        is SaveManagerViewModel.Operation.Busy -> XdSheet(onDismiss = {}, title = null, dismissible = false) {
            Text(op.message, style = XdText.label, color = Xd.colors.fg)
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Xd.colors.acc, trackColor = Xd.colors.s3)
        }
        is SaveManagerViewModel.Operation.Result -> if (op.success) LaunchedEffect(op) { toast.show(op.message); vm.dismiss() }
            else XdSheet(onDismiss = vm::dismiss, title = stringResource(R.string.sv_failed),
                actions = { XdButton(stringResource(R.string.common_ok), vm::dismiss, kind = XdButtonKind.PRIMARY) }) {
                Text(op.message, style = XdText.body, color = Xd.colors.fg2)
            }
        is SaveManagerViewModel.Operation.Review -> {
            val manifest = op.backup.manifest
            val names = manifest.xuids.map { x -> owners[x.uppercase()]?.gamertag?.let { "$it ($x)" } ?: x }
            XdSheet(onDismiss = vm::dismiss, title = stringResource(R.string.xd_sv_restore_from, title), actions = {
                XdButton(stringResource(R.string.common_cancel), vm::dismiss, kind = XdButtonKind.GHOST)
                XdButton(stringResource(R.string.prof_restore), { vm.restore(overwriteProfiles) }, kind = XdButtonKind.PRIMARY)
            }) {
                XdCard(Modifier.fillMaxWidth()) {
                    Text(pluralStringResource(R.plurals.sv_restore_files, manifest.files.size, manifest.files.size, names.joinToString()),
                        style = XdText.body, color = Xd.colors.fg)
                    Text(pluralStringResource(R.plurals.sv_restore_conflicts, op.backup.conflicts.size, op.backup.conflicts.size),
                        style = XdText.body, color = Xd.colors.fg2)
                }
                XdSheetOption(stringResource(R.string.sv_replace_profiles)) { XdSwitch(overwriteProfiles, { overwriteProfiles = it }) }
            }
        }
        else -> Unit
    }
}

/** One profile's saves of this game: choose it for export, see its saves by name. */
@Composable
private fun SaveOwnerRow(
    p: SaveProfile,
    owner: SaveManagerViewModel.Owner?,
    entries: List<SaveEntry>,
    selected: Boolean,
    open: Boolean,
    enabled: Boolean,
    last: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    val c = Xd.colors
    val context = LocalContext.current
    // A trashed profile's gamertag sits in its (encrypted) account file: it is named by where it is.
    val name = owner?.gamertag ?: stringResource(if (owner?.inTrash == true) R.string.xd_sv_trashed_profile else R.string.xd_sv_unknown_profile)
    val newest = entries.maxOfOrNull { it.lastModified }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CheckBox(selected, enabled, stringResource(R.string.xd_sv_choose, name), onSelect)
            ProfileAvatar(p.xuid, owner?.gamertag.orEmpty(), owner?.hasAvatar == true, 36.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, style = XdText.label, color = if (owner != null) c.fg else c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (owner?.inTrash == true && owner.gamertag != null) XdBadge(stringResource(R.string.xd_sv_in_trash), tone = BadgeTone.WARN)
                }
                Text(listOfNotNull(p.xuid, pluralStringResource(R.plurals.xd_pf_files, p.saveFiles, p.saveFiles), Formatter.formatShortFileSize(context, p.bytes),
                    newest?.let { stringResource(R.string.xd_sv_last_save, DateUtils.getRelativeTimeSpanString(it).toString()) }).joinToString(" · "),
                    style = XdText.small.copy(fontSize = 12.sp), color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            XdButton(stringResource(if (open) R.string.xd_sv_hide else R.string.xd_sv_show), onOpen, kind = XdButtonKind.GHOST, size = XdButtonSize.SM,
                enabled = entries.isNotEmpty())
        }
        if (open) Column(Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entries.forEach { e ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.s2).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(e.displayName ?: e.file, style = XdText.bodySm, color = c.fg, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(DateUtils.getRelativeTimeSpanString(e.lastModified).toString() + " · " + Formatter.formatShortFileSize(context, e.bytes),
                        style = XdText.small, color = c.fg3, maxLines = 1)
                }
            }
        }
        if (!last) androidx.compose.material3.HorizontalDivider(thickness = 1.dp, color = c.line)
    }
}

@Composable
private fun CheckBox(checked: Boolean, enabled: Boolean, description: String, onToggle: () -> Unit) {
    val c = Xd.colors
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.size(24.dp).focusRing(shape).clip(shape).background(if (checked) c.acc else c.s3)
            .then(if (!checked) Modifier.border(1.5.dp, c.line2, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(XdIcons.check, null, Modifier.size(16.dp), tint = c.onAcc)
    }
}
