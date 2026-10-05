package xendroid.compose.sessions

/**
 * Round 2: what a finished session suggests changing, from what the app records of it (how it
 * ended, its numbers, the driver it ran on) and the game's settings. Each suggestion carries the
 * numbers behind it and the values it would write for the game, so it can be applied right
 * there. Pure: the JVM tests hold every rule.
 */
object SessionAdvice {
    enum class Rule(val id: String, val error: Boolean) {
        /** A native crash on a custom driver: try the phone's own. */
        SYSTEM_DRIVER("system-driver", true),
        /** It failed to open with values of its own: back to the global ones. */
        RESET_GAME("reset-game", true),
        /** Stutters while many pipelines were made: make them on more threads. */
        PIPELINES("pipelines", false),
        /** Well under its FPS limit at a resolution scale above 1x: 1x. */
        SCALE_DOWN("scale-1x", false),
        /** Swinging between 30 and 60 under a 60 limit: a steady 30. */
        STEADY_30("fps-30", false),
        /** Audio filled in for late blocks: a deeper buffer. */
        AUDIO_BUFFER("audio-buffer", false),
        /** Hot: a lower FPS limit. */
        HEAT("heat", false),
    }

    /** One suggestion: [values] go to the game's config (null: back to the global value); [numbers] fill its reason. */
    data class Advice(val rule: Rule, val values: Map<String, String?>, val numbers: List<String> = emptyList())

    const val DRIVER = "Vulkan|vulkan_lib_path"
    const val PIPELINE_THREADS = "Vulkan|vulkan_pipeline_creation_threads"
    const val FPS_LIMIT = "GPU|framerate_limit"
    const val SCALE_X = "GPU|draw_resolution_scale_x"
    const val SCALE_Y = "GPU|draw_resolution_scale_y"
    const val AUDIO_BURSTS = "APU|apu_aaudio_buffer_bursts"

    private const val MAX_PIPELINE_THREADS = 5
    private const val MAX_AUDIO_BURSTS = 8
    const val HOT_BATTERY_C = 45f

    /** Worth a look: a session that failed (to open or later), or one that ran over a minute. */
    fun worthALook(run: SessionRun): Boolean = run.state == RunState.FAILED || (run.playedMs ?: 0L) >= 60_000L

    /**
     * What [run] suggests, errors first, at most [max]. [setting] answers a key's value as the
     * game ran it next time (its own or the global one); [gameOwn], the keys the game has values of.
     */
    fun of(run: SessionRun, setting: (String) -> String?, gameOwn: Set<String>, max: Int = 3): List<Advice> {
        val out = mutableListOf<Advice>()
        val failed = run.state == RunState.FAILED
        val crashed = failed && (run.nativeBacktrace != null || run.endReason.orEmpty().contains("native crash", ignoreCase = true))
        val driver = setting(DRIVER).orEmpty()
        if (crashed && driver.isNotBlank()) {
            out += Advice(Rule.SYSTEM_DRIVER, mapOf(DRIVER to ""), listOf(run.driver?.label ?: java.io.File(driver).parentFile?.name ?: driver))
        }
        val neverDrew = run.performance?.firstFrameSeconds == null && (run.performance?.sampledSeconds ?: 0) == 0
        if (failed && neverDrew && gameOwn.isNotEmpty()) {
            out += Advice(Rule.RESET_GAME, gameOwn.associateWith { null }, listOf(gameOwn.size.toString()))
        }
        val p = run.performance?.takeIf { it.sampledSeconds > 0 }
        if (p != null) {
            val median = p.fpsPercentile(0.5)
            val low = p.fpsPercentile(0.05)
            val p99 = p.frameTimeUpperMs(0.99)
            val limit = (setting(FPS_LIMIT)?.toIntOrNull() ?: p.fpsLimits.lastOrNull() ?: 0)
            val pipelines = p.pipelineCreations ?: 0
            val threads = setting(PIPELINE_THREADS)?.toIntOrNull() ?: 4
            if (pipelines >= 500 && p99 != null && p99 >= 50 && threads < MAX_PIPELINE_THREADS) {
                out += Advice(Rule.PIPELINES, mapOf(PIPELINE_THREADS to MAX_PIPELINE_THREADS.toString()), listOf(p99.toString(), pipelines.toString()))
            }
            val scale = maxOf(setting(SCALE_X)?.toIntOrNull() ?: 1, setting(SCALE_Y)?.toIntOrNull() ?: 1)
            val slow = median != null && limit > 0 && median < limit * 0.8
            if (slow && scale > 1) {
                out += Advice(Rule.SCALE_DOWN, mapOf(SCALE_X to "1", SCALE_Y to "1"), listOf(median.toString(), limit.toString(), "${scale}x"))
            } else if (limit == 60 && median != null && low != null && median in 34..52 && low < 40) {
                out += Advice(Rule.STEADY_30, mapOf(FPS_LIMIT to "30"), listOf(median.toString(), low.toString()))
            }
            val blocks = p.audioBlocks ?: 0
            val concealed = p.audioConcealedBlocks ?: 0
            val bursts = setting(AUDIO_BURSTS)?.toIntOrNull() ?: 4
            if (blocks > 0 && concealed * 100 > blocks && bursts < MAX_AUDIO_BURSTS) {
                out += Advice(Rule.AUDIO_BUFFER, mapOf(AUDIO_BURSTS to minOf(bursts + 2, MAX_AUDIO_BURSTS).toString()),
                    listOf("%.1f".format(java.util.Locale.ROOT, concealed * 100.0 / blocks)))
            }
            val hottest = p.batteryMaxC
            if (hottest != null && hottest >= HOT_BATTERY_C && (limit == 0 || limit > 30) && out.none { it.values[FPS_LIMIT] != null }) {
                out += Advice(Rule.HEAT, mapOf(FPS_LIMIT to "30"), listOf("%.0f".format(java.util.Locale.ROOT, hottest)))
            }
        }
        return out.sortedBy { if (it.rule.error) 0 else 1 }.take(max)
    }

    /** When the sheet shows: always, only after errors, or never. */
    enum class Mode { ALWAYS, ERRORS, NEVER }

    /**
     * What is left to show of [advice] for [titleId]: by [mode]; nothing for a muted game; no
     * muted rule (for every game or for this one); performance suggestions at most once a day per
     * game ([lastPerformanceShown], 0 for never).
     */
    fun shown(advice: List<Advice>, titleId: String, mode: Mode, muted: Set<String>, lastPerformanceShown: Long, now: Long): List<Advice> {
        if (mode == Mode.NEVER || gameKey(titleId) in muted) return emptyList()
        val recent = now - lastPerformanceShown < DAY_MS
        return advice.filter { a ->
            (a.rule.error || mode == Mode.ALWAYS) && a.rule.id !in muted && ruleKey(a.rule, titleId) !in muted && (a.rule.error || !recent)
        }
    }

    fun gameKey(titleId: String) = "game@${titleId.uppercase()}"
    fun ruleKey(rule: Rule, titleId: String) = "${rule.id}@${titleId.uppercase()}"
    const val DAY_MS = 24L * 3_600_000L
}
