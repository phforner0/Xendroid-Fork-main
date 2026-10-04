package xendroid.compose.ui.ingame

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import xendroid.compose.R
import xendroid.compose.core.PerformancePanel
import xendroid.compose.core.ThermalWatch

/** How a HUD value reads: plain, good (the frame rate), near a limit, or past it. */
enum class HudTone { NORMAL, GOOD, WARN, BAD }

/** A line of the performance panel and how it reads. */
data class PanelLine(val text: String, val tone: HudTone = HudTone.NORMAL)

/** A titled group of the performance panel ("Pacing", "Work", "Settings in effect"). */
data class PanelSection(val title: String, val lines: List<PanelLine>)

/**
 * 15n, redesign: the performance panel's groups under the HUD's live figures, in the app's
 * language: pacing (the last seconds and the session, stutter in yellow), what made the game
 * stutter and the heat (near the limit in yellow, reducing performance in red), and the settings
 * in effect.
 */
@Composable
internal fun performancePanelSections(panel: PerformancePanel.Snapshot): List<PanelSection> {
    val stutter = stringResource(R.string.panel_stutter)
    val pacing = listOf(
        panel.recent?.let {
            PanelLine(stringResource(R.string.panel_recent, panel.recentSeconds, PerformancePanel.bound(it.medianUnderMs),
                PerformancePanel.bound(it.p99UnderMs)) + if (it.stutters) " · $stutter" else "",
                if (it.stutters) HudTone.WARN else HudTone.NORMAL)
        } ?: PanelLine(stringResource(R.string.panel_recent_none)),
        PanelLine(listOfNotNull(
            if (panel.fpsMedian != null && panel.fpsLow != null) {
                stringResource(R.string.panel_session_fps, panel.fpsMedian, panel.fpsLow)
            } else null,
            panel.run?.let { stringResource(R.string.panel_p99, PerformancePanel.bound(it.p99UnderMs)) },
        ).joinToString(" · ").ifEmpty { stringResource(R.string.panel_session_none) }),
    )
    val seconds = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }
    val work = listOfNotNull(
        panel.pipelines?.let { PanelLine(stringResource(R.string.panel_pipelines, it, seconds.format((panel.pipelineMs ?: 0L) / 1000.0))) },
        if (panel.audioConcealed != null && panel.audioBlocks != null) {
            if (panel.audioConcealed == 0L) PanelLine(stringResource(R.string.panel_audio_clean))
            else PanelLine(stringResource(R.string.panel_audio, panel.audioConcealed, panel.audioBlocks), HudTone.WARN)
        } else null,
        PanelLine(stringResource(when (panel.thermal) {
            ThermalWatch.Level.OK -> R.string.panel_heat_ok
            ThermalWatch.Level.NEAR_LIMIT -> R.string.panel_heat_near
            ThermalWatch.Level.THROTTLING -> R.string.panel_heat_throttling
        }) + (panel.headroom?.let { " (%.2f)".format(java.util.Locale.getDefault(), it) } ?: ""), when (panel.thermal) {
            ThermalWatch.Level.OK -> HudTone.NORMAL
            ThermalWatch.Level.NEAR_LIMIT -> HudTone.WARN
            ThermalWatch.Level.THROTTLING -> HudTone.BAD
        }),
    )
    val settings = panel.settings.map { (setting, value) ->
        PanelLine(stringResource(when (setting) {
            PerformancePanel.Setting.DRIVER -> R.string.panel_driver
            PerformancePanel.Setting.SCALING -> R.string.panel_image
            PerformancePanel.Setting.FRAME_GENERATION -> R.string.panel_fg
            PerformancePanel.Setting.FPS_LIMIT -> R.string.panel_limit
            PerformancePanel.Setting.PERFORMANCE_MODE -> R.string.panel_performance
        }, value))
    } + if (panel.changedTotal == 0) listOf(PanelLine(stringResource(R.string.panel_changed_none))) else {
        listOf(PanelLine(stringResource(R.string.panel_changed, panel.changedTotal))) + panel.changed.map { PanelLine("  $it") } +
            (panel.changedTotal - panel.changed.size).let { more ->
                if (more > 0) listOf(PanelLine("  " + stringResource(R.string.panel_changed_more, more))) else emptyList()
            }
    }
    return listOf(
        PanelSection(stringResource(R.string.xd_hud_pacing), pacing),
        PanelSection(stringResource(R.string.xd_hud_work), work),
        PanelSection(stringResource(R.string.xd_hud_settings), settings),
    )
}
