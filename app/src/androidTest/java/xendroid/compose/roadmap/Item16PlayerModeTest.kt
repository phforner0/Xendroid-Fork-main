package xendroid.compose.roadmap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.core.PresentationState
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.FpsConfigSnapshot
import xendroid.compose.settings.GameSettingsRepository
import xendroid.compose.settings.GameSettingsViewModel
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingsRepository
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.settings.UiMode
import xendroid.compose.settings.UiModeStore
import xendroid.compose.ui.ingame.InGameMenu
import xendroid.compose.ui.ingame.InGameMenuState
import xendroid.compose.ui.ingame.InGamePage
import xendroid.compose.ui.settings.PerGameSettingsScreen
import xendroid.compose.ui.settings.SettingsScreen
import xendroid.compose.ui.theme.xendroidTheme

/**
 * Roadmap item 16 (L02, Player mode): Settings → "Switch to Player" leaves one section of the 19
 * settings a player changes; a value changed there is the same one Developer shows, and an
 * advanced setting changed before keeps its value. The in-game menu in Player has no Win-FG,
 * LSFG, ADPF hints, host submissions, background pause or sustained performance (Developer has
 * them); a game's own settings follow the mode.
 *
 * Left for the phone: the in-game menu inside a running game (group C).
 */
@RunWith(AndroidJUnit4::class)
class Item16PlayerModeTest {
    @get:Rule val compose = createComposeRule()

    private val context = Device.context
    private val fps = SettingsSchema.byKey.getValue("GPU|framerate_limit") as Setting.ListChoice
    private val validation = SettingsSchema.byKey.getValue("Vulkan|vulkan_validation") as Setting.Bool
    private lateinit var global: File
    private var saved: ByteArray? = null

    @Before fun setUp() {
        Device.requireTestPackage()
        EmulatorRuntime.ensureLoaded()
        val store = ConfigStore(context)
        global = store.globalConfigFile()
        saved = global.takeIf { it.isFile }?.readBytes()
        store.editLiveConfig { it.putSetting(fps, "60"); it.putSetting(validation, "true") }
        UiModeStore.write(context, UiMode.DEVELOPER)
    }

    @After fun tearDown() {
        saved?.let { global.writeBytes(it) }
        UiModeStore.write(context, UiMode.DEVELOPER)
    }

    private fun live(s: Setting): String? = ConfigStore(context).openLiveSnapshot().let { h ->
        try { h.getString(s.section, s.name) } finally { h.closeDiscard() }
    }

    private fun waitForText(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun playerShowsTheEssentialsAndEveryValueIsKept() {
        val vm = SettingsViewModel(SettingsRepository(ConfigStore(context)))
        compose.setContent { xendroidTheme { SettingsScreen(vm, onBack = {}) } }
        val toPlayer = Device.string(R.string.set_to_player)
        waitForText(toPlayer)
        compose.onNodeWithText(toPlayer).performClick()
        assertEquals(UiMode.PLAYER, UiModeStore.read(context))

        val essentials = Device.string(R.string.set_cat_essentials)
        compose.onNodeWithText(essentials).assertIsDisplayed()
        compose.onNodeWithText(Device.plural(R.plurals.set_count, 19, 19), substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("GPU").assertCountEquals(0)
        compose.onAllNodesWithText("Vulkan").assertCountEquals(0)

        // The frame limit, changed in Essentials.
        compose.onNodeWithText(essentials).performClick()
        compose.onNodeWithText(Device.string(R.string.set_framerate_limit)).performClick()
        val label30 = fps.options.first { it.value == "30" }.label
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText(label30))
        compose.onNodeWithText(label30).performClick()
        compose.waitUntil(10_000) { vm.currentListValue(fps) == "30" }
        compose.onNodeWithContentDescription(Device.string(R.string.set_back_sections)).performClick()

        // Developer again: the same value under GPU; the advanced setting kept its own.
        val toDeveloper = Device.string(R.string.set_to_developer)
        waitForText(toDeveloper)
        compose.onNodeWithText(toDeveloper).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("GPU"))
        compose.onNodeWithText("GPU").performClick()
        compose.onNode(hasText(Device.string(R.string.set_framerate_limit)) and hasText(label30)).assertExists()
        vm.flush()
        assertEquals("30", live(fps))
        assertEquals("true", live(validation))
    }

    @Test fun aGamesSettingsFollowTheMode() {
        UiModeStore.write(context, UiMode.PLAYER)
        val vm = GameSettingsViewModel(GameSettingsRepository(ConfigStore(context), "4D5309C9"))
        compose.setContent { xendroidTheme { PerGameSettingsScreen(vm, gameName = "Roadmap", onBack = {}) } }
        waitForText(Device.string(R.string.set_cat_essentials))
        compose.onAllNodesWithText("GPU").assertCountEquals(0)
        compose.onAllNodesWithText("Vulkan").assertCountEquals(0)
    }

    @Test fun theInGameMenuInPlayerHasNoEngineOptions() {
        var state by mutableStateOf(InGameMenuState(open = true, developer = false, advanced = InGamePage.entries.toSet()))
        compose.setContent { xendroidTheme { Menu(state) } }
        val off = Device.string(R.string.menu_off)
        val developerOnly = mapOf(
            InGamePage.GRAPHICS to listOf(Device.string(R.string.menu_winfg, off), Device.string(R.string.menu_lsfg, off),
                Device.string(R.string.menu_lsfg_multiplier), Device.string(R.string.menu_import_lsfg),
                Device.string(R.string.menu_clear_lsfg)),
            InGamePage.SYSTEM to listOf(Device.string(R.string.menu_sustained), Device.string(R.string.menu_hints),
                Device.string(R.string.menu_hud_metric, Device.string(R.string.menu_metric_submissions), off)),
            InGamePage.SESSION to listOf(Device.string(R.string.menu_background)),
        )
        for (developer in listOf(false, true)) {
            for ((page, labels) in developerOnly) {
                state = state.copy(page = page, developer = developer)
                compose.waitForIdle()
                labels.forEach { label ->
                    compose.onAllNodesWithText(label).assertCountEquals(if (developer) 1 else 0)
                }
            }
        }
        // Player: Graphics keeps "More options" (stretch, color filter); Session has none.
        state = InGameMenuState(open = true, developer = false, page = InGamePage.GRAPHICS)
        compose.onNodeWithText(Device.string(R.string.menu_more_options, 2)).assertExists()
        state = InGameMenuState(open = true, developer = false, page = InGamePage.SESSION, advanced = setOf(InGamePage.SESSION))
        compose.waitForIdle()
        compose.onAllNodesWithText(Device.string(R.string.menu_fewer_options)).assertCountEquals(0)
    }
}

/** The in-game menu as the game process shows it, with a game's ordinary state. */
@Composable
internal fun Menu(state: InGameMenuState, sessionInfo: String = "") = InGameMenu(
    state = state, paused = false, fpsLimit = 60, fpsConfig = FpsConfigSnapshot("4D5309C9", 60, null),
    presentation = PresentationState(), fgPreset = 1, lsfgAvailable = true, extensionLabels = emptyMap(),
    performanceHud = false, compactHud = false, hudMetrics = emptySet(), touchControls = true, adaptiveSticks = false,
    stretch = false, volume = 100, sessionInfo = sessionInfo, phoneControllers = null, logSessions = emptyList(),
    onLogChoice = {}, onPage = {}, onSelect = {}, onAction = {}, onQuitChoice = {},
)
