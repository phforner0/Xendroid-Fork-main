package xendroid.compose.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xendroid.compose.settings.ConfigMigrations.Update

class ConfigMigrationsTest {
    private val mailbox = Update(2026100712, "Vulkan", "vulkan_allow_present_mode_mailbox", "bool", "true", "false")
    private val leaf = Update(2026100512, "CPU", "inline_leaf_max_instructions", "int", "16", "32")
    private val ir3 = Update(2026100511, "Vulkan", "ir3_debug", "string", "", "noearlypreamble")

    private fun file(vararg entries: Pair<String, String>): (String, String) -> String? {
        val map = entries.toMap()
        return { section, name -> map["$section|$name"] }
    }

    @Test fun rowsFromTheCore() {
        val rows = arrayOf(
            "2026100712\tVulkan\tvulkan_allow_present_mode_mailbox\tbool\ttrue\tfalse",
            "2026100511\tVulkan\tir3_debug\tstring\t\tnoearlypreamble",
            "bad row", "2026100712\tA\tb\tlist\tx\ty")
        assertEquals(listOf(mailbox, ir3), ConfigMigrations.parse(rows))
        assertEquals(emptyList<Update>(), ConfigMigrations.parse(null))
    }

    @Test fun anOldDefaultTakesTheNewOneAndTheFileIsDated() {
        val plan = ConfigMigrations.plan(listOf(ir3, leaf, mailbox), file(
            "Config|defaults_date" to "2026072714",
            "Vulkan|vulkan_allow_present_mode_mailbox" to "true",
            "CPU|inline_leaf_max_instructions" to "16",
            "Vulkan|ir3_debug" to ""))!!
        assertEquals(setOf(mailbox, leaf, ir3), plan.writes.toSet())
        assertEquals(2026100712L, plan.date)
    }

    @Test fun thePlayersOwnValuesAndAbsentOnesStay() {
        // Set away from the old default, or not in the file (already the new default): only dated.
        val plan = ConfigMigrations.plan(listOf(leaf, mailbox), file(
            "Config|defaults_date" to "2026072714",
            "CPU|inline_leaf_max_instructions" to "24"))!!
        assertEquals(emptyList<Update>(), plan.writes)
        assertEquals(2026100712L, plan.date)
    }

    @Test fun aDatedFileIsLeftAsIsSoAChoiceOfTheOldDefaultSticks() {
        // Once dated past the update, true is the player's choice (the bug: it reverted every boot).
        assertNull(ConfigMigrations.plan(listOf(mailbox), file(
            "Config|defaults_date" to "2026100712",
            "Vulkan|vulkan_allow_present_mode_mailbox" to "true")))
        // The core applies nothing to a file without a date either.
        assertNull(ConfigMigrations.plan(listOf(mailbox), file(
            "Vulkan|vulkan_allow_present_mode_mailbox" to "true")))
    }

    @Test fun onlyTheUpdatesNewerThanTheFile() {
        val plan = ConfigMigrations.plan(listOf(leaf, mailbox), file(
            "Config|defaults_date" to "2026100600",
            "Vulkan|vulkan_allow_present_mode_mailbox" to "true",
            "CPU|inline_leaf_max_instructions" to "16"))!!
        // The 2026100512 update is older than the file: its 16 is the player's.
        assertEquals(listOf(mailbox), plan.writes)
        assertEquals(2026100712L, plan.date)
    }

    @Test fun valuesCompareByType() {
        val plan = ConfigMigrations.plan(listOf(mailbox, leaf), file(
            "Config|defaults_date" to "1",
            "Vulkan|vulkan_allow_present_mode_mailbox" to "TRUE",
            "CPU|inline_leaf_max_instructions" to " 16"))!!
        assertEquals(setOf(mailbox, leaf), plan.writes.toSet())
    }

    @Test fun whatTheNativeWriterWouldRetypeIsNotWritten() {
        assertTrue(ConfigMigrations.writable(mailbox))
        assertTrue(ConfigMigrations.writable(leaf))
        assertTrue(ConfigMigrations.writable(ir3))
        assertFalse(ConfigMigrations.writable(ir3.copy(newDefault = "16")))
        assertFalse(ConfigMigrations.writable(ir3.copy(newDefault = "true")))
        assertFalse(ConfigMigrations.writable(leaf.copy(newDefault = "4294967295")))
    }
}
