package xendroid.compose.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xendroid.compose.Emulator
import xendroid.compose.R
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdHint
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.panel.GuestPanelFrame

/**
 * Answers a guest text-entry prompt (XamShowKeyboardUI), lote 7: which game asks, its request,
 * the field with the game's limit counted as the game counts it (UTF-16 units), and the grid
 * with the command keys in the shown language. A kernel dispatch thread is held until answered,
 * so [onAccept]/[onCancel] must fire for every request. U10: [grid] is hoisted to the host, which
 * drives it with a controller (the D-pad arrives as hat axes that never reach a composable; the
 * Xbox 360 shortcuts show under the panel); by touch, the phone's keyboard types in the field and
 * the grid can be tapped. Top-aligned: with windowSoftInputMode=adjustNothing the window never
 * resizes around the phone's keyboard, and API 29 devices report no keyboard insets at all.
 */
@Composable
fun GuestKeyboardPanel(
    request: Emulator.KeyboardRequest,
    grid: KeyboardGrid,
    onGridChange: (KeyboardGrid) -> Unit,
    onAccept: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    gameName: String? = null,
    art: Any? = null,
) {
    val c = Xd.colors
    val focusRequester = remember(request.id) { FocusRequester() }
    // With a controller the grid types; the phone's keyboard would only cover the game.
    LaunchedEffect(request.id, c.controller) { if (!c.controller) runCatching { focusRequester.requestFocus() } }
    val hints = listOf(
        XdHint("A", stringResource(R.string.xd_kb_type)), XdHint("X", stringResource(R.string.xd_kb_delete)),
        XdHint("Y", stringResource(R.string.xd_kb_space)), XdHint("LB/RB", stringResource(R.string.xd_kb_cursor)),
        XdHint("L3", stringResource(R.string.xd_kb_shift)), XdHint("R3", stringResource(R.string.xd_kb_symbols)),
        XdHint("≡", stringResource(R.string.xd_kb_done)),
    )
    GuestPanelFrame(request.id, stringResource(R.string.xd_gp_asks_text), modifier, gameName, art, hints, imePadding = true,
        maxWidth = 760.dp) { compact ->
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
            val description = request.description.orEmpty().trim().ifEmpty { request.title.orEmpty().trim() }
            if (description.isNotEmpty()) Text(description, style = XdText.label, color = c.fg, maxLines = if (compact) 1 else 3,
                overflow = TextOverflow.Ellipsis)
            Field(grid, onGridChange, onAccept, focusRequester, editable = !c.controller, compact = compact)
            KeyGrid(grid, onGridChange, onAccept, onCancel, compact)
        }
    }
}

