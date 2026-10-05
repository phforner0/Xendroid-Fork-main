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
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.SampleArt
import xendroid.compose.shots.SampleLibrary
import xendroid.compose.shots.SampleMenu
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.shots.shot
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.ingame.BootStatus
import xendroid.compose.ui.ingame.GameLoadingScreen
import xendroid.compose.ui.ingame.InGameMenu
import xendroid.compose.ui.ingame.InGameMenuState
import xendroid.compose.ui.ingame.InGamePage
import xendroid.compose.ui.ingame.LaunchFailure
import xendroid.compose.ui.ingame.LaunchFailureScreen
import xendroid.compose.ui.ingame.LoadingDetails

/** Batch 2 "depois": the loading screen, the launch failure and the in-game menu over a game. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class Lote2Shots {
    @get:Rule val compose = createComposeRule()

    private val halo = SampleLibrary.games.first { it.titleId == "4D5307E6" }

    private fun cover(): File = File(Fixture.context.cacheDir, "halo-cover.png").apply { writeBytes(SampleArt.coverPng(halo)) }

    /** A stand-in for the game's picture behind the menu: its cover, large and soft. */
    @Composable
    private fun Scene(content: @Composable () -> Unit) {
        val bitmap = BitmapFactory.decodeByteArray(SampleArt.coverPng(halo), 0, SampleArt.coverPng(halo).size).asImageBitmap()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(bitmap, null, Modifier.fillMaxSize().blur(2.dp), contentScale = ContentScale.Crop)
            content()
        }
    }

    private fun loading(mode: InputMode) {
        val art = cover()
        compose.app(mode) {
            GameLoadingScreen(BootStatus(BootStatus.Stage.GRAPHICS, pipelines = 287, seconds = 6), art, "Halo 3", onCancel = {},
                details = LoadingDetails(profile = "ChefeMaster117", titleId = "4D5307E6", badges = listOf("Turnip v25.3.0 r2", "Limite 30 FPS"),
                    ownSettings = 3, withOptions = false))
        }
        compose.waitForIdle()
    }

    private fun menu(mode: InputMode, page: InGamePage = InGamePage.GRAPHICS) {
        val art = cover()
        compose.app(mode) {
            Scene {
                InGameMenu(
                    state = InGameMenuState(open = true, page = page, developer = false),
                    model = SampleMenu.model(art),
                    onPage = {}, onSelect = {}, onAction = {}, onAdjust = { _, _ -> }, onChoose = { _, _ -> }, onSet = { _, _ -> },
                    onLogChoice = {}, onQuitChoice = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun failure(mode: InputMode) {
        val art = cover()
        compose.app(mode) {
            LaunchFailureScreen(LaunchFailure("O núcleo do emulador não iniciou: vkCreateDevice falhou (VK_ERROR_INITIALIZATION_FAILED).",
                LaunchFailure.Kind.DRIVER, listOf("E xe: vkCreateDevice: VK_ERROR_INITIALIZATION_FAILED",
                    "E xe: GPU: Adreno (TM) 740 · driver Turnip 26.0 dev", "I xe: Abortando o boot: o dispositivo Vulkan não foi criado")),
                art, onBack = {}, onRetry = {}, onShareLogs = {}, onRetrySystemDriver = {})
        }
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun loadingLand() { loading(InputMode.TOUCH); compose.shot("lote2/depois-carregamento") }
    @Config(qualifiers = Phone.PORT) @Test fun loadingPort() { loading(InputMode.TOUCH); compose.shot("lote2/depois-carregamento-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun loadingController() { loading(InputMode.CONTROLLER); compose.shot("lote2/depois-carregamento-controle") }
    @Config(qualifiers = Phone.LAND) @Test fun menuLand() { menu(InputMode.TOUCH); compose.shot("lote2/depois-menu-em-jogo") }
    @Config(qualifiers = Phone.LAND) @Test fun menuPerformance() { menu(InputMode.TOUCH, InGamePage.SYSTEM); compose.shot("lote2/depois-menu-desempenho") }
    @Config(qualifiers = Phone.PORT) @Test fun menuPort() { menu(InputMode.TOUCH); compose.shot("lote2/depois-menu-em-jogo-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun menuController() { menu(InputMode.CONTROLLER); compose.shot("lote2/depois-menu-em-jogo-controle") }
    @Config(qualifiers = Phone.LAND) @Test fun failureLand() { failure(InputMode.TOUCH); compose.shot("lote2/depois-falha-ao-abrir") }
}
