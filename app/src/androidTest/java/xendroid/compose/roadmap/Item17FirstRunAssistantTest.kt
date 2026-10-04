package xendroid.compose.roadmap

import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
 * Roadmap item 17 (L01), in the steps of the redesign (lote 6): the first-run assistant over the
 * library of a fresh install (the test package with its first-run and mode choices cleared and
 * no game folder). Step 1 checks the phone (GPU by name, 64-bit ARM, Android) and says the game
 * folder is not chosen; step 2's button opens the folder flow (and the phone's back returns to
 * the same step); step 3 proposes the games' language and region from the phone's (pt-BR here)
 * and saves them to the console settings; step 4 opens Profiles and comes back; "Start" without
 * a mode leaves Player. Reopening the library does not show it again; ⋮ → "Setup assistant" does.
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

    /** A text inside the assistant's dialog (the library behind it may show the same words). */
    private fun inAssistant(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun next() = inAssistant(Device.string(R.string.xd_fr_continue)).performClick()

    private fun gone(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isEmpty()
    }

    @Test fun checksProposesSavesAndRunsOnce() {
        Device.grantAllFilesAccess()
        val welcome = Device.string(R.string.fr_welcome)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(welcome)
            EmulatorRuntime.gpuDeviceName?.let { inAssistant(it).assertExists() }
            inAssistant("arm64-v8a").assertExists()
            inAssistant(Device.string(R.string.fr_check_android, "${Build.VERSION.SDK_INT}")).assertExists()
            inAssistant(Device.string(R.string.fr_check_folder_unset)).assertExists()

            // The folder button opens the folder browser; the phone's back returns to the same step.
            next()
            val gamesNote = Device.string(R.string.xd_fr_games_note)
            waitFor(gamesNote)
            inAssistant(Device.string(R.string.fr_choose_folder)).performScrollTo().performClick()
            gone(gamesNote)
            UiDevice.getInstance(Device.instrumentation).pressBack()
            waitFor(gamesNote)

            // Language and region proposed from the phone (named in its language), saved for the games.
            next()
            val proposed = FirstRun.guestLocale("pt", "BR")
            val shown = Locale.getDefault()
            val language = Locale.forLanguageTag(proposed.languageLabel!!).getDisplayLanguage(shown).replaceFirstChar { it.titlecase(shown) }
            val region = Locale("", proposed.countryLabel!!).getDisplayCountry(shown)
            val from = listOf(Device.string(R.string.fr_language, language), Device.string(R.string.fr_region, region)).joinToString(" · ")
            waitFor(Device.string(R.string.fr_from_phone, from))
            inAssistant(Device.string(R.string.fr_use_locale)).performScrollTo().performClick()
            waitFor(Device.string(R.string.fr_locale_saved))
            assertEquals(proposed.languageValue, live("Console", "user_language"))
            assertEquals(proposed.countryValue, live("Console", "user_country"))

            // Profiles opens, and back comes to the assistant again, on the same step.
            next()
            val profileNote = Device.string(R.string.fr_profile_note)
            waitFor(profileNote)
            inAssistant(Device.string(R.string.fr_open_profiles)).performScrollTo().performClick()
            gone(profileNote)
            waitFor(Device.string(R.string.lib_menu_profiles))
            UiDevice.getInstance(Device.instrumentation).pressBack()
            waitFor(profileNote)

            // "Start" without choosing a mode: Player.
            next()
            inAssistant(Device.string(R.string.xd_fr_start)).performClick()
            gone(Device.string(R.string.xd_fr_start))
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
