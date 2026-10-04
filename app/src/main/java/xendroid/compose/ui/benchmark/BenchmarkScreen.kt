package xendroid.compose.ui.benchmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xendroid.compose.R
import xendroid.compose.data.MissingTitles
import xendroid.compose.sessions.BenchRun
import xendroid.compose.sessions.Benchmark
import xendroid.compose.sessions.SessionRun
import xendroid.compose.sessions.SessionRuns
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
import xendroid.compose.ui.design.XdSegmented
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdSingleScreen
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTwoColumns

/** A finished run with what a comparison needs. */
data class BenchCandidate(val run: SessionRun, val bench: BenchRun)

/** The finished, measured runs of the run history, by title (uppercase). Reads; writes nothing. */
object BenchmarkRuns {
    fun load(): Map<String, List<BenchCandidate>> = runCatching {
        val store = SessionRuns.store()
        store.runs().filter { it.state.final && it.titleId != null && (it.performance?.sampledSeconds ?: 0) > 0 }
            .map { run ->
                val perf = run.performance!!
                val markers = store.events(run.runId)?.events?.count { it.kind == "marker" } ?: 0
                BenchCandidate(run, BenchRun(' ', run.startedAt, perf.fpsPercentile(0.5) ?: 0, perf.fpsPercentile(0.05) ?: 0,
                    perf.frameTimeUpperMs(0.99), perf.sampledSeconds, run.driver?.label,
                    perf.frameGenerationSeconds > 0, perf.batteryStartC, markers,
                    perf.fpsLimits, perf.displayHz, perf.guestRefreshCap))
            }
            .groupBy { it.run.titleId!!.uppercase() }
    }.getOrDefault(emptyMap())
}

/** Games' names, from the library. */
class BenchmarkLinks(val gameName: (titleId: String) -> String? = { null })

/** The colours of the two sides, as in the chart and the run badges. */
private val SIDE_A = Color(0xFF4F8EF0)
private val SIDE_B = Color(0xFFE5873E)

private fun sideColor(label: Char?): Color? = when (label) { 'A' -> SIDE_A; 'B' -> SIDE_B; else -> null }

