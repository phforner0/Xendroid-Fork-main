package xendroid.compose.ui.ingame

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import xendroid.compose.R
import xendroid.compose.ui.design.CBackground
import xendroid.compose.ui.design.CHints
import xendroid.compose.ui.design.LocalSwapConfirm
import xendroid.compose.ui.design.WithCoverColors
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdHint
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.rememberCoverColors

/** Why the game did not start (batch 2), in the shown language, with the log lines around it. */
data class LaunchFailure(val message: String, val kind: Kind, val log: List<String> = emptyList()) {
    enum class Kind {
        /** The core did not start. */
        CORE,
        /** The core did not start with a custom driver selected: the system one may work. */
        DRIVER,
        /** Another save, profile or content operation holds the game's data. */
        BUSY,
        /** An interrupted save restore must be recovered first. */
        RECOVERY,
        OTHER,
    }
}

/**
 * When the game does not start: the reason in a sentence, the last lines of the log, and the
 * right next step for that reason (the system driver once, the saves, try again in a new
 * process), with sharing the logs and going back. Replaces the old dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LaunchFailureScreen(
    failure: LaunchFailure,
    art: File?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onShareLogs: () -> Unit,
    modifier: Modifier = Modifier,
    onRetrySystemDriver: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    val colors = rememberCoverColors(art)
    val first = remember { FocusRequester() }
    WithCoverColors(colors?.dyn, colors?.accent) {
        val c = Xd.colors
        Box(modifier.fillMaxSize().background(Color.Black)) {
            CBackground(art)
            Column(
                Modifier.align(Alignment.Center).windowInsetsPadding(WindowInsets.safeDrawing).widthIn(max = 680.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(20.dp)
                    .clip(RoundedCornerShape(20.dp)).background(c.solid(c.sheet)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(XdIcons.alert, null, Modifier.size(26.dp), tint = c.errText)
                    Text(stringResource(when (failure.kind) {
                        LaunchFailure.Kind.BUSY -> R.string.xd_fail_busy_title
                        LaunchFailure.Kind.RECOVERY -> R.string.xd_fail_recovery_title
                        else -> R.string.host_launch_failed
                    }), style = XdText.h1, color = c.fg)
                }
                Text(failure.message, style = XdText.body, color = c.fg2)
                if (failure.log.isNotEmpty()) Text(failure.log.joinToString("\n"), style = XdText.monoSm, color = Color(0xFFD7E2DB),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A0E0C)).padding(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val driver = onRetrySystemDriver?.takeIf { failure.kind == LaunchFailure.Kind.DRIVER }
                    if (driver != null) XdButton(stringResource(R.string.xd_fail_system_driver), driver, kind = XdButtonKind.PRIMARY,
                        icon = XdIcons.chip, modifier = Modifier.focusRequester(first))
                    XdButton(stringResource(R.string.host_try_again), onRetry, kind = if (driver == null) XdButtonKind.PRIMARY else XdButtonKind.SECONDARY,
                        icon = XdIcons.refresh, modifier = if (driver == null) Modifier.focusRequester(first) else Modifier)
                    XdButton(stringResource(R.string.menu_share_logs), onShareLogs, kind = XdButtonKind.GHOST, icon = XdIcons.share)
                    XdButton(stringResource(R.string.common_back), onBack, kind = XdButtonKind.GHOST)
                }
                Text(stringResource(R.string.xd_fail_note) + if (failure.kind == LaunchFailure.Kind.DRIVER) " " + stringResource(R.string.xd_fail_note_driver) else "",
                    style = XdText.note, color = c.fg3)
            }
            if (Xd.controller) {
                val swap = LocalSwapConfirm.current
                Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    CHints(listOf(XdHint(if (swap) "B" else "A", stringResource(R.string.xd_hint_select)),
                        XdHint(if (swap) "A" else "B", stringResource(R.string.xd_back), onBack)))
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
