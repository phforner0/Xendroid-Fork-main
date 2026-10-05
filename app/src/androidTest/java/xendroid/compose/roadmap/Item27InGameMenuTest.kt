package xendroid.compose.roadmap

import android.os.Build
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.R
import xendroid.compose.settings.UiMode
import xendroid.compose.settings.UiModeStore

/**
 * Roadmap item 27 (U01) and the in-game parts of 16 (Player) and 28 (pt-BR), in a running game.
 * The menu (Back) has the Graphics · System · Controls · Session tabs; Graphics says the driver the
 * game runs on; with a controller (key events), RB/LB change tabs, the D-pad reaches "More options
 * (N)" and A opens it as "Fewer options", which stays open after changing tabs and back; System
 * has the frame limit block. In Player, Graphics' "More options" holds two and Session has none,
 * with no Win-FG. With the app in Portuguese, the tabs, footer and exit question are in Portuguese.
 *
 * Left for the phone: a 4:3 or split screen, TalkBack's speech, a keyboard's Esc.
 */
@RunWith(AndroidJUnit4::class)
class Item27InGameMenuTest {
    private val context = Device.context

    @After fun tearDown() {
        UiModeStore.write(context, UiMode.DEVELOPER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) AppLanguage.set(null)
    }

    private fun startOf(id: Int, vararg args: Any): String = Device.string(id, *args).substringBefore("7").substringBefore("X")

    private fun inGame(game: File, check: (GameSession) -> Unit) = GameSession(game).use { session ->
        session.start()
        session.awaitFirstFrame()
        session.openMenu()
        check(session)
        session.closeMenu()
        session.exitByMenu()
    }

    @Test fun theMenuInARunningGame() {
        val game = GameSession.game()
        UiModeStore.write(context, UiMode.DEVELOPER)
        inGame(game) { s ->
            listOf(R.string.menu_tab_graphics, R.string.menu_tab_system, R.string.menu_tab_controls, R.string.menu_tab_session)
                .forEach { s.find(s.text(it)) }
            // Low in the Graphics tab: scrolled into view (it throws when the line is not there).
            s.findInMenu(By.textStartsWith(startOf(R.string.menu_driver, "X")))

            // A controller: RB to System (the frame limit block), LB back to Graphics.
            s.device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_R1)
            s.find(s.text(R.string.menu_frame_limit_title))
            assertTrue(s.device.hasObject(By.textStartsWith(startOf(R.string.menu_live_limit, "X"))))
            s.device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_L1)
            s.find(s.text(R.string.menu_tab_graphics))

            // The D-pad down to "More options (N)", A: "Fewer options", kept across tabs.
            val more = startOf(R.string.menu_more_options, 7)
            var reached = false
            repeat(30) {
                if (!reached) {
                    reached = s.device.findObject(By.textStartsWith(more))?.isSelected == true
                    if (!reached) s.device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
                }
            }
            assertTrue("the D-pad never reached \"$more…\"", reached)
            s.device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_A)
            val fewer = s.text(R.string.menu_fewer_options)
            s.find(fewer)
            s.device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_R1)
            s.device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_L1)
            s.find(fewer)
        }

        // Player (item 16): two advanced options in Graphics, none in Session, no frame generation.
        UiModeStore.write(context, UiMode.PLAYER)
        inGame(game) { s ->
            s.findInMenu(s.text(R.string.menu_more_options, 2))
            assertFalse(s.device.hasObject(By.textStartsWith("Win-FG")))
            s.page(R.string.menu_tab_session)
            assertFalse(s.device.wait(Until.hasObject(By.textStartsWith(startOf(R.string.menu_more_options, 7))), 3_000) == true)
        }

        // Portuguese (item 28): the app's own language set to pt-BR.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            UiModeStore.write(context, UiMode.DEVELOPER)
            AppLanguage.set("pt-BR")
            val pt = Device.resourcesIn("pt-BR")
            inGame(game) { s ->
                listOf(R.string.menu_tab_graphics, R.string.menu_tab_system, R.string.menu_tab_controls, R.string.menu_tab_session)
                    .forEach { s.find(pt.getString(it)) }
                s.find(pt.getString(R.string.menu_exit_game)).click()
                s.find(pt.getString(R.string.menu_exit_question))
                s.find(pt.getString(R.string.menu_cancel)).click()
                s.find(pt.getString(R.string.menu_continue))
            }
        }
    }
}
