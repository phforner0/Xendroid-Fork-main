package xendroid.compose.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.AppContainer
import xendroid.compose.driver.CustomDrivers
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.shots.shot
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.drivers.DriversScreen
import xendroid.compose.ui.settings.SettingsLinks
import xendroid.compose.ui.settings.SettingsScreen

/** Batch 1 "depois": Settings (summary, a group with games that use another value, the app's
 *  options) and the Drivers area, through the app's own view models. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class Lote1Shots {
    @get:Rule val compose = createComposeRule()

    /** Two games with values of their own, the global config away from the defaults in places,
     *  two installed driver packages and one older import. */
    private fun world(): Pair<SettingsViewModel, (String) -> String?> {
        CustomDrivers.forced = true
        val container = AppContainer(Fixture.context)
        val store = ConfigStore(Fixture.context)
        store.editGameConfig("4D5307E6") { h ->
            h.putInt("GPU", "framerate_limit", 30)
            h.putString("Display", "postprocess_scaling_and_sharpening", "fsr")
        }
        store.editGameConfig("4D5309C9") { h -> h.putString("Display", "postprocess_scaling_and_sharpening", "cas") }
        val root = xendroid.compose.Application.get_custom_driver_dir()
        fun pkg(dir: String, name: String, version: String, marker: Boolean): File {
            val d = File(root, dir).apply { mkdirs() }
            val lib = File(d, "libvulkan_freedreno.so").apply { writeBytes(ByteArray(2_400_000)) }
            File(d, "meta.json").writeText("""{"name":"$name","driverVersion":"$version","libraryName":"libvulkan_freedreno.so"}""")
            if (marker) File(d, "xendroid-installation.json").writeText(
                """{"version":1,"archiveSha256":"$dir","library":"libvulkan_freedreno.so","files":[]}""")
            return lib
        }
        val turnip = pkg("a".repeat(64), "Turnip", "v25.3.0 r2", marker = true)
        pkg("b".repeat(64), "Turnip", "v26.0.0 dev", marker = true)
        pkg("turnip-24.1", "Turnip", "v24.1.0", marker = false)
        store.editLiveConfig { h ->
            h.putString("Vulkan", "vulkan_lib_path", turnip.absolutePath)
            h.putInt("GPU", "anisotropic_override", 4)
            h.putString("Vulkan", "turnip_debug", "sysmem,nolrz")
        }
        store.editGameConfig("5454082B") { h -> h.putString("Vulkan", "vulkan_lib_path", "") }
        val library = Fixture.library(container)
        val names = (library.state.value as xendroid.compose.ui.library.LibraryUiState.Loaded).games
            .associate { it.titleId!!.uppercase() to it.name }
        val vm = container.settingsViewModelFactory().create(SettingsViewModel::class.java)
        Fixture.settle(30)
        return vm to { title: String -> names[title] }
    }

    private fun settings(mode: InputMode, section: String? = null) {
        val (vm, name) = world()
        compose.app(mode) {
            SettingsScreen(vm, onBack = {}, links = SettingsLinks(onDrivers = {}, onGameSettings = {}, gameName = name), initialSection = section)
        }
        Fixture.settle(20)
        compose.waitForIdle()
    }

    private fun drivers(mode: InputMode) {
        val (vm, name) = world()
        compose.app(mode) { DriversScreen(vm, onBack = {}, onGameSettings = {}, gameName = name) }
        Fixture.settle(20)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun summary() { settings(InputMode.TOUCH); compose.shot("lote1/depois-configuracoes") }
    @Config(qualifiers = Phone.PORT) @Test fun summaryPort() { settings(InputMode.TOUCH); compose.shot("lote1/depois-configuracoes-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun group() { settings(InputMode.TOUCH, "set:IMAGE"); compose.shot("lote1/depois-configuracoes-imagem") }
    @Config(qualifiers = Phone.LAND) @Test fun app() { settings(InputMode.TOUCH, "app:ui"); compose.shot("lote1/depois-configuracoes-interface") }
    @Config(qualifiers = Phone.LAND) @Test fun controller() { settings(InputMode.CONTROLLER, "set:IMAGE"); compose.shot("lote1/depois-configuracoes-controle") }
    @Config(qualifiers = Phone.LAND) @Test fun driversNow() { drivers(InputMode.TOUCH); compose.shot("lote1/depois-drivers") }
    @Config(qualifiers = Phone.PORT) @Test fun driversPort() { drivers(InputMode.TOUCH); compose.shot("lote1/depois-drivers-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun driversController() { drivers(InputMode.CONTROLLER); compose.shot("lote1/depois-drivers-controle") }
}
