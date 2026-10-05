package xendroid.compose.roadmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.sessions.BenchRun
import xendroid.compose.sessions.Benchmark
import xendroid.compose.sessions.SessionRun
import xendroid.compose.sessions.SessionRuns
import xendroid.compose.settings.UiMode
import xendroid.compose.settings.UiModeStore

/**
 * Roadmap item 34 (C07, group C), only with `-e longRuns true` (four boots): runs in the order
 * A (frame generation off) · B (Win-FG 2× on) · B · A, each marking the scene from the menu
 * (Session → "Mark scene…") and playing `-e runSeconds` (default 40). Compared as the Compare runs
 * screen does: balanced ABBA order, two B − A pair numbers, and the verdict, warnings and "what
 * changed" written to the results file for the FG report.
 *
 * Left for the phone: the same scene by hand, cooling the battery between runs, judging the
 * result; the driver-only and limit-change variations.
 */
@RunWith(AndroidJUnit4::class)
class Item34CompareRunsTest {
    private fun once(game: File, frameGeneration: Boolean, seconds: Int): SessionRun = GameSession(game).use { s ->
        s.start()
        s.awaitFirstFrame()
        s.openMenu()
        s.page(R.string.menu_tab_session)
        val mark = Device.string(R.string.menu_mark_scene, 7).substringBefore("7")
        (s.device.findObject(By.textStartsWith(mark)) ?: throw AssertionError("no \"$mark…\" in Session")).click()
        if (frameGeneration) {
            s.page(R.string.menu_tab_graphics)
            s.find(s.text(R.string.menu_winfg, s.text(R.string.menu_off))).click()
        }
        s.closeMenu()
        s.play(seconds)
        s.exitByMenu()
        s.awaitRecord("the run to finish", 60_000) { it.state.final }
    }

    @Test fun fourRunsInAbbaOrderCompare() {
        assumeTrue("only with -e longRuns true", GameRun.flag("longRuns"))
        val game = GameSession.game()
        UiModeStore.write(Device.context, UiMode.DEVELOPER)
        val seconds = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("runSeconds")?.toIntOrNull() ?: 40
        val order = "ABBA"
        val runs = order.map { label -> once(game, frameGeneration = label == 'B', seconds = seconds) }
        val store = SessionRuns.store()
        val bench = runs.zip(order.toList()).map { (run, label) ->
            val perf = run.performance!!
            BenchRun(label, run.startedAt, perf.fpsPercentile(0.5) ?: 0, perf.fpsPercentile(0.05) ?: 0,
                perf.frameTimeUpperMs(0.99), perf.sampledSeconds, run.driver?.label, perf.frameGenerationSeconds > 0,
                perf.batteryStartC, store.events(run.runId)?.events?.count { it.kind == "marker" } ?: 0,
                perf.fpsLimits, perf.displayHz, perf.guestRefreshCap)
        }
        bench.forEach { assertEquals("one scene marker per run", 1, it.markers) }
        val result = Benchmark.compare(bench)
        GameRun.note(34, "${runs.first().titleId} ABBA: B − A per pair ${result.pairDeltas}; ${result.verdict}; " +
            "changed: ${result.changed}; warnings: ${result.warnings}; FG active in B: ${bench.filter { it.label == 'B' }.map { it.frameGeneration }}")
        assertTrue(result.balanced)
        assertEquals(2, result.pairDeltas.size)
    }
}
