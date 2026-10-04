package xendroid.compose.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.AppContainer
import xendroid.compose.HudSample
import xendroid.compose.HudView
import xendroid.compose.PowerReading
import xendroid.compose.core.HudDetail
import xendroid.compose.core.HudLook
import xendroid.compose.core.HudMetric
import xendroid.compose.core.PerformancePanel
import xendroid.compose.core.PresentationState
import xendroid.compose.core.ThermalWatch
import xendroid.compose.driver.CustomDrivers
import xendroid.compose.driver.DriverRepository
import xendroid.compose.settings.FpsConfigSnapshot
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.SampleArt
import xendroid.compose.shots.SampleLibrary
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.shots.shot
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.drivers.DriversScreen
import xendroid.compose.ui.ingame.InGameAction
import xendroid.compose.ui.ingame.InGameMenu
import xendroid.compose.ui.ingame.InGameMenuState
import xendroid.compose.ui.ingame.InGamePage
import xendroid.compose.ui.ingame.MenuStat
import xendroid.compose.ui.ingame.performancePanelSections

/**
 * First round of device feedback, "depois": the performance HUD in the new look (FPS only, the
 * chosen metrics, the panel; a box and an outline over the game), the in-game menu's Image
 * options tried live, and the drivers to download in portrait (long names wrap under their badges).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class Ajustes1Shots {
    @get:Rule val compose = createComposeRule()

    private val halo = SampleLibrary.games.first { it.titleId == "4D5307E6" }

    @After fun forgetFetch() { DriverRepository.fetchForTests = null }

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

    private val sample = HudSample(
        fps = 29.8, frameMs = 33.6, submissionsPerSecond = 61.0, cpu = 47f, gpu = 83, ramUsed = 7_400_000_000L,
        ramTotal = 11_500_000_000L, batteryCelsius = 41.2f, socCelsius = 72f,
        power = PowerReading(watts = 6.4, percent = 72, pluggedIn = false, minutesLeft = 128, minutesToFull = null),
        gpuMemory = 1_420_000_000L,
    )

    private val panel = PerformancePanel.Snapshot(
        recentSeconds = 10, recent = PerformancePanel.Pacing(34, 81, 298), run = PerformancePanel.Pacing(34, 52, 18_220),
        fpsMedian = 30, fpsLow = 24, pipelines = 287, pipelineMs = 6_400, audioConcealed = 3, audioBlocks = 12_400,
        thermal = ThermalWatch.Level.NEAR_LIMIT, headroom = 0.82f,
        settings = listOf(PerformancePanel.Setting.DRIVER to "Turnip v25.3.0 r2", PerformancePanel.Setting.SCALING to "FSR",
            PerformancePanel.Setting.FPS_LIMIT to "30"),
        changed = listOf("Resolução: 1280 × 720", "Limite de FPS: 30"), changedTotal = 3,
    )

    private fun hud() {
        compose.app(InputMode.TOUCH) {
            Scene {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HudView(sample, HudDetail.COMPACT, HudMetric.entries.toSet(), HudLook.BOX)
                        HudView(sample, HudDetail.FULL, HudMetric.entries.toSet(), HudLook.BOX)
                        HudView(sample, HudDetail.COMPACT, HudMetric.entries.toSet(), HudLook.OUTLINE)
                    }
                    HudView(sample, HudDetail.FULL, setOf(HudMetric.CPU, HudMetric.GPU, HudMetric.SOC_TEMPERATURE), HudLook.OUTLINE)
                    HudView(sample, HudDetail.PANEL, HudMetric.entries.toSet(), HudLook.BOX, panelSections = performancePanelSections(panel))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun menu(mode: InputMode, selected: Int) {
        val art = File(Fixture.context.cacheDir, "halo-cover.png").apply { writeBytes(SampleArt.coverPng(halo)) }
        compose.app(mode) {
            Scene {
                InGameMenu(
                    state = InGameMenuState(open = true, page = InGamePage.GRAPHICS, developer = false, selections = listOf(selected, 0, 0, 0)),
                    paused = true, fpsLimit = 30,
                    fpsConfig = FpsConfigSnapshot(titleId = "4D5307E6", globalLimit = 60, gameLimit = 30),
                    presentation = PresentationState(displayMode = 0), fgPreset = 1, lsfgAvailable = false,
                    extensionLabels = mapOf(
                        InGameAction.SCALING_EFFECT to "Efeito de escala: FSR",
                        InGameAction.ANTIALIASING to "Suavização: FXAA",
                        InGameAction.SHARPNESS to "Nitidez: alta",
                        InGameAction.DITHER to "Pontilhado: das configurações",
                    ),
                    performanceHud = true, compactHud = true, hudMetrics = HudMetric.entries.toSet(),
                    touchControls = true, adaptiveSticks = false, stretch = false, volume = 80,
                    sessionInfo = "v412 · 7bb3409\nAdreno (TM) 825", phoneControllers = null,
                    logSessions = emptyList(), onLogChoice = {}, onPage = {}, onSelect = {}, onAction = {}, onQuitChoice = {},
                    gameName = "Halo 3", art = art,
                    status = listOf(MenuStat("30", "FPS"), MenuStat("34", "ms", "p99"), MenuStat("41", "°C"), MenuStat("72%", label = "bateria")),
                )
            }
        }
        compose.waitForIdle()
    }

    /** The drivers to download, from a stand-in for GitHub's releases API: long package names,
     *  one with its checksum, one from a second source. */
    private fun available(mode: InputMode) {
        CustomDrivers.forced = true
        DriverRepository.fetchForTests = { url ->
            when {
                "K11MCH1" in url -> RELEASES_MAIN
                else -> RELEASES_OTHER
            }
        }
        val container = AppContainer(Fixture.context)
        val vm = container.settingsViewModelFactory().create(SettingsViewModel::class.java)
        Fixture.settle(20)
        compose.app(mode) { DriversScreen(vm, onBack = {}, onGameSettings = {}, gameName = { null }, initialSection = "drv:avail") }
        Fixture.settle(40)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun hudLooks() { hud(); compose.shot("ajustes-1/depois-hud") }
    @Config(qualifiers = Phone.LAND) @Test fun menuImage() { menu(InputMode.TOUCH, 5); compose.shot("ajustes-1/depois-menu-imagem") }
    @Config(qualifiers = Phone.PORT) @Test fun menuImagePort() { menu(InputMode.TOUCH, 6); compose.shot("ajustes-1/depois-menu-imagem-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun menuImageController() { menu(InputMode.CONTROLLER, 5); compose.shot("ajustes-1/depois-menu-imagem-controle") }
    @Config(qualifiers = Phone.PORT) @Test fun driversAvailablePort() { available(InputMode.TOUCH); compose.shot("ajustes-1/depois-drivers-para-baixar-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun driversAvailable() { available(InputMode.TOUCH); compose.shot("ajustes-1/depois-drivers-para-baixar") }

    private companion object {
        val RELEASES_MAIN = """
            [{"tag_name":"v25.3.0-r2","name":"Turnip Mesa 25.3.0 r2 (A6xx/A7xx/A8xx)","published_at":"2026-09-21T10:00:00Z","draft":false,"prerelease":false,
              "assets":[{"name":"turnip_v25.3.0_r2_a8xx_emulators_axxx.zip","browser_download_url":"https://example.invalid/a.zip","digest":"sha256:${"ab".repeat(32)}"},
                        {"name":"turnip_v25.3.0_r2_a6xx_a7xx_legacy_emulators_build.zip","browser_download_url":"https://example.invalid/b.zip"}]},
             {"tag_name":"v25.2.1","name":"Turnip Mesa 25.2.1","published_at":"2026-08-02T10:00:00Z","draft":false,"prerelease":false,
              "assets":[{"name":"turnip_v25.2.1.zip","browser_download_url":"https://example.invalid/c.zip","digest":"sha256:${"cd".repeat(32)}"}]},
             {"tag_name":"v26.0.0-dev","name":"Turnip 26.0 dev","published_at":"2026-09-30T10:00:00Z","draft":false,"prerelease":true,
              "assets":[{"name":"turnip_dev.zip","browser_download_url":"https://example.invalid/d.zip"}]}]
        """.trimIndent()
        val RELEASES_OTHER = "[]"
    }
}
