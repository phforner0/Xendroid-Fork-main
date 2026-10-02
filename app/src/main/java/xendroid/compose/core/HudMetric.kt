package xendroid.compose.core

/** Optional detailed HUD rows. Guest FPS/frame time stay visible in both presets. */
enum class HudMetric(val label: String) {
    HOST_SUBMISSIONS("Vulkan submissions"), CPU("CPU"), GPU("GPU"), RAM("RAM"),
    BATTERY_TEMPERATURE("Battery temperature"), SOC_TEMPERATURE("SoC temperature"),
}
