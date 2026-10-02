package xendroid.compose.gamepad

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TitleLayoutTest {
    private val shared = OrientationLayoutDto(listOf(ControlLayoutDto("A", 0.9f, 0.8f)))
    private val racing = OrientationLayoutDto(listOf(ControlLayoutDto("A", 0.7f, 0.5f, scale = 1.6f)))
    private val base = GamepadConfigDto(version = 2, landscape = shared, portrait = shared)

    @Test fun aGameUsesItsOwnLayoutOnlyWhereItHasOne() {
        val cfg = base.withLayout("4d5309c9", landscape = true, layout = racing)
        assertEquals(racing, cfg.layoutFor("4D5309C9", landscape = true))
        assertEquals(shared, cfg.layoutFor("4D5309C9", landscape = false))   // no portrait of its own
        assertEquals(shared, cfg.layoutFor("415607E6", landscape = true))    // another game
        assertEquals(shared, cfg.layoutFor(null, landscape = true))
        assertTrue(cfg.hasOwnLayout("4D5309C9", true))
        assertFalse(cfg.hasOwnLayout("4D5309C9", false))
        assertEquals(shared, cfg.landscape)                                   // the shared one is untouched
    }

    @Test fun withoutATitleEditsGoToTheSharedLayout() {
        for (title in listOf(null, "00000000", "../x", "4D5309C")) {
            val cfg = base.withLayout(title, landscape = true, layout = racing)
            assertEquals(racing, cfg.landscape)
            assertTrue(cfg.titles.isEmpty())
        }
    }

    @Test fun goingBackToTheSharedLayoutDropsEmptyEntries() {
        val both = base.withLayout("4D5309C9", true, racing).withLayout("4D5309C9", false, racing)
        val oneLeft = both.withoutOwnLayout("4D5309C9", landscape = true)
        assertEquals(shared, oneLeft.layoutFor("4D5309C9", true))
        assertEquals(racing, oneLeft.layoutFor("4D5309C9", false))
        assertTrue(oneLeft.withoutOwnLayout("4D5309C9", landscape = false).titles.isEmpty())
        assertEquals(base, base.withoutOwnLayout("4D5309C9", true))
    }

    @Test fun layoutsWrittenBeforePerGameLayoutsStillLoad() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = json.decodeFromString(GamepadConfigDto.serializer(),
            """{"version":2,"globals":{"opacity":0.5},"landscape":{"controls":[{"id":"A","x":0.9,"y":0.8}]}}""")
        assertTrue(old.titles.isEmpty())
        assertEquals(0.5f, old.globals.opacity)
        val withGame = old.withLayout("4D5309C9", true, racing)
        assertEquals(withGame, json.decodeFromString(GamepadConfigDto.serializer(),
            json.encodeToString(GamepadConfigDto.serializer(), withGame)))
    }
}
