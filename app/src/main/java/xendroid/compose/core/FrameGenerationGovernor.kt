package xendroid.compose.core

import java.util.Locale

/**
 * F04, advisory: once per second, does frame generation fit this device right now? The
 * generation pass has to fit the time between two outputs, the guest cadence times the
 * multiplier has to fit the display, synthetic slots should not be skipped for being late,
 * and a severe thermal status means backing off. A verdict replaces the current one only
 * after holding [holdSeconds] seconds in a row (hysteresis), so one slow second does not
 * flip it. It never acts on its own: the thresholds need the A/B on a phone first.
 */
class FrameGenerationGovernor(private val holdSeconds: Int = 3) {
    enum class Verdict(val label: String) {
        OFF("off"),
        FITS("fits"),
        GPU_OVER_BUDGET("generation over budget"),
        CADENCE_OVER_DISPLAY("more outputs than the display shows"),
        OUTPUTS_LATE("synthetic outputs late"),
        THERMAL("thermal: back off"),
    }

    /** One second of figures; [slots] and [lateSkips] are the cumulative native counters. */
    data class Second(
        val active: Boolean,
        val displayHz: Float,
        val multiplier: Int,
        val guestFps: Double,
        /** Latest generation pass, negative when it could not be timed. */
        val gpuMs: Double,
        val slots: Long,
        val lateSkips: Long,
        /** android.os.PowerManager.THERMAL_STATUS_*; 3 is SEVERE. */
        val thermalStatus: Int,
    )

    data class Assessment(val verdict: Verdict, val detail: String) {
        val text: String get() = "${verdict.label}${if (detail.isEmpty()) "" else " · $detail"}"
    }

    var current: Assessment = Assessment(Verdict.OFF, "")
        private set
    private var candidate: Verdict? = null
    private var held = 0
    private var lastSlots: Long? = null
    private var lastLate: Long? = null

    /** Feeds one second; returns the new assessment when the verdict changed, else null. */
    fun sample(second: Second): Assessment? {
        val slotsDelta = lastSlots?.let { (second.slots - it).coerceAtLeast(0) } ?: 0L
        val lateDelta = lastLate?.let { (second.lateSkips - it).coerceAtLeast(0) } ?: 0L
        lastSlots = second.slots
        lastLate = second.lateSkips
        val judged = judge(second, slotsDelta, lateDelta)
        if (judged.verdict == Verdict.OFF) {
            candidate = null
            held = 0
            return change(judged)
        }
        if (judged.verdict == current.verdict) {
            current = judged        // same verdict, fresher figures
            candidate = null
            held = 0
            return null
        }
        if (judged.verdict != candidate) {
            candidate = judged.verdict
            held = 0
        }
        held++
        // Turning FG on starts from OFF: report its first judgement without waiting.
        return if (held >= holdSeconds || current.verdict == Verdict.OFF) change(judged) else null
    }

    private fun change(next: Assessment): Assessment? {
        val changed = next.verdict != current.verdict
        current = next
        candidate = null
        held = 0
        return if (changed) next else null
    }

    private fun judge(second: Second, slots: Long, late: Long): Assessment {
        if (!second.active) return Assessment(Verdict.OFF, "")
        if (second.thermalStatus >= 3) return Assessment(Verdict.THERMAL, "thermal status ${second.thermalStatus}")
        val outputsPerSecond = second.guestFps * second.multiplier.coerceAtLeast(2)
        if (second.displayHz > 0 && outputsPerSecond > second.displayHz * 1.025) {
            return Assessment(Verdict.CADENCE_OVER_DISPLAY,
                "%.0f FPS × %d over %.0f Hz".format(Locale.ROOT, second.guestFps, second.multiplier, second.displayHz))
        }
        val interval = if (outputsPerSecond > 0) 1000.0 / outputsPerSecond else 0.0
        val timing = if (second.gpuMs >= 0 && interval > 0)
            "generation %.1f ms of %.1f ms between outputs".format(Locale.ROOT, second.gpuMs, interval) else ""
        if (second.gpuMs >= 0 && interval > 0 && second.gpuMs > interval * 0.8) {
            return Assessment(Verdict.GPU_OVER_BUDGET, timing)
        }
        if (slots >= 5 && late * 5 > slots) return Assessment(Verdict.OUTPUTS_LATE, "$late of $slots slots skipped late")
        return Assessment(Verdict.FITS, timing.ifEmpty { "GPU time not measured" })
    }
}
