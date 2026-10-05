package xendroid.compose.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.roundToInt
import xendroid.compose.R
import xendroid.compose.gamepad.ControlStyle
import xendroid.compose.gamepad.GamepadGlobalsDto
import xendroid.compose.gamepad.SplitScreenMode
import xendroid.compose.gamepad.TouchCamera
import xendroid.compose.gamepad.splitScreenLabel
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdGroupHeader
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSegmented
import xendroid.compose.ui.design.XdStepper
import xendroid.compose.ui.design.XdSwitch
import xendroid.compose.ui.design.XdText

internal fun percent(value: Float): String = NumberFormat.getPercentInstance().format(value.toDouble())

/**
 * An app option: title and explanation, the control at the end; with [stack] a wide control
 * goes under the text when the column is narrow (portrait).
 */
@Composable
internal fun OptionRow(title: String, desc: String?, modifier: Modifier = Modifier, stack: Boolean = false, control: @Composable () -> Unit) {
    val c = Xd.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val below = stack && maxWidth < 560.dp
        val shape = RoundedCornerShape(12.dp)
        val text: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Text(title, style = XdText.label, color = c.fg)
                if (desc != null) Text(desc, style = XdText.small, color = c.fg3, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (below) Column(Modifier.fillMaxWidth().clip(shape).background(c.s1).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            text(Modifier)
            control()
        } else Row(Modifier.fillMaxWidth().clip(shape).background(c.s1).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            text(Modifier.weight(1f))
            control()
        }
    }
}

/**
 * The touch controls' options for the whole pad, by group: general, split screen, sliding the
 * finger, touch camera. [globals] are the layout file's; the Controls area also shows the
 * switch of the touch controls ([overlay], the config's "Show on-screen controller") and the
 * touch camera ([camera], an option every game starts with).
 */
@Composable
fun TouchOptionRows(
    globals: GamepadGlobalsDto,
    onGlobals: ((GamepadGlobalsDto) -> GamepadGlobalsDto) -> Unit,
    overlay: Boolean? = null,
    onOverlay: ((Boolean) -> Unit)? = null,
    camera: Boolean? = null,
    onCamera: ((Boolean) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        XdGroupHeader(stringResource(R.string.xd_tg_general), modifier = Modifier.padding(top = 0.dp))
        if (overlay != null && onOverlay != null) {
            OptionRow(stringResource(R.string.xd_ctl_touch_title), stringResource(R.string.xd_tg_overlay_desc)) {
                XdSwitch(overlay, onOverlay)
            }
            if (!globals.enabled) XdNote(stringResource(R.string.xd_tg_layout_off), tone = NoteTone.INFO)
        } else if (!globals.enabled) {
            // The layout's own switch of older builds: shown only while it is off, to turn it back on.
            OptionRow(stringResource(R.string.ge_enabled), null) {
                XdSwitch(false, { on -> onGlobals { it.copy(enabled = on) } })
            }
        }
        // Round 2: the controls' look, modern (dark glass) or classic (coloured buttons).
        OptionRow(stringResource(R.string.xd_tg_style), stringResource(R.string.xd_tg_style_desc), stack = true) {
            XdSegmented(ControlStyle.entries.map { it to stringResource(if (it == ControlStyle.MODERN) R.string.menu_opt_modern else R.string.menu_opt_classic) },
                ControlStyle.parse(globals.style), { style -> onGlobals { it.copy(style = style.key) } })
        }
        val opacity = (globals.opacity * 100).roundToInt()
        OptionRow(stringResource(R.string.xd_tg_opacity), stringResource(R.string.xd_tg_opacity_desc)) {
            XdStepper(percent(opacity / 100f),
                { onGlobals { it.copy(opacity = ((opacity - 5).coerceIn(20, 100)) / 100f) } },
                { onGlobals { it.copy(opacity = ((opacity + 5).coerceIn(20, 100)) / 100f) } },
                canPrevious = opacity > 20, canNext = opacity < 100)
        }
        val hide = globals.autoHideSeconds.roundToInt()
        OptionRow(stringResource(R.string.xd_tg_autohide), stringResource(R.string.xd_tg_autohide_desc)) {
            XdStepper(if (hide <= 0) stringResource(R.string.xd_off) else stringResource(R.string.xd_tg_seconds, hide),
                { onGlobals { it.copy(autoHideSeconds = (hide - 1).coerceIn(0, 20).toFloat()) } },
                { onGlobals { it.copy(autoHideSeconds = (hide + 1).coerceIn(0, 20).toFloat()) } },
                canPrevious = hide > 0, canNext = hide < 20, minLabelWidth = 84.dp)
        }
        OptionRow(stringResource(R.string.ge_haptics), stringResource(R.string.xd_tg_haptics_desc)) {
            XdSwitch(globals.hapticsEnabled, { on -> onGlobals { it.copy(hapticsEnabled = on) } })
        }
        OptionRow(stringResource(R.string.ge_hide_with_controller), stringResource(R.string.ge_hide_with_controller_desc)) {
            XdSwitch(globals.hideWithController, { on -> onGlobals { it.copy(hideWithController = on) } })
        }

        XdGroupHeader(stringResource(R.string.ge_split))
        OptionRow(stringResource(R.string.ge_split), stringResource(R.string.ge_split_desc), stack = true) {
            XdSegmented(SplitScreenMode.entries.map { it to stringResource(splitScreenLabel(it)) }, SplitScreenMode.parse(globals.splitScreen),
                { mode -> onGlobals { it.copy(splitScreen = mode.key) } })
        }

        XdGroupHeader(stringResource(R.string.xd_tg_slide))
        OptionRow(stringResource(R.string.ge_slide_buttons), stringResource(R.string.xd_tg_slide_buttons_desc)) {
            XdSwitch(globals.slideButtons, { on -> onGlobals { it.copy(slideButtons = on) } })
        }
        OptionRow(stringResource(R.string.ge_slide_sticks), stringResource(R.string.xd_tg_slide_sticks_desc)) {
            XdSwitch(globals.slideSticks, { on -> onGlobals { it.copy(slideSticks = on) } })
        }

        XdGroupHeader(stringResource(R.string.xd_tg_camera))
        if (camera != null && onCamera != null) {
            OptionRow(stringResource(R.string.xd_tg_camera), stringResource(R.string.xd_tg_camera_desc)) { XdSwitch(camera, onCamera) }
        }
        val speed = (globals.cameraSensitivity.coerceIn(0.5f, 2f) * 100).roundToInt()
        OptionRow(stringResource(R.string.xd_tg_camera_speed), stringResource(R.string.xd_tg_camera_speed_desc)) {
            XdStepper(percent(speed / 100f),
                { onGlobals { it.copy(cameraSensitivity = ((speed - 10).coerceIn(50, 200)) / 100f) } },
                { onGlobals { it.copy(cameraSensitivity = ((speed + 10).coerceIn(50, 200)) / 100f) } },
                canPrevious = speed > 50, canNext = speed < 200)
        }
        val area = ((1f - TouchCamera.areaStart(globals.cameraAreaStart)) * 100).roundToInt()
        OptionRow(stringResource(R.string.xd_tg_camera_area), stringResource(R.string.xd_tg_camera_area_desc)) {
            XdStepper(percent(area / 100f),
                { onGlobals { it.copy(cameraAreaStart = 1f - ((area - 5).coerceIn(30, 70)) / 100f) } },
                { onGlobals { it.copy(cameraAreaStart = 1f - ((area + 5).coerceIn(30, 70)) / 100f) } },
                canPrevious = area > 30, canNext = area < 70)
        }
    }
}
