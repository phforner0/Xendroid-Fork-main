package xendroid.compose.ui.messagebox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import xendroid.compose.Emulator
import xendroid.compose.R
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdHint
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.panel.GuestPanelFrame
import xendroid.compose.ui.panel.GuestPanelOption
import xendroid.compose.ui.panel.GuestPanelOptions

/**
 * Answers a guest message box (XamShowMessageBoxUI), lote 7: which game asks and that it waits,
 * the title, the text (it scrolls when long, the options stay in view) and the guest's options
 * as whole rows. A guest thread blocks until answered, so [onChoose] must fire for every request
 * (no cancel). [selected] is driven by the host activity because the D-pad arrives as hat axes
 * that never reach a composable.
 */
@Composable
fun GuestMessageBoxPanel(
    request: Emulator.MessageBoxRequest,
    selected: Int,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
    gameName: String? = null,
    art: Any? = null,
) {
    val c = Xd.colors
    val ok = stringResource(R.string.common_ok)
    val buttons = request.buttons?.takeIf { it.isNotEmpty() } ?: arrayOf(ok)
    GuestPanelFrame(request.id, stringResource(R.string.xd_gp_waiting), modifier, gameName, art,
        hints = listOf(XdHint("A", stringResource(R.string.xd_gp_choose)))) { compact ->
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            val title = request.title.orEmpty().trim()
            if (title.isNotEmpty()) Text(title, style = XdText.sheetTitle, color = c.fg)
            val text = request.text.orEmpty().trim()
            if (text.isNotEmpty()) Text(text, style = XdText.body, color = c.fg2,
                modifier = Modifier.padding(top = if (compact) 4.dp else 8.dp))
        }
        // Full-width, not a Row: guest labels are whole sentences often enough to wrap badly.
        GuestPanelOptions {
            for (i in buttons.indices) {
                GuestPanelOption(label = buttons[i].ifBlank { ok }, selected = i == selected, onClick = { onChoose(i) })
            }
        }
        Text(stringResource(R.string.xd_gp_paused), style = XdText.small, color = c.fg3)
    }
}
