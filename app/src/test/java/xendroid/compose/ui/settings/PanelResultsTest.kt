package xendroid.compose.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingCatalog
import xendroid.compose.settings.SettingGroup
import xendroid.compose.settings.SettingLevel

/** Round 2: a search looks in its tab, suggests the other tabs, and says what the level hides. */
class PanelResultsTest {
    private val editing = MapSettingsEditing(forGame = false)
    private fun text(s: Setting) = s.title + " " + s.desc
    private fun state(query: String = "", level: SettingLevel = SettingLevel.ALL) =
        SettingsPanelState(level).also { it.query = query }
    private fun groupOf(s: Setting) = SettingCatalog.meta(s).group

    @Test fun aSearchInATabListsThatTabAndSuggestsTheOthers() {
        // "GPU|" keys live in several tabs.
        val found = panelResults(SettingGroup.IMAGE, state("gpu"), editing, emptyList(), ::text)
        assertTrue(found.rows.isNotEmpty())
        assertTrue(found.rows.all { groupOf(it) == SettingGroup.IMAGE })
        assertTrue(found.elsewhere.isNotEmpty())
        assertTrue(found.elsewhere.none { groupOf(it) == SettingGroup.IMAGE })
        // Everything that matches is somewhere: here or elsewhere, once.
        val all = panelResults(null, state("gpu"), editing, emptyList(), ::text).rows
        assertEquals(all.map { it.key }.toSet(), (found.rows + found.elsewhere).map { it.key }.toSet())
        assertEquals(all.size, found.rows.size + found.elsewhere.size)
    }

    @Test fun theLevelSwitchCountsWhatEachLevelWouldList() {
        val plain = panelResults(SettingGroup.IMAGE, state(level = SettingLevel.ESSENTIAL), editing, emptyList(), ::text)
        assertEquals(SettingLevel.entries.map { SettingCatalog.settings(SettingGroup.IMAGE, it).size }, plain.perLevel)
        assertEquals(plain.perLevel[0], plain.rows.size)
        assertEquals("no search, no filter: the level hides nothing worth saying", 0, plain.hidden)
        assertTrue(plain.perLevel.zipWithNext().all { (a, b) -> a <= b })
        // Every tab together is the Summary's total.
        val total = panelResults(null, state(), editing, emptyList(), ::text).perLevel
        assertEquals(SettingLevel.entries.map { SettingCatalog.settings(it).size }, total)
    }

    @Test fun aSearchSaysHowManyResultsTheLevelHides() {
        val essential = panelResults(SettingGroup.IMAGE, state("gpu", SettingLevel.ESSENTIAL), editing, emptyList(), ::text)
        val everything = panelResults(SettingGroup.IMAGE, state("gpu", SettingLevel.ALL), editing, emptyList(), ::text)
        assertEquals(everything.rows.size - essential.rows.size, essential.hidden)
        assertTrue(essential.hidden > 0)
        assertEquals(0, everything.hidden)
        // The other tabs suggest at any level: the jump there says if the level hides it.
        assertEquals(everything.elsewhere, essential.elsewhere)
    }

    @Test fun withoutASearchThereAreNoSuggestions() {
        val found = panelResults(SettingGroup.PERFORMANCE, state(), editing, emptyList(), ::text)
        assertTrue(found.elsewhere.isEmpty())
        val summary = panelResults(null, state("gpu"), editing, emptyList(), ::text)
        assertTrue("the Summary already searches every tab", summary.elsewhere.isEmpty())
        assertTrue(summary.rows.map(::groupOf).toSet().size > 1)
    }
}
