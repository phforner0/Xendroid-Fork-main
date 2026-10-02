package xendroid.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xendroid.compose.updater.UpdateChannel
import xendroid.compose.updater.setUpdateChannel
import xendroid.compose.updater.updateChannel
import xendroid.compose.updater.updateRepository

/** R02: which releases this build offers to install. Absent in builds without a release feed. */
@Composable
fun UpdateChannelSection() {
    if (updateRepository() == null) return
    val context = LocalContext.current
    var channel by remember { mutableStateOf(updateChannel(context)) }
    ListItem(
        headlineContent = { Text("App updates") },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(when (channel) {
                    UpdateChannel.STABLE -> "Offers new releases; each is checked against its published SHA-256 and installed only after you confirm."
                    UpdateChannel.PREVIEW -> "Also offers preview releases, which may be less tested."
                    UpdateChannel.OFF -> "Never checks for updates."
                })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdateChannel.entries.forEach { option ->
                        FilterChip(selected = channel == option, label = { Text(option.label) }, onClick = {
                            channel = option
                            setUpdateChannel(context, option)
                        })
                    }
                }
            }
        },
    )
}
