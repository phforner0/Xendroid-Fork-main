package xendroid.compose.screens

import android.os.Build
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import xendroid.compose.data.GameFormat
import xendroid.compose.data.MissingTitle
import xendroid.compose.data.MissingTitles
import xendroid.compose.sessions.TitleActivity
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.SampleArt
import xendroid.compose.shots.SampleGame
import xendroid.compose.shots.SampleLibrary
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.shots.screen
import xendroid.compose.shots.shot
import xendroid.compose.ui.about.AboutScreen
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.library.FirstRunAssistant
import xendroid.compose.ui.library.FirstRunStep
import xendroid.compose.ui.library.FolderBrowserScreen
import xendroid.compose.ui.library.GameFoldersScreen
import xendroid.compose.ui.library.MissingGamesScreen
import xendroid.compose.ui.library.NoVulkanScreen
import xendroid.compose.updater.FeedRelease
import xendroid.compose.updater.ReleaseAsset
import xendroid.compose.updater.UpdateResult
import xendroid.compose.updater.UpdateScreen
import xendroid.compose.updater.UpdateStage
import xendroid.compose.updater.UpdateState

/** Batch 6 "depois": first run, game folders, the folder browser, missing games, no Vulkan, app updates, About. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class Lote6Shots {
    @get:Rule val compose = createComposeRule()

    /** A phone as the prints describe it: 64-bit ARM, a named model and SoC. */
    @Before fun phone() {
        ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_ABIS", arrayOf("arm64-v8a"))
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Xiaomi")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "POCO F7")
        ReflectionHelpers.setStaticField(Build::class.java, "SOC_MANUFACTURER", "Qualcomm")
        ReflectionHelpers.setStaticField(Build::class.java, "SOC_MODEL", "SM8735")
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "RELEASE", "14")
    }

    private val root: File get() = Environment.getExternalStorageDirectory()
    private val games: File get() = File(root, "Games/Xbox 360")

    /** Each sample game's cover as a file the screens show. */
    private fun cover(g: SampleGame): File = File(Fixture.context.cacheDir, "cover-${g.titleId}.png").apply { if (!isFile) writeBytes(SampleArt.coverPng(g)) }

    /** The phone's storage as the browser and the folder counts see it: Xbox 360 discs carry their
     *  XDVDFS magic, the PS2 one does not. */
    private fun storage() {
        SampleLibrary.games.forEach { g ->
            File(games, g.path.substringAfter("Xbox 360/")).apply {
                parentFile?.mkdirs()
                when {
                    !name.contains('.') -> mkdirs()
                    name.endsWith(".iso", ignoreCase = true) ->
                        java.io.RandomAccessFile(this, "rw").use { it.seek(32 * 2048L); it.write("MICROSOFT*XBOX*MEDIA".toByteArray()) }
                    else -> writeBytes(ByteArray(64))
                }
            }
        }
        File(games, "Viva Pinata").mkdirs(); File(games, "Viva Pinata/default.xex").writeBytes(ByteArray(64))
        listOf("Android", "DCIM/Camera", "Download", "Music", "Pictures", "Games/PS2", "Games/Switch").forEach { File(root, it).mkdirs() }
        File(root, "Download/Halo 3 - Mythic Map Pack").writeBytes(ByteArray(2048))
        File(root, "Games/PS2/God of War.iso").writeBytes(ByteArray(64))
    }

    // ------------------------------------------------------------------ first run

    private fun firstRun(mode: InputMode, step: FirstRunStep, folder: Boolean = false, profile: String? = null) {
        val covers = if (folder) SampleLibrary.games.filter { it.customCover }.take(9).map(::cover) else emptyList()
        compose.app(mode) {
            Box(Modifier.fillMaxSize().background(Xd.colors.bg))
            FirstRunAssistant(folderReady = folder, onChooseFolder = {}, onOpenProfiles = {}, onClose = {},
                folders = if (folder) listOf(games.absolutePath) else emptyList(), gamesFound = if (folder) SampleLibrary.games.size else null,
                covers = covers, activeProfile = profile, onCreateProfile = {}, step = step)
        }
        Fixture.settle(20)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun firstRunPhone() { firstRun(InputMode.TOUCH, FirstRunStep.PHONE); compose.screen("lote6/depois-primeira-abertura") }
    @Config(qualifiers = Phone.PORT) @Test fun firstRunPhonePort() { firstRun(InputMode.TOUCH, FirstRunStep.PHONE); compose.screen("lote6/depois-primeira-abertura-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun firstRunGames() {
        firstRun(InputMode.TOUCH, FirstRunStep.GAMES, folder = true); compose.screen("lote6/depois-primeira-abertura-jogos")
    }
    @Config(qualifiers = Phone.LAND) @Test fun firstRunLocale() { firstRun(InputMode.TOUCH, FirstRunStep.LOCALE); compose.screen("lote6/depois-primeira-abertura-idioma") }
    @Config(qualifiers = Phone.LAND) @Test fun firstRunProfile() { firstRun(InputMode.TOUCH, FirstRunStep.PROFILE); compose.screen("lote6/depois-primeira-abertura-perfil") }
    @Config(qualifiers = Phone.LAND) @Test fun firstRunUse() { firstRun(InputMode.CONTROLLER, FirstRunStep.USE); compose.screen("lote6/depois-primeira-abertura-como-usar") }

    // ------------------------------------------------------------------ folders and the browser

    private fun folders(mode: InputMode) {
        storage()
        val library = SampleLibrary.games.map { it.game(null).copy(launchUri = File(games, it.path.substringAfter("Xbox 360/")).absolutePath) }
        val sd = "/storage/1A2B-3C4D/Jogos"
        compose.app(mode) {
            GameFoldersScreen(folders = listOf(games.absolutePath, sd, File(root, "Download").absolutePath), unavailable = listOf(sd),
                games = library, scanning = false, onAdd = {}, onRemove = {}, onMakeInstallFolder = {}, onRescan = {}, onBack = {})
        }
        Fixture.settle(10)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun gameFolders() { folders(InputMode.TOUCH); compose.shot("lote6/depois-pastas") }
    @Config(qualifiers = Phone.PORT) @Test fun gameFoldersPort() { folders(InputMode.TOUCH); compose.shot("lote6/depois-pastas-retrato") }

    private fun browser(mode: InputMode, files: Boolean = false) {
        storage()
        compose.app(mode) {
            if (files) FolderBrowserScreen(onFileChosen = {}, onCancel = {}, start = File(root, "Download"))
            else FolderBrowserScreen(onFolderChosen = {}, onCancel = {}, start = File(root, "Games"))
        }
        // The game counts of each folder land from a background walk.
        Fixture.settle(80)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun folderBrowser() { browser(InputMode.TOUCH); compose.shot("lote6/depois-navegador-de-pastas") }
    @Config(qualifiers = Phone.LAND) @Test fun folderBrowserController() { browser(InputMode.CONTROLLER); compose.shot("lote6/depois-navegador-de-pastas-controle") }
    @Config(qualifiers = Phone.PORT) @Test fun fileBrowserPort() { browser(InputMode.TOUCH, files = true); compose.shot("lote6/depois-navegador-arquivo-retrato") }

    // ------------------------------------------------------------------ missing games

    /** Games that left the library: one on the phone, one on an SD card that is out, one outside the folders. */
    private fun goneGames() = listOf(
        SampleGame("4D5307D5", "Mass Effect", GameFormat.ISO, File(games, "Mass Effect.iso").absolutePath,
            Triple(0xFF0A1426.toInt(), 0xFF2A5AA8.toInt(), 0xFFE0E8FF.toInt()), "shards", customCover = true),
        SampleGame("5454085C", "BioShock", GameFormat.ISO, "/storage/1A2B-3C4D/Jogos/BioShock.iso",
            Triple(0xFF0D1A14.toInt(), 0xFF1F6B5A.toInt(), 0xFFF2C46A.toInt()), "bands", customCover = true),
        SampleGame("41560817", "Crackdown", GameFormat.ZAR, File(root, "Download/Crackdown.zar").absolutePath,
            Triple(0xFF140A1A.toInt(), 0xFF7A2A9A.toInt(), 0xFF9BE37F.toInt()), "grid", customCover = true),
    )

    @Config(qualifiers = Phone.LAND) @Test fun missingGames() {
        val reasons = listOf(MissingTitles.Reason.FILE_GONE, MissingTitles.Reason.FOLDER_AWAY, MissingTitles.Reason.OUTSIDE_FOLDERS)
        val goneGames = goneGames()
        val missing = goneGames.zip(reasons) { g, why -> MissingTitle(g.titleId, g.name, g.path, why) }
        val now = System.currentTimeMillis()
        val activity = mapOf(
            "4D5307D5" to TitleActivity("4D5307D5", now - 40L * 86_400_000, 14 * 3_600_000L, 22),
            "5454085C" to TitleActivity("5454085C", now - 9L * 86_400_000, 3 * 3_600_000L, 5),
            "41560817" to TitleActivity("41560817", now - 2L * 86_400_000, 50 * 60_000L, 2),
        )
        val covers = goneGames.associate { it.titleId to cover(it) }
        compose.app(InputMode.TOUCH) {
            MissingGamesScreen(missing, activity, coverOf = { covers.getValue(it) }, onRemove = {}, onAddFolderOf = {}, onFind = {},
                onFolders = {}, onBack = {})
        }
        Fixture.settle(20)
        compose.waitForIdle()
        compose.shot("lote6/depois-jogos-que-sairam")
    }

    // ------------------------------------------------------------------ no Vulkan

    private fun noVulkan(mode: InputMode) {
        compose.app(mode) { NoVulkanScreen(onQuit = {}) }
        Fixture.settle(10)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun noVulkanLand() { noVulkan(InputMode.TOUCH); compose.shot("lote6/depois-sem-vulkan") }
    @Config(qualifiers = Phone.PORT) @Test fun noVulkanPort() { noVulkan(InputMode.TOUCH); compose.shot("lote6/depois-sem-vulkan-retrato") }

    // ------------------------------------------------------------------ updates

    private val release = FeedRelease(tagName = "v413-8c1e2f0", title = "XenDroid v413",
        notes = "## What's Changed\n* Biblioteca: capas maiores e o carrossel no modo controle\n* Menu em jogo: limite de FPS com salvar para o jogo numa linha\n" +
            "* Drivers: download conferido pelo SHA-256 com progresso\n* Correção: travamento ao restaurar saves com o jogo aberto\n" +
            "**Full Changelog**: https://example.invalid/compare/v412...v413",
        pageUrl = "https://example.invalid/releases/v413", prerelease = false, draft = false,
        assets = listOf(ReleaseAsset("XenDroid_Release_8c1e2f0.apk", 32_925_000L, "https://example.invalid/XenDroid_Release_8c1e2f0.apk",
            "8c1e2f04a9d1b7e3c6f2a8b0d4e6f1a3c5b7d9e1f3a5c7e9b1d3f5a7c9e1b3d5")))

    private fun updates(mode: InputMode, state: UpdateState) {
        // As a release build with its update channel shows it.
        compose.app(mode) { UpdateScreen(onBack = {}, state = state, checkOnOpen = false, feed = true) }
        Fixture.settle(10)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun updateAvailable() { updates(InputMode.TOUCH, UpdateState(UpdateResult.Available(release))); compose.shot("lote6/depois-atualizador") }
    @Config(qualifiers = Phone.PORT) @Test fun updateAvailablePort() { updates(InputMode.TOUCH, UpdateState(UpdateResult.Available(release))); compose.shot("lote6/depois-atualizador-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun updateDownloading() {
        updates(InputMode.TOUCH, UpdateState(UpdateResult.Available(release)).apply { stage = UpdateStage.DOWNLOAD; progress = 0.42f })
        compose.shot("lote6/depois-atualizador-baixando")
    }
    @Config(qualifiers = Phone.LAND) @Test fun updateLatest() {
        updates(InputMode.CONTROLLER, UpdateState(UpdateResult.Latest("7bb3409c2")))
        compose.shot("lote6/depois-atualizador-controle")
    }

    // ------------------------------------------------------------------ About

    private fun about(mode: InputMode) {
        compose.app(mode) { AboutScreen(onBack = {}) }
        Fixture.settle(10)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun aboutLand() { about(InputMode.TOUCH); compose.shot("lote6/depois-sobre") }
    @Config(qualifiers = Phone.PORT) @Test fun aboutPort() { about(InputMode.TOUCH); compose.shot("lote6/depois-sobre-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun aboutController() { about(InputMode.CONTROLLER); compose.shot("lote6/depois-sobre-controle") }
}
