package xendroid.compose.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The HUD's power line: the watts the battery gives and how long it would last at that rate.
 * Android's raw values need care: CURRENT_NOW is µA on paper, but many OEMs report mA
 * (Bannerlator 6a14c4a2), so a magnitude under 20,000 is read as mA (20 mA would be
 * impossibly little for a running game, 20 A impossibly much); the charge counter likewise
 * (under 100,000 is mAh); EXTRA_VOLTAGE is mV on paper and µV or whole volts on some
 * devices. The sign of the current differs between vendors, so whether the phone runs on
 * a charger comes from the battery broadcast, never from the sign.
 */
object BatteryReadout {
    /** Amperes from a CURRENT_NOW/CURRENT_AVERAGE reading; null when the device gives none. */
    fun amps(raw: Long): Double? {
        if (raw == 0L || raw == Long.MIN_VALUE || raw == Int.MIN_VALUE.toLong() || raw == Long.MAX_VALUE) return null
        val magnitude = abs(raw.toDouble())
        return if (magnitude < 20_000) magnitude / 1_000.0 else magnitude / 1_000_000.0
    }

    /** Ampere-hours left from a CHARGE_COUNTER reading; null when the device gives none. */
    fun ampHours(raw: Long): Double? {
        if (raw <= 0L || raw == Long.MAX_VALUE || raw == Int.MAX_VALUE.toLong()) return null
        return if (raw < 100_000) raw / 1_000.0 else raw / 1_000_000.0
    }

    /** Volts from EXTRA_VOLTAGE; null outside what a phone battery can be. */
    fun volts(raw: Int): Double? = when (raw) {
        in 2_500..5_500 -> raw / 1_000.0
        in 2_500_000..5_500_000 -> raw / 1_000_000.0
        in 3..5 -> raw.toDouble()
        else -> null
    }

    fun watts(currentRaw: Long, voltageRaw: Int): Double? {
        val amps = amps(currentRaw) ?: return null
        val volts = volts(voltageRaw) ?: return null
        return amps * volts
    }

    /** "1 h 40 min", "35 min". */
    fun duration(minutes: Int): String =
        if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(Locale.ROOT, minutes % 60)} min"

    /** "PWR 7.4 W · BAT 62% ~1 h 40 min" or "PWR plugged in · BAT 62% full in 45 min". */
    fun line(watts: Double?, percent: Int?, pluggedIn: Boolean, minutesLeft: Int?, minutesToFull: Int?): String {
        val power = when {
            pluggedIn -> "PWR plugged in"
            watts != null -> "PWR %.1f W".format(Locale.ROOT, watts)
            else -> "PWR N/A"
        }
        val battery = percent?.let { "BAT $it%" } ?: return power
        val time = when {
            pluggedIn -> minutesToFull?.let { " full in ${duration(it)}" }
            else -> minutesLeft?.let { " ~${duration(it)}" }
        }.orEmpty()
        return "$power · $battery$time"
    }
}

/**
 * Minutes of battery left while discharging: the remaining charge against a smoothed current
 * (10 s time constant, so a loading screen does not swing it); on a device without a charge
 * counter, how fast the percentage falls, once it fell two points. Null while unknown, on a
 * charger, and beyond two days (an idle reading, not a game's).
 */
class BatteryTimeEstimate {
    private var amps: Double? = null
    private var lastMs = 0L
    private var dropStart: Pair<Long, Int>? = null

    fun sample(nowMs: Long, percent: Int?, chargeRaw: Long, currentRaw: Long, pluggedIn: Boolean): Int? {
        if (pluggedIn) {
            amps = null
            dropStart = null
            return null
        }
        BatteryReadout.amps(currentRaw)?.let { now ->
            val previous = amps
            val weight = ((nowMs - lastMs).coerceAtLeast(0) / SMOOTHING_MS).coerceIn(0.0, 1.0)
            amps = if (previous == null) now else previous + (now - previous) * weight
            lastMs = nowMs
        }
        val charge = BatteryReadout.ampHours(chargeRaw)
        val drain = amps
        val minutes = if (charge != null && drain != null && drain > 0.01) {
            charge / drain * 60
        } else {
            byPercentage(nowMs, percent)
        }
        return minutes?.takeIf { it.isFinite() && it >= 0 && it <= MAX_MINUTES }?.roundToInt()
    }

    private fun byPercentage(nowMs: Long, percent: Int?): Double? {
        percent ?: return null
        val start = dropStart
        if (start == null || percent > start.second) {
            dropStart = nowMs to percent
            return null
        }
        val dropped = start.second - percent
        if (dropped < 2) return null
        return percent * ((nowMs - start.first).toDouble() / dropped) / 60_000
    }

    private companion object {
        const val SMOOTHING_MS = 10_000.0
        const val MAX_MINUTES = 48 * 60.0
    }
}
