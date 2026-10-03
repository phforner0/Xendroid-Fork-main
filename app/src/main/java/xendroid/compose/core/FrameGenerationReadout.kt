package xendroid.compose.core

import java.util.Locale

/**
 * Plain figures for experimental frame generation in the Graphics tab (developer builds):
 * before it is on, what it would do to this game at this refresh rate (more outputs than the
 * display refreshes are never shown, so turning it on caps the game, as GenerationCap does;
 * Bannerlator a6cdc8e0 warns about the same); while it runs, the guest's own frames against
 * the frames submitted with the generated ones. Submitted is not scanout, and the text says so.
 */
object FrameGenerationReadout {
    /** The same 2.5% slack as the governor's "more outputs than the display shows". */
    private const val SLACK = 1.025

    fun overDisplay(guestFps: Double, multiplier: Int, displayHz: Float): Boolean =
        displayHz > 0 && guestFps > 0 && guestFps * multiplier.coerceAtLeast(2) > displayHz * SLACK

    /** The guest cap frame generation brings ([GenerationCap.prepare]'s own rule). */
    fun cap(displayHz: Float, multiplier: Int): Int =
        (displayHz / multiplier.coerceAtLeast(2)).toInt().coerceAtLeast(1)

    /** "Win-FG 2×: 45 FPS × 2 = 90/s is over the 60 Hz display; …"; null without figures. */
    fun preview(engine: String, multiplier: Int, guestFps: Double, displayHz: Float): String? {
        if (guestFps <= 0 || displayHz <= 0) return null
        val m = multiplier.coerceAtLeast(2)
        val head = String.format(Locale.ROOT, "%s %d×: %.0f FPS × %d = %.0f/s", engine, m, guestFps, m, guestFps * m)
        if (!overDisplay(guestFps, m, displayHz)) {
            return head + String.format(Locale.ROOT, " fits the %.0f Hz display", displayHz)
        }
        val cap = cap(displayHz, m)
        return head + String.format(Locale.ROOT,
            " is over the %.0f Hz display; turning it on caps the game at %d FPS (base %d → %d frames/s submitted). " +
                "A higher refresh rate avoids the cap.", displayHz, cap, cap, cap * m)
    }

    /** While it runs: base → submitted, and a warning once the guest outruns the display. */
    fun running(baseFps: Double, generatedPerSecond: Double, multiplier: Int, displayHz: Float): List<String> {
        val lines = mutableListOf(String.format(Locale.ROOT,
            "Base %.0f FPS → %.0f frames/s submitted (%.0f generated; submitted, not proof of display)",
            baseFps, baseFps + generatedPerSecond, generatedPerSecond))
        val m = multiplier.coerceAtLeast(2)
        if (overDisplay(baseFps, m, displayHz)) {
            lines += String.format(Locale.ROOT,
                "Over the display: %.0f FPS × %d = %.0f/s above %.0f Hz; frames past the refresh are never shown " +
                    "and frame generation stops itself. Limit the game to %d FPS or raise the refresh rate.",
                baseFps, m, baseFps * m, displayHz, cap(displayHz, m))
        }
        return lines
    }
}
