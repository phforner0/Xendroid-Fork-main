package xendroid.compose.ui.benchmark

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xendroid.compose.data.MissingTitles
import xendroid.compose.sessions.BenchRun
import xendroid.compose.sessions.Benchmark
import xendroid.compose.sessions.SessionRun
import xendroid.compose.sessions.SessionRuns

/** A finished run with what a comparison needs. */
private data class Candidate(val run: SessionRun, val bench: BenchRun)

/**
 * C07: compare finished runs of one game as A and B (one change: driver, frame generation, a
 * setting), in the order that cancels warming up, with what was not kept fixed said first.
 * Guest FPS only: synthetic frames never count as game frames. Reads the run history; writes
 * nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(onBack: () -> Unit) {
    var byTitle by remember { mutableStateOf<Map<String, List<Candidate>>>(emptyMap()) }
    var title by rememberSaveable { mutableStateOf<String?>(null) }
    val labels = remember { mutableStateMapOf<String, Char>() }
    LaunchedEffect(Unit) {
        byTitle = withContext(Dispatchers.IO) {
            runCatching {
                val store = SessionRuns.store()
                store.runs().filter { it.state.final && it.titleId != null && (it.performance?.sampledSeconds ?: 0) > 0 }
                    .map { run ->
                        val perf = run.performance!!
                        val markers = store.events(run.runId)?.events?.count { it.kind == "marker" } ?: 0
                        Candidate(run, BenchRun(' ', run.startedAt, perf.fpsPercentile(0.5) ?: 0, perf.fpsPercentile(0.05) ?: 0,
                            perf.frameTimeUpperMs(0.99), perf.sampledSeconds, run.driver?.label,
                            perf.frameGenerationSeconds > 0, perf.batteryStartC, markers,
                            perf.fpsLimits, perf.displayHz, perf.guestRefreshCap))
                    }
                    .groupBy { it.run.titleId!!.uppercase() }
            }.getOrDefault(emptyMap())
        }
    }
    val selected = title?.let { byTitle[it] }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.lib_menu_compare_runs)) },
            navigationIcon = {
                IconButton(onClick = { if (title != null) title = null else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(R.string.bm_intro), style = MaterialTheme.typography.bodySmall)
            }
            if (selected == null) {
                if (byTitle.isEmpty()) item { Text(stringResource(R.string.bm_none)) }
                items(byTitle.entries.sortedByDescending { e -> e.value.maxOf { it.run.startedAt } }.toList(), key = { it.key }) { (id, runs) ->
                    ListItem(
                        headlineContent = { Text(MissingTitles.nameFromPath(runs.first().run.gamePath, id)) },
                        supportingContent = { Text(stringResource(R.string.bm_runs, id, runs.size)) },
                        modifier = Modifier.clickable { title = id; labels.clear() },
                    )
                }
            } else {
                items(selected.sortedByDescending { it.run.startedAt }, key = { it.run.runId }) { candidate ->
                    RunRow(candidate, labels[candidate.run.runId]) { label ->
                        if (label == null) labels.remove(candidate.run.runId) else labels[candidate.run.runId] = label
                    }
                }
                item {
                    val chosen = selected.mapNotNull { c -> labels[c.run.runId]?.let { c.bench.copy(label = it) } }
                    ResultCard(Benchmark.compare(chosen))
                }
            }
        }
    }
}

@Composable
private fun RunRow(candidate: Candidate, label: Char?, onLabel: (Char?) -> Unit) {
    val b = candidate.bench
    ListItem(
        headlineContent = {
            Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                .format(java.util.Date(candidate.run.startedAt)))
        },
        supportingContent = {
            val off = stringResource(R.string.bm_off)
            Text(listOfNotNull(
                stringResource(R.string.bm_run_line, b.medianFps, b.lowFps, b.frameTimeP99Ms?.let { "< $it ms" } ?: stringResource(R.string.bm_na)),
                "${b.sampledSeconds} s",
                b.driver ?: stringResource(R.string.bm_driver_unknown),
                "FG".takeIf { b.frameGeneration },
                b.batteryStartC?.let { stringResource(R.string.bm_started_at, it) },
                b.fpsLimits.takeIf { it.isNotEmpty() }?.let { limits ->
                    stringResource(R.string.bm_limit, limits.joinToString("/") { if (it == 0) off else "$it" })
                },
                b.refreshCap?.let { stringResource(if (it) R.string.bm_vblank_capped else R.string.bm_vblank_uncapped) },
                b.displayHz.takeIf { it.isNotEmpty() }?.let { it.joinToString("/") + " Hz" },
                b.markers.takeIf { it > 0 }?.let { stringResource(R.string.bm_markers, it) },
            ).joinToString(" · "))
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = label == 'A', onClick = { onLabel(if (label == 'A') null else 'A') }, label = { Text("A") })
                FilterChip(selected = label == 'B', onClick = { onLabel(if (label == 'B') null else 'B') }, label = { Text("B") })
            }
        },
    )
}

@Composable
private fun ResultCard(result: Benchmark.Result) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(result.verdict, style = MaterialTheme.typography.titleSmall)
            if (result.order.isNotEmpty()) Text(stringResource(R.string.bm_order, result.order) +
                if (result.balanced) " " + stringResource(R.string.bm_balanced) else "",
                style = MaterialTheme.typography.bodySmall)
            if (result.changed.size == 1) Text(stringResource(R.string.bm_changed, result.changed.single()), style = MaterialTheme.typography.bodySmall)
            result.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            listOf("A" to result.a, "B" to result.b).forEach { (name, side) ->
                side?.let {
                    Text(stringResource(R.string.bm_side, name, it.runs, it.medianFps, it.lowFps) +
                        (it.frameTimeP99Ms?.let { p -> ", " + stringResource(R.string.bm_side_p99, p) } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (result.pairDeltas.isNotEmpty()) {
                Text(stringResource(R.string.bm_pairs, result.pairDeltas.joinToString { if (it > 0) "+$it" else "$it" }),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
