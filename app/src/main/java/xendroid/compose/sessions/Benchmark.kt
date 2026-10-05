package xendroid.compose.sessions

import kotlin.math.abs

/** One finished run put in a comparison, as A or B. */
data class BenchRun(
    val label: Char,
    val startedAt: Long,
    /** Guest frames only (synthetic frames never count as game FPS). */
    val medianFps: Int,
    val lowFps: Int,
    val frameTimeP99Ms: Int?,
    val sampledSeconds: Int,
    val driver: String?,
    val frameGeneration: Boolean,
    val batteryStartC: Float?,
    val markers: Int = 0,
    /** Pacing the run recorded (empty/null in older runs: not checked). */
    val fpsLimits: List<Int> = emptyList(),
    val displayHz: List<Int> = emptyList(),
    val refreshCap: Boolean? = null,
)

/**
 * C07: an A/B comparison of runs of one title the player ran on purpose (same scene, one thing
 * changed). It checks what makes the result trustworthy before giving one: the order (ABBA
 * cancels the phone warming up over the session), that each side ran one setup (driver, frame
 * generation, FPS limit, vblank cap, display refresh) and A and B differ in one of them at
 * most, the starting temperature and whether every run sampled enough; then it pairs runs in
 * time order and gives B − A per pair, calling a difference only when every pair agrees.
 * What it says is structured ([Warning], [Verdict], [Change], [Value]) so the screen says it in
 * its language (U02); each has its English text for logs and tests.
 */
object Benchmark {
    /** Shorter runs say little: a scene with loading in it, a menu. */
    const val MIN_SECONDS = 30
    /** Starting battery temperatures further apart than this make runs incomparable. */
    const val MAX_START_DELTA_C = 3f

    data class Side(val runs: Int, val medianFps: Double, val lowFps: Double, val frameTimeP99Ms: Double?)

    /** Something a run records that has to be the same in every run of a side. */
    enum class Dimension(val english: String) {
        DRIVER("driver"), FRAME_GENERATION("frame generation"), FPS_LIMIT("FPS limit"),
        VBLANK_CAP("vblank cap"), DISPLAY_REFRESH("display refresh"),
    }

    /** A dimension's value in one run. */
    sealed interface Value {
        val english: String
        data class Driver(val name: String?) : Value { override val english get() = name ?: "unknown driver" }
        data class OnOff(val on: Boolean) : Value { override val english get() = if (on) "on" else "off" }
        /** 0 = unlimited. */
        data class FpsLimit(val fps: Int) : Value { override val english get() = if (fps == 0) "unlimited" else "$fps FPS" }
        data class Capped(val capped: Boolean) : Value { override val english get() = if (capped) "capped" else "uncapped" }
        data class Hz(val hz: Int) : Value { override val english get() = "$hz Hz" }
        /** Changed during the run. */
        data class Changing(val values: List<Value>) : Value {
            override val english get() = "changing (${values.joinToString(" / ") { it.english }})"
        }
    }

    /** What the runs recorded as different between A and B: "driver (X → Y)". */
    data class Change(val dimension: Dimension, val a: Value, val b: Value) {
        val english: String get() = "${dimension.english} (${a.english} → ${b.english})"
    }

    /** What keeps the numbers from being a result. */
    sealed interface Warning {
        val english: String
        data object NoSides : Warning { override val english get() = "Mark at least one run as A and one as B." }
        data object TooFewRuns : Warning {
            override val english get() = "Run at least A B B A (4 runs): the phone warms up during a session."
        }
        data class Unbalanced(val order: String, val plan: String) : Warning {
            override val english get() = "Order $order does not cancel warming up: run them as $plan."
        }
        data class ShortRun(val label: Char, val seconds: Int) : Warning {
            override val english get() = "A $label run sampled only $seconds s (at least $MIN_SECONDS s of the same scene)."
        }
        data class PacingChanged(val label: Char) : Warning {
            override val english get() =
                "The FPS limit or the display refresh changed during a run ($label); keep both fixed while measuring."
        }
        data class MixedSide(val dimension: Dimension, val side: Char, val values: List<Value>) : Warning {
            override val english get() =
                "The ${dimension.english} differs among the $side runs (${values.joinToString(" / ") { it.english }}): each side needs one setup."
        }
        data class MoreThanOne(val changes: List<Change>) : Warning {
            override val english get() =
                "A and B differ in more than one thing: ${changes.joinToString("; ") { it.english }}. Change one at a time."
        }
        data class Temperature(val apartC: Float) : Warning {
            override val english get() = "Runs started %.0f °C apart; let the phone cool to the same temperature first.".format(apartC)
        }
    }

    sealed interface Verdict {
        val english: String
        data object NotEnough : Verdict { override val english get() = "Not enough runs to compare." }
        data object NoPair : Verdict { override val english get() = "No A/B pair to compare." }
        data object FixWarnings : Verdict { override val english get() = "Fix the warnings first; the numbers below are not a result yet." }
        data class Faster(val min: Int, val max: Int) : Verdict {
            override val english get() = "B is faster in every pair (+$min to +$max FPS median)."
        }
        /** [least]: the smallest loss (closest to 0); [most]: the largest. */
        data class Slower(val least: Int, val most: Int) : Verdict {
            override val english get() = "B is slower in every pair ($least to $most FPS median)."
        }
        data object Same : Verdict { override val english get() = "No difference in median FPS." }
        data class Disagree(val deltas: List<Int>) : Verdict {
            override val english get() =
                "Pairs disagree (${deltas.joinToString { if (it > 0) "+$it" else "$it" }}): no difference shown; run more pairs."
        }
    }

