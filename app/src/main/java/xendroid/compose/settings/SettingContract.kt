package xendroid.compose.settings

enum class SettingApply { NEXT_LAUNCH, IMMEDIATE_ACTION }
enum class LiveSetter { FPS_LIMIT, TOUCH_OVERLAY }
data class SettingContract(val scope: String, val apply: SettingApply,
                           val liveSetter: LiveSetter?, val available: Boolean, val reason: String) {
    val label: String get() = when {
        !available -> reason
        apply == SettingApply.IMMEDIATE_ACTION -> "Action · does not change emulation configuration"
        else -> "Saved $scope · applies next game launch"
    }
}

/** Disk editors never pretend that saving a cvar calls its live JNI setter. */
fun settingContract(setting: Setting, scope: String, customDrivers: Boolean): SettingContract {
    val action = setting.name == "dump_session_logs"
    val supported = setting.name != "vulkan_lib_path" || customDrivers
    return SettingContract(scope, if (action) SettingApply.IMMEDIATE_ACTION else SettingApply.NEXT_LAUNCH,
        when (setting.key) {
            "GPU|framerate_limit" -> LiveSetter.FPS_LIMIT
            "HID|show_touch_overlay" -> LiveSetter.TOUCH_OVERLAY
            else -> null
        }, supported, if (supported) "" else "Custom driver loading is unavailable on this device")
}
