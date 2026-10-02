package xendroid.compose.ui.ingame

import org.junit.Assert.assertEquals
import org.junit.Test

class BootStatusTextTest {
    @Test fun saysWhatTheCoreIsDoingBeforeTheFirstFrame() {
        assertEquals("Starting the game… 3 s", bootStatusText(titleActive = false, pipelinesCreated = 0, creatingNow = 0, elapsedSeconds = 3))
        assertEquals("Waiting for the first frame… 9 s", bootStatusText(true, 0, 0, 9))
        assertEquals("Preparing graphics: 0 pipelines created… 10 s", bootStatusText(true, 0, 1, 10))
        assertEquals("Preparing graphics: 312 pipelines created… 41 s", bootStatusText(true, 312, 0, 41))
    }
}
