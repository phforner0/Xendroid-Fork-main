package xendroid.compose.ui.design

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Round 2: a link to another section can be undone with Back; picking from the list starts over. */
class SectionHistoryTest {
    /** The screen: [select] changes the section; the history then sees it, as the effect would. */
    private class Screen(start: String) {
        val history = XdSectionHistory(mutableStateOf(emptyList()))
        var selected = start
        init {
            history.onSelect = { select(it) }
            history.observe(start)
        }
        fun select(id: String) { if (id != selected) { selected = id; history.observe(id) } }
    }

    @Test fun aLinkLeavesAStepAndBackReturns() {
        val s = Screen("resumo")
        s.select("set:IMAGE")                       // "All settings", "See the patches"...
        assertEquals("resumo", s.history.previous)
        s.select("set:DRIVER")                      // a suggestion from another tab
        assertEquals("set:IMAGE", s.history.previous)
        s.history.back()
        assertEquals("set:IMAGE", s.selected)
        assertEquals("resumo", s.history.previous)
        s.history.back()
        assertEquals("resumo", s.selected)
        assertNull(s.history.previous)
    }

    @Test fun pickingFromTheListStartsOver() {
        val s = Screen("resumo")
        s.select("set:IMAGE")
        s.history.pick("set:AUDIO")
        assertEquals("set:AUDIO", s.selected)
        assertNull(s.history.previous)
        // The next link is remembered as usual.
        s.select("set:SYSTEM")
        assertEquals("set:AUDIO", s.history.previous)
    }

    @Test fun pickingTheSectionAlreadyOpenDoesNotSwallowTheNextLink() {
        val s = Screen("resumo")
        s.history.pick("resumo")
        s.select("set:IMAGE")
        assertEquals("resumo", s.history.previous)
    }
}
