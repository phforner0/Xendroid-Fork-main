package xendroid.compose.gamepad

import android.content.Context
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.R
import xendroid.compose.archive.ArchiveFiles
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdIconButton
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdListRow
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTextInput

/**
 * U06: named layouts. Saving takes the layout being edited (both orientations); applying shows
 * what moves first; files go out and come in through the system picker, checked before use.
 * [onEdit] receives each change as a function of the stored layouts: the editor applies it to
 * its working copy (kept with "Save and exit"), the Controls area to the stored file at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutPresetsPanel(
    config: GamepadConfigDto,
    /** The game whose own layout is being edited, or null for the shared one. */
    editScope: String?,
    landscape: Boolean,
    onEdit: ((GamepadConfigDto) -> GamepadConfigDto) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Xd.colors
    var name by remember { mutableStateOf("") }
    var applying by remember { mutableStateOf<LayoutPresetDto?>(null) }
    var exporting by remember { mutableStateOf<LayoutPresetDto?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val preset = exporting ?: return@rememberLauncherForActivityResult
        exporting = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            onMessage(withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(LayoutPresets.encodeFile(preset).toByteArray()) }
                        ?: error("no file")
                }.fold({ context.getString(R.string.lp_exported, preset.name) },
                    { context.getString(R.string.lp_write_failed, it.message ?: it.javaClass.simpleName) })
            })
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        LayoutPresets.decodeFile(ArchiveFiles.readBounded(it, LayoutPresets.MAX_FILE_BYTES.toLong()).toString(Charsets.UTF_8))
                    } ?: LayoutPresets.Decoded.Refused(LayoutPresets.Refusal.CANNOT_OPEN)
                }.getOrElse { LayoutPresets.Decoded.Refused(LayoutPresets.Refusal.CANNOT_READ, it.message ?: it.javaClass.simpleName) }
            }
            onMessage(when (decoded) {
                is LayoutPresets.Decoded.Refused -> context.getString(R.string.lp_not_imported, refusalText(context, decoded.why, decoded.detail))
                is LayoutPresets.Decoded.Ok -> runCatching {
                    val named = decoded.preset.copy(name = LayoutPresets.uniqueName(config, decoded.preset.name))
                    LayoutPresets.save(config, named)   // refused here (full) before anything is stored
                    onEdit { LayoutPresets.save(it, named.copy(name = LayoutPresets.uniqueName(it, named.name))) }
                    context.getString(R.string.lp_imported, named.name)
                }.getOrElse { context.getString(R.string.lp_not_imported, failureText(context, it)) }
            })
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (config.presets.isEmpty()) Text(stringResource(R.string.lp_none), style = XdText.note, color = c.fg3,
            modifier = Modifier.padding(vertical = 6.dp))
        config.presets.forEach { preset ->
            val orientations = when {
                preset.landscape != null && preset.portrait != null -> stringResource(R.string.xd_lp_both)
                preset.landscape != null -> stringResource(R.string.lp_landscape_lc)
                else -> stringResource(R.string.lp_portrait_lc)
            }
            val saved = preset.savedAt.takeIf { it > 0 }?.let {
                stringResource(R.string.xd_lp_saved_at, DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH))
            }
            XdListRow(preset.name, subtitle = listOfNotNull(orientations, saved).joinToString(" · "), icon = XdIcons.hand) {
                XdButton(stringResource(R.string.lp_apply_ellipsis), { applying = preset }, size = XdButtonSize.SM)
                XdButton(stringResource(R.string.lp_export), {
                    exporting = preset
                    exportLauncher.launch("${preset.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.xdlayout.json")
                }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                XdIconButton(XdIcons.trash, stringResource(R.string.xd_lp_delete, preset.name), {
                    onEdit { LayoutPresets.delete(it, preset.name) }
                    onMessage(context.getString(R.string.lp_deleted, preset.name))
                }, size = 34.dp)
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            XdTextInput(name, { name = it.take(LayoutPresets.MAX_NAME) }, placeholder = stringResource(R.string.lp_name), mono = false, width = 220.dp)
            XdButton(stringResource(R.string.xd_lp_save_current), {
                // The change may run later (the Controls area stores it in the background): it keeps its own name.
                val wanted = name
                val clean = LayoutPresets.cleanName(wanted)
                onMessage(runCatching {
                    val captured = LayoutPresets.capture(config, editScope, wanted, System.currentTimeMillis())
                    val replaced = LayoutPresets.find(config, wanted) != null
                    LayoutPresets.save(config, captured)   // refused here before anything is stored
                    onEdit { LayoutPresets.save(it, LayoutPresets.capture(it, editScope, wanted, captured.savedAt)) }
                    name = ""
                    context.getString(if (replaced) R.string.lp_replaced else R.string.lp_saved, clean)
                }.getOrElse { failureText(context, it) })
            }, size = XdButtonSize.SM, icon = XdIcons.save, enabled = LayoutPresets.cleanName(name) != null)
            XdButton(stringResource(R.string.xd_lp_import), { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.download)
        }
    }

    applying?.let { preset ->
        val here = if (landscape) preset.landscape else preset.portrait
        val diff = here?.let { LayoutPresets.diff(config.layoutFor(editScope, landscape), it, defaultLayout(landscape)) }
        XdSheet(onDismiss = { applying = null }, title = stringResource(R.string.lp_apply_title, preset.name),
            subtitle = stringResource(if (editScope != null) R.string.lp_scope_game else R.string.lp_scope_shared),
            actions = {
                XdButton(stringResource(R.string.common_cancel), { applying = null }, kind = XdButtonKind.GHOST)
                XdButton(stringResource(R.string.lp_apply), {
                    onEdit { LayoutPresets.apply(it, preset, editScope) }
                    onMessage(context.getString(R.string.lp_applied, preset.name))
                    applying = null
                }, kind = XdButtonKind.PRIMARY)
            }) {
            Text(if (diff != null) stringResource(if (landscape) R.string.lp_diff_landscape else R.string.lp_diff_portrait, diffText(diff))
                else stringResource(if (landscape) R.string.lp_no_landscape else R.string.lp_no_portrait), style = XdText.body, color = c.fg)
            (if (landscape) preset.portrait else preset.landscape)?.let {
                Text(stringResource(if (landscape) R.string.lp_other_portrait else R.string.lp_other_landscape), style = XdText.body, color = c.fg2)
            }
        }
    }
}

