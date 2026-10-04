package xendroid.compose.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.settings.PinnedSettings
import xendroid.compose.settings.SettingGroup
import xendroid.compose.settings.SettingLevel
import xendroid.compose.shots.Phone
import xendroid.compose.shots.app
import xendroid.compose.shots.shot
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.design.XdArea
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdSection
import xendroid.compose.ui.design.XdSectionedScreen
import xendroid.compose.ui.settings.MapSettingsEditing
import xendroid.compose.ui.settings.SettingsPanelState
import xendroid.compose.ui.settings.XdSettingsPanel
import xendroid.compose.ui.settings.groupIcon
import xendroid.compose.ui.settings.groupTitle

/** The settings rows and panel of the redesign, in both modes, with values in memory. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPanelShots {
    @get:Rule val compose = createComposeRule()

    private fun panel(mode: InputMode, name: String) {
        val editing = MapSettingsEditing(forGame = true,
            global = mutableMapOf("Display|postprocess_scaling_and_sharpening" to "fsr"),
            own = mutableMapOf("GPU|framerate_limit" to "30", "GPU|anisotropic_override" to "5"))
        compose.app(mode) {
            var tick by remember { mutableIntStateOf(0) }
            val state = remember { SettingsPanelState(SettingLevel.ADVANCED) }
            val sections = SettingGroup.entries.map { g ->
                XdSection("set:$g", groupTitle(g), groupIcon(g), group = "Ajustes deste jogo") {
                    tick.let {}
                    XdSettingsPanel(editing, state, PinnedSettings.DEFAULT, {}, group = g, gameName = "Halo 3")
                }
            }
            XdSectionedScreen("Halo 3", sections, "set:IMAGE", {}, area = XdArea.GAMES, subtitle = "4D5307E6 · Media 7A1C3E52",
                subtitleMono = true, onBack = {}, headIcon = XdIcons.gear)
        }
        compose.shot(name)
    }

    @Config(qualifiers = Phone.LAND) @Test fun touch() = panel(InputMode.TOUCH, "dev/settings-panel-b")
    @Config(qualifiers = Phone.LAND) @Test fun controller() = panel(InputMode.CONTROLLER, "dev/settings-panel-c")
    @Config(qualifiers = Phone.PORT) @Test fun touchPortrait() = panel(InputMode.TOUCH, "dev/settings-panel-b-port")
}
