package xendroid.compose.roadmap

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.gamepad.GamepadConfigDto
import xendroid.compose.gamepad.LayoutPresetDto
import xendroid.compose.gamepad.LayoutPresets
import xendroid.compose.gamepad.LayoutPresetsDialog
import xendroid.compose.gamepad.OrientationLayoutDto
import xendroid.compose.gamepad.defaultLayout
import xendroid.compose.gamepad.layoutFor
import xendroid.compose.gamepad.toDto
import xendroid.compose.gamepad.withLayout
import xendroid.compose.ui.theme.xendroidTheme

/**
 * Roadmap item 38 (U06): the editor's Layouts dialog. Two controls moved, saved as "Corrida";
 * after a reset, "Apply…" previews "Controls: 2 moved" and applying brings them back. Export
 * writes the file (the system picker answered with a test file); after deleting the layout,
 * importing that file brings it back. A file of a newer version ("version": 2) and one with a
 * control off the screen ("x": 1.5) are refused saying why. With "This game only", saving and
 * applying touch only that game's layout.
 *
 * Left for the phone: dragging controls in the editor and the game using the layout (group C).
 */
@RunWith(AndroidJUnit4::class)
class Item38TouchLayoutsTest {
    @get:Rule val compose = createComposeRule()

    private val context = Device.context
    private var created: Uri? = null
    private var opened: Uri? = null

    @Before fun setUp() {
        Device.requireTestPackage()
        Intents.init()
        // The system picker, answered with whatever file the test points it at.
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWithFunction { Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(created)) }
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWithFunction { Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(opened)) }
    }

    @After fun tearDown() = Intents.release()

    /** The default landscape layout with its first two controls moved. */
    private fun moved(): OrientationLayoutDto =
        defaultLayout(true).mapIndexed { i, c -> if (i < 2) c.withLayout(x = (c.xFraction + 0.1f).coerceAtMost(0.95f)) else c }.toDto()

    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun save(name: String) {
        compose.onNode(hasSetTextAction()).performTextInput(name)
        compose.onNodeWithText(Device.string(R.string.common_save)).performClick()
        waitFor(Device.string(R.string.lp_saved, name))
    }

    private fun apply(name: String, preview: String) {
        compose.onNodeWithText(Device.string(R.string.lp_apply_ellipsis)).performClick()
        waitFor(Device.string(R.string.lp_apply_title, name))
        compose.onNodeWithText(preview, substring = true).assertExists()
        compose.onNodeWithText(Device.string(R.string.lp_apply)).performClick()
        waitFor(Device.string(R.string.lp_applied, name))
    }

    private val twoMoved get() = Device.string(R.string.lp_diff_landscape,
        Device.string(R.string.lp_diff_controls, Device.plural(R.plurals.lp_diff_moved, 2, 2)))

    @Test fun saveApplyExportImportAndRefusals() {
        var config by mutableStateOf(GamepadConfigDto(version = 2, landscape = moved()))
        // The dialog reports by onMessage (the editor shows it as a toast); shown here to be waited for.
        var message by mutableStateOf<String?>(null)
        compose.setContent {
            xendroidTheme {
                message?.let { Text(it) }
                LayoutPresetsDialog(config, editScope = null, landscape = true, onChange = { config = it }, onMessage = { message = it }, onDismiss = {})
            }
        }
        compose.onNodeWithText(Device.string(R.string.lp_scope_shared)).assertExists()
        save("Corrida")
        assertEquals(listOf("Corrida"), config.presets.map { it.name })

        config = config.copy(landscape = defaultLayout(true).toDto())       // the editor's Reset
        apply("Corrida", twoMoved)
        assertEquals(moved(), config.landscape)

        val file = File(context.cacheDir, "roadmap-corrida.xdlayout.json").apply { delete() }
        created = Uri.fromFile(file)
        compose.onNodeWithText(Device.string(R.string.lp_export)).performClick()
        waitFor(Device.string(R.string.lp_exported, "Corrida"))
        val exported = (LayoutPresets.decodeFile(file.readText()) as LayoutPresets.Decoded.Ok).preset
        assertEquals(moved(), exported.landscape)

        compose.onNodeWithText(Device.string(R.string.common_delete)).performClick()
        waitFor(Device.string(R.string.lp_deleted, "Corrida"))
        assertTrue(config.presets.isEmpty())
        opened = Uri.fromFile(file)
        compose.onNodeWithText(Device.string(R.string.lp_import)).performClick()
        waitFor(Device.string(R.string.lp_imported, "Corrida"))
        assertEquals(moved(), config.presets.single().landscape)

        val newer = File(context.cacheDir, "roadmap-newer.json").apply { writeText(file.readText().replace("\"version\":1", "\"version\":2")) }
        opened = Uri.fromFile(newer)
        compose.onNodeWithText(Device.string(R.string.lp_import)).performClick()
        waitFor(Device.string(R.string.lp_not_imported, Device.string(R.string.lp_refuse_newer, "2")))

        val offScreen = exported.copy(landscape = exported.landscape!!.copy(
            controls = exported.landscape!!.controls.mapIndexed { i, c -> if (i == 0) c.copy(x = 1.5f) else c }))
        val off = File(context.cacheDir, "roadmap-off.json").apply { writeText(LayoutPresets.encodeFile(offScreen)) }
        opened = Uri.fromFile(off)
        compose.onNodeWithText(Device.string(R.string.lp_import)).performClick()
        waitFor(Device.string(R.string.lp_not_imported,
            Device.string(R.string.lp_refuse_off_screen, offScreen.landscape!!.controls.first().id)))
        assertEquals("refused files add nothing", 1, config.presets.size)
    }

    @Test fun thisGameOnlyTouchesOnlyThatGamesLayout() {
        val game = "4D5309C9"
        var config by mutableStateOf(GamepadConfigDto(version = 2).withLayout(game, true, moved()))
        var message by mutableStateOf<String?>(null)
        compose.setContent {
            xendroidTheme {
                message?.let { Text(it) }
                LayoutPresetsDialog(config, editScope = game, landscape = true, onChange = { config = it }, onMessage = { message = it }, onDismiss = {})
            }
        }
        compose.onNodeWithText(Device.string(R.string.lp_scope_game)).assertExists()
        save("Corrida")
        val saved: LayoutPresetDto = config.presets.single()
        assertEquals(moved(), saved.landscape)

        config = config.withLayout(game, true, defaultLayout(true).toDto())
        apply("Corrida", twoMoved)
        assertEquals(moved(), config.layoutFor(game, true))
        assertEquals("the shared layout stays", OrientationLayoutDto(), config.landscape)
    }
}