/** The text and its count; typed by the phone's keyboard by touch, shown with its caret with a controller. */
@Composable
private fun Field(grid: KeyboardGrid, onGridChange: (KeyboardGrid) -> Unit, onAccept: (String) -> Unit, focus: FocusRequester, editable: Boolean,
    compact: Boolean) {
    val c = Xd.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 40.dp else 46.dp).clip(shape).background(c.s2).border(1.5.dp, c.acc.copy(alpha = 0.7f), shape)
            .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f)) {
            if (editable) BasicTextField(
                value = TextFieldValue(grid.text, TextRange(grid.caret)),
                onValueChange = { onGridChange(grid.withText(it.text, it.selection.start)) },
                singleLine = true,
                textStyle = XdText.body.copy(color = c.fg),
                cursorBrush = SolidColor(c.acc),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAccept(grid.text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            ) else Text(buildAnnotatedString {
                append(grid.text.substring(0, grid.caret))
                withStyle(SpanStyle(color = c.acc)) { append("|") }
                append(grid.text.substring(grid.caret))
            }, style = XdText.body, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (grid.maxUnits < Int.MAX_VALUE) Text("${grid.text.length}/${grid.maxUnits}", style = XdText.monoNum, color = c.fg3)
    }
}

@Composable
private fun keyLabel(key: GridKey, grid: KeyboardGrid): String = when (key) {
    is GridKey.Char -> if (grid.shift != KeyboardGrid.Shift.OFF) key.upper else key.lower
    GridKey.Command.SHIFT -> stringResource(R.string.xd_kb_shift) + if (grid.shift == KeyboardGrid.Shift.LOCK) " ⇪" else ""
    GridKey.Command.PAGE -> if (grid.symbols) "ABC" else "?123"
    GridKey.Command.SPACE -> stringResource(R.string.xd_kb_space)
    GridKey.Command.BACKSPACE -> "⌫"
    GridKey.Command.LEFT -> "←"
    GridKey.Command.RIGHT -> "→"
    GridKey.Command.DONE -> stringResource(R.string.xd_kb_done)
    GridKey.Command.CANCEL -> stringResource(R.string.xd_kb_cancel)
}

/** A command key's word, or on a narrow grid its icon (Shift, Done, Cancel), named for screen readers. */
@Composable
private fun KeyFace(key: GridKey, grid: KeyboardGrid, narrow: Boolean, color: Color) {
    val icon = if (!narrow) null else when (key) {
        GridKey.Command.SHIFT -> if (grid.shift == KeyboardGrid.Shift.LOCK) XdIcons.capsLock else XdIcons.shift
        GridKey.Command.DONE -> XdIcons.check
        GridKey.Command.CANCEL -> XdIcons.x
        else -> null
    }
    val label = keyLabel(key, grid)
    if (icon != null) Icon(icon, label, Modifier.size(19.dp), tint = color)
    else Text(label, style = if (key !is GridKey.Command) XdText.label else if (narrow) XdText.labelSm.copy(fontSize = 12.sp) else XdText.labelSm,
        color = color, maxLines = 1)
}

/** U10: the grid, for a controller (the highlight) or a touch (a tap). */
@Composable
private fun KeyGrid(grid: KeyboardGrid, onGridChange: (KeyboardGrid) -> Unit, onAccept: (String) -> Unit, onCancel: () -> Unit, compact: Boolean) {
    val c = Xd.colors
    BoxWithConstraints {
        // Words on the command keys need about 44 dp per key unit: a portrait phone shows icons.
        val narrow = maxWidth < 470.dp
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            grid.rows.forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEachIndexed { col, key ->
                        // The highlight is the controller's cursor; by touch a key is simply tapped.
                        val highlighted = c.controller && r == grid.row && col == grid.col
                        val command = key is GridKey.Command
                        val on = (key == GridKey.Command.SHIFT && grid.shift != KeyboardGrid.Shift.OFF) || (key == GridKey.Command.PAGE && grid.symbols)
                        val shape = RoundedCornerShape(9.dp)
                        val weight = when (key) {
                            GridKey.Command.SPACE -> 2f
                            GridKey.Command.DONE, GridKey.Command.CANCEL -> 1.4f
                            else -> 1f
                        }
                        Box(
                            Modifier.weight(weight).heightIn(min = if (compact) 32.dp else 40.dp).clip(shape)
                                .background(when {
                                    highlighted -> c.acc
                                    key == GridKey.Command.DONE -> c.acc.copy(alpha = 0.22f)
                                    command -> c.s3
                                    else -> c.s2
                                })
                                .then(if (on && !highlighted) Modifier.border(1.5.dp, c.acc, shape) else Modifier)
                                .clickable(role = Role.Button) {
                                    val (next, outcome) = grid.copy(row = r, col = col).press()
                                    when (outcome) {
                                        KeyboardGrid.Outcome.DONE -> onAccept(next.text)
                                        KeyboardGrid.Outcome.CANCEL -> onCancel()
                                        KeyboardGrid.Outcome.NONE -> onGridChange(next)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            KeyFace(key, grid, narrow, if (highlighted) c.onAcc else if (on || key == GridKey.Command.DONE) c.acc else c.fg)
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
