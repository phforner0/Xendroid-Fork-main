package xendroid.compose.roadmap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.AppContainer
import xendroid.compose.Application
import xendroid.compose.R
import xendroid.compose.compatibility.ProfileSource
import xendroid.compose.compatibility.SettingsProfileStore
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.GameSettingsRepository
import xendroid.compose.settings.GameSettingsViewModel
import xendroid.compose.settings.GameSettingsViewModel.ProfileMessage
import xendroid.compose.settings.GameSettingsViewModel.ProfilePreview
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.ui.settings.PerGameSettingsScreen
import xendroid.compose.ui.theme.xendroidTheme

/**
 * Roadmap item 35 (C05): a settings profile file put in the user data's settings-profiles
 * folder ("teste.json", as in the roadmap). A game's settings screen offers it ("From the file
 * teste.json…"); with this game's widescreen set Off by the player, the preview says the frame
 * limit goes 60 → 30 and widescreen stays Off; applying writes only that, for this game;
 * restoring puts the limit back to following the global one, but keeps a value the player
 * changed after applying. A newer format or an unknown requirement refuses the file; a GPU
 * requirement this phone does not meet says "Not offered on this phone".
 *
 * Left for the phone: the game opening at 30 FPS (group C).
 */
@RunWith(AndroidJUnit4::class)
class Item35RecommendedSettingsTest {
    @get:Rule val compose = createComposeRule()

    private val context = Device.context
    private val title = "4D5309C9"
    private val name = "Teste 30 FPS"
    private val fps = SettingsSchema.byKey.getValue("GPU|framerate_limit") as Setting.ListChoice
    private val widescreen = SettingsSchema.byKey.getValue("Console|widescreen") as Setting.Bool
    private lateinit var profileFile: File
    private lateinit var global: File
    private lateinit var game: File
    private var savedGlobal: ByteArray? = null
    private var savedGame: ByteArray? = null
    private val record get() = File(File(Application.get_internal_data_dir(), "settings-profiles-applied"), "$title.json")

    @Before fun setUp() {
        Device.requireTestPackage()
        EmulatorRuntime.ensureLoaded()
        val store = ConfigStore(context)
        global = store.globalConfigFile()
        game = store.perGameConfigFile(title)
        savedGlobal = global.takeIf { it.isFile }?.readBytes()
        savedGame = game.takeIf { it.isFile }?.readBytes()
        record.delete()
        profileFile = File(File(Device.storageRoot, SettingsProfileStore.LOCAL_FOLDER).apply { mkdirs() }, "teste.json")
        store.editLiveConfig { it.putSetting(fps, "60") }
        // The player chose widescreen Off for this game.
        GameSettingsRepository(store, title).apply { open(); setBool(widescreen, false); flush() }
    }

    @After fun tearDown() {
        profileFile.delete()
        record.delete()
        savedGlobal?.let { global.writeBytes(it) }
        if (savedGame != null) game.writeBytes(savedGame!!) else game.delete()
    }

    private fun profiles(version: Int = 1, requires: String? = null) = profileFile.writeText(
        """{"format":"xendroid-settings-profiles","version":$version,"profiles":[{"id":"teste-30","name":"$name",""" +
            """"titleIds":["$title"],"reason":"teste do mecanismo",""" +
            (requires?.let { """"requires":$it,""" } ?: "") +
            """"settings":{"GPU|framerate_limit":"30","Console|widescreen":"true"}}]}""")

    private fun viewModel(): GameSettingsViewModel =
        AppContainer(context).gameSettingsViewModelFactory(title).create(GameSettingsViewModel::class.java)

    private fun loaded(vm: GameSettingsViewModel) = Device.await(vm.profilesState, "profiles read") { !it.isEmpty }

    private fun gameValue(s: Setting): String? = ConfigStore(context).openGameConfig(title).let { h ->
        try { h.getString(s.section, s.name) } finally { h.closeDiscard() }
    }

