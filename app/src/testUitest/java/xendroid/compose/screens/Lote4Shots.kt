package xendroid.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.AppContainer
import xendroid.compose.core.ContentPaths
import xendroid.compose.data.PlayableProfile
import xendroid.compose.data.ProfileSlots
import xendroid.compose.settings.ConfigStore
import xendroid.compose.shots.FakeCore
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.SampleArt
import xendroid.compose.shots.SampleLibrary
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.shots.screen
import xendroid.compose.shots.shot
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.library.PlayAsDialog
import xendroid.compose.ui.profile.ProfileManagerViewModel
import xendroid.compose.ui.profile.ProfilesLinks
import xendroid.compose.ui.profile.ProfilesScreen
import xendroid.compose.ui.profile.ProfilesSections
import xendroid.compose.ui.saves.SaveManagerScreen
import xendroid.compose.ui.saves.SaveManagerViewModel
import xendroid.compose.ui.saves.SavesSections

/** Batch 4 "depois": Profiles (cards, who plays, trash, the form), Play as, and a game's saves. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class Lote4Shots {
    @get:Rule val compose = createComposeRule()

    private val noble = "E03000000C0FFEE1"

    /** An XCONTENT_AGGREGATE_DATA header as the core writes it. */
    private fun header(name: String, file: String): ByteArray = ByteBuffer.allocate(0x148).apply {
        putInt(1); putInt(1)
        position(0x8); put(name.toByteArray(Charsets.UTF_16BE))
        position(0x108); put(file.toByteArray(Charsets.US_ASCII))
    }.array()

    /** A save package of [xuid] for [title], named by the game, [kb] KB, saved [hoursAgo] ago. */
    private fun save(xuid: String, title: String, pkg: String, name: String?, kb: Int, hoursAgo: Int) {
        val root = ContentPaths.contentRoot()
        val dir = File(root, "$xuid/$title/00000001/$pkg").apply { mkdirs() }
        File(dir, "data").apply { writeBytes(ByteArray(kb * 1024)); setLastModified(System.currentTimeMillis() - hoursAgo * 3_600_000L) }
        if (name != null) File(root, "$xuid/$title/Headers/00000001").apply { mkdirs() }.let { File(it, "$pkg.header").writeBytes(header(name, pkg)) }
    }

    /** Three profiles (P1 active, P2 the second), their saves in a few games, one in the trash. */
    private fun world(): AppContainer {
        val container = AppContainer(Fixture.context)
        FakeCore.profiles = listOf(FakeCore.profile(Fixture.P1, "ChefeMaster117"), FakeCore.profile(Fixture.P2, "Arbiter"),
            FakeCore.profile(noble, "Noble6", language = 1, country = 103))
        ConfigStore(Fixture.context).editLiveConfig { h ->
            h.putString(ProfileSlots.SECTION, ProfileSlots.key(0), Fixture.P1)
            h.putString(ProfileSlots.SECTION, ProfileSlots.key(1), Fixture.P2)
        }
        save(Fixture.P1, "4D5307E6", "CAMPAIGN1", "Campanha · Lendário", 1434, 2)
        save(Fixture.P1, "4D5307E6", "PROFILE", "Perfil do jogador", 486, 2)
        save(Fixture.P1, "4D5307E6", "FILMS", "Filmes salvos (3)", 320, 26 * 7)
        save(Fixture.P1, "4D5309C9", "FH1", "Festival · dia 12", 2210, 26)
        save(Fixture.P1, "5454082B", "RDR", "Capítulo 3 · Armadillo", 880, 24 * 20)
        save(Fixture.P2, "4D5307E6", "CAMPAIGN1", "Campanha · Normal", 480, 24 * 13)
        save(Fixture.P2, "4D5307E6", "PROFILE", null, 32, 24 * 13)
        // A profile in the trash still has its saves of Halo 3 there and on the device.
        File(ContentPaths.contentRoot(), ".xendroid-trash/profiles/E03000000BADF00D-${System.currentTimeMillis() - 86_400_000L * 5}").mkdirs()
        save("E03000000BADF00D", "4D5307E6", "OLD", "Campanha · Heroico", 300, 24 * 60)
        Fixture.settle(5)
        return container
    }

    private fun profiles(mode: InputMode, section: String? = null) {
        val container = world()
        val vm = container.profileManagerViewModelFactory().create(ProfileManagerViewModel::class.java)
        Fixture.settleUntil { vm.summaries.value.isNotEmpty() }
        compose.app(mode) {
            ProfilesScreen(vm, onBack = {}, links = ProfilesLinks(gameName = { id -> SampleLibrary.games.firstOrNull { it.titleId == id }?.name },
                gameArt = { id -> SampleLibrary.games.firstOrNull { it.titleId == id }?.let { g ->
                    File(Fixture.context.cacheDir, "cover-$id.png").apply { if (!isFile) writeBytes(SampleArt.coverPng(g)) } } }),
                initialSection = section)
        }
        Fixture.settle(20)
        compose.waitForIdle()
    }

    private fun saves(mode: InputMode, section: String? = null) {
        val container = world()
        val vm = container.saveManagerViewModelFactory("4D5307E6").create(SaveManagerViewModel::class.java)
        Fixture.settleUntil { vm.owners.value.isNotEmpty() }
        val sample = SampleLibrary.byId("4D5307E6")
        val art = File(Fixture.context.cacheDir, "halo-cover.png").apply { writeBytes(SampleArt.coverPng(sample)) }
        compose.app(mode) { SaveManagerScreen(vm, sample.name, onBack = {}, art = art, initialSection = section) }
        Fixture.settle(20)
        compose.waitForIdle()
    }

    @Config(qualifiers = Phone.LAND) @Test fun profilesLand() { profiles(InputMode.TOUCH); compose.shot("lote4/depois-perfis") }
    @Config(qualifiers = Phone.PORT) @Test fun profilesPort() { profiles(InputMode.TOUCH); compose.shot("lote4/depois-perfis-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun profilesController() { profiles(InputMode.CONTROLLER); compose.shot("lote4/depois-perfis-controle") }
    @Config(qualifiers = Phone.LAND) @Test fun players() { profiles(InputMode.TOUCH, ProfilesSections.PLAYERS); compose.shot("lote4/depois-quem-joga") }
    @Config(qualifiers = Phone.LAND) @Test fun trash() { profiles(InputMode.TOUCH, ProfilesSections.TRASH); compose.shot("lote4/depois-lixeira-de-perfis") }
    @Config(qualifiers = Phone.LAND) @Test fun profileForm() {
        profiles(InputMode.TOUCH)
        compose.onAllNodesWithText("Editar").onFirst().performClick()
        Fixture.settle(10)
        compose.screen("lote4/depois-editar-perfil")
    }
    @Config(qualifiers = Phone.LAND) @Test fun profileSaves() {
        profiles(InputMode.TOUCH)
        // The second card's button (ChefeMaster117: saves in three games), not the "Saves" label.
        compose.onAllNodes(hasText("Saves") and hasClickAction())[1].performClick()
        // The sheet's covers are decoded in the background.
        Fixture.settle(40)
        compose.waitForIdle()
        compose.screen("lote4/depois-saves-do-perfil")
    }
    @Config(qualifiers = Phone.LAND) @Test fun playAs() {
        world()
        compose.app(InputMode.TOUCH) {
            Box(Modifier.fillMaxSize().background(xendroid.compose.ui.design.Xd.colors.bg))
            PlayAsDialog(listOf(PlayableProfile(Fixture.P1, "ChefeMaster117"), PlayableProfile(Fixture.P2, "Arbiter"), PlayableProfile(noble, "Noble6")),
                preselected = Fixture.P1, onPlay = { _, _ -> }, onDismiss = {}, otherPlayers = mapOf(Fixture.P2 to 2), gameName = "Halo 3")
        }
        Fixture.settle(10)
        compose.screen("lote4/depois-jogar-como")
    }

    @Config(qualifiers = Phone.LAND) @Test fun gameSaves() {
        saves(InputMode.TOUCH)
        compose.onAllNodesWithText("Ver saves").onFirst().performClick()
        Fixture.settle(5)
        compose.shot("lote4/depois-saves")
    }
    @Config(qualifiers = Phone.PORT) @Test fun gameSavesPort() { saves(InputMode.TOUCH); compose.shot("lote4/depois-saves-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun gameSavesController() { saves(InputMode.CONTROLLER); compose.shot("lote4/depois-saves-controle") }
    @Config(qualifiers = Phone.LAND) @Test fun gameSavesSync() { saves(InputMode.TOUCH, SavesSections.SYNC); compose.shot("lote4/depois-saves-sincronizacao") }
}
