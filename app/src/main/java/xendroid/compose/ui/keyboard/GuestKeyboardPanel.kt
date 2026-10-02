package xendroid.compose.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xendroid.compose.Emulator

/**
 * Answers a guest text-entry prompt (XamShowKeyboardUI). An in-window Surface, not a Dialog:
 * a Dialog takes window focus and trips the host's focus-loss pause. A kernel dispatch thread
 * is held until answered, so [onAccept]/[onCancel] must fire for every request. U10: [grid]
 * is hoisted to the host, which drives it with a controller (the D-pad arrives as hat axes
 * that never reach a composable); touch types on the system keyboard or taps the grid.
 *
 * Top-aligned, not centred: with windowSoftInputMode=adjustNothing the window never resizes
 * around the IME, and API 29 devices report no IME insets at all.
 */
@Composable
fun GuestKeyboardPanel(
    request: Emulator.KeyboardRequest,
    grid: KeyboardGrid,
    onGridChange: (KeyboardGrid) -> Unit,
    onAccept: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember(request.id) { FocusRequester() }

    LaunchedEffect(request.id) { focusRequester.requestFocus() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Swallow taps meant for the game surface underneath.
            .pointerInput(request.id) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val compact = maxHeight < 400.dp
        val outerPadding = if (compact) 8.dp else 24.dp
        val innerPadding = if (compact) 12.dp else 20.dp

        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(outerPadding),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                val description = request.description.orEmpty()
                if (description.isNotEmpty()) {
                    Text(
                        description,
                        maxLines = if (compact) 1 else 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = TextFieldValue(grid.text, TextRange(grid.caret)),
                    onValueChange = { onGridChange(grid.withText(it.text, it.selection.start)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (description.isEmpty()) 0.dp
                                 else if (compact) 8.dp else 16.dp)
                        .heightIn(min = 56.dp)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAccept(grid.text) }),
                )
                Text(
                    "Controller: A type · X delete · Y space · LB/RB cursor · L3 shift · R3 symbols · Start done",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // U10: the grid, for a controller (highlight) or a touch (tap).
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    grid.rows.forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEachIndexed { c, key ->
                                val label = when {
                                    key == GridKey.Command.PAGE && grid.symbols -> "ABC"
                                    key == GridKey.Command.SHIFT && grid.shift != KeyboardGrid.Shift.OFF ->
                                        if (grid.shift == KeyboardGrid.Shift.LOCK) "SHIFT" else "Shift ↑"
                                    key is GridKey.Char && grid.shift != KeyboardGrid.Shift.OFF -> key.upper
                                    else -> key.label
                                }
                                val tap = {
                                    val (next, outcome) = grid.copy(row = r, col = c).press()
                                    when (outcome) {
                                        KeyboardGrid.Outcome.DONE -> onAccept(next.text)
                                        KeyboardGrid.Outcome.CANCEL -> onCancel()
                                        KeyboardGrid.Outcome.NONE -> onGridChange(next)
                                    }
                                }
                                val keyModifier = Modifier.weight(if (key == GridKey.Command.SPACE) 2f else 1f).heightIn(min = 36.dp)
                                if (r == grid.row && c == grid.col) {
                                    Button(onClick = tap, modifier = keyModifier, contentPadding = PaddingValues(2.dp)) {
                                        Text(label, maxLines = 1)
                                    }
                                } else {
                                    OutlinedButton(onClick = tap, modifier = keyModifier, contentPadding = PaddingValues(2.dp)) {
                                        Text(label, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Trims to [maxUnits] UTF-16 code units without splitting a surrogate pair. */
internal fun clampToUtf16Units(text: String, maxUnits: Int): String {
    if (maxUnits <= 0) return ""
    if (text.length <= maxUnits) return text
    var cut = maxUnits
    if (Character.isHighSurrogate(text[cut - 1])) cut--
    return text.substring(0, cut)
}
