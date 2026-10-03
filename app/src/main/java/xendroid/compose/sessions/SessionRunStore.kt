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
    /** L06: the XUID of P1's profile when the title started (the library names it); never
     *  part of a shared report. */
    val profileXuid: String? = null,
    /** L10: the game's module hashes as its patches were matched (main executable first), so
     *  the patches screen can tell which files are for this version. */
    val moduleHashes: List<String> = emptyList(),
    /** C06: the settings away from the core's defaults as the run booted, one line each
     *  ("GPU|framerate_limit = 30 (default 60) · this game"); null when not recorded (older
     *  runs, a title that never started), empty when everything was at its default. */
    val changedSettings: List<String>? = null,
    /** The crashing thread's backtrace from the platform's tombstone, for a native crash
     *  (API 31+), filled in when the run is reconciled after its process died. */
    val nativeBacktrace: NativeBacktrace? = null,
) {
    /** Time the title was actually running; null until it started. */
    val playedMs: Long? get() = runningAt?.let { start -> ((endedAt ?: lastSeenAt) - start).coerceAtLeast(0) }
}

/** What the frontend knows about a process that did not finalize its run. */
data class ProcessFate(val alive: Boolean, val crashed: Boolean = false, val reason: String? = null,
                       val backtrace: NativeBacktrace? = null)

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
    private val xuidPattern = Regex("[0-9A-F]{16}")
    private val hashPattern = Regex("[0-9A-F]{16}")

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
     * and fill in the driver (and the boot's changed settings) if they were not known yet. */
    fun running(runId: String, titleId: String, driver: xendroid.compose.driver.DriverIdentity? = null,
                profileXuid: String? = null, moduleHashes: List<String> = emptyList(),
                changedSettings: List<String>? = null): SessionRun? =
        transition(runId) { run, now ->
            require(titlePattern.matches(titleId)) { "Invalid Title ID" }
            val profile = profileXuid?.trim()?.uppercase()?.takeIf { xuidPattern.matches(it) }
            val settings = run.changedSettings ?: changedSettings?.let(::boundSettings)
            if (run.state == RunState.BEGIN) {
                run.copy(state = RunState.RUNNING, titleId = titleId, runningAt = now, lastSeenAt = now, driver = driver,
                    profileXuid = profile, moduleHashes = mergeHashes(run.moduleHashes, moduleHashes),
                    changedSettings = settings)
            } else run.copy(lastSeenAt = now, driver = run.driver ?: driver, profileXuid = run.profileXuid ?: profile,
                moduleHashes = mergeHashes(run.moduleHashes, moduleHashes), changedSettings = settings)
        }

    /** One printable line per setting, each and all of them bounded (the record stays small). */
    private fun boundSettings(lines: List<String>): List<String> =
        lines.map { line -> line.filter { it >= ' ' }.take(MAX_SETTING_CHARS) }.filter { it.isNotBlank() }.take(MAX_SETTINGS)

    fun heartbeat(runId: String, performance: RunPerformance? = null,
                  driver: xendroid.compose.driver.DriverIdentity? = null,
                  moduleHashes: List<String> = emptyList()): SessionRun? = transition(runId) { run, now ->
        run.copy(lastSeenAt = now, performance = performance ?: run.performance, driver = run.driver ?: driver,
            moduleHashes = mergeHashes(run.moduleHashes, moduleHashes))
    }

    /** Loading order kept (a DLL loads after the executable), each once, 16 hex digits, bounded. */
    private fun mergeHashes(known: List<String>, seen: List<String>): List<String> =
        (known + seen.map { it.trim().uppercase() }.filter { hashPattern.matches(it) }).distinct().take(MAX_MODULE_HASHES)

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
            // A native crash's own line (xe_crash_record.h) already says what it is.
            val reason = fatal?.let { if (it.startsWith(NATIVE_CRASH)) it else "fatal error: $it" } ?: run.endReason ?: process.reason
                ?: "process ended without finishing the run"
            run.copy(state = state, endedAt = run.lastSeenAt, endReason = reason,
                nativeBacktrace = process.backtrace ?: run.nativeBacktrace).also(::write)
        }
    }

    fun runs(): List<SessionRun> = locked {
        root.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull(::read).sortedByDescending { it.startedAt }
    }

    /** Recents and play time per title, from runs whose title actually started. */
    fun titleActivity(): List<TitleActivity> = titleActivityOf(runs())

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
        const val NATIVE_CRASH = "native crash: "
        const val MAX_MODULE_HASHES = 16
        // The core lists at most 200 and says when there were more (xe_changed_settings.h).
        const val MAX_SETTINGS = 201
        const val MAX_SETTING_CHARS = 240
    }
}

/** Recents and play time per title, from the finished runs among [runs] whose title started. */
fun titleActivityOf(runs: List<SessionRun>): List<TitleActivity> = runs.filter { it.titleId != null && it.state.final }
    .groupBy { it.titleId!!.uppercase() }
    .map { (title, runs) ->
        TitleActivity(title, runs.maxOf { it.endedAt ?: it.lastSeenAt }, runs.sumOf { it.playedMs ?: 0L }, runs.size)
    }
    .sortedByDescending { it.lastPlayedAt }

/** The game path of each title's newest finished run (L06: where a missing game was last). */
fun lastGamePaths(runs: List<SessionRun>): Map<String, String> = runs
    .filter { it.titleId != null && it.state.final && it.gamePath.isNotBlank() }
    .sortedByDescending { it.startedAt }
    .distinctBy { it.titleId!!.uppercase() }
    .associate { it.titleId!!.uppercase() to it.gamePath }

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
