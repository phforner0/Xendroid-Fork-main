package xendroid.compose.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InGameChangesTest {
    /** The game's own values and the global ones, as the TOML files and preferences would hold them. */
    private class FakeStore(val game: MutableMap<String, String> = mutableMapOf(), val global: MutableMap<String, String> = mutableMapOf()) :
        InGameChanges.Store {
        var gameWrites = 0
        override fun gameValue(key: String): String? = game[key]
        override fun writeGame(values: Map<String, String?>) {
            gameWrites++
            values.forEach { (k, v) -> if (v == null) game.remove(k) else game[k] = v }
        }
        override fun writeGlobal(values: Map<String, String>) { global.putAll(values) }
    }

    @Test fun aChangeIsKeptForTheGameAtOnce() {
        val store = FakeStore(game = mutableMapOf("GPU|framerate_limit" to "30"))
        val changes = InGameChanges(store)
        changes.change("GPU|framerate_limit", "60", keep = true)
        changes.change(InGameChanges.DISPLAY_MODE, "1", keep = true)
        assertEquals("60", store.game["GPU|framerate_limit"])
        assertEquals("1", store.game[InGameChanges.DISPLAY_MODE])
        assertEquals(2, changes.count)
        assertEquals("30", changes.original("GPU|framerate_limit"))
    }

    @Test fun changedBackIsNoChange() {
        val store = FakeStore(game = mutableMapOf("GPU|framerate_limit" to "30"))
        val changes = InGameChanges(store)
        changes.change("GPU|framerate_limit", "60", keep = true)
        changes.change("GPU|framerate_limit", "30", keep = true)
        assertEquals(0, changes.count)
    }

    @Test fun notKeptUntilTheSwitchTurnsOn() {
        val store = FakeStore()
        val changes = InGameChanges(store)
        changes.change("Display|postprocess_antialiasing", "fxaa", keep = false)
        assertEquals(0, store.gameWrites)
        assertEquals(1, changes.count)
        changes.keepAll()
        assertEquals("fxaa", store.game["Display|postprocess_antialiasing"])
    }

    @Test fun undoPutsTheGameBackAsItWas() {
        val store = FakeStore(game = mutableMapOf("GPU|framerate_limit" to "30"))
        val changes = InGameChanges(store)
        changes.change("GPU|framerate_limit", "60", keep = true)
        changes.change("Display|postprocess_antialiasing", "fxaa", keep = true)
        val restored = changes.undo()
        assertEquals("30", store.game["GPU|framerate_limit"])
        assertNull("it had none: it inherits again", store.game["Display|postprocess_antialiasing"])
        assertEquals(mapOf("GPU|framerate_limit" to "30", "Display|postprocess_antialiasing" to null), restored)
        assertEquals(0, changes.count)
    }

    @Test fun makeGlobalMovesTheValuesOutOfTheGame() {
        val store = FakeStore(game = mutableMapOf("GPU|framerate_limit" to "30", "HID|show_touch_overlay" to "false"))
        val changes = InGameChanges(store)
        changes.change("GPU|framerate_limit", "60", keep = true)
        changes.change(InGameChanges.COLOR_FILTER, "2", keep = true)
        changes.makeGlobal()
        assertEquals(mapOf("GPU|framerate_limit" to "60", InGameChanges.COLOR_FILTER to "2"), store.global)
        assertNull(store.game["GPU|framerate_limit"])
        assertNull(store.game[InGameChanges.COLOR_FILTER])
        assertEquals("a setting this session did not touch stays the game's", "false", store.game["HID|show_touch_overlay"])
        assertEquals(0, changes.count)
    }

    @Test fun backToInheritingIsAChangeToo() {
        val store = FakeStore(game = mutableMapOf("Display|postprocess_scaling_and_sharpening" to "fsr"))
        val changes = InGameChanges(store)
        changes.change("Display|postprocess_scaling_and_sharpening", null, keep = true)
        assertNull(store.game["Display|postprocess_scaling_and_sharpening"])
        assertEquals(1, changes.count)
    }
}
