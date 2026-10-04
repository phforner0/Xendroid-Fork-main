package xendroid.compose.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import xendroid.compose.R
import xendroid.compose.settings.AppLanguage
import xendroid.compose.settings.AppLanguageStore
import xendroid.compose.settings.SettingCatalog
import xendroid.compose.settings.SettingLevel
import xendroid.compose.settings.SettingLevelStore
import xendroid.compose.ui.design.InputModePref
import xendroid.compose.ui.design.InputModeStore
import xendroid.compose.ui.design.XdSegmented
import xendroid.compose.ui.design.XdSelect
import xendroid.compose.ui.design.XdSheetOption
import xendroid.compose.ui.design.XdStepper
import xendroid.compose.ui.theme.UiScale
import xendroid.compose.ui.theme.UiScaleStore
import xendroid.compose.ui.theme.UiScaling

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun percent(value: Float): String = NumberFormat.getPercentInstance().format(value.toDouble())

/** Touch or controller layout: automatic (a controller is connected), or always one of them. */
@Composable
fun InputModeOption() {
    val context = LocalContext.current
    var pref by remember { mutableStateOf(InputModeStore.read(context)) }
    XdSheetOption(stringResource(R.string.xd_app_input_mode), subtitle = stringResource(when (pref) {
        InputModePref.AUTO -> R.string.xd_app_input_auto_sub
        InputModePref.TOUCH -> R.string.xd_app_input_touch_sub
        InputModePref.CONTROLLER -> R.string.xd_app_input_controller_sub
    })) {
        XdSegmented(listOf(InputModePref.AUTO to stringResource(R.string.xd_app_input_auto),
            InputModePref.TOUCH to stringResource(R.string.xd_app_input_touch),
            InputModePref.CONTROLLER to stringResource(R.string.xd_app_input_controller)), pref,
            { pref = it; InputModeStore.write(context, it) })
    }
}

/** How many settings the lists show: Essential, Advanced, All. */
@Composable
fun SettingLevelOption(level: SettingLevel, onLevel: (SettingLevel) -> Unit) {
    val context = LocalContext.current
    val count = SettingCatalog.settings(level).size
    XdSettingLevelRow(level, count) { onLevel(it); SettingLevelStore.write(context, it) }
}

@Composable
private fun XdSettingLevelRow(level: SettingLevel, count: Int, onLevel: (SettingLevel) -> Unit) {
    XdSheetOption(stringResource(R.string.xd_app_level), subtitle = stringResource(when (level) {
        SettingLevel.ESSENTIAL -> R.string.xd_app_level_essential_sub
        SettingLevel.ADVANCED -> R.string.xd_app_level_advanced_sub
        SettingLevel.ALL -> R.string.xd_app_level_all_sub
    }, count)) {
        XdSegmented(SettingLevel.entries.map { it to levelTitle(it) }, level, onLevel)
    }
}

/** U04: which controller button confirms in the app's menus (never in the game). */
@Composable
fun MenuButtonsOption() {
    val context = LocalContext.current
    var swap by remember { mutableStateOf(xendroid.compose.gamepad.MenuButtonPrefs.swapConfirm(context)) }
    XdSheetOption(stringResource(R.string.xd_app_menus), subtitle = stringResource(R.string.set_menus_note)) {
        XdSegmented(listOf(false to stringResource(R.string.xd_app_menus_a), true to stringResource(R.string.xd_app_menus_b)), swap,
            { swap = it; xendroid.compose.gamepad.MenuButtonPrefs.setSwapConfirm(context, it) })
    }
}

/** 15l: how large the app draws its screens and its text. Saved at once; followed live. */
@Composable
fun UiScaleOptions() {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(UiScaleStore.read(context)) }
    fun change(next: UiScale) {
        if (next == scale) return
        scale = next
        UiScaleStore.write(context, next)
    }
    val configuration = LocalConfiguration.current
    val size = UiScaling.fittingSize(scale.size, minOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat())
    val text = UiScaling.fittingText(scale.text, configuration.fontScale)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        XdSheetOption(stringResource(R.string.xd_app_ui_size), subtitle = stringResource(R.string.ui_size_note) +
            if (size < scale.size) " " + stringResource(R.string.ui_size_limited, percent(size)) else "") {
            XdStepper(percent(scale.size), { change(scale.smaller()) }, { change(scale.larger()) },
                canPrevious = scale.size > UiScale.SIZES.first(), canNext = scale.size < UiScale.SIZES.last())
        }
        XdSheetOption(stringResource(R.string.xd_app_text_size), subtitle = stringResource(R.string.ui_text_size_note) +
            if (text < scale.text) " " + stringResource(R.string.ui_text_size_limited, percent(text)) else "") {
            XdStepper(percent(scale.text), { change(scale.smallerText()) }, { change(scale.largerText()) },
                canPrevious = scale.text > UiScale.TEXTS.first(), canNext = scale.text < UiScale.TEXTS.last())
        }
    }
}

/** 15l: the app's language, from the phone's or one the app has. */
@Composable
fun AppLanguageOption() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppLanguageStore.current(context)) }
    val system = stringResource(R.string.app_language_system)
    XdSheetOption(stringResource(R.string.app_language_title), subtitle = stringResource(R.string.app_language_note)) {
        XdSelect(AppLanguage.entries.map { it to (it.autonym ?: system) }, current, { language ->
            if (language != current) {
                current = language
                context.activity()?.let { AppLanguageStore.set(it, language) }
            }
        })
    }
}
