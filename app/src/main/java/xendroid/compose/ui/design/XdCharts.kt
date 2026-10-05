package xendroid.compose.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/** A labelled vertical line over a histogram: a percentile ([reference] = a target, dashed). */
data class ChartMarker(val at: Float, val label: String, val reference: Boolean = false)

/**
 * A histogram as the run reports keep them: one bar per bucket of [counts] over [range]
 * (bucket i covers range.start + i·step), [highlight] painted solid, markers labelled above,
 * axis labels under. Pure drawing; the caller picks the buckets.
 */
@Composable
fun XdHistogram(
    counts: List<Float>,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    highlight: Set<Int> = emptySet(),
    markers: List<ChartMarker> = emptyList(),
    axis: List<Pair<Float, String>> = emptyList(),
    height: androidx.compose.ui.unit.Dp = 76.dp,
) {
    val c = Xd.colors
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val full = constraints.maxWidth.toFloat()
    // Labels closer than this share no row: the second goes above the first.
    val minGap = with(androidx.compose.ui.platform.LocalDensity.current) { 92.dp.toPx() }
    val rows = run {
        var lastX = Float.NEGATIVE_INFINITY
        var lastRow = 1
        markers.sortedBy { it.at }.associateWith { m ->
            val x = ((m.at - range.start) / span).coerceIn(0f, 1f) * full
            val row = if (x - lastX < minGap && lastRow == 0) 1 else 0
            lastX = x; lastRow = row
            row
        }
    }
    val twoRows = rows.values.any { it == 1 }
    Column(Modifier.fillMaxWidth().padding(top = if (twoRows) 38.dp else 20.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
            val w = constraints.maxWidth.toFloat()
            Canvas(Modifier.fillMaxWidth().height(height)) {
                val peak = (counts.maxOrNull() ?: 0f).takeIf { it > 0f } ?: 1f
                val n = counts.size.coerceAtLeast(1)
                val bw = size.width / n
                counts.forEachIndexed { i, v ->
                    if (v <= 0f) return@forEachIndexed
                    val h = (v / peak) * size.height
                    drawRect(
                        color = if (i in highlight) c.acc else c.acc.copy(alpha = 0.48f),
                        topLeft = Offset(i * bw + bw * 0.08f, size.height - h),
                        size = Size(max(1f, bw * 0.84f), h),
                    )
                }
                drawLine(c.line2, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                for (m in markers) {
                    val x = ((m.at - range.start) / span).coerceIn(0f, 1f) * size.width
                    val top = if (rows[m] == 1) -33.dp.toPx() else -15.dp.toPx()
                    drawLine(
                        if (m.reference) c.fg3 else c.fg2, Offset(x, top), Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (m.reference) PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) else null,
                    )
                }
            }
            // Marker labels above the plot, kept inside its width.
            for (m in markers) {
                val x = ((m.at - range.start) / span).coerceIn(0f, 1f) * w
                ChartLabel(m.label, x, w, top = true, dim = m.reference, row = rows[m] ?: 0)
            }
        }
        if (axis.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp).padding(top = 4.dp)) {
            val w = constraints.maxWidth.toFloat()
            for ((at, label) in axis) ChartLabel(label, ((at - range.start) / span).coerceIn(0f, 1f) * w, w, top = false, dim = true)
        }
    }
    }
}

/** A small mono label centred on [x], nudged in from the edges. */
@Composable
private fun ChartLabel(text: String, x: Float, width: Float, top: Boolean, dim: Boolean, row: Int = 0) {
    val c = Xd.colors
    Layout(content = {
        Text(text, style = XdText.monoSm.copy(fontSize = if (top) 10.5.sp else 10.sp, fontWeight = if (top && !dim) FontWeight.SemiBold else FontWeight.Medium),
            color = if (dim) c.fg3 else c.fg, maxLines = 1)
    }, modifier = if (top) Modifier.offset(y = if (row == 1) (-36).dp else (-18).dp) else Modifier) { measurables, constraints ->
        val p = measurables.first().measure(Constraints())
        val left = (x - p.width / 2f).coerceIn(0f, max(0f, width - p.width)).roundToInt()
        layout(constraints.maxWidth, p.height) { p.place(left, 0) }
    }
}

/** FPS per second of play ([perSecond]: seconds at each FPS, index = FPS) with the median and 5 %. */
@Composable
fun FpsChart(perSecond: List<Int>, median: Int?, low: Int?, limit: Int?, medianLabel: String, lowLabel: String, modifier: Modifier = Modifier) {
    val top = maxOf(30, perSecond.indexOfLast { it > 0 } + 1, limit ?: 0).let { ((it + 14) / 15) * 15 }
    val counts = List(top + 1) { i -> perSecond.getOrElse(i) { 0 }.toFloat() }
    val markers = buildList {
        if (median != null) add(ChartMarker(median.toFloat(), "$medianLabel $median"))
        if (low != null && low != median) add(ChartMarker(low.toFloat(), "$lowLabel $low"))
        if (limit != null && limit > 0 && limit != median) add(ChartMarker(limit.toFloat(), "$limit", reference = true))
    }
    XdHistogram(counts, 0f..top.toFloat(), modifier, highlight = setOfNotNull(median), markers = markers,
        axis = (0..top step 15).map { it.toFloat() to "$it" })
}

/** Frames by frame time (1 ms buckets, the last open) shown to 50 ms, with the median and 99 %. */
@Composable
fun FrameTimeChart(perMs: List<Long>, median: Int?, p99: Int?, modifier: Modifier = Modifier) {
    val top = 50
    val counts = List(top + 1) { i ->
        if (i < top) perMs.getOrElse(i) { 0L }.toFloat() else perMs.drop(top).sum().toFloat()
    }
    val markers = buildList {
        if (median != null) add(ChartMarker(median.coerceAtMost(top).toFloat(), "$median ms"))
        if (p99 != null && p99 != median) add(ChartMarker(p99.coerceAtMost(top).toFloat(), "99% $p99 ms"))
    }
    XdHistogram(counts, 0f..top.toFloat(), modifier, highlight = setOfNotNull(median?.minus(1)), markers = markers,
        axis = listOf(0f to "0", 16.7f to "16,7", 33.3f to "33,3", 50f to "50+"))
}

/** Space reserved under a chart for its caption. */
@Composable
fun ChartCaption(text: String) {
    Box(Modifier.padding(top = 2.dp)) { Text(text, style = XdText.tiny, color = Xd.colors.fg3) }
}
