package xendroid.compose.ui.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xendroid.compose.R
import xendroid.compose.ui.panel.GuestPanelOption
import xendroid.compose.settings.FpsConfigSnapshot
import xendroid.compose.core.HudMetric
import xendroid.compose.core.SessionLogs
import xendroid.compose.core.PresentationState
import xendroid.compose.BuildConfig

/** A narrow touch target; Back or a controller opens the same menu without touching the screen. */
@Composable
fun InGameMenuHandle(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.width(28.dp).fillMaxHeight().pointerInput(onOpen) {
            var dragged = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragged = 0f },
                onHorizontalDrag = { change, amount ->
                    dragged += amount
                    if (dragged > 48.dp.toPx()) {
                        onOpen()
                        dragged = Float.NEGATIVE_INFINITY
                    }
                    change.consume()
                },
                onDragEnd = { dragged = 0f },
            )
        },
    ) {
        val description = stringResource(R.string.menu_open)
        Text(
            text = "☰",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.48f))
                .clickable(onClick = onOpen)
                .semantics { contentDescription = description; role = Role.Button }
                .padding(horizontal = 5.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InGameMenu(
    state: InGameMenuState,
    paused: Boolean,
    fpsLimit: Int,
    fpsConfig: FpsConfigSnapshot,
    presentation: PresentationState,
    fgPreset: Int,
    lsfgAvailable: Boolean,
    extensionLabels: Map<InGameAction, String>,
    performanceHud: Boolean,
    compactHud: Boolean,
    hudMetrics: Set<HudMetric>,
    touchControls: Boolean,
    adaptiveSticks: Boolean,
    stretch: Boolean,
    volume: Int,
    sessionInfo: String,
    /** While phone controllers are on: address, code and players (Controls page). */
    phoneControllers: String?,
    /** While frame generation runs: the advisory budget verdict (Graphics page). */
    frameGenerationBudget: String? = null,
    logSessions: List<SessionLogs.Session>,
    onLogChoice: (Int) -> Unit,
    onPage: (InGamePage) -> Unit,
    onSelect: (Int) -> Unit,
    onAction: (InGameAction) -> Unit,
    onQuitChoice: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.54f))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        contentAlignment = Alignment.CenterStart,
    ) {
        val sheetWidth = minOf(maxWidth * 0.88f, 400.dp)
        // Hardware navigation changes the selected option, not Compose focus. Scroll the
        // corresponding row into view rather than leaving the highlight off-screen.
        val scrollState = remember(state.page, state.confirmingQuit, state.logPicker) { ScrollState(0) }
        val selectedRow = remember(state.page, state.confirmingQuit, state.logPicker) { BringIntoViewRequester() }
        LaunchedEffect(state.page, state.selected, state.confirmingQuit, state.logPicker) {
            selectedRow.bringIntoView()
        }
        Surface(
            modifier = Modifier.width(sheetWidth).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.fillMaxHeight().padding(if (maxHeight < 400.dp) 12.dp else 20.dp)) {
                Text(
                    "XenDroid${fpsConfig.titleId?.let { " · $it" }.orEmpty()}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(if (paused) R.string.menu_paused else R.string.menu_running),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!state.confirmingQuit && !state.logPicker) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        InGamePage.entries.forEach { page ->
                            TextButton(onClick = { onPage(page) }) {
                                Text(
                                    page.label(),
                                    color = if (page == state.page) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                // Header, tabs and footer stay visible even on short screens. Only options
                // scroll; hardware selection is kept visible by the requester above.
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState)) {
                    if (state.confirmingQuit) {
                        Text(stringResource(R.string.menu_exit_question), modifier = Modifier.padding(top = 22.dp))
                        Text(stringResource(R.string.menu_exit_warning), style = MaterialTheme.typography.bodySmall)
                        GuestPanelOption(
                            label = stringResource(R.string.menu_cancel), selected = state.selected == 0,
                            onClick = { onQuitChoice(false) },
                            modifier = Modifier.padding(top = 12.dp)
                                .then(if (state.selected == 0) Modifier.bringIntoViewRequester(selectedRow) else Modifier),
                        )
                        GuestPanelOption(
                            label = stringResource(R.string.menu_exit_game), selected = state.selected == 1,
                            onClick = { onQuitChoice(true) },
                            modifier = Modifier.padding(top = 8.dp)
                                .then(if (state.selected == 1) Modifier.bringIntoViewRequester(selectedRow) else Modifier),
                        )
                    } else if (state.logPicker) {
                        Text(stringResource(R.string.menu_logs_title), style = MaterialTheme.typography.titleSmall)
                        val labels = listOf(stringResource(R.string.menu_logs_all)) +
                            logSessions.map { stringResource(R.string.menu_logs_session, it.label, (it.bytes / 1024).toInt()) } +
                            stringResource(R.string.menu_back)
                        labels.forEachIndexed { index, label ->
                            GuestPanelOption(label, selected = state.selected == index,
                                modifier = Modifier.padding(top = 8.dp).then(
                                    if (state.selected == index) Modifier.bringIntoViewRequester(selectedRow) else Modifier),
                                onClick = { onLogChoice(index) })
                        }
                    } else {
                        if (state.page == InGamePage.GRAPHICS && state.developer) {
                            Text(presentation.label, style = MaterialTheme.typography.bodySmall)
                            frameGenerationBudget?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(stringResource(R.string.menu_fg_experimental), style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.page == InGamePage.SYSTEM) {
                            Text(stringResource(R.string.menu_frame_limit_title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.menu_live_limit, fpsText(fpsLimit)), style = MaterialTheme.typography.bodySmall)
                            Text(
                                when {
                                    fpsConfig.saving -> stringResource(R.string.menu_saving_config)
                                    fpsConfig.loading -> stringResource(R.string.menu_reading_config)
                                    fpsConfig.error != null -> fpsConfig.error
                                    else -> stringResource(R.string.menu_next_launch, fpsText(fpsConfig.globalLimit),
                                        if (fpsConfig.titleId == null) stringResource(R.string.menu_game_id_unavailable)
                                        else stringResource(R.string.menu_game_limit,
                                            fpsConfig.gameLimit?.let { fpsText(it) } ?: stringResource(R.string.menu_inherits_global)))
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        state.actions().forEachIndexed { index, action ->
                            GuestPanelOption(
                                label = if (action == InGameAction.MORE_OPTIONS) {
                                    if (state.page in state.advanced) stringResource(R.string.menu_fewer_options)
                                    else stringResource(R.string.menu_more_options, state.advancedCount())
                                } else {
                                    extensionLabels[action] ?: action.label(fpsLimit, performanceHud, compactHud, touchControls, adaptiveSticks, stretch, hudMetrics, presentation, fgPreset, volume)
                                },
                                selected = index == state.selected,
                                enabled = (action != InGameAction.ADAPTIVE_STICKS || fpsConfig.titleId != null) &&
                                    (!action.isFrameGeneration || BuildConfig.DEBUG) &&
                                    (action != InGameAction.LSFG || lsfgAvailable) &&
                                    (!action.isPersistence ||
                                        (!fpsConfig.loading && !fpsConfig.saving && fpsConfig.error == null &&
                                            (action == InGameAction.SAVE_GLOBAL_FPS || action == InGameAction.STRETCH ||
                                                fpsConfig.titleId != null))),
                                modifier = Modifier.padding(top = 8.dp)
                                    .then(if (index == state.selected) Modifier.bringIntoViewRequester(selectedRow) else Modifier),
                                onClick = {
                                    onSelect(index)
                                    onAction(action)
                                },
                            )
                        }
                        if (state.page == InGamePage.GRAPHICS) {
                            Text(
                                stringResource(R.string.menu_graphics_note),
                                modifier = Modifier.padding(top = 14.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (!BuildConfig.DEBUG && state.developer) Text(stringResource(R.string.menu_fg_gated), style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.page == InGamePage.SYSTEM) {
                            Text(stringResource(R.string.menu_system_note), style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.page == InGamePage.CONTROLS) {
                            phoneControllers?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                            }
                            Text(stringResource(R.string.menu_adaptive_note), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.menu_touch_camera_note), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.menu_phones_note), style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.page == InGamePage.SESSION) {
                            Text(sessionInfo, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
                if (!state.confirmingQuit && !state.logPicker) {
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onAction(InGameAction.RESUME) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.menu_continue))
                        }
                        TextButton(onClick = { onAction(InGameAction.QUIT) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.menu_exit_game))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InGamePage.label(): String = stringResource(
    when (this) {
        InGamePage.GRAPHICS -> R.string.menu_tab_graphics
        InGamePage.SYSTEM -> R.string.menu_tab_system
        InGamePage.CONTROLS -> R.string.menu_tab_controls
        InGamePage.SESSION -> R.string.menu_tab_session
    },
)

@Composable
private fun onOff(on: Boolean): String = stringResource(if (on) R.string.menu_on else R.string.menu_off)

@Composable
private fun InGameAction.label(fps: Int, hud: Boolean, compact: Boolean, touch: Boolean, adaptive: Boolean, stretch: Boolean,
                             metrics: Set<HudMetric>, presentation: PresentationState, preset: Int, volume: Int): String {
    fun check(on: Boolean) = if (on) " ✓" else ""
    return when (this) {
        InGameAction.FPS_UNLIMITED -> stringResource(R.string.menu_unlimited) + check(fps == 0)
        InGameAction.FPS_30 -> stringResource(R.string.menu_fps, 30) + check(fps == 30)
        InGameAction.FPS_45 -> stringResource(R.string.menu_fps, 45) + check(fps == 45)
        InGameAction.FPS_60 -> stringResource(R.string.menu_fps, 60) + check(fps == 60)
        InGameAction.FPS_90 -> stringResource(R.string.menu_fps, 90) + check(fps == 90)
        InGameAction.FPS_120 -> stringResource(R.string.menu_fps, 120) + check(fps == 120)
        InGameAction.SAVE_GAME_FPS -> stringResource(R.string.menu_save_game_fps)
        InGameAction.INHERIT_GAME_FPS -> stringResource(R.string.menu_inherit_game_fps)
        InGameAction.SAVE_GLOBAL_FPS -> stringResource(R.string.menu_save_global_fps)
        InGameAction.STRETCH -> stringResource(R.string.menu_stretch_next, onOff(stretch))
        InGameAction.DISPLAY_FIT -> stringResource(R.string.menu_display_fit) + check(presentation.displayMode == 0)
        InGameAction.DISPLAY_FILL -> stringResource(R.string.menu_display_fill) + check(presentation.displayMode == 1)
        InGameAction.DISPLAY_STRETCH -> stringResource(R.string.menu_display_stretch) + check(presentation.displayMode == 2)
        InGameAction.DISPLAY_INTEGER -> stringResource(R.string.menu_display_integer) + check(presentation.displayMode == 3)
        InGameAction.WINFG -> stringResource(R.string.menu_winfg, onOff(presentation.requested))
        InGameAction.WINFG_PRESET -> stringResource(R.string.menu_winfg_preset, stringResource(
            listOf(R.string.menu_preset_quality, R.string.menu_preset_balanced, R.string.menu_preset_performance)[preset.coerceIn(0, 2)]))
        InGameAction.LSFG -> stringResource(R.string.menu_lsfg, onOff(presentation.requested && presentation.engine == 1))
        InGameAction.LSFG_MULTIPLIER -> stringResource(R.string.menu_lsfg_multiplier)
        InGameAction.IMPORT_LSFG_DLL -> stringResource(R.string.menu_import_lsfg)
        InGameAction.CLEAR_LSFG_CACHE -> stringResource(R.string.menu_clear_lsfg)
        InGameAction.PERFORMANCE_HUD -> stringResource(R.string.menu_performance_hud, onOff(hud))
        InGameAction.HUD_STYLE -> stringResource(R.string.menu_hud_detail,
            stringResource(if (compact) R.string.menu_hud_compact else R.string.menu_hud_full))
        InGameAction.HUD_HOST_SUBMISSIONS -> HudMetric.HOST_SUBMISSIONS.label(metrics)
        InGameAction.HUD_CPU -> HudMetric.CPU.label(metrics)
        InGameAction.HUD_GPU -> HudMetric.GPU.label(metrics)
        InGameAction.HUD_RAM -> HudMetric.RAM.label(metrics)
        InGameAction.HUD_BATTERY -> HudMetric.BATTERY_TEMPERATURE.label(metrics)
        InGameAction.HUD_SOC -> HudMetric.SOC_TEMPERATURE.label(metrics)
        InGameAction.HUD_POWER -> HudMetric.POWER.label(metrics)
        InGameAction.TOUCH_CONTROLS -> stringResource(R.string.menu_touch_controls, onOff(touch))
        InGameAction.ADAPTIVE_STICKS -> stringResource(R.string.menu_adaptive_sticks, onOff(adaptive))
        InGameAction.EDIT_TOUCH_LAYOUT -> stringResource(R.string.menu_edit_layout)
        InGameAction.RESUME -> stringResource(R.string.menu_continue)
        InGameAction.SHARE_LOGS -> stringResource(R.string.menu_share_logs)
        InGameAction.QUIT -> stringResource(R.string.menu_exit_game)
        InGameAction.MUTE -> if (volume == 0) stringResource(R.string.menu_muted) else stringResource(R.string.menu_volume, volume)
        InGameAction.VOLUME_DOWN -> stringResource(R.string.menu_volume_down)
        InGameAction.VOLUME_UP -> stringResource(R.string.menu_volume_up)
        InGameAction.REFRESH_RATE -> stringResource(R.string.menu_refresh_rate)
        InGameAction.SUSTAINED_PERFORMANCE -> stringResource(R.string.menu_sustained)
        InGameAction.BACKGROUND_POLICY -> stringResource(R.string.menu_background)
        InGameAction.GYRO_CAMERA -> stringResource(R.string.menu_gyro_camera)
        InGameAction.EXTERNAL_DISPLAY -> stringResource(R.string.menu_external_display)
        InGameAction.SCALING_EFFECT -> stringResource(R.string.menu_scaling)
        InGameAction.PERFORMANCE_HINTS -> stringResource(R.string.menu_hints)
        InGameAction.COLOR_FILTER -> stringResource(R.string.menu_color_filter)
        InGameAction.GYRO_CALIBRATE -> stringResource(R.string.menu_gyro_calibrate)
        InGameAction.GYRO_SENSITIVITY -> stringResource(R.string.menu_gyro_sensitivity)
        InGameAction.CONTROLLER_RUMBLE -> stringResource(R.string.menu_rumble)
        InGameAction.PHONE_CONTROLLERS -> stringResource(R.string.menu_phone_controllers)
        InGameAction.MORE_OPTIONS -> stringResource(R.string.menu_fewer_options)
        InGameAction.TOUCH_CAMERA -> stringResource(R.string.menu_touch_camera, onOff(false))
        InGameAction.MARK_SCENE -> stringResource(R.string.menu_mark_scene, 0)
        InGameAction.DRIVER_INFO -> stringResource(R.string.menu_driver_unknown)
    }
}

@Composable
private fun HudMetric.label(metrics: Set<HudMetric>): String {
    val name = when (this) {
        HudMetric.HOST_SUBMISSIONS -> stringResource(R.string.menu_metric_submissions)
        HudMetric.BATTERY_TEMPERATURE -> stringResource(R.string.menu_metric_battery)
        HudMetric.SOC_TEMPERATURE -> stringResource(R.string.menu_metric_soc)
        HudMetric.POWER -> stringResource(R.string.menu_metric_power)
        else -> label
    }
    return stringResource(R.string.menu_hud_metric, name, onOff(this in metrics))
}

@Composable
private fun fpsText(fps: Int?): String = when (fps) {
    null -> stringResource(R.string.menu_unknown)
    0 -> stringResource(R.string.menu_unlimited)
    else -> stringResource(R.string.menu_fps, fps)
}

private val InGameAction.isPersistence: Boolean
    get() = this == InGameAction.SAVE_GAME_FPS || this == InGameAction.INHERIT_GAME_FPS ||
        this == InGameAction.SAVE_GLOBAL_FPS || this == InGameAction.STRETCH

private val InGameAction.isFrameGeneration: Boolean
    get() = this == InGameAction.WINFG || this == InGameAction.WINFG_PRESET ||
        this == InGameAction.LSFG || this == InGameAction.LSFG_MULTIPLIER
