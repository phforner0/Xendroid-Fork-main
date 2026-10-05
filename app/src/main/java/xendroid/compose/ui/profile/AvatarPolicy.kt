package xendroid.compose.ui.profile

/** Bounds an avatar image BEFORE any pixel buffer is allocated (JVM-testable). */
object AvatarPolicy {
    const val MAX_INPUT_BYTES = 32L * 1024 * 1024
    const val MAX_SIDE = 16_384
    /** Decoded size kept for the center crop: well above the 64 px tile. */
    const val DECODE_TARGET = 256

    fun validate(width: Int, height: Int) {
        require(width > 0 && height > 0) { "The file is not a supported image" }
        require(width <= MAX_SIDE && height <= MAX_SIDE) {
            "The image is too large (${width}×$height; limit $MAX_SIDE px per side)"
        }
    }

    /** Largest power-of-two subsampling that keeps the shorter side >= [target]. */
    fun sampleSize(width: Int, height: Int, target: Int = DECODE_TARGET): Int {
        validate(width, height)
        val shorter = minOf(width, height)
        var sample = 1
        while (shorter / (sample * 2) >= target) sample *= 2
        return sample
    }
}