    data class Result(
        val order: String,
        val balanced: Boolean,
        val notes: List<Warning>,
        val a: Side?,
        val b: Side?,
        /** B − A median FPS of each time-ordered pair. */
        val pairDeltas: List<Int>,
        val outcome: Verdict,
        val changes: List<Change> = emptyList(),
    ) {
        val warnings: List<String> get() = notes.map { it.english }
        val verdict: String get() = outcome.english
        val changed: List<String> get() = changes.map { it.english }
    }

    private fun pacing(values: List<Int>, one: (Int) -> Value): Value? = when (values.size) {
        0 -> null
        1 -> one(values.single())
        else -> Value.Changing(values.map(one))
    }

    /** [dimension] as [run] recorded it; null = not recorded. */
    private fun value(dimension: Dimension, run: BenchRun): Value? = when (dimension) {
        Dimension.DRIVER -> Value.Driver(run.driver)
        Dimension.FRAME_GENERATION -> Value.OnOff(run.frameGeneration)
        Dimension.FPS_LIMIT -> pacing(run.fpsLimits) { Value.FpsLimit(it) }
        Dimension.VBLANK_CAP -> run.refreshCap?.let { Value.Capped(it) }
        Dimension.DISPLAY_REFRESH -> pacing(run.displayHz) { Value.Hz(it) }
    }

    /** The suggested order of [runs] runs: A B B A A B B A… */
    fun abbaPlan(runs: Int): String = "ABBA".repeat((runs + 3) / 4).take(runs)

    fun compare(runs: List<BenchRun>): Result {
        val ordered = runs.filter { it.label == 'A' || it.label == 'B' }.sortedBy { it.startedAt }
        val order = ordered.joinToString("") { it.label.toString() }
        val a = ordered.filter { it.label == 'A' }
        val b = ordered.filter { it.label == 'B' }
        val warnings = ArrayList<Warning>()
        if (a.isEmpty() || b.isEmpty()) {
            return Result(order, false, listOf(Warning.NoSides), side(a), side(b), emptyList(), Verdict.NotEnough)
        }
        val balanced = isBalanced(order)
        when {
            ordered.size < 4 -> warnings += Warning.TooFewRuns
            !balanced -> warnings += Warning.Unbalanced(order, abbaPlan(ordered.size + ordered.size % 2))
        }
        ordered.filter { it.sampledSeconds < MIN_SECONDS }.forEach { warnings += Warning.ShortRun(it.label, it.sampledSeconds) }
        ordered.filter { it.fpsLimits.size > 1 || it.displayHz.size > 1 }.forEach { warnings += Warning.PacingChanged(it.label) }
        val changed = ArrayList<Change>()
        for (dimension in Dimension.entries) {
            if (ordered.any { value(dimension, it) == null }) continue      // not recorded by every run
            val sides = listOf(a, b).map { side -> side.map { value(dimension, it)!! }.distinct() }
            sides.forEachIndexed { i, values ->
                if (values.size > 1) warnings += Warning.MixedSide(dimension, "AB"[i], values)
            }
            if (sides.all { it.size == 1 } && sides[0] != sides[1]) changed += Change(dimension, sides[0].single(), sides[1].single())
        }
        if (changed.size > 1) warnings += Warning.MoreThanOne(changed.toList())
        val temps = ordered.mapNotNull { it.batteryStartC }
        if (temps.size >= 2 && temps.max() - temps.min() > MAX_START_DELTA_C) warnings += Warning.Temperature(temps.max() - temps.min())
        val pairs = pairUp(ordered)
        val deltas = pairs.map { (x, y) -> y.medianFps - x.medianFps }
        val verdict = when {
            deltas.isEmpty() -> Verdict.NoPair
            warnings.isNotEmpty() -> Verdict.FixWarnings
            deltas.all { it > 0 } -> Verdict.Faster(deltas.min(), deltas.max())
            deltas.all { it < 0 } -> Verdict.Slower(deltas.max(), deltas.min())
            deltas.all { it == 0 } -> Verdict.Same
            else -> Verdict.Disagree(deltas)
        }
        return Result(order, balanced, warnings, side(a), side(b), deltas, verdict, changed)
    }

    /** ABBA-like: in every prefix the counts of A and B never drift apart by more than one
     *  pair's worth, and both sides sit as early as late on average. */
    fun isBalanced(order: String): Boolean {
        if (order.isEmpty()) return false
        val a = order.indices.filter { order[it] == 'A' }
        val b = order.indices.filter { order[it] == 'B' }
        if (a.isEmpty() || b.isEmpty() || abs(a.size - b.size) > 0) return false
        return abs(a.average() - b.average()) < 0.01
    }

    /** Time-ordered (A, B) pairs: each A with the nearest unpaired B, oriented as (A, B). */
    private fun pairUp(ordered: List<BenchRun>): List<Pair<BenchRun, BenchRun>> {
        val pending = ordered.toMutableList()
        val pairs = ArrayList<Pair<BenchRun, BenchRun>>()
        while (true) {
            val i = pending.indices.zipWithNext().firstOrNull { (x, y) -> pending[x].label != pending[y].label } ?: break
            val first = pending[i.first]
            val second = pending[i.second]
            pairs += if (first.label == 'A') first to second else second to first
            pending.removeAt(i.second)
            pending.removeAt(i.first)
        }
        return pairs
    }

    private fun side(runs: List<BenchRun>): Side? {
        if (runs.isEmpty()) return null
        fun median(values: List<Double>) = values.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
        val p99 = runs.mapNotNull { it.frameTimeP99Ms?.toDouble() }
        return Side(runs.size, median(runs.map { it.medianFps.toDouble() }), median(runs.map { it.lowFps.toDouble() }),
            p99.takeIf { it.isNotEmpty() }?.let(::median))
    }
}
