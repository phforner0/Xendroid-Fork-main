package xendroid.compose.ui.diagnostics

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.R
import xendroid.compose.core.SessionLogs
import xendroid.compose.core.diagnosticsShareIntent
import xendroid.compose.data.MissingTitles
import xendroid.compose.sessions.RunState
import xendroid.compose.sessions.SessionRun
import xendroid.compose.sessions.formatPlayTime
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdChip
import xendroid.compose.ui.design.XdEmpty
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdKv
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdSingleScreen
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTwoColumns
import xendroid.compose.ui.settings.ExportLogsButton

/** Names and covers of games, from the library. */
class DiagnosticsLinks(
    val gameName: (titleId: String) -> String? = { null },
    val gameArt: (titleId: String) -> Any? = { null },
)

private enum class DiagFilter { ALL, GAME, PROBLEMS }

/**
 * Lote 5: the kept log sessions, each with the games that ran in it and how it went (ended,
 * ended by Android, failed, with the reason), a summary before sharing (time played, driver,
 * FPS, first frame, pipelines, battery; where it crashed), and what goes in the shared file and
 * what is taken out first. Sharing sends a cleaned copy; the logs on the device stay as they are.
 * Opened from a game, the list starts filtered to it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen(
    titleId: String?,
    onBack: () -> Unit,
    links: DiagnosticsLinks = DiagnosticsLinks(),
    load: suspend () -> List<DiagSession> = { DiagnosticsSessions.load() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalXdToast.current
    val c = Xd.colors
    var sessions by remember { mutableStateOf<List<DiagSession>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(if (titleId != null) DiagFilter.GAME else DiagFilter.ALL) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { sessions = withContext(Dispatchers.IO) { runCatching { load() }.getOrDefault(emptyList()) } }
    fun share(id: String?) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val zip = withContext(Dispatchers.IO) { SessionLogs.exportRedactedForSharing(context, id) }
                if (zip != null) context.startActivity(diagnosticsShareIntent(context, zip))
                else toast.show(context.getString(R.string.dg_none))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                toast.show(context.getString(R.string.dg_export_failed))
            } finally { busy = false }
        }
    }
    val name: (SessionRun) -> String = { run ->
        run.titleId?.let { links.gameName(it.uppercase()) } ?: MissingTitles.nameFromPath(run.gamePath, run.titleId.orEmpty())
    }
    val titleName: (String) -> String = { t -> links.gameName(t.uppercase()) ?: t.uppercase() }
    val all = sessions
    val shown = all.orEmpty().filter { s ->
        when (filter) {
            DiagFilter.ALL -> true
            DiagFilter.GAME -> titleId != null && s.titles.any { it.equals(titleId, ignoreCase = true) }
            DiagFilter.PROBLEMS -> s.outcome == DiagSession.Outcome.FAILED || s.outcome == DiagSession.Outcome.INTERRUPTED
        }
    }

    XdSingleScreen(
        title = stringResource(R.string.lib_menu_diagnostics),
        subtitle = all?.let { pluralStringResource(R.plurals.xd_dg_sessions, it.size, it.size) },
        onBack = onBack,
        headIcon = XdIcons.bug,
    ) {
        BoxWithConstraints {
            val narrow = maxWidth < 560.dp
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                XdNote(stringResource(R.string.dg_intro), tone = NoteTone.INFO)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    XdChip(stringResource(R.string.xd_dg_all), filter == DiagFilter.ALL, { filter = DiagFilter.ALL }, count = all?.size)
                    if (titleId != null) XdChip(titleName(titleId), filter == DiagFilter.GAME, { filter = DiagFilter.GAME })
                    XdChip(stringResource(R.string.xd_dg_problems), filter == DiagFilter.PROBLEMS, { filter = DiagFilter.PROBLEMS },
                        icon = XdIcons.alert)
                    XdButton(stringResource(R.string.dg_share_all), { share(null) }, kind = XdButtonKind.PRIMARY, size = XdButtonSize.SM,
                        icon = XdIcons.share, enabled = !busy && !all.isNullOrEmpty())
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.acc, trackColor = c.s3)
                when {
                    all == null -> XdEmpty(stringResource(R.string.xd_cm_loading))
                    all.isEmpty() -> XdEmpty(stringResource(R.string.xd_dg_none))
                    shown.isEmpty() -> XdEmpty(stringResource(if (filter == DiagFilter.GAME) R.string.dg_no_session else R.string.xd_dg_none_filter))
                    else -> XdCard(Modifier.fillMaxWidth()) {
                        Column {
                            shown.forEachIndexed { i, s ->
                                SessionRow(s, name, links.gameArt, divider = i < shown.lastIndex, narrow = narrow, busy = busy,
                                    onOpen = { openedId = s.id }, onShare = { share(s.id) })
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.xd_dg_export_note), style = XdText.note, color = c.fg3, modifier = Modifier.weight(1f))
                    ExportLogsButton()
                }
            }
        }
    }

    all?.firstOrNull { it.id == openedId }?.let { s ->
        SessionSheet(s, name, busy, onShare = { share(s.id) }, onDismiss = { openedId = null })
    }
}

/** "Today 21:30", "Yesterday 23:10", "2 Oct 19:05": the day as people say it, and the time. */
private fun whenText(context: android.content.Context, millis: Long): String {
    val day = DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_ABBREV_MONTH)
    return "$day " + DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)
}

