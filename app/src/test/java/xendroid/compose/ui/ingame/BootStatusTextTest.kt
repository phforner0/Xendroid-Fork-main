package xendroid.compose.ui.ingame

import org.junit.Assert.assertEquals
import org.junit.Test
import xendroid.compose.ui.ingame.BootStatus.Stage

class BootStatusTextTest {
    @Test fun saysWhatTheCoreIsDoingBeforeTheFirstFrame() {
        assertEquals(BootStatus(Stage.GAME, seconds = 3), bootStatus(titleActive = false, pipelinesCreated = 0, creatingNow = 0, elapsedSeconds = 3))
        assertEquals(BootStatus(Stage.FIRST_FRAME, seconds = 9), bootStatus(true, 0, 0, 9))
        assertEquals(BootStatus(Stage.GRAPHICS, 0, 10), bootStatus(true, 0, 1, 10))
        assertEquals(BootStatus(Stage.GRAPHICS, 312, 41), bootStatus(true, 312, 0, 41))
    }
}
