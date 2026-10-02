package xendroid.compose.sessions

import kotlinx.serialization.Serializable

/** One host event of a run (C01 flight recorder); [atMs] counts from the start of the run. */
@Serializable
data class RunEvent(val atMs: Long, val kind: String, val detail: String = "")

/** A flushed flight recorder: the newest events, oldest first, and how many older ones the ring dropped. */
@Serializable
data class RunEventLog(val version: Int = 1, val events: List<RunEvent> = emptyList(), val dropped: Int = 0)

/**
 * Bounded flight recorder of one run (C01, first version), kept in the host process.
 * It takes rare host events only — lifecycle, surface, focus, pause, menu, presentation
 * state, stalls, thermal status, memory trims, controllers, guest prompts, errors —
 * never one per frame or per input, and never guest text or device names. The owner
 * flushes it with the run (heartbeat, background, errors, exit), so a crash loses at
 * most the events since the last flush.
 */
class RunEventRecorder(private val capacity: Int = 200, private val clock: () -> Long) {
    private val start = clock()
    private val ring = ArrayDeque<RunEvent>(capacity)
    private var dropped = 0
    private var changed = false

    @Synchronized
    fun record(kind: String, detail: String = "") {
        if (ring.size >= capacity) {
            ring.removeFirst()
            dropped++
        }
        ring.addLast(RunEvent((clock() - start).coerceAtLeast(0), kind.take(24), detail.take(160)))
        changed = true
    }

    @Synchronized
    fun snapshot(): RunEventLog = RunEventLog(events = ring.toList(), dropped = dropped)

    /** The log to write, or null when nothing was recorded since the previous call. */
    @Synchronized
    fun snapshotIfChanged(): RunEventLog? {
        if (!changed) return null
        changed = false
        return snapshot()
    }
}

/** "1:02:05 thermal · severe" (h:mm:ss from the run start; m:ss under an hour). */
fun describeEvent(event: RunEvent): String {
    val seconds = event.atMs / 1000
    val time = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
    return if (event.detail.isBlank()) "$time ${event.kind}" else "$time ${event.kind} · ${event.detail}"
}

/** Android thermal status (PowerManager.THERMAL_STATUS_*) as a word. */
fun thermalStatusName(status: Int): String = when (status) {
    0 -> "none"
    1 -> "light"
    2 -> "moderate"
    3 -> "severe"
    4 -> "critical"
    5 -> "emergency"
    6 -> "shutdown"
    else -> "status $status"
}