@Composable
private fun outcomeColor(outcome: DiagSession.Outcome): Color {
    val c = Xd.colors
    return when (outcome) {
        DiagSession.Outcome.ENDED -> c.ok
        DiagSession.Outcome.INTERRUPTED -> c.warn
        DiagSession.Outcome.FAILED -> c.errText
        DiagSession.Outcome.RUNNING -> c.acc
        DiagSession.Outcome.NO_GAME -> c.fg3
    }
}

/** How a run ended, in the shown language, with the reason when it did not end normally. */
@Composable
private fun runOutcomeText(run: SessionRun): String = when (run.state) {
    RunState.ENDED -> stringResource(R.string.xd_dg_ended)
    RunState.INTERRUPTED -> listOfNotNull(stringResource(R.string.xd_dg_interrupted), run.endReason).joinToString(": ")
    RunState.FAILED -> listOfNotNull(stringResource(R.string.xd_dg_failed), run.endReason).joinToString(": ")
    else -> stringResource(R.string.xd_dg_running)
}

@Composable
private fun sessionOutcomeText(s: DiagSession): String? = s.telling?.let { runOutcomeText(it) }

/** The games of a session, by name; "No game opened" when it had none. */
@Composable
private fun gamesText(s: DiagSession, name: (SessionRun) -> String): String =
    s.runs.distinctBy { it.titleId ?: it.gamePath }.joinToString { name(it) }.ifEmpty { stringResource(R.string.xd_dg_no_game) }

private fun shortDriver(run: SessionRun?): String? =
    run?.driver?.let { d -> listOf(d.driverName, d.driverInfo).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null } }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionRow(
    s: DiagSession,
    name: (SessionRun) -> String,
    art: (String) -> Any?,
    divider: Boolean,
    narrow: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val c = Xd.colors
    val context = LocalContext.current
    val title = gamesText(s, name) + " · " + if (s.current) stringResource(R.string.xd_dg_current) else whenText(context, s.startedAt ?: s.endedAt)
    val line = listOfNotNull(
        s.playedMs.takeIf { it > 0 }?.let { formatPlayTime(it) },
        shortDriver(s.telling),
        Formatter.formatShortFileSize(context, s.bytes),
        sessionOutcomeText(s),
    ).joinToString(" · ")
    val cover = s.runs.lastOrNull { it.titleId != null }?.titleId?.let { art(it.uppercase()) }
    val actions: @Composable () -> Unit = {
        XdButton(stringResource(R.string.xd_dg_summary), onOpen, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
        XdButton(stringResource(R.string.xd_dg_share), onShare, size = XdButtonSize.SM, icon = XdIcons.share, enabled = !busy)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().then(if (c.controller) Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(c.s1) else Modifier)
                .padding(horizontal = if (c.controller) 12.dp else 2.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(outcomeColor(s.outcome)))
            Cover(cover, if (s.runs.isEmpty()) XdIcons.bug else XdIcons.disc)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column {
                    Text(title, style = XdText.label, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(line, style = XdText.small, color = c.fg3, modifier = Modifier.padding(top = 2.dp))
                }
                if (narrow) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
            if (!narrow) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        if (divider && !c.controller) HorizontalDivider(thickness = 1.dp, color = c.line)
    }
}

@Composable
private fun Cover(art: Any?, fallback: ImageVector) {
    val c = Xd.colors
    val shape = RoundedCornerShape(6.dp)
    if (art != null) AsyncImage(art, null, Modifier.width(30.dp).aspectRatio(0.75f).clip(shape), contentScale = ContentScale.Crop)
    else Box(Modifier.width(30.dp).aspectRatio(0.75f).clip(shape).background(c.s2), contentAlignment = Alignment.Center) {
        Icon(fallback, null, Modifier.size(15.dp), tint = c.fg3)
    }
}

/** The session before it is shared: each run's numbers, where a crash happened, and what the file holds. */
@Composable
private fun SessionSheet(s: DiagSession, name: (SessionRun) -> String, busy: Boolean, onShare: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = Xd.colors
    XdSheet(
        onDismiss = onDismiss,
        title = gamesText(s, name) + " · " + if (s.current) stringResource(R.string.xd_dg_current) else whenText(context, s.startedAt ?: s.endedAt),
        subtitle = sessionOutcomeText(s),
        wide = true,
        actions = {
            XdButton(stringResource(R.string.xd_close), onDismiss, kind = XdButtonKind.GHOST)
            XdButton(stringResource(R.string.xd_dg_share_this), onShare, kind = XdButtonKind.PRIMARY, icon = XdIcons.share, enabled = !busy)
        },
    ) {
        if (s.runs.isEmpty()) XdNote(stringResource(R.string.xd_dg_no_game))
        s.runs.forEach { run ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.runs.size > 1) Text(name(run) + " · " + runOutcomeText(run), style = XdText.label, color = c.fg)
                RunNumbers(run)
            }
        }
        XdKv(listOf(stringResource(R.string.xd_dg_k_size) to Formatter.formatShortFileSize(context, s.bytes)))
        Privacy()
    }
}

