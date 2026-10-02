package xendroid.compose.ui.benchmark

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
            title = { Text("Compare runs") },
            navigationIcon = {
                IconButton(onClick = { if (title != null) title = null else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Play the same scene with one change (driver, frame generation, FPS limit, a setting), closing the " +
                    "game between runs, in the order A B B A. Mark a scene from the in-game menu (Session → Mark scene) to " +
                    "line runs up. FPS here are the game's own frames.", style = MaterialTheme.typography.bodySmall)
            }
            if (selected == null) {
                if (byTitle.isEmpty()) item { Text("No finished run with measurements yet.") }
                items(byTitle.entries.sortedByDescending { e -> e.value.maxOf { it.run.startedAt } }.toList(), key = { it.key }) { (id, runs) ->
                    ListItem(
                        headlineContent = { Text(MissingTitles.nameFromPath(runs.first().run.gamePath, id)) },
                        supportingContent = { Text("$id · ${runs.size} run(s)") },
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
            Text("median ${b.medianFps} FPS · 5th pct ${b.lowFps} · 99% of frames ${b.frameTimeP99Ms?.let { "< $it ms" } ?: "n/a"} · " +
                "${b.sampledSeconds} s · ${b.driver ?: "driver ?"}${if (b.frameGeneration) " · FG" else ""}" +
                (b.batteryStartC?.let { " · started %.0f °C".format(it) } ?: "") +
                (if (b.fpsLimits.isNotEmpty()) " · limit " + b.fpsLimits.joinToString("/") { if (it == 0) "off" else "$it" } else "") +
                (b.refreshCap?.let { if (it) " · vblank capped" else " · vblank uncapped" } ?: "") +
                (if (b.displayHz.isNotEmpty()) " · " + b.displayHz.joinToString("/") + " Hz" else "") +
                (if (b.markers > 0) " · ${b.markers} marker(s)" else ""))
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
            if (result.order.isNotEmpty()) Text("Order: ${result.order}${if (result.balanced) " (balanced)" else ""}",
                style = MaterialTheme.typography.bodySmall)
            if (result.changed.size == 1) Text("What changed: ${result.changed.single()}", style = MaterialTheme.typography.bodySmall)
            result.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            listOf("A" to result.a, "B" to result.b).forEach { (name, side) ->
                side?.let {
                    Text("$name: ${it.runs} run(s), median %.1f FPS, 5th pct %.1f%s".format(it.medianFps, it.lowFps,
                        it.frameTimeP99Ms?.let { p -> ", 99%% of frames < %.0f ms".format(p) } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (result.pairDeltas.isNotEmpty()) {
                Text("B − A per pair: ${result.pairDeltas.joinToString { if (it > 0) "+$it" else "$it" }} FPS (median)",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
