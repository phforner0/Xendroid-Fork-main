package xendroid.compose.core

data class PresentationState(val displayMode: Int = -1, val requested: Boolean = false,
                             val state: Int = 0, val error: Int = 0, val generated: Long = 0,
                             val gpuMs: Double = -1.0, val dropped: Long = 0, val hz: Float = 60f, val engine: Int = 0,
                             val colorFilter: Int = 0, val colorError: Int = 0, val multiplier: Int = 2,
                             /** Synthetic outputs skipped because their display slot had passed. */
                             val lateSkips: Long = 0) {
    val label: String get() = (when (state) {
        0 -> "Win-FG: Off"
        1 -> "Win-FG: warming up"
        2 -> "Win-FG: active · requested ${multiplier}× · ${if (gpuMs >= 0) "%.2f ms GPU".format(gpuMs) else "GPU timing unavailable"}" +
            if (lateSkips > 0) " · $lateSkips late frames skipped" else ""
        3, 4 -> when (error) {
            1 -> "Win-FG unavailable: image formats/transfer features"
            2 -> "Win-FG unavailable: pipeline initialization failed"
            3 -> "Win-FG unavailable: image allocation failed"
            5 -> "Win-FG stopped: guest cadence exceeds display Hz"
            6 -> "Win-FG stopped: descriptor/UBO allocation failed"
            7 -> "Win-FG unavailable: LSFG cache missing or incompatible"
            8 -> "Win-FG unavailable: LSFG requires nullDescriptor and Vulkan memory model"
            9 -> "Win-FG waiting: native UI/surface owns presentation"
            else -> "Win-FG stopped: backend error"
        }
        else -> "Win-FG status unknown"
    }).let { if (engine == 1) it.replace("Win-FG", "LSFG Native") else it }

    /** [label] without the figures that change every frame (GPU time, skipped frames):
     *  it changes only when the presentation state itself does. */
    val stateLabel: String get() =
        if (state == 2) "${if (engine == 1) "LSFG Native" else "Win-FG"}: active · requested ${multiplier}×" else label

    companion object {
        fun decode(values: LongArray): PresentationState {
            require(values.size >= 8)
            return PresentationState(values[0].toInt(), values[1] != 0L, values[2].toInt(), values[3].toInt(),
                values[4], values[5] / 1_000_000.0, values[6], values[7] / 1000f, values.getOrElse(8) { 0 }.toInt(),
                values.getOrElse(9) { 0 }.toInt(), values.getOrElse(10) { 0 }.toInt(), values.getOrElse(11) { 2 }.toInt(),
                values.getOrElse(12) { 0 })
        }
    }
}
