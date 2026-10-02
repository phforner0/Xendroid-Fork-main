package xendroid.compose.ui.ingame

import org.junit.Assert.*
import org.junit.Test

class InGameLogPickerTest {
    @Test fun sessionPickerHasAllArchivesAndBackWithoutSwitchingTabs() {
        val state = InGameMenuState().show(true).changePage(3).showLogs(4)
        assertEquals(6, state.count)
        assertEquals(InGamePage.SESSION, state.changePage(1).page)
        assertNull(state.action)
        assertEquals(5, state.move(-1).selected)
        assertFalse(state.closeLogs().logPicker)
        assertEquals(InGamePage.SESSION, state.closeLogs().page)
    }
}
