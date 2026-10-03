package xendroid.compose.roadmap

import android.content.res.Resources
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.MainActivity
import xendroid.compose.R
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.SettingsRepository
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.settings.SettingsViewModel
import xendroid.compose.settings.UiMode
import xendroid.compose.settings.UiModeStore
import xendroid.compose.ui.ingame.InGameMenuHandle
import xendroid.compose.ui.ingame.InGameMenuState
import xendroid.compose.ui.ingame.InGamePage
import xendroid.compose.ui.library.FirstRunStore
import xendroid.compose.ui.settings.SettingsScreen
import xendroid.compose.ui.theme.xendroidTheme

/**
 * Roadmap item 28 (U02), the screen part: the app set to Português (Brasil) the way Android 13+
 * does it (Settings → Apps → XenDroid → Language). The in-game menu in Portuguese on every tab,
 * nothing cut, the "☰" read as a button named "Abrir menu", the highlighted option read as
 * selected, the footer still on screen at the largest font on a small screen, and English when
 * switched back. The library's ⋮ menu and every screen it opens show their Portuguese title with
 * nothing cut; Settings in Portuguese, "1 ajuste"/"N ajustes", and searching "tela" finds
 * "Tela larga". Every expected text is read from the pt-BR resources, so a text left in
 * English fails here.
 *
 * Left for the phone: the menu inside a running game (group C) and TalkBack's own speech.
 */
