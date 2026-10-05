package xendroid.compose.gamepad

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutPresetsTest {
    // The runtime store's settings (GamepadConfigSerializer).
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val racing = OrientationLayoutDto(listOf(ControlLayoutDto("A", 0.7f, 0.5f, scale = 1.6f)))

    @Test fun whatThisBuildDoesNotKnowSurvivesReadingAndWriting() {
        // A newer build added a control (PADDLE_L) and a field (rotation) this one does not know.
        val stored = """{"version":2,"landscape":{"controls":[
            {"id":"A","x":0.9,"y":0.8,"rotation":15},{"id":"PADDLE_L","x":0.1,"y":0.9}]},
            "presets":[{"name":"Racing","landscape":{"controls":[]},"author":"someone"}]}"""
        val cfg = json.decodeFromString(GamepadConfigDto.serializer(), stored)
        assertEquals(JsonPrimitive(15), cfg.landscape.controls[0].extra["rotation"])
        assertEquals("PADDLE_L", cfg.landscape.controls[1].id)
        assertEquals(JsonPrimitive("someone"), cfg.presets.single().extra["author"])
        val written = json.encodeToString(GamepadConfigDto.serializer(), cfg)
        assertTrue(written.contains("\"rotation\":15"))
        assertTrue(written.contains("\"author\":\"someone\""))
        assertFalse(written.contains("\"extra\""))                                  // flattened back
        assertEquals(cfg, json.decodeFromString(GamepadConfigDto.serializer(), written))
        // Applying it to this build's controls ignores the unknown one.
        assertEquals(defaultLayout(true).size, cfg.landscape.applyTo(defaultLayout(true)).size)
    }

    @Test fun editingKeepsWhatThisBuildDoesNotKnow() {
        val previous = json.decodeFromString(OrientationLayoutDto.serializer(),
            """{"controls":[{"id":"A","x":0.9,"y":0.8,"rotation":15},{"id":"PADDLE_L","x":0.1,"y":0.9}]}""")
        // The editor rebuilds the layout from this build's controls: A moved.
        val edited = previous.applyTo(defaultLayout(true)).map { if (it.id == ControlId.A) it.withLayout(x = 0.5f, y = 0.5f) else it }.toDto()
        assertTrue(edited.controls.none { it.id == "PADDLE_L" })                    // what the editor alone would save
        val kept = edited.preserving(previous)
        val a = kept.controls.single { it.id == "A" }
        assertEquals(0.5f, a.x, 0f)
        assertEquals(JsonPrimitive(15), a.extra["rotation"])
        assertEquals(1, kept.controls.count { it.id == "PADDLE_L" })
        // Preserving twice changes nothing more.
        assertEquals(kept, kept.preserving(previous))
    }

    @Test fun layoutsAreNamedOnceAndBounded() {
        assertEquals("Racing wheel", LayoutPresets.cleanName("  Racing \n wheel "))
        assertNull(LayoutPresets.cleanName("   "))
        assertNull(LayoutPresets.cleanName("x".repeat(LayoutPresets.MAX_NAME + 1)))
        var cfg = GamepadConfigDto(version = 2)
        cfg = LayoutPresets.save(cfg, LayoutPresetDto("Racing", landscape = racing))
        cfg = LayoutPresets.save(cfg, LayoutPresetDto("racing", portrait = racing))       // same name: replaced
        assertEquals(1, cfg.presets.size)
        assertEquals("racing", cfg.presets.single().name)
        assertNull(cfg.presets.single().landscape)
        assertEquals("Racing (2)", LayoutPresets.uniqueName(cfg, "Racing"))
        assertEquals("Shooter", LayoutPresets.uniqueName(cfg, "Shooter"))
        assertTrue(runCatching { LayoutPresets.save(cfg, LayoutPresetDto("Empty")) }.isFailure)      // no orientation
        repeat(LayoutPresets.MAX_PRESETS - 1) { cfg = LayoutPresets.save(cfg, LayoutPresetDto("L$it", landscape = racing)) }
        assertTrue(runCatching { LayoutPresets.save(cfg, LayoutPresetDto("One more", landscape = racing)) }.isFailure)
        assertEquals(LayoutPresets.MAX_PRESETS - 1, LayoutPresets.delete(cfg, "RACING").presets.size)
    }

    @Test fun applyingReplacesOnlyTheEditedLayoutAndThePreviewSaysWhatMoves() {
        val shared = OrientationLayoutDto(listOf(ControlLayoutDto("A", 0.9f, 0.8f)))
        val cfg = GamepadConfigDto(version = 2, landscape = shared, portrait = shared)
        val preset = LayoutPresets.capture(cfg.withLayout(null, true, racing), null, "Racing", 7L)
        assertEquals(racing, preset.landscape)
        assertEquals(shared, preset.portrait)
        val forGame = LayoutPresets.apply(cfg, preset.copy(portrait = null), "4D5309C9")
        assertEquals(racing, forGame.layoutFor("4D5309C9", true))
        assertEquals(shared, forGame.layoutFor("4D5309C9", false))                  // no portrait in it: unchanged
        assertEquals(shared, forGame.landscape)                                      // the shared layout is untouched
        val b = defaultLayout(true).first { it.id == ControlId.B }                   // B only hidden, where it is
        val next = OrientationLayoutDto(listOf(ControlLayoutDto("A", 0.7f, 0.5f, scale = 1.6f),
            ControlLayoutDto("B", b.xFraction, b.yFraction, scale = b.scale, visible = false)))
        val diff = LayoutPresets.diff(shared, next, defaultLayout(true))
        assertEquals(LayoutPresets.Diff(moved = 1, resized = 1, shown = 0, hidden = 1), diff)
        assertEquals("Controls: 1 moved, 1 resized, 1 hidden", diff.summary)
        assertEquals("Nothing changes", LayoutPresets.diff(shared, shared, defaultLayout(true)).summary)
    }

    @Test fun aLayoutFileIsVersionedAndChecked() {
        val preset = LayoutPresetDto("Racing", landscape = racing, savedAt = 5L)
        val text = LayoutPresets.encodeFile(preset)
        val root = json.parseToJsonElement(text).jsonObject
        assertEquals(JsonPrimitive("xendroid-touch-layout"), root["format"])
        assertEquals(JsonPrimitive(1), root["version"])
        assertEquals(LayoutPresets.Decoded.Ok(preset), LayoutPresets.decodeFile(text))
        // A newer exporter's extra field is kept for the next export.
        val withAuthor = text.replace("\"name\":", "\"author\":\"someone\",\"name\":")
        val imported = (LayoutPresets.decodeFile(withAuthor) as LayoutPresets.Decoded.Ok).preset
        assertTrue(LayoutPresets.encodeFile(imported).contains("\"author\":\"someone\""))
        fun refusal(t: String) = (LayoutPresets.decodeFile(t) as LayoutPresets.Decoded.Refused).reason
        assertTrue(refusal(text.replace("\"version\":1", "\"version\":2")).startsWith("Made by a newer XenDroid"))
        assertEquals("Not a XenDroid touch layout", refusal(text.replace("xendroid-touch-layout", "other")))
        assertEquals("Not a layout file", refusal("{"))
        assertEquals("A is off the screen", refusal(text.replace("\"x\":0.7", "\"x\":1.5")))
        assertEquals("A has a size out of range", refusal(text.replace("\"scale\":1.6", "\"scale\":9.0")))
        val twice = LayoutPresets.encodeFile(preset.copy(landscape = OrientationLayoutDto(racing.controls + racing.controls)))
        assertEquals("A control appears twice", refusal(twice))
        assertEquals("The file has no layout", refusal(LayoutPresets.encodeFile(preset.copy(landscape = null))))
        assertTrue(refusal(" ".repeat(LayoutPresets.MAX_FILE_BYTES + 1)).startsWith("The file is larger"))
        // The screen says them from the reason and its detail, in its language.
        val off = LayoutPresets.decodeFile(text.replace("\"x\":0.7", "\"x\":1.5")) as LayoutPresets.Decoded.Refused
        assertEquals(LayoutPresets.Refusal.OFF_SCREEN to "A", off.why to off.detail)
        val full = (1..LayoutPresets.MAX_PRESETS).fold(GamepadConfigDto()) { cfg, n -> LayoutPresets.save(cfg, preset.copy(name = "L$n")) }
        val refused = runCatching { LayoutPresets.save(full, preset.copy(name = "One more")) }.exceptionOrNull() as LayoutPresets.RefusedException
        assertEquals(LayoutPresets.Refusal.FULL, refused.why)
        assertEquals("At most ${LayoutPresets.MAX_PRESETS} layouts; delete one first", refused.message)
    }
}
