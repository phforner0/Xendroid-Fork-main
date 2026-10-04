package xendroid.compose.ui.ingame

/** U01: Graphics = presentation, frame generation and driver; System = frame rate, power and HUD;
 *  Controls = layout, players and gyro; Session = pause, sound, logs and exit. */
enum class InGamePage { GRAPHICS, SYSTEM, CONTROLS, SESSION }

enum class InGameAction {
    FPS_UNLIMITED, FPS_30, FPS_45, FPS_60, FPS_90, FPS_120,
    SAVE_GAME_FPS, INHERIT_GAME_FPS, SAVE_GLOBAL_FPS,
    STRETCH, PERFORMANCE_HUD, HUD_STYLE, TOUCH_CONTROLS, ADAPTIVE_STICKS, RESUME, SHARE_LOGS, QUIT,
    HUD_HOST_SUBMISSIONS, HUD_CPU, HUD_GPU, HUD_RAM, HUD_BATTERY, HUD_SOC, HUD_POWER,
    EDIT_TOUCH_LAYOUT,
    DISPLAY_FIT, DISPLAY_FILL, DISPLAY_STRETCH, DISPLAY_INTEGER, WINFG, WINFG_PRESET,
    LSFG, IMPORT_LSFG_DLL, CLEAR_LSFG_CACHE,
    LSFG_MULTIPLIER,
    VOLUME_UP, VOLUME_DOWN, MUTE,
    REFRESH_RATE, SUSTAINED_PERFORMANCE, BACKGROUND_POLICY, GYRO_CAMERA,
    EXTERNAL_DISPLAY, SCALING_EFFECT,
    PERFORMANCE_HINTS,
    COLOR_FILTER,
    GYRO_CALIBRATE, GYRO_SENSITIVITY,
    CONTROLLER_RUMBLE,
    PHONE_CONTROLLERS,
    /** U01: shows or hides the tab's advanced options (it stays where it is in the list). */
    MORE_OPTIONS,
    /** U07: the free right side of the screen as a touchpad for the right stick. */
    TOUCH_CAMERA,
    /** C07: a numbered scene marker in the run's timeline, to line A/B runs up. */
    MARK_SCENE,
    /** U01: the driver the game runs on, against the one selected (read-only). */
    DRIVER_INFO,
    /** The gyroscope aims always, or only while LT or LB is held. */
    GYRO_AIM,
    /** Controller input as it arrives instead of batched to the next frame (an A/B switch). */
    UNBUFFERED_INPUT,
    /** 15c: the game above and the touch controls clear of it: off, at a fold, always. */
    SPLIT_SCREEN,
    /** 15d: LSFG by an output target (60/90/120/the display) instead of a multiplier by hand. */
    LSFG_TARGET,
    /** 15g: GPU memory (KGSL's total) on the HUD. */
    HUD_GPU_MEMORY,
    /** 15g: the HUD's look: a box, an outline around the letters, or plain text. */
    HUD_LOOK,
    /** 15h: a margin around the picture on a TV that cuts the edges (overscan). */
    TV_MARGIN,
    /** Image options tried live: antialiasing (FXAA), sharpness (CAS and FSR), dither. */
    ANTIALIASING, SHARPNESS, DITHER,
    /** Keeps the scaling effect and the Image options tried here for the game. */
    SAVE_GAME_IMAGE,
}

/** Every option once, on the tab it belongs to (U01), most used first. */
val inGamePageActions: Map<InGamePage, List<InGameAction>> = mapOf(
    InGamePage.GRAPHICS to listOf(
        InGameAction.DISPLAY_FIT, InGameAction.DISPLAY_FILL, InGameAction.DISPLAY_STRETCH, InGameAction.DISPLAY_INTEGER,
        InGameAction.SCALING_EFFECT, InGameAction.ANTIALIASING, InGameAction.SHARPNESS, InGameAction.SAVE_GAME_IMAGE,
        InGameAction.EXTERNAL_DISPLAY, InGameAction.TV_MARGIN,
        InGameAction.WINFG, InGameAction.WINFG_PRESET, InGameAction.LSFG, InGameAction.DRIVER_INFO,
        InGameAction.STRETCH, InGameAction.COLOR_FILTER, InGameAction.DITHER,
        InGameAction.LSFG_MULTIPLIER, InGameAction.LSFG_TARGET, InGameAction.IMPORT_LSFG_DLL, InGameAction.CLEAR_LSFG_CACHE,
    ),
    InGamePage.SYSTEM to listOf(
        InGameAction.FPS_UNLIMITED, InGameAction.FPS_30, InGameAction.FPS_45,
        InGameAction.FPS_60, InGameAction.FPS_90, InGameAction.FPS_120,
        InGameAction.SAVE_GAME_FPS, InGameAction.INHERIT_GAME_FPS,
        InGameAction.PERFORMANCE_HUD, InGameAction.HUD_STYLE, InGameAction.HUD_LOOK, InGameAction.REFRESH_RATE,
        InGameAction.SAVE_GLOBAL_FPS, InGameAction.SUSTAINED_PERFORMANCE, InGameAction.PERFORMANCE_HINTS,
        InGameAction.HUD_HOST_SUBMISSIONS, InGameAction.HUD_CPU, InGameAction.HUD_GPU,
        InGameAction.HUD_RAM, InGameAction.HUD_BATTERY, InGameAction.HUD_SOC, InGameAction.HUD_POWER,
        InGameAction.HUD_GPU_MEMORY,
    ),
    InGamePage.CONTROLS to listOf(InGameAction.TOUCH_CONTROLS, InGameAction.ADAPTIVE_STICKS, InGameAction.TOUCH_CAMERA,
        InGameAction.EDIT_TOUCH_LAYOUT, InGameAction.SPLIT_SCREEN,
        InGameAction.PHONE_CONTROLLERS, InGameAction.CONTROLLER_RUMBLE,
        InGameAction.GYRO_CAMERA, InGameAction.GYRO_AIM, InGameAction.GYRO_SENSITIVITY, InGameAction.GYRO_CALIBRATE,
        InGameAction.UNBUFFERED_INPUT),
    InGamePage.SESSION to listOf(InGameAction.RESUME, InGameAction.SHARE_LOGS, InGameAction.QUIT,
        InGameAction.MUTE, InGameAction.VOLUME_DOWN, InGameAction.VOLUME_UP, InGameAction.MARK_SCENE,
        InGameAction.BACKGROUND_POLICY),
)

/** U01: behind "More options" on their tab: persistence, imports, fine HUD and power tuning. */
val advancedActions: Set<InGameAction> = setOf(
    InGameAction.STRETCH, InGameAction.COLOR_FILTER, InGameAction.DITHER,
    InGameAction.LSFG_MULTIPLIER, InGameAction.LSFG_TARGET, InGameAction.IMPORT_LSFG_DLL, InGameAction.CLEAR_LSFG_CACHE,
    InGameAction.SAVE_GLOBAL_FPS, InGameAction.SUSTAINED_PERFORMANCE, InGameAction.PERFORMANCE_HINTS,
    InGameAction.HUD_HOST_SUBMISSIONS, InGameAction.HUD_CPU, InGameAction.HUD_GPU,
    InGameAction.HUD_RAM, InGameAction.HUD_BATTERY, InGameAction.HUD_SOC, InGameAction.HUD_POWER,
    InGameAction.HUD_GPU_MEMORY,
    InGameAction.GYRO_CALIBRATE, InGameAction.BACKGROUND_POLICY, InGameAction.UNBUFFERED_INPUT,
)

/** L02: hidden in Player mode (engine internals and experiments); build gates still apply. */
val developerActions: Set<InGameAction> = setOf(
    InGameAction.WINFG, InGameAction.WINFG_PRESET, InGameAction.LSFG, InGameAction.IMPORT_LSFG_DLL,
    InGameAction.CLEAR_LSFG_CACHE, InGameAction.LSFG_MULTIPLIER, InGameAction.LSFG_TARGET, InGameAction.PERFORMANCE_HINTS,
    InGameAction.HUD_HOST_SUBMISSIONS, InGameAction.BACKGROUND_POLICY, InGameAction.SUSTAINED_PERFORMANCE,
    InGameAction.UNBUFFERED_INPUT,
)

/** Immutable navigation state shared by touch, Back, hardware buttons and hat axes. */
data class InGameMenuState(
    val open: Boolean = false,
    val page: InGamePage = InGamePage.GRAPHICS,
    val selections: List<Int> = List(InGamePage.entries.size) { 0 },
    val confirmingQuit: Boolean = false,
    val confirmationSelection: Int = 0,
    val pausedByMenu: Boolean = false,
    val logPicker: Boolean = false,
    val logCount: Int = 0,
    val logSelection: Int = 0,
    /** Developer interface (L02); Player hides [developerActions]. */
    val developer: Boolean = true,
    /** U01: tabs whose "More options" are open. */
    val advanced: Set<InGamePage> = emptySet(),
) {
    /** The page's actions as shown, what hardware navigation moves through: the common ones,
     *  then [InGameAction.MORE_OPTIONS] when the tab has advanced ones, then those if open. */
    fun actions(page: InGamePage = this.page): List<InGameAction> {
        val shown = inGamePageActions.getValue(page).filter { developer || it !in developerActions }
        val (more, common) = shown.partition { it in advancedActions }
        if (more.isEmpty()) return common
        return common + InGameAction.MORE_OPTIONS + if (page in advanced) more else emptyList()
    }

    /** How many advanced options the tab holds (for the "More options" label). */
    fun advancedCount(page: InGamePage = this.page): Int =
        inGamePageActions.getValue(page).count { it in advancedActions && (developer || it !in developerActions) }

    /** Opens or closes the tab's advanced options; the toggle keeps its place and selection. */
    fun toggleAdvanced(): InGameMenuState {
        val next = copy(advanced = if (page in advanced) advanced - page else advanced + page)
        return next.select(next.actions().indexOf(InGameAction.MORE_OPTIONS).coerceAtLeast(0))
    }

    val selected: Int
        get() = if (confirmingQuit) confirmationSelection else if (logPicker) logSelection else selections[page.ordinal]

    val count: Int
        get() = if (confirmingQuit) 2 else if (logPicker) logCount + 2 else actions().size

    val action: InGameAction?
        get() = if (confirmingQuit || logPicker) null else actions().getOrNull(selected)

    fun show(pause: Boolean): InGameMenuState =
        copy(open = true, confirmingQuit = false, logPicker = false, pausedByMenu = pause)

    fun hide(): InGameMenuState = copy(open = false, confirmingQuit = false, logPicker = false, pausedByMenu = false)

    fun changePage(delta: Int): InGameMenuState {
        if (confirmingQuit || logPicker) return this
        val pages = InGamePage.entries
        return copy(page = pages[(page.ordinal + delta + pages.size) % pages.size])
    }

    fun select(index: Int): InGameMenuState {
        if (confirmingQuit) return copy(confirmationSelection = index.coerceIn(0, 1))
        if (logPicker) return copy(logSelection = index.coerceIn(0, logCount + 1))
        val next = selections.toMutableList()
        next[page.ordinal] = index.coerceIn(0, count - 1)
        return copy(selections = next)
    }

    fun move(delta: Int): InGameMenuState =
        select((selected + delta % count + count) % count)

    fun askToQuit(): InGameMenuState = copy(confirmingQuit = true, confirmationSelection = 0)

    fun cancelQuit(): InGameMenuState = copy(confirmingQuit = false)
    fun showLogs(count: Int): InGameMenuState = copy(logPicker = true, logCount = count, logSelection = 0)
    fun closeLogs(): InGameMenuState = copy(logPicker = false)
}