@RunWith(AndroidJUnit4::class)
class Item28PortugueseTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = Device.context
    private val pt: Resources = Device.resourcesIn("pt-BR")
    private val en: Resources = Device.resourcesIn("en")

    @Before fun setUp() {
        assumeTrue("the app's own language needs Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Device.requireTestPackage()
        AppLanguage.set("pt-BR")
    }

    @After fun tearDown() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) AppLanguage.set(null)
        UiModeStore.write(context, UiMode.DEVELOPER)
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit): ActivityScenario<ComponentActivity> =
        ActivityScenario.launch(ComponentActivity::class.java).also { scenario ->
            scenario.onActivity { it.setContent { xendroidTheme { content() } } }
        }

    private fun waitFor(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun assertNothingCut(where: String) = assertEquals("cut texts in $where", emptyList<String>(), compose.cutTexts())

    @Test fun theInGameMenuInPortuguese() {
        var state by mutableStateOf(InGameMenuState(open = true, developer = true))
        show { Menu(state) }.use {
            val tabs = listOf(R.string.menu_tab_graphics, R.string.menu_tab_system, R.string.menu_tab_controls, R.string.menu_tab_session)
            tabs.forEach { tab ->
                assertNotEquals(en.getString(tab), pt.getString(tab))
                compose.onNodeWithText(pt.getString(tab)).assertExists()
            }
            for (page in InGamePage.entries) {
                state = state.copy(page = page, advanced = InGamePage.entries.toSet())
                compose.waitForIdle()
                assertNothingCut("the $page tab")
            }
            state = InGameMenuState(open = true, developer = false)
            compose.onNodeWithText(pt.getString(R.string.menu_more_options, 2)).assertExists()
            // The option the controller highlights is announced as selected.
            state = state.select(0)
            compose.onNodeWithText(pt.getString(R.string.menu_display_fit), substring = true).assertIsSelected()
            state = state.askToQuit()
            compose.onNodeWithText(pt.getString(R.string.menu_exit_question)).assertExists()
            compose.onNodeWithText(pt.getString(R.string.menu_cancel)).assertIsSelected()
        }
        AppLanguage.set("en")
        show { Menu(InGameMenuState(open = true)) }.use {
            compose.onNodeWithText(en.getString(R.string.menu_tab_graphics)).assertExists()
            compose.onNodeWithText(en.getString(R.string.menu_continue)).assertExists()
        }
    }

    @Test fun theMenuHandleIsAButtonNamedOpenMenu() {
        show { InGameMenuHandle(onOpen = {}) }.use {
            compose.onNodeWithContentDescription(pt.getString(R.string.menu_open))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }

    @Test fun theFooterStaysWithTheLargestFontOnASmallScreen() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                Box(Modifier.size(width = 360.dp, height = 270.dp)) { Menu(InGameMenuState(open = true, page = InGamePage.SYSTEM)) }
            }
        }.use {
            compose.onNodeWithText(pt.getString(R.string.menu_continue)).assertIsDisplayed()
            compose.onNodeWithText(pt.getString(R.string.menu_exit_game)).assertIsDisplayed()
        }
    }

    @Test fun theLibraryMenuAndEveryScreenItOpens() {
        FirstRunStore.markDone(context)
        EmulatorRuntime.ensureLoaded()
        ActivityScenario.launch(MainActivity::class.java).use {
            val library = pt.getString(R.string.lib_title)
            waitFor(library)
            assertNothingCut("the library")
            val more = pt.getString(R.string.lib_more)
            compose.onNodeWithContentDescription(more).performClick()
            listOf(R.string.lib_menu_add_folder, R.string.lib_menu_folders,
                R.string.lib_menu_install_content, R.string.lib_menu_profiles, R.string.lib_menu_diagnostics,
                R.string.lib_menu_keymap, R.string.lib_menu_touch, R.string.lib_menu_phone_controller,
                R.string.lib_menu_test_controllers, R.string.lib_menu_compare_runs, R.string.lib_menu_setup,
                R.string.lib_menu_user_data, R.string.lib_menu_about, R.string.lib_menu_updates,
            ).forEach { compose.onNodeWithText(pt.getString(it)).performScrollTo().assertExists() }
            assertNothingCut("the ⋮ menu")
            UiDevice.getInstance(Device.instrumentation).pressBack()

            for (screen in listOf(R.string.lib_menu_profiles, R.string.lib_menu_diagnostics, R.string.lib_menu_keymap,
                    R.string.lib_menu_phone_controller, R.string.lib_menu_test_controllers, R.string.lib_menu_compare_runs,
                    R.string.lib_menu_about)) {
                val title = pt.getString(screen)
                compose.onNodeWithContentDescription(more).performClick()
                compose.onNodeWithText(title).performScrollTo().performClick()
                compose.waitUntil(20_000) { compose.onAllNodesWithText(library).fetchSemanticsNodes().isEmpty() }
                waitFor(title)
                assertNothingCut(title)
                UiDevice.getInstance(Device.instrumentation).pressBack()
                waitFor(library)
            }
        }
    }

    @Test fun settingsInPortugueseAndTheSearchFindsTranslatedNames() {
        EmulatorRuntime.ensureLoaded()
        UiModeStore.write(context, UiMode.DEVELOPER)
        val vm = SettingsViewModel(SettingsRepository(ConfigStore(context)))
        show { SettingsScreen(vm, onBack = {}) }.use {
            waitFor(pt.getString(R.string.lib_settings))
            // Section counts in Portuguese, singular and plural.
            val many = SettingsSchema.categories.first { it.settings.size > 1 }.settings.size
            assumeTrue(SettingsSchema.categories.any { it.settings.size == 1 })
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(pt.getQuantityString(R.plurals.set_count, 1, 1), substring = true))
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(pt.getQuantityString(R.plurals.set_count, many, many), substring = true))
            assertNothingCut("Settings")

            compose.onNodeWithText(pt.getString(R.string.set_to_player)).performClick()
            val essentials = pt.getString(R.string.set_cat_essentials)
            compose.onNodeWithText(pt.getQuantityString(R.plurals.set_count, 22, 22), substring = true).assertExists()
            compose.onNodeWithText(essentials).performClick()
            compose.onNode(hasSetTextAction()).performTextInput("tela")
            compose.onNodeWithText(pt.getString(R.string.set_widescreen)).assertExists()
            assertNothingCut("the search")
        }
    }
}
