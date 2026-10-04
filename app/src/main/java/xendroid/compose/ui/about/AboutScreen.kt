package xendroid.compose.ui.about

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import xendroid.compose.BuildConfig
import xendroid.compose.R
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdKv
import xendroid.compose.ui.design.XdListRow
import xendroid.compose.ui.design.XdLogo
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdSingleScreen
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTwoColumns

/**
 * Lote 6: the version and build; this phone as a table to copy whole into a problem report;
 * credits and the open-source licenses; and the way to app updates, Diagnostics and the setup
 * assistant.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onUpdates: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onSetup: () -> Unit = {},
) {
    val context = LocalContext.current
    val toast = LocalXdToast.current
    val c = Xd.colors
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    val device = remember { DeviceInfo.rows(context) }
    XdSingleScreen(title = stringResource(R.string.lib_menu_about), subtitle = "XenDroid v${BuildConfig.VERSION_CODE}", onBack = onBack,
        headIcon = XdIcons.info) {
        BoxWithConstraints {
            val wide = maxWidth > 640.dp
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                XdCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        XdLogo(56.dp)
                        Column(Modifier.weight(1f)) {
                            Text("XenDroid", style = XdText.h1, color = c.fg)
                            Text(stringResource(R.string.ab_version, "v${BuildConfig.VERSION_CODE} · ${BuildConfig.VERSION_NAME.take(9)}"),
                                style = XdText.small, color = c.fg3)
                            Text(stringResource(R.string.xd_fr_tagline), style = XdText.bodySm, color = c.fg2)
                        }
                    }
                }
                XdTwoColumns(wide, left = {
                    XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.ab_device), icon = XdIcons.phone) {
                        XdKv(device.map { (label, value) -> stringResource(label) to value })
                        XdButton(stringResource(R.string.xd_ab_copy_all), {
                            DeviceInfo.copy(context)
                            toast.show(context.getString(R.string.xd_ab_copied))
                        }, size = XdButtonSize.SM, icon = XdIcons.copy)
                    }
                }, right = {
                    // Whole rows open their screen; a controller's A on the row does it.
                    XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_ab_shortcuts_head), icon = XdIcons.spark) {
                        Column {
                            ShortcutRow(stringResource(R.string.lib_menu_updates), XdIcons.download, onUpdates)
                            ShortcutRow(stringResource(R.string.lib_menu_diagnostics), XdIcons.bug, onDiagnostics)
                            ShortcutRow(stringResource(R.string.lib_menu_setup), XdIcons.spark, onSetup, divider = false)
                        }
                    }
                    XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_ab_credits_head), icon = XdIcons.info) {
                        Text(stringResource(R.string.xd_ab_credits_line), style = XdText.bodySm, color = c.fg2)
                        XdButton(stringResource(R.string.ab_licenses_open), { showLicenses = true }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                    }
                })
            }
        }
    }

    if (showLicenses) XdSheet(onDismiss = { showLicenses = false }, title = stringResource(R.string.ab_licenses), wide = true, actions = {
        XdButton(stringResource(R.string.common_ok), { showLicenses = false }, kind = XdButtonKind.PRIMARY)
    }) {
        AndroidView(factory = { ctx -> WebView(ctx).apply { loadUrl("file:///android_asset/licenses.html") } },
            modifier = Modifier.fillMaxWidth().height(380.dp))
    }
}

@Composable
private fun ShortcutRow(title: String, icon: ImageVector, onClick: () -> Unit, divider: Boolean = true) {
    XdListRow(title, icon = icon, onClick = onClick, divider = divider) {
        Icon(XdIcons.chevR, null, Modifier.size(18.dp), tint = Xd.colors.fg3)
    }
}
