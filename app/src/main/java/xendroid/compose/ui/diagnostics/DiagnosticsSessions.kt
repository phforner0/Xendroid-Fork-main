package xendroid.compose.ui.diagnostics

import java.io.File
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.ZipFile
import xendroid.compose.Utils
import xendroid.compose.core.SessionLogs
import xendroid.compose.sessions.RunState
import xendroid.compose.sessions.SessionRun
import xendroid.compose.sessions.SessionRuns

/** One kept log session (a main-process lifetime) and the game runs that happened in it. */
data class DiagSession(
    /** "current" or the shelved archive's file name, as [SessionLogs] shares it. */
    val id: String,
    /** When its logs end (their newest write). */
    val endedAt: Long,
    val bytes: Long,
    /** Title IDs its logs name, then its runs'. */
    val titles: List<String>,
    /** Its runs, oldest first. */
    val runs: List<SessionRun>,
) {
    enum class Outcome { ENDED, INTERRUPTED, FAILED, RUNNING, NO_GAME }

    val current: Boolean get() = id == SessionLogs.CURRENT_ID

    /** How it went, the worst of its runs first: a failure, then Android ending a game. */
    val outcome: Outcome get() = when {
        runs.any { it.state == RunState.FAILED } -> Outcome.FAILED
        runs.any { it.state == RunState.INTERRUPTED } -> Outcome.INTERRUPTED
        runs.any { it.state == RunState.ENDED } -> Outcome.ENDED
        runs.isNotEmpty() -> Outcome.RUNNING
        else -> Outcome.NO_GAME
    }

    /** The run that tells the outcome: the failure, the interruption, else the latest. */
    val telling: SessionRun?
        get() = runs.lastOrNull { it.state == RunState.FAILED } ?: runs.lastOrNull { it.state == RunState.INTERRUPTED } ?: runs.lastOrNull()

    val playedMs: Long get() = runs.sumOf { it.playedMs ?: 0L }

    /** When it began, as far as its runs tell. */
    val startedAt: Long? get() = runs.firstOrNull()?.startedAt
}

/**
 * The Diagnostics list: the kept log sessions (what can be shared) with the game runs in each, so
 * a session says how it went. Sessions and runs are kept apart (logs by [SessionLogs], runs by
 * [SessionRuns]); a run belongs to the session whose span holds its start.
 */
object DiagnosticsSessions {
    /** A main-process lifetime rarely spans more than this; the oldest kept session without a
     *  readable start is bounded by it so runs whose logs were pruned are not given to it. */
    const val MAX_SPAN_MS = 24L * 60 * 60 * 1000

    /**
     * [logs] as [SessionLogs.sessions] lists them; [starts]: when a session's logcat begins, if
     * known. Each session spans (previous session's end, its own end]; the current one has no end
     * yet. Returns newest first; runs older than every kept session are left out (their logs are gone).
     */
    fun group(logs: List<SessionLogs.Session>, runs: List<SessionRun>, starts: Map<String, Long> = emptyMap()): List<DiagSession> {
        val ordered = logs.sortedWith(compareBy<SessionLogs.Session> { it.id == SessionLogs.CURRENT_ID }.thenBy { it.timestamp })
        val byStart = runs.sortedBy { it.startedAt }
        return ordered.mapIndexed { i, log ->
            val previousEnd = ordered.getOrNull(i - 1)?.timestamp
            val from = listOfNotNull(previousEnd, starts[log.id]?.minus(1)).maxOrNull() ?: (log.timestamp - MAX_SPAN_MS)
            val open = log.id == SessionLogs.CURRENT_ID
            val mine = byStart.filter { it.startedAt > from && (open || it.startedAt <= log.timestamp) }
            val titles = (log.titles + mine.mapNotNull { it.titleId }).map { it.uppercase() }.distinct()
            DiagSession(log.id, log.timestamp, log.bytes, titles, mine)
        }.reversed()
    }

    private val THREADTIME = Regex("^(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})")

    /**
     * When a logcat capture ("-v threadtime": "10-04 08:11:30.123 …", no year) begins: the year of
     * [end], or the one before when that would be after it. Null for a tail ("[truncated…" first)
     * or no dated line among the first few.
     */
    fun firstLogcatTime(lines: Sequence<String>, end: Long, zone: TimeZone = TimeZone.getDefault()): Long? {
        val head = lines.take(8).toList()
        if (head.firstOrNull()?.startsWith("[truncated") == true) return null
        val m = head.firstNotNullOfOrNull { THREADTIME.find(it) } ?: return null
        val (month, day, hour, minute, second, millis) = m.destructured
        val cal = Calendar.getInstance(zone).apply { timeInMillis = end }
        cal.set(cal.get(Calendar.YEAR), month.toInt() - 1, day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
        cal.set(Calendar.MILLISECOND, millis.toInt())
        if (cal.timeInMillis > end + 60_000) cal.add(Calendar.YEAR, -1)
        return cal.timeInMillis
    }

    /** When each kept session's logcat begins, read from its first lines. */
    fun starts(logs: List<SessionLogs.Session>): Map<String, Long> {
        val dir = File(File(Utils.get_log_file_path()).parentFile, "logs")
        return logs.mapNotNull { s ->
            val time = runCatching {
                if (s.id == SessionLogs.CURRENT_ID) File(dir, SessionLogs.CAPTURE_NAME).takeIf { it.isFile }?.bufferedReader()?.useLines { firstLogcatTime(it, s.timestamp) }
                else ZipFile(File(dir, s.id)).use { zip ->
                    zip.getEntry("logcat.txt")?.let { e -> zip.getInputStream(e).bufferedReader().useLines { firstLogcatTime(it, s.timestamp) } }
                }
            }.getOrNull()
            time?.let { s.id to it }
        }.toMap()
    }

    /** Everything the screen lists, off the main thread. */
    fun load(): List<DiagSession> {
        val logs = SessionLogs.sessions()
        val runs = runCatching { SessionRuns.store().runs() }.getOrDefault(emptyList())
        return group(logs, runs, starts(logs))
    }
}
