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
 * Roadmap item 42 (C01, group C): a SIGSEGV sent to the running game process (debuggable build,
 * `run-as … kill -SEGV`). The process ends with Android's tombstone and, once the library looks,
 * the run is "failed …: native crash: SIGSEGV (code 0) at 0x…, thread '…', pc 0x…" — the line the
 * core's crash hook wrote (code 0: the signal came from outside).
 *
 * Left for the phone: a real crash inside libe.so matching the tombstone's "#00 pc", and a fatal
 * core error keeping the core's own words.
 */
@RunWith(AndroidJUnit4::class)
class Item42NativeCrashTest {
    @Test fun aSegfaultIsNamedInTheRun() {
        val game = GameSession.game()
        val runId = GameSession(game).use { session ->
            session.start()
            session.awaitRunning()
            session.awaitFirstFrame()
            val id = session.record()!!.runId
            session.signal("SEGV")
            id
        }
        SessionRuns.store().reconcile(SessionRuns.fates(Device.context))
        val run = SessionRuns.store().runs().single { it.runId == runId }
        assertEquals(RunState.FAILED, run.state)
        val reason = run.endReason.orEmpty()
        assertTrue(reason, reason.startsWith("native crash: SIGSEGV (code 0) at 0x"))
        // The pc is the thread's at the moment of the signal: an address, or "libe.so+0x…" inside the core.
        assertTrue(reason, reason.contains(", thread '") && Regex("', pc (0x|\\S+\\+0x)[0-9a-f]+").containsMatchIn(reason))
        GameRun.note(42, "${run.titleId}: ${describeRun(run)}")
    }
}
