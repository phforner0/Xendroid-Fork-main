package xendroid.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xendroid.compose.R
import xendroid.compose.driver.InstalledDriverPackage
import xendroid.compose.driver.InstalledDrivers
import xendroid.compose.settings.Setting
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdMenuItem
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.library.formatSize

/**
 * The driver of a scope (the global config or one game), chosen among the system driver and the
 * installed packages; downloading and importing stay in the Drivers area ([onManage]). A game can
 * also go back to the global choice.
 */
@Composable
fun DriverPickerSheet(s: Setting, editing: SettingsEditing, onManage: (() -> Unit)?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = Xd.colors
    var installed by remember { mutableStateOf<List<InstalledDriverPackage>?>(null) }
    LaunchedEffect(Unit) {
        installed = withContext(Dispatchers.IO) {
            runCatching { InstalledDrivers.list(xendroid.compose.Application.get_custom_driver_dir()) }.getOrDefault(emptyList())
        }
    }
    val current = editing.raw(s)
    val pick: (String) -> Unit = { path -> editing.set(s, path); onDismiss() }
    XdSheet(onDismiss = onDismiss, title = settingTitle(s),
        subtitle = stringResource(if (editing.forGame) R.string.xd_drv_pick_sub_game else R.string.xd_drv_pick_sub_global),
        actions = {
            if (onManage != null) XdButton(stringResource(R.string.xd_drv_manage), { onDismiss(); onManage() }, icon = XdIcons.chip)
            XdButton(stringResource(R.string.xd_done), onDismiss, kind = XdButtonKind.PRIMARY)
        }) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (editing.forGame) {
                val inherit = editing.globalRaw(s)
                XdMenuItem(stringResource(R.string.xd_drv_use_global, driverName(context, inherit)), onClick = { editing.reset(s); onDismiss() },
                    icon = if (!editing.changed(s)) XdIcons.checkCircle else XdIcons.reset, tint = if (!editing.changed(s)) c.acc else null)
            }
            val own = !editing.forGame || editing.changed(s)
            XdMenuItem(stringResource(R.string.drv_system), onClick = { pick("") }, subtitle = stringResource(R.string.xd_drv_system_sub),
                icon = if (own && current.isBlank()) XdIcons.checkCircle else XdIcons.cpu, tint = if (own && current.isBlank()) c.acc else null)
            for (d in installed.orEmpty()) {
                val path = d.library.absolutePath
                val on = own && path == current
                XdMenuItem(listOfNotNull(d.name, d.version).joinToString(" "), onClick = { pick(path) },
                    subtitle = listOfNotNull(formatSize(d.bytes), if (d.verifiedLayout) null else stringResource(R.string.xd_drv_older_import)).joinToString(" · "),
                    icon = if (on) XdIcons.checkCircle else XdIcons.chip, tint = if (on) c.acc else null)
            }
        }
        if (installed?.isEmpty() == true) XdNote(stringResource(R.string.xd_drv_none_installed), icon = XdIcons.info)
        XdNote(stringResource(R.string.xd_drv_next_launch), icon = XdIcons.restart)
    }
}
