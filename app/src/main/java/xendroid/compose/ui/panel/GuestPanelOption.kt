package xendroid.compose.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * One option of the in-game panels (menu, guest dialogs, disc swap), in the redesign's look: a
 * row that the controller highlight fills with the accent. [splitValue] shows "Title: value"
 * as the title with its value on the right; a trailing " ✓" marks the current choice.
 */
@Composable
fun GuestPanelOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    splitValue: Boolean = false,
    /** A quieter row ("More options"). */
    subtle: Boolean = false,
    /** Leaving or deleting: red text. */
    danger: Boolean = false,
    /** Lote 7: an icon before the label, and a line under it (a disc's file, what Cancel does). */
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    subtitle: String? = null,
) {
    val c = Xd.colors
    val shape = RoundedCornerShape(12.dp)
    val checked = label.endsWith(" ✓")
    val text = label.removeSuffix(" ✓")
    val cut = if (splitValue) text.indexOf(": ").takeIf { it in 1..48 && '\n' !in text } else null
    val title = cut?.let { text.substring(0, it) } ?: text
    val value = cut?.let { text.substring(it + 2) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            // U02: the controller highlight is the screen reader's "selected" too.
            .semantics { this.selected = selected }
            .clip(shape)
            .background(when {
                selected -> c.acc.copy(alpha = 0.22f).compositeOver(c.solid(c.s1))
                subtle -> Color.Transparent
                else -> c.s1
            })
            .then(if (selected) Modifier.border(2.dp, c.acc, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.42f)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = if (selected) c.acc else c.fg3)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (subtle) XdText.labelSm else XdText.label,
                color = when { danger -> c.dangerText; checked -> c.acc; subtle -> c.fg2; else -> c.fg },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, style = XdText.small, color = c.fg3, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, style = XdText.labelSm, color = if (selected) c.fg else c.fg2, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 210.dp))
        if (checked) Icon(XdIcons.check, null, Modifier.size(18.dp), tint = c.acc)
    }
}

@Composable
fun GuestPanelOptions(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun GuestPanelOptionsRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}