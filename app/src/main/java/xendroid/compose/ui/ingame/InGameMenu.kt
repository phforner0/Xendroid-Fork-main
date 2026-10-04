package xendroid.compose.ui.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import coil.compose.AsyncImage
import xendroid.compose.ui.design.ButtonGlyph
import xendroid.compose.ui.design.CHints
import xendroid.compose.ui.design.LocalSwapConfirm
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdHint
import xendroid.compose.ui.design.XdIconButton
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.part
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
import androidx.compose.material3.Text
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
        Row(
            Modifier.align(Alignment.TopStart).padding(top = 10.dp)
                .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onOpen)
                .semantics { contentDescription = description; role = Role.Button }
                .padding(horizontal = 6.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Icon(XdIcons.menu, null, Modifier.size(16.dp), tint = Color.White) }
    }
}

/** One figure of the menu's status line: "30 FPS", "p99 34 ms", "41 °C"… ([value] in bold). */
data class MenuStat(val value: String, val unit: String? = null, val label: String? = null)

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
    /** FG figures: what it would cost here, or base → submitted while it runs. */
    frameGenerationNotes: List<String> = emptyList(),
    /** 15n: the HUD shows the performance panel (its third level). */
    hudPanel: Boolean = false,
    logSessions: List<SessionLogs.Session>,
    onLogChoice: (Int) -> Unit,
    onPage: (InGamePage) -> Unit,
    onSelect: (Int) -> Unit,
    onAction: (InGameAction) -> Unit,
    onQuitChoice: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Batch 2: the game's name and cover in the menu's head. */
    gameName: String? = null,
    art: Any? = null,
    /** Batch 2: the status line (FPS, p99, temperature, battery, driver) without the HUD. */
    status: List<MenuStat> = emptyList(),
) {
    val c = Xd.colors
    val controller = Xd.controller
    BoxWithConstraints(
        modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        val portrait = maxHeight > maxWidth
        val short = maxHeight < 400.dp
        // Hardware navigation changes the selected option, not Compose focus. Scroll the
        // corresponding row into view rather than leaving the highlight off-screen.
        val scrollState = remember(state.page, state.confirmingQuit, state.logPicker) { ScrollState(0) }
        val selectedRow = remember(state.page, state.confirmingQuit, state.logPicker) { BringIntoViewRequester() }
        LaunchedEffect(state.page, state.selected, state.confirmingQuit, state.logPicker) {
            // After this frame's layout: when the menu opens, the selected row is not placed yet.
            androidx.compose.runtime.withFrameNanos { }
            selectedRow.bringIntoView()
        }
        val shape = if (portrait) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)
        val panel = if (portrait) Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.8f)
        else Modifier.align(Alignment.CenterStart).width(minOf(maxWidth * 0.66f, if (controller) 500.dp else 430.dp)).fillMaxHeight()
        Column(
            panel.clip(shape).background(c.solid(c.sheet))
                .windowInsetsPadding(WindowInsets.safeDrawing.part(top = !portrait, bottom = true, start = true))
                .padding(horizontal = if (short) 12.dp else 16.dp, vertical = if (short) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (short) 8.dp else 10.dp),
        ) {
            // Head: the game, its state, and the way back to it.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (art != null) AsyncImage(art, null, Modifier.size(40.dp, 52.dp).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(gameName ?: "XenDroid${fpsConfig.titleId?.let { " · $it" }.orEmpty()}", style = XdText.h2, color = c.fg,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(if (paused) R.string.xd_menu_paused else R.string.xd_menu_running), style = XdText.small, color = c.fg3)
                }
                if (!controller) XdIconButton(XdIcons.x, stringResource(R.string.menu_continue), { onAction(InGameAction.RESUME) })
            }
            if (status.isNotEmpty() && !state.confirmingQuit && !state.logPicker) StatusLine(status)
            if (!state.confirmingQuit && !state.logPicker) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (controller) ButtonGlyph("LB")
                    InGamePage.entries.forEach { page -> MenuTab(page.label(), page == state.page) { onPage(page) } }
                    if (controller) ButtonGlyph("RB")
                }
            }
            // Head, tabs and foot stay visible even on short screens. Only options scroll;
            // hardware selection is kept visible by the requester above.
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (state.confirmingQuit) {
                    Text(stringResource(R.string.menu_exit_question), style = XdText.h2, color = c.fg, modifier = Modifier.padding(top = 8.dp))
                    Text(stringResource(R.string.menu_exit_warning), style = XdText.bodySm, color = c.fg2)
                    GuestPanelOption(
                        label = stringResource(R.string.menu_cancel), selected = state.selected == 0,
                        onClick = { onQuitChoice(false) },
                        modifier = Modifier.padding(top = 6.dp)
                            .then(if (state.selected == 0) Modifier.bringIntoViewRequester(selectedRow) else Modifier),
                    )
                    GuestPanelOption(
                        label = stringResource(R.string.menu_exit_game), selected = state.selected == 1,
                        onClick = { onQuitChoice(true) }, danger = true,
                        modifier = if (state.selected == 1) Modifier.bringIntoViewRequester(selectedRow) else Modifier,
                    )
                } else if (state.logPicker) {
                    Text(stringResource(R.string.menu_logs_title), style = XdText.h2, color = c.fg)
                    Text(stringResource(R.string.xd_menu_logs_note), style = XdText.note, color = c.fg3)
                    val labels = listOf(stringResource(R.string.menu_logs_all)) +
                        logSessions.map { stringResource(R.string.menu_logs_session, it.label, (it.bytes / 1024).toInt()) } +
                        stringResource(R.string.menu_back)
                    labels.forEachIndexed { index, label ->
                        GuestPanelOption(label, selected = state.selected == index,
                            modifier = if (state.selected == index) Modifier.bringIntoViewRequester(selectedRow) else Modifier,
                            onClick = { onLogChoice(index) })
                    }
                } else {
                    if (state.page == InGamePage.GRAPHICS && state.developer) {
                        MenuNote(presentation.label)
                        frameGenerationBudget?.let { MenuNote(it) }
                        frameGenerationNotes.forEach { MenuNote(it) }
                        MenuNote(stringResource(R.string.menu_fg_experimental))
                    }
                    if (state.page == InGamePage.SYSTEM) {
                        Text(stringResource(R.string.menu_frame_limit_title), style = XdText.label, color = c.fg)
                        MenuNote(stringResource(R.string.menu_live_limit, fpsText(fpsLimit)) + "\n" +
                            when {
                                fpsConfig.saving -> stringResource(R.string.menu_saving_config)
                                fpsConfig.loading -> stringResource(R.string.menu_reading_config)
                                fpsConfig.error != null -> fpsConfig.error
                                else -> stringResource(R.string.menu_next_launch, fpsText(fpsConfig.globalLimit),
                                    if (fpsConfig.titleId == null) stringResource(R.string.menu_game_id_unavailable)
                                    else stringResource(R.string.menu_game_limit,
                                        fpsConfig.gameLimit?.let { fpsText(it) } ?: stringResource(R.string.menu_inherits_global)))
                            })
                    }
                    state.actions().forEachIndexed { index, action ->
                        val more = action == InGameAction.MORE_OPTIONS
                        GuestPanelOption(
                            label = if (more) {
                                if (state.page in state.advanced) stringResource(R.string.menu_fewer_options)
                                else stringResource(R.string.menu_more_options, state.advancedCount())
                            } else {
                                extensionLabels[action] ?: action.label(fpsLimit, performanceHud, compactHud, hudPanel, touchControls, adaptiveSticks, stretch, hudMetrics, presentation, fgPreset, volume)
                            },
                            selected = index == state.selected,
                            enabled = (action != InGameAction.ADAPTIVE_STICKS || fpsConfig.titleId != null) &&
                                (!action.isFrameGeneration || BuildConfig.DEBUG) &&
                                (action != InGameAction.LSFG || lsfgAvailable) &&
                                (!action.isPersistence ||
                                    (!fpsConfig.loading && !fpsConfig.saving && fpsConfig.error == null &&
                                        (action == InGameAction.SAVE_GLOBAL_FPS || action == InGameAction.STRETCH ||
                                            fpsConfig.titleId != null))),
                            splitValue = true,
                            subtle = more,
                            danger = action == InGameAction.QUIT,
                            modifier = if (index == state.selected) Modifier.bringIntoViewRequester(selectedRow) else Modifier,
                            onClick = {
                                onSelect(index)
                                onAction(action)
                            },
                        )
                    }
                    if (state.page == InGamePage.GRAPHICS) {
                        MenuNote(stringResource(R.string.menu_image_live_note), Modifier.padding(top = 6.dp))
                        MenuNote(stringResource(R.string.menu_graphics_note))
                        if (!BuildConfig.DEBUG && state.developer) MenuNote(stringResource(R.string.menu_fg_gated))
                    }
                    if (state.page == InGamePage.SYSTEM) MenuNote(stringResource(R.string.menu_system_note), Modifier.padding(top = 6.dp))
                    if (state.page == InGamePage.CONTROLS) {
                        phoneControllers?.let {
                            Text(it, style = XdText.bodySm, color = c.fg, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(c.s1).padding(12.dp))
                        }
                        MenuNote(stringResource(R.string.menu_adaptive_note))
                        MenuNote(stringResource(R.string.menu_touch_camera_note))
                        MenuNote(stringResource(R.string.menu_phones_note))
                    }
                    if (state.page == InGamePage.SESSION) MenuNote(sessionInfo, Modifier.padding(top = 6.dp))
                }
            }
            if (controller) {
                val swap = LocalSwapConfirm.current
                CHints(listOf(XdHint(if (swap) "B" else "A", stringResource(R.string.xd_hint_select)),
                    XdHint(if (swap) "A" else "B", stringResource(R.string.xd_close)) { onAction(InGameAction.RESUME) },
                    XdHint("LB/RB", stringResource(R.string.xd_hint_tabs))), Modifier.clip(RoundedCornerShape(12.dp)))
            } else if (!state.confirmingQuit && !state.logPicker) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XdButton(stringResource(R.string.menu_continue), { onAction(InGameAction.RESUME) }, kind = XdButtonKind.PRIMARY,
                        icon = XdIcons.play, modifier = Modifier.weight(1f))
                    XdButton(stringResource(R.string.menu_exit_game), { onAction(InGameAction.QUIT) }, kind = XdButtonKind.GHOST, icon = XdIcons.exit)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(status: List<MenuStat>) {
    val c = Xd.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f))
        .horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        for (s in status) {
            Text(buildAnnotatedString {
                s.label?.let { append(it); append(' ') }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.fg)) { append(s.value) }
                s.unit?.let { append(' '); append(it) }
            }, style = XdText.small, color = c.fg2, maxLines = 1)
        }
    }
}

