package xendroid.compose.sessions

import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xendroid.compose.archive.ArchiveFiles

/** BEGIN → RUNNING → (ENDING) → ENDED, or FAILED/INTERRUPTED. The last three are final. */
enum class RunState { BEGIN, RUNNING, ENDING, ENDED, FAILED, INTERRUPTED;
    val final: Boolean get() = this == ENDED || this == FAILED || this == INTERRUPTED
}

/**
 * One guest execution (one :emu boot). Written by the :emu process while it runs and
 * finalized exactly once: by the host on a normal exit, or later by the frontend for a
 * process that died without finishing (crash, kill, OOM). Title ID comes from the core,
 * never from the launch intent. [lastSeenAt] bounds the duration of an interrupted run.
 */
@Serializable
data class SessionRun(
    val version: Int = 1,
    val runId: String,
    val state: RunState,
    val launchSource: String,
    val gamePath: String,
    val buildVersion: String,
    val pid: Int,
    val startedAt: Long,
    val titleId: String? = null,
    val runningAt: Long? = null,
    val lastSeenAt: Long,
    val endedAt: Long? = null,
    val endReason: String? = null,
    /** The Vulkan driver build the guest ran on, once the presenter reported it. */
    val driver: xendroid.compose.driver.DriverIdentity? = null,
    /** Latest summary written by the host (heartbeat or finish); absent for older runs. */
    val performance: RunPerformance? = null,
) {
    /** Time the title was actually running; null until it started. */
    val playedMs: Long? get() = runningAt?.let { start -> ((endedAt ?: lastSeenAt) - start).coerceAtLeast(0) }
}

/** What the frontend knows about a process that did not finalize its run. */
data class ProcessFate(val alive: Boolean, val crashed: Boolean = false, val reason: String? = null)

data class TitleActivity(val titleId: String, val lastPlayedAt: Long, val playedMs: Long, val runs: Int)

/** "<1 min", "42 min", "3 h 05 min" — play time as shown in the library. */
fun formatPlayTime(ms: Long): String {
    val minutes = (ms.coerceAtLeast(0) / 60_000)
    return when {
        minutes < 1 -> "<1 min"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${"%02d".format(minutes % 60)} min"
    }
}

/**
 * Durable run records in one directory (internal storage; both app processes). Every
 * transition is a read-check-write under one file lock, so the host finishing a run
 * and the frontend reconciling it can never both finalize it.
 */
