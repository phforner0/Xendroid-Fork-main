package xendroid.compose.sessions

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * Performance summary of one run (C02), built from the host's one-second samples
 * and the core's per-frame counters. What it is and is not:
 *  - the FPS figures are the distribution of 1-second guest averages, NOT per-frame
 *    percentiles or "1% lows";
 *  - the frame-time histogram IS per guest frame (present-to-present, 1 ms buckets),
 *    but a gap over one second (pause, loading) restarts the timing and is left out;
 *  - pipeline creation time includes pipeline cache hits, so it is time the GPU thread
 *    spent creating pipelines, not only driver compiles;
 *  - present submissions are frames accepted by the compositor, NOT measured scanout;
 *  - seconds while the guest was paused or produced no new frame are left out;
 *  - audio underruns are blocks the output had to conceal because the emulator had not
 *    produced them in time; device xruns are counted separately, and both are absent
 *    (never zero) when no audio output ran.
 */
@Serializable
data class RunPerformance(
    val version: Int = 1,
    /** Sampled running seconds by rounded guest FPS; index = FPS, the last bucket also holds anything above it. */
    val fpsHistogram: List<Int> = emptyList(),
    /** Seconds in which the guest was paused, in the background or produced no frame. */
    val idleSeconds: Int = 0,
    val presentSubmissions: Long = 0,
    val syntheticSubmissions: Long = 0,
    val frameGenerationSeconds: Int = 0,
    val batteryStartC: Float? = null,
    val batteryMaxC: Float? = null,
    val batteryEndC: Float? = null,
    /** Guest frames of this run by frame time: index = whole milliseconds, the last bucket
     *  holds everything from its index up (native GuestFrameTimeHistogram deltas). */
    val frameTimeHistogramMs: List<Long> = emptyList(),
    /** Graphics pipelines created during this run (driver compiles and pipeline cache hits)
     *  and the time spent creating them; null in records from before it was measured. */
    val pipelineCreations: Long? = null,
    val pipelineCreationMs: Long? = null,
    /** Seconds from the game process starting to the first guest frames (1 s resolution). */
    val firstFrameSeconds: Int? = null,
    /** Audio output of this run: backend ("AAudio" / "OpenSL ES"), blocks the device played
     *  after the guest's first one, blocks concealed because the emulator was late (heard as
     *  dropouts or crackle) and the stream's own xruns (AAudio only). Null when no audio
     *  output ran or it was not measured. */
    val audioBackend: String? = null,
    val audioBlocks: Long? = null,
    val audioConcealedBlocks: Long? = null,
    val audioDeviceXruns: Long? = null,
) {
    val sampledSeconds: Int get() = fpsHistogram.sum()
    val frames: Long get() = frameTimeHistogramMs.sum()

    /**
     * Upper bound (ms) that [fraction] of this run's guest frames stayed under:
     * frameTimeUpperMs(0.99) = 40 means 99% of frames took less than 40 ms.
     * [FRAME_TIME_OPEN_BUCKET] means "that long or longer" (the open last bucket).
     */
    fun frameTimeUpperMs(fraction: Double): Int? {
        val total = frames
        if (total == 0L) return null
        val wanted = ceil(fraction.coerceIn(0.0, 1.0) * total).toLong().coerceAtLeast(1)
        var seen = 0L
        var ms = 0
        while (ms < frameTimeHistogramMs.lastIndex) {
            seen += frameTimeHistogramMs[ms]
            if (seen >= wanted) break
            ms++
        }
        return if (ms >= FRAME_TIME_OPEN_BUCKET) FRAME_TIME_OPEN_BUCKET else ms + 1
    }

    /** The FPS that [fraction] of the sampled seconds stay at or below (0.5 = median). */
    fun fpsPercentile(fraction: Double): Int? {
        val total = sampledSeconds
        if (total == 0) return null
        val wanted = ceil(fraction.coerceIn(0.0, 1.0) * total).toInt().coerceAtLeast(1)
        var seen = 0
        fpsHistogram.forEachIndexed { fps, count ->
            seen += count
            if (seen >= wanted) return fps
        }
        return fpsHistogram.lastIndex
    }

    /** Compositor submissions per sampled second (guest + synthetic frames). */
    val presentRate: Double? get() = sampledSeconds.takeIf { it > 0 }?.let { presentSubmissions.toDouble() / it }

    companion object {
        /** Index of the native histogram's open "this long or longer" bucket. */
        const val FRAME_TIME_OPEN_BUCKET = 250
    }
}

