package xendroid.compose.sessions

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RunEventsTest {
    @get:Rule val folder = TemporaryFolder()
    private var now = 10_000L

    @Test fun ringKeepsTheNewestEventsWithTimesFromTheRunStart() {
        val recorder = RunEventRecorder(capacity = 3, clock = { now })
        now += 1_500; recorder.record("lifecycle", "foreground")
        now += 1_000; recorder.record("surface", "2400x1080")
        now += 1_000; recorder.record("pause", "guest paused by the app lifecycle")
        now += 61_000; recorder.record("thermal", "x".repeat(500))
        val log = recorder.snapshot()
        assertEquals(1, log.dropped)
        assertEquals(listOf("surface", "pause", "thermal"), log.events.map { it.kind })
        assertEquals(2_500L, log.events.first().atMs)
        assertEquals(160, log.events.last().detail.length)               // details are bounded
        assertEquals("1:04 thermal · " + "x".repeat(160), describeEvent(log.events.last()))
        assertEquals("1:02:05 stall", describeEvent(RunEvent(3_725_000, "stall")))
    }

    @Test fun onlyChangesAreHandedOutForWriting() {
        val recorder = RunEventRecorder(clock = { now })
        assertNull(recorder.snapshotIfChanged())
        recorder.record("boot", "run started")
        assertEquals(1, recorder.snapshotIfChanged()!!.events.size)
        assertNull(recorder.snapshotIfChanged())                          // nothing new since
        recorder.record("menu", "opened")
        assertEquals(2, recorder.snapshotIfChanged()!!.events.size)
    }

    @Test fun eventsLiveBesideTheirRunAndArePrunedWithIt() {
        var t = 1_000L
        val store = SessionRunStore(folder.root, clock = { t++ }, maxRecords = 1)
        val first = store.begin("library", "/games/a.iso", "b", 1).runId
        val log = RunEventLog(events = listOf(RunEvent(0, "boot", "run started")))
        store.saveEvents(first, log)
        assertEquals(log, store.events(first))
        // Never read as a run record.
        assertEquals(listOf(first), store.runs().map { it.runId })
        store.finish(first, RunState.ENDED, "activity finished")
        // A second finished run prunes the first one, and its events go with it.
        val second = store.begin("library", "/games/b.iso", "b", 2).runId
        store.finish(second, RunState.ENDED, "activity finished")
        store.begin("library", "/games/c.iso", "b", 3)
        assertNull(store.events(first))
        assertFalse(File(folder.root, "$first.events").exists())
    }

    @Test fun unknownRunsAndDamagedLogsGiveNothing() {
        val store = SessionRunStore(folder.root, clock = { now })
        val unknown = "0f8fad5b-d9cb-469f-a165-70867728950e"
        store.saveEvents(unknown, RunEventLog(events = listOf(RunEvent(0, "boot"))))
        assertNull(store.events(unknown))
        val id = store.begin("library", "/games/a.iso", "b", 1).runId
        File(folder.root, "$id.events").writeText("{ damaged")
        assertNull(store.events(id))
        assertThrows(IllegalArgumentException::class.java) { store.events("../escape") }
    }
}
