package xendroid.compose

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import xendroid.compose.core.BatteryReadout
import xendroid.compose.core.BatteryTimeEstimate
import xendroid.compose.core.EmulatorSession
import xendroid.compose.core.presentSubmissionRate
import xendroid.compose.core.HudMetric

private data class GpuCounter(
    val busy: Long,
    val total: Long,
)

private data class GpuSource(
    val directPercentFile: File? = null,
    val counterFile: File? = null,
)

private fun findGpuSource(): GpuSource? {
    return runCatching {
        val kgslPercent = File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
        if (kgslPercent.isFile) {
            return GpuSource(directPercentFile = kgslPercent)
        }

        val kgslDevfreqLoad = File("/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load")
        if (kgslDevfreqLoad.isFile) {
            return GpuSource(directPercentFile = kgslDevfreqLoad)
        }

        val devfreq = File("/sys/class/devfreq")
        devfreq.listFiles()?.forEach { node ->
            val name = node.name.lowercase(Locale.US)
            if (name.contains("gpu") || name.contains("kgsl") || name.contains("3d")) {
                val load = File(node, "load")
                if (load.isFile) {
                    return GpuSource(directPercentFile = load)
                }
            }
        }

        val kgslBusy = File("/sys/class/kgsl/kgsl-3d0/gpubusy")
        if (kgslBusy.isFile) {
            return GpuSource(counterFile = kgslBusy)
        }

        null
    }.getOrNull()
}

private fun readDirectGpuPercent(file: File): Int? {
    return runCatching {
        file.readText()
            .trim()
            .removeSuffix("%")
            .toFloatOrNull()
            ?.coerceIn(0f, 100f)
            ?.roundToInt()
    }.getOrNull()
}

private fun readGpuCounter(file: File): GpuCounter? {
    return runCatching {
        val values = file.readText().trim().split(Regex("\\s+"))
        if (values.size < 2) return null
        val busy = values[0].toLongOrNull() ?: return null
        val total = values[1].toLongOrNull() ?: return null
        if (busy < 0L || total <= 0L) return null
        GpuCounter(busy = busy, total = total)
    }.getOrNull()
}

private fun readRamUsage(context: Context): Pair<Long, Long> {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    return (info.totalMem - info.availMem) to info.totalMem
}

private fun readBatteryTemperature(context: Context): Float {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    return temp / 10.0f
}

/** The battery as the power line needs it: the sticky broadcast plus the fuel gauge's current
 *  and charge counter (Long.MIN_VALUE where the device does not report them). */
private class BatterySample(
    val percent: Int?,
    val pluggedIn: Boolean,
    val voltageRaw: Int,
    val currentRaw: Long,
    val chargeRaw: Long,
    val minutesToFull: Int?,
)

private fun readBatterySample(context: Context): BatterySample? {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val manager = context.getSystemService(BatteryManager::class.java)
    return BatterySample(
        percent = if (level >= 0 && scale > 0) level * 100 / scale else null,
        pluggedIn = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
        voltageRaw = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
        currentRaw = manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Long.MIN_VALUE,
        chargeRaw = manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Long.MIN_VALUE,
        minutesToFull = manager?.computeChargeTimeRemaining()?.takeIf { it >= 0 }?.let { (it / 60_000).toInt() },
    )
}

private fun readCpuSocTemperature(): Float? {
    return runCatching {
        val thermalDir = File("/sys/class/thermal")
        thermalDir.listFiles()?.forEach { zone ->
            if (zone.name.startsWith("thermal_zone")) {
                val typeFile = File(zone, "type")
                val tempFile = File(zone, "temp")
                if (typeFile.isFile && tempFile.isFile) {
                    val type = typeFile.readText().lowercase(Locale.US)
                    if (type.contains("cpu") || type.contains("soc") || type.contains("tsens") || type.contains("mtk")) {
                        val rawTemp = tempFile.readText().trim().toFloatOrNull()
                        if (rawTemp != null && rawTemp > 0) {
                            val temp = if (rawTemp > 1000f) rawTemp / 1000f else rawTemp
                            if (temp in 10f..115f) {
                                return temp
                            }
                        }
                    }
                }
            }
        }
        null
    }.getOrNull()
}