    private fun waitFor(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun offeredPreviewedAppliedAndRestored() {
        profiles()
        val vm = viewModel()
        val offered = loaded(vm).offered.single { it.profile.id == "teste-30" }
        assertEquals(ProfileSource.LOCAL, offered.source)
        assertEquals("teste.json", offered.origin)
        compose.setContent { xendroidTheme { PerGameSettingsScreen(vm, gameName = "Roadmap", onBack = {}) } }
        waitFor(Device.string(R.string.prof_title))
        compose.onNodeWithText(name).assertExists()
        compose.onNodeWithText(Device.string(R.string.prof_from_file, "teste.json")).assertExists()

        compose.onNodeWithText(Device.string(R.string.prof_preview)).performClick()
        Device.await(vm.profilePreview, "the preview") { it is ProfilePreview.Apply }
        val fpsTitle = Device.string(R.string.set_framerate_limit)
        val wideTitle = Device.string(R.string.set_widescreen)
        compose.onNodeWithText(Device.string(R.string.prof_line_change, fpsTitle, "60 FPS", "30 FPS")).assertExists()
        compose.onNodeWithText(Device.string(R.string.prof_line_yours, wideTitle,
            Device.string(R.string.prof_off), Device.string(R.string.prof_on))).assertExists()
        compose.onNodeWithText(Device.plural(R.plurals.prof_apply_n, 1, 1)).performClick()
        waitFor(Device.plural(R.plurals.prof_msg_applied, 1, name, 1))
        assertEquals(ProfileMessage.Applied(name, 1), vm.profileMessage.value)
        assertEquals("30", gameValue(fps))
        assertEquals("false", gameValue(widescreen))
        compose.onNodeWithText(Device.string(R.string.common_ok)).performClick()

        compose.onNodeWithText(Device.string(R.string.prof_restore_previous)).performClick()
        Device.await(vm.profilePreview, "the restore preview") { it is ProfilePreview.Restore }
        compose.onNodeWithText(Device.string(R.string.prof_restore)).performClick()
        waitFor(Device.string(R.string.prof_msg_restored, name))
        assertNull("the limit follows the global one again", gameValue(fps))
        assertEquals("false", gameValue(widescreen))
    }

    @Test fun restoringKeepsWhatThePlayerChangedSince() {
        profiles()
        val vm = viewModel()
        val offered = loaded(vm).offered.single()
        vm.previewProfile(offered)
        Device.await(vm.profilePreview, "the preview") { it is ProfilePreview.Apply }
        vm.confirmPreview()
        Device.await(vm.profileMessage, "applied") { it is ProfileMessage.Applied }
        vm.onListChanged(fps, "45")
        vm.flush()
        Device.waitUntil("the player's 45 saved") { gameValue(fps) == "45" }
        Device.await(vm.profilesState, "the applied record") { it.applied != null }
        vm.clearProfileMessage()
        vm.previewRestore()
        Device.await(vm.profilePreview, "the restore preview") { it is ProfilePreview.Restore }
        vm.confirmPreview()
        assertEquals(ProfileMessage.Restored(name, changedSince = true),
            Device.await(vm.profileMessage, "restored") { it is ProfileMessage.Restored })
        assertEquals("45", gameValue(fps))
    }

    @Test fun refusedFilesAndOtherPhonesAreSaid() {
        var shown by mutableStateOf<GameSettingsViewModel?>(null)
        compose.setContent {
            xendroidTheme { shown?.let { vm -> key(vm) { PerGameSettingsScreen(vm, gameName = "Roadmap", onBack = {}) } } }
        }
        fun open(): GameSettingsViewModel = viewModel().also { shown = it }

        profiles(version = 2)
        val newer = loaded(open()).skipped
        assertEquals(listOf("teste.json: format \"xendroid-settings-profiles\" version 2 is not supported"), newer)
        waitFor(Device.string(R.string.prof_skipped, newer.single()))

        profiles(requires = """{"futureRule":true}""")
        val unknown = loaded(open()).skipped
        assertEquals(listOf("teste.json: not a settings profile file"), unknown)
        waitFor(Device.string(R.string.prof_skipped, unknown.single()))

        profiles(requires = """{"gpuContains":["Mali"]}""")
        val state = loaded(open())
        assertTrue(state.offered.isEmpty())
        assertEquals(name, state.notHere.single().first.profile.name)
        waitFor(Device.string(R.string.prof_not_here))
    }
}
