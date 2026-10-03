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
 * on opening), the run is "interrupted" with its play time, its last sign of life is the 30 s
 * heartbeat before the kill, and its timeline ends at the last event written: what the heartbeat
 * saved is there (the first frame), what came after it may be lost.
 */
@RunWith(AndroidJUnit4::class)
class Item10InterruptedRunTest {
    @Test fun aKilledGameIsAnInterruptedRun() {
        val game = GameSession.game()
        var killedAt = 0L
        val runId = GameSession(game).use { session ->
            session.start()
            session.awaitRunning()
            session.awaitFirstFrame()
            session.play(10)
            val id = session.record()!!.runId
            killedAt = System.currentTimeMillis()
            session.signal("KILL")
            id
        }
        // The process is gone from the kernel (pidof) a moment before Android lists it as gone
        // (the first run on a phone read it as still running): look again until it is final.
        Device.waitUntil("the killed run to be reconciled", 20_000) {
            SessionRuns.store().reconcile(SessionRuns.fates(Device.context))
            SessionRuns.store().runs().single { it.runId == runId }.state.final
        }
        val run = SessionRuns.store().runs().single { it.runId == runId }
        assertEquals(RunState.INTERRUPTED, run.state)
        assertTrue(describeRun(run), describeRun(run).startsWith("interrupted after"))
        assertTrue("last sign of life ${(killedAt - run.lastSeenAt) / 1000} s before the kill",
            run.lastSeenAt >= killedAt - 45_000)
        val events = SessionRuns.store().events(runId)?.events.orEmpty()
        val last = events.lastOrNull() ?: throw AssertionError("no timeline saved")
        // The first frame was written with the heartbeat the test waited for, before the kill.
        assertTrue("the timeline lost what the heartbeat saved",
            events.any { it.kind == "boot" && it.detail.startsWith("first guest frames") })
        // Event times count from the recorder, which starts a moment before the run record:
        // "run started" is the run's own zero.
        val zero = events.firstOrNull { it.kind == "boot" && it.detail == "run started" }?.atMs ?: 0L
        GameRun.note(10, "${run.titleId}: ${describeRun(run)}; last event ${last.kind} ${last.detail}, " +
            "${(killedAt - run.startedAt - (last.atMs - zero)) / 1000} s before the kill; " +
            "last heartbeat ${(killedAt - run.lastSeenAt) / 1000} s before it")
    }
}
