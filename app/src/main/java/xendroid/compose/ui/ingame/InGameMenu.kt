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
import androidx.compose.ui.unit.dp
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
        Text(
            text = "☰",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.48f))
                .clickable(onClick = onOpen)
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
                    if (paused) "Paused · Back to close" else "Game running · Back to close",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!state.confirmingQuit && !state.logPicker) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        InGamePage.entries.forEach { page ->
                            TextButton(onClick = { onPage(page) }) {
                                Text(
                                    page.label,
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
                        Text("Exit the game?", modifier = Modifier.padding(top = 22.dp))
                        Text("Unsaved progress may be lost.", style = MaterialTheme.typography.bodySmall)
                        GuestPanelOption(
                            label = "Cancel", selected = state.selected == 0,
                            onClick = { onQuitChoice(false) },
                            modifier = Modifier.padding(top = 12.dp)
                                .then(if (state.selected == 0) Modifier.bringIntoViewRequester(selectedRow) else Modifier),
                        )
                        GuestPanelOption(
                            label = "Exit game", selected = state.selected == 1,
                            onClick = { onQuitChoice(true) },
                            modifier = Modifier.padding(top = 8.dp)
                                .then(if (state.selected == 1) Modifier.bringIntoViewRequester(selectedRow) else Modifier),
                        )
                    } else if (state.logPicker) {
                        Text("Share diagnostics · choose a session", style = MaterialTheme.typography.titleSmall)
                        val labels = listOf("All retained sessions") + logSessions.map { "${it.label} · ${it.bytes / 1024} KB" } + "Back"
                        labels.forEachIndexed { index, label ->
                            GuestPanelOption(label, selected = state.selected == index,
                                modifier = Modifier.padding(top = 8.dp).then(
                                    if (state.selected == index) Modifier.bringIntoViewRequester(selectedRow) else Modifier),
                                onClick = { onLogChoice(index) })
                        }
                    } else {
                        if (state.page == InGamePage.GRAPHICS) {
                            Text(presentation.label, style = MaterialTheme.typography.bodySmall)
                            Text("Experimental host interpolation; hardware cadence/latency remain unvalidated.", style = MaterialTheme.typography.bodySmall)
                            Text("Frame limit · this session", style = MaterialTheme.typography.titleSmall)
                            Text("Live limit: ${fpsText(fpsLimit)}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                when {
                                    fpsConfig.saving -> "Saving configuration…"
                                    fpsConfig.loading -> "Reading saved configuration…"
                                    fpsConfig.error != null -> fpsConfig.error
                                    else -> "Next launch: global ${fpsText(fpsConfig.globalLimit)} · " +
                                        if (fpsConfig.titleId == null) "game Title ID unavailable"
                                        else "game ${fpsConfig.gameLimit?.let(::fpsText) ?: "inherits global"}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        inGamePageActions.getValue(state.page).forEachIndexed { index, action ->
                            GuestPanelOption(
                                label = extensionLabels[action] ?: action.label(fpsLimit, performanceHud, compactHud, touchControls, adaptiveSticks, stretch, hudMetrics, presentation, fgPreset, volume),
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
                                "Stretch next launch is persistent; display modes below apply live to this session. Submitted frames are not measured scanout.",
                                modifier = Modifier.padding(top = 14.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (!BuildConfig.DEBUG) Text("Frame generation remains gated to developer/debug builds pending hardware validation.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.page == InGamePage.HUD) {
                            Text("Vulkan submitted counts sends to the compositor, not measured display scanout.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.page == InGamePage.CONTROLS) {
                            Text("Adaptive sticks are opt-in per game: touch near a saved stick position to place it under your thumb. Fixed buttons keep priority.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.page == InGamePage.SESSION) {
                            Text(sessionInfo, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
                if (!state.confirmingQuit && !state.logPicker) {
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onAction(InGameAction.RESUME) }, modifier = Modifier.weight(1f)) {
                            Text("Continue")
                        }
                        TextButton(onClick = { onAction(InGameAction.QUIT) }, modifier = Modifier.weight(1f)) {
                            Text("Exit game")
                        }
                    }
                }
            }
        }
    }
}

private val InGamePage.label: String
    get() = when (this) {
        InGamePage.GRAPHICS -> "Graphics"
        InGamePage.HUD -> "HUD"
        InGamePage.CONTROLS -> "Controls"
        InGamePage.SESSION -> "Session"
    }

private fun InGameAction.label(fps: Int, hud: Boolean, compact: Boolean, touch: Boolean, adaptive: Boolean, stretch: Boolean,
                             metrics: Set<HudMetric>, presentation: PresentationState, preset: Int, volume: Int): String =
    when (this) {
        InGameAction.FPS_UNLIMITED -> "Unlimited${if (fps == 0) " ✓" else ""}"
        InGameAction.FPS_30 -> "30 FPS${if (fps == 30) " ✓" else ""}"
        InGameAction.FPS_45 -> "45 FPS${if (fps == 45) " ✓" else ""}"
        InGameAction.FPS_60 -> "60 FPS${if (fps == 60) " ✓" else ""}"
        InGameAction.FPS_90 -> "90 FPS${if (fps == 90) " ✓" else ""}"
        InGameAction.FPS_120 -> "120 FPS${if (fps == 120) " ✓" else ""}"
        InGameAction.SAVE_GAME_FPS -> "Save current limit for this game"
        InGameAction.INHERIT_GAME_FPS -> "Use global limit for this game"
        InGameAction.SAVE_GLOBAL_FPS -> "Save current limit globally"
        InGameAction.STRETCH -> "Stretch next launch: ${if (stretch) "On" else "Off"}"
        InGameAction.DISPLAY_FIT -> "Display · Fit${if (presentation.displayMode == 0) " ✓" else ""}"
        InGameAction.DISPLAY_FILL -> "Display · Fill/crop${if (presentation.displayMode == 1) " ✓" else ""}"
        InGameAction.DISPLAY_STRETCH -> "Display · Stretch${if (presentation.displayMode == 2) " ✓" else ""}"
        InGameAction.DISPLAY_INTEGER -> "Display · Integer${if (presentation.displayMode == 3) " ✓" else ""}"
        InGameAction.WINFG -> "Win-FG 2× · ${if (presentation.requested) "On" else "Off"}"
        InGameAction.WINFG_PRESET -> "Win-FG preset · ${listOf("Quality", "Balanced", "Performance")[preset.coerceIn(0, 2)]}"
        InGameAction.LSFG -> "LSFG Native · ${if (presentation.requested && presentation.engine == 1) "On" else "Off"}"
        InGameAction.LSFG_MULTIPLIER -> "LSFG multiplier · experimental"
        InGameAction.IMPORT_LSFG_DLL -> "Import my Lossless.dll"
        InGameAction.CLEAR_LSFG_CACHE -> "Remove imported LSFG shader cache"
        InGameAction.PERFORMANCE_HUD -> "Performance HUD: ${if (hud) "On" else "Off"}"
        InGameAction.HUD_STYLE -> "HUD detail: ${if (compact) "Compact" else "Full"}"
        InGameAction.HUD_HOST_SUBMISSIONS -> HudMetric.HOST_SUBMISSIONS.label(metrics)
        InGameAction.HUD_CPU -> HudMetric.CPU.label(metrics)
        InGameAction.HUD_GPU -> HudMetric.GPU.label(metrics)
        InGameAction.HUD_RAM -> HudMetric.RAM.label(metrics)
        InGameAction.HUD_BATTERY -> HudMetric.BATTERY_TEMPERATURE.label(metrics)
        InGameAction.HUD_SOC -> HudMetric.SOC_TEMPERATURE.label(metrics)
        InGameAction.TOUCH_CONTROLS -> "Touch controls · this session: ${if (touch) "On" else "Off"}"
        InGameAction.ADAPTIVE_STICKS -> "Adaptive sticks · this game: ${if (adaptive) "On" else "Off"}"
        InGameAction.EDIT_TOUCH_LAYOUT -> "Edit layout · opacity · auto-hide · haptics"
        InGameAction.RESUME -> "Continue"
        InGameAction.SHARE_LOGS -> "Share diagnostic logs"
        InGameAction.QUIT -> "Exit game"
        InGameAction.MUTE -> "Audio · ${if (volume == 0) "Muted (activate to restore)" else "$volume% (activate to mute)"}"
        InGameAction.VOLUME_DOWN -> "Audio volume −10%"
        InGameAction.VOLUME_UP -> "Audio volume +10%"
        InGameAction.REFRESH_RATE -> "Display refresh rate"
        InGameAction.SUSTAINED_PERFORMANCE -> "Sustained performance mode"
        InGameAction.BACKGROUND_POLICY -> "Background pause policy"
        InGameAction.GYRO_CAMERA -> "Gyro camera input"
        InGameAction.EXTERNAL_DISPLAY -> "TV / external display"
        InGameAction.SCALING_EFFECT -> "Scaling and sharpening"
        InGameAction.PERFORMANCE_HINTS -> "Presenter ADPF hints"
        InGameAction.COLOR_FILTER -> "Optional SDR color filter"
        InGameAction.GYRO_CALIBRATE -> "Calibrate gyro · keep phone still after closing menu"
        InGameAction.GYRO_SENSITIVITY -> "Gyro camera sensitivity"
        InGameAction.CONTROLLER_RUMBLE -> "Controller rumble"
    }

private fun HudMetric.label(metrics: Set<HudMetric>): String = "$label · full HUD: ${if (this in metrics) "On" else "Off"}"

private fun fpsText(fps: Int?): String = when (fps) {
    null -> "unknown"
    0 -> "Unlimited"
    else -> "$fps FPS"
}

private val InGameAction.isPersistence: Boolean
    get() = this == InGameAction.SAVE_GAME_FPS || this == InGameAction.INHERIT_GAME_FPS ||
        this == InGameAction.SAVE_GLOBAL_FPS || this == InGameAction.STRETCH

private val InGameAction.isFrameGeneration: Boolean
    get() = this == InGameAction.WINFG || this == InGameAction.WINFG_PRESET ||
        this == InGameAction.LSFG || this == InGameAction.LSFG_MULTIPLIER
