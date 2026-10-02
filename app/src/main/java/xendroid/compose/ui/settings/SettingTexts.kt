package xendroid.compose.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import xendroid.compose.R
import xendroid.compose.settings.Setting
import xendroid.compose.settings.SettingApply
import xendroid.compose.settings.SettingContract
import xendroid.compose.settings.SettingsCategory

/**
 * U02: the shown names of what players see in Settings: the Player-mode settings (title and
 * description), the category names and the save/apply note. The engine's own settings, shown
 * only in Developer mode, keep their English names, like the cvars they are.
 */
private val TITLES: Map<String, Int> = mapOf(
    "GPU|framerate_limit" to R.string.set_framerate_limit,
    "GPU|guest_display_refresh_cap" to R.string.set_refresh_cap,
    "GPU|draw_resolution_scale_x" to R.string.set_scale_x,
    "GPU|draw_resolution_scale_y" to R.string.set_scale_y,
    "Display|postprocess_scaling_and_sharpening" to R.string.set_scaling,
    "Display|postprocess_antialiasing" to R.string.set_antialiasing,
    "Display|present_letterbox" to R.string.set_letterbox,
    "Console|widescreen" to R.string.set_widescreen,
    "Console|internal_display_resolution" to R.string.set_display_mode,
    "Vulkan|vulkan_lib_path" to R.string.set_driver,
    "HID|show_touch_overlay" to R.string.set_touch_overlay,
    "UI|android_soft_keyboard" to R.string.set_soft_keyboard,
    "UI|android_message_box" to R.string.set_message_box,
    "UI|show_achievement_notification" to R.string.set_achievements,
    "Console|user_language" to R.string.set_language,
    "Console|user_country" to R.string.set_country,
    "APU|mute" to R.string.set_mute,
    "General|apply_patches" to R.string.set_apply_patches,
    "Logging|dump_session_logs" to R.string.set_export_logs,
)

private val DESCRIPTIONS: Map<String, Int> = mapOf(
    "GPU|framerate_limit" to R.string.set_framerate_limit_desc,
    "GPU|guest_display_refresh_cap" to R.string.set_refresh_cap_desc,
    "GPU|draw_resolution_scale_x" to R.string.set_scale_x_desc,
    "GPU|draw_resolution_scale_y" to R.string.set_scale_y_desc,
    "Display|postprocess_scaling_and_sharpening" to R.string.set_scaling_desc,
    "Display|postprocess_antialiasing" to R.string.set_antialiasing_desc,
    "Display|present_letterbox" to R.string.set_letterbox_desc,
    "Console|widescreen" to R.string.set_widescreen_desc,
    "Console|internal_display_resolution" to R.string.set_display_mode_desc,
    "Vulkan|vulkan_lib_path" to R.string.set_driver_desc,
    "HID|show_touch_overlay" to R.string.set_touch_overlay_desc,
    "UI|android_soft_keyboard" to R.string.set_soft_keyboard_desc,
    "UI|android_message_box" to R.string.set_message_box_desc,
    "UI|show_achievement_notification" to R.string.set_achievements_desc,
    "Console|user_language" to R.string.set_language_desc,
    "Console|user_country" to R.string.set_country_desc,
    "APU|mute" to R.string.set_mute_desc,
    "General|apply_patches" to R.string.set_apply_patches_desc,
    "Logging|dump_session_logs" to R.string.set_export_logs_desc,
)

private val CATEGORIES: Map<String, Int> = mapOf(
    "Essentials" to R.string.set_cat_essentials, "Video" to R.string.set_cat_video, "Storage" to R.string.set_cat_storage,
    "Controller" to R.string.set_cat_controller, "Memory" to R.string.set_cat_memory, "Display" to R.string.set_cat_display,
    "Logging" to R.string.set_cat_logging, "Content" to R.string.set_cat_content, "General" to R.string.set_cat_general,
)

@Composable
fun settingTitle(s: Setting): String = TITLES[s.key]?.let { stringResource(it) } ?: s.title

@Composable
fun settingDesc(s: Setting): String = DESCRIPTIONS[s.key]?.let { stringResource(it) } ?: s.desc

/** For search: the shown title and description, outside composition. */
fun settingSearchText(context: Context, s: Setting): String =
    listOfNotNull(TITLES[s.key]?.let(context::getString), DESCRIPTIONS[s.key]?.let(context::getString)).joinToString(" ")

@Composable
fun categoryTitle(category: SettingsCategory): String = CATEGORIES[category.title]?.let { stringResource(it) } ?: category.title

/** A list option as shown; only words are translated (sizes, codes and filter names stay). */
@Composable
fun optionLabel(s: Setting.ListChoice, value: String, english: String): String =
    if (s.key == "GPU|framerate_limit" && value == "0") stringResource(R.string.set_fps_unlimited) else english

/** U03: what saving the setting does, in the shown language. */
@Composable
fun contractText(contract: SettingContract): String = when {
    !contract.available -> stringResource(R.string.set_driver_unavailable)
    contract.apply == SettingApply.IMMEDIATE_ACTION -> stringResource(R.string.set_contract_action)
    contract.scope == "globally" -> stringResource(R.string.set_contract_global)
    else -> stringResource(R.string.set_contract_game)
}
