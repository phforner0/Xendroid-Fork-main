package xendroid.compose.core

/** Optional detailed HUD rows. Guest FPS/frame time stay visible in both presets. */
enum class HudMetric(val label: String) {
    HOST_SUBMISSIONS("Vulkan submissions"), CPU("CPU"), GPU("GPU"), RAM("RAM"),
    BATTERY_TEMPERATURE("Battery temperature"), SOC_TEMPERATURE("SoC temperature"),
    /** Watts from the battery and the time it would last at that rate ([BatteryReadout]). */
    POWER("Power and battery time"),
    /** 15g: what KGSL (the Adreno driver) has allocated, for every app ([KgslMemory]). */
    GPU_MEMORY("GPU memory"),
}