/**
 * C07, lote 5: a game's measured runs, each marked A, B or left out, beside the result: the
 * verdict with what keeps it from being one (order, warming up, short runs, more than one
 * change), the runs in the order they ran coloured by side (warming up shows), the two sides and
 * B − A per pair. Guest FPS only: synthetic frames never count as game frames. "How to measure"
 * is a sheet instead of the long text on top. [labels]: run id → 'A'/'B'.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BenchmarkScreen(
    onBack: () -> Unit,
    titleId: String? = null,
    links: BenchmarkLinks = BenchmarkLinks(),
    labels: SnapshotStateMap<String, Char> = remember { mutableStateMapOf() },
    load: suspend () -> Map<String, List<BenchCandidate>> = { BenchmarkRuns.load() },
) {
    var byTitle by remember { mutableStateOf<Map<String, List<BenchCandidate>>?>(null) }
    var title by rememberSaveable { mutableStateOf(titleId?.uppercase()) }
    var how by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { byTitle = withContext(Dispatchers.IO) { load() } }
    val loaded = byTitle
    val games = loaded.orEmpty().entries.sortedByDescending { e -> e.value.maxOf { it.run.startedAt } }
    val current = title?.takeIf { loaded?.containsKey(it) == true } ?: games.firstOrNull()?.key
    val nameOf: (String) -> String = { id ->
        links.gameName(id) ?: loaded?.get(id)?.firstOrNull()?.let { MissingTitles.nameFromPath(it.run.gamePath, id) } ?: id
    }

    XdSingleScreen(
        title = stringResource(R.string.lib_menu_compare_runs),
        subtitle = stringResource(R.string.xd_bm_sub),
        onBack = onBack,
        headIcon = XdIcons.ab,
        actions = { XdButton(stringResource(R.string.xd_bm_how), { how = true }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.info) },
    ) {
        when {
            loaded == null -> XdEmpty(stringResource(R.string.xd_cm_loading))
            games.isEmpty() || current == null -> {
                XdEmpty(stringResource(R.string.bm_none)) {
                    XdButton(stringResource(R.string.xd_bm_how), { how = true }, size = XdButtonSize.SM, icon = XdIcons.info)
                }
                XdNote(stringResource(R.string.xd_bm_none_lead), modifier = Modifier.padding(horizontal = 10.dp))
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    games.forEach { (id, runs) ->
                        XdChip(nameOf(id), id == current, { if (id != current) { title = id; labels.clear() } }, count = runs.size)
                    }
                }
                val runs = loaded[current].orEmpty().sortedBy { it.run.startedAt }
                val chosen = runs.mapNotNull { c -> labels[c.run.runId]?.let { c.bench.copy(label = it) } }
                val result = Benchmark.compare(chosen)
                BoxWithConstraints {
                    XdTwoColumns(maxWidth > 700.dp, left = {
                        RunsCard(nameOf(current), runs, labels)
                    }, right = {
                        ResultCard(result, runs, labels)
                    })
                }
            }
        }
    }
    if (how) HowSheet { how = false }
}

private fun whenText(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

@Composable
private fun RunsCard(game: String, runs: List<BenchCandidate>, labels: SnapshotStateMap<String, Char>) {
    XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_bm_runs_of, game), icon = XdIcons.timeline,
        trailing = pluralStringResource(R.plurals.xd_bm_runs_n, runs.size, runs.size)) {
        Column {
            runs.forEachIndexed { i, candidate ->
                RunRow(i + 1, candidate, labels[candidate.run.runId]) { label ->
                    if (label == null) labels.remove(candidate.run.runId) else labels[candidate.run.runId] = label
                }
                if (i < runs.lastIndex && !Xd.colors.controller) HorizontalDivider(thickness = 1.dp, color = Xd.colors.line)
            }
        }
    }
}

/** A side badge: A or B in its colour, or the run's number when it is left out. */
@Composable
private fun SideBadge(label: Char?, number: Int) {
    val c = Xd.colors
    Box(Modifier.size(30.dp).clip(CircleShape).background(sideColor(label) ?: c.s3), contentAlignment = Alignment.Center) {
        Text(label?.toString() ?: "$number", style = XdText.label.copy(fontSize = 13.sp), color = if (label != null) Color.White else c.fg2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunRow(number: Int, candidate: BenchCandidate, label: Char?, onLabel: (Char?) -> Unit) {
    val c = Xd.colors
    val b = candidate.bench
    val off = stringResource(R.string.bm_off)
    // The title says the median; the driver by its name and version (the comparison keeps the full label).
    val driver = candidate.run.driver?.let { d -> listOf(d.driverName, d.driverInfo).filter { it.isNotBlank() }.joinToString(" ") }?.ifBlank { null }
    val details = listOfNotNull(
        stringResource(R.string.xd_bm_run_details, b.lowFps, b.frameTimeP99Ms?.let { "< $it ms" } ?: stringResource(R.string.bm_na)),
        "${b.sampledSeconds} s",
        driver ?: b.driver ?: stringResource(R.string.bm_driver_unknown),
        "FG".takeIf { b.frameGeneration },
        b.batteryStartC?.let { stringResource(R.string.bm_started_at, it) },
        b.fpsLimits.takeIf { it.isNotEmpty() }?.let { limits -> stringResource(R.string.bm_limit, limits.joinToString("/") { if (it == 0) off else "$it" }) },
        b.refreshCap?.let { stringResource(if (it) R.string.bm_vblank_capped else R.string.bm_vblank_uncapped) },
        b.displayHz.takeIf { it.isNotEmpty() }?.let { it.joinToString("/") + " Hz" },
        b.markers.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.bm_markers, it, it) },
    ).joinToString(" · ")
    val whenText = whenText(candidate.run.startedAt)
    Row(
        Modifier.fillMaxWidth().then(if (c.controller) Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(c.s1) else Modifier)
            .padding(horizontal = if (c.controller) 12.dp else 2.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SideBadge(label, number)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.xd_bm_run_title, whenText, b.medianFps), style = XdText.label, color = c.fg)
            Text(details, style = XdText.small, color = c.fg3)
            val description = stringResource(R.string.xd_bm_side_cd, whenText)
            XdSegmented(listOf('A' to "A", '-' to "–", 'B' to "B"), label ?: '-', { onLabel(it.takeIf { side -> side != '-' }) },
                modifier = Modifier.semantics { contentDescription = description }, compact = true)
        }
    }
}

