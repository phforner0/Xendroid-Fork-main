package xendroid.compose.gamepad

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
                }.fold({ "Exported “${preset.name}”." }, { "Could not write the file: ${it.message}" })
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
                    } ?: LayoutPresets.Decoded.Refused("The file could not be opened")
                }.getOrElse { LayoutPresets.Decoded.Refused("The file could not be read (${it.message})") }
            }
            message = when (decoded) {
                is LayoutPresets.Decoded.Refused -> "Not imported: ${decoded.reason}."
                is LayoutPresets.Decoded.Ok -> runCatching {
                    val named = decoded.preset.copy(name = LayoutPresets.uniqueName(config, decoded.preset.name))
                    onChange(LayoutPresets.save(config, named))
                    "Imported “${named.name}”."
                }.getOrElse { "Not imported: ${it.message}." }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Layouts") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (editScope != null) "Saving takes this game's layout; applying replaces it." else
                    "Saving takes the shared layout; applying replaces it.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(LayoutPresets.MAX_NAME) }, singleLine = true,
                        label = { Text("Name") }, modifier = Modifier.weight(1f))
                    TextButton(enabled = LayoutPresets.cleanName(name) != null, onClick = {
                        message = runCatching {
                            val replaced = LayoutPresets.find(config, name) != null
                            onChange(LayoutPresets.save(config, LayoutPresets.capture(config, editScope, name, System.currentTimeMillis())))
                            if (replaced) "Replaced “${LayoutPresets.cleanName(name)}”." else "Saved “${LayoutPresets.cleanName(name)}”."
                        }.getOrElse { it.message }
                        name = ""
                    }) { Text("Save") }
                }
                if (config.presets.isEmpty()) Text("No saved layouts yet.", style = MaterialTheme.typography.bodySmall)
                config.presets.forEach { preset ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                        Text(listOfNotNull(preset.landscape?.let { "landscape" }, preset.portrait?.let { "portrait" }).joinToString(" + "),
                            style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { applying = preset }) { Text("Apply…") }
                            TextButton(onClick = {
                                exporting = preset
                                exportLauncher.launch("${preset.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.xdlayout.json")
                            }) { Text("Export") }
                            TextButton(onClick = {
                                onChange(LayoutPresets.delete(config, preset.name))
                                message = "Deleted “${preset.name}”."
                            }) { Text("Delete") }
                        }
                    }
                }
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                    Text("Import a layout file…")
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("Saved layouts are kept with Save & Quit.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    applying?.let { preset ->
        val here = if (landscape) preset.landscape else preset.portrait
        val diff = here?.let {
            LayoutPresets.diff(config.layoutFor(editScope, landscape), it, defaultLayout(landscape))
        }
        AlertDialog(
            onDismissRequest = { applying = null },
            title = { Text("Apply “${preset.name}”?") },
            text = {
                Text(buildString {
                    append(if (diff != null) "${if (landscape) "Landscape" else "Portrait"}: ${diff.summary}."
                        else "It has no ${if (landscape) "landscape" else "portrait"} layout; this orientation stays.")
                    val other = if (landscape) preset.portrait else preset.landscape
                    if (other != null) append(" The ${if (landscape) "portrait" else "landscape"} layout is replaced too.")
                })
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LayoutPresets.apply(config, preset, editScope))
                    message = "Applied “${preset.name}”."
                    applying = null
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { applying = null }) { Text("Cancel") } },
        )
    }
}
