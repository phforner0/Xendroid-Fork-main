package xendroid.compose.ui.about

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import xendroid.compose.Emulator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    // simple_device_info() is a JNI instance method; Emulator.get may be null before
    // load_library() on delay-load devices. Guard it.
    val deviceInfo = remember {
        runCatching { Emulator.get?.simple_device_info() }.getOrNull()
            ?: context.getString(R.string.ab_no_device_info)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lib_menu_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("xendroid", style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.ab_version, versionName), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.ab_credits), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ab_credits_text), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.ab_device), style = MaterialTheme.typography.titleMedium)
            SelectionContainerText(deviceInfo)
            Spacer(Modifier.height(16.dp))

            Button(onClick = { showLicenses = true }) { Text(stringResource(R.string.ab_licenses_open)) }
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.common_ok)) } },
            title = { Text(stringResource(R.string.ab_licenses)) },
            text = {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply { loadUrl("file:///android_asset/licenses.html") }
                    },
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                )
            },
        )
    }
}

@Composable
private fun SelectionContainerText(text: String) {
    SelectionContainer {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
