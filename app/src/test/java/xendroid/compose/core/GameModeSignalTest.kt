package xendroid.compose.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GameModeSignalTest {
    @Test fun theSessionIsLoadingUntilItsFirstFrameThenPlayingOrPaused() {
        assertEquals(GamePhase.LOADING, gamePhase(foreground = true, firstFrame = false, menuOrPaused = false))
        assertEquals(GamePhase.LOADING, gamePhase(foreground = true, firstFrame = false, menuOrPaused = true))
        assertEquals(GamePhase.PLAYING, gamePhase(foreground = true, firstFrame = true, menuOrPaused = false))
        assertEquals(GamePhase.PAUSED, gamePhase(foreground = true, firstFrame = true, menuOrPaused = true))
        // In the background the platform hears nothing is being played, whatever the state.
        assertEquals(GamePhase.NONE, gamePhase(foreground = false, firstFrame = true, menuOrPaused = false))
        assertEquals(GamePhase.NONE, gamePhase(foreground = false, firstFrame = false, menuOrPaused = true))
    }
}
