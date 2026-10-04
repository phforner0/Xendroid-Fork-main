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
import xendroid.compose.core.HudDetail
import xendroid.compose.core.HudMetric
import xendroid.compose.core.PerformancePanel
import xendroid.compose.core.HudLook
import xendroid.compose.core.HudPlacement
import xendroid.compose.core.HudPlacements
import xendroid.compose.core.KgslMemory
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import xendroid.compose.ui.design.XdFonts
import xendroid.compose.ui.ingame.HudTone
import xendroid.compose.ui.ingame.PanelSection
import xendroid.compose.ui.ingame.performancePanelSections

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

/** 15g: the HUD's place, size and look in its preferences ([HudPlacements]' keys). */
internal class HudPreferences(private val prefs: android.content.SharedPreferences) : HudPlacements.Store {
    override fun float(key: String): Float? =
        if (prefs.contains(key)) runCatching { prefs.getFloat(key, 0f) }.getOrNull() else null
    override fun string(key: String): String? = runCatching { prefs.getString(key, null) }.getOrNull()
    override fun put(values: Map<String, Any>) {
        prefs.edit().apply {
            values.forEach { (key, value) ->
                when (value) {
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                }
            }
        }.apply()
    }

    companion object {
        fun of(context: Context) = HudPreferences(context.getSharedPreferences("fps_overlay", Context.MODE_PRIVATE))
    }
}

/** The battery as the power row shows it ([BatteryReadout]). */
internal data class PowerReading(val watts: Double?, val percent: Int?, val pluggedIn: Boolean, val minutesLeft: Int?, val minutesToFull: Int?)

/** One sample of what the HUD shows; null where the phone does not report it (the row is left out). */
internal data class HudSample(
    val fps: Double = 0.0,
    val frameMs: Double = 0.0,
    val submissionsPerSecond: Double? = null,
    val cpu: Float = 0f,
    val gpu: Int? = null,
    val ramUsed: Long = 0L,
    val ramTotal: Long = 0L,
    val batteryCelsius: Float = 0f,
    val socCelsius: Float? = null,
    val power: PowerReading? = null,
    val gpuMemory: Long? = null,
)

/** A row of the HUD: what is measured and its value; [tone] colours the value. */
private data class HudLine(val label: String, val value: AnnotatedString, val tone: HudTone = HudTone.NORMAL)

private val HudText = Color.White.copy(alpha = 0.92f)
private val HudLabel = Color.White.copy(alpha = 0.62f)
private val HudGood = Color(0xFF9BE37F)
private val HudWarn = Color(0xFFF3CF55)
private val HudBad = Color(0xFFFF8B84)

private fun toneColor(tone: HudTone): Color = when (tone) {
    HudTone.NORMAL -> HudText
    HudTone.GOOD -> HudGood
    HudTone.WARN -> HudWarn
    HudTone.BAD -> HudBad
}

/** Numbers as the phone's language writes them ("16,0" in Portuguese). */
private fun fmt(pattern: String, vararg args: Any): String = String.format(Locale.getDefault(), pattern, *args)

/**
 * [titleId]: the running game, whose own HUD place and size are used and kept (15g); [look]: box,
 * outline or plain text. [detail] (15n): FPS only, the [metrics] chosen in the menu, or the
 * performance panel, which shows every metric and then [panel]. Redesign: rows of a label and its
 * value in the app's mono font, the frame rate in green and heat near or past the limit in yellow
 * or red; a metric the phone does not report is left out instead of reading "N/A".
 */
