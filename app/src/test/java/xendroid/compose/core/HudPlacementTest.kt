package xendroid.compose.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudPlacementTest {
    private class MapStore(val values: MutableMap<String, Any> = mutableMapOf()) : HudPlacements.Store {
        override fun float(key: String) = values[key] as? Float
        override fun string(key: String) = values[key] as? String
        override fun put(values: Map<String, Any>) { this.values.putAll(values) }
    }

    @Test fun nothingSavedIsTheTopLeftBox() {
        assertEquals(HudPlacement(0f, 0f, 1f, HudLook.BOX), HudPlacements.read(MapStore(), "4D5307E6"))
    }

    @Test fun whatWasSavedBeforeStaysWhereItWas() {
        // The keys of the HUD before 15g are the "last set anywhere" ones.
        val store = MapStore(mutableMapOf("x" to 40f, "y" to 12f, "scale" to 1.4f))
        assertEquals(HudPlacement(40f, 12f, 1.4f, HudLook.BOX), HudPlacements.read(store, "4D5307E6"))
        assertEquals(HudPlacement(40f, 12f, 1.4f, HudLook.BOX), HudPlacements.read(store, null))
    }

    @Test fun eachGameKeepsItsOwnAndANewGameStartsFromTheLast() {
        val store = MapStore()
        HudPlacements.write(store, "4d5307e6", HudPlacement(100f, 20f, 2f, HudLook.OUTLINE))
        HudPlacements.write(store, "415607E6", HudPlacement(5f, 300f, 0.8f, HudLook.PLAIN))
        assertEquals(HudPlacement(100f, 20f, 2f, HudLook.OUTLINE), HudPlacements.read(store, "4D5307E6"))
        assertEquals(HudPlacement(5f, 300f, 0.8f, HudLook.PLAIN), HudPlacements.read(store, "415607E6"))
        // A game with none of its own starts where the HUD was last put.
        assertEquals(HudPlacement(5f, 300f, 0.8f, HudLook.PLAIN), HudPlacements.read(store, "58410889"))
        assertTrue("x@4D5307E6" in store.values)
    }

    @Test fun noGameOrNotAGameIsTheSharedPlace() {
        val store = MapStore()
        HudPlacements.write(store, null, HudPlacement(1f, 2f, 1f, HudLook.BOX))
        HudPlacements.write(store, "../x", HudPlacement(3f, 4f, 1f, HudLook.BOX))
        HudPlacements.write(store, "00000000", HudPlacement(5f, 6f, 1f, HudLook.BOX))
        assertFalse(store.values.keys.any { '@' in it })
        assertEquals(HudPlacement(5f, 6f, 1f, HudLook.BOX), HudPlacements.read(store, null))
    }

    @Test fun damagedValuesAreBroughtBack() {
        val store = MapStore(mutableMapOf("x" to Float.NaN, "y" to -5f, "scale" to 40f, "look" to "neon"))
        assertEquals(HudPlacement(0f, 0f, HudPlacements.MAX_SCALE, HudLook.BOX), HudPlacements.read(store, null))
        assertEquals(listOf(HudLook.OUTLINE, HudLook.PLAIN, HudLook.BOX), HudLook.entries.map { it.next() })
    }

    @Test fun gpuMemoryAsKgslCountsIt() {
        assertEquals(1_342_177_280L, KgslMemory.parse("1342177280\n"))
        assertEquals(null, KgslMemory.parse("n/a"))
        assertEquals(null, KgslMemory.parse("-4"))
        assertEquals(null, KgslMemory.parse(null))
        assertEquals("GPU mem 1.25 GB (all apps)", KgslMemory.line(1_342_177_280L))
        assertEquals("GPU mem 512 MB (all apps)", KgslMemory.line(512L * 1024 * 1024))
        assertEquals("GPU mem N/A", KgslMemory.line(null))
    }
}
