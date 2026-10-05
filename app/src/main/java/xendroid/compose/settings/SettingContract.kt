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

/** U03: why the custom driver setting is unavailable, and what runs instead. */
const val UNSUPPORTED_DRIVER = "Unavailable: custom drivers load only on Adreno GPUs (Qualcomm KGSL), " +
    "and this phone has none. Games run on the system driver."

/** Disk editors never pretend that saving a cvar calls its live JNI setter. */
fun settingContract(setting: Setting, scope: String, customDrivers: Boolean): SettingContract {
    val action = setting.name == "dump_session_logs"
    val supported = setting.name != "vulkan_lib_path" || customDrivers
    return SettingContract(scope, if (action) SettingApply.IMMEDIATE_ACTION else SettingApply.NEXT_LAUNCH,
        when (setting.key) {
            "GPU|framerate_limit" -> LiveSetter.FPS_LIMIT
            "HID|show_touch_overlay" -> LiveSetter.TOUCH_OVERLAY
            else -> null
        }, supported, if (supported) "" else UNSUPPORTED_DRIVER)
}