class SessionRunStore(
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxRecords: Int = 300,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val idPattern = Regex("[0-9a-f-]{36}")
    private val titlePattern = Regex("[0-9A-F]{8}")

    private fun file(runId: String): File {
        require(idPattern.matches(runId)) { "Invalid run id" }
        return File(root, "$runId.json")
    }

    /** Flight recorder of a run, beside its record; not "*.json", so run listings never read it. */
    private fun eventsFile(runId: String): File {
        require(idPattern.matches(runId)) { "Invalid run id" }
        return File(root, "$runId$EVENTS")
    }

    /** Where the run's core leaves a fatal error's message before it aborts (xe_fatal_report.h). */
    fun fatalReportFile(runId: String): File {
        require(idPattern.matches(runId)) { "Invalid run id" }
        return File(root, "$runId$FATAL")
    }

    /** First line of the fatal message the run's process left, bounded; null when there is none. */
    private fun fatalMessage(runId: String): String? = runCatching {
        val file = fatalReportFile(runId)
        if (!file.isFile || file.length() > 16 * 1024) return null
        file.readText().lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?.filter { it >= ' ' }?.take(180)
    }.getOrNull()

    private fun <T> locked(block: () -> T): T = xendroid.compose.archive.withDirectoryLock(root, block)

    private fun read(file: File): SessionRun? = runCatching {
        if (!file.isFile || file.length() > 256 * 1024) return null
        json.decodeFromString<SessionRun>(file.readText())
    }.getOrNull()

    private fun write(run: SessionRun) = ArchiveFiles.atomicText(file(run.runId), json.encodeToString(run))

    /** Starts a run; the returned id is the handle for every later transition. */
    fun begin(launchSource: String, gamePath: String, buildVersion: String, pid: Int): SessionRun = locked {
        val now = clock()
        val run = SessionRun(runId = UUID.randomUUID().toString(), state = RunState.BEGIN,
            launchSource = launchSource.take(32), gamePath = gamePath.take(4096), buildVersion = buildVersion.take(128),
            pid = pid, startedAt = now, lastSeenAt = now)
        write(run)
        prune()
        run
    }

    /** First observation of the running title (from the core). Later calls only refresh lastSeenAt
     * and fill in the driver if it was not known yet. */
    fun running(runId: String, titleId: String, driver: xendroid.compose.driver.DriverIdentity? = null): SessionRun? =
        transition(runId) { run, now ->
            require(titlePattern.matches(titleId)) { "Invalid Title ID" }
            if (run.state == RunState.BEGIN) {
                run.copy(state = RunState.RUNNING, titleId = titleId, runningAt = now, lastSeenAt = now, driver = driver)
            } else run.copy(lastSeenAt = now, driver = run.driver ?: driver)
        }

    fun heartbeat(runId: String, performance: RunPerformance? = null,
                  driver: xendroid.compose.driver.DriverIdentity? = null): SessionRun? = transition(runId) { run, now ->
        run.copy(lastSeenAt = now, performance = performance ?: run.performance, driver = run.driver ?: driver)
    }

    /** The user chose to exit; the process may still be shutting down. */
    fun ending(runId: String, reason: String): SessionRun? = transition(runId) { run, now ->
        run.copy(state = RunState.ENDING, lastSeenAt = now, endReason = reason.take(200))
    }

    /** Final transition by the run's own process. A run that is already final is left as it is. */
    fun finish(runId: String, state: RunState, reason: String, performance: RunPerformance? = null): SessionRun? {
        require(state.final) { "Not a final state" }
        return transition(runId) { run, now ->
            run.copy(state = state, endedAt = now, lastSeenAt = now, endReason = run.endReason ?: reason.take(200),
                performance = performance ?: run.performance)
        }
    }

    /** Newest finished run of a title, or null. */
    fun lastRun(titleId: String): SessionRun? =
        runs().firstOrNull { it.titleId.equals(titleId, ignoreCase = true) && it.state.final }

    /** Replaces the flight recorder log of a run (C01); ignored once the run record is gone. */
    fun saveEvents(runId: String, log: RunEventLog) = locked {
        if (file(runId).isFile) ArchiveFiles.atomicText(eventsFile(runId), json.encodeToString(log))
    }

    /** The run's last flushed flight recorder log; null when there is none or it is damaged. */
    fun events(runId: String): RunEventLog? = locked {
        val file = eventsFile(runId)
        runCatching {
            if (!file.isFile || file.length() > 256 * 1024) null
            else json.decodeFromString<RunEventLog>(file.readText())
        }.getOrNull()
    }

    private fun transition(runId: String, change: (SessionRun, Long) -> SessionRun): SessionRun? = locked {
        val file = file(runId)
        val current = read(file) ?: return@locked null
        if (current.state.final) return@locked current
        change(current, clock()).also(::write)
    }

    /**
     * Finalizes runs whose process is gone without finishing them. [fate] answers for a
     * pid (alive, or how it died); a live process keeps its run open. Returns the runs
     * finalized by this call.
     */
    fun reconcile(fate: (Int) -> ProcessFate): List<SessionRun> = locked {
        root.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { file ->
            val run = read(file) ?: return@mapNotNull null
            if (run.state.final) return@mapNotNull null
            val process = fate(run.pid)
            if (process.alive) return@mapNotNull null
            // The core's own words beat the platform's "native crash" (e.g. GPU device lost).
            val fatal = fatalMessage(run.runId)
            val state = when {
                fatal != null -> RunState.FAILED
                run.state == RunState.ENDING -> RunState.ENDED     // exit was requested; it finished dying
                process.crashed -> RunState.FAILED
                else -> RunState.INTERRUPTED
            }
            val reason = fatal?.let { "fatal error: $it" } ?: run.endReason ?: process.reason
                ?: "process ended without finishing the run"
            run.copy(state = state, endedAt = run.lastSeenAt, endReason = reason).also(::write)
        }
    }

    fun runs(): List<SessionRun> = locked {
        root.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull(::read).sortedByDescending { it.startedAt }
    }

    /** Recents and play time per title, from runs whose title actually started. */
    fun titleActivity(): List<TitleActivity> = runs().filter { it.titleId != null && it.state.final }
        .groupBy { it.titleId!! }
        .map { (title, runs) ->
            TitleActivity(title, runs.maxOf { it.endedAt ?: it.lastSeenAt }, runs.sumOf { it.playedMs ?: 0L }, runs.size)
        }
        .sortedByDescending { it.lastPlayedAt }

    /** Keeps the newest [maxRecords] final runs; open runs are never pruned. A flight
     *  recorder log goes with its run, and one whose run is gone is removed too. */
    private fun prune() {
        val finals = root.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { f -> read(f)?.let { f to it } }.filter { it.second.state.final }
            .sortedByDescending { it.second.startedAt }
        finals.drop(maxRecords).forEach { it.first.delete() }
        for (suffix in listOf(EVENTS, FATAL)) {
            root.listFiles { f -> f.name.endsWith(suffix) }.orEmpty()
                .filter { !File(root, it.name.removeSuffix(suffix) + ".json").isFile }
                .forEach { it.delete() }
        }
        // A writer that died between creating and renaming its temp file leaves it behind.
        val stale = clock() - 24 * 60 * 60 * 1000L
        root.listFiles { f -> f.name.endsWith(".tmp") && f.lastModified() < stale }.orEmpty().forEach { it.delete() }
    }

    private companion object {
        const val EVENTS = ".events"
        const val FATAL = ".fatal"
    }
}

/** One line for the library: how the run ended and for how long the title ran. */
fun describeRun(run: SessionRun): String {
    val played = run.playedMs?.let { " after ${formatPlayTime(it)}" } ?: ""
    return when (run.state) {
        RunState.ENDED -> if (run.titleId != null && run.runningAt != null) "ended normally$played" else "closed before the game started"
        RunState.FAILED -> "failed$played: ${run.endReason ?: "unknown cause"}"
        RunState.INTERRUPTED -> "interrupted$played: ${run.endReason ?: "unknown cause"}"
        RunState.BEGIN, RunState.RUNNING, RunState.ENDING -> "still open"
    }
}
