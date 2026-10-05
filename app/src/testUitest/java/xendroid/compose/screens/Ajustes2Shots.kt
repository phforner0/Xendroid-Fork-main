package xendroid.compose.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.HudBar
import xendroid.compose.HudSample
import xendroid.compose.HudView
import xendroid.compose.PowerReading
import xendroid.compose.R
import xendroid.compose.core.HudDetail
import xendroid.compose.core.HudEdge
import xendroid.compose.core.HudLayout
import xendroid.compose.core.HudLook
import xendroid.compose.core.HudMetric
import xendroid.compose.core.HudStyle
import xendroid.compose.gamepad.ControlStyle
import xendroid.compose.gamepad.GamepadOverlay
import xendroid.compose.gamepad.defaultLayout
import xendroid.compose.shots.SampleArt
import xendroid.compose.shots.SampleLibrary
import xendroid.compose.shots.SampleMenu
import xendroid.compose.ui.ingame.InGameAction
import xendroid.compose.ui.ingame.InGameMenu
import xendroid.compose.ui.ingame.InGameMenuState
import xendroid.compose.ui.ingame.InGamePage
import xendroid.compose.shots.FakeCore
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.shots.screen
import xendroid.compose.shots.shot
import xendroid.compose.ui.about.AboutScreen
import xendroid.compose.ui.about.DeviceInfo
import xendroid.compose.ui.design.InputMode
import xendroid.compose.AppContainer
import xendroid.compose.driver.CustomDrivers
import xendroid.compose.patches.GamePatchesViewModel
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.GameSettingsViewModel
import xendroid.compose.settings.SettingLevel
import xendroid.compose.settings.SettingLevelStore
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.ui.compress.GameCompressViewModel
import xendroid.compose.ui.game.GameScreen
import xendroid.compose.ui.game.GameScreenLinks
import xendroid.compose.ui.game.GameSections
import xendroid.compose.ui.library.LibraryUiState
import xendroid.compose.ui.settings.SettingsLinks
import xendroid.compose.ui.settings.SettingsScreen

