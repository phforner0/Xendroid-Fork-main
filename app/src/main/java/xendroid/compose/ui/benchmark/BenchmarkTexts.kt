package xendroid.compose.ui.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import xendroid.compose.R
import xendroid.compose.sessions.Benchmark

/* U02: what the comparison says (Benchmark's structured notes), in the shown language. */

@Composable
fun dimensionText(dimension: Benchmark.Dimension): String = stringResource(when (dimension) {
    Benchmark.Dimension.DRIVER -> R.string.bm_dim_driver
    Benchmark.Dimension.FRAME_GENERATION -> R.string.bm_dim_frame_generation
    Benchmark.Dimension.FPS_LIMIT -> R.string.bm_dim_fps_limit
    Benchmark.Dimension.VBLANK_CAP -> R.string.bm_dim_vblank_cap
    Benchmark.Dimension.DISPLAY_REFRESH -> R.string.bm_dim_display_refresh
})

@Composable
fun valueText(value: Benchmark.Value): String = when (value) {
    is Benchmark.Value.Driver -> value.name ?: stringResource(R.string.bm_val_unknown_driver)
    is Benchmark.Value.OnOff -> stringResource(if (value.on) R.string.bm_val_on else R.string.bm_val_off)
    is Benchmark.Value.FpsLimit -> if (value.fps == 0) stringResource(R.string.bm_val_unlimited) else stringResource(R.string.bm_val_fps, value.fps)
    is Benchmark.Value.Capped -> stringResource(if (value.capped) R.string.bm_val_capped else R.string.bm_val_uncapped)
    is Benchmark.Value.Hz -> stringResource(R.string.bm_val_hz, value.hz)
    is Benchmark.Value.Changing -> stringResource(R.string.bm_val_changing, value.values.map { valueText(it) }.joinToString(" / "))
}

@Composable
fun changeText(change: Benchmark.Change): String =
    "${dimensionText(change.dimension)} (${valueText(change.a)} → ${valueText(change.b)})"

@Composable
fun warningText(warning: Benchmark.Warning): String = when (warning) {
    Benchmark.Warning.NoSides -> stringResource(R.string.bm_w_no_sides)
    Benchmark.Warning.TooFewRuns -> stringResource(R.string.bm_w_too_few)
    is Benchmark.Warning.Unbalanced -> stringResource(R.string.bm_w_unbalanced, warning.order, warning.plan)
    is Benchmark.Warning.ShortRun -> stringResource(R.string.bm_w_short, warning.label.toString(), warning.seconds, Benchmark.MIN_SECONDS)
    is Benchmark.Warning.PacingChanged -> stringResource(R.string.bm_w_pacing, warning.label.toString())
    is Benchmark.Warning.MixedSide -> stringResource(R.string.bm_w_mixed, dimensionText(warning.dimension), warning.side.toString(),
        warning.values.map { valueText(it) }.joinToString(" / "))
    is Benchmark.Warning.MoreThanOne -> stringResource(R.string.bm_w_more_than_one, warning.changes.map { changeText(it) }.joinToString("; "))
    is Benchmark.Warning.Temperature -> stringResource(R.string.bm_w_temperature, warning.apartC)
}

@Composable
fun verdictText(verdict: Benchmark.Verdict): String = when (verdict) {
    Benchmark.Verdict.NotEnough -> stringResource(R.string.bm_v_not_enough)
    Benchmark.Verdict.NoPair -> stringResource(R.string.bm_v_no_pair)
    Benchmark.Verdict.FixWarnings -> stringResource(R.string.bm_v_fix_warnings)
    is Benchmark.Verdict.Faster -> stringResource(R.string.bm_v_faster, verdict.min, verdict.max)
    is Benchmark.Verdict.Slower -> stringResource(R.string.bm_v_slower, verdict.least, verdict.most)
    Benchmark.Verdict.Same -> stringResource(R.string.bm_v_same)
    is Benchmark.Verdict.Disagree -> stringResource(R.string.bm_v_disagree, verdict.deltas.joinToString { if (it > 0) "+$it" else "$it" })
}
