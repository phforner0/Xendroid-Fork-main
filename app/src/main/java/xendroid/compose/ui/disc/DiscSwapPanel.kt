package xendroid.compose.ui.disc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import xendroid.compose.Emulator
import xendroid.compose.R
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdHint
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.panel.GuestPanelFrame
import xendroid.compose.ui.panel.GuestPanelOption
import xendroid.compose.ui.panel.GuestPanelOptions

/**
 * Answers a guest disc-swap prompt (XamSwapDisc), lote 7: which game asks, the disc to insert,
 * the title's discs found in the game folders (each with its file) and Cancel last, saying that
 * the game is left without a disc. A guest thread blocks until answered, so [onChoose]/[onCancel]
 * must fire for every request; the guest ejects before asking. [selected] is driven by the host
 * because the D-pad arrives as hat axes that never reach a composable; Cancel is the LAST option,
 * index discCount. [current] is the disc that was in the drive (marked "the one before").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscSwapPanel(
    request: Emulator.DiscSwapRequest,
    selected: Int,
    onChoose: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    gameName: String? = null,
    art: Any? = null,
    current: String? = null,
) {
    val c = Xd.colors
    val labels = request.discLabels ?: emptyArray()
    val paths = request.discPaths ?: emptyArray()
    val count = minOf(labels.size, paths.size)
    GuestPanelFrame(request.id, stringResource(R.string.xd_gp_asks_disc), modifier, gameName, art,
        hints = listOf(XdHint("A", stringResource(R.string.xd_gp_choose)), XdHint("B", stringResource(R.string.common_cancel)))) { compact ->
        // The title stays; the discs scroll when they do not fit (a landscape phone with a controller).
        Column {
            Text(if (request.discNumber > 0) stringResource(R.string.disc_insert_n, request.discNumber) else stringResource(R.string.disc_insert),
                style = XdText.sheetTitle, color = c.fg)
            val message = request.message.orEmpty().trim()
            if (message.isNotEmpty()) Text(message, style = XdText.body, color = if (request.isError) c.errText else c.fg2,
                maxLines = if (compact) 2 else 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (compact) 4.dp else 8.dp))
            if (count == 0) Text(stringResource(R.string.disc_none), style = XdText.body, color = c.fg2,
                modifier = Modifier.padding(top = if (compact) 6.dp else 10.dp))
        }
        if (count > 0) GuestPanelOptions(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            for (i in 0 until count) {
                // The host moves the selection with the D-pad: keep it in view.
                val view = remember { BringIntoViewRequester() }
                LaunchedEffect(selected) { if (i == selected) runCatching { view.bringIntoView() } }
                val file = File(paths[i]).name
                GuestPanelOption(label = labels[i], selected = i == selected, onClick = { onChoose(paths[i]) },
                    modifier = Modifier.bringIntoViewRequester(view), icon = XdIcons.disc,
                    subtitle = if (paths[i] == current) stringResource(R.string.xd_ds_before, file) else file)
            }
        }
        // Pinned, so a long list cannot hide Cancel.
        GuestPanelOptions {
            GuestPanelOption(label = stringResource(R.string.common_cancel), selected = selected == count, onClick = onCancel,
                icon = XdIcons.x, subtitle = stringResource(R.string.xd_ds_cancel_note), subtle = true)
        }
    }
}

/** The option to highlight first: the disc whose label carries the number the game asks for
 *  ("Disc 2 of 3"), else the first. */
fun requestedDiscIndex(request: Emulator.DiscSwapRequest): Int {
    val labels = request.discLabels ?: return 0
    val count = minOf(labels.size, request.discPaths?.size ?: 0)
    if (request.discNumber <= 0) return 0
    val number = Regex("\\d+")
    return (0 until count).firstOrNull { number.find(labels[it])?.value?.toIntOrNull() == request.discNumber } ?: 0
}
