package xendroid.compose.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import xendroid.compose.ui.design.CHints
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdHint
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdText

/**
 * Lote 7: the frame of the panels the game itself asks for (a message, a text, another disc)
 * over the paused scene. An in-window panel, not a Dialog: a Dialog takes window focus and trips
 * the host's focus-loss pause. It says which game is asking and that it waits ([gameName],
 * [what]); [hints] (controller mode) show the pad's buttons under it; [maxWidth] lets the keyboard
 * use a landscape screen. Taps meant for the game
 * underneath are swallowed. Top-aligned, so the phone's keyboard never covers it.
 */
@Composable
fun GuestPanelFrame(
    requestKey: Any,
    what: String,
    modifier: Modifier = Modifier,
    gameName: String? = null,
    art: Any? = null,
    hints: List<XdHint> = emptyList(),
    imePadding: Boolean = false,
    maxWidth: Dp = 560.dp,
    content: @Composable ColumnScope.(compact: Boolean) -> Unit,
) {
    val c = Xd.colors
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(requestKey) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .then(if (imePadding) Modifier.imePadding() else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        // A phone in landscape (about 410 dp high) is compact: the keyboard's five rows must fit.
        val compact = maxHeight < 480.dp
        val outer = if (compact) 8.dp else 24.dp
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .padding(outer)
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                // Bounded so a long text scrolls instead of pushing the options offscreen.
                .heightIn(max = maxHeight - outer * 2)
                .clip(shape)
                .background(c.solid(c.sheet))
                .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
                .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 10.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            if (gameName != null || art != null) From(gameName, art, what)
            content(compact)
            if (hints.isNotEmpty() && c.controller) Column {
                HorizontalDivider(thickness = 1.dp, color = c.line)
                CHints(hints, scrim = false)
            }
        }
    }
}

/** "[cover] Halo 3 is waiting for your answer". */
@Composable
private fun From(gameName: String?, art: Any?, what: String) {
    val c = Xd.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val shape = RoundedCornerShape(5.dp)
        if (art != null) AsyncImage(art, null, Modifier.width(24.dp).aspectRatio(0.75f).clip(shape), contentScale = ContentScale.Crop)
        else Box(Modifier.size(24.dp).clip(shape).background(c.s3), contentAlignment = Alignment.Center) {
            Icon(XdIcons.gamepad, null, Modifier.size(15.dp), tint = c.fg3)
        }
        Text(buildAnnotatedString {
            if (gameName != null) withStyle(SpanStyle(color = c.fg, fontWeight = FontWeight.SemiBold)) { append(gameName); append(" ") }
            append(what)
        }, style = XdText.small, color = c.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
