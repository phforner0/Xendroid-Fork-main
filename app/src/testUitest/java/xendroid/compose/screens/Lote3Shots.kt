package xendroid.compose.screens

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.AppContainer
import xendroid.compose.companion.CompanionPadLink.State
import xendroid.compose.companion.CompanionPadLink.Why
import xendroid.compose.companion.CompanionProtocol
import xendroid.compose.data.KeymapStore
import xendroid.compose.gamepad.ControlId
import xendroid.compose.gamepad.ControlOptionsStore
import xendroid.compose.gamepad.ControllerTestModel
import xendroid.compose.gamepad.GamepadController
import xendroid.compose.gamepad.GamepadEditorScreen
import xendroid.compose.gamepad.GyroAim
import xendroid.compose.gamepad.LayoutPresetDto
import xendroid.compose.gamepad.LayoutPresets
import xendroid.compose.gamepad.PadAxes
import xendroid.compose.gamepad.RumbleIntensity
import xendroid.compose.gamepad.RumbleSettings
import xendroid.compose.gamepad.TestedDevice
import xendroid.compose.gamepad.defaultLayout
import xendroid.compose.gamepad.layoutFor
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.shots.screen
import xendroid.compose.shots.shot
import xendroid.compose.ui.companion.PhoneControllerForm
import xendroid.compose.ui.companion.PhonePadView
import xendroid.compose.ui.controls.ControlsLinks
import xendroid.compose.ui.controls.ControlsScreen
import xendroid.compose.ui.controls.ControlsSections
import xendroid.compose.ui.controls.InputDevicesNow
import xendroid.compose.ui.controls.KeyboardDevice
import xendroid.compose.ui.controls.LocalInputDevicesPreview
import xendroid.compose.ui.controllertest.ControllerTestScreen
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.keymap.KeymapScreen
import xendroid.compose.ui.keymap.KeymapViewModel