@Composable
private fun RunNumbers(run: SessionRun) {
    val context = LocalContext.current
    val c = Xd.colors
    val perf = run.performance
    val fps = perf?.fpsPercentile(0.5)
    val p99 = perf?.frameTimeUpperMs(0.99)
    val heat = perf?.batteryStartC
    val hottest = perf?.batteryMaxC ?: perf?.batteryEndC
    XdKv(listOfNotNull(
        stringResource(R.string.xd_dg_k_started) to whenText(context, run.startedAt),
        run.playedMs?.let { stringResource(R.string.xd_dg_k_played) to formatPlayTime(it) },
        run.driver?.let { stringResource(R.string.xd_dg_k_driver) to it.label },
        fps?.let { stringResource(R.string.xd_dg_k_fps) to (p99?.let { ms -> stringResource(R.string.xd_dg_v_fps, fps, ms) } ?: "$fps FPS") },
        perf?.firstFrameSeconds?.let { stringResource(R.string.xd_dg_k_first) to stringResource(R.string.xd_dg_v_seconds, it) },
        perf?.pipelineCreations?.let { stringResource(R.string.xd_dg_k_pipes) to NumberFormat.getIntegerInstance().format(it) },
        if (heat != null && hottest != null) stringResource(R.string.xd_dg_k_battery) to stringResource(R.string.xd_dg_v_battery, heat, hottest) else null,
    ))
    run.nativeBacktrace?.let { crash ->
        Text(stringResource(R.string.xd_dg_crash).uppercase(), style = XdText.cardHead, color = c.fg3)
        val lines = listOfNotNull(crash.signal, crash.cause, crash.abortMessage, crash.thread) + crash.frames.take(4)
        Text(lines.joinToString("\n"), style = XdText.mono.copy(fontSize = 11.5.sp, lineHeight = 17.sp), color = Color(0xFFD7E2DB),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A0E0C)).padding(horizontal = 14.dp, vertical = 12.dp))
    }
}

/** What goes in the shared file and what is taken out before (SanitizedSessionExport, LogRedactor). */
@Composable
private fun Privacy() {
    BoxWithConstraints {
        XdTwoColumns(maxWidth > 460.dp, left = {
            PrivacyCard(stringResource(R.string.xd_dg_goes), XdIcons.check, listOf(R.string.xd_dg_goes_1, R.string.xd_dg_goes_2, R.string.xd_dg_goes_3, R.string.xd_dg_goes_4))
        }, right = {
            PrivacyCard(stringResource(R.string.xd_dg_out), XdIcons.lock, listOf(R.string.xd_dg_out_1, R.string.xd_dg_out_2, R.string.xd_dg_out_3, R.string.xd_dg_out_4))
        })
    }
    XdNote(stringResource(R.string.xd_dg_out_note))
}

@Composable
private fun PrivacyCard(title: String, icon: ImageVector, items: List<Int>) {
    val c = Xd.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.s2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(14.dp), tint = c.fg3)
            Text(title.uppercase(), style = XdText.cardHead, color = c.fg3)
        }
        items.forEach { Text("• " + stringResource(it), style = XdText.bodySm, color = c.fg2) }
    }
}
