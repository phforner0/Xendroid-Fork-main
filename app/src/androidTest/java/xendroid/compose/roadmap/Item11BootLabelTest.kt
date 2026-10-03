package xendroid.compose.roadmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.sessions.SessionRun

/**
 * Roadmap item 11 (group C): the game started with the test package's pipeline/shader caches
 * deleted. Before the first frame a label says what is happening ("Starting the game… N s",
 * "Preparing graphics: N pipelines created… X s", "Waiting for the first frame… X s") and it goes
 * with the image; the run records the first frame time and the pipelines created. Started again
 * with the cache, no more pipelines are created and the first frame comes no later.
 *
 * Left for the phone: watching the label, the creation bursts on the timeline graph.
 */
@RunWith(AndroidJUnit4::class)
class Item11BootLabelTest {
    /** The fixed beginning of each label, in the shown language. */
    private fun labelStarts(): List<String> = listOf(
        Device.string(R.string.boot_game, 7).substringBefore("7"),
        Device.plural(R.plurals.boot_graphics, 7, 7, 3).substringBefore("7"),
        Device.string(R.string.boot_first_frame, 7).substringBefore("7"),
    )

    private fun boot(game: File, watchLabel: Boolean): Pair<SessionRun, String?> = GameSession(game).use { session ->
        session.start()
        var seen: String? = null
        if (watchLabel) {
            Device.waitUntil("a boot label", 5 * 60_000L) {
                seen = labelStarts().firstNotNullOfOrNull { start -> session.device.findObject(By.textStartsWith(start))?.text }
                seen != null || session.record()?.performance?.firstFrameSeconds != null
            }
        }
        session.awaitFirstFrame()
        Device.waitUntil("the label to go with the image", 60_000) {
            labelStarts().none { session.device.hasObject(By.textStartsWith(it)) }
        }
        session.play(20)
        session.exitByMenu()
        session.awaitRecord("the run to finish", 60_000) { it.state.final } to seen
    }

    @Test fun theLabelSaysWhatTheBootDoesAndTheCacheHelps() {
        val game = GameSession.game()
        listOf("cache", "cache0", "cache1").forEach { File(Device.storageRoot, it).deleteRecursively() }
        val (cold, label) = boot(game, watchLabel = true)
        assertNotNull("no boot label before the first frame", label)
        val (warm, _) = boot(game, watchLabel = false)
        val coldPerf = cold.performance!!
        val warmPerf = warm.performance!!
        GameRun.note(11, "${cold.titleId}: label \"$label\"; cold: first frame ${coldPerf.firstFrameSeconds} s, " +
            "${coldPerf.pipelineCreations} pipelines; with the cache: first frame ${warmPerf.firstFrameSeconds} s, ${warmPerf.pipelineCreations} pipelines")
        assertTrue((warmPerf.pipelineCreations ?: 0) <= (coldPerf.pipelineCreations ?: 0))
        assertTrue((warmPerf.firstFrameSeconds ?: 0) <= (coldPerf.firstFrameSeconds ?: Int.MAX_VALUE))
    }
}