@Composable
private fun MenuTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Xd.colors
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.height(34.dp).clip(shape).background(if (selected) c.acc.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }.padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = XdText.labelSm, color = if (selected) c.acc else c.fg2, maxLines = 1) }
}

@Composable
private fun MenuNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = XdText.note, color = Xd.colors.fg3, modifier = modifier)
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
private fun InGameAction.label(fps: Int, hud: Boolean, compact: Boolean, panel: Boolean, touch: Boolean, adaptive: Boolean, stretch: Boolean,
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
        InGameAction.LSFG_TARGET -> stringResource(R.string.menu_lsfg_target)
        InGameAction.IMPORT_LSFG_DLL -> stringResource(R.string.menu_import_lsfg)
        InGameAction.CLEAR_LSFG_CACHE -> stringResource(R.string.menu_clear_lsfg)
        InGameAction.PERFORMANCE_HUD -> stringResource(R.string.menu_performance_hud, onOff(hud))
        InGameAction.HUD_STYLE -> stringResource(R.string.menu_hud_detail, stringResource(when {
            panel -> R.string.menu_hud_panel
            compact -> R.string.menu_hud_compact
            else -> R.string.menu_hud_full
        }))
        InGameAction.HUD_HOST_SUBMISSIONS -> HudMetric.HOST_SUBMISSIONS.label(metrics)
        InGameAction.HUD_CPU -> HudMetric.CPU.label(metrics)
        InGameAction.HUD_GPU -> HudMetric.GPU.label(metrics)
        InGameAction.HUD_RAM -> HudMetric.RAM.label(metrics)
        InGameAction.HUD_BATTERY -> HudMetric.BATTERY_TEMPERATURE.label(metrics)
        InGameAction.HUD_SOC -> HudMetric.SOC_TEMPERATURE.label(metrics)
        InGameAction.HUD_POWER -> HudMetric.POWER.label(metrics)
        InGameAction.HUD_GPU_MEMORY -> HudMetric.GPU_MEMORY.label(metrics)
        InGameAction.HUD_LOOK -> stringResource(R.string.menu_hud_look_title)
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
        InGameAction.TV_MARGIN -> stringResource(R.string.menu_tv_margin_title)
        InGameAction.SCALING_EFFECT -> stringResource(R.string.menu_scaling)
        InGameAction.ANTIALIASING -> stringResource(R.string.menu_aa_value, stringResource(R.string.menu_from_settings))
        InGameAction.SHARPNESS -> stringResource(R.string.menu_sharpness_value, stringResource(R.string.menu_from_settings))
        InGameAction.DITHER -> stringResource(R.string.menu_dither_value, stringResource(R.string.menu_from_settings))
        InGameAction.SAVE_GAME_IMAGE -> stringResource(R.string.menu_save_game_image)
        InGameAction.PERFORMANCE_HINTS -> stringResource(R.string.menu_hints)
        InGameAction.COLOR_FILTER -> stringResource(R.string.menu_color_filter)
        InGameAction.GYRO_CALIBRATE -> stringResource(R.string.menu_gyro_calibrate)
        InGameAction.GYRO_SENSITIVITY -> stringResource(R.string.menu_gyro_sensitivity)
        InGameAction.GYRO_AIM -> stringResource(R.string.menu_gyro_aim_title)
        InGameAction.UNBUFFERED_INPUT -> stringResource(R.string.menu_unbuffered_title)
        InGameAction.SPLIT_SCREEN -> stringResource(R.string.menu_split_title)
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
        HudMetric.GPU_MEMORY -> stringResource(R.string.menu_metric_gpu_memory)
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
        this == InGameAction.SAVE_GLOBAL_FPS || this == InGameAction.STRETCH || this == InGameAction.SAVE_GAME_IMAGE

private val InGameAction.isFrameGeneration: Boolean
    get() = this == InGameAction.WINFG || this == InGameAction.WINFG_PRESET ||
        this == InGameAction.LSFG || this == InGameAction.LSFG_MULTIPLIER || this == InGameAction.LSFG_TARGET
