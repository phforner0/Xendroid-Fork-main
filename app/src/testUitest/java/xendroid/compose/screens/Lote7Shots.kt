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
import xendroid.compose.Emulator
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.SampleArt
import xendroid.compose.shots.SampleGame
import xendroid.compose.shots.SampleLibrary
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.shots.shot
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.disc.DiscSwapPanel
import xendroid.compose.ui.disc.requestedDiscIndex
import xendroid.compose.ui.keyboard.GuestKeyboardPanel
import xendroid.compose.ui.keyboard.KeyboardGrid
import xendroid.compose.ui.messagebox.GuestMessageBoxPanel

/** Batch 7 "depois": the panels the game asks for (a message, a text, another disc) over the paused game. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class Lote7Shots {
    @get:Rule val compose = createComposeRule()

    private fun cover(g: SampleGame): File = File(Fixture.context.cacheDir, "cover-${g.titleId}.png").apply { if (!isFile) writeBytes(SampleArt.coverPng(g)) }

    /** A stand-in for the paused game behind the panel: its cover, large and soft. */
    @Composable
    private fun Scene(g: SampleGame, content: @Composable () -> Unit) {
        val png = SampleArt.coverPng(g)
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size).asImageBitmap()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(bitmap, null, Modifier.fillMaxSize().blur(2.dp), contentScale = ContentScale.Crop)
            content()
        }
    }

    private fun show(mode: InputMode, content: @Composable () -> Unit) {
        compose.app(mode, content)
        Fixture.settle(15)
        compose.waitForIdle()
    }

    // ------------------------------------------------------------------ message

    private val halo = SampleLibrary.byId("4D5307E6")

    private fun message(long: Boolean = false) = Emulator.MessageBoxRequest().apply {
        id = 1
        if (long) {
            title = "Contrato de licença"
            text = "Ao jogar você concorda com os termos de uso do jogo. ".repeat(14).trim() + " Os dados de progresso ficam no perfil ativo."
            buttons = arrayOf("Aceitar", "Recusar")
        } else {
            title = "Dispositivo de armazenamento"
            text = "Nenhum dispositivo de armazenamento selecionado. Sem um, o seu progresso não será salvo. Deseja continuar?"
            buttons = arrayOf("Selecionar dispositivo", "Continuar sem salvar")
        }
        activeButton = 0
    }

    @Config(qualifiers = Phone.LAND) @Test fun messageBox() {
        show(InputMode.TOUCH) { Scene(halo) { GuestMessageBoxPanel(message(), 0, {}, gameName = halo.name, art = cover(halo)) } }
        compose.shot("lote7/depois-mensagem")
    }
    @Config(qualifiers = Phone.LAND) @Test fun messageBoxController() {
        show(InputMode.CONTROLLER) { Scene(halo) { GuestMessageBoxPanel(message(), 1, {}, gameName = halo.name, art = cover(halo)) } }
        compose.shot("lote7/depois-mensagem-controle")
    }
    @Config(qualifiers = Phone.PORT) @Test fun messageBoxLong() {
        val rdr = SampleLibrary.byId("5454082B")
        show(InputMode.TOUCH) { Scene(rdr) { GuestMessageBoxPanel(message(long = true), 0, {}, gameName = rdr.name, art = cover(rdr)) } }
        compose.shot("lote7/depois-mensagem-longa-retrato")
    }

    // ------------------------------------------------------------------ keyboard

    private val forza = SampleLibrary.byId("4D5309C9")

    private val prompt = Emulator.KeyboardRequest().apply {
        id = 2; title = "Nome"; description = "Digite o nome do seu piloto"; defaultText = "Shepard"; maxLength = 15
    }

    @Config(qualifiers = Phone.LAND) @Test fun keyboard() {
        val grid = KeyboardGrid(maxUnits = 15).withText("Shepard")
        show(InputMode.TOUCH) { Scene(forza) { GuestKeyboardPanel(prompt, grid, {}, {}, {}, gameName = forza.name, art = cover(forza)) } }
        compose.shot("lote7/depois-teclado")
    }
    @Config(qualifiers = Phone.LAND) @Test fun keyboardController() {
        val grid = KeyboardGrid(maxUnits = 15).withText("Shepard").copy(row = 2, col = 3, shift = KeyboardGrid.Shift.ONCE)
        show(InputMode.CONTROLLER) { Scene(forza) { GuestKeyboardPanel(prompt, grid, {}, {}, {}, gameName = forza.name, art = cover(forza)) } }
        compose.shot("lote7/depois-teclado-controle")
    }
    @Config(qualifiers = Phone.PORT) @Test fun keyboardSymbolsPort() {
        val grid = KeyboardGrid(maxUnits = 15).withText("Shepard").togglePage()
        show(InputMode.TOUCH) { Scene(forza) { GuestKeyboardPanel(prompt, grid, {}, {}, {}, gameName = forza.name, art = cover(forza)) } }
        compose.shot("lote7/depois-teclado-simbolos-retrato")
    }

    // ------------------------------------------------------------------ disc swap

    private val blue = SampleLibrary.byId("4D5307DF")

    private fun disc(found: Boolean) = Emulator.DiscSwapRequest().apply {
        id = 3; message = ""; isError = false; discNumber = 2
        val dir = "/storage/emulated/0/Games/Xbox 360"
        discLabels = if (found) arrayOf("Disco 1 de 3", "Disco 2 de 3", "Disco 3 de 3") else emptyArray()
        discPaths = if (found) Array(3) { "$dir/Blue Dragon (Disc ${it + 1}).iso" } else emptyArray()
    }

    @Config(qualifiers = Phone.LAND) @Test fun discSwap() {
        val request = disc(found = true)
        show(InputMode.TOUCH) {
            Scene(blue) { DiscSwapPanel(request, requestedDiscIndex(request), {}, {}, gameName = blue.name, art = cover(blue), current = request.discPaths[0]) }
        }
        compose.shot("lote7/depois-troca-de-disco")
    }
    @Config(qualifiers = Phone.LAND) @Test fun discSwapController() {
        val request = disc(found = true)
        show(InputMode.CONTROLLER) {
            Scene(blue) { DiscSwapPanel(request, requestedDiscIndex(request), {}, {}, gameName = blue.name, art = cover(blue), current = request.discPaths[0]) }
        }
        compose.shot("lote7/depois-troca-de-disco-controle")
    }
    @Config(qualifiers = Phone.PORT) @Test fun discSwapNone() {
        show(InputMode.TOUCH) { Scene(blue) { DiscSwapPanel(disc(found = false), 0, {}, {}, gameName = blue.name, art = cover(blue)) } }
        compose.shot("lote7/depois-troca-de-disco-sem-disco-retrato")
    }
}
