package xendroid.compose.roadmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.sessions.RunState
import xendroid.compose.sessions.SessionRuns
import xendroid.compose.sessions.describeRun

/**
 * Roadmap item 10 (group C): the game process killed while it runs (SIGKILL, what force-stop
 * does to it; the test runner itself stays). When the library looks again (the reconcile it does
 * on opening), the run is "interrupted" with its play time, and its timeline ends at the last
 * event written (the heartbeat writes it every 30 s), with nothing after the last sign of life.
 */
@RunWith(AndroidJUnit4::class)
class Item10InterruptedRunTest {
    @Test fun aKilledGameIsAnInterruptedRun() {
        val game = GameSession.game()
        val runId = GameSession(game).use { session ->
            session.start()
            session.awaitRunning()
            session.awaitFirstFrame()
            session.play(10)
            val id = session.record()!!.runId
            session.signal("KILL")
            id
        }
        SessionRuns.store().reconcile(SessionRuns.fates(Device.context))
        val run = SessionRuns.store().runs().single { it.runId == runId }
        assertEquals(RunState.INTERRUPTED, run.state)
        assertTrue(describeRun(run), describeRun(run).startsWith("interrupted after"))
        val events = SessionRuns.store().events(runId)
        val last = events?.events?.lastOrNull()
        assertTrue("timeline saved", last != null)
        // Nothing after the last sign of life (the recorder starts a moment before the run record).
        assertTrue("an event after the run's last sign of life",
            run.startedAt + last!!.atMs <= run.lastSeenAt + 5_000)
        GameRun.note(10, "${run.titleId}: ${describeRun(run)}; last event ${last.kind} ${last.detail}, " +
            "${(run.lastSeenAt - run.startedAt - last.atMs) / 1000} s before the last heartbeat")
    }
}
