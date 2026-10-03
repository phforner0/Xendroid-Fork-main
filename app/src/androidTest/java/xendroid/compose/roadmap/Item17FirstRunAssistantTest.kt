package xendroid.compose.roadmap

import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.MainActivity
import xendroid.compose.R
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.data.PreferencesStore
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.UiMode
import xendroid.compose.settings.UiModeStore
import xendroid.compose.ui.library.FirstRun
import xendroid.compose.ui.library.FirstRunStore

/**
 * Roadmap item 17 (L01): the first-run assistant over the library of a fresh install (the test
 * package with its first-run and mode choices cleared and no game folder). It checks the phone
 * (GPU by name, 64-bit ARM, Android), shows "!" for the missing game folder with the button
 * that opens the folder flow (and the phone's back returns to it), proposes the games' language
 * and region from the phone's (pt-BR here) and saves them to the console settings, opens
 * Profiles and comes back, and "Done" without a mode leaves Player. Reopening the library does
 * not show it again; ⋮ → "Setup assistant" does.
 */
@RunWith(AndroidJUnit4::class)
class Item17FirstRunAssistantTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = Device.context
    private lateinit var global: File
    private var savedConfig: ByteArray? = null
    private var savedFolders: List<String> = emptyList()
    private val phoneLocale: Locale = Locale.getDefault()

    @Before fun setUp() {
        Device.requireTestPackage()
        EmulatorRuntime.ensureLoaded()
        global = ConfigStore(context).globalConfigFile()
        savedConfig = global.takeIf { it.isFile }?.readBytes()
        // A fresh install: no assistant run yet, no mode chosen, no game folder.
        context.getSharedPreferences("first_run", 0).edit().clear().commit()
        context.getSharedPreferences("ui_mode", 0).edit().clear().commit()
        val prefs = PreferencesStore(context)
        savedFolders = runBlocking { prefs.gameDirPaths.first() }
        runBlocking { savedFolders.forEach { prefs.removeGameDirPath(it) } }
        // The phone in Brazilian Portuguese, for what the assistant proposes.
        Locale.setDefault(Locale("pt", "BR"))
    }

    @After fun tearDown() {
        Locale.setDefault(phoneLocale)
        savedConfig?.let { global.writeBytes(it) }
        runBlocking { savedFolders.forEach { PreferencesStore(context).addGameDirPath(it) } }
        FirstRunStore.markDone(context)
        UiModeStore.write(context, UiMode.DEVELOPER)
    }

    private fun waitFor(text: String) = compose.waitUntil(30_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun live(section: String, name: String): String? = ConfigStore(context).openLiveSnapshot().let { h ->
        try { h.getString(section, name) } finally { h.closeDiscard() }
    }

    @Test fun checksProposesSavesAndRunsOnce() {
        Device.grantAllFilesAccess()
        val welcome = Device.string(R.string.fr_welcome)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(welcome)
            EmulatorRuntime.gpuDeviceName?.let { compose.onNodeWithText(it).assertExists() }
            compose.onNodeWithText("arm64-v8a").assertExists()
            compose.onNodeWithText(Device.string(R.string.fr_check_android, "${Build.VERSION.SDK_INT}")).assertExists()
            compose.onAllNodesWithText("✓").assertCountEquals(3)
            compose.onNodeWithText(Device.string(R.string.fr_check_folder_unset)).assertExists()
            compose.onNodeWithText("!").assertExists()

            // The folder button opens the folder browser; the phone's back returns to the assistant.
            compose.onNodeWithText(Device.string(R.string.fr_choose_folder)).performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(welcome).fetchSemanticsNodes().isEmpty() }
            UiDevice.getInstance(Device.instrumentation).pressBack()
            waitFor(welcome)

            // Language and region proposed from the phone, saved for the games.
            val proposed = FirstRun.guestLocale("pt", "BR")
            val from = listOf(Device.string(R.string.fr_language, proposed.languageLabel!!),
                Device.string(R.string.fr_region, proposed.countryLabel!!)).joinToString(" · ")
            compose.onNodeWithText(Device.string(R.string.fr_from_phone, from)).assertExists()
            compose.onNodeWithText(Device.string(R.string.fr_use_locale)).performScrollTo().performClick()
            waitFor(Device.string(R.string.fr_locale_saved))
            assertEquals(proposed.languageValue, live("Console", "user_language"))
            assertEquals(proposed.countryValue, live("Console", "user_country"))

            // Profiles opens, and back comes to the assistant again.
            compose.onNodeWithText(Device.string(R.string.fr_open_profiles)).performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(welcome).fetchSemanticsNodes().isEmpty() }
            waitFor(Device.string(R.string.lib_menu_profiles))
            UiDevice.getInstance(Device.instrumentation).pressBack()
            waitFor(welcome)

            // Done without choosing a mode: Player.
            compose.onNodeWithText(Device.string(R.string.common_done)).performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(welcome).fetchSemanticsNodes().isEmpty() }
            assertEquals(UiMode.PLAYER, UiModeStore.read(context))
            assertTrue(FirstRunStore.done(context))
        }
        // The library again: no assistant; the menu reopens it.
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(Device.string(R.string.lib_title))
            compose.onAllNodesWithText(welcome).assertCountEquals(0)
            compose.onNodeWithContentDescription(Device.string(R.string.lib_more)).performClick()
            compose.onNodeWithText(Device.string(R.string.lib_menu_setup)).performClick()
            waitFor(welcome)
        }
    }
}
