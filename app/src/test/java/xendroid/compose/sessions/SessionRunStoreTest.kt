package xendroid.compose.sessions

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionRunStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private var now = 1_000_000L
    private fun store(root: File = folder.root) = SessionRunStore(root, clock = { now })

    @Test fun normalRunEndsOnceWithItsPlayTime() {
        val store = store()
        val run = store.begin("library", "/storage/x/Forza.iso", "77011a0c+local.abc", pid = 42)
        assertEquals(RunState.BEGIN, run.state)
        now += 5_000
        assertEquals(RunState.RUNNING, store.running(run.runId, "4D5309C9", profileXuid = " e03000002b7c4d1a ")!!.state)
        assertEquals("E03000002B7C4D1A", store.runs().single().profileXuid)          // L06: P1 when it started
        store.running(run.runId, "4D5309C9", profileXuid = "E030000000000099")
        assertEquals("E03000002B7C4D1A", store.runs().single().profileXuid)          // the first one stays
        now += 60_000
        store.heartbeat(run.runId)
        now += 1_000
        store.ending(run.runId, "user exit")
        now += 500
        val ended = store.finish(run.runId, RunState.ENDED, "activity destroyed")!!
        assertEquals(RunState.ENDED, ended.state)
        assertEquals("user exit", ended.endReason)
        assertEquals(61_500L, ended.playedMs)
        // A second finalization (for example the frontend reconciling late) changes nothing.
        now += 10_000
        assertEquals(ended, store.finish(run.runId, RunState.INTERRUPTED, "late"))
        assertEquals(emptyList<SessionRun>(), store.reconcile { ProcessFate(alive = false) })
    }

    @Test fun deadProcessesAreFinalizedFromTheirLastHeartbeat() {
        val store = store()
        val crashed = store.begin("frontend", "/a.iso", "v", pid = 100)
        store.running(crashed.runId, "4D5309C9")
        now += 30_000
        store.heartbeat(crashed.runId)
        val killed = store.begin("library", "/b.iso", "v", pid = 200)
        val alive = store.begin("library", "/c.iso", "v", pid = 300)
        val exiting = store.begin("library", "/d.iso", "v", pid = 400)
        store.ending(exiting.runId, "user exit")
        now += 3_600_000  // the frontend reconciles an hour later
        val finalized = store.reconcile { pid ->
            when (pid) {
                100 -> ProcessFate(alive = false, crashed = true, reason = "native crash")
                300 -> ProcessFate(alive = true)
                else -> ProcessFate(alive = false, reason = "low memory")
            }
        }.associateBy { it.pid }
        assertEquals(setOf(100, 200, 400), finalized.keys)
        assertEquals(RunState.FAILED, finalized.getValue(100).state)
        assertEquals(30_000L, finalized.getValue(100).playedMs)        // not the hour of absence
        assertEquals(RunState.INTERRUPTED, finalized.getValue(200).state)
        assertNull(finalized.getValue(200).playedMs)                   // never reached a title
        assertEquals(RunState.ENDED, finalized.getValue(400).state)      // exit had been requested
        assertEquals(RunState.BEGIN, store.runs().single { it.runId == alive.runId }.state)
    }

    @Test fun aFatalReportNamesTheCauseOfADeadRun() {
        val store = store()
        val lost = store.begin("library", "/a.iso", "v", pid = 100)
        store.running(lost.runId, "4D5309C9")
        store.fatalReportFile(lost.runId).writeText("\n  Graphics device lost (probably due to an internal error)\u0007\nsecond line")
        // Even a run whose exit was requested failed if the core died with a fatal error.
        val exiting = store.begin("library", "/b.iso", "v", pid = 200)
        store.ending(exiting.runId, "user exit")
        store.fatalReportFile(exiting.runId).writeText("x".repeat(500))
        val plain = store.begin("library", "/c.iso", "v", pid = 300)
        // C01: the line the fault handler left for a crash nobody handled (xe_crash_record.h).
        val crashed = store.begin("library", "/d.iso", "v", pid = 500)
        store.fatalReportFile(crashed.runId).writeText(
            "native crash: SIGSEGV (SEGV_MAPERR) at 0x0000000000000010, thread 'GPU Commands', pc libe.so+0x1a2b3c\n")
        val finalized = store.reconcile { ProcessFate(alive = false, crashed = true, reason = "native crash") }.associateBy { it.pid }
        assertEquals(RunState.FAILED, finalized.getValue(100).state)
        assertEquals("fatal error: Graphics device lost (probably due to an internal error)", finalized.getValue(100).endReason)
        assertEquals(RunState.FAILED, finalized.getValue(200).state)
        assertEquals("fatal error: " + "x".repeat(180), finalized.getValue(200).endReason)
        assertEquals("native crash", finalized.getValue(300).endReason)    // no report: the platform's reason
        assertEquals(RunState.FAILED, finalized.getValue(500).state)
        assertEquals("native crash: SIGSEGV (SEGV_MAPERR) at 0x0000000000000010, thread 'GPU Commands', pc libe.so+0x1a2b3c",
            finalized.getValue(500).endReason)
        assertThrows(IllegalArgumentException::class.java) { store.fatalReportFile("../x") }
        assertEquals(plain.runId, finalized.getValue(300).runId)
    }

    @Test fun fatalReportsArePrunedWithTheirRun() {
        val store = SessionRunStore(folder.root, clock = { now++ }, maxRecords = 1)
        val first = store.begin("library", "/a.iso", "v", pid = 1).runId
        store.fatalReportFile(first).writeText("boom")
        store.reconcile { ProcessFate(alive = false) }
        store.begin("library", "/b.iso", "v", pid = 2).runId.let { second ->
            store.finish(second, RunState.ENDED, "activity finished")
        }
        store.begin("library", "/c.iso", "v", pid = 3)
        assertFalse(store.fatalReportFile(first).exists())
    }

    @Test fun concurrentFinalizersFinalizeExactlyOnce() {
        val root = folder.newFolder()
        val run = store(root).begin("library", "/x.iso", "v", pid = 7)
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(16) { i ->
            pool.execute {
                start.await()
                val s = store(root)
                if (i % 2 == 0) s.finish(run.runId, RunState.ENDED, "host $i")
                else s.reconcile { ProcessFate(alive = false, reason = "reconcile $i") }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        val final = store(root).runs().single()
        assertTrue(final.state.final)
        val first = final.endReason!!
        // Whoever won, nobody overwrote it afterwards.
        assertEquals(final, store(root).finish(run.runId, RunState.FAILED, "again"))
        assertEquals(first, store(root).runs().single().endReason)
    }

    @Test fun recentsAndPlayTimeComeFromFinishedRunsOnly() {
        val store = store()
        fun play(title: String, ms: Long) {
            val run = store.begin("library", "/$title.iso", "v", pid = 1)
            store.running(run.runId, title)
            now += ms
            store.finish(run.runId, RunState.ENDED, "exit")
            now += 1_000
        }
        play("4D5309C9", 60_000)
        play("415607E6", 10_000)
        play("4D5309C9", 30_000)
        val open = store.begin("library", "/open.iso", "v", pid = 2)
        store.running(open.runId, "11111111")
        val activity = store.titleActivity()
        assertEquals(listOf("4D5309C9", "415607E6"), activity.map { it.titleId })
        assertEquals(90_000L, activity[0].playedMs)
        assertEquals(2, activity[0].runs)
    }

    @Test fun theLastPathOfEachTitleIsItsNewestFinishedRun() {
        val store = store()
        fun play(title: String, path: String) {
            val run = store.begin("library", path, "v", pid = 1)
            store.running(run.runId, title)
            now += 1_000
            store.finish(run.runId, RunState.ENDED, "exit")
            now += 1_000
        }
        play("4D5309C9", "/old/Halo 3.iso")
        play("4D5309C9", "/new/Halo 3.iso")
        play("415607E6", "/games/Forza.zar")
        val open = store.begin("library", "/later/Halo 3.iso", "v", pid = 2)
        store.running(open.runId, "4D5309C9")   // still running: not where the game "was"
        store.begin("library", "/never-started.iso", "v", pid = 3)
        assertEquals(mapOf("4D5309C9" to "/new/Halo 3.iso", "415607E6" to "/games/Forza.zar"),
            lastGamePaths(store.runs()))
        assertEquals(store.titleActivity(), titleActivityOf(store.runs()))
    }

    @Test fun invalidInputsAndDamagedRecordsAreRejectedOrIgnored() {
        val store = store()
        val run = store.begin("library", "/x.iso", "v", pid = 1)
        assertThrows(IllegalArgumentException::class.java) { store.running(run.runId, "../evil") }
        assertThrows(IllegalArgumentException::class.java) { store.heartbeat("../../outside") }
        assertThrows(IllegalArgumentException::class.java) { store.finish(run.runId, RunState.RUNNING, "x") }
        File(folder.root, "00000000-0000-0000-0000-000000000000.json").writeText("{ broken")
        assertEquals(listOf(run.runId), store.runs().map { it.runId })
    }

    @Test fun driverAndPerformanceAreKeptWithTheRun() {
        val store = store()
        val driver = xendroid.compose.driver.DriverIdentity("0x5143", "0x44050A00", "0x80C00000", "1.4", 18,
            "turnip", "Mesa", "Adreno", "0".repeat(32))
        val run = store.begin("library", "/f.iso", "v", pid = 3)
        store.running(run.runId, "4D5309C9")                       // driver not known yet
        store.heartbeat(run.runId, driver = driver)                 // filled in later
        val acc = RunPerformanceAccumulator()
        acc.sample(true, 30.0, 0, 0, false); acc.sample(true, 30.0, 30, 0, false)
        now += 60_000
        store.finish(run.runId, RunState.ENDED, "exit", acc.snapshot())
        val last = store.lastRun("4d5309c9")!!
        assertEquals(driver, last.driver)
        assertEquals(1, last.performance!!.sampledSeconds)
        assertEquals("ended normally after 1 min", describeRun(last))
        // A driver reported later never replaces the first one.
        assertEquals(driver, store.heartbeat(run.runId, driver = driver.copy(uuid = "f".repeat(32)))!!.driver)
    }

    @Test fun runEndingsAreDescribedForTheLibrary() {
        val base = SessionRun(runId = "00000000-0000-0000-0000-000000000001", state = RunState.FAILED,
            launchSource = "library", gamePath = "/x", buildVersion = "v", pid = 1, startedAt = 0,
            titleId = "4D5309C9", runningAt = 0, lastSeenAt = 120_000, endedAt = 120_000, endReason = "native crash")
        assertEquals("failed after 2 min: native crash", describeRun(base))
        assertEquals("interrupted after 2 min: killed by the system for memory",
            describeRun(base.copy(state = RunState.INTERRUPTED, endReason = "killed by the system for memory")))
        assertEquals("closed before the game started", describeRun(base.copy(state = RunState.ENDED, titleId = null, runningAt = null)))
        assertEquals("still open", describeRun(base.copy(state = RunState.RUNNING)))
    }

    @Test fun playTimeIsFormattedForTheLibrary() {
        assertEquals("<1 min", formatPlayTime(59_999))
        assertEquals("42 min", formatPlayTime(42 * 60_000L + 5))
        assertEquals("3 h 05 min", formatPlayTime((3 * 60 + 5) * 60_000L))
        assertEquals("<1 min", formatPlayTime(-5))
    }

    @Test fun recentSortPutsTheLastPlayedFirstAndKeepsTheRestByName() {
        val activity = mapOf(
            "4D5309C9" to TitleActivity("4D5309C9", lastPlayedAt = 200, playedMs = 1, runs = 1),
            "415607E6" to TitleActivity("415607E6", lastPlayedAt = 300, playedMs = 1, runs = 1),
        )
        fun game(name: String, title: String?) = xendroid.compose.data.Game("/$name.iso", name,
            xendroid.compose.data.GameFormat.entries.first(), titleId = title)
        val sorted = xendroid.compose.ui.library.sortByRecent(listOf(
            game("Zeta", null), game("Forza", "4d5309c9"), game("Alpha", "00000001"), game("Halo", "415607E6"),
        ), activity)
        assertEquals(listOf("Halo", "Forza", "Alpha", "Zeta"), sorted.map { it.name })
    }

    @Test fun onlyFinalRunsArePrunedBeyondTheLimit() {
        val store = SessionRunStore(folder.root, clock = { now }, maxRecords = 3)
        val open = store.begin("library", "/open.iso", "v", pid = 9)
        repeat(5) {
            now += 1_000
            val run = store.begin("library", "/$it.iso", "v", pid = 1)
            store.finish(run.runId, RunState.ENDED, "exit")
        }
        now += 1_000
        store.begin("library", "/last.iso", "v", pid = 1)  // prunes on begin
        val runs = store.runs()
        assertEquals(3, runs.count { it.state.final })
        assertTrue(runs.any { it.runId == open.runId })
    }
}
