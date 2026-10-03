package xendroid.compose.roadmap

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.BuildConfig
import xendroid.compose.MainActivity
import xendroid.compose.sessions.RunReports
import xendroid.compose.sessions.RunState
import xendroid.compose.sessions.SessionRuns
import xendroid.compose.sessions.describeAudio
import xendroid.compose.sessions.describeFrameTimes
import xendroid.compose.sessions.describeRun

/**
 * Roadmap items 9 and 12 (group C: the game runs). Played for `-e playSeconds` (default 120),
 * Home for 5 s and back, then Exit through the menu. The run record says it ended normally
 * after the play time, with FPS over 1-second windows, the frame time line, audio, the driver,
 * and a timeline with background/foreground, Surface and the exit; the game sheet says "Last
 * run: ended normally after …". The shared report (12) carries the Title ID and the format,
 * never the game's path or file name, nor the profile.
 *
 * Left for the phone: whether the numbers look right for the game, rating it, the device-lost
 * message (needs a GPU fault).
 */
@RunWith(AndroidJUnit4::class)
class Item09RunRecordTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = Device.context

    @Test fun aPlayedRunIsRecordedAndItsReportKeepsPrivateThingsOut() {
        val game = GameSession.game()
        val seconds = InstrumentationRegistry.getArguments().getString("playSeconds")?.toIntOrNull() ?: 120
        val run = GameSession(game).use { session ->
            session.start()
            session.awaitRunning()
            session.awaitFirstFrame()
            session.play(seconds / 2)
            session.device.pressHome()
            SystemClock.sleep(5_000)
            session.start()                       // back to the same game (one game process)
            session.play(seconds - seconds / 2)
            session.exitByMenu()
            session.awaitRecord("the run to finish", 60_000) { it.state.final }
        }
        assertEquals(RunState.ENDED, run.state)
        assertTrue(describeRun(run), describeRun(run).startsWith("ended normally after"))
        val perf = run.performance ?: throw AssertionError("no performance summary in the run")
        assertTrue("sampled ${perf.sampledSeconds} s", perf.sampledSeconds >= seconds / 2)
        assertNotNull(perf.fpsPercentile(0.5))
        assertNotNull(perf.fpsPercentile(0.05))
        assertNotNull(describeFrameTimes(perf))
        assertNotNull("audio (item 12)", describeAudio(perf))
        assertNotNull(run.driver)
        val events = SessionRuns.store().events(run.runId)!!
        listOf("background", "foreground").forEach { detail ->
            assertTrue("timeline: $detail", events.events.any { it.kind == "lifecycle" && it.detail == detail })
        }
        assertTrue("timeline: surface", events.events.any { it.kind == "surface" })
        assertTrue("timeline: exit", events.events.any { it.kind == "exit" })
        GameRun.note(9, "${run.titleId}: ${describeRun(run)}; FPS median ${perf.fpsPercentile(0.5)}, 5th ${perf.fpsPercentile(0.05)}; " +
            "${describeFrameTimes(perf)}; audio ${describeAudio(perf)}; driver ${run.driver?.label}")

        // Item 12: the shared report keeps the path, the file name and the profile out.
        val report = RunReports.build(run, events, emptyList(), SessionRuns.reportDevice(BuildConfig.VERSION_NAME), System.currentTimeMillis())
        val preview = RunReports.preview(report).joinToString("\n")
        val zip = SessionRuns.writeReport(context, report)
        val shared = ZipFile(zip).use { file -> file.entries().toList().joinToString("\n") { e -> file.getInputStream(e).readBytes().toString(Charsets.UTF_8) } }
        for (text in listOf(preview, shared)) {
            assertFalse("the game's path in the report", text.contains(game.path))
            assertFalse("the game's file name in the report", text.contains(game.name))
            run.profileXuid?.let { assertFalse("the profile in the report", text.contains(it)) }
            assertTrue(text.contains(run.titleId!!))
        }
        zip.delete()

        // The game sheet says it (the game in the test package's library).
        Library(listOf(game.parentFile!!)).use { library ->
            val shown = library.viewModel().rescan("the game's folder").games.first { it.launchUri == game.path }
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(60_000) { compose.onAllNodesWithText(shown.name).fetchSemanticsNodes().isNotEmpty() }
                compose.onAllNodesWithText(shown.name).onFirst().performTouchInput { longClick() }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("Last run: ended normally after", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
    }
}
