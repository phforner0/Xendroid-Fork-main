package xendroid.compose.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.core.EmulatorRuntime

/** Real JNI/TOML + Android filesystem tests, isolated from installed game/user data. */
@RunWith(AndroidJUnit4::class)
class ConfigTransactionsInstrumentedTest {
    private lateinit var root: File
    private lateinit var store: ConfigStore
    private val title = "0F000001"
    private val fps = SettingsSchema.allSettings.single { it.key == "GPU|framerate_limit" } as Setting.ListChoice
    private val touch = SettingsSchema.allSettings.single { it.key == "HID|show_touch_overlay" } as Setting.Bool

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        EmulatorRuntime.ensureLoaded()
        root = File(context.cacheDir, "config-jni-${System.nanoTime()}").apply { check(mkdirs()) }
        val global = File(root, "global.toml").apply {
            writeText("[GPU]\nframerate_limit = 60\n[HID]\nshow_touch_overlay = true\n")
        }
        store = ConfigStore(context, global)
    }

    @After fun tearDown() { if (::root.isInitialized) root.deleteRecursively() }

    private fun writeGame(text: String): File = store.perGameConfigFile(title).apply {
        parentFile!!.mkdirs()
        writeText(text)
    }

    @Test fun removingLastKnownOverrideKeepsUnknownTypesAndNestedTables() {
        val file = writeGame("""
            [GPU]
            framerate_limit = 30
            [Future]
            large = 9223372036854775807
            values = [1, 2, 3]
            [Future.nested]
            text = "keep-this-nested-value"
        """.trimIndent())
        val repo = GameSettingsRepository(store, title).apply { open() }
        repo.setOverride(fps, false)
        repo.flush()
        assertTrue(file.exists())
        val h = store.openGameConfig(title)
        try {
            assertNull(h.getString("GPU", "framerate_limit"))
            assertEquals("9223372036854775807", h.getString("Future", "large"))
            val text = h.closeString()
            assertTrue(Regex("values\\s*=\\s*\\[\\s*1\\s*,\\s*2\\s*,\\s*3\\s*]").containsMatchIn(text))
            assertTrue(text.contains("[Future.nested]"))
            assertTrue(text.contains("keep-this-nested-value"))
        } finally { h.closeDiscard() }
    }

    @Test fun removingLastOverrideDeletesOnlyTrulyEmptyConfig() {
        val file = writeGame("[GPU]\nframerate_limit = 30\n")
        val repo = GameSettingsRepository(store, title).apply { open() }
        repo.setOverride(fps, false)
        repo.flush()
        assertFalse(file.exists())
        assertEquals("60", store.openLiveSnapshot().let { h ->
            try { h.getString("GPU", "framerate_limit") } finally { h.closeDiscard() }
        })
    }

    @Test fun staleEditorsMergeOnlyTheirOwnEdits() {
        writeGame("[Future]\nuntouched = true\n")
        val first = GameSettingsRepository(store, title).apply { open() }
        val second = GameSettingsRepository(store, title).apply { open() }
        first.setListValue(fps, "30")
        second.setBool(touch, false)
        first.flush()
        second.flush()
        val h = store.openGameConfig(title)
        try {
            assertEquals("30", h.getString("GPU", "framerate_limit"))
            assertEquals("false", h.getString("HID", "show_touch_overlay"))
            assertEquals("true", h.getString("Future", "untouched"))
        } finally { h.closeDiscard() }
    }

    @Test fun malformedLateEditIsNotErasedAndPendingChangeCanRetry() {
        val file = writeGame("[GPU]\nframerate_limit = 60\n")
        val repo = GameSettingsRepository(store, title).apply { open() }
        repo.setListValue(fps, "45")
        val invalid = "[GPU]\nframerate_limit = [unfinished"
        file.writeText(invalid)
        assertThrows(Exception::class.java) { repo.flush() }
        assertEquals(invalid, file.readText())
        file.writeText("[Future]\nkeep = 'repaired'\n")
        repo.flush()
        val h = store.openGameConfig(title)
        try {
            assertEquals("45", h.getString("GPU", "framerate_limit"))
            assertEquals("repaired", h.getString("Future", "keep"))
        } finally { h.closeDiscard() }
    }

    @Test fun openingMalformedConfigDoesNotResetIt() {
        val file = writeGame("[[not-closed")
        assertThrows(Exception::class.java) { GameSettingsRepository(store, title).open() }
        assertEquals("[[not-closed", file.readText())
    }

    @Test fun globalSnapshotDoesNotOverwriteOtherWritersKeys() {
        val repo = SettingsRepository(store).apply { open() }
        repo.setListValue(fps, "30")
        store.editLiveConfig { h ->
            h.putBool("HID", "show_touch_overlay", false)
            h.putString("Future", "outside-schema", "preserved")
        }
        repo.flushAndClose()
        val h = store.openLiveSnapshot()
        try {
            assertEquals("30", h.getString("GPU", "framerate_limit"))
            assertEquals("false", h.getString("HID", "show_touch_overlay"))
            assertEquals("preserved", h.getString("Future", "outside-schema"))
        } finally { h.closeDiscard() }
    }

    @Test fun malformedGlobalConfigIsNeverReseededFromTheTemplate() {
        val global = store.globalConfigFile()
        val invalid = "[GPU]\nframerate_limit = [unfinished\n[Future]\nkeep = 1\n"
        global.writeText(invalid)
        assertThrows(Exception::class.java) {
            store.editLiveConfig { it.putBool("HID", "show_touch_overlay", false) }
        }
        assertThrows(Exception::class.java) { store.openLiveSnapshot() }
        assertEquals(invalid, global.readText())
    }

    @Test fun missingGlobalConfigIsCreatedOnceFromTheTemplate() {
        val global = store.globalConfigFile()
        check(global.delete())
        store.editLiveConfig { it.putString("Future", "seeded", "yes") }
        val h = store.openLiveSnapshot()
        try {
            assertEquals("yes", h.getString("Future", "seeded"))
            assertNotNull(h.getString("GPU", "framerate_limit"))
        } finally { h.closeDiscard() }
    }

    @Test fun titleIdCannotEscapeConfigDirectory() {
        assertThrows(IllegalArgumentException::class.java) { store.perGameConfigFile("../../other") }
        assertThrows(IllegalArgumentException::class.java) { store.perGameConfigFile("00000000") }
        assertEquals("0F000001.config.toml", store.perGameConfigFile(title.lowercase()).name)
    }

    @Test fun inGameFpsPersistenceSeparatesGlobalGameAndInheritedValues() {
        val file = writeGame("[GPU]\nframerate_limit = 30\n[Future]\nkeep = 'untouched'\n")
        val menu = InGameConfigRepository(store)
        assertEquals(FpsConfigSnapshot(title, 60, 30), menu.fpsSnapshot(title))
        menu.saveGlobalFps(90)
        assertEquals(FpsConfigSnapshot(title, 90, 30), menu.fpsSnapshot(title))
        menu.saveGameFps(title, 45)
        assertEquals(FpsConfigSnapshot(title, 90, 45), menu.fpsSnapshot(title))
        assertEquals(90, menu.inheritGlobalFps(title))
        assertEquals(FpsConfigSnapshot(title, 90, null), menu.fpsSnapshot(title))
        assertTrue(file.readText().contains("untouched"))
    }
}