@Composable
fun FpsOverlay(
    session: EmulatorSession,
    visible: Boolean,
    compact: Boolean = false,
    metrics: Set<HudMetric> = HudMetric.entries.toSet(),
    modifier: Modifier = Modifier,
    pollHz: Int = 4,
    baseFontSizeSp: Float = 9f
) {
    if (!visible) return

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fps_overlay", Context.MODE_PRIVATE) }

    var offset by remember {
        mutableStateOf(Offset(prefs.getFloat("x", 0f), prefs.getFloat("y", 0f)))
    }
    var scale by remember {
        mutableStateOf(prefs.getFloat("scale", 1.0f))
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val currentContainerSize by rememberUpdatedState(containerSize)

    var fps by remember { mutableStateOf(0.0) }
    var frameMs by remember { mutableStateOf(0.0) }
    var presentSubmissionsPerSecond by remember { mutableStateOf<Double?>(null) }
    var cpu by remember { mutableStateOf(0f) }
    var gpu by remember { mutableStateOf<Int?>(null) }
    var ramUsed by remember { mutableStateOf(0L) }
    var ramTotal by remember { mutableStateOf(0L) }
    var batTemp by remember { mutableStateOf(0f) }
    var socTemp by remember { mutableStateOf<Float?>(null) }
    var powerLine by remember { mutableStateOf<String?>(null) }
    val batteryEstimate = remember { BatteryTimeEstimate() }

    LaunchedEffect(pollHz, compact, metrics) {
        val periodMs = 1000L / pollHz.coerceIn(1, 10)
        val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        var previousCpuMs = Process.getElapsedCpuTime()
        var previousWallNs = SystemClock.elapsedRealtimeNanos()
        var previousPresentCount = session.hostPresentSubmissionCount()
        var previousPresentNs = previousWallNs
        var previousGpu: GpuCounter? = null
        var gpuSource: GpuSource? = null
        var tick = 0L

        while (true) {
            val currentFps = session.averageFps()
            val currentFrameMs = session.lastFrameTimeMs()

            if (compact) {
                fps = currentFps
                frameMs = currentFrameMs
                delay(periodMs)
                continue
            }

            if (HudMetric.HOST_SUBMISSIONS in metrics) {
                val presentCount = session.hostPresentSubmissionCount()
                val presentNs = SystemClock.elapsedRealtimeNanos()
                presentSubmissionsPerSecond = presentSubmissionRate(
                    previousPresentCount, presentCount, presentNs - previousPresentNs,
                )
                previousPresentCount = presentCount
                previousPresentNs = presentNs
            }

            var nextPowerLine = powerLine
            val stats = withContext(Dispatchers.IO) {
                if (HudMetric.GPU in metrics && gpuSource == null) {
                    gpuSource = findGpuSource()
                }

                val newCpuMs = if (HudMetric.CPU in metrics) Process.getElapsedCpuTime() else previousCpuMs
                val newWallNs = SystemClock.elapsedRealtimeNanos()
                val cpuDeltaMs = newCpuMs - previousCpuMs
                val wallDeltaMs = (newWallNs - previousWallNs) / 1_000_000.0

                val cpuUsage = if (cpuDeltaMs >= 0L && wallDeltaMs > 0.0) {
                    (cpuDeltaMs.toDouble() / wallDeltaMs / cpuCount.toDouble() * 100.0)
                        .coerceIn(0.0, 100.0)
                        .toFloat()
                } else {
                    0f
                }

                previousCpuMs = newCpuMs
                previousWallNs = newWallNs

                var gpuUsage: Int? = null
                val source = gpuSource

                if (HudMetric.GPU in metrics && source?.directPercentFile != null) {
                    gpuUsage = readDirectGpuPercent(source.directPercentFile)
                } else if (HudMetric.GPU in metrics && source?.counterFile != null) {
                    val currentGpu = readGpuCounter(source.counterFile)
                    if (previousGpu != null && currentGpu != null) {
                        val busyDelta = currentGpu.busy - previousGpu!!.busy
                        val totalDelta = currentGpu.total - previousGpu!!.total
                        if (busyDelta >= 0L && totalDelta > 0L) {
                            gpuUsage = (busyDelta.toDouble() / totalDelta.toDouble() * 100.0)
                                .coerceIn(0.0, 100.0)
                                .roundToInt()
                        }
                    }
                    previousGpu = currentGpu
                }

                val bTemp = if (HudMetric.BATTERY_TEMPERATURE in metrics) readBatteryTemperature(context) else 0f
                val sTemp = if (HudMetric.SOC_TEMPERATURE in metrics) readCpuSocTemperature() else null
                val ram = if (HudMetric.RAM in metrics) readRamUsage(context) else (0L to 0L)
                // The fuel gauge once a second: four binder calls are not worth 4 Hz.
                if (HudMetric.POWER in metrics && tick % pollHz.coerceIn(1, 10) == 0L) {
                    val battery = runCatching { readBatterySample(context) }.getOrNull()
                    nextPowerLine = battery?.let {
                        val left = batteryEstimate.sample(SystemClock.elapsedRealtime(), it.percent, it.chargeRaw,
                            it.currentRaw, it.pluggedIn)
                        BatteryReadout.line(BatteryReadout.watts(it.currentRaw, it.voltageRaw), it.percent,
                            it.pluggedIn, left, it.minutesToFull)
                    } ?: "PWR N/A"
                }
                tick++
                Triple(cpuUsage, gpuUsage, ram) to (bTemp to sTemp)
            }

            fps = currentFps
            frameMs = currentFrameMs
            cpu = stats.first.first
            gpu = stats.first.second // inaccessible counters must show N/A, not a stale percent
            ramUsed = stats.first.third.first
            ramTotal = stats.first.third.second
            batTemp = stats.second.first
            socTemp = stats.second.second
            powerLine = nextPowerLine

            delay(periodMs)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        val usedGb = ramUsed / 1_073_741_824.0
        val totalGb = ramTotal / 1_073_741_824.0

        Text(
            text = buildString {
                append(String.format(Locale.US, "FPS %.0f · %.1f ms\n", fps, frameMs))
                if (!compact) {
                    if (HudMetric.HOST_SUBMISSIONS in metrics) presentSubmissionsPerSecond?.let {
                        append(String.format(Locale.US, "Vulkan submitted %.0f/s\n", it))
                    }
                    if (HudMetric.CPU in metrics) append(String.format(Locale.US, "CPU %.0f%%\n", cpu))
                    if (HudMetric.GPU in metrics) append(gpu?.let { "GPU $it%\n" } ?: "GPU N/A\n")
                    if (HudMetric.RAM in metrics) append(String.format(Locale.US, "RAM %.1f/%.1f GB\n", usedGb, totalGb))
                    if (HudMetric.BATTERY_TEMPERATURE in metrics) append(String.format(Locale.US, "BAT %.1f°C\n", batTemp))
                    if (HudMetric.SOC_TEMPERATURE in metrics) append(socTemp?.let { String.format(Locale.US, "SoC/CPU %.0f°C", it) } ?: "SoC/CPU N/A")
                    if (HudMetric.POWER in metrics) powerLine?.let {
                        if (isNotEmpty() && !endsWith("\n")) append("\n")
                        append(it)
                    }
                }
            },
            color = Color.White.copy(alpha = 0.85f),
            fontSize = (baseFontSizeSp * scale).sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                .padding(horizontal = (4 * scale).dp, vertical = (3 * scale).dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 2.5f)

                        val bounds = currentContainerSize
                        val maxX = maxOf(0f, bounds.width.toFloat() - size.width)
                        val maxY = maxOf(0f, bounds.height.toFloat() - size.height)

                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(0f, maxX),
                            y = (offset.y + pan.y).coerceIn(0f, maxY)
                        )

                        prefs.edit()
                            .putFloat("x", offset.x)
                            .putFloat("y", offset.y)
                            .putFloat("scale", scale)
                            .apply()
                    }
                }
        )
    }
}
