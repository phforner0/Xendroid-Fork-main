package xendroid.compose.ui.keymap

import xendroid.compose.data.GameButton
import xendroid.compose.data.GameButtons
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeymapScreen(vm: KeymapViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val swap by vm.swap.collectAsStateWithLifecycle()
    var capturing by remember { mutableStateOf<KeymapRow?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val unbound = stringResource(R.string.km_unbound)
    fun bindingText(code: Int) = if (code == 0) unbound else keyLabel(code)

    // 15o: say when a key was taken from another button, which got this one's old key.
    swap?.let { traded ->
        val message = stringResource(R.string.km_swapped, buttonLabel(GameButtons.ALL[traded.from]),
            buttonLabel(GameButtons.ALL[traded.index]))
        LaunchedEffect(traded) {
            snackbar.showSnackbar(message)
            vm.onSwapShown()
        }
    }

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
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            // 15o: the controller drawn, each button where it sits; tapping one binds it.
            item(key = "drawing") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ControllerDrawing(
                        label = { buttonLabel(GameButtons.ALL[it]) },
                        binding = { index -> bindingText(state.rows.firstOrNull { it.button.index == index }?.boundKey ?: 0) },
                        state = { index ->
                            val key = state.rows.firstOrNull { it.button.index == index }?.boundKey ?: 0
                            when {
                                state.rows.isEmpty() -> ButtonState.USUAL
                                key == 0 -> ButtonState.UNBOUND
                                index in state.shared -> ButtonState.SHARED
                                index in state.changed -> ButtonState.CHANGED
                                else -> ButtonState.USUAL
                            }
                        },
                        onPick = { index -> state.rows.firstOrNull { it.button.index == index }?.let { capturing = it } },
                        modifier = Modifier.widthIn(max = 560.dp),
                    )
                    Text(stringResource(R.string.km_drawn_hint), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp))
                    if (state.shared.isNotEmpty()) {
                        val names = GameButtons.ALL.filter { it.index in state.shared }.map { buttonLabel(it) }
                        Text(stringResource(R.string.km_shared_warning, names.joinToString(", ")),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            item(key = "swap-face") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.km_swap_face)) },
                    supportingContent = { Text(stringResource(R.string.km_swap_face_note)) },
                    modifier = Modifier.clickable { vm.onSwapFaceButtons() },
                )
                HorizontalDivider()
            }
            items(state.rows, key = { it.button.index }) { row ->
                ListItem(
                    headlineContent = { Text(buttonLabel(row.button)) },
                    supportingContent = {
                        Text(bindingText(row.boundKey).let {
                            if (row.button.index in state.shared) stringResource(R.string.km_shared_mark, it) else it
                        })
                    },
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
            current = bindingText(row.boundKey),
            onKey = { code -> vm.onKeyCaptured(row.button.index, code); capturing = null },
            onDismiss = { capturing = null },
        )
    }
}

@Composable
private fun KeyCaptureDialog(label: String, current: String, onKey: (Int) -> Unit, onDismiss: () -> Unit) {
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
            ) {
                Column {
                    Text(stringResource(R.string.km_now, current))
                    Text(stringResource(R.string.km_waiting))
                }
            }
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
