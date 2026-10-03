package xendroid.compose.ui.ingame

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import xendroid.compose.R
import xendroid.compose.core.PerformancePanel
import xendroid.compose.core.ThermalWatch

/**
 * 15n: the performance panel's lines under the HUD's live figures, in the app's language:
 * pacing (the last seconds and the session), what made the game stutter, heat, and the
 * settings in effect. Blank lines separate the groups.
 */
@Composable
internal fun performancePanelText(panel: PerformancePanel.Snapshot): String {
    val stutter = stringResource(R.string.panel_stutter)
    val pacing = listOf(
        panel.recent?.let {
            stringResource(R.string.panel_recent, panel.recentSeconds, PerformancePanel.bound(it.medianUnderMs),
                PerformancePanel.bound(it.p99UnderMs)) + if (it.stutters) " · $stutter" else ""
        } ?: stringResource(R.string.panel_recent_none),
        listOfNotNull(
            if (panel.fpsMedian != null && panel.fpsLow != null) {
                stringResource(R.string.panel_session_fps, panel.fpsMedian, panel.fpsLow)
            } else null,
            panel.run?.let { stringResource(R.string.panel_p99, PerformancePanel.bound(it.p99UnderMs)) },
        ).joinToString(" · ").ifEmpty { stringResource(R.string.panel_session_none) },
    )
    val seconds = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }
    val sources = listOfNotNull(
        panel.pipelines?.let { stringResource(R.string.panel_pipelines, it, seconds.format((panel.pipelineMs ?: 0L) / 1000.0)) },
        if (panel.audioConcealed != null && panel.audioBlocks != null) {
            if (panel.audioConcealed == 0L) stringResource(R.string.panel_audio_clean)
            else stringResource(R.string.panel_audio, panel.audioConcealed, panel.audioBlocks)
        } else null,
        stringResource(when (panel.thermal) {
            ThermalWatch.Level.OK -> R.string.panel_heat_ok
            ThermalWatch.Level.NEAR_LIMIT -> R.string.panel_heat_near
            ThermalWatch.Level.THROTTLING -> R.string.panel_heat_throttling
        }) + (panel.headroom?.let { " (%.2f)".format(java.util.Locale.ROOT, it) } ?: ""),
    )
    val settings = panel.settings.map { (setting, value) ->
        stringResource(when (setting) {
            PerformancePanel.Setting.DRIVER -> R.string.panel_driver
            PerformancePanel.Setting.SCALING -> R.string.panel_image
            PerformancePanel.Setting.FRAME_GENERATION -> R.string.panel_fg
            PerformancePanel.Setting.FPS_LIMIT -> R.string.panel_limit
            PerformancePanel.Setting.PERFORMANCE_MODE -> R.string.panel_performance
        }, value)
    } + if (panel.changedTotal == 0) listOf(stringResource(R.string.panel_changed_none)) else {
        listOf(stringResource(R.string.panel_changed, panel.changedTotal)) + panel.changed.map { "  $it" } +
            (panel.changedTotal - panel.changed.size).let { more ->
                if (more > 0) listOf("  " + stringResource(R.string.panel_changed_more, more)) else emptyList()
            }
    }
    return listOf(pacing, sources, settings).joinToString("\n\n") { it.joinToString("\n") }
}
