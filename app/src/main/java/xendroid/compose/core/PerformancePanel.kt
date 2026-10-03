package xendroid.compose.core

import xendroid.compose.sessions.RunPerformance

/** 15n: how much the HUD shows: FPS only, the rows chosen in the menu, or the whole panel. */
enum class HudDetail(val key: String) {
    COMPACT("compact"), FULL("full"), PANEL("panel");

    /** The menu's cycle: full → panel → compact → full. */
    fun next(): HudDetail = when (this) {
        FULL -> PANEL
        PANEL -> COMPACT
        COMPACT -> FULL
    }

    companion object {
        /** The saved detail; before 15n only "compact" (true or false) was kept. */
        fun parse(key: String?, legacyCompact: Boolean): HudDetail =
            entries.firstOrNull { it.key == key } ?: if (legacyCompact) COMPACT else FULL
    }
}

/**
 * 15n (Bannerlator `83d0c1b9`): the performance panel, the HUD's third level. Over every live
 * figure it adds how even the frames were (the last seconds and the whole run), what made the
 * game stutter (pipeline creation, late audio), the phone's heat and the settings in effect.
 * This class keeps the last seconds of the core's frame-time counts; everything else comes in
 * as plain values once per second ([Snapshot]). Pure, tested on the JVM.
 */
class PerformancePanel(private val windowSeconds: Int = 10) {
    /**
     * Frame times as upper bounds of the core's 1 ms buckets: half the frames took under
     * [medianUnderMs], 99 in 100 under [p99UnderMs].
     */
    data class Pacing(val medianUnderMs: Int, val p99UnderMs: Int, val frames: Long) {
        /** The slowest frames took more than twice the typical one: stutter that shows. */
        val stutters: Boolean get() = p99UnderMs > 2 * medianUnderMs
    }

    /** What the activity knows about the settings in effect, shown as "label: value". */
    enum class Setting { DRIVER, SCALING, FRAME_GENERATION, FPS_LIMIT, PERFORMANCE_MODE }

    data class Snapshot(
        val recentSeconds: Int = 0,
        val recent: Pacing? = null,
        val run: Pacing? = null,
        /** The run's 1-second guest FPS: median and the 5th percentile ("low"). */
        val fpsMedian: Int? = null,
        val fpsLow: Int? = null,
        val pipelines: Long? = null,
        val pipelineMs: Long? = null,
        val audioConcealed: Long? = null,
        val audioBlocks: Long? = null,
        val thermal: ThermalWatch.Level = ThermalWatch.Level.OK,
        val headroom: Float? = null,
        val settings: List<Pair<Setting, String>> = emptyList(),
        val changed: List<String> = emptyList(),
        val changedTotal: Int = 0,
    )

    /** The core's cumulative counts as of each second, oldest first. */
    private val history = ArrayDeque<LongArray>()

    /** The core's cumulative frame-time counts, once per second (null while it has none). */
    fun frameTimes(counts: LongArray?) {
        if (counts == null || counts.isEmpty()) return
        if (history.isNotEmpty() && history.last().size != counts.size) history.clear()
        history.addLast(counts.copyOf())
        while (history.size > windowSeconds + 1) history.removeFirst()
    }

    /** How many seconds [recent] covers: up to [windowSeconds], fewer right after the start. */
    val recentSeconds: Int get() = (history.size - 1).coerceAtLeast(0)

    /** Frame times over the last [recentSeconds]; null under [MIN_FRAMES] frames (paused, loading). */
    fun recent(): Pacing? {
        if (history.size < 2) return null
        val newest = history.last()
        val oldest = history.first()
        return pacing(newest.indices.map { (newest[it] - oldest[it]).coerceAtLeast(0) })
    }

    companion object {
        /** Fewer frames than this say nothing about pacing. */
        const val MIN_FRAMES = 30L

        /** Bounds for a frame-time histogram (index = whole ms, as in [RunPerformance]). */
        fun pacing(histogram: List<Long>): Pacing? {
            val perf = RunPerformance(frameTimeHistogramMs = histogram)
            if (perf.frames < MIN_FRAMES) return null
            val median = perf.frameTimeUpperMs(0.5) ?: return null
            val p99 = perf.frameTimeUpperMs(0.99) ?: return null
            return Pacing(median, p99, perf.frames)
        }

        /** "<17 ms", or "≥250 ms" for the core's open last bucket. */
        fun bound(ms: Int): String = if (ms >= RunPerformance.FRAME_TIME_OPEN_BUCKET) "≥$ms ms" else "<$ms ms"

        /** Categories whose settings change how fast a game runs; their lines come first. */
        private val PERFORMANCE_CATEGORIES = listOf("GPU", "Vulkan", "Display", "CPU", "Memory", "APU")

        /**
         * The core's changed-setting lines ("GPU|draw_resolution_scale_x = 2 (default 1) · this
         * game") as the panel lists them: "draw_resolution_scale_x = 2 · this game", the
         * categories that weigh on performance first, at most [max]; and how many there are.
         */
        fun changedSettings(lines: List<String>?, max: Int = 6): Pair<List<String>, Int> {
            val settings = lines.orEmpty().filter { '|' in it }
            val ordered = settings.sortedBy { line ->
                PERFORMANCE_CATEGORIES.indexOf(line.substringBefore('|')).let { if (it < 0) Int.MAX_VALUE else it }
            }
            return ordered.take(max).map { line ->
                line.substringAfter('|').replace(Regex(" \\(default [^)]*\\)"), "")
            } to settings.size
        }

        /** "2×2" when the run renders above the console's resolution; null at 1×1 or unknown. */
        fun resolutionScale(lines: List<String>?): String? {
            fun axis(name: String) = lines.orEmpty().firstOrNull { it.startsWith("GPU|$name = ") }
                ?.substringAfter(" = ")?.substringBefore(' ')?.toIntOrNull()?.takeIf { it in 1..8 } ?: 1
            val x = axis("draw_resolution_scale_x")
            val y = axis("draw_resolution_scale_y")
            return if (x == 1 && y == 1) null else "$x×$y"
        }
    }
}