@Composable
fun FpsOverlay(
    session: EmulatorSession,
    visible: Boolean,
    detail: HudDetail = HudDetail.FULL,
    metrics: Set<HudMetric> = HudMetric.entries.toSet(),
    modifier: Modifier = Modifier,
    pollHz: Int = 4,
    baseFontSizeSp: Float = 10f,
    titleId: String? = null,
    look: HudLook = HudLook.BOX,
    panel: PerformancePanel.Snapshot? = null,
) {
    if (!visible) return
    val compact = detail == HudDetail.COMPACT
    @Suppress("NAME_SHADOWING")
    val metrics = if (detail == HudDetail.PANEL) HudMetric.entries.toSet() else metrics
    val panelSections = if (detail == HudDetail.PANEL) panel?.let { performancePanelSections(it) } else null

    val context = LocalContext.current
    val store = remember { HudPreferences.of(context) }
    val placed = remember(titleId) { HudPlacements.read(store, titleId) }

    var offset by remember(titleId) { mutableStateOf(Offset(placed.x, placed.y)) }
    var scale by remember(titleId) { mutableStateOf(placed.scale) }
    val currentLook by rememberUpdatedState(look)

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
    var power by remember { mutableStateOf<PowerReading?>(null) }
    var gpuMemory by remember { mutableStateOf<Long?>(null) }
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

            var nextPower = power
            var nextGpuMemory = gpuMemory
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
                    nextPower = battery?.let {
                        val left = batteryEstimate.sample(SystemClock.elapsedRealtime(), it.percent, it.chargeRaw,
                            it.currentRaw, it.pluggedIn)
                        PowerReading(BatteryReadout.watts(it.currentRaw, it.voltageRaw), it.percent, it.pluggedIn, left, it.minutesToFull)
                    }
                }
                // 15g: KGSL's total once a second (one small sysfs read).
                if (HudMetric.GPU_MEMORY in metrics && tick % pollHz.coerceIn(1, 10) == 0L) {
                    nextGpuMemory = runCatching { KgslMemory.parse(File(KgslMemory.PATH).readText()) }.getOrNull()
                }
                tick++
                Triple(cpuUsage, gpuUsage, ram) to (bTemp to sTemp)
            }

            fps = currentFps
            frameMs = currentFrameMs
            cpu = stats.first.first
            gpu = stats.first.second // an unreadable counter leaves the row out, never a stale percent
            ramUsed = stats.first.third.first
            ramTotal = stats.first.third.second
            batTemp = stats.second.first
            socTemp = stats.second.second
            power = nextPower
            gpuMemory = nextGpuMemory

            delay(periodMs)
        }
    }

    val sample = HudSample(fps, frameMs, presentSubmissionsPerSecond, cpu, gpu, ramUsed, ramTotal, batTemp, socTemp, power, gpuMemory)
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        HudView(
            sample, detail, metrics, look, scale, baseFontSizeSp, panelSections,
            Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .pointerInput(titleId) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(HudPlacements.MIN_SCALE, HudPlacements.MAX_SCALE)

                        val bounds = currentContainerSize
                        val maxX = maxOf(0f, bounds.width.toFloat() - size.width)
                        val maxY = maxOf(0f, bounds.height.toFloat() - size.height)

                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(0f, maxX),
                            y = (offset.y + pan.y).coerceIn(0f, maxY)
                        )

                        // 15g: kept for this game, and as where the next game without its own starts.
                        HudPlacements.write(store, titleId, HudPlacement(offset.x, offset.y, scale, currentLook))
                    }
                },
        )
    }
}

/**
 * The HUD as drawn, from one [sample]: FPS alone (compact), the chosen [metrics] as rows of a
 * label and its value, or (the panel, [panelSections]) every metric under "Now" and then the
 * panel's groups. [look]: box, outline or plain text; [scale]: the player's pinch.
 */