/**
 * Turns the per-second growth of a cumulative counter into flight recorder bursts: a
 * second in which [amount][sample] grew by [threshold] or more starts or extends a
 * burst, and [quietSeconds] seconds in a row below it end it (so sporadic spikes make
 * one burst, not one pair of events each). [events][sample] is summed alongside:
 * pipelines next to creation time, blocks next to concealed audio blocks. Feed it
 * once per second with the totals.
 */
class BurstTracker(private val threshold: Long, private val quietSeconds: Int = 1) {
    sealed interface Burst {
        data object Started : Burst
        /** [seconds] counts only the busy seconds; [events] covers the whole burst. */
        data class Ended(val seconds: Int, val amount: Long, val events: Long) : Burst
    }

    private var lastAmount: Long? = null
    private var lastEvents: Long? = null
    private var busySeconds = 0
    private var quiet = 0
    private var amount = 0L
    private var events = 0L

    fun sample(amountTotal: Long, eventsTotal: Long): Burst? {
        val previousAmount = lastAmount
        val previousEvents = lastEvents
        lastAmount = amountTotal
        lastEvents = eventsTotal
        if (previousAmount == null || previousEvents == null) return null
        val amountDelta = (amountTotal - previousAmount).coerceAtLeast(0)
        val eventsDelta = (eventsTotal - previousEvents).coerceAtLeast(0)
        if (amountDelta >= threshold) {
            busySeconds++
            quiet = 0
            amount += amountDelta
            events += eventsDelta
            return if (busySeconds == 1) Burst.Started else null
        }
        if (busySeconds == 0) return null
        events += eventsDelta
        if (++quiet < quietSeconds) return null
        return Burst.Ended(busySeconds, amount, events).also { busySeconds = 0; quiet = 0; amount = 0; events = 0 }
    }
}

/** "AAudio: 32 of 8000 blocks concealed (0.4%), 2 device xruns", or null without audio output. */
fun describeAudio(perf: RunPerformance): String? {
    val backend = perf.audioBackend ?: return null
    val blocks = perf.audioBlocks ?: return null
    val concealed = perf.audioConcealedBlocks ?: return null
    if (blocks == 0L) return "$backend: no guest audio played"
    val share = "%.1f".format(java.util.Locale.ROOT, 100.0 * concealed / blocks)
    val underruns = if (concealed == 0L) "no underruns in $blocks blocks" else "$concealed of $blocks blocks concealed ($share%)"
    return "$backend: $underruns" + (perf.audioDeviceXruns?.let { ", $it device xruns" } ?: "")
}

/** "99th percentile under 34 ms, 99.9th 250 ms or more (N frames)", or null without frames. */
fun describeFrameTimes(perf: RunPerformance): String? {
    val p99 = perf.frameTimeUpperMs(0.99) ?: return null
    val p999 = perf.frameTimeUpperMs(0.999) ?: return null
    fun bound(ms: Int) =
        if (ms >= RunPerformance.FRAME_TIME_OPEN_BUCKET) "$ms ms or more" else "under $ms ms"
    return "99th percentile ${bound(p99)}, 99.9th ${bound(p999)} (${perf.frames} frames)"
}

/** Collects [RunPerformance] in the host process; one [sample] call per second. Not thread-safe. */
class RunPerformanceAccumulator(private val maxFps: Int = 240) {
    private val histogram = IntArray(maxFps + 1)
    private var idle = 0
    private var lastPresents: Long? = null
    private var lastGenerated: Long? = null
    private var lastGuestFrames: Long? = null
    private var presents = 0L
    private var synthetic = 0L
    private var fgSeconds = 0
    private var batteryStart: Float? = null
    private var batteryMax: Float? = null
    private var batteryEnd: Float? = null
    private var frameBaseline: LongArray? = null
    private var frameLatest: LongArray? = null
    private var compileBaseline: LongArray? = null
    private var compileLatest: LongArray? = null
    private var firstFrameSeconds: Int? = null
    private var audioBaseline: LongArray? = null
    private var audioLatest: LongArray? = null

    /** Time to the first guest frames; only the first call counts. */
    fun firstFrame(seconds: Int) {
        if (firstFrameSeconds == null && seconds >= 0) firstFrameSeconds = seconds
    }