@Composable
private fun ResultCard(result: Benchmark.Result, runs: List<BenchCandidate>, labels: SnapshotStateMap<String, Char>) {
    val c = Xd.colors
    XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_bm_result), icon = XdIcons.ab) {
        if (result.outcome == Benchmark.Verdict.NotEnough && labels.isEmpty()) {
            XdNote(stringResource(R.string.xd_bm_pick), tone = NoteTone.INFO)
            return@XdCard
        }
        val tone = when (result.outcome) {
            Benchmark.Verdict.FixWarnings, is Benchmark.Verdict.Disagree -> NoteTone.WARN
            is Benchmark.Verdict.Faster, is Benchmark.Verdict.Slower, Benchmark.Verdict.Same -> NoteTone.OK
            else -> NoteTone.INFO
        }
        XdNote(verdictText(result.outcome), tone = tone)
        if (result.notes.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            result.notes.forEach { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(XdIcons.warn, null, Modifier.padding(top = 2.dp).size(14.dp), tint = c.warn)
                    Text(warningText(note), style = XdText.bodySm, color = c.fg2)
                }
            }
        }
        val marked = runs.filter { labels[it.run.runId] != null }
        if (result.a != null && result.b != null) {
            Bars(marked, labels)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend("A", SIDE_A); Legend("B", SIDE_B)
                Text(stringResource(R.string.xd_bm_legend), style = XdText.small, color = c.fg3, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf('A' to result.a, 'B' to result.b).forEach { (name, side) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.s2).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text("%.1f FPS".format(side.medianFps), style = XdText.kpi.copy(fontSize = 20.sp), color = sideColor(name) ?: c.fg)
                        Text(pluralStringResource(R.plurals.bm_side, side.runs, name.toString(), side.runs, side.medianFps, side.lowFps) +
                            (side.frameTimeP99Ms?.let { p -> ", " + stringResource(R.string.bm_side_p99, p) } ?: ""),
                            style = XdText.small, color = c.fg3, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
            XdKv(listOfNotNull(
                stringResource(R.string.xd_bm_k_order) to (result.order.toList().joinToString(" ") + if (result.balanced) " " + stringResource(R.string.bm_balanced) else ""),
                result.changes.singleOrNull()?.let { stringResource(R.string.xd_bm_k_changed) to changeText(it) },
                result.pairDeltas.takeIf { it.isNotEmpty() }?.let { d ->
                    stringResource(R.string.xd_bm_k_pairs) to stringResource(R.string.xd_bm_v_pairs, d.joinToString { if (it > 0) "+$it" else "$it" })
                },
            ))
        }
    }
}

/** The marked runs' median FPS in the order they ran, coloured by side. */
@Composable
private fun Bars(marked: List<BenchCandidate>, labels: SnapshotStateMap<String, Char>) {
    val c = Xd.colors
    val top = (marked.maxOfOrNull { it.bench.medianFps } ?: 1).coerceAtLeast(1) + 4
    Row(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(12.dp)).background(c.s2).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
        marked.forEach { run ->
            val label = labels[run.run.runId]
            Column(Modifier.width(38.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text("${run.bench.medianFps}", style = XdText.monoNum, color = c.fg)
                Box(Modifier.padding(vertical = 4.dp).width(26.dp).fillMaxHeight(run.bench.medianFps.toFloat() / top * 0.78f)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(sideColor(label) ?: c.s4))
                Text(label?.toString() ?: "", style = XdText.small, color = c.fg3, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(text, style = XdText.small, color = Xd.colors.fg2)
    }
}

/** How to measure, as steps. */
@Composable
private fun HowSheet(onDismiss: () -> Unit) {
    val c = Xd.colors
    XdSheet(onDismiss = onDismiss, title = stringResource(R.string.xd_bm_how), actions = {
        XdButton(stringResource(R.string.xd_bm_got_it), onDismiss, kind = XdButtonKind.PRIMARY)
    }) {
        val steps = listOf(stringResource(R.string.xd_bm_how_1), stringResource(R.string.xd_bm_how_2),
            stringResource(R.string.xd_bm_how_3, Benchmark.MIN_SECONDS), stringResource(R.string.xd_bm_how_4))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            steps.forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(c.s3), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = XdText.monoNum, color = c.fg2)
                    }
                    Text(step, style = XdText.bodySm, color = c.fg2, modifier = Modifier.weight(1f).padding(top = 1.dp))
                }
            }
        }
        XdNote(stringResource(R.string.xd_bm_how_5), tone = NoteTone.INFO)
    }
}
