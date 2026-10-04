package xendroid.compose.ui.library

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xendroid.compose.R
import xendroid.compose.ui.about.DeviceInfo
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdText

/**
 * Lote 6: without a Vulkan GPU the emulator cannot run. A clear screen instead of a dialog: why,
 * what was checked on this phone, and its data copied for asking for help. Nothing is deleted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoVulkanScreen(onQuit: () -> Unit) {
    val context = LocalContext.current
    val toast = LocalXdToast.current
    val c = Xd.colors
    val checks = remember { FirstRun.deviceChecks(null, Build.SUPPORTED_ABIS.toList(), Build.VERSION.SDK_INT) }
    Box(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center) {
        XdCard(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(XdIcons.chip, null, Modifier.size(30.dp), tint = c.errText)
                Text(stringResource(R.string.xd_nv_title), style = XdText.h1, color = c.fg)
            }
            Text(stringResource(R.string.xd_nv_text), style = XdText.body, color = c.fg2)
            checks.forEach { CheckLine(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                XdButton(stringResource(R.string.lib_quit), onQuit, kind = XdButtonKind.PRIMARY)
                XdButton(stringResource(R.string.xd_nv_copy), {
                    DeviceInfo.copy(context)
                    toast.show(context.getString(R.string.xd_nv_copied))
                }, kind = XdButtonKind.GHOST, icon = XdIcons.copy)
            }
            XdNote(stringResource(R.string.xd_nv_note))
        }
    }
}
