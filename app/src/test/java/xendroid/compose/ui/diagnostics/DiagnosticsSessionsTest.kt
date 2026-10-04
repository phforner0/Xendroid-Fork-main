package xendroid.compose.ui.diagnostics

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xendroid.compose.core.SessionLogs
import xendroid.compose.sessions.RunState
import xendroid.compose.sessions.SessionRun

class DiagnosticsSessionsTest {
    private val base = 1_790_000_000_000L
    private val hour = 3_600_000L

    private fun run(id: String, at: Long, state: RunState, title: String? = "4D5307E6") = SessionRun(
        runId = id, state = state, launchSource = "library", gamePath = "/games/$id.iso", buildVersion = "v1", pid = 1,
        startedAt = at, titleId = title, runningAt = at + 5_000, lastSeenAt = at + 20 * 60_000, endedAt = at + 20 * 60_000)

    private fun log(id: String, end: Long, vararg titles: String) = SessionLogs.Session(id, id, end, 2048, titles.toList())

    @Test fun putsEachRunInTheSessionItRanIn() {
        val logs = listOf(
            log(SessionLogs.CURRENT_ID, base + 8 * hour),
            log("session_b.zip", base + 5 * hour, "4D5307E6"),
            log("session_a.zip", base + hour, "4D5309C9"),
        )
        val runs = listOf(
            run("pruned", base - 72 * hour, RunState.ENDED),         // its logs are gone
            run("a1", base + hour / 2, RunState.ENDED, "4D5309C9"),
            run("b1", base + 2 * hour, RunState.ENDED),
            run("b2", base + 4 * hour, RunState.FAILED),
            run("c1", base + 6 * hour, RunState.RUNNING),
        )
        val sessions = DiagnosticsSessions.group(logs, runs, starts = mapOf("session_a.zip" to base))
        assertEquals(listOf(SessionLogs.CURRENT_ID, "session_b.zip", "session_a.zip"), sessions.map { it.id })
        assertEquals(listOf(listOf("c1"), listOf("b1", "b2"), listOf("a1")), sessions.map { s -> s.runs.map { it.runId } })
        assertEquals(DiagSession.Outcome.FAILED, sessions[1].outcome)
        assertEquals("b2", sessions[1].telling?.runId)
        assertEquals(DiagSession.Outcome.RUNNING, sessions[0].outcome)
        assertEquals(DiagSession.Outcome.ENDED, sessions[2].outcome)
        assertEquals(listOf("4D5309C9"), sessions[2].titles)
    }

    @Test fun theOldestSessionWithoutAStartKeepsToADay() {
        val logs = listOf(log("session_a.zip", base + hour))
        val runs = listOf(run("old", base + hour - 30 * hour, RunState.ENDED), run("recent", base, RunState.INTERRUPTED))
        val only = DiagnosticsSessions.group(logs, runs).single()
        assertEquals(listOf("recent"), only.runs.map { it.runId })
        assertEquals(DiagSession.Outcome.INTERRUPTED, only.outcome)
    }

    @Test fun aSessionWithoutGamesSaysSo() {
        val only = DiagnosticsSessions.group(listOf(log("session_a.zip", base)), emptyList()).single()
        assertEquals(DiagSession.Outcome.NO_GAME, only.outcome)
        assertNull(only.telling)
        assertEquals(0L, only.playedMs)
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0, millis: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(year, month - 1, day, hour, minute, second); set(Calendar.MILLISECOND, millis) }.timeInMillis

    @Test fun readsWhenALogcatCaptureBegins() {
        val utc = TimeZone.getTimeZone("UTC")
        val lines = sequenceOf("--------- beginning of main", "10-04 08:11:30.123  1195  1230 I Tag: started")
        assertEquals(utc(2026, 10, 4, 8, 11, 30, 123), DiagnosticsSessions.firstLogcatTime(lines, utc(2026, 10, 4, 9, 0), utc))
        // No year in logcat: a December line read in January is last year's.
        assertEquals(utc(2026, 12, 31, 23, 59), DiagnosticsSessions.firstLogcatTime(sequenceOf("12-31 23:59:00.000 1 1 I a: b"), utc(2027, 1, 1, 0, 10), utc))
        // A shelved tail does not say when the session began.
        assertNull(DiagnosticsSessions.firstLogcatTime(sequenceOf("[truncated: shelved last 1 of 2 bytes]", "10-04 08:11:30.123 1 1 I a: b"), utc(2026, 10, 4, 9, 0), utc))
        assertNull(DiagnosticsSessions.firstLogcatTime(sequenceOf("nothing dated"), utc(2026, 10, 4, 9, 0), utc))
    }
}
