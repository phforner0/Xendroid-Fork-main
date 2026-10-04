package xendroid.compose.ui.content

import android.content.Context
import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import xendroid.compose.core.ContentPaths
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdText
import java.io.File

/** Shared install flow state for both the per-game ContentManager and the global
 *  install-only entrypoint. Delete stays per-game (see ContentManagerViewModel). */
sealed interface ContentInstallState {
    data object Idle : ContentInstallState
    data class Busy(val message: String, val progress: Float = -1f) : ContentInstallState
    /** Existing package dir found -> ask the user before clobbering. */
    data class ConfirmOverwrite(val srcPath: String, val displayName: String) : ContentInstallState
    data class Done(val message: String) : ContentInstallState
    data class Failed(val message: String) : ContentInstallState
}

/** Native install_content status -> human message. Shared by both VMs. */
fun installReasonFor(context: Context, status: Int): String = when (status) {
    -1 -> context.getString(R.string.pf_no_emulator)
    0xC000000D.toInt() -> context.getString(R.string.ci_corrupt)          // X_STATUS_INVALID_PARAMETER
    0xC0000022.toInt() -> context.getString(R.string.ci_write_failed)     // X_STATUS_ACCESS_DENIED
    0xC000007F.toInt() -> context.getString(R.string.ci_disk_full)        // X_STATUS_DISK_FULL
    else -> context.getString(R.string.ci_failed_code, status.toUInt().toString(16))
}

/** Free-space pre-flight against the volume [target] lives on; 1.1x mirrors the native
 *  guard. Null when it fits or the size/free space is unknown (defer to the native guard). */
fun storageShortfallOn(context: Context, target: File, requiredBytes: Long): String? {
    if (requiredBytes <= 0L) return null
    val probe = if (target.exists()) target else (target.parentFile ?: target)
    val free = probe.usableSpace.takeIf { it > 0L } ?: return null
    val needed = (requiredBytes * 11) / 10
    if (free >= needed) return null
    return context.getString(R.string.ci_shortfall, formatBytes(needed), formatBytes(free))
}

/** Free-space check for the content root (the native install volume), shared by both VMs. */
fun storageShortfall(context: Context, requiredBytes: Long): String? =
    storageShortfallOn(context, ContentPaths.contentRoot(), requiredBytes)

private fun formatBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val u = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = -1
    do { v /= 1024.0; i++ } while (v >= 1024.0 && i < u.lastIndex)
    return "%.1f %s".format(v, u[i])
}

/** Busy / ConfirmOverwrite / Done / Failed sheets for the shared install flow. */
@Composable
fun ContentInstallDialogs(
    state: ContentInstallState,
    onDismiss: () -> Unit,
    onConfirmOverwrite: (srcPath: String, displayName: String) -> Unit,
) {
    val c = Xd.colors
    when (val s = state) {
        is ContentInstallState.Busy -> XdSheet(onDismiss = {}, title = null, dismissible = false) {
            Text(s.message, style = XdText.label, color = c.fg)
            if (s.progress >= 0f) {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), color = c.acc, trackColor = c.s3)
                Text(stringResource(R.string.lib_compress_progress, (s.progress * 100).toInt()), style = XdText.note, color = c.fg3)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.acc, trackColor = c.s3)
                Text(stringResource(R.string.lib_may_take_while), style = XdText.note, color = c.fg3)
            }
        }
        is ContentInstallState.ConfirmOverwrite -> XdSheet(onDismiss = onDismiss, title = stringResource(R.string.ci_already_title), actions = {
            XdButton(stringResource(R.string.common_cancel), onDismiss, kind = XdButtonKind.GHOST)
            XdButton(stringResource(R.string.ci_overwrite), { onConfirmOverwrite(s.srcPath, s.displayName) }, kind = XdButtonKind.PRIMARY)
        }) {
            Text(stringResource(R.string.ci_already_text, s.displayName), style = XdText.body, color = c.fg2)
        }
        is ContentInstallState.Done -> XdSheet(onDismiss = onDismiss, title = stringResource(R.string.common_done), actions = {
            XdButton(stringResource(R.string.common_ok), onDismiss, kind = XdButtonKind.PRIMARY)
        }) {
            XdNote(s.message, tone = NoteTone.OK)
        }
        is ContentInstallState.Failed -> XdSheet(onDismiss = onDismiss, title = stringResource(R.string.pf_failed), actions = {
            XdButton(stringResource(R.string.common_ok), onDismiss, kind = XdButtonKind.PRIMARY)
        }) {
            XdNote(s.message, tone = NoteTone.ERROR)
        }
        ContentInstallState.Idle -> {}
    }
}