/**
 * Second round of device feedback, "depois": the in-game menu by category, the horizontal HUD,
 * the modern on-screen controls; Settings with the levels' counts, a search kept to its tab (with
 * the other tabs' matches), the Summary's search and "Back to…" after a link; About with a real
 * phone's core report (a Snapdragon with dozens of CPU features and hundreds of Vulkan
 * extensions) summed up, the full report on request.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class Ajustes2Shots {
    @get:Rule val compose = createComposeRule()

    private fun about(mode: InputMode, fullReport: Boolean = false) {
        FakeCore.deviceInfo = REPORT
        DeviceInfo.forgetCoreReport()
        compose.app(mode) { AboutScreen(onBack = {}) }
        Fixture.settle(20)
        compose.waitForIdle()
        if (fullReport) {
            compose.onAllNodesWithText(Fixture.context.getString(R.string.xd_ab_full_report)).onFirst().performClick()
            compose.waitForIdle()
        }
    }

    // ------------------------------------------------------------------ the game open

    private val halo = SampleLibrary.games.first { it.titleId == "4D5307E6" }

    /** A stand-in for the game's picture: its cover, large and soft. */
    @Composable
    private fun Scene(content: @Composable () -> Unit) {
        val png = SampleArt.coverPng(halo)
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size).asImageBitmap()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(bitmap, null, Modifier.fillMaxSize().blur(2.dp), contentScale = ContentScale.Crop)
            content()
        }
    }

    private fun menu(mode: InputMode, page: InGamePage, row: InGameAction? = null, chip: Int = 0) {
        val art = File(Fixture.context.cacheDir, "halo-cover.png").apply { writeBytes(SampleArt.coverPng(halo)) }
        val state = InGameMenuState(open = true, page = page, developer = false).let { s ->
            row?.let { s.select(s.actions().indexOf(it)).copy(chip = chip) } ?: s
        }
        compose.app(mode) {
            Scene {
                InGameMenu(state = state, model = SampleMenu.model(art),
                    onPage = {}, onSelect = {}, onAction = {}, onAdjust = { _, _ -> }, onChoose = { _, _ -> }, onSet = { _, _ -> },
                    onLogChoice = {}, onQuitChoice = {})
            }
        }
        compose.waitForIdle()
    }

    private val sample = HudSample(
        fps = 29.8, frameMs = 33.6, cpu = 47f, gpu = 83, ramUsed = 7_400_000_000L, ramTotal = 11_500_000_000L,
        batteryCelsius = 41.2f, socCelsius = 72f, power = PowerReading(watts = 6.4, percent = 72, pluggedIn = false, minutesLeft = 128, minutesToFull = null),
        gpuMemory = 1_420_000_000L,
    )
    private val graph = List(60) { i -> 30f + 6f * kotlin.math.sin(i / 4f) - if (i in 38..41) 14f else 0f }
    private val metrics = setOf(HudMetric.CPU, HudMetric.GPU, HudMetric.RAM, HudMetric.BATTERY_TEMPERATURE, HudMetric.SOC_TEMPERATURE, HudMetric.POWER)

    private fun hudBars() {
        compose.app(InputMode.TOUCH) {
            Scene {
                Box(Modifier.fillMaxSize()) {
                    HudBar(sample, HudDetail.FULL, metrics, HudLook.BOX, HudStyle(layout = HudLayout.HORIZONTAL), graph = graph,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter))
                    HudView(sample, HudDetail.FULL, metrics, HudLook.BOX, style = HudStyle(colors = 1f), graph = graph,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterStart))
                    HudView(sample, HudDetail.COMPACT, metrics, HudLook.OUTLINE, style = HudStyle(colors = 0f), graph = graph,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd))
                    HudBar(sample, HudDetail.FULL, metrics, HudLook.OUTLINE, HudStyle(layout = HudLayout.HORIZONTAL, edge = HudEdge.BOTTOM, colors = 0.5f),
                        graph = graph, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun controls(style: ControlStyle, landscape: Boolean = true) {
        compose.app(InputMode.TOUCH) {
            Scene { GamepadOverlay(controls = defaultLayout(landscape), opacity = 0.75f, onKeyEvent = { _, _, _ -> }, style = style) }
        }
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun menuImage() { menu(InputMode.TOUCH, InGamePage.GRAPHICS); compose.shot("ajustes-2/depois-menu-imagem") }
    @Config(qualifiers = Phone.LAND) @Test fun menuPerformance() { menu(InputMode.TOUCH, InGamePage.SYSTEM); compose.shot("ajustes-2/depois-menu-desempenho") }
    @Config(qualifiers = Phone.LAND) @Test fun menuHud() { menu(InputMode.TOUCH, InGamePage.HUD); compose.shot("ajustes-2/depois-menu-hud") }
    @Config(qualifiers = Phone.LAND) @Test fun menuControls() { menu(InputMode.TOUCH, InGamePage.CONTROLS); compose.shot("ajustes-2/depois-menu-controles") }
    @Config(qualifiers = Phone.LAND) @Test fun menuSession() { menu(InputMode.TOUCH, InGamePage.SESSION); compose.shot("ajustes-2/depois-menu-sessao") }
    @Config(qualifiers = Phone.PORT) @Test fun menuHudPort() { menu(InputMode.TOUCH, InGamePage.HUD); compose.shot("ajustes-2/depois-menu-hud-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun menuHudController() {
        menu(InputMode.CONTROLLER, InGamePage.HUD, InGameAction.HUD_METRICS, chip = 3); compose.shot("ajustes-2/depois-menu-hud-controle")
    }
    @Config(qualifiers = Phone.LAND) @Test fun hud() { hudBars(); compose.shot("ajustes-2/depois-hud-horizontal") }
    @Config(qualifiers = Phone.LAND) @Test fun controlsModern() { controls(ControlStyle.MODERN); compose.shot("ajustes-2/depois-controles-moderno") }
    @Config(qualifiers = Phone.LAND) @Test fun controlsClassic() { controls(ControlStyle.CLASSIC); compose.shot("ajustes-2/depois-controles-classico") }
    @Config(qualifiers = Phone.PORT) @Test fun controlsModernPort() { controls(ControlStyle.MODERN, landscape = false); compose.shot("ajustes-2/depois-controles-moderno-retrato") }

    // ------------------------------------------------------------------ Settings

    /** The global config away from the defaults in places, one game with its own values, the
     *  Essential level; [query] typed in the first search box. */
    private fun settings(mode: InputMode, section: String? = null, query: String? = null) {
        CustomDrivers.forced = true
        val container = AppContainer(Fixture.context)
        val store = ConfigStore(Fixture.context)
        store.editGameConfig("4D5307E6") { h -> h.putInt("GPU", "framerate_limit", 30) }
        store.editLiveConfig { h -> h.putInt("GPU", "anisotropic_override", 4) }
        SettingLevelStore.write(Fixture.context, SettingLevel.ESSENTIAL)
        val library = Fixture.library(container)
        val names = (library.state.value as LibraryUiState.Loaded).games.associate { it.titleId!!.uppercase() to it.name }
        val vm = container.settingsViewModelFactory().create(SettingsViewModel::class.java)
        Fixture.settle(30)
        compose.app(mode) {
            SettingsScreen(vm, onBack = {}, links = SettingsLinks(onDrivers = {}, onGameSettings = {}, gameName = { names[it] }),
                initialSection = section)
        }
        Fixture.settle(20)
        compose.waitForIdle()
        if (query != null) {
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(query)
            compose.waitForIdle()
        }
    }

    /** Taps [text]; the [last] one, when the section list has the same words as a link. */
    private fun click(text: String, last: Boolean = false) {
        val node = compose.onAllNodesWithText(text, substring = true).let { if (last) it.onLast() else it.onFirst() }
        runCatching { node.performScrollTo() }                      // a link below the fold
        node.performClick()
        compose.waitForIdle()
        Fixture.settle(10)
        compose.waitForIdle()
    }

    private fun sheet(mode: InputMode, section: String) {
        val titleId = "4D5307E6"
        val container = AppContainer(Fixture.context)
        ConfigStore(Fixture.context).editGameConfig(titleId) { h ->
            h.putInt("GPU", "framerate_limit", 30)
            h.putString("Display", "postprocess_scaling_and_sharpening", "fsr")
        }
        SettingLevelStore.write(Fixture.context, SettingLevel.ESSENTIAL)
        val vm = Fixture.library(container)
        val game = Fixture.game(vm, titleId)
        val settings = container.gameSettingsViewModelFactory(titleId).create(GameSettingsViewModel::class.java)
        val global = container.settingsViewModelFactory().create(SettingsViewModel::class.java)
        val patches = container.gamePatchesViewModelFactory(titleId).create(GamePatchesViewModel::class.java)
        val compress = container.gameCompressViewModelFactory().create(GameCompressViewModel::class.java)
        vm.loadDetails(game)
        Fixture.settle()
        compose.app(mode) {
            GameScreen(game, vm, compress, settings, global, patches, GameScreenLinks(onBack = {}), initialSection = section)
        }
        Fixture.settle(30)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun settingsTabSearch() {
        settings(InputMode.TOUCH, "set:IMAGE", query = "gpu"); compose.shot("ajustes-2/depois-configuracoes-busca-na-aba")
    }
    @Config(qualifiers = Phone.LAND) @Test fun settingsTabSearchOtherTabs() {
        settings(InputMode.TOUCH, "set:IMAGE", query = "gpu")
        compose.onNodeWithText(Fixture.context.getString(R.string.xd_set_other_tabs), substring = true, ignoreCase = true).performScrollTo()
        compose.waitForIdle()
        compose.shot("ajustes-2/depois-configuracoes-busca-outras-abas")
    }
    @Config(qualifiers = Phone.PORT) @Test fun settingsTabSearchPort() {
        settings(InputMode.TOUCH, "set:PERFORMANCE", query = "gpu"); compose.shot("ajustes-2/depois-configuracoes-busca-na-aba-retrato")
    }
    @Config(qualifiers = Phone.LAND) @Test fun settingsSummarySearch() {
        settings(InputMode.TOUCH, query = "fsr"); compose.shot("ajustes-2/depois-configuracoes-busca-no-resumo")
    }
    @Config(qualifiers = Phone.LAND) @Test fun settingsBackAfterALink() {
        settings(InputMode.TOUCH, query = "fsr")
        click(Fixture.context.getString(R.string.xd_set_open_tab))
        compose.shot("ajustes-2/depois-configuracoes-voltar")
    }
    @Config(qualifiers = Phone.LAND) @Test fun settingsLevels() { settings(InputMode.TOUCH, "app:ui"); compose.shot("ajustes-2/depois-configuracoes-niveis") }
    @Config(qualifiers = Phone.LAND) @Test fun settingsTabSearchController() {
        settings(InputMode.CONTROLLER, "set:IMAGE", query = "gpu"); compose.shot("ajustes-2/depois-configuracoes-busca-controle")
    }
    @Config(qualifiers = Phone.LAND) @Test fun sheetAllSettings() {
        sheet(InputMode.TOUCH, GameSections.OVERVIEW)
        click(Fixture.context.getString(R.string.xd_lib_all_settings), last = true)
        compose.shot("ajustes-2/depois-ficha-todos-os-ajustes")
    }

    @Config(qualifiers = Phone.LAND) @Test fun aboutLand() { about(InputMode.TOUCH); compose.shot("ajustes-2/depois-sobre") }
    @Config(qualifiers = Phone.PORT) @Test fun aboutPort() { about(InputMode.TOUCH); compose.shot("ajustes-2/depois-sobre-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun aboutReport() { about(InputMode.TOUCH, fullReport = true); compose.screen("ajustes-2/depois-sobre-relato-completo") }

    private companion object {
        val FEATURES = listOf("fp", "asimd", "evtstrm", "aes", "pmull", "sha1", "sha2", "crc32", "atomics", "fphp", "asimdhp", "cpuid",
            "asimdrdm", "jscvt", "fcma", "lrcpc", "dcpop", "sha3", "sm3", "sm4", "asimddp", "sha512", "sve", "asimdfhm", "dit", "uscat",
            "ilrcpc", "flagm", "ssbs", "sb", "paca", "pacg", "dcpodp", "sve2", "sveaes", "svepmull", "svebitperm", "svesha3", "svesm4",
            "flagm2", "frint", "svei8mm", "svebf16", "i8mm", "bf16", "dgh", "bti", "ecv", "afp", "wfxt", "mte", "mte3")
        val EXTENSIONS = (1..212).map { i ->
            listOf("VK_KHR_", "VK_EXT_", "VK_QCOM_", "VK_ANDROID_")[i % 4] + listOf("swapchain", "robustness2", "descriptor_indexing",
                "shader_float16_int8", "fragment_density_map", "external_memory", "timeline_semaphore", "custom_border_color")[i % 8] + "_$i"
        }
        val REPORT = "CPU [cortex-x4*1+cortex-a720*3+cortex-a720*2+cortex-a520*2(armv9.2-a)]:\n" +
            FEATURES.joinToString("") { "    * $it\n" } + "\nGPU [Adreno (TM) 825(Vulkan: 1.3.284)]:\n" +
            EXTENSIONS.joinToString("") { "    * $it\n" }
    }
}
