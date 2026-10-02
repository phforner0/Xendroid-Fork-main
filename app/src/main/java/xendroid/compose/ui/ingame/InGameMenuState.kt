package xendroid.compose.ui.ingame

enum class InGamePage { GRAPHICS, HUD, CONTROLS, SESSION }

enum class InGameAction {
    FPS_UNLIMITED, FPS_30, FPS_45, FPS_60, FPS_90, FPS_120,
    SAVE_GAME_FPS, INHERIT_GAME_FPS, SAVE_GLOBAL_FPS,
    STRETCH, PERFORMANCE_HUD, HUD_STYLE, TOUCH_CONTROLS, ADAPTIVE_STICKS, RESUME, SHARE_LOGS, QUIT,
    HUD_HOST_SUBMISSIONS, HUD_CPU, HUD_GPU, HUD_RAM, HUD_BATTERY, HUD_SOC,
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
}

val inGamePageActions: Map<InGamePage, List<InGameAction>> = mapOf(
    InGamePage.GRAPHICS to listOf(
        InGameAction.FPS_UNLIMITED, InGameAction.FPS_30, InGameAction.FPS_45,
        InGameAction.FPS_60, InGameAction.FPS_90, InGameAction.FPS_120,
        InGameAction.SAVE_GAME_FPS, InGameAction.INHERIT_GAME_FPS, InGameAction.SAVE_GLOBAL_FPS,
        InGameAction.STRETCH,
        InGameAction.DISPLAY_FIT, InGameAction.DISPLAY_FILL, InGameAction.DISPLAY_STRETCH, InGameAction.DISPLAY_INTEGER,
        InGameAction.WINFG, InGameAction.WINFG_PRESET,
        InGameAction.LSFG, InGameAction.IMPORT_LSFG_DLL, InGameAction.CLEAR_LSFG_CACHE,
        InGameAction.LSFG_MULTIPLIER,
        InGameAction.REFRESH_RATE, InGameAction.SUSTAINED_PERFORMANCE,
        InGameAction.EXTERNAL_DISPLAY, InGameAction.SCALING_EFFECT,
        InGameAction.PERFORMANCE_HINTS,
        InGameAction.COLOR_FILTER,
    ),
    InGamePage.HUD to listOf(InGameAction.PERFORMANCE_HUD, InGameAction.HUD_STYLE,
        InGameAction.HUD_HOST_SUBMISSIONS, InGameAction.HUD_CPU, InGameAction.HUD_GPU,
        InGameAction.HUD_RAM, InGameAction.HUD_BATTERY, InGameAction.HUD_SOC),
    InGamePage.CONTROLS to listOf(InGameAction.TOUCH_CONTROLS, InGameAction.ADAPTIVE_STICKS, InGameAction.EDIT_TOUCH_LAYOUT,
        InGameAction.GYRO_CAMERA, InGameAction.GYRO_CALIBRATE, InGameAction.GYRO_SENSITIVITY,
        InGameAction.CONTROLLER_RUMBLE, InGameAction.PHONE_CONTROLLERS),
    InGamePage.SESSION to listOf(InGameAction.RESUME, InGameAction.SHARE_LOGS, InGameAction.QUIT,
        InGameAction.MUTE, InGameAction.VOLUME_DOWN, InGameAction.VOLUME_UP, InGameAction.BACKGROUND_POLICY),
)

/** L02: hidden in Player mode (engine internals and experiments); build gates still apply. */
val developerActions: Set<InGameAction> = setOf(
    InGameAction.WINFG, InGameAction.WINFG_PRESET, InGameAction.LSFG, InGameAction.IMPORT_LSFG_DLL,
    InGameAction.CLEAR_LSFG_CACHE, InGameAction.LSFG_MULTIPLIER, InGameAction.PERFORMANCE_HINTS,
    InGameAction.HUD_HOST_SUBMISSIONS, InGameAction.BACKGROUND_POLICY, InGameAction.SUSTAINED_PERFORMANCE,
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
) {
    /** The page's actions as shown: what hardware navigation moves through. */
    fun actions(page: InGamePage = this.page): List<InGameAction> =
        inGamePageActions.getValue(page).let { all -> if (developer) all else all.filterNot { it in developerActions } }

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