/** Batch 3 "depois": the Controls area and its tools (key mapping, touch editor, controller test,
 *  this phone as a controller), with two controllers and a keyboard connected. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class Lote3Shots {
    @get:Rule val compose = createComposeRule()

    private val xbox = TestedDevice(7, "Xbox Wireless Controller", "desc-xbox", 0x045E, 0x0B13, listOf("gamepad", "joystick"),
        canVibrate = true, hasGyro = false)
    private val dualSense = TestedDevice(9, "DualSense Wireless Controller", "desc-ds", 0x054C, 0x0CE6, listOf("gamepad", "joystick"),
        canVibrate = true, hasGyro = true)
    private val devices = InputDevicesNow(pads = listOf(xbox, dualSense), keyboards = listOf(KeyboardDevice(12, "Logitech K380")))

    /** Back sends Guide, RT the key RB has (so one of them does nothing); two saved layouts; the
     *  touch camera on, gyro aim while LT; the DualSense vibrates low. */
    private fun world(): Pair<SettingsViewModel, KeymapViewModel> {
        val context = Fixture.context
        val container = AppContainer(context)
        Fixture.io { KeymapStore(context).setBindings(mapOf(8 to KeyEvent.KEYCODE_BUTTON_MODE, 15 to KeyEvent.KEYCODE_BUTTON_R1)) }
        val now = System.currentTimeMillis()
        Fixture.io {
            GamepadController(context).update { cfg ->
                var next = LayoutPresets.save(cfg, LayoutPresets.capture(cfg, null, "Polegares grandes", now - 3 * 86_400_000L))
                next = LayoutPresets.save(next, LayoutPresetDto("Corrida", landscape = next.layoutFor(null, true), savedAt = now - 20 * 86_400_000L))
                next
            }
        }
        Fixture.io { ControlOptionsStore(context).update { it.copy(touchCamera = true, gyroAim = GyroAim.WHILE_LT) } }
        context.getSharedPreferences(RumbleSettings.DEVICES_PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            putString(RumbleSettings.DEVICES_KEY, RumbleSettings().withDevice("desc-ds", RumbleIntensity.LOW).encodeDevices())
        }
        val vm = container.settingsViewModelFactory().create(SettingsViewModel::class.java)
        val keymap = container.keymapViewModelFactory().create(KeymapViewModel::class.java)
        Fixture.settle(30)
        return vm to keymap
    }

    private fun controls(mode: InputMode, section: String? = null) {
        val (vm, keymap) = world()
        compose.app(mode) {
            CompositionLocalProvider(LocalInputDevicesPreview provides devices) {
                ControlsScreen(vm, keymap, onBack = {}, links = ControlsLinks(), initialSection = section)
            }
        }
        Fixture.settle(40)
        compose.waitForIdle()
    }

    private fun keymap(mode: InputMode) {
        val (_, keymap) = world()
        compose.app(mode) { KeymapScreen(keymap, onBack = {}) }
        Fixture.settle(40)
        compose.waitForIdle()
    }

    private fun editor(mode: InputMode, pick: ControlId? = ControlId.A) {
        world()
        compose.app(mode) { GamepadEditorScreen(GamepadController(Fixture.context), onDone = {}) }
        Fixture.settle(40)
        compose.waitForIdle()
        if (pick != null) {
            val control = defaultLayout(true).first { it.id == pick }
            compose.onRoot().performTouchInput { click(Offset(width * control.xFraction, height * control.yFraction)) }
            Fixture.settle(10)
            compose.waitForIdle()
        }
    }

    private fun test(mode: InputMode) {
        val (vm, _) = world()
        val model = ControllerTestModel().apply {
            connected(xbox)
            connected(dualSense)
            listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_L1,
                KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT)
                .forEach { key(7, it, true); key(7, it, false) }
            key(7, KeyEvent.KEYCODE_BUTTON_Y, true)
            motion(7, PadAxes(lx = 0.64f, ly = -0.38f, rx = 0.05f, ry = 0.03f, lt = 0.82f, rt = 0.12f))
            key(9, KeyEvent.KEYCODE_BUTTON_A, true); key(9, KeyEvent.KEYCODE_BUTTON_A, false)
            motion(9, PadAxes(lx = 0.04f, ly = 0.02f, rx = -0.7f, ry = 0.45f))
        }
        compose.app(mode) { ControllerTestScreen(onBack = {}, global = vm, model = model, listen = false) }
        Fixture.settle(40)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun controlsLand() { controls(InputMode.TOUCH); compose.shot("lote3/depois-controles") }
    @Config(qualifiers = Phone.PORT) @Test fun controlsPort() { controls(InputMode.TOUCH); compose.shot("lote3/depois-controles-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun controlsController() { controls(InputMode.CONTROLLER); compose.shot("lote3/depois-controles-controle") }
    @Config(qualifiers = Phone.LAND) @Test fun controlsTouch() { controls(InputMode.TOUCH, ControlsSections.TOUCH); compose.shot("lote3/depois-controles-toque") }
    @Config(qualifiers = Phone.LAND) @Test fun controlsPads() { controls(InputMode.TOUCH, ControlsSections.PADS); compose.shot("lote3/depois-controles-fisicos") }
    @Config(qualifiers = Phone.LAND) @Test fun controlsMotion() { controls(InputMode.TOUCH, ControlsSections.MOTION); compose.shot("lote3/depois-controles-movimento") }
    @Config(qualifiers = Phone.LAND) @Test fun controlsPhones() { controls(InputMode.TOUCH, ControlsSections.PHONES); compose.shot("lote3/depois-controles-telefones") }

    @Config(qualifiers = Phone.LAND) @Test fun keymapLand() { keymap(InputMode.TOUCH); compose.shot("lote3/depois-mapeamento") }
    @Config(qualifiers = Phone.PORT) @Test fun keymapPort() { keymap(InputMode.TOUCH); compose.shot("lote3/depois-mapeamento-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun keymapController() { keymap(InputMode.CONTROLLER); compose.shot("lote3/depois-mapeamento-controle") }
    @Config(qualifiers = Phone.LAND) @Test fun keymapCapture() {
        keymap(InputMode.TOUCH)
        // The drawn RT (the list's row is below the fold).
        compose.onAllNodesWithContentDescription("Gatilho direito (RT)", substring = true).onFirst().performClick()
        Fixture.settle(10)
        compose.screen("lote3/depois-mapeamento-captura")
    }

    @Config(qualifiers = Phone.LAND) @Test fun editorLand() { editor(InputMode.TOUCH); compose.shot("lote3/depois-editor-de-toque") }
    @Config(qualifiers = Phone.LAND) @Test fun editorGlobals() {
        editor(InputMode.TOUCH, pick = null)
        compose.onAllNodesWithText("Gerais").onFirst().performClick()
        Fixture.settle(10)
        compose.screen("lote3/depois-editor-de-toque-gerais")
    }
    @Config(qualifiers = Phone.LAND) @Test fun editorController() { editor(InputMode.CONTROLLER, ControlId.LEFT_STICK); compose.shot("lote3/depois-editor-de-toque-controle") }

    @Config(qualifiers = Phone.LAND) @Test fun testLand() { test(InputMode.TOUCH); compose.shot("lote3/depois-testar-controles") }
    @Config(qualifiers = Phone.PORT) @Test fun testPort() { test(InputMode.TOUCH); compose.shot("lote3/depois-testar-controles-retrato") }

    @Config(qualifiers = Phone.LAND) @Test fun phoneForm() {
        compose.app(InputMode.TOUCH) {
            PhoneControllerForm(State.Idle(Why(Why.Kind.REJECTED, code = CompanionProtocol.REJECT_PROOF)),
                "192.168.1.20:41234", {}, "482931", {}, "Pixel 7a", {}, RumbleIntensity.MEDIUM, {}, {}, {}, {})
        }
        compose.shot("lote3/depois-celular-como-controle")
    }
    @Config(qualifiers = Phone.LAND) @Test fun phonePlaying() {
        compose.app(InputMode.TOUCH) { PhonePadView(State.Playing(slot = 1, latencyMs = 18), defaultLayout(true), 0.65f, { _, _, _ -> }, {}) }
        compose.shot("lote3/depois-celular-jogando")
    }
}
