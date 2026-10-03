package xendroid.compose.gamepad

import androidx.compose.ui.res.pluralStringResource
import android.content.Context
import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.archive.ArchiveFiles

/**
 * U06: named layouts in the editor. Saving takes the layout being edited (both orientations);
 * applying shows what moves first; files are exported/imported through the system picker.
 * Changes go into the editor's working copy and are kept with "Save & Quit", like any edit.
 */
@Composable
fun LayoutPresetsDialog(
    config: GamepadConfigDto,
    /** The game whose own layout is being edited, or null for the shared one. */
    editScope: String?,
    landscape: Boolean,
    onChange: (GamepadConfigDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var applying by remember { mutableStateOf<LayoutPresetDto?>(null) }
    var exporting by remember { mutableStateOf<LayoutPresetDto?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val preset = exporting ?: return@rememberLauncherForActivityResult
        exporting = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(LayoutPresets.encodeFile(preset).toByteArray()) }
                        ?: error("no file")
                }.fold({ context.getString(R.string.lp_exported, preset.name) },
                    { context.getString(R.string.lp_write_failed, it.message ?: it.javaClass.simpleName) })
            }
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
            message = when (decoded) {
                is LayoutPresets.Decoded.Refused -> context.getString(R.string.lp_not_imported, refusalText(context, decoded.why, decoded.detail))
                is LayoutPresets.Decoded.Ok -> runCatching {
                    val named = decoded.preset.copy(name = LayoutPresets.uniqueName(config, decoded.preset.name))
                    onChange(LayoutPresets.save(config, named))
                    context.getString(R.string.lp_imported, named.name)
                }.getOrElse { context.getString(R.string.lp_not_imported, failureText(context, it)) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lp_title)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (editScope != null) stringResource(R.string.lp_scope_game) else
                    stringResource(R.string.lp_scope_shared), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(LayoutPresets.MAX_NAME) }, singleLine = true,
                        label = { Text(stringResource(R.string.lp_name)) }, modifier = Modifier.weight(1f))
                    TextButton(enabled = LayoutPresets.cleanName(name) != null, onClick = {
                        message = runCatching {
                            val replaced = LayoutPresets.find(config, name) != null
                            onChange(LayoutPresets.save(config, LayoutPresets.capture(config, editScope, name, System.currentTimeMillis())))
                            context.getString(if (replaced) R.string.lp_replaced else R.string.lp_saved, LayoutPresets.cleanName(name))
                        }.getOrElse { failureText(context, it) }
                        name = ""
                    }) { Text(stringResource(R.string.common_save)) }
                }
                if (config.presets.isEmpty()) Text(stringResource(R.string.lp_none), style = MaterialTheme.typography.bodySmall)
                config.presets.forEach { preset ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                        Text(listOfNotNull(preset.landscape?.let { stringResource(R.string.lp_landscape_lc) },
                            preset.portrait?.let { stringResource(R.string.lp_portrait_lc) }).joinToString(" + "),
                            style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { applying = preset }) { Text(stringResource(R.string.lp_apply_ellipsis)) }
                            TextButton(onClick = {
                                exporting = preset
                                exportLauncher.launch("${preset.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.xdlayout.json")
                            }) { Text(stringResource(R.string.lp_export)) }
                            TextButton(onClick = {
                                onChange(LayoutPresets.delete(config, preset.name))
                                message = context.getString(R.string.lp_deleted, preset.name)
                            }) { Text(stringResource(R.string.common_delete)) }
                        }
                    }
                }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                    Text(stringResource(R.string.lp_import))
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.lp_kept_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )

    applying?.let { preset ->
        val here = if (landscape) preset.landscape else preset.portrait
        val diff = here?.let {
            LayoutPresets.diff(config.layoutFor(editScope, landscape), it, defaultLayout(landscape))
        }
        AlertDialog(
            onDismissRequest = { applying = null },
            title = { Text(stringResource(R.string.lp_apply_title, preset.name)) },
            text = {
                Text(listOfNotNull(
                    if (diff != null) stringResource(if (landscape) R.string.lp_diff_landscape else R.string.lp_diff_portrait, diffText(diff))
                    else stringResource(if (landscape) R.string.lp_no_landscape else R.string.lp_no_portrait),
                    (if (landscape) preset.portrait else preset.landscape)?.let {
                        stringResource(if (landscape) R.string.lp_other_portrait else R.string.lp_other_landscape)
                    },
                ).joinToString(" "))
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LayoutPresets.apply(config, preset, editScope))
                    message = context.getString(R.string.lp_applied, preset.name)
                    applying = null
                }) { Text(stringResource(R.string.lp_apply)) }
            },
            dismissButton = { TextButton(onClick = { applying = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
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
