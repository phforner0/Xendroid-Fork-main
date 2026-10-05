package xendroid.compose.roadmap

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.bundle.DataBundleIo
import xendroid.compose.bundle.DataBundles
import xendroid.compose.bundle.ImportPlan
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.gamepad.GamepadConfigDto
import xendroid.compose.gamepad.GamepadLayoutStore
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.ui.settings.DataBundleSection
import xendroid.compose.ui.theme.xendroidTheme

/**
 * Roadmap item 13 (L08): Settings → "Back up or move settings", on the real section. Export
 * (the system picker answered with a test file), change a global setting and the touch
 * layout, import the exported file: the preview lists exactly those changes and the backup
 * note, and after importing the values are back. This phone's device paths (driver library,
 * storage folders) stay as they are now, and a file that is not a bundle is refused with a
 * message, changing nothing.
 */
@RunWith(AndroidJUnit4::class)
class Item13DataBundleTest {
    @get:Rule val compose = createComposeRule()

    private val context = Device.context
    private val fps = SettingsSchema.byKey.getValue("GPU|framerate_limit") as Setting.ListChoice
    private val layouts get() = GamepadLayoutStore(context)
    private lateinit var global: File
    private var savedConfig: ByteArray? = null
    private lateinit var savedLayout: GamepadConfigDto

    @Before fun setUp() {
        Device.requireTestPackage()
        EmulatorRuntime.ensureLoaded()
        global = ConfigStore(context).globalConfigFile()
        savedConfig = global.takeIf { it.isFile }?.readBytes()
        savedLayout = runBlocking { layouts.config.first() }
        Intents.init()
    }

    @After fun tearDown() {
        Intents.release()
        savedConfig?.let { global.writeBytes(it) }
        runBlocking { layouts.save(savedLayout) }
    }

    private fun live(section: String, name: String): String? = ConfigStore(context).openLiveSnapshot().let { h ->
        try { h.getString(section, name) } finally { h.closeDiscard() }
    }

    /** The system file picker, answered with [uri]. */
    private fun answer(action: String, uri: Uri) {
        Intents.intending(hasAction(action)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
    }

    private fun waitFor(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun opacity(value: Float) = runBlocking { layouts.save(savedLayout.copy(globals = savedLayout.globals.copy(opacity = value))) }

    @Test fun theImportShowsExactlyTheChangesAndPutsThemBack() {
        val store = ConfigStore(context)
        store.editLiveConfig { it.putSetting(fps, "60"); it.putString("Vulkan", "vulkan_lib_path", "/this/phone/a/libvulkan.so") }
        val contentRoot = live("Storage", "content_root")
        opacity(0.65f)
        compose.setContent { xendroidTheme { Column { DataBundleSection(beforeImport = {}, afterImport = {}) } } }

        val bundle = File(context.cacheDir, "roadmap-bundle.zip").apply { delete() }
        answer(Intent.ACTION_CREATE_DOCUMENT, Uri.fromFile(bundle))
        compose.onNodeWithText(Device.string(R.string.bundle_export)).performClick()
        Device.waitUntil("the bundle is written", 20_000) {
            runCatching { bundle.inputStream().use(DataBundles::read) }.isSuccess
        }
        val exported = bundle.inputStream().use(DataBundles::read)
        assertTrue("device paths never leave", exported.globalConfig!!.lines().none { it.trim().startsWith("vulkan_lib_path") })

        // After the export: another frame limit and layout; this phone's driver path also changes.
        store.editLiveConfig { it.putSetting(fps, "30"); it.putString("Vulkan", "vulkan_lib_path", "/this/phone/b/libvulkan.so") }
        opacity(0.3f)

        val plan = runBlocking { DataBundleIo.preview(context, Uri.fromFile(bundle)) }.second
        assertEquals("only the emulator settings and the touch layout change", 2, plan.changes)
        val settings = plan.items.first() as ImportPlan.Item.Replaced
        assertTrue(ImportPlan.Item.Replaced(ImportPlan.Item.Part.TOUCH) in plan.items)

        answer(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(bundle))
        val importLabel = Device.string(R.string.bundle_import)
        compose.onNodeWithText(importLabel).performClick()
        val title = Device.string(R.string.bundle_confirm)
        waitFor(title)
        compose.onNodeWithText(Device.plural(R.plurals.bundle_plan_settings_replaced, settings.differ!!, settings.differ!!)).assertExists()
        compose.onNodeWithText(Device.string(R.string.bundle_plan_touch_replaced)).assertExists()
        compose.onNodeWithText(Device.string(R.string.bundle_plan_favorites_none)).assertExists()
        compose.onNodeWithText(Device.string(R.string.bundle_plan_kept)).assertExists()
        compose.onNodeWithText(Device.string(R.string.bundle_backup_note)).assertExists()
        compose.onNode(hasText(importLabel) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }

        assertEquals("60", live("GPU", "framerate_limit"))
        assertEquals(0.65f, runBlocking { layouts.config.first() }.globals.opacity, 0f)
        assertEquals("this phone's driver stays", "/this/phone/b/libvulkan.so", live("Vulkan", "vulkan_lib_path"))
        assertEquals(contentRoot, live("Storage", "content_root"))
        assertTrue("a backup of what was replaced",
            File(context.filesDir, "data-bundles").listFiles().orEmpty().any { it.name.startsWith("backup-") })
    }

    @Test fun aFileThatIsNotABundleIsRefusedWithAMessage() {
        val before = global.readBytes()
        compose.setContent { xendroidTheme { Column { DataBundleSection(beforeImport = {}, afterImport = {}) } } }
        val notes = File(context.cacheDir, "roadmap-notes.txt").apply { writeText("just some notes") }
        answer(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(notes))
        compose.onNodeWithText(Device.string(R.string.bundle_import)).performClick()
        waitFor(Device.string(R.string.bundle_why_no_manifest))
        compose.onAllNodesWithText(Device.string(R.string.bundle_confirm)).fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
        assertArrayEquals(before, global.readBytes())
    }
}