/** The editor's Layouts: the panel in a sheet; what it changes is kept with "Save and exit". */
@Composable
fun LayoutPresetsDialog(
    config: GamepadConfigDto,
    editScope: String?,
    landscape: Boolean,
    onChange: (GamepadConfigDto) -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    XdSheet(onDismiss = onDismiss, title = stringResource(R.string.lp_title), wide = true,
        subtitle = stringResource(if (editScope != null) R.string.lp_scope_game else R.string.lp_scope_shared),
        actions = { XdButton(stringResource(R.string.xd_done), onDismiss, kind = XdButtonKind.PRIMARY) }) {
        LayoutPresetsPanel(config, editScope, landscape, onEdit = { onChange(it(config)) }, onMessage = onMessage)
        Text(stringResource(R.string.lp_kept_note), style = XdText.note, color = Xd.colors.fg3)
    }
}

/** U02: what applying changes, in the shown language. */
@Composable
private fun diffText(diff: LayoutPresets.Diff): String {
    val parts = listOfNotNull(
        diff.moved.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.lp_diff_moved, it, it) },
        diff.resized.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.lp_diff_resized, it, it) },
        diff.shown.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.lp_diff_shown, it, it) },
        diff.hidden.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.lp_diff_hidden, it, it) },
    )
    return if (parts.isEmpty()) stringResource(R.string.lp_diff_nothing) else stringResource(R.string.lp_diff_controls, parts.joinToString(", "))
}

/** U02: why a layout is refused, in the shown language. */
private fun refusalText(context: Context, why: LayoutPresets.Refusal, detail: String): String = when (why) {
    LayoutPresets.Refusal.TOO_LARGE -> context.getString(R.string.lp_refuse_too_large)
    LayoutPresets.Refusal.NOT_JSON -> context.getString(R.string.lp_refuse_not_json)
    LayoutPresets.Refusal.NOT_XENDROID -> context.getString(R.string.lp_refuse_not_xendroid)
    LayoutPresets.Refusal.NO_VERSION -> context.getString(R.string.lp_refuse_no_version)
    LayoutPresets.Refusal.NEWER -> context.getString(R.string.lp_refuse_newer, detail)
    LayoutPresets.Refusal.UNKNOWN_VERSION -> context.getString(R.string.lp_refuse_unknown_version, detail)
    LayoutPresets.Refusal.DAMAGED -> context.getString(R.string.lp_refuse_damaged)
    LayoutPresets.Refusal.BAD_NAME -> context.getString(R.string.lp_refuse_bad_name, detail)
    LayoutPresets.Refusal.EMPTY -> context.getString(R.string.lp_refuse_empty)
    LayoutPresets.Refusal.TOO_MANY_CONTROLS -> context.getString(R.string.lp_refuse_too_many, detail)
    LayoutPresets.Refusal.DUPLICATE_CONTROL -> context.getString(R.string.lp_refuse_duplicate)
    LayoutPresets.Refusal.UNNAMED_CONTROL -> context.getString(R.string.lp_refuse_unnamed)
    LayoutPresets.Refusal.OFF_SCREEN -> context.getString(R.string.lp_refuse_off_screen, detail)
    LayoutPresets.Refusal.BAD_SIZE -> context.getString(R.string.lp_refuse_bad_size, detail)
    LayoutPresets.Refusal.NO_ORIENTATION -> context.getString(R.string.lp_refuse_no_orientation)
    LayoutPresets.Refusal.FULL -> context.getString(R.string.lp_refuse_full, detail)
    LayoutPresets.Refusal.CANNOT_OPEN -> context.getString(R.string.lp_refuse_cannot_open)
    LayoutPresets.Refusal.CANNOT_READ -> context.getString(R.string.lp_refuse_cannot_read, detail)
}

/** A save or import that threw: the refusal in the shown language, else the system's words. */
private fun failureText(context: Context, e: Throwable): String =
    (e as? LayoutPresets.RefusedException)?.let { refusalText(context, it.why, it.detail) } ?: e.message ?: e.javaClass.simpleName
