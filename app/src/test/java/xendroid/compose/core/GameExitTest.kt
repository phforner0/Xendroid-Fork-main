package xendroid.compose.core

import org.junit.Assert.assertEquals
import org.junit.Test
import xendroid.compose.core.GameExit.Way

class GameExitTest {
    @Test fun theGameLeavesTowardsWhereItWasStartedFrom() {
        // From the library: back to it, in the same task.
        assertEquals(Way.FINISH, GameExit.way("library", isTaskRoot = false))
        assertEquals(Way.FINISH, GameExit.way("library", isTaskRoot = true))
        // A shortcut or a frontend started a task of its own: no empty task left in Recents.
        assertEquals(Way.FINISH_AND_REMOVE_TASK, GameExit.way("shortcut", isTaskRoot = true))
        assertEquals(Way.FINISH_AND_REMOVE_TASK, GameExit.way("external:org.es_de.frontend", isTaskRoot = true))
        // The library was open in the task underneath: the player goes back to the frontend, the library stays.
        assertEquals(Way.TASK_TO_BACK_THEN_FINISH, GameExit.way("external:org.es_de.frontend", isTaskRoot = false))
        assertEquals(Way.TASK_TO_BACK_THEN_FINISH, GameExit.way("external", isTaskRoot = false))
    }
}
