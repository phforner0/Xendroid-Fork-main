package xendroid.compose.ui.keymap

import xendroid.compose.data.GameButton
import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeymapScreen(vm: KeymapViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var capturing by remember { mutableStateOf<KeymapRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lib_menu_keymap)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = { vm.onResetDefaults() }) { Text(stringResource(R.string.km_reset)) }
                },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(state.rows, key = { it.button.index }) { row ->
                ListItem(
                    headlineContent = { Text(buttonLabel(row.button)) },
                    supportingContent = { Text(if (row.boundKey == 0) stringResource(R.string.km_unbound) else keyLabel(row.boundKey)) },
                    trailingContent = {
                        TextButton(onClick = { vm.onClear(row.button.index) }) { Text(stringResource(R.string.km_clear)) }
                    },
                    modifier = Modifier.clickable { capturing = row },
                )
                HorizontalDivider()
            }
        }
    }

    capturing?.let { row ->
        KeyCaptureDialog(
            label = buttonLabel(row.button),
            onKey = { code -> vm.onKeyCaptured(row.button.index, code); capturing = null },
            onDismiss = { capturing = null },
        )
    }
}

@Composable
private fun KeyCaptureDialog(label: String, onKey: (Int) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(enabled = true, onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        title = { Text(stringResource(R.string.km_press, label)) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown) {
                            onKey(ev.nativeKeyEvent.keyCode); true
                        } else false
                    }
            ) { Text(stringResource(R.string.km_waiting)) }
        },
    )
}

/** Human-readable name for a bound Android keycode. */
private fun keyLabel(code: Int): String = AndroidKeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

/** U02: the button's shown name; letters, Back and Start stay as printed on the controller. */
@Composable
private fun buttonLabel(button: GameButton): String = when (button.index) {
    0 -> stringResource(R.string.km_btn_dpad_left)
    1 -> stringResource(R.string.km_btn_dpad_up)
    2 -> stringResource(R.string.km_btn_dpad_right)
    3 -> stringResource(R.string.km_btn_dpad_down)
    10 -> stringResource(R.string.km_btn_lb)
    11 -> stringResource(R.string.km_btn_rb)
    12 -> stringResource(R.string.km_btn_l3)
    13 -> stringResource(R.string.km_btn_r3)
    14 -> stringResource(R.string.km_btn_lt)
    15 -> stringResource(R.string.km_btn_rt)
    else -> button.label
}