@Composable
internal fun HudView(
    sample: HudSample,
    detail: HudDetail,
    metrics: Set<HudMetric>,
    look: HudLook,
    scale: Float = 1f,
    baseFontSizeSp: Float = 10f,
    panelSections: List<PanelSection>? = null,
    modifier: Modifier = Modifier,
) {
    val compact = detail == HudDetail.COMPACT
    val perSecond = stringResource(R.string.xd_hud_per_second)
    val charging = stringResource(R.string.xd_hud_charging)
    val fullIn = stringResource(R.string.xd_hud_full_in)
    val fpsValue = buildAnnotatedString {
        withStyle(SpanStyle(color = HudGood)) { append(fmt("%.0f", sample.fps)) }
        withStyle(SpanStyle(color = HudLabel)) { append(" · ") }
        append(fmt("%.1f ms", sample.frameMs))
    }
    val rows = if (compact) emptyList() else buildList {
        add(HudLine("FPS", fpsValue))
        if (HudMetric.HOST_SUBMISSIONS in metrics) sample.submissionsPerSecond?.let {
            add(HudLine(stringResource(R.string.xd_hud_vulkan), AnnotatedString(perSecond.format(fmt("%.0f", it)))))
        }
        if (HudMetric.CPU in metrics) add(HudLine("CPU", AnnotatedString(fmt("%.0f%%", sample.cpu))))
        if (HudMetric.GPU in metrics) sample.gpu?.let { add(HudLine("GPU", AnnotatedString(fmt("%d%%", it)))) }
        if (HudMetric.GPU_MEMORY in metrics) sample.gpuMemory?.let { bytes ->
            add(HudLine(stringResource(R.string.xd_hud_gpu_memory), AnnotatedString(
                if (bytes >= 1L shl 30) fmt("%.2f GB", bytes / 1_073_741_824.0) else fmt("%.0f MB", bytes / 1_048_576.0))))
        }
        if (HudMetric.RAM in metrics && sample.ramTotal > 0) {
            add(HudLine("RAM", AnnotatedString(fmt("%.1f / %.1f GB", sample.ramUsed / 1_073_741_824.0, sample.ramTotal / 1_073_741_824.0))))
        }
        if (HudMetric.BATTERY_TEMPERATURE in metrics && sample.batteryCelsius > 0f) {
            val t = sample.batteryCelsius
            add(HudLine(stringResource(R.string.xd_hud_battery), AnnotatedString(fmt("%.1f °C", t)),
                when { t >= 45f -> HudTone.BAD; t >= 40f -> HudTone.WARN; else -> HudTone.NORMAL }))
        }
        if (HudMetric.SOC_TEMPERATURE in metrics) sample.socCelsius?.let { t ->
            add(HudLine("SoC", AnnotatedString(fmt("%.0f °C", t)),
                when { t >= 95f -> HudTone.BAD; t >= 80f -> HudTone.WARN; else -> HudTone.NORMAL }))
        }
        if (HudMetric.POWER in metrics) sample.power?.let { p ->
            val parts = listOfNotNull(
                if (p.pluggedIn) charging else p.watts?.let { fmt("%.1f W", it) },
                p.percent?.let { "$it%" },
                if (p.pluggedIn) p.minutesToFull?.let { fullIn.format(BatteryReadout.duration(it)) }
                else p.minutesLeft?.let { "~" + BatteryReadout.duration(it) },
            )
            if (parts.isNotEmpty()) add(HudLine(stringResource(R.string.xd_hud_power), AnnotatedString(parts.joinToString(" · "))))
        }
    }
    val style = TextStyle(
        fontFamily = XdFonts.mono,
        fontWeight = FontWeight.SemiBold,
        fontSize = (baseFontSizeSp * scale).sp,
        lineHeight = (baseFontSizeSp * scale * 1.4f).sp,
        fontFeatureSettings = "tnum",
        // 15g: an outline (a dark halo around the letters) instead of the box, or plain text.
        shadow = if (look == HudLook.OUTLINE) Shadow(Color.Black, Offset(1f, 1f), blurRadius = 4f) else null,
    )
    Column(
        modifier
            .then(if (look == HudLook.BOX) Modifier.background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape((8 * scale).dp)) else Modifier)
            .padding(horizontal = (8 * scale).dp, vertical = (5 * scale).dp)
            // The rows as wide as the widest, values to the right; the panel wraps its longer lines.
            .widthIn(max = (if (panelSections != null) 300 else 260).times(scale).dp)
            .width(IntrinsicSize.Max),
    ) {
        if (compact) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = HudGood)) { append(fmt("%.0f", sample.fps)) }
                append(" FPS")
                withStyle(SpanStyle(color = HudLabel)) { append(" · ") }
                append(fmt("%.1f ms", sample.frameMs))
            }, style = style, color = HudText, maxLines = 1)
        } else {
            if (panelSections != null) HudHeading(stringResource(R.string.xd_hud_now), style)
            rows.forEach { line -> HudRow(line, style, scale) }
            panelSections?.forEach { section ->
                Box(Modifier.fillMaxWidth().padding(vertical = (4 * scale).dp).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                HudHeading(section.title, style)
                section.lines.forEach { line -> Text(line.text, style = style, color = toneColor(line.tone)) }
            }
        }
    }
}

@Composable
private fun HudRow(line: HudLine, style: TextStyle, scale: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(line.label, style = style, color = HudLabel, maxLines = 1)
        Text(line.value, style = style, color = toneColor(line.tone), maxLines = 1, modifier = Modifier.padding(start = (14 * scale).dp))
    }
}

@Composable
private fun HudHeading(text: String, style: TextStyle) {
    Text(text.uppercase(), style = style.copy(fontFamily = XdFonts.body, fontWeight = FontWeight.Bold, fontSize = style.fontSize * 0.82f,
        letterSpacing = 0.12.em), color = HudLabel, modifier = Modifier.padding(bottom = 2.dp))
}
