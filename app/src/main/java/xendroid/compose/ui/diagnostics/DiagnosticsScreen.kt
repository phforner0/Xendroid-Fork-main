package xendroid.compose.ui.diagnostics

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.core.SessionLogs
import xendroid.compose.core.diagnosticsShareIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(titleId: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionLogs.Session>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(titleId) { sessions = withContext(Dispatchers.IO) { SessionLogs.sessions() } }
    fun share(id: String?) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val zip = withContext(Dispatchers.IO) { SessionLogs.exportRedactedForSharing(context, id) }
                if (zip != null) context.startActivity(diagnosticsShareIntent(context, zip))
                else Toast.makeText(context, context.getString(R.string.dg_none), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, context.getString(R.string.dg_export_failed), Toast.LENGTH_LONG).show()
            } finally { busy = false }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.lib_menu_diagnostics) + titleId?.let { " · $it" }.orEmpty()) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(stringResource(R.string.dg_intro))
                OutlinedButton(enabled = !busy, onClick = { share(null) }) { Text(stringResource(R.string.dg_share_all)) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val matching = sessions.filter { titleId == null || it.titles.contains(titleId) }
            if (matching.isEmpty()) item { Text(stringResource(R.string.dg_no_session)) }
            items(matching, key = { it.id }) { session ->
                ListItem(headlineContent = { Text(session.label) }, supportingContent = {
                    Text("${session.bytes / 1024} KB${session.titles.takeIf { it.isNotEmpty() }?.joinToString(prefix = " · ").orEmpty()}")
                }, modifier = Modifier.clickable(enabled = !busy) { share(session.id) })
            }
        }
    }
}
