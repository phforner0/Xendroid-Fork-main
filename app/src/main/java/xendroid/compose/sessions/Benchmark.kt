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
 */
object Benchmark {
    /** Shorter runs say little: a scene with loading in it, a menu. */
    const val MIN_SECONDS = 30
    /** Starting battery temperatures further apart than this make runs incomparable. */
    const val MAX_START_DELTA_C = 3f

    data class Side(val runs: Int, val medianFps: Double, val lowFps: Double, val frameTimeP99Ms: Double?)

    data class Result(
        val order: String,
        val balanced: Boolean,
        val warnings: List<String>,
        val a: Side?,
        val b: Side?,
        /** B − A median FPS of each time-ordered pair. */
        val pairDeltas: List<Int>,
        val verdict: String,
        /** What the runs recorded as different between A and B: "driver (X → Y)". */
        val changed: List<String> = emptyList(),
    )

    /** Something a run records that has to be the same in every run of a side; null = not recorded. */
    private class Dimension(val name: String, val value: (BenchRun) -> String?)

    private fun pacing(values: List<Int>, unit: (Int) -> String): String? = when (values.size) {
        0 -> null
        1 -> unit(values.single())
        else -> "changing (${values.joinToString(" / ", transform = unit)})"
    }

    private val dimensions = listOf(
        Dimension("driver") { it.driver ?: "unknown driver" },
        Dimension("frame generation") { if (it.frameGeneration) "on" else "off" },
        Dimension("FPS limit") { r -> pacing(r.fpsLimits) { if (it == 0) "unlimited" else "$it FPS" } },
        Dimension("vblank cap") { r -> r.refreshCap?.let { if (it) "capped" else "uncapped" } },
        Dimension("display refresh") { r -> pacing(r.displayHz) { "$it Hz" } },
    )

    /** The suggested order of [runs] runs: A B B A A B B A… */
    fun abbaPlan(runs: Int): String = "ABBA".repeat((runs + 3) / 4).take(runs)

    fun compare(runs: List<BenchRun>): Result {
        val ordered = runs.filter { it.label == 'A' || it.label == 'B' }.sortedBy { it.startedAt }
        val order = ordered.joinToString("") { it.label.toString() }
        val a = ordered.filter { it.label == 'A' }
        val b = ordered.filter { it.label == 'B' }
        val warnings = ArrayList<String>()
        if (a.isEmpty() || b.isEmpty()) {
            return Result(order, false, listOf("Mark at least one run as A and one as B."), side(a), side(b), emptyList(),
                "Not enough runs to compare.")
        }
        val balanced = isBalanced(order)
        when {
            ordered.size < 4 -> warnings += "Run at least A B B A (4 runs): the phone warms up during a session."
            !balanced -> warnings += "Order $order does not cancel warming up: run them as ${abbaPlan(ordered.size + ordered.size % 2)}."
        }
        ordered.filter { it.sampledSeconds < MIN_SECONDS }.forEach {
            warnings += "A ${it.label} run sampled only ${it.sampledSeconds} s (at least $MIN_SECONDS s of the same scene)."
        }
        ordered.filter { it.fpsLimits.size > 1 || it.displayHz.size > 1 }.forEach {
            warnings += "The FPS limit or the display refresh changed during a run (${it.label}); keep both fixed while measuring."
        }
        val changed = ArrayList<String>()
        for (dimension in dimensions) {
            if (ordered.any { dimension.value(it) == null }) continue      // not recorded by every run
            val sides = listOf(a, b).map { side -> side.map { dimension.value(it)!! }.distinct() }
            sides.forEachIndexed { i, values ->
                if (values.size > 1) {
                    warnings += "The ${dimension.name} differs among the ${"AB"[i]} runs (${values.joinToString(" / ")}): each side needs one setup."
                }
            }
            if (sides.all { it.size == 1 } && sides[0] != sides[1]) changed += "${dimension.name} (${sides[0].single()} → ${sides[1].single()})"
        }
        if (changed.size > 1) warnings += "A and B differ in more than one thing: ${changed.joinToString("; ")}. Change one at a time."
        val temps = ordered.mapNotNull { it.batteryStartC }
        if (temps.size >= 2 && temps.max() - temps.min() > MAX_START_DELTA_C) {
            warnings += "Runs started %.0f °C apart; let the phone cool to the same temperature first.".format(temps.max() - temps.min())
        }
        val pairs = pairUp(ordered)
        val deltas = pairs.map { (x, y) -> y.medianFps - x.medianFps }
        val verdict = when {
            deltas.isEmpty() -> "No A/B pair to compare."
            warnings.isNotEmpty() -> "Fix the warnings first; the numbers below are not a result yet."
            deltas.all { it > 0 } -> "B is faster in every pair (+${deltas.min()} to +${deltas.max()} FPS median)."
            deltas.all { it < 0 } -> "B is slower in every pair (${deltas.max()} to ${deltas.min()} FPS median)."
            deltas.all { it == 0 } -> "No difference in median FPS."
            else -> "Pairs disagree (${deltas.joinToString { if (it > 0) "+$it" else "$it" }}): no difference shown; run more pairs."
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