    /** Cumulative native {backend, blocks, concealed, device xruns}; the first call is the baseline. */
    fun audio(stats: LongArray?) {
        if (stats == null || stats.size < 4) return
        if (audioBaseline == null) audioBaseline = stats.copyOf(4)
        audioLatest = stats.copyOf(4)
    }

    private fun audioDelta(index: Int): Long? {
        val latest = audioLatest ?: return null
        val baseline = audioBaseline ?: return null
        if (latest[0] == 0L) return null       // no audio output ran
        return (latest[index] - baseline[index]).coerceAtLeast(0)
    }

    /** Cumulative native {pipeline creations, ns spent}; the first call is the run's baseline. */
    fun compiles(stats: LongArray?) {
        if (stats == null || stats.size < 2) return
        if (compileBaseline == null) compileBaseline = stats.copyOf(2)
        compileLatest = stats.copyOf(2)
    }

    /** Cumulative native frame-time counts; the first call is the run's baseline. */
    fun frameTimes(counts: LongArray?) {
        if (counts == null || counts.isEmpty()) return
        val baseline = frameBaseline
        if (baseline == null || baseline.size != counts.size) frameBaseline = counts.copyOf()
        frameLatest = counts.copyOf()
    }

    /**
     * [running]: guest not paused and the app in the foreground. [presentCount],
     * [generatedCount] and [guestFrames] are process-wide monotonic counters; the first
     * call only establishes them. The guest FPS keeps its last value while no guest
     * frame arrives, so a second without a new guest frame ([guestFrames]; host
     * submissions when it is unknown) is idle whatever [guestFps] says.
     */
    fun sample(running: Boolean, guestFps: Double, presentCount: Long, generatedCount: Long, frameGenerationActive: Boolean,
               guestFrames: Long? = null) {
        val previousPresents = lastPresents
        val previousGenerated = lastGenerated
        val previousFrames = lastGuestFrames
        lastPresents = presentCount
        lastGenerated = generatedCount
        lastGuestFrames = guestFrames
        if (previousPresents == null || previousGenerated == null) return
        val presentDelta = (presentCount - previousPresents).coerceAtLeast(0)
        val generatedDelta = (generatedCount - previousGenerated).coerceAtLeast(0)
        val newFrames = if (guestFrames != null && previousFrames != null) guestFrames - previousFrames > 0 else presentDelta > 0
        if (!running || !newFrames || !guestFps.isFinite()) {
            idle++
            return
        }
        histogram[guestFps.roundToInt().coerceIn(0, maxFps)]++
        presents += presentDelta
        synthetic += generatedDelta
        if (frameGenerationActive) fgSeconds++
    }

    fun battery(celsius: Float?) {
        if (celsius == null || !celsius.isFinite()) return
        if (batteryStart == null) batteryStart = celsius
        batteryMax = maxOf(batteryMax ?: celsius, celsius)
        batteryEnd = celsius
    }

    fun snapshot(): RunPerformance = RunPerformance(
        fpsHistogram = histogram.toList().dropLastWhile { it == 0 },
        idleSeconds = idle,
        presentSubmissions = presents,
        syntheticSubmissions = synthetic,
        frameGenerationSeconds = fgSeconds,
        batteryStartC = batteryStart,
        batteryMaxC = batteryMax,
        batteryEndC = batteryEnd,
        frameTimeHistogramMs = frameDeltas(),
        pipelineCreations = compileDelta(0),
        pipelineCreationMs = compileDelta(1)?.let { it / 1_000_000 },
        firstFrameSeconds = firstFrameSeconds,
        audioBackend = when (audioLatest?.get(0)) { 1L -> "AAudio"; 2L -> "OpenSL ES"; else -> null },
        audioBlocks = audioDelta(1),
        audioConcealedBlocks = audioDelta(2),
        // OpenSL ES reports no xruns: absent, not zero.
        audioDeviceXruns = audioDelta(3)?.takeIf { audioLatest?.get(0) == 1L },
    )

    private fun compileDelta(index: Int): Long? {
        val latest = compileLatest ?: return null
        val baseline = compileBaseline ?: return null
        return (latest[index] - baseline[index]).coerceAtLeast(0)
    }

    private fun frameDeltas(): List<Long> {
        val latest = frameLatest ?: return emptyList()
        val baseline = frameBaseline ?: return emptyList()
        return latest.indices.map { (latest[it] - baseline[it]).coerceAtLeast(0) }.dropLastWhile { it == 0L }
    }
}
