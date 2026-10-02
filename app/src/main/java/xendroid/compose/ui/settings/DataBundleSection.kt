package xendroid.compose.ui.settings

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import xendroid.compose.bundle.BundleException
import xendroid.compose.bundle.DataBundle
import xendroid.compose.bundle.DataBundleIo
import xendroid.compose.bundle.ImportPlan

/**
 * L08: export the settings, touch controls, favorites, collections and compatibility notes to a file
 * the user picks, or import one after seeing exactly what it would change. [beforeImport]
 * writes pending edits of the open settings screen; [afterImport] re-reads them.
 */
@Composable
fun DataBundleSection(beforeImport: () -> Unit, afterImport: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<DataBundle, ImportPlan>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    fun run(work: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                work()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failure = (e as? BundleException)?.message ?: context.getString(R.string.bundle_bad_file, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) run {
            val parts = DataBundleIo.export(context, uri)
            Toast.makeText(context, context.getString(R.string.bundle_exported, parts), Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run { pending = DataBundleIo.preview(context, uri) }
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.bundle_title)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.bundle_note))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        exportLauncher.launch("xendroid-settings-$stamp.zip")
                    }) { Text(stringResource(R.string.bundle_export)) }
                    OutlinedButton(enabled = !busy, onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }) { Text(stringResource(R.string.bundle_import)) }
                }
                if (busy) LinearProgressIndicator()
            }
        },
    )

    pending?.let { (bundle, plan) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (plan.changes > 0) stringResource(R.string.bundle_confirm) else stringResource(R.string.bundle_nothing)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    plan.lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (plan.changes > 0) Text(stringResource(R.string.bundle_backup_note),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = {
                if (plan.changes > 0) TextButton(enabled = !busy, onClick = {
                    run {
                        beforeImport()
                        val backup = DataBundleIo.import(context, bundle)
                        pending = null
                        afterImport()
                        Toast.makeText(context, context.getString(R.string.bundle_imported, backup), Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.bundle_import)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(if (plan.changes > 0) stringResource(R.string.common_cancel) else stringResource(R.string.common_close)) } },
        )
    }

    failure?.let { message ->
        AlertDialog(
            onDismissRequest = { failure = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { failure = null }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}
