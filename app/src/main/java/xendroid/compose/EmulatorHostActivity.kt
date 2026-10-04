package xendroid.compose

import android.content.Intent
import android.content.ClipData
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import xendroid.compose.core.EmuProcessLink
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.core.FrontendLaunch
import xendroid.compose.core.EmulatorSession
import xendroid.compose.core.ScreenBrightnessSampler
import xendroid.compose.core.SessionLogs
import xendroid.compose.core.HudMetric
import xendroid.compose.core.PresentationState
import xendroid.compose.core.LsfgAssets
import xendroid.compose.core.BackgroundPolicy
import xendroid.compose.core.GyroCamera
import xendroid.compose.core.refreshChoices
import xendroid.compose.core.selectRefresh
import xendroid.compose.core.sustainedPerformance
import xendroid.compose.core.ExternalGameDisplay
import xendroid.compose.core.PresenterPerformanceHints
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.first
import xendroid.compose.gamepad.atRect
import android.content.res.Configuration
import android.widget.Toast
import android.os.VibrationEffect
import android.os.Vibrator
import android.preference.PreferenceManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import xendroid.compose.ui.disc.DiscSwapPanel
import xendroid.compose.ui.keyboard.GuestKeyboardPanel
import xendroid.compose.ui.messagebox.GuestMessageBoxPanel
import xendroid.compose.ui.ingame.InGameAction
import xendroid.compose.ui.ingame.InGameMenu
import xendroid.compose.ui.ingame.InGameMenuHandle
import xendroid.compose.ui.ingame.InGameMenuState
import xendroid.compose.ui.ingame.InGamePage
import xendroid.compose.ui.theme.xendroidTheme
import xendroid.compose.gamepad.GamepadConfigDto
import xendroid.compose.gamepad.GamepadController
import xendroid.compose.gamepad.GamepadEditorScreen
import xendroid.compose.gamepad.GamepadOverlay
import xendroid.compose.gamepad.Kc
import xendroid.compose.gamepad.rememberAutoHide
import xendroid.compose.data.GameButtons
import xendroid.compose.data.KeymapStore
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.FpsConfigSnapshot
import xendroid.compose.settings.InGameConfigRepository

/**
 * The :emu emulator host (separate process; see manifest). Reads game_uri from the Intent,
 * performs the PRE-surface native setup, hosts a Vulkan SurfaceView, and drives the exact
 * surface->boot ordering. onDestroy hard-kills the process.
 */
class EmulatorHostActivity : ComponentActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "EmuHost"
        private const val KEYBOARD_POLL_MS = 150L
        const val EXTRA_GAME_URI = "game_uri"
        const val EXTRA_DISC_LABELS = "disc_labels"
        const val EXTRA_DISC_PATHS = "disc_paths"
        /** 15e: shown while the game starts; a path to one of this app's own cover files. */
        const val EXTRA_GAME_NAME = "game_name"
        const val EXTRA_GAME_ART = "game_art"
        /** 15e: "Try again" asks the main process to start the game again in a new :emu. */
        const val ACTION_RELAUNCH_GAME = "xendroid.intent.action.RELAUNCH_GAME"
        /** R4 "Start with…": command-line cvars for this launch only, honoured with this app's
         *  [xendroid.compose.core.LaunchToken] and after [xendroid.compose.core.LaunchOptions.sanitize]. */
        const val EXTRA_LAUNCH_ARGS = "launch_args"
        const val EXTRA_LAUNCH_TOKEN = "launch_token"
        /** The extras a relaunch carries over (the game, its discs, what the loading screen shows,
         *  the options of this launch). */
        val RELAUNCH_EXTRAS = listOf(EXTRA_GAME_URI, EXTRA_DISC_LABELS, EXTRA_DISC_PATHS, EXTRA_GAME_NAME, EXTRA_GAME_ART,
            EXTRA_LAUNCH_ARGS, EXTRA_LAUNCH_TOKEN)

        private const val KC_DPAD_LEFT = 0
        private const val KC_DPAD_UP = 1
        private const val KC_DPAD_RIGHT = 2
        private const val KC_DPAD_DOWN = 3
        private const val KC_A = 4
        private const val KC_B = 5
        private const val KC_X = 6
        private const val KC_Y = 7
        private const val KC_BACK = 8
        private const val KC_START = 9
        private const val KC_SHOULDER_L = 10
        private const val KC_SHOULDER_R = 11
        private const val KC_TRIGGER_L = 14
        private const val KC_TRIGGER_R = 15
        private const val KC_LTHUMB_LEFT = 16
        private const val KC_LTHUMB_UP = 17
        private const val KC_LTHUMB_RIGHT = 18
        private const val KC_LTHUMB_DOWN = 19
        private const val KC_RTHUMB_LEFT = 20
        private const val KC_RTHUMB_UP = 21
        private const val KC_RTHUMB_RIGHT = 22
        private const val KC_RTHUMB_DOWN = 23
        private const val KEY_VALUE_UNUSED = -1
        private const val AXIS_DEADZONE = 0.08f
        private const val FOCUS_PAUSE_DEBOUNCE_MS = 250L
        /** Running seconds without a new guest frame before the flight recorder notes a stall. */
        private const val STALL_EVENT_SECONDS = 3

        private const val DISPLAY_SETTINGS_PREFS = "display_settings"
        private const val FULLSCREEN_STRETCH_KEY =
            "fullscreen_stretch_enabled"
    }

    private val session = EmulatorSession()
    /** Durable record of this guest run (sessions/SessionRunStore); null if unavailable. */
    @Volatile private var runId: String? = null
    private var lastRunHeartbeatMs = 0L
    /** One-second samples of this run (main thread only), saved with the heartbeat and at the end. */
    private val runPerformance = xendroid.compose.sessions.RunPerformanceAccumulator()
    /** Flight recorder of this run (C01): rare host events, flushed beside the run record. */
    private val runEvents = xendroid.compose.sessions.RunEventRecorder(clock = android.os.SystemClock::elapsedRealtime)
    private var lastPresentationLabel: String? = null
    private var lastPresentCount = -1L
    private var stalledSeconds = 0
    private var lastThermalStatus = -1
    /** 15j: warns before the phone throttles for heat (advisory: it never changes a setting). */
    private val thermalWatch = xendroid.compose.core.ThermalWatch()
    private var thermalNoticeAt = Long.MIN_VALUE
    private var thermalTick = 0
    /** 15n: the last thermal headroom read (null where the device has none). */
    private var lastHeadroom: Float? = null
    private var driverRecorded = false
    /** Seconds with >= 100 ms spent creating pipelines (ns counter, pipelines alongside). */
    private val compileBursts = xendroid.compose.sessions.BurstTracker(threshold = 100_000_000, quietSeconds = 2)
    /** Seconds with concealed audio blocks; 5 quiet seconds end a burst, so sporadic dropouts stay one event pair. */
    private val audioBursts = xendroid.compose.sessions.BurstTracker(threshold = 1, quietSeconds = 5)
    /** Shown over the black screen until the first guest frame (U09); null afterwards. */
    private val bootStatus = mutableStateOf<xendroid.compose.ui.ingame.BootStatus?>(
        xendroid.compose.ui.ingame.BootStatus(xendroid.compose.ui.ingame.BootStatus.Stage.EMULATOR))
    /** U09: Cancel was pressed on the label; the start sequence stops at its next step. */
    private var bootCancelled = false
    /** 15e: the loading screen's picture and name (from the library, or the title's cover once known). */
    private val loadingArt = mutableStateOf<java.io.File?>(null)
    private var loadingName: String? = null
    /** Batch 2: who plays and what the game starts with, under the loading screen's stages. */
    private val loadingDetails = mutableStateOf<xendroid.compose.ui.ingame.LoadingDetails?>(null)
    /** Batch 2: the in-game menu's status line, refreshed each second while it is open. */
    private val menuStatus = mutableStateOf<List<xendroid.compose.ui.ingame.MenuStat>>(emptyList())
    /** Batch 2: why the game did not start, shown over everything (replaces the old dialog). */
    private val launchFailureState = mutableStateOf<xendroid.compose.ui.ingame.LaunchFailure?>(null)
    /** What Android's GameManager hears: loading, playing, paused under the menu, or nothing. */
    private val gameModeSignal by lazy { xendroid.compose.core.GameModeSignal(applicationContext) }
    private val createdAtMs = android.os.SystemClock.elapsedRealtime()
    /** Connected controllers by device id, described by vendor/product only. */
    private val controllers = mutableMapOf<Int, String>()
    /** I01: physical controllers -> players. P1 keeps the existing path; P2-P4 go through [slotInput]. */
    private val controllerSlots = xendroid.compose.gamepad.ControllerSlots()
    private val slotInput = xendroid.compose.gamepad.SlotInputRouter(AXIS_DEADZONE) { slot, key, pressed, value ->
        session.keyEventSlot(slot, key, pressed, value)
    }
    /** Device id -> descriptor of each known controller (a removed device can no longer be asked). */
    private val controllerKeys = mutableMapOf<Int, String>()
    /** I04/U08: guest rumble on physical controllers only (never the phone): each controller's own
     *  intensity (set in Test controllers), else the default the in-game menu changes. */
    private val rumbleSettings by lazy {
        mutableStateOf(xendroid.compose.gamepad.RumbleSettings.decode(
            getSharedPreferences(xendroid.compose.gamepad.RumbleSettings.PREFS, MODE_PRIVATE)
                .getString(xendroid.compose.gamepad.RumbleSettings.DEFAULT_KEY, null),
            getSharedPreferences(xendroid.compose.gamepad.RumbleSettings.DEVICES_PREFS, MODE_PRIVATE)
                .getString(xendroid.compose.gamepad.RumbleSettings.DEVICES_KEY, null)))
    }
    private val rumbleAmplitudes = mutableMapOf<Int, Int>()

    /**
     * I06–I09: other phones on the LAN as P2–P4. Off at every boot; the in-game menu turns it
     * on. Its callbacks run on socket threads: the slot table and the JNI input calls are
     * thread-safe (the native driver locks its key table), the flight recorder is not.
     */
    private val phoneControllers = xendroid.compose.companion.CompanionHostControl(
        newHost = { lan ->
            xendroid.compose.companion.CompanionHost(lan.address,
                claimSlot = { key, _ ->
                    // A generic label: a phone's own name can carry its owner's.
                    controllerSlots.connectRemote(key)?.also { session.setSlotConnected(it, true, "Phone P${it + 1}") }
                },
                releaseSlot = { key, slot ->
                    if (controllerSlots.disconnect(key) != null) session.setSlotConnected(slot, false, "Phone P${slot + 1}")
                },
                onInput = { slot, change -> session.keyEventSlot(slot, change.key, change.pressed, change.value) },
                onEvent = { message -> mainHandler.post { recordEvent("companion", message) } })
        },
        onChange = { mainHandler.post { refreshPhoneControllers() } },
    )
    /** Null until the first refresh (resources are not ready while the activity is constructed). */
    private val phoneControllersLabel = mutableStateOf<String?>(null)
    private val phoneControllersDetails = mutableStateOf<String?>(null)
    private var phoneControllersRecorded = "off"

    /** Menu texts; on, off and failures also go to the run's timeline (never the address or code). */
    private fun refreshPhoneControllers() {
        phoneControllersLabel.value = xendroid.compose.ui.ingame.phoneControllersLabel(this, phoneControllers.status)
        phoneControllersDetails.value = xendroid.compose.ui.ingame.phoneControllersDetails(this, phoneControllers.status)
        val status = when (val s = phoneControllers.status) {
            is xendroid.compose.companion.CompanionHostControl.Status.On -> "on (${s.network})"
            is xendroid.compose.companion.CompanionHostControl.Status.Failed -> "not started: ${s.reason}"
            xendroid.compose.companion.CompanionHostControl.Status.Off -> "off"
            else -> return
        }
        if (status != phoneControllersRecorded) {
            phoneControllersRecorded = status
            recordEvent("companion", "phone controllers $status")
        }
    }

    /** Plays each slot's guest rumble on the controller holding it; called every 50 ms while the game runs. */
    private fun driveRumble(state: LongArray) {
        controllerSlots.players.forEachIndexed { slot, key ->
            val deviceId = key?.let { k -> controllerKeys.entries.firstOrNull { it.value == k }?.key } ?: return@forEachIndexed
            val amplitude = xendroid.compose.gamepad.rumbleAmplitude(state.getOrElse(slot) { 0L }, rumbleSettings.value.forDevice(key))
            val vibrator = controllerVibrator(deviceId) ?: return@forEachIndexed
            if (amplitude > 0) {
                // Short overlapping shots: stops by itself if this loop does.
                vibrator.vibrate(VibrationEffect.createOneShot(100, amplitude))
            } else if ((rumbleAmplitudes[deviceId] ?: 0) > 0) {
                vibrator.cancel()
            }
            rumbleAmplitudes[deviceId] = amplitude
        }
    }

    private fun stopRumble() {
        rumbleAmplitudes.keys.toList().forEach { id -> if ((rumbleAmplitudes[id] ?: 0) > 0) controllerVibrator(id)?.cancel() }
        rumbleAmplitudes.clear()
    }

    private fun controllerVibrator(deviceId: Int): Vibrator? {
        val device = InputDevice.getDevice(deviceId) ?: return null
        val vibrator = if (Build.VERSION.SDK_INT >= 31) device.vibratorManager.defaultVibrator
        else @Suppress("DEPRECATION") device.vibrator
        return vibrator.takeIf { it.hasVibrator() }
    }
    private val thermalListener = android.os.PowerManager.OnThermalStatusChangedListener { noteThermal(it) }
    private val inputDeviceListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = noteController(deviceId, "connected")
        override fun onInputDeviceRemoved(deviceId: Int) {
            controllers.remove(deviceId)?.let { recordEvent("controller", "disconnected ($it)", flush = true) }
            if (deviceId == touchHiddenBy) touchControlsBack("the controller disconnected")
            // Release only what the lost controller held (I04); its player slot frees up.
            controllerKeys.remove(deviceId)?.let { key ->
                controllerSlots.disconnect(key)?.let { slot ->
                    if (slot == 0) releaseGuestInput() else {
                        slotInput.release(slot)
                        session.setSlotConnected(slot, false, "Controller ${slot + 1}")
                    }
                }
            }
        }
        override fun onInputDeviceChanged(deviceId: Int) {}
    }
    /** Set when the launch failed visibly, so the run is recorded as FAILED. */
    private var launchFailure: String? = null
    /** The game this single-shot process boots; a later launch intent cannot switch it. */
    private var launchedGame: String? = null
    private var surfaceView: SurfaceView? = null
    private var surfaceAvailable = false
    private var externalDisplay: ExternalGameDisplay? = null
    /** Null until the TV output reports (it starts on the phone). */
    private val externalDisplayLabel = mutableStateOf<String?>(null)
    /** 15h: the TV margin (overscan), percent of the screen per side. */
    private val tvMargin = androidx.compose.runtime.mutableFloatStateOf(0f)
    /** The game is drawn on another display (the TV); the handset keeps only the controls. */
    private val gameOnExternalDisplay = mutableStateOf(false)
    /** 15c: a horizontal fold half open (tabletop), as (top, bottom) in window pixels. */
    private val windowFold = mutableStateOf<Pair<Int, Int>?>(null)
    /** 15c: the split last written to the run's timeline. */
    private var splitRecorded: String? = null
    private val scalingEffect = mutableIntStateOf(-1)
    private val performanceHints by lazy { PresenterPerformanceHints(applicationContext) }
    private val performanceHintsLabel = mutableStateOf("Presenter ADPF · Off")
    private var started = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pauseOnFocusLost =
        Runnable {
            pauseForLifecycle()
        }

    private val gamepad by lazy {
        GamepadController(applicationContext)
    }

    private val brightnessSampler = ScreenBrightnessSampler()

    @Volatile
    private var overlayWantsBrightness = false

    private var hapticsEnabled = false

    private val bootedState = mutableStateOf(false)
    private val foregroundState = mutableStateOf(false)
    private val fpsLimitState = mutableIntStateOf(60)
    private val showFpsOverlay = mutableStateOf(false)
    private val performanceOverlayEnabled = mutableStateOf(false)
    /** 15n: FPS only, the chosen rows, or the performance panel. */
    private val hudDetail = mutableStateOf(xendroid.compose.core.HudDetail.FULL)
    /** 15n: the last seconds of frame times, and what the panel shows (built only while it is shown). */
    private val performancePanel = xendroid.compose.core.PerformancePanel()
    private val panelSnapshot = mutableStateOf<xendroid.compose.core.PerformancePanel.Snapshot?>(null)
    /** The settings this run booted with that differ from the defaults (14c), for the panel. */
    @Volatile private var bootChangedSettings: List<String>? = null
    private val hudMetrics = mutableStateOf(HudMetric.entries.toSet())
    /** 15g: the HUD's look for the running game (or the last one set). */
    private val hudLook = mutableStateOf(xendroid.compose.core.HudLook.BOX)

    // Fullscreen stretch:
    // false = preserve aspect ratio / black bars
    // true  = stretch game image to fill the display
    private val fullscreenStretchEnabled = mutableStateOf(false)

    private val showTouchOverlay = mutableStateOf<Boolean?>(null)
    /** The on-screen controls put aside by P1's physical controller (main thread only). */
    private val touchPresence = xendroid.compose.gamepad.TouchOverlayPresence()
    private val overlayHiddenByController = mutableStateOf(false)
    private var touchHiddenBy = -1
    /** The touch layout's "Hide while a controller plays P1", read outside composition. */
    private var hideTouchWithController = true
    private val menuState = mutableStateOf(InGameMenuState())
    private val menuPaused = mutableStateOf(false)
    private val editorOpen = mutableStateOf(false)
    private val menuLogSessions = mutableStateOf<List<SessionLogs.Session>>(emptyList())
    private val presentationState = mutableStateOf(PresentationState())
    private val fgGovernor = xendroid.compose.core.FrameGenerationGovernor()
    private val fgBudgetLabel = mutableStateOf<String?>(null)
    /** FG before it is on (what it would cost here) or while it runs (base → submitted). */
    private val fgNotes = mutableStateOf<List<String>>(emptyList())
    /** The guest's FPS in the last second it ran with the menu closed (the menu pauses it). */
    private var lastGuestFps = 0.0
    private var lastGenerated = -1L
    private var lastGeneratedNs = 0L
    private var generatedPerSecond = 0.0
    private val fgPreset = mutableIntStateOf(2)
    private val lsfgMultiplier = mutableIntStateOf(2)
    /** 15d: [xendroid.compose.core.FrameGenerationTarget] choice; off = the multiplier above, by hand. */
    private val lsfgTarget = mutableIntStateOf(xendroid.compose.core.FrameGenerationTarget.OFF)
    private val generationCap = xendroid.compose.core.GenerationCap()
    private val audioVolume = mutableIntStateOf(100)
    private val gpuLabel = mutableStateOf("")
    /** U01: the driver setting when the game started, and the Graphics tab's driver line. */
    private val driverAtBoot = mutableStateOf<String?>(null)
    private val driverLine = mutableStateOf(xendroid.compose.driver.DriverIdentity.InGame.UNKNOWN to "")
    private var volumeBeforeMute = 100
    private val backgroundPolicy = mutableStateOf(BackgroundPolicy.AUTO)
    private val gyroEnabled = mutableStateOf(false)
    private val gyroSensitivity = mutableIntStateOf(1)
    private val sustainedMode = mutableStateOf(false)
    private val sustainedAvailable = mutableStateOf(true)
    private val requestedRefresh = mutableStateOf<Float?>(null)
    /** Right-stick directions currently held on the touch overlay. */
    private val touchStickHeld = BooleanArray(24)
    /** When the gyroscope aims: always, or while P1 holds LT or LB (touch_options "gyro_aim"). */
    private val gyroAim = mutableStateOf(xendroid.compose.gamepad.GyroAim.ALWAYS)
    private var gyroAiming = false
    /** Controller sticks (and each touch gesture) delivered as they arrive, not once a frame. */
    private val unbufferedInput = mutableStateOf(true)
    private val gyroCamera by lazy { GyroCamera(applicationContext) { code, down, value ->
        // Hold-to-aim: with the chosen button up the camera stays still; the stick the
        // gyroscope was moving is let go once, never one a finger or a controller holds.
        if (!gyroAim.value.active(session::p1Holding)) {
            if (gyroAiming) {
                gyroAiming = false
                for (key in KC_RTHUMB_LEFT..KC_RTHUMB_DOWN) {
                    if (!axisPressed[key] && !touchStickHeld[key]) session.keyEvent(key, false, 0)
                }
            }
            return@GyroCamera
        }
        gyroAiming = true
        // A physical or touch right stick keeps priority over the optional camera sensor:
        // the sensor re-sends every sample, which would zero a held touch stick.
        if (!(KC_RTHUMB_LEFT..KC_RTHUMB_DOWN).any { axisPressed[it] || touchStickHeld[it] }) {
            session.keyEvent(code, down, value)
        }
    } }
    /** The input options every game starts with; the in-game menu changes them for the next ones too. */
    private val controlOptions by lazy { xendroid.compose.gamepad.ControlOptionsStore(applicationContext) }

    private fun applyControlOptions(options: xendroid.compose.gamepad.ControlOptions) {
        touchCamera.value = options.touchCamera
        gyroAim.value = options.gyroAim
        gyroSensitivity.intValue = options.gyroSensitivity.ordinal
        gyroCamera.sensitivity = options.gyroSensitivity.scale
        val unbuffered = unbufferedInput.value != options.unbufferedInput
        unbufferedInput.value = options.unbufferedInput
        if (unbuffered && window?.decorView?.isAttachedToWindow == true) applyUnbufferedInput()
        if (rumbleSettings.value.default != options.rumble) rumbleSettings.value = rumbleSettings.value.copy(default = options.rumble)
        val gyro = options.gyroCamera && gyroCamera.available
        if (gyro != gyroEnabled.value) {
            gyroEnabled.value = gyro
            if (gyro && foregroundState.value && !menuState.value.open && hasWindowFocus()) gyroCamera.start()
        }
    }

    /** Saves an in-game change for the next games (the shared store, never this process's preferences). */
    private fun saveControlOptions(change: (xendroid.compose.gamepad.ControlOptions) -> xendroid.compose.gamepad.ControlOptions) {
        lifecycleScope.launch {
            runCatching { controlOptions.update(change) }.onFailure { Log.w(TAG, "Saving the control options failed", it) }
        }
    }
    private val lsfgCache = mutableStateOf<String?>(null)
    private var importingLsfg = false
    private val lsfgPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importingLsfg) {
            importingLsfg = true
            lifecycleScope.launch {
                try {
                    val cache = LsfgAssets.import(applicationContext, uri)
                    lsfgCache.value = cache.path
                    Toast.makeText(this@EmulatorHostActivity, "LSFG shaders imported locally", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Toast.makeText(this@EmulatorHostActivity, "LSFG import failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally { importingLsfg = false }
            }
        }
    }
    private val fpsConfig = mutableStateOf(FpsConfigSnapshot())
    private val adaptiveSticks = mutableStateOf(false)
    /** U07: the free right side of the screen drives the right stick by finger speed (opt-in, all games). */
    private val touchCamera = mutableStateOf(false)
    /** C07: scene markers placed in this run (for "Compare runs"). */
    private val sceneMarkers = mutableIntStateOf(0)
    private var controlsTitleId: String? = null
    /** The title the core reports running, for per-game touch layouts (U06). */
    private val activeTitleState = mutableStateOf<String?>(null)
    private val inGameConfig by lazy { InGameConfigRepository(ConfigStore(applicationContext)) }
    private var pausedByLifecycle = false
    private val consumedMenuKeys = mutableSetOf<Long>()
    private val sentGuestKeys = mutableMapOf<Long, Int>()
    private var sharingLogs = false

    private val keyboardRequestState =
        mutableStateOf<Emulator.KeyboardRequest?>(null)

    private val discRequestState =
        mutableStateOf<Emulator.DiscSwapRequest?>(null)

    private val messageBoxRequestState =
        mutableStateOf<Emulator.MessageBoxRequest?>(null)

    private val panelSelectedState = mutableIntStateOf(0)
    /** U10: the guest's text prompt as typed so far (grid highlight, caret, page, shift). */
    private val keyboardGrid = mutableStateOf(xendroid.compose.ui.keyboard.KeyboardGrid())
    private val gridRepeat = xendroid.compose.gamepad.NavRepeat()
    private var gridHatDirection = 4   // (dy + 1) * 3 + (dx + 1); 4 = centred

    @Volatile
    private var keyMap: Map<Int, Int> =
        GameButtons.DEFAULT_LOOKUP

    private var vibrator: Vibrator? = null
    private var lTriggerDown = false
    private var rTriggerDown = false

    /** 15l: the UI scale chosen in the library, for the in-game menu and panels (read per game). */
    private var uiScale = xendroid.compose.ui.theme.UiScale.DEFAULT

    /** 15l: before Android 13 the language chosen in the app is applied here. */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(newBase)
        xendroid.compose.settings.AppLanguageStore.overrideFor(newBase)?.let { applyOverrideConfiguration(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiScale = xendroid.compose.ui.theme.UiScaleStore.read(this)
        // L02: the interface mode chosen in the library; read once per game process.
        menuState.value = menuState.value.copy(
            developer = xendroid.compose.settings.UiModeStore.read(this) == xendroid.compose.settings.UiMode.DEVELOPER)
        // What the game starts with for input (the Controls area's values): older builds' at once,
        // then the shared store's as soon as it is read.
        applyControlOptions(xendroid.compose.gamepad.ControlOptions.legacy(getSharedPreferences("touch_options", MODE_PRIVATE)))
        lifecycleScope.launch {
            runCatching { controlOptions.options.first() }
                .onFailure { Log.w(TAG, "Reading the control options failed", it) }
                .getOrNull()?.let(::applyControlOptions)
        }
        enterImmersiveMode()
        gameModeSignal.update(xendroid.compose.core.GamePhase.LOADING)
        watchFold()
        loadingName = intent?.getStringExtra(EXTRA_GAME_NAME)?.take(120)
        loadingArt.value = xendroid.compose.core.LaunchArt.fromExtra(intent?.getStringExtra(EXTRA_GAME_ART), listOf(filesDir, cacheDir))

        EmuProcessLink.bindToMainProcess(this)

        val gameUri =
            FrontendLaunch.resolveGamePath(
                this,
                intent
            )

        if (gameUri.isNullOrEmpty()) {
            Log.e(
                TAG,
                "No bootable game in launch intent; finishing"
            )

            Toast.makeText(
                this,
                getString(R.string.host_no_game),
                Toast.LENGTH_LONG
            ).show()

            leave()
            return
        }

        if (!EmulatorRuntime.supportsVulkan) {
            Toast.makeText(
                this,
                getString(R.string.host_no_vulkan),
                Toast.LENGTH_LONG
            ).show()

            leave()
            return
        }
        launchedGame = gameUri
        startEventSources()

        lifecycleScope.launch {
            val store = KeymapStore(applicationContext)

            keyMap =
                withContext(Dispatchers.IO) {
                    EmulatorRuntime.ensureLoaded()

                    store.androidToGameKey.firstOrNull()
                        ?: GameButtons.DEFAULT_LOOKUP
                }

            showFpsOverlay.value =
                withContext(Dispatchers.IO) {
                    readShowDebugOverlay()
                }

            performanceOverlayEnabled.value =
                getSharedPreferences(
                    "fps_overlay",
                    MODE_PRIVATE
                ).getBoolean(
                    "performance_overlay_enabled",
                    false
                )

            hudDetail.value = getSharedPreferences("fps_overlay", MODE_PRIVATE).let { prefs ->
                xendroid.compose.core.HudDetail.parse(prefs.getString("performance_overlay_detail", null),
                    prefs.getBoolean("performance_overlay_compact", false))
            }
            val savedMetrics = getSharedPreferences("fps_overlay", MODE_PRIVATE).getStringSet("hud_metrics", null)
            if (savedMetrics != null) hudMetrics.value = HudMetric.entries.filter { it.name in savedMetrics }.toSet()
            hudLook.value = xendroid.compose.core.HudPlacements.read(HudPreferences.of(this@EmulatorHostActivity), null).look

            fullscreenStretchEnabled.value =
                getSharedPreferences(
                    DISPLAY_SETTINGS_PREFS,
                    MODE_PRIVATE
                ).getBoolean(
                    FULLSCREEN_STRETCH_KEY,
                    withContext(Dispatchers.IO) {
                        readFullscreenStretch()
                    }
                )

            if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                !Environment.isExternalStorageManager()
            ) {
                Toast.makeText(
                    this@EmulatorHostActivity,
                    getString(R.string.host_no_files_access),
                    Toast.LENGTH_LONG
                ).show()

                leave()
                return@launch
            }

            withContext(Dispatchers.IO) {
                runCatching {
                    SessionLogs.ensureCaptureRunning()
                }.onFailure {
                    Log.w(
                        TAG,
                        "Session log capture failed",
                        it
                    )
                }
            }

            // Lease + save-restore recovery before the guest can touch the content tree.
            // A failure is shown to the user instead of a silent finish().
            val storage = withContext(Dispatchers.IO) {
                runCatching {
                    session.prepareStorage(onWaiting = {
                        lifecycleScope.launch {
                            Toast.makeText(this@EmulatorHostActivity,
                                getString(R.string.host_waiting_storage), Toast.LENGTH_SHORT).show()
                        }
                    })
                }.getOrElse {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    Log.e(TAG, "Preparing game storage failed", it)
                    EmulatorSession.StorageResult.Unavailable(EmulatorSession.StorageResult.Unavailable.Reason.FAILED,
                        it.message ?: it.javaClass.simpleName, "Game data could not be prepared: ${it.message}")
                }
            }
            if (storage is EmulatorSession.StorageResult.Unavailable) {
                showLaunchFailure(when (storage.reason) {
                    EmulatorSession.StorageResult.Unavailable.Reason.BUSY -> getString(R.string.host_storage_busy)
                    EmulatorSession.StorageResult.Unavailable.Reason.RECOVERY_PENDING -> getString(R.string.host_storage_recovery, storage.detail)
                    EmulatorSession.StorageResult.Unavailable.Reason.FAILED -> getString(R.string.host_storage_failed, storage.detail)
                }, storage.english, when (storage.reason) {
                    EmulatorSession.StorageResult.Unavailable.Reason.BUSY -> xendroid.compose.ui.ingame.LaunchFailure.Kind.BUSY
                    EmulatorSession.StorageResult.Unavailable.Reason.RECOVERY_PENDING -> xendroid.compose.ui.ingame.LaunchFailure.Kind.RECOVERY
                    EmulatorSession.StorageResult.Unavailable.Reason.FAILED -> xendroid.compose.ui.ingame.LaunchFailure.Kind.OTHER
                })
                return@launch
            }
            recordEvent("storage", "ready")
            // U09: Cancel can come during any wait above; a start cancelled this early leaves
            // no run and boots nothing (the activity is already finishing).
            if (bootCancelled) return@launch

            // Runs left open by a :emu that died (crash, kill) are closed first, then this
            // run starts. Frontend-only launches never start the main process to do it.
            runId = withContext(Dispatchers.IO) {
                runCatching {
                    val runs = xendroid.compose.sessions.SessionRuns.store()
                    runs.reconcile(xendroid.compose.sessions.SessionRuns.fates(applicationContext))
                    runs.begin(origin, gameUri, BuildConfig.VERSION_NAME, Process.myPid()).runId
                }.onFailure { Log.w(TAG, "Session run record unavailable", it) }.getOrNull()
            }
            recordEvent("boot", "run started", flush = true)
            recordEvent("game mode", "system: ${gameModeSignal.systemMode()}")
            recordEvent("input", "unbuffered " + if (unbufferedInput.value) "on" else "off")
            if (bootCancelled) {
                // Cancelled while the run record was being written: cancelBoot had no run to end.
                runId?.let { id -> runCatching { xendroid.compose.sessions.SessionRuns.store().ending(id, "cancelled while starting") } }
                return@launch
            }
            // A fatal core error (GPU device lost...) aborts without UI: its message goes
            // beside the run record so the next reconcile can name the cause.
            runId?.let { id ->
                runCatching { session.setFatalReportPath(xendroid.compose.sessions.SessionRuns.store().fatalReportFile(id).path) }
                    .onFailure { Log.w(TAG, "Fatal report path unavailable", it) }
            }

            prepareNativeRealPath(gameUri)

            // Re-apply the saved display setting immediately before the
            // emulator session is allowed to boot.
            withContext(Dispatchers.IO) {
                persistFullscreenStretchConfig(
                    fullscreenStretchEnabled.value
                )
            }

            if (bootCancelled) return@launch
            installSurfaceView()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        recordEvent("focus", if (hasFocus) "gained" else "lost")

        if (hasFocus) {
            mainHandler.removeCallbacks(
                pauseOnFocusLost
            )

            enterImmersiveMode()

            resumeForLifecycle()
        } else {
            mainHandler.removeCallbacks(
                pauseOnFocusLost
            )

            mainHandler.postDelayed(
                pauseOnFocusLost,
                FOCUS_PAUSE_DEBOUNCE_MS
            )
        }
    }

    /** Blocking explanation before leaving: a Toast is easy to miss on a launch from an
     *  external frontend, and the user needs to know the game did not start and why. */
    /** singleTask: a frontend or shortcut launching while a game runs lands here. The
     *  core is single-shot per process, so say why the requested game did not start
     *  instead of silently showing the running one. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val requested = FrontendLaunch.resolveForHandOff(this, intent) ?: return
        if (requested != launchedGame) {
            Toast.makeText(this, getString(R.string.host_already_running),
                Toast.LENGTH_LONG).show()
        }
    }

    /** U09: where this game was started from, read once (the run record and the way out). */
    private val origin by lazy { launchSource() }

    /** Leaves the way [GameExit] says for [origin]; the process ends in onDestroy. */
    private fun leave() {
        when (xendroid.compose.core.GameExit.way(origin, isTaskRoot)) {
            xendroid.compose.core.GameExit.Way.FINISH -> finish()
            xendroid.compose.core.GameExit.Way.FINISH_AND_REMOVE_TASK -> finishAndRemoveTask()
            xendroid.compose.core.GameExit.Way.TASK_TO_BACK_THEN_FINISH -> {
                moveTaskToBack(true)
                finish()
            }
        }
    }

    /** U09: the player gave up waiting for the game to start. */
    private fun cancelBoot() {
        bootCancelled = true
        runId?.let { id -> runCatching { xendroid.compose.sessions.SessionRuns.store().ending(id, "cancelled while starting") } }
        recordEvent("exit", "cancelled while starting", flush = true)
        leave()
    }

    /** Where this launch came from, as far as Android tells us (local record only). */
    private fun launchSource(): String {
        val origin = referrer?.host
        return when {
            origin == packageName -> "library"
            intent?.action == Intent.ACTION_VIEW && origin == null -> "shortcut"
            origin != null -> "external:$origin"
            else -> "external"
        }
    }

    /** Flight recorder entry (C01). [flush] writes the log now, for events a crash or kill may follow. */
    private fun recordEvent(kind: String, detail: String = "", flush: Boolean = false) {
        runEvents.record(kind, detail)
        if (flush) flushRunEvents()
    }

    private fun flushRunEvents() {
        val id = runId ?: return
        val log = runEvents.snapshotIfChanged() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { xendroid.compose.sessions.SessionRuns.store().saveEvents(id, log) }
                .onFailure { Log.w(TAG, "Saving the run events failed", it) }
        }
    }

    /** Thermal status and controller changes for the flight recorder (main thread). */
    private fun startEventSources() {
        runCatching {
            getSystemService(android.os.PowerManager::class.java)?.let { power ->
                noteThermal(power.currentThermalStatus)
                power.addThermalStatusListener(mainExecutor, thermalListener)
            }
        }.onFailure { Log.w(TAG, "Thermal status unavailable", it) }
        runCatching {
            InputDevice.getDeviceIds().forEach { noteController(it, "present") }
            getSystemService(android.hardware.input.InputManager::class.java)
                ?.registerInputDeviceListener(inputDeviceListener, mainHandler)
        }.onFailure { Log.w(TAG, "Input device events unavailable", it) }
    }

    private fun stopEventSources() {
        runCatching { getSystemService(android.os.PowerManager::class.java)?.removeThermalStatusListener(thermalListener) }
        runCatching { getSystemService(android.hardware.input.InputManager::class.java)?.unregisterInputDeviceListener(inputDeviceListener) }
    }

    /** 15j: every other second, Android's thermal headroom (10 s ahead) against the device's own
     *  limit; the timeline gets each change and the player one notice per rise, five minutes apart. */
    private fun watchThermalHeadroom() {
        if (thermalTick++ % 2 != 0) return
        val headroom = if (Build.VERSION.SDK_INT >= 30) {
            runCatching { getSystemService(android.os.PowerManager::class.java)?.getThermalHeadroom(10) }.getOrNull()
        } else null
        lastHeadroom = headroom?.takeIf { it.isFinite() && it >= 0f }
        val level = thermalWatch.sample(headroom, lastThermalStatus) ?: return
        recordEvent("thermal watch", level.name.lowercase().replace('_', ' ') +
            (headroom?.takeIf { it.isFinite() }?.let { " (headroom %.2f)".format(java.util.Locale.ROOT, it) } ?: ""))
        val now = android.os.SystemClock.elapsedRealtime()
        if (level != xendroid.compose.core.ThermalWatch.Level.OK && foregroundState.value &&
            (thermalNoticeAt == Long.MIN_VALUE || now - thermalNoticeAt >= xendroid.compose.core.ThermalWatch.NOTICE_EVERY_MS)) {
            thermalNoticeAt = now
            Toast.makeText(this, getString(if (level == xendroid.compose.core.ThermalWatch.Level.THROTTLING)
                R.string.host_thermal_throttling else R.string.host_thermal_near), Toast.LENGTH_LONG).show()
        }
    }

    /** 15n: what the performance panel shows this second; settings values in the app's language. */
    private fun buildPanelSnapshot(fg: PresentationState): xendroid.compose.core.PerformancePanel.Snapshot {
        val run = runPerformance.snapshot()
        val changed = bootChangedSettings
        val (shown, total) = xendroid.compose.core.PerformancePanel.changedSettings(changed)
        val effect = scalingEffect.intValue
        val image = listOfNotNull(
            if (effect < 0) getString(R.string.menu_scaling_inherited)
            else listOf("Bilinear", "CAS", "FSR", "SGSR", "Lanczos", "CRT").getOrNull(effect),
            xendroid.compose.core.PerformancePanel.resolutionScale(changed)?.let { getString(R.string.panel_resolution, it) },
        ).joinToString(" · ")
        val frameGeneration = when {
            !fg.requested -> getString(R.string.menu_off)
            fg.engine == 1 -> "LSFG ×${fg.multiplier}"
            else -> "Win-FG"
        }
        val limit = fpsLimitState.intValue.let { if (it == 0) getString(R.string.menu_unlimited) else it.toString() } +
            " · " + getString(R.string.panel_screen_hz, currentOutputHz().roundToInt().toString())
        val mode = listOfNotNull(getString(R.string.panel_sustained).takeIf { sustainedMode.value },
            performanceHintsLabel.value).joinToString(" · ")
        val driver = runCatching { session.activeDriverIdentity()?.label }.getOrNull() ?: gpuLabel.value.ifEmpty { "?" }
        return xendroid.compose.core.PerformancePanel.Snapshot(
            recentSeconds = performancePanel.recentSeconds,
            recent = performancePanel.recent(),
            run = xendroid.compose.core.PerformancePanel.pacing(run.frameTimeHistogramMs),
            fpsMedian = run.fpsPercentile(0.5),
            fpsLow = run.fpsPercentile(0.05),
            pipelines = run.pipelineCreations,
            pipelineMs = run.pipelineCreationMs,
            audioConcealed = run.audioConcealedBlocks,
            audioBlocks = run.audioBlocks,
            thermal = thermalWatch.level,
            headroom = lastHeadroom,
            settings = listOf(
                xendroid.compose.core.PerformancePanel.Setting.DRIVER to driver,
                xendroid.compose.core.PerformancePanel.Setting.SCALING to image,
                xendroid.compose.core.PerformancePanel.Setting.FRAME_GENERATION to frameGeneration,
                xendroid.compose.core.PerformancePanel.Setting.FPS_LIMIT to limit,
                xendroid.compose.core.PerformancePanel.Setting.PERFORMANCE_MODE to mode,
            ),
            changed = shown,
            changedTotal = total,
        )
    }

    private fun noteThermal(status: Int) {
        if (status == lastThermalStatus) return
        lastThermalStatus = status
        recordEvent("thermal", xendroid.compose.sessions.thermalStatusName(status),
            flush = status >= android.os.PowerManager.THERMAL_STATUS_SEVERE)
    }

    private fun noteController(deviceId: Int, what: String) {
        val device = InputDevice.getDevice(deviceId) ?: return
        val sources = device.sources
        val controller = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!controller || device.isVirtual) return
        // IDs only: a Bluetooth name can carry its owner's name.
        val id = "vendor 0x%04X product 0x%04X".format(device.vendorId, device.productId)
        controllers[deviceId] = id
        val key = device.descriptor ?: "device:$deviceId"
        controllerKeys[deviceId] = key
        val slot = controllerSlots.connect(key)
        recordEvent("controller", "$what ($id) " + (slot?.let { "as P${it + 1}" } ?: "with no free player slot"))
        if (slot != null && slot > 0) session.setSlotConnected(slot, true, "Controller ${slot + 1}")
    }

    /** A press, or a push past half, from a physical controller: playing P1, it puts the
     *  on-screen controls aside (TouchOverlayPresence). Virtual devices never count. */
    private fun noteControllerUse(event: android.view.InputEvent) {
        if (overlayHiddenByController.value) return
        val device = event.device ?: return
        if (device.isVirtual) return
        if (touchPresence.controllerInput(hideTouchWithController, playerSlot(event.deviceId))) {
            touchHiddenBy = event.deviceId
            overlayHiddenByController.value = true
            recordEvent("touch controls", "hidden: a controller is playing P1")
        }
    }

    private fun touchControlsBack(why: String) {
        touchHiddenBy = -1
        if (touchPresence.controllerGone()) recordEvent("touch controls", "shown: $why")
        overlayHiddenByController.value = false
    }

    /** L06: P1's profile as the config names it (the core signed it in at boot); null = nobody. */
    private fun firstPlayerXuid(): String? {
        val handle = xendroid.compose.settings.ConfigStore(applicationContext).openLiveSnapshot()
        return try {
            handle.getString(xendroid.compose.data.ProfileSlots.SECTION, xendroid.compose.data.ProfileSlots.key(0))
                ?.trim()?.ifEmpty { null }
        } finally { handle.closeDiscard() }
    }

    /** The player a device plays as; anything not assigned (keyboards, unknown devices) is P1, as before. */
    private fun playerSlot(deviceId: Int): Int =
        controllerKeys[deviceId]?.let { controllerSlots.slotOf(it) } ?: 0

    /** Before boot the core ignores slot changes: tell it which players exist once it runs. */
    private fun syncControllerSlots() {
        controllerSlots.players.forEachIndexed { slot, key ->
            if (slot > 0 && key != null) session.setSlotConnected(slot, true, "Controller ${slot + 1}")
        }
    }

    /** Per-second flight recorder checks: presentation state changes and guest stalls. */
    private fun notePresentation(state: PresentationState, running: Boolean, presentCount: Long) {
        val label = state.stateLabel
        val previousLabel = lastPresentationLabel
        lastPresentationLabel = label
        if (previousLabel != null && label != previousLabel) recordEvent("presentation", label)
        val previousCount = lastPresentCount
        lastPresentCount = presentCount
        if (previousCount < 0) return
        if (running && presentCount == previousCount) {
            stalledSeconds++
            if (stalledSeconds == STALL_EVENT_SECONDS) recordEvent("stall", "no new frame for $STALL_EVENT_SECONDS s", flush = true)
        } else {
            if (stalledSeconds >= STALL_EVENT_SECONDS) recordEvent("stall", "frames resumed after $stalledSeconds s")
            stalledSeconds = 0
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 10 = TRIM_MEMORY_RUNNING_LOW and above: the kill this may precede loses no events.
        recordEvent("memory", "trim level $level", flush = level >= 10)
    }

    /** [shown] in the dialog, in the shown language; [logged] (English) in the run's record.
     *  15e (DroidDeck `02f356f`): the dialog stays with Back, Try again and Share logs. */
    private fun showLaunchFailure(shown: String, logged: String = shown,
                                  kind: xendroid.compose.ui.ingame.LaunchFailure.Kind = xendroid.compose.ui.ingame.LaunchFailure.Kind.OTHER) {
        launchFailure = logged
        recordEvent("error", logged, flush = true)
        if (isFinishing || isDestroyed) return
        lifecycleScope.launch {
            // The core's last words, on screen only (never shared from here).
            val log = withContext(Dispatchers.IO) {
                runCatching {
                    java.io.File(Utils.get_log_file_path()).takeIf { it.isFile }?.let { f ->
                        java.io.RandomAccessFile(f, "r").use { raf ->
                            val from = (raf.length() - 4096).coerceAtLeast(0)
                            raf.seek(from)
                            val bytes = ByteArray((raf.length() - from).toInt())
                            raf.readFully(bytes)
                            String(bytes, Charsets.UTF_8).lines().map { it.trimEnd() }.filter { it.isNotBlank() }.takeLast(6)
                                .map { it.take(160) }
                        }
                    }
                }.getOrNull().orEmpty()
            }
            // A core that did not start on a custom driver may start on the system one.
            val customDriver = withContext(Dispatchers.IO) {
                runCatching { inGameConfig.driverPath(activeTitleState.value) }.getOrNull()?.isNotBlank() == true
            }
            val shownKind = if (kind == xendroid.compose.ui.ingame.LaunchFailure.Kind.CORE && customDriver)
                xendroid.compose.ui.ingame.LaunchFailure.Kind.DRIVER else kind
            launchFailureState.value = xendroid.compose.ui.ingame.LaunchFailure(shown, shownKind, log)
            if (failureView == null) {
                failureView = ComposeView(this@EmulatorHostActivity).apply {
                    setContent {
                        val failure = launchFailureState.value ?: return@setContent
                        val mode = xendroid.compose.ui.design.InputModeStore.read(this@EmulatorHostActivity)
                            .resolve(xendroid.compose.ui.design.rememberControllerConnected())
                        xendroidTheme(mode = mode) {
                            xendroid.compose.ui.ingame.LaunchFailureScreen(failure, loadingArt.value,
                                onBack = ::leave, onRetry = { tryAgain() }, onShareLogs = { shareSessionLogs(null) },
                                onRetrySystemDriver = { tryAgain(systemDriver = true) })
                        }
                    }
                }
                addContentView(failureView, android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
    }

    /** The failure screen's own view (it may come before the game's views exist). */
    private var failureView: ComposeView? = null

    /** Batch 2: the menu's status line: FPS now, p99 of the last seconds, battery, the driver. */
    private fun buildMenuStatus(): List<xendroid.compose.ui.ingame.MenuStat> = buildList {
        val fps = session.averageFps().takeIf { it > 0 } ?: lastGuestFps
        if (fps > 0) add(xendroid.compose.ui.ingame.MenuStat(fps.roundToInt().toString(), "FPS"))
        performancePanel.recent()?.let { add(xendroid.compose.ui.ingame.MenuStat(it.p99UnderMs.toString(), "ms", "p99")) }
        xendroid.compose.sessions.SessionRuns.batteryCelsius(applicationContext)?.let {
            add(xendroid.compose.ui.ingame.MenuStat("%.0f".format(it), "°C"))
        }
        val level = runCatching {
            (getSystemService(BATTERY_SERVICE) as android.os.BatteryManager).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrNull()?.takeIf { it in 0..100 }
        level?.let { add(xendroid.compose.ui.ingame.MenuStat("$it%", label = getString(R.string.xd_menu_battery))) }
        runCatching { session.activeDriverIdentity() }.getOrNull()?.let { d ->
            add(xendroid.compose.ui.ingame.MenuStat(listOf(d.driverName, d.driverInfo).filter { it.isNotBlank() }.joinToString(" ")
                .ifBlank { d.label }.take(40)))
        }
    }

    /** 15e: the core is single-shot per process, so the main process starts the game again in a
     *  new :emu once this one is gone. */
    private fun tryAgain(systemDriver: Boolean = false) {
        recordEvent("exit", if (systemDriver) "trying again with the system driver" else "trying again", flush = true)
        val source = intent
        val relaunch = Intent(this, MainActivity::class.java).apply {
            action = ACTION_RELAUNCH_GAME
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            RELAUNCH_EXTRAS.forEach { key ->
                source?.getStringExtra(key)?.let { putExtra(key, it) }
                source?.getStringArrayExtra(key)?.let { putExtra(key, it) }
            }
            if (systemDriver) {
                // This launch's own options, plus the system driver; the chosen one stays saved.
                val own = if (xendroid.compose.core.LaunchToken.matches(filesDir, source?.getStringExtra(EXTRA_LAUNCH_TOKEN)))
                    source?.getStringArrayExtra(EXTRA_LAUNCH_ARGS).orEmpty().filterNot { it.startsWith("--vulkan_lib_path=") } else emptyList()
                putExtra(EXTRA_LAUNCH_ARGS, (own + "--vulkan_lib_path=").toTypedArray())
                putExtra(EXTRA_LAUNCH_TOKEN, xendroid.compose.core.LaunchToken.get(filesDir))
            }
            // A game another app handed over keeps its read grant through the relaunch.
            source?.getStringExtra(EXTRA_GAME_URI)?.takeIf { it.startsWith("content://") }?.let { uri ->
                clipData = android.content.ClipData.newRawUri("game", android.net.Uri.parse(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        runCatching { startActivity(relaunch) }.onFailure { Log.w(TAG, "Starting the game again failed", it) }
        leave()
    }

    private fun enterImmersiveMode() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        // A TV or monitor that supports it switches to its low-latency game mode (ALLM).
        if (Build.VERSION.SDK_INT >= 30) window.setPreferMinimalPostProcessing(true)

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            hide(
                WindowInsetsCompat.Type.systemBars()
            )
        }
    }

    private fun readShowDebugOverlay(): Boolean =
        runCatching {
            val handle =
                ConfigStore(applicationContext)
                    .openLiveSnapshot()

            try {
                handle.getBool(
                    "Display",
                    "show_debug_overlay",
                    false
                )
            } finally {
                handle.closeDiscard()
            }
        }.getOrDefault(false)

    private fun readFullscreenStretch(): Boolean =
        runCatching {
            val handle =
                ConfigStore(applicationContext)
                    .openLiveSnapshot()

            try {
                !handle.getBool(
                    "Display",
                    "present_letterbox",
                    true
                )
            } finally {
                handle.closeDiscard()
            }
        }.getOrDefault(false)

    private fun persistFullscreenStretchConfig(
        enabled: Boolean
    ): Boolean =
        runCatching {
            ConfigStore(applicationContext).editLiveConfig { handle ->
                handle.putBool(
                    "Display",
                    "present_letterbox",
                    !enabled
                )
            }
        }.onFailure {
            Log.w(
                TAG,
                "persisting fullscreen stretch failed",
                it
            )
        }.isSuccess

    private fun prepareNativeRealPath(
        absPath: String
    ) {
        session.setupContext(this)
        session.setupGamePathReal(absPath)

        session.setupLaunchArgs(
            arrayOf(
                "--storage_root=" +
                    Utils.get_storage_root_path(),

                "--config=" +
                    Application
                        .get_global_config_file()
                        .absolutePath,

                "--log_file=" +
                    Utils.get_log_file_path(),

                "--log_append=true",
            ) + launchOptionArgs().also { launchArgsUsed = it }.toTypedArray()
        )

        session.setupUriInfoListFile(
            Application
                .get_uri_info_list_file()
                .absolutePath
        )
    }

    /** The options this launch passed to the core (R4), for the loading screen. */
    private var launchArgsUsed: List<String> = emptyList()

    /** Batch 2: what the loading screen says about the launch once the title is known. */
    private fun loadingDetailsOf(title: String): xendroid.compose.ui.ingame.LoadingDetails {
        val own = runCatching {
            val handle = ConfigStore(applicationContext).openGameConfig(title)
            try { xendroid.compose.settings.SettingsSchema.allSettings.count { handle.getString(it.section, it.name) != null } }
            finally { handle.closeDiscard() }
        }.getOrDefault(0)
        val driver = runCatching { xendroid.compose.ui.settings.driverName(this, inGameConfig.driverPath(title)) }.getOrNull()
        val limit = runCatching { session.fpsLimit() }.getOrNull()?.let {
            if (it == 0) getString(R.string.menu_unlimited) else getString(R.string.xd_boot_limit, it)
        }
        val profile = runCatching {
            firstPlayerXuid()?.let { xuid ->
                xendroid.compose.core.EmulatorRuntime.emulator
                    ?.list_profiles(xendroid.compose.core.ContentPaths.contentRoot().absolutePath)
                    ?.firstOrNull { it.xuid.equals(xuid, ignoreCase = true) }?.gamertag?.ifBlank { null }
            }
        }.getOrNull()
        return xendroid.compose.ui.ingame.LoadingDetails(profile = profile, titleId = title, badges = listOfNotNull(driver, limit),
            ownSettings = own, withOptions = launchArgsUsed.isNotEmpty())
    }

    /** R4: this launch's own options, when the library sent them (its token); every other launch
     *  boots as configured. */
    private fun launchOptionArgs(): List<String> {
        val requested = intent?.getStringArrayExtra(EXTRA_LAUNCH_ARGS) ?: return emptyList()
        if (!xendroid.compose.core.LaunchToken.matches(filesDir, intent?.getStringExtra(EXTRA_LAUNCH_TOKEN))) {
            Log.w(TAG, "Launch options without this app's token ignored")
            return emptyList()
        }
        val args = xendroid.compose.core.LaunchOptions.sanitize(requested, Application.get_custom_driver_dir())
        if (args.size != requested.size) Log.w(TAG, "${requested.size - args.size} launch options dropped")
        if (args.isNotEmpty()) recordEvent("launch options", args.joinToString(" ") { it.substringBefore('=') })
        return args
    }

    /** 15c: follows the fold of a foldable (Jetpack WindowManager); a horizontal one half open is
     *  the tabletop posture "Split screen" waits for. Devices without one report none. */
    private fun watchFold() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    androidx.window.layout.WindowInfoTracker.getOrCreate(this@EmulatorHostActivity)
                        .windowLayoutInfo(this@EmulatorHostActivity)
                        .collect { info ->
                            val fold = info.displayFeatures.filterIsInstance<androidx.window.layout.FoldingFeature>().firstOrNull {
                                it.state == androidx.window.layout.FoldingFeature.State.HALF_OPENED &&
                                    it.orientation == androidx.window.layout.FoldingFeature.Orientation.HORIZONTAL
                            }
                            windowFold.value = fold?.bounds?.let { it.top to it.bottom }
                        }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "The fold state is unavailable", e)
                }
            }
        }
    }

    private fun installSurfaceView() {
        val sv =
            SurfaceView(this).apply {
                isFocusable = true
                isFocusableInTouchMode = true

                holder.addCallback(
                    this@EmulatorHostActivity
                )

                setOnGenericMotionListener { _, ev ->
                    onGenericMotion(ev)
                }
            }

        surfaceView = sv
        externalDisplay = ExternalGameDisplay(this, sv, this, detach = {
            surfaceAvailable = false
            pauseForLifecycle()
            if (started) session.detachSurface()
        }, statusChanged = {
            externalDisplayLabel.value = it
            gameOnExternalDisplay.value = externalDisplay?.activeDisplay != null
        })
        tvMargin.floatValue = getSharedPreferences(DISPLAY_SETTINGS_PREFS, MODE_PRIVATE).getFloat("tv_margin_percent", 0f)
            .coerceIn(0f, xendroid.compose.core.TvMargin.MAX)
        externalDisplay?.setMargin(tvMargin.floatValue)

        val compose =
            ComposeView(this).apply {
                setContent {
                    // Batch 2: the menu and panels in touch or controller layout, like the app.
                    val hostMode = xendroid.compose.ui.design.InputModeStore.read(this@EmulatorHostActivity)
                        .resolve(xendroid.compose.ui.design.rememberControllerConnected())
                    val cfg by gamepad.config.collectAsState(
                        initial = GamepadConfigDto()
                    )

                    val landscape =
                        resources.configuration.orientation ==
                            Configuration.ORIENTATION_LANDSCAPE

                    val booted by bootedState

                    val playingTitle by activeTitleState
                    val controls =
                        remember(
                            cfg,
                            landscape,
                            playingTitle
                        ) {
                            // The running game's own layout when it has one (U06).
                            gamepad.controlsFor(
                                cfg,
                                landscape,
                                playingTitle
                            )
                        }

                    val (visible, poke) =
                        rememberAutoHide(
                            cfg.globals.autoHideSeconds
                        )

                    val alpha by animateFloatAsState(
                        if (visible) {
                            cfg.globals.opacity
                        } else {
                            0f
                        },
                        tween(500),
                        label = "padAlpha"
                    )

                    LaunchedEffect(cfg.globals.hideWithController) {
                        hideTouchWithController = cfg.globals.hideWithController
                        if (!hideTouchWithController && overlayHiddenByController.value) {
                            touchPresence.disabled()
                            touchHiddenBy = -1
                            overlayHiddenByController.value = false
                        }
                    }

                    val padVisible =
                        showTouchOverlay.value == true && !overlayHiddenByController.value

                    val overlayActive =
                        booted &&
                            foregroundState.value &&
                            cfg.globals.enabled &&
                            padVisible &&
                            !menuState.value.open

                    DisposableEffect(
                        overlayActive
                    ) {
                        overlayWantsBrightness =
                            overlayActive

                        if (overlayActive) {
                            brightnessSampler.start(sv)
                        }

                        onDispose {
                            brightnessSampler.stop()
                        }
                    }

                    val contrastState =
                        rememberOverlayContrast(
                            brightnessSampler.brightness
                        )

                    LaunchedEffect(
                        cfg.globals.hapticsEnabled
                    ) {
                        configureHaptics(
                            cfg.globals.hapticsEnabled
                        )
                    }

                    // 15c: where the layout is in the window (to place a fold) and how big it is.
                    var layoutBox by remember { mutableStateOf<Pair<IntSize, Int>?>(null) }
                    val fold by windowFold
                    val split = layoutBox?.let { (size, top) ->
                        val hinge = fold?.let { (foldTop, foldBottom) ->
                            xendroid.compose.gamepad.SplitScreen.hingeInLayout(foldTop, foldBottom, top, size.height)
                        }
                        // Without a fold the split is for the touch controls: a controller playing
                        // (or the controls off) gives the picture the whole screen back.
                        val touchShown = cfg.globals.enabled && padVisible
                        if (gameOnExternalDisplay.value || (hinge == null && !touchShown)) null
                        else xendroid.compose.gamepad.SplitScreen.layout(size.width, size.height,
                            xendroid.compose.gamepad.SplitScreenMode.parse(cfg.globals.splitScreen), hinge)
                    }
                    LaunchedEffect(split) {
                        val text = split?.let { "game ${it.game.width}x${it.game.height}, controls ${it.controls.width}x${it.controls.height}" }
                        if (text != splitRecorded && (text != null || splitRecorded != null)) recordEvent("split", text ?: "off")
                        splitRecorded = text
                    }

                    Box(Modifier.fillMaxSize().onGloballyPositioned { c ->
                        val next = c.size to c.positionInWindow().y.roundToInt()
                        if (next != layoutBox) layoutBox = next
                    }) {
                            AndroidView(
                                factory = {
                                    sv
                                },
                                modifier =
                                    split?.let { Modifier.atRect(it.game) } ?: Modifier.fillMaxSize()
                            )

                            // 15e: the loading screen until the first guest frame, then a short fade.
                            val loading = bootStatus.value
                            var lastLoading by remember { mutableStateOf(loading) }
                            if (loading != null) lastLoading = loading
                            androidx.compose.animation.AnimatedVisibility(
                                visible = loading != null,
                                enter = androidx.compose.animation.EnterTransition.None,
                                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400)),
                            ) {
                                lastLoading?.let { status ->
                                    xendroidTheme(scale = uiScale, mode = hostMode) {
                                        xendroid.compose.ui.ingame.GameLoadingScreen(status, loadingArt.value, loadingName,
                                            onCancel = if (menuState.value.open || loading == null) null else ::cancelBoot,
                                            details = loadingDetails.value)
                                    }
                                }
                            }

                            if (
                                booted &&
                                foregroundState.value &&
                                cfg.globals.enabled &&
                                padVisible &&
                                !menuState.value.open &&
                                bootStatus.value == null
                            ) {
                                GamepadOverlay(
                                    controls =
                                        controls,
                                    adaptiveSticks = adaptiveSticks.value,
                                    touchCamera = touchCamera.value,
                                    cameraSensitivity = cfg.globals.cameraSensitivity,
                                    cameraAreaStart = cfg.globals.cameraAreaStart,
                                    slideButtons = cfg.globals.slideButtons,
                                    slideSticks = cfg.globals.slideSticks,

                                    opacity =
                                        alpha,

                                    contrast = {
                                        contrastState.value
                                    },

                                    onUserInteraction =
                                        poke,

                                    onKeyEvent = {
                                        kc,
                                        pressed,
                                        v ->

                                        if (
                                            pressed &&
                                            v ==
                                                Kc.VALUE_UNUSED
                                        ) {
                                            maybeVibrate()
                                        }
                                        if (kc in KC_RTHUMB_LEFT..KC_RTHUMB_DOWN) touchStickHeld[kc] = pressed

                                        session.keyEvent(
                                            kc,
                                            pressed,
                                            v
                                        )
                                    },

                                    modifier =
                                        split?.let { Modifier.atRect(it.controls) } ?: Modifier.fillMaxSize(),
                                )
                            }

                            LaunchedEffect(
                                booted
                            ) {
                                if (!booted) {
                                    return@LaunchedEffect
                                }
                                // Guest rumble (I04): only while the game itself runs, never under the
                                // menu or a pause, where the last requested strength would linger.
                                // Phone controllers (I09) get their slot's rumble raw: each phone
                                // applies its owner's intensity. Their input is held meanwhile.
                                launch {
                                    while (isActive) {
                                        delay(50)
                                        // U10: the same for the keyboard grid, in two dimensions.
                                        if (keyboardRequestState.value != null && gridHatDirection != 4 &&
                                            gridRepeat.press(gridHatDirection, SystemClock.uptimeMillis())) {
                                            keyboardGrid.value = keyboardGrid.value.move(gridHatDirection % 3 - 1, gridHatDirection / 3 - 1)
                                        }
                                        // U04: a stick or hat held on a menu keeps moving at the menu's pace.
                                        val heldDirection = if (panelNavPrev) -1 else if (panelNavNext) 1 else 0
                                        if (heldDirection != 0) panelNav()?.let { nav ->
                                            if (navRepeat.press(heldDirection, SystemClock.uptimeMillis())) movePanelSelection(nav, heldDirection)
                                        }
                                        val playing = foregroundState.value && !menuState.value.open && !session.isPaused()
                                        val phones = phoneControllers.host != null
                                        val controllerRumble = rumbleSettings.value.anyOn
                                        val guest = if (playing && (controllerRumble || phones)) session.rumbleState() else null
                                        if (phones) runCatching { phoneControllers.tick(playing, guest) }
                                        val state = if (controllerRumble) guest else null
                                        if (state == null) stopRumble() else runCatching { driveRumble(state) }
                                    }
                                }
                                syncControllerSlots()

                                while (isActive) {
                                    showTouchOverlay.value =
                                        session
                                            .showTouchOverlayEnabled()
                                    gameModeSignal.update(xendroid.compose.core.gamePhase(foregroundState.value,
                                        firstFrame = bootStatus.value == null,
                                        menuOrPaused = menuState.value.open || session.isPaused()))
                                    // The controller that put the touch controls aside now plays P2–P4.
                                    if (overlayHiddenByController.value && touchHiddenBy >= 0 && playerSlot(touchHiddenBy) != 0) {
                                        touchControlsBack("the controller now plays another player")
                                    }

                                    val activeTitle = session.activeTitleId()
                                    performanceHints.update(session.presenterWork(),
                                        1_000_000_000L / session.fpsLimit().coerceAtLeast(30),
                                        foregroundState.value && !menuState.value.open)
                                    performanceHintsLabel.value = performanceHints.status
                                    val activeRun = runId
                                    if (activeTitle != controlsTitleId) {
                                        controlsTitleId = activeTitle
                                        activeTitleState.value = activeTitle
                                        // 15g: this game's own HUD look, when it has one.
                                        hudLook.value = xendroid.compose.core.HudPlacements.read(
                                            HudPreferences.of(this@EmulatorHostActivity), activeTitle).look
                                        if (bootStatus.value != null && loadingArt.value == null && activeTitle != null) {
                                            loadingArt.value = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    xendroid.compose.core.LaunchArt.forTitle(
                                                        xendroid.compose.data.CoverStore(java.io.File(filesDir, "covers")), activeTitle)
                                                }.getOrNull()
                                            }
                                        }
                                        val driver = session.activeDriverIdentity()
                                        recordEvent("title", activeTitle?.let { "$it running" } ?: "none active", flush = true)
                                        if (driver != null && !driverRecorded) {
                                            driverRecorded = true
                                            recordEvent("driver", driver.label)
                                        }
                                        if (activeTitle != null) lifecycleScope.launch(Dispatchers.IO) {
                                            runCatching { SessionLogs.noteTitle(activeTitle, BuildConfig.VERSION_NAME) }
                                                .onFailure { Log.w(TAG, "Writing diagnostic session context failed", it) }
                                            val profile = runCatching { firstPlayerXuid() }
                                                .onFailure { Log.w(TAG, "Reading P1's profile failed", it) }.getOrNull()
                                            val modules = runCatching { session.moduleHashes() }.getOrDefault(emptyList())
                                            val settings = runCatching { session.changedSettings() }
                                                .onFailure { Log.w(TAG, "Reading the changed settings failed", it) }.getOrNull()
                                            bootChangedSettings = settings
                                            if (activeRun != null) runCatching { xendroid.compose.sessions.SessionRuns.store().running(activeRun, activeTitle, driver, profile, modules, settings) }
                                                .onFailure { Log.w(TAG, "Recording the running title failed", it) }
                                        }
                                        // C07: the vblank cap this run booted with, for comparisons; U01: the
                                        // driver setting it started with, for the Graphics tab.
                                        if (activeTitle != null) lifecycleScope.launch {
                                            withContext(Dispatchers.IO) {
                                                runCatching { inGameConfig.guestRefreshCap(activeTitle) }
                                                    .onFailure { Log.w(TAG, "Reading the vblank cap failed", it) }.getOrNull()
                                            }?.let { runPerformance.guestRefreshCap(it) }
                                            driverAtBoot.value = withContext(Dispatchers.IO) {
                                                runCatching { inGameConfig.driverPath(activeTitle) }
                                                    .onFailure { Log.w(TAG, "Reading the driver setting failed", it) }.getOrNull()
                                            }
                                            if (bootStatus.value != null) loadingDetails.value = withContext(Dispatchers.IO) {
                                                runCatching { loadingDetailsOf(activeTitle) }
                                                    .onFailure { Log.w(TAG, "Reading what the game starts with failed", it) }.getOrNull()
                                            }
                                        }
                                        adaptiveSticks.value = activeTitle != null &&
                                            getSharedPreferences("touch_options", MODE_PRIVATE)
                                                .getBoolean("adaptive_$activeTitle", false)
                                    }
                                    // One sample per second for the run summary (C02); paused or
                                    // background seconds count as idle, never as FPS.
                                    val fgNow = session.presentationState()
                                    val frameTimes = session.guestFrameTimeHistogram()
                                    val guestFrames = frameTimes?.sum()
                                    val runningNow = foregroundState.value && !session.isPaused()
                                    runPerformance.sample(
                                        running = runningNow,
                                        guestFps = session.averageFps(),
                                        presentCount = session.hostPresentSubmissionCount(),
                                        generatedCount = fgNow.generated,
                                        frameGenerationActive = fgNow.requested && fgNow.state == 2,
                                        guestFrames = guestFrames,
                                        fpsLimit = session.fpsLimit(),
                                        displayHz = currentOutputHz(),
                                    )
                                    runPerformance.frameTimes(frameTimes)
                                    performancePanel.frameTimes(frameTimes)
                                    runPerformance.frameGeneration(session.frameGenerationGpuHistogram(), fgNow.lateSkips, fgNow.dropped, fgNow.syntheticSlots)
                                    // F04, advisory only: a verdict change goes to the timeline.
                                    fgGovernor.sample(xendroid.compose.core.FrameGenerationGovernor.Second(
                                        active = fgNow.requested && fgNow.state == 2, displayHz = fgNow.hz,
                                        multiplier = if (fgNow.engine == 1) fgNow.multiplier else 2,
                                        guestFps = session.averageFps(), gpuMs = fgNow.gpuMs,
                                        slots = fgNow.syntheticSlots, lateSkips = fgNow.lateSkips,
                                        thermalStatus = lastThermalStatus,
                                    ))?.let { recordEvent("fg budget", it.text) }
                                    // What frame generation does, or would do, at this refresh rate.
                                    if (runningNow && !menuState.value.open) {
                                        session.averageFps().takeIf { it > 0 }?.let { lastGuestFps = it }
                                        val generatedNs = android.os.SystemClock.elapsedRealtimeNanos()
                                        if (lastGenerated >= 0 && generatedNs > lastGeneratedNs) {
                                            generatedPerSecond = (fgNow.generated - lastGenerated).coerceAtLeast(0) *
                                                1e9 / (generatedNs - lastGeneratedNs)
                                        }
                                        lastGenerated = fgNow.generated
                                        lastGeneratedNs = generatedNs
                                    } else {
                                        lastGenerated = -1L
                                    }
                                    if (BuildConfig.DEBUG) fgNotes.value = frameGenerationNotes(fgNow)
                                    fgBudgetLabel.value = fgGovernor.current.takeIf {
                                        it.verdict != xendroid.compose.core.FrameGenerationGovernor.Verdict.OFF
                                    }?.let { "Budget (advisory, never acts): ${it.text}" }
                                    notePresentation(fgNow, runningNow, guestFrames ?: session.hostPresentSubmissionCount())
                                    if (runningNow) watchThermalHeadroom()
                                    val compileStats = session.shaderCompileStats()
                                    if (bootStatus.value != null) {
                                        val elapsed = (android.os.SystemClock.elapsedRealtime() - createdAtMs) / 1000
                                        if ((guestFrames ?: 0L) > 0L) {
                                            bootStatus.value = null
                                            runPerformance.firstFrame(elapsed.toInt())
                                            recordEvent("boot", "first guest frames after $elapsed s")
                                        } else {
                                            bootStatus.value = xendroid.compose.ui.ingame.bootStatus(activeTitle != null,
                                                compileStats?.getOrNull(0) ?: 0L, compileStats?.getOrNull(2) ?: 0L, elapsed)
                                        }
                                    }
                                    compileStats?.let { stats ->
                                        runPerformance.compiles(stats)
                                        when (val burst = compileBursts.sample(stats[1], stats[0])) {
                                            // Driver crashes during compiles happen: keep the start on disk.
                                            xendroid.compose.sessions.BurstTracker.Burst.Started ->
                                                recordEvent("compile", "pipeline creation burst started", flush = true)
                                            is xendroid.compose.sessions.BurstTracker.Burst.Ended -> recordEvent("compile",
                                                "${burst.events} pipelines, ${burst.amount / 1_000_000} ms over ${burst.seconds} s")
                                            null -> {}
                                        }
                                    }
                                    session.audioRunStats()?.let { audio ->
                                        runPerformance.audio(audio)
                                        when (val burst = audioBursts.sample(audio[2], audio[1])) {
                                            xendroid.compose.sessions.BurstTracker.Burst.Started ->
                                                recordEvent("audio", "underruns started")
                                            is xendroid.compose.sessions.BurstTracker.Burst.Ended -> recordEvent("audio",
                                                "${burst.amount} of ${burst.events} blocks concealed, ${burst.seconds} s with underruns")
                                            null -> {}
                                        }
                                    }
                                    if (menuState.value.open) menuStatus.value = buildMenuStatus()
                                    // 15n: the performance panel, built only while it is on screen.
                                    panelSnapshot.value = if (performanceOverlayEnabled.value &&
                                        hudDetail.value == xendroid.compose.core.HudDetail.PANEL) {
                                        runCatching { buildPanelSnapshot(fgNow) }
                                            .onFailure { Log.w(TAG, "Building the performance panel failed", it) }.getOrNull()
                                    } else null
                                    // Bounds the play time of a run that later dies without finishing.
                                    val nowMs = android.os.SystemClock.elapsedRealtime()
                                    if (activeRun != null && nowMs - lastRunHeartbeatMs >= 30_000) {
                                        lastRunHeartbeatMs = nowMs
                                        runPerformance.battery(xendroid.compose.sessions.SessionRuns.batteryCelsius(applicationContext))
                                        val summary = runPerformance.snapshot()
                                        val driver = session.activeDriverIdentity()
                                        if (driver != null && !driverRecorded) {
                                            driverRecorded = true
                                            recordEvent("driver", driver.label)
                                        }
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            val modules = runCatching { session.moduleHashes() }.getOrDefault(emptyList())
                                            runCatching { xendroid.compose.sessions.SessionRuns.store().heartbeat(activeRun, summary, driver, modules) }
                                        }
                                        flushRunEvents()
                                    }

                                    // FG can stop by itself (failure, cadence over Hz) while the
                                    // menu is closed: hand the temporary cap back then too.
                                    if (generationCap.active && !menuState.value.open) {
                                        val state = session.presentationState()
                                        if (!state.requested) { presentationState.value = state; restoreGenerationCap() }
                                    }
                                    if (menuState.value.open) {
                                        presentationState.value = session.presentationState()
                                        if (!presentationState.value.requested) restoreGenerationCap()
                                        audioVolume.intValue = session.audioVolume()
                                        gpuLabel.value = session.activeGpuLabel()
                                        menuPaused.value = session.isPaused()
                                        fpsLimitState.intValue = session.fpsLimit()
                                        if (activeTitle != fpsConfig.value.titleId) refreshFpsConfig()
                                        // Players joining or leaving and their latency.
                                        refreshPhoneControllers()
                                    }

                                    delay(1000)
                                }
                            }

                            FpsOverlay(
                                session = session,

                                visible =
                                    booted &&
                                        foregroundState.value && !menuState.value.open &&
                                        performanceOverlayEnabled.value,
                                detail = hudDetail.value,
                                panel = panelSnapshot.value,
                                metrics = hudMetrics.value,
                                titleId = activeTitleState.value,
                                look = hudLook.value,

                                modifier =
                                    Modifier.fillMaxSize(),
                            )

                            if (editorOpen.value) {
                                xendroidTheme(mode = hostMode) { GamepadEditorScreen(
                                    controller = gamepad,
                                    onDone = { editorOpen.value = false },
                                    inGame = true,
                                    titleId = activeTitleState.value,
                                    gameName = loadingName,
                                ) }
                            } else if (menuState.value.open) {
                                xendroidTheme(scale = uiScale, mode = hostMode) {
                                    // U02: from string resources (en / pt-BR); status texts built by
                                    // other components (ADPF, TV, phone controllers) are still English.
                                    val on = stringResource(R.string.menu_on)
                                    val off = stringResource(R.string.menu_off)
                                    val rumbleNames = xendroid.compose.gamepad.RumbleIntensity.entries.associateWith { xendroid.compose.ui.rumbleLabel(it) }
                                    InGameMenu(
                                        state = menuState.value,
                                        paused = menuPaused.value,
                                        fpsLimit = fpsLimitState.intValue,
                                        fpsConfig = fpsConfig.value,
                                        presentation = presentationState.value,
                                        fgPreset = fgPreset.intValue,
                                        lsfgAvailable = lsfgCache.value != null && !importingLsfg,
                                        extensionLabels = mapOf(
                                            InGameAction.COLOR_FILTER to if (presentationState.value.colorError != 0) stringResource(R.string.menu_color_filter_unavailable) else
                                                stringResource(R.string.menu_color_filter_value, listOf(off, stringResource(R.string.menu_color_grayscale),
                                                    stringResource(R.string.menu_color_contrast), stringResource(R.string.menu_color_warm),
                                                    stringResource(R.string.menu_color_vivid))[presentationState.value.colorFilter.coerceIn(0, 4)]),
                                            InGameAction.LSFG_MULTIPLIER to if (lsfgTarget.intValue != xendroid.compose.core.FrameGenerationTarget.OFF)
                                                stringResource(R.string.menu_lsfg_multiplier_target, lsfgMultiplier.intValue)
                                                else stringResource(R.string.menu_lsfg_multiplier_value, lsfgMultiplier.intValue),
                                            InGameAction.LSFG_TARGET to stringResource(R.string.menu_lsfg_target_value, lsfgTargetText()),
                                            InGameAction.PERFORMANCE_HINTS to performanceHintsLabel.value,
                                            InGameAction.EXTERNAL_DISPLAY to (externalDisplayLabel.value ?: stringResource(R.string.tv_phone)),
                                            InGameAction.TV_MARGIN to stringResource(R.string.menu_tv_margin,
                                                java.text.NumberFormat.getNumberInstance().format(tvMargin.floatValue.toDouble())),
                                            InGameAction.SCALING_EFFECT to stringResource(R.string.menu_scaling_value,
                                                listOf(stringResource(R.string.menu_scaling_inherited), "Bilinear", "CAS", "FSR", "SGSR", "Lanczos", "CRT")[scalingEffect.intValue + 1]),
                                            InGameAction.REFRESH_RATE to stringResource(R.string.menu_refresh_rate_value,
                                                requestedRefresh.value?.toString() ?: stringResource(R.string.menu_auto),
                                                (if (Build.VERSION.SDK_INT >= 30) display?.refreshRate else windowManager.defaultDisplay.refreshRate).toString()),
                                            InGameAction.SUSTAINED_PERFORMANCE to stringResource(R.string.menu_sustained_value,
                                                if (!sustainedAvailable.value) stringResource(R.string.menu_unavailable) else if (sustainedMode.value) on else off),
                                            InGameAction.BACKGROUND_POLICY to stringResource(R.string.menu_background_value, backgroundPolicy.value.name),
                                            InGameAction.GYRO_CAMERA to if (!gyroCamera.available) stringResource(R.string.menu_gyro_unavailable)
                                                else stringResource(R.string.menu_gyro_camera_value, if (gyroEnabled.value) on else off),
                                            InGameAction.GYRO_AIM to if (!gyroCamera.available) stringResource(R.string.menu_gyro_unavailable)
                                                else stringResource(R.string.menu_gyro_aim_value, stringResource(when (gyroAim.value) {
                                                    xendroid.compose.gamepad.GyroAim.ALWAYS -> R.string.menu_gyro_aim_always
                                                    xendroid.compose.gamepad.GyroAim.WHILE_LT -> R.string.menu_gyro_aim_lt
                                                    xendroid.compose.gamepad.GyroAim.WHILE_LB -> R.string.menu_gyro_aim_lb
                                                })),
                                            InGameAction.UNBUFFERED_INPUT to stringResource(R.string.menu_unbuffered_value,
                                                if (unbufferedInput.value) on else off),
                                            InGameAction.HUD_LOOK to stringResource(R.string.menu_hud_look, stringResource(when (hudLook.value) {
                                                xendroid.compose.core.HudLook.BOX -> R.string.menu_hud_look_box
                                                xendroid.compose.core.HudLook.OUTLINE -> R.string.menu_hud_look_outline
                                                xendroid.compose.core.HudLook.PLAIN -> R.string.menu_hud_look_plain
                                            })),
                                            InGameAction.SPLIT_SCREEN to stringResource(R.string.menu_split_value, stringResource(
                                                xendroid.compose.gamepad.splitScreenLabel(xendroid.compose.gamepad.SplitScreenMode.parse(cfg.globals.splitScreen)))),
                                            InGameAction.GYRO_SENSITIVITY to stringResource(R.string.menu_gyro_sensitivity_value, listOf(
                                                stringResource(R.string.menu_low), stringResource(R.string.menu_normal), stringResource(R.string.menu_high))[gyroSensitivity.intValue]),
                                            InGameAction.CONTROLLER_RUMBLE to stringResource(R.string.menu_rumble_value, rumbleNames.getValue(rumbleSettings.value.default),
                                                controllerSlots.players.withIndex()
                                                    .filter { it.value != null && !it.value!!.startsWith(xendroid.compose.companion.CompanionHost.KEY_PREFIX) }
                                                    .joinToString(", ") { player ->
                                                        // U08: a controller with its own intensity says it.
                                                        "P${player.index + 1}" + (player.value?.let { rumbleSettings.value.perDevice[it] }?.let { " (${rumbleNames.getValue(it)})" } ?: "")
                                                    }.ifEmpty { stringResource(R.string.menu_no_controller) }),
                                            InGameAction.PHONE_CONTROLLERS to (phoneControllersLabel.value ?: stringResource(R.string.phone_ctl_off)),
                                            InGameAction.TOUCH_CAMERA to stringResource(R.string.menu_touch_camera, if (touchCamera.value) on else off),
                                            InGameAction.MARK_SCENE to stringResource(R.string.menu_mark_scene, sceneMarkers.intValue),
                                            InGameAction.DRIVER_INFO to driverLine.value.let { (state, label) ->
                                                when (state) {
                                                    xendroid.compose.driver.DriverIdentity.InGame.UNKNOWN -> stringResource(R.string.menu_driver_unknown)
                                                    xendroid.compose.driver.DriverIdentity.InGame.AS_SELECTED -> stringResource(R.string.menu_driver, label)
                                                    xendroid.compose.driver.DriverIdentity.InGame.CUSTOM_DID_NOT_LOAD -> stringResource(R.string.menu_driver_fallback, label)
                                                    xendroid.compose.driver.DriverIdentity.InGame.OTHER_FOR_NEXT_START -> stringResource(R.string.menu_driver_next, label)
                                                }
                                            },
                                        ),
                                        phoneControllers = phoneControllersDetails.value,
                                        frameGenerationBudget = fgBudgetLabel.value,
                                        frameGenerationNotes = fgNotes.value,
                                        performanceHud = performanceOverlayEnabled.value,
                                        compactHud = hudDetail.value == xendroid.compose.core.HudDetail.COMPACT,
                                        hudPanel = hudDetail.value == xendroid.compose.core.HudDetail.PANEL,
                                        hudMetrics = hudMetrics.value,
                                        touchControls = showTouchOverlay.value == true && !overlayHiddenByController.value,
                                        adaptiveSticks = adaptiveSticks.value,
                                        stretch = fullscreenStretchEnabled.value,
                                        volume = audioVolume.intValue,
                                        sessionInfo = "${BuildConfig.VERSION_NAME}\n${gpuLabel.value.ifEmpty { "GPU information unavailable" }}\nAudio output follows Android's media route.",
                                        logSessions = menuLogSessions.value,
                                        onLogChoice = ::chooseLogSession,
                                        onPage = { page -> menuState.value = menuState.value.copy(page = page) },
                                        onSelect = { index -> menuState.value = menuState.value.select(index) },
                                        onAction = ::performMenuAction,
                                        onQuitChoice = ::chooseMenuQuit,
                                        gameName = loadingName,
                                        art = loadingArt.value,
                                        status = menuStatus.value,
                                    )
                                }
                            } else if (
                                booted && keyboardRequestState.value == null &&
                                discRequestState.value == null && messageBoxRequestState.value == null
                            ) {
                                InGameMenuHandle(
                                    onOpen = { openMenu(pause = false) },
                                    modifier = Modifier.align(Alignment.CenterStart),
                                )
                            }

                            val keyboardRequest by
                                keyboardRequestState

                            LaunchedEffect(
                                booted
                            ) {
                                if (!booted) {
                                    return@LaunchedEffect
                                }

                                while (isActive) {
                                    if (
                                        keyboardRequestState.value ==
                                            null
                                    ) {
                                        session
                                            .keyboardRequest()
                                            ?.let { req ->

                                                val maxUnits = if (req.maxLength <= 0) Int.MAX_VALUE else req.maxLength
                                                keyboardGrid.value = xendroid.compose.ui.keyboard.KeyboardGrid(maxUnits = maxUnits)
                                                    .withText(req.defaultText.orEmpty())
                                                gridHatDirection = 4
                                                gridRepeat.release()

                                                panelSelectedState.intValue =
                                                    0

                                                keyboardRequestState.value =
                                                    req
                                                recordEvent("guest-ui", "text entry prompt")
                                            }
                                    }

                                    delay(
                                        KEYBOARD_POLL_MS
                                    )
                                }
                            }

                            keyboardRequest?.let { req ->
                                xendroidTheme(scale = uiScale, mode = hostMode) {
                                    GuestKeyboardPanel(
                                        request = req,

                                        grid = keyboardGrid.value,

                                        onGridChange = { keyboardGrid.value = it },

                                        onAccept = {
                                            text ->
                                            acceptKeyboard(
                                                text
                                            )
                                        },

                                        onCancel = {
                                            cancelKeyboard()
                                        },

                                        modifier =
                                            Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            val messageBoxRequest by
                                messageBoxRequestState

                            LaunchedEffect(
                                booted
                            ) {
                                if (!booted) {
                                    return@LaunchedEffect
                                }

                                while (isActive) {
                                    if (
                                        messageBoxRequestState.value ==
                                            null
                                    ) {
                                        session
                                            .messageBoxRequest()
                                            ?.let { req ->

                                                panelSelectedState.intValue =
                                                    req.activeButton

                                                messageBoxRequestState.value =
                                                    req
                                                recordEvent("guest-ui", "message box (${req.buttons?.size ?: 0} buttons)")
                                            }
                                    }

                                    delay(
                                        KEYBOARD_POLL_MS
                                    )
                                }
                            }

                            messageBoxRequest?.let { req ->
                                xendroidTheme(scale = uiScale, mode = hostMode) {
                                    GuestMessageBoxPanel(
                                        request = req,

                                        selected =
                                            panelSelectedState.intValue,

                                        onChoose = {
                                            button ->
                                            answerMessageBox(
                                                button
                                            )
                                        },

                                        modifier =
                                            Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            val discRequest by
                                discRequestState

                            LaunchedEffect(
                                booted
                            ) {
                                if (!booted) {
                                    return@LaunchedEffect
                                }

                                while (isActive) {
                                    if (
                                        discRequestState.value ==
                                            null
                                    ) {
                                        session
                                            .discRequest()
                                            ?.let { req ->

                                                panelSelectedState.intValue =
                                                    0

                                                discRequestState.value =
                                                    req
                                                recordEvent("guest-ui", "disc ${req.discNumber} requested", flush = true)
                                            }
                                    }

                                    delay(
                                        KEYBOARD_POLL_MS
                                    )
                                }
                            }

                            discRequest?.let { req ->
                                xendroidTheme(scale = uiScale, mode = hostMode) {
                                    DiscSwapPanel(
                                        request = req,

                                        selected =
                                            panelSelectedState.intValue,

                                        onChoose = {
                                            path ->
                                            chooseDisc(
                                                path
                                            )
                                        },

                                        onCancel = {
                                            cancelDisc()
                                        },

                                        modifier =
                                            Modifier.fillMaxSize(),
                                    )
                                }
                            }
                    }

                    val menuOpen = menuState.value.open

                    val keyboardOpen =
                        keyboardRequestState.value !=
                            null

                    val discOpen =
                        discRequestState.value !=
                            null

                    val messageBoxOpen =
                        messageBoxRequestState.value !=
                            null

                    BackHandler(
                        enabled = keyboardOpen
                    ) {
                        cancelKeyboard()
                    }

                    BackHandler(
                        enabled = discOpen
                    ) {
                        cancelDisc()
                    }

                    BackHandler(
                        enabled = messageBoxOpen
                    ) {
                        messageBoxRequestState.value
                            ?.let {
                                answerMessageBox(
                                    it.activeButton
                                )
                            }
                    }

                    BackHandler(
                        enabled =
                            !menuOpen &&
                                !keyboardOpen &&
                                !discOpen &&
                                !messageBoxOpen
                    ) {
                        openMenu(pause = true)
                    }

                    BackHandler(
                        enabled = menuOpen && !editorOpen.value && !keyboardOpen && !discOpen && !messageBoxOpen
                    ) {
                        backMenu()
                    }
                    BackHandler(enabled = editorOpen.value && !keyboardOpen && !discOpen && !messageBoxOpen) {
                        editorOpen.value = false
                    }
                }
            }

        setContentView(compose)
        sv.requestFocus()
    }

    private fun configureHaptics(
        globalsFlag: Boolean
    ) {
        val legacy =
            PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(
                    "enable_vibrator",
                    false
                )

        hapticsEnabled =
            globalsFlag || legacy

        if (
            hapticsEnabled &&
            vibrator == null
        ) {
            vibrator =
                getSystemService(
                    VIBRATOR_SERVICE
                ) as Vibrator
        }
    }

    private fun maybeVibrate() {
        if (!hapticsEnabled) return

        vibrator?.vibrate(
            VibrationEffect.createOneShot(
                25,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }

    override fun surfaceCreated(
        holder: SurfaceHolder
    ) {
        if (externalDisplay?.owns(holder) == false) return
        surfaceAvailable = true
        recordEvent("surface", if (started) "recreated" else "created, booting the core")
        if (!started) {
            started = true

            session.attachSurface(
                holder.surface
            )

            try {
                val labels =
                    intent.getStringArrayExtra(
                        EXTRA_DISC_LABELS
                    )

                val paths =
                    intent.getStringArrayExtra(
                        EXTRA_DISC_PATHS
                    )

                if (
                    labels != null &&
                    paths != null &&
                    labels.isNotEmpty()
                ) {
                    session.discSetKnown(
                        labels.toList(),
                        paths.toList()
                    )
                }

                session.bootOnce()
                bootedState.value = true
            } catch (
                t: RuntimeException
            ) {
                Log.e(
                    TAG,
                    "boot failed",
                    t
                )

                val detail = t.message ?: t.javaClass.simpleName
                showLaunchFailure(getString(R.string.host_core_failed, detail), "The emulator core did not start: $detail",
                    xendroid.compose.ui.ingame.LaunchFailure.Kind.CORE)
            }
        } else {
            session.attachSurface(
                holder.surface
            )

            resumeForLifecycle()
        }
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        if (externalDisplay?.owns(holder) == false) return
        if (!started) return
        if (width == 0 || height == 0) return
        recordEvent("surface", "${width}x$height")

        session.changeSurface(
            width,
            height
        )
    }

    override fun surfaceDestroyed(
        holder: SurfaceHolder
    ) {
        if (externalDisplay?.owns(holder) == false) return
        surfaceAvailable = false
        if (!started) return
        recordEvent("surface", "destroyed", flush = true)

        pauseForLifecycle()

        session.detachSurface()
    }

    override fun onPause() {
        super.onPause()
        session.flushGpuCaches()
    }

    override fun onStop() {
        super.onStop()
        foregroundState.value = false
        // The background is where the system kills a game: keep the log current.
        recordEvent("lifecycle", "background", flush = true)

        mainHandler.removeCallbacks(
            pauseOnFocusLost
        )

        pauseForLifecycle()

        brightnessSampler.stop()

        EmuProcessLink.setEmuForeground(
            false
        )
    }

    override fun onStart() {
        super.onStart()
        foregroundState.value = true
        recordEvent("lifecycle", "foreground")

        EmuProcessLink.setEmuForeground(
            true
        )

        if (overlayWantsBrightness) {
            surfaceView?.let {
                brightnessSampler.start(it)
            }
        }

        resumeForLifecycle()
    }

    private fun pauseForLifecycle() {
        gyroCamera.stop()
        performanceHints.close()
        releaseGuestInput()
        if (backgroundPolicy.value != BackgroundPolicy.AUTO && surfaceAvailable) return
        if (!session.booted) return
        releaseGuestInput()
        if (!session.isPaused()) {
            session.pause()
            pausedByLifecycle = true
            recordEvent("pause", "guest paused by the app lifecycle")
        }
        menuPaused.value = session.isPaused()
    }

    private fun resumeForLifecycle() {
        if (pausedByLifecycle && session.booted && surfaceAvailable && hasWindowFocus() &&
            !menuState.value.pausedByMenu &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            session.resumeIfPaused()
            pausedByLifecycle = false
            menuPaused.value = session.isPaused()
            recordEvent("pause", "guest resumed")
        }
        if (gyroEnabled.value && foregroundState.value && !menuState.value.open && hasWindowFocus()) gyroCamera.start()
    }

    override fun onDestroy() {
        gameModeSignal.update(xendroid.compose.core.GamePhase.NONE)
        performanceHints.close()
        gyroCamera.stop()
        stopEventSources()
        externalDisplay?.close()
        // The process dies below: tell the phones now (bounded wait, sockets off this thread).
        phoneControllers.close(timeoutMs = 300)
        super.onDestroy()

        keyboardRequestState.value = null
        session.keyboardCancelAll()

        discRequestState.value = null
        session.discCancelAll()

        messageBoxRequestState.value = null
        session.messageBoxCancelAll()

        // The one place this process finalizes its run; a death before this point is
        // finalized later from the platform's exit reason (SessionRunStore.reconcile).
        runId?.let { id ->
            runCatching {
                val failure = launchFailure
                runPerformance.battery(xendroid.compose.sessions.SessionRuns.batteryCelsius(applicationContext))
                runPerformance.frameTimes(session.guestFrameTimeHistogram())
                session.presentationState().let { fg ->
                    runPerformance.frameGeneration(session.frameGenerationGpuHistogram(), fg.lateSkips, fg.dropped, fg.syntheticSlots)
                }
                runPerformance.compiles(session.shaderCompileStats())
                runPerformance.audio(session.audioRunStats())
                runEvents.record("exit", if (isFinishing) "activity finished" else "activity destroyed by the system")
                runCatching { xendroid.compose.sessions.SessionRuns.store().saveEvents(id, runEvents.snapshot()) }
                    .onFailure { Log.w(TAG, "Saving the run events failed", it) }
                xendroid.compose.sessions.SessionRuns.store().finish(id,
                    if (failure != null) xendroid.compose.sessions.RunState.FAILED else xendroid.compose.sessions.RunState.ENDED,
                    failure ?: if (isFinishing) "activity finished" else "activity destroyed by the system",
                    runPerformance.snapshot())
            }.onFailure { Log.w(TAG, "Finishing the session run record failed", it) }
        }

        Process.killProcess(
            Process.myPid()
        )
    }

    // Public platform input callback; only the inherited AndroidX implementation
    // class is library-restricted. Keep the restriction exception local to this override.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && isControllerEvent(event) &&
            event.keyCode != KeyEvent.KEYCODE_BACK) noteControllerUse(event)
        // U10: a controller types on the grid; the focused text field must not eat its D-pad.
        if (keyboardRequestState.value != null && !editorOpen.value && isControllerEvent(event)) {
            return when (event.action) {
                KeyEvent.ACTION_DOWN -> onKeyDown(event.keyCode, event)
                KeyEvent.ACTION_UP -> onKeyUp(event.keyCode, event)
                else -> true
            }
        }
        if (editorOpen.value && !hasGuestPrompt()) {
            val code = xendroid.compose.gamepad.MenuButtons.frontendKey(event.keyCode, swapConfirm)
                ?: return super.dispatchKeyEvent(event)
            return super.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, event.action, code,
                event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source))
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {
        if (editorOpen.value && !hasGuestPrompt()) return super.onKeyDown(keyCode, event)
        // Let Android/Compose dispatch Back to the highest-priority BackHandler.
        // Consuming it with other menu keys leaves the pause sheet impossible to close.
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)

        val identity = keyIdentity(event)
        val direction = xendroid.compose.gamepad.MenuButtons.direction(keyCode)
        if (event.repeatCount > 0 && consumedMenuKeys.contains(identity)) {
            // U04: a held D-pad moves at a readable pace, not at the key repeat rate.
            if (direction == 0 || !navRepeat.press(direction, event.eventTime)) return true
        } else if (direction != 0 && event.repeatCount == 0) {
            // A fresh press restarts the pace, even if the last release never arrived.
            navRepeat.release()
            navRepeat.press(direction, event.eventTime)
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_MODE && !hasGuestPrompt()) {
            if (event.repeatCount == 0) {
                if (menuState.value.open) backMenu() else openMenu(pause = true)
            }
            consumedMenuKeys.add(identity)
            return true
        }

        if (keyboardRequestState.value != null && keyboardKeyDown(keyCode, event)) {
            consumedMenuKeys.add(identity)
            return true
        }

        val nav = panelNav()

        if (nav != null) {
            if (menuState.value.open && !hasGuestPrompt() &&
                !menuState.value.confirmingQuit && !menuState.value.logPicker &&
                (keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1)
            ) {
                if (event.repeatCount == 0) {
                    menuState.value = menuState.value.changePage(
                        if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1
                    )
                }
                consumedMenuKeys.add(identity)
                return true
            }
            if (
                panelKeyDown(
                    nav,
                    keyCode
                )
            ) {
                consumedMenuKeys.add(identity)
                return true
            }

            if (consumeIfGamepad(event)) {
                consumedMenuKeys.add(identity)
                return true
            }
            if (menuState.value.open && !hasGuestPrompt()) {
                consumedMenuKeys.add(identity)
                return true
            }
        }

        val gameKey =
            keyMap[keyCode]
                ?: return consumeIfGamepad(event) ||
                    super.onKeyDown(
                        keyCode,
                        event
                    )

        // P2-P4 controllers play their own slot (I03); P1 continues below unchanged.
        val player = playerSlot(event.deviceId)
        if (player > 0) {
            if (event.repeatCount == 0) slotInput.keyDown(player, identity, gameKey)
            return true
        }

        if (event.repeatCount == 0) {
            sentGuestKeys[identity] = gameKey
            session.keyEvent(
                gameKey,
                true,
                KEY_VALUE_UNUSED
            )

            return true
        }

        return super.onKeyDown(
            keyCode,
            event
        )
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {
        if (editorOpen.value && !hasGuestPrompt()) return super.onKeyUp(keyCode, event)
        val identity = keyIdentity(event)
        if (xendroid.compose.gamepad.MenuButtons.direction(keyCode) != 0) navRepeat.release()
        if (consumedMenuKeys.remove(identity)) return true
        val player = playerSlot(event.deviceId)
        if (player > 0 && slotInput.keyUp(player, identity)) return true

        sentGuestKeys.remove(identity)?.let { gameKey ->
            session.keyEvent(gameKey, false, KEY_VALUE_UNUSED)
            return true
        }

        if (
            panelNav() != null &&
            (
                isPanelKey(keyCode) ||
                    consumeIfGamepad(event)
            )
        ) {
            return true
        }

        return consumeIfGamepad(event) || super.onKeyUp(keyCode, event)
    }

    private fun keyIdentity(event: KeyEvent): Long =
        (event.deviceId.toLong() shl 32) or (event.keyCode.toLong() and 0xffffffffL)

    private fun consumeIfGamepad(
        event: KeyEvent
    ): Boolean =
        event.keyCode != KeyEvent.KEYCODE_BACK &&
            (
                event.source and
                    InputDevice.SOURCE_GAMEPAD ==
                    InputDevice.SOURCE_GAMEPAD ||
                    event.source and
                    InputDevice.SOURCE_JOYSTICK ==
                    InputDevice.SOURCE_JOYSTICK
            )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyUnbufferedInput()
    }

    /** Controller sticks as they arrive instead of batched to the next frame, up to a frame
     *  less latency (Bannerlator 3b08d65e); Android 11+. Key presses are never batched. */
    private fun applyUnbufferedInput() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.decorView.requestUnbufferedDispatch(if (unbufferedInput.value) InputDevice.SOURCE_CLASS_JOYSTICK else 0)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Each touch gesture unbatched too, for the on-screen controls.
        if (unbufferedInput.value && event.actionMasked == MotionEvent.ACTION_DOWN) {
            window.decorView.requestUnbufferedDispatch(event)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
            xendroid.compose.gamepad.TouchOverlayPresence.pushed(
                event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
                event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ),
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE), event.getAxisValue(MotionEvent.AXIS_GAS),
                event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y))
        ) noteControllerUse(event)
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onGenericMotionEvent(
        event: MotionEvent
    ): Boolean =
        onGenericMotion(event)

    private fun onGenericMotion(
        event: MotionEvent
    ): Boolean {
        if (editorOpen.value && !hasGuestPrompt()) return super.onGenericMotionEvent(event)
        if (keyboardRequestState.value != null &&
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            keyboardHat(event)
            return true
        }
        panelNav()?.let { nav ->
            panelHat(
                nav,
                event
            )

            return true
        }

        val player = playerSlot(event.deviceId)
        if (player > 0 && event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            // Same axes and rules as P1 below; a D-pad that also sends keys has no hat here.
            val hat = isNonDpadSource(event)
            slotInput.motion(player,
                event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
                event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ),
                maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)),
                maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)),
                if (hat) event.getAxisValue(MotionEvent.AXIS_HAT_X) else 0f,
                if (hat) event.getAxisValue(MotionEvent.AXIS_HAT_Y) else 0f)
            return true
        }

        val hatHandled =
            isNonDpadSource(event) &&
                handleHat(event)

        if (
            event.source and
                InputDevice.SOURCE_JOYSTICK !=
                InputDevice.SOURCE_JOYSTICK
        ) {
            return hatHandled ||
                super.onGenericMotionEvent(
                    event
                )
        }

        emitAxisPair(
            event.getAxisValue(
                MotionEvent.AXIS_X
            ),
            negKey = KC_LTHUMB_LEFT,
            posKey = KC_LTHUMB_RIGHT,
            invert = false
        )

        emitAxisPair(
            event.getAxisValue(
                MotionEvent.AXIS_Y
            ),
            negKey = KC_LTHUMB_UP,
            posKey = KC_LTHUMB_DOWN,
            invert = true
        )

        emitAxisPair(
            event.getAxisValue(
                MotionEvent.AXIS_Z
            ),
            negKey = KC_RTHUMB_LEFT,
            posKey = KC_RTHUMB_RIGHT,
            invert = false
        )

        emitAxisPair(
            event.getAxisValue(
                MotionEvent.AXIS_RZ
            ),
            negKey = KC_RTHUMB_UP,
            posKey = KC_RTHUMB_DOWN,
            invert = true
        )

        lTriggerDown =
            emitTrigger(
                maxOf(
                    event.getAxisValue(
                        MotionEvent.AXIS_LTRIGGER
                    ),
                    event.getAxisValue(
                        MotionEvent.AXIS_BRAKE
                    )
                ),
                KC_TRIGGER_L,
                lTriggerDown
            )

        rTriggerDown =
            emitTrigger(
                maxOf(
                    event.getAxisValue(
                        MotionEvent.AXIS_RTRIGGER
                    ),
                    event.getAxisValue(
                        MotionEvent.AXIS_GAS
                    )
                ),
                KC_TRIGGER_R,
                rTriggerDown
            )

        return true
    }

    private fun emitTrigger(
        value: Float,
        gameKey: Int,
        wasDown: Boolean
    ): Boolean {
        val down =
            value > 0.5f

        if (down != wasDown) {
            session.keyEvent(
                gameKey,
                down,
                KEY_VALUE_UNUSED
            )
        }

        return down
    }

    private fun emitAxisPair(
        axis: Float,
        negKey: Int,
        posKey: Int,
        invert: Boolean
    ) {
        val raw =
            if (invert) {
                -axis
            } else {
                axis
            }

        val v =
            if (
                abs(raw) <
                    AXIS_DEADZONE
            ) {
                0f
            } else {
                raw
            }

        when {
            v < 0f -> {
                emitAxis(
                    posKey,
                    false,
                    0
                )

                emitAxis(
                    negKey,
                    true,
                    (v * 32768f).toInt()
                )
            }

            v > 0f -> {
                emitAxis(
                    negKey,
                    false,
                    0
                )

                emitAxis(
                    posKey,
                    true,
                    (v * 32767f).toInt()
                )
            }

            else -> {
                emitAxis(
                    negKey,
                    false,
                    0
                )

                emitAxis(
                    posKey,
                    false,
                    0
                )
            }
        }
    }

    private val axisPressed =
        BooleanArray(24)

    private val axisValue =
        IntArray(24) {
            Int.MIN_VALUE
        }

    private fun emitAxis(
        code: Int,
        pressed: Boolean,
        value: Int
    ) {
        if (
            axisPressed[code] == pressed &&
            axisValue[code] == value
        ) {
            return
        }

        axisPressed[code] = pressed
        axisValue[code] = value

        session.keyEvent(
            code,
            pressed,
            value
        )
    }

    private var hatLeft = false
    private var hatUp = false
    private var hatRight = false
    private var hatDown = false

    private fun handleHat(
        event: MotionEvent
    ): Boolean {
        val hx =
            event.getAxisValue(
                MotionEvent.AXIS_HAT_X
            )

        val hy =
            event.getAxisValue(
                MotionEvent.AXIS_HAT_Y
            )

        val left =
            hx < -0.5f

        val right =
            hx > 0.5f

        val up =
            hy < -0.5f

        val down =
            hy > 0.5f

        if (left != hatLeft) {
            session.keyEvent(
                KC_DPAD_LEFT,
                left,
                KEY_VALUE_UNUSED
            )

            hatLeft = left
        }

        if (right != hatRight) {
            session.keyEvent(
                KC_DPAD_RIGHT,
                right,
                KEY_VALUE_UNUSED
            )

            hatRight = right
        }

        if (up != hatUp) {
            session.keyEvent(
                KC_DPAD_UP,
                up,
                KEY_VALUE_UNUSED
            )

            hatUp = up
        }

        if (down != hatDown) {
            session.keyEvent(
                KC_DPAD_DOWN,
                down,
                KEY_VALUE_UNUSED
            )

            hatDown = down
        }

        return left ||
            right ||
            up ||
            down
    }

    private fun isNonDpadSource(
        event: MotionEvent
    ): Boolean =
        event.source and
            InputDevice.SOURCE_DPAD !=
            InputDevice.SOURCE_DPAD

    private class PanelNav(
        val count: Int,
        val activate: (Int) -> Unit,
        val cancel: () -> Unit,
    )

    private fun hasGuestPrompt(): Boolean =
        discRequestState.value != null || messageBoxRequestState.value != null ||
            keyboardRequestState.value != null

    private fun panelNav(): PanelNav? {
        discRequestState.value?.let { req ->
            val paths =
                req.discPaths ?: emptyArray()

            val discs =
                minOf(
                    req.discLabels?.size ?: 0,
                    paths.size
                )

            return PanelNav(
                discs + 1,

                { i ->
                    if (i < discs) {
                        chooseDisc(
                            paths[i]
                        )
                    } else {
                        cancelDisc()
                    }
                },

                {
                    cancelDisc()
                }
            )
        }

        messageBoxRequestState.value?.let { req ->
            return PanelNav(
                req.buttons?.size ?: 0,

                { i ->
                    answerMessageBox(
                        i
                    )
                },

                {
                    answerMessageBox(
                        req.activeButton
                    )
                }
            )
        }

        keyboardRequestState.value?.let {
            return PanelNav(
                2,

                { i ->
                    if (i == 0) {
                        acceptKeyboard(
                            keyboardGrid.value.text
                        )
                    } else {
                        cancelKeyboard()
                    }
                },

                {
                    cancelKeyboard()
                }
            )
        }

        if (menuState.value.open) {
            return PanelNav(
                menuState.value.count,

                { i ->
                    menuState.value = menuState.value.select(i)
                    if (menuState.value.confirmingQuit) {
                        chooseMenuQuit(i == 1)
                    } else if (menuState.value.logPicker) {
                        chooseLogSession(i)
                    } else {
                        menuState.value.action?.let(::performMenuAction)
                    }
                },

                {
                    backMenu()
                }
            )
        }

        return null
    }

    private fun toggleTouchOverlay() {
        // Put aside by a controller: showing them overrules it until that controller goes,
        // and the setting (on) stays as it is.
        if (overlayHiddenByController.value && showTouchOverlay.value == true) {
            touchPresence.shownByUser()
            overlayHiddenByController.value = false
            recordEvent("touch controls", "shown from the menu while a controller plays P1")
            return
        }
        val next =
            showTouchOverlay.value != true
        if (next) {
            touchPresence.shownByUser()
            overlayHiddenByController.value = false
        }

        showTouchOverlay.value =
            next

        session.setShowTouchOverlay(
            next
        )

        // Live/session-only. The settings screens expose explicit global/per-game
        // persistence, so opening the menu cannot silently overwrite either scope.
    }

    private fun openMenu(pause: Boolean) {
        gyroCamera.stop()
        if (menuState.value.open || hasGuestPrompt()) return
        releaseGuestInput()
        val pausedHere = pause && session.booted && !session.isPaused()
        if (pausedHere) session.pause()
        recordEvent("menu", if (pausedHere) "opened, guest paused" else "opened")
        menuPaused.value = session.isPaused()
        fpsLimitState.intValue = session.fpsLimit()
        presentationState.value = session.presentationState()
        menuState.value = menuState.value.show(pausedHere)
        menuStatus.value = runCatching { buildMenuStatus() }.getOrDefault(emptyList())
        refreshFpsConfig()
        refreshDriverLine()
        lifecycleScope.launch {
            lsfgCache.value = withContext(Dispatchers.IO) { runCatching { LsfgAssets.cache(applicationContext)?.path }.getOrNull() }
        }
    }

    private fun closeMenuAndResume() {
        val resume = menuState.value.pausedByMenu || pausedByLifecycle
        menuState.value = menuState.value.hide()
        recordEvent("menu", "closed")
        panelNavPrev = false
        panelNavNext = false
        if (resume) {
            pausedByLifecycle = true
            resumeForLifecycle()
        }
        if (gyroEnabled.value && hasWindowFocus()) gyroCamera.start()
    }

    private fun backMenu() {
        if (menuState.value.logPicker) menuState.value = menuState.value.closeLogs()
        else if (menuState.value.confirmingQuit) menuState.value = menuState.value.cancelQuit()
        else closeMenuAndResume()
    }

    private fun chooseMenuQuit(quit: Boolean) {
        if (quit && fpsConfig.value.saving) {
            Toast.makeText(this, getString(R.string.host_wait_config_save), Toast.LENGTH_SHORT).show()
        } else if (quit) {
            runId?.let { id -> runCatching { xendroid.compose.sessions.SessionRuns.store().ending(id, "user exit") } }
            recordEvent("exit", "user exit")
            leave()
        } else menuState.value = menuState.value.cancelQuit()
    }

    @Suppress("DEPRECATION")
    private fun currentOutputHz(): Float = externalDisplay?.activeDisplay?.refreshRate ?:
        (if (Build.VERSION.SDK_INT >= 30) display?.refreshRate else windowManager.defaultDisplay.refreshRate) ?: 60f

    private fun frameGenerationNotes(state: PresentationState): List<String> {
        val hz = currentOutputHz()
        return if (state.requested && state.state == 2) {
            xendroid.compose.core.FrameGenerationReadout.running(lastGuestFps, generatedPerSecond,
                if (state.engine == 1) state.multiplier else 2, hz)
        } else {
            // 15d: with a target, LSFG runs at the planned multiplier from the planned cap.
            val target = xendroid.compose.core.FrameGenerationTarget.plan(lsfgTarget.intValue, hz,
                generationCap.playerLimit(session.fpsLimit()))
            listOfNotNull(
                xendroid.compose.core.FrameGenerationReadout.preview("Win-FG", 2, lastGuestFps, hz),
                lsfgCache.value?.let {
                    xendroid.compose.core.FrameGenerationReadout.preview("LSFG Native", target?.multiplier ?: lsfgMultiplier.intValue,
                        target?.let { plan -> minOf(lastGuestFps, plan.cap.toDouble()) } ?: lastGuestFps, hz)
                },
            )
        }
    }

    private fun prepareGenerationCap(hz: Float, multiplier: Int = 2) {
        generationCap.prepare(session.fpsLimit(), hz, multiplier)?.let { capped ->
            session.setFpsLimit(capped)
            fpsLimitState.intValue = session.fpsLimit()
        }
    }

    /** 15d: the game at exactly [cap] FPS while LSFG meets a target. */
    private fun prepareExactCap(cap: Int) {
        generationCap.prepareExact(session.fpsLimit(), cap)?.let { capped ->
            session.setFpsLimit(capped)
            fpsLimitState.intValue = session.fpsLimit()
        }
    }

    /** 15d: "120 FPS → 2× with the game at 60 FPS", or "off: multiplier by hand", in the shown language. */
    @androidx.compose.runtime.Composable
    private fun lsfgTargetText(): String {
        val choice = lsfgTarget.intValue
        if (choice == xendroid.compose.core.FrameGenerationTarget.OFF) return stringResource(R.string.menu_lsfg_target_off)
        val hz = currentOutputHz()
        val name = if (choice == xendroid.compose.core.FrameGenerationTarget.SCREEN)
            stringResource(R.string.menu_lsfg_target_screen, hz.roundToInt())
        else stringResource(R.string.menu_lsfg_target_fps, choice)
        val plan = xendroid.compose.core.FrameGenerationTarget.plan(choice, hz, generationCap.playerLimit(fpsLimitState.intValue))
            ?: return name
        return stringResource(R.string.menu_lsfg_target_plan, name, plan.multiplier, plan.cap) +
            (if (plan.limitedByDisplay) " " + stringResource(R.string.menu_lsfg_target_display, plan.target) else "") +
            (if (plan.belowTarget) " " + stringResource(R.string.menu_lsfg_target_below, plan.output) else "")
    }

    private fun restoreGenerationCap() {
        generationCap.restore(session.fpsLimit())?.let { before ->
            session.setFpsLimit(before)
            fpsLimitState.intValue = session.fpsLimit()
        }
    }

    private fun performMenuAction(action: InGameAction) {
        if (action == InGameAction.MORE_OPTIONS) {
            menuState.value = menuState.value.toggleAdvanced()
            return
        }
        if (!BuildConfig.DEBUG && action in listOf(InGameAction.WINFG, InGameAction.WINFG_PRESET,
                InGameAction.LSFG, InGameAction.LSFG_MULTIPLIER, InGameAction.LSFG_TARGET)) return
        if (action == InGameAction.COLOR_FILTER) {
            session.setColorFilter((session.presentationState().colorFilter + 1) % 5)
            presentationState.value = session.presentationState()
            return
        }
        if (action == InGameAction.PERFORMANCE_HINTS) { performanceHints.requested = !performanceHints.requested; return }
        if (action == InGameAction.EXTERNAL_DISPLAY) { externalDisplay?.cycle(); return }
        if (action == InGameAction.TV_MARGIN) {
            tvMargin.floatValue = xendroid.compose.core.TvMargin.next(tvMargin.floatValue)
            getSharedPreferences(DISPLAY_SETTINGS_PREFS, MODE_PRIVATE).edit().putFloat("tv_margin_percent", tvMargin.floatValue).apply()
            externalDisplay?.setMargin(tvMargin.floatValue)
            return
        }
        if (action == InGameAction.SCALING_EFFECT) {
            scalingEffect.intValue = if (scalingEffect.intValue >= 5) -1 else scalingEffect.intValue + 1
            session.setScalingEffect(scalingEffect.intValue)
            return
        }
        if (action == InGameAction.BACKGROUND_POLICY) {
            backgroundPolicy.value = BackgroundPolicy.entries[(backgroundPolicy.value.ordinal + 1) % BackgroundPolicy.entries.size]
            return
        }
        if (action == InGameAction.GYRO_CAMERA) {
            if (gyroCamera.available) {
                gyroEnabled.value = !gyroEnabled.value
                val on = gyroEnabled.value
                saveControlOptions { it.copy(gyroCamera = on) }
            }
            return
        }
        if (action == InGameAction.GYRO_CALIBRATE) { gyroCamera.calibrate(); return }
        if (action == InGameAction.GYRO_AIM) {
            if (!gyroCamera.available) return
            gyroAim.value = gyroAim.value.next()
            val aim = gyroAim.value
            saveControlOptions { it.copy(gyroAim = aim) }
            return
        }
        if (action == InGameAction.SPLIT_SCREEN) {
            // Saved with the touch controls (as the editor's "Split screen"): off, at a fold, always.
            lifecycleScope.launch {
                runCatching {
                    gamepad.update { cfg ->
                        cfg.copy(globals = cfg.globals.copy(splitScreen = xendroid.compose.gamepad.SplitScreenMode.parse(cfg.globals.splitScreen).next().key))
                    }
                }.onFailure { Log.w(TAG, "Saving the split screen mode failed", it) }
            }
            return
        }
        if (action == InGameAction.UNBUFFERED_INPUT) {
            unbufferedInput.value = !unbufferedInput.value
            val unbuffered = unbufferedInput.value
            saveControlOptions { it.copy(unbufferedInput = unbuffered) }
            applyUnbufferedInput()
            recordEvent("input", "unbuffered " + if (unbufferedInput.value) "on" else "off")
            return
        }
        if (action == InGameAction.PHONE_CONTROLLERS) {
            // The native driver takes player slots only once the emulator runs a title.
            if (phoneControllers.host == null && activeTitleState.value == null) {
                Toast.makeText(this, getString(R.string.host_phone_wait), Toast.LENGTH_SHORT).show()
                return
            }
            phoneControllers.toggle()
            refreshPhoneControllers()
            return
        }
        if (action == InGameAction.CONTROLLER_RUMBLE) {
            rumbleSettings.value = rumbleSettings.value.cycleDefault()
            val rumble = rumbleSettings.value.default
            saveControlOptions { it.copy(rumble = rumble) }
            stopRumble()
            return
        }
        if (action == InGameAction.GYRO_SENSITIVITY) {
            val next = xendroid.compose.gamepad.GyroSensitivity.entries[gyroSensitivity.intValue].next()
            gyroSensitivity.intValue = next.ordinal
            gyroCamera.sensitivity = next.scale
            saveControlOptions { it.copy(gyroSensitivity = next) }
            return
        }
        if (action == InGameAction.SUSTAINED_PERFORMANCE) {
            val enabled = !sustainedMode.value
            sustainedAvailable.value = sustainedPerformance(this, enabled)
            if (sustainedAvailable.value) sustainedMode.value = enabled
            return
        }
        if (action == InGameAction.REFRESH_RATE) {
            @Suppress("DEPRECATION") val activeDisplay = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
            if (activeDisplay != null) {
                val modes = refreshChoices(activeDisplay)
                val index = modes.indexOfFirst { it.hz == requestedRefresh.value }
                requestedRefresh.value = if (index + 1 < modes.size) modes[index + 1].hz else null
                selectRefresh(this, requestedRefresh.value)
            }
            return
        }
        if (action == InGameAction.MUTE || action == InGameAction.VOLUME_UP || action == InGameAction.VOLUME_DOWN) {
            val current = session.audioVolume()
            val volume = when (action) {
                InGameAction.MUTE -> if (current == 0) volumeBeforeMute else { volumeBeforeMute = current; 0 }
                InGameAction.VOLUME_UP -> (current + 10).coerceAtMost(100)
                else -> (current - 10).coerceAtLeast(0)
            }
            session.setAudioVolume(volume); audioVolume.intValue = session.audioVolume()
            return
        }
        if (action == InGameAction.IMPORT_LSFG_DLL) {
            if (!importingLsfg) lsfgPicker.launch(arrayOf("application/octet-stream", "application/x-msdownload", "*/*"))
            return
        }
        if (action == InGameAction.CLEAR_LSFG_CACHE) {
            if (session.presentationState().engine == 1) {
                session.setFrameGeneration(false, fgPreset.intValue, currentOutputHz())
                restoreGenerationCap()
            }
            lifecycleScope.launch {
                val deleted = withContext(Dispatchers.IO) { runCatching { LsfgAssets.clear(applicationContext) } }
                if (deleted.isSuccess) lsfgCache.value = null
                else Toast.makeText(this@EmulatorHostActivity, getString(R.string.host_cache_not_removed), Toast.LENGTH_LONG).show()
                presentationState.value = session.presentationState()
            }
            return
        }
        if (action == InGameAction.LSFG || action == InGameAction.LSFG_MULTIPLIER || action == InGameAction.LSFG_TARGET) {
            when (action) {
                InGameAction.LSFG_MULTIPLIER -> {
                    // A multiplier chosen by hand ends the target (15d).
                    lsfgTarget.intValue = xendroid.compose.core.FrameGenerationTarget.OFF
                    lsfgMultiplier.intValue = if (lsfgMultiplier.intValue == 4) 2 else lsfgMultiplier.intValue + 1
                }
                InGameAction.LSFG_TARGET -> lsfgTarget.intValue = xendroid.compose.core.FrameGenerationTarget.next(lsfgTarget.intValue)
                else -> {}
            }
            val cache = lsfgCache.value ?: return
            val current = session.presentationState()
            val running = current.requested && current.engine == 1
            if (action != InGameAction.LSFG && !running) return
            val enabled = if (action == InGameAction.LSFG) !running else true
            val hz = currentOutputHz()
            // 15d: a target picks the multiplier and caps the game at target ÷ multiplier, planned
            // from the player's own limit (never from a cap frame generation put there).
            val plan = xendroid.compose.core.FrameGenerationTarget.plan(lsfgTarget.intValue, hz,
                generationCap.playerLimit(session.fpsLimit()))
            if (plan != null) lsfgMultiplier.intValue = plan.multiplier
            when {
                !enabled -> restoreGenerationCap()
                plan != null -> prepareExactCap(plan.cap)
                else -> prepareGenerationCap(hz, lsfgMultiplier.intValue)
            }
            session.setLsfg(enabled, cache, hz, lsfgMultiplier.intValue)
            if (enabled) recordEvent("lsfg", plan?.let { "target ${it.target}/s: ${it.multiplier}x from ${it.cap} FPS" }
                ?: "${lsfgMultiplier.intValue}x by hand")
            presentationState.value = session.presentationState()
            return
        }
        val displayMode = when (action) {
            InGameAction.DISPLAY_FIT -> 0
            InGameAction.DISPLAY_FILL -> 1
            InGameAction.DISPLAY_STRETCH -> 2
            InGameAction.DISPLAY_INTEGER -> 3
            else -> null
        }
        if (displayMode != null) {
            session.setPresentationMode(displayMode)
            presentationState.value = session.presentationState()
            return
        }
        if (action == InGameAction.WINFG || action == InGameAction.WINFG_PRESET) {
            if (action == InGameAction.WINFG_PRESET) fgPreset.intValue = (fgPreset.intValue + 1) % 3
            val state = session.presentationState()
            if (action == InGameAction.WINFG_PRESET && state.engine == 1) return
            val enabled = if (action == InGameAction.WINFG) !(state.requested && state.engine == 0) else state.requested
            val hz = currentOutputHz()
            if (enabled) prepareGenerationCap(hz) else restoreGenerationCap()
            session.setFrameGeneration(enabled, fgPreset.intValue, hz)
            presentationState.value = session.presentationState()
            return
        }
        val metric = when (action) {
            InGameAction.HUD_HOST_SUBMISSIONS -> HudMetric.HOST_SUBMISSIONS
            InGameAction.HUD_CPU -> HudMetric.CPU
            InGameAction.HUD_GPU -> HudMetric.GPU
            InGameAction.HUD_RAM -> HudMetric.RAM
            InGameAction.HUD_BATTERY -> HudMetric.BATTERY_TEMPERATURE
            InGameAction.HUD_SOC -> HudMetric.SOC_TEMPERATURE
            InGameAction.HUD_POWER -> HudMetric.POWER
            InGameAction.HUD_GPU_MEMORY -> HudMetric.GPU_MEMORY
            else -> null
        }
        if (metric != null) {
            val enabled = hudMetrics.value.toMutableSet()
            if (!enabled.remove(metric)) enabled.add(metric)
            hudMetrics.value = enabled.toSet()
            getSharedPreferences("fps_overlay", MODE_PRIVATE).edit()
                .putStringSet("hud_metrics", enabled.map { it.name }.toSet()).apply()
            return
        }
        val fps = when (action) {
            InGameAction.FPS_UNLIMITED -> 0
            InGameAction.FPS_30 -> 30
            InGameAction.FPS_45 -> 45
            InGameAction.FPS_60 -> 60
            InGameAction.FPS_90 -> 90
            InGameAction.FPS_120 -> 120
            else -> null
        }
        if (fps != null) {
            // Explicit manual changes supersede a temporary automatic FG cap.
            generationCap.forget()
            fpsLimitState.intValue = fps
            session.setFpsLimit(fps) // session-only; don't overwrite the global/per-game config.
            return
        }
        when (action) {
            InGameAction.EDIT_TOUCH_LAYOUT -> {
                if (session.booted && !session.isPaused()) {
                    session.pause()
                    menuState.value = menuState.value.copy(pausedByMenu = true)
                }
                menuPaused.value = session.isPaused()
                editorOpen.value = true
            }
            InGameAction.SAVE_GAME_FPS,
            InGameAction.INHERIT_GAME_FPS,
            InGameAction.SAVE_GLOBAL_FPS -> persistMenuFps(action)
            InGameAction.STRETCH -> {
                if (fpsConfig.value.loading || fpsConfig.value.saving || fpsConfig.value.error != null) return
                val enabled = !fullscreenStretchEnabled.value
                val before = fpsConfig.value
                fpsConfig.value = before.copy(saving = true)
                lifecycleScope.launch {
                    val saved = withContext(Dispatchers.IO) { persistFullscreenStretchConfig(enabled) }
                    if (saved) {
                        fullscreenStretchEnabled.value = enabled
                        getSharedPreferences(DISPLAY_SETTINGS_PREFS, MODE_PRIVATE).edit()
                            .putBoolean(FULLSCREEN_STRETCH_KEY, enabled).apply()
                    } else {
                        Toast.makeText(this@EmulatorHostActivity, getString(R.string.host_display_not_saved), Toast.LENGTH_LONG).show()
                    }
                    fpsConfig.value = before
                }
            }
            InGameAction.PERFORMANCE_HUD -> {
                val enabled = !performanceOverlayEnabled.value
                performanceOverlayEnabled.value = enabled
                getSharedPreferences("fps_overlay", MODE_PRIVATE).edit()
                    .putBoolean("performance_overlay_enabled", enabled).apply()
            }
            InGameAction.HUD_LOOK -> {
                // 15g: kept with this game's HUD place and size.
                val store = HudPreferences.of(this)
                val title = activeTitleState.value
                val placement = xendroid.compose.core.HudPlacements.read(store, title)
                val next = placement.look.next()
                xendroid.compose.core.HudPlacements.write(store, title, placement.copy(look = next))
                hudLook.value = next
            }
            InGameAction.HUD_STYLE -> {
                val detail = hudDetail.value.next()
                hudDetail.value = detail
                if (detail != xendroid.compose.core.HudDetail.PANEL) panelSnapshot.value = null
                getSharedPreferences("fps_overlay", MODE_PRIVATE).edit()
                    .putString("performance_overlay_detail", detail.key).apply()
            }
            InGameAction.TOUCH_CONTROLS -> toggleTouchOverlay()
            InGameAction.ADAPTIVE_STICKS -> {
                val title = session.activeTitleId() ?: return
                val enabled = !adaptiveSticks.value
                adaptiveSticks.value = enabled
                controlsTitleId = title
                getSharedPreferences("touch_options", MODE_PRIVATE).edit()
                    .putBoolean("adaptive_$title", enabled).apply()
            }
            InGameAction.DRIVER_INFO -> Toast.makeText(this, getString(R.string.menu_driver_note), Toast.LENGTH_LONG).show()
            InGameAction.MARK_SCENE -> {
                sceneMarkers.intValue++
                recordEvent("marker", "scene ${sceneMarkers.intValue}", flush = true)
                Toast.makeText(this, getString(R.string.host_scene_marked, sceneMarkers.intValue), Toast.LENGTH_SHORT).show()
            }
            InGameAction.TOUCH_CAMERA -> {
                touchCamera.value = !touchCamera.value
                val on = touchCamera.value
                saveControlOptions { it.copy(touchCamera = on) }
            }
            InGameAction.RESUME -> closeMenuAndResume()
            InGameAction.SHARE_LOGS -> lifecycleScope.launch {
                menuLogSessions.value = withContext(Dispatchers.IO) { runCatching { SessionLogs.sessions() }.getOrDefault(emptyList()) }
                menuState.value = menuState.value.showLogs(menuLogSessions.value.size)
            }
            InGameAction.QUIT -> menuState.value = menuState.value.askToQuit()
            else -> Unit
        }
    }

    /** U01: what loaded (from the presenter) against the driver setting then and now. */
    private fun refreshDriverLine() {
        val active = session.activeDriverIdentity()
        val title = session.activeTitleId()
        lifecycleScope.launch {
            val now = withContext(Dispatchers.IO) { runCatching { inGameConfig.driverPath(title) }.getOrNull() }
            driverLine.value = xendroid.compose.driver.DriverIdentity.inGame(driverAtBoot.value, now, active) to (active?.label ?: "")
        }
    }

    private fun refreshFpsConfig() {
        if (fpsConfig.value.loading || fpsConfig.value.saving) return
        val title = session.activeTitleId()
        fpsConfig.value = FpsConfigSnapshot(titleId = title, loading = true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { inGameConfig.fpsSnapshot(title) } }
            fpsConfig.value = result.getOrElse {
                Log.w(TAG, "Reading saved FPS configuration failed", it)
                FpsConfigSnapshot(titleId = title, error = getString(R.string.host_config_unreadable))
            }
        }
    }

    private fun persistMenuFps(action: InGameAction) {
        val before = fpsConfig.value
        if (before.loading || before.saving || before.error != null) return
        val title = before.titleId
        if (action != InGameAction.SAVE_GLOBAL_FPS &&
            (title == null || session.activeTitleId() != title)) return
        val currentLimit = session.fpsLimit()
        fpsConfig.value = before.copy(saving = true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val inherited = when (action) {
                        InGameAction.SAVE_GAME_FPS -> { inGameConfig.saveGameFps(title!!, currentLimit); null }
                        InGameAction.SAVE_GLOBAL_FPS -> { inGameConfig.saveGlobalFps(currentLimit); null }
                        InGameAction.INHERIT_GAME_FPS -> inGameConfig.inheritGlobalFps(title!!)
                        else -> null
                    }
                    val snapshot = runCatching { inGameConfig.fpsSnapshot(title) }.getOrElse {
                        before.copy(saving = false, error = getString(R.string.host_config_not_refreshed))
                    }
                    snapshot to inherited
                }
            }
            result.onSuccess { (snapshot, inherited) ->
                fpsConfig.value = snapshot
                if (inherited != null && session.activeTitleId() == title) {
                    session.setFpsLimit(inherited)
                    fpsLimitState.intValue = session.fpsLimit()
                }
                Toast.makeText(this@EmulatorHostActivity, "FPS configuration saved", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Log.w(TAG, "Saving FPS configuration failed; keeping previous file", it)
                fpsConfig.value = before
                Toast.makeText(this@EmulatorHostActivity, getString(R.string.host_config_not_saved), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun chooseLogSession(index: Int) {
        if (index == menuLogSessions.value.size + 1) {
            menuState.value = menuState.value.closeLogs(); return
        }
        val sessionId = if (index == 0) null else menuLogSessions.value.getOrNull(index - 1)?.id ?: return
        shareSessionLogs(sessionId)
    }

    private fun shareSessionLogs(selectedSession: String? = null) {
        if (sharingLogs) return
        sharingLogs = true
        if (session.booted && !session.isPaused()) {
            session.pause()
            menuState.value = menuState.value.copy(pausedByMenu = true)
        }
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    runCatching { SessionLogs.exportRedactedForSharing(applicationContext, selectedSession) }
                        .onFailure { Log.w(TAG, "Sharing diagnostics failed", it) }.getOrNull()
                }
                if (file == null) {
                    Toast.makeText(this@EmulatorHostActivity, getString(R.string.host_no_diagnostics), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                runCatching {
                    val uri = FileProvider.getUriForFile(
                        this@EmulatorHostActivity, "$packageName.share", file,
                    )
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("XenDroid diagnostics", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(send, getString(R.string.host_share_diagnostics)))
                }.onFailure {
                    Log.w(TAG, "Opening diagnostics share sheet failed", it)
                    Toast.makeText(this@EmulatorHostActivity, getString(R.string.host_share_failed), Toast.LENGTH_SHORT).show()
                }
            } finally {
                sharingLogs = false
            }
        }
    }

    /** Neutralize a held controller before routing its next events to the menu. */
    private fun releaseGuestInput() {
        consumedMenuKeys.addAll(sentGuestKeys.keys)
        sentGuestKeys.values.forEach { session.keyEvent(it, false, KEY_VALUE_UNUSED) }
        sentGuestKeys.clear()
        for (code in KC_LTHUMB_LEFT..KC_RTHUMB_DOWN) {
            if (axisPressed[code]) emitAxis(code, false, 0)
        }
        if (lTriggerDown) session.keyEvent(KC_TRIGGER_L, false, KEY_VALUE_UNUSED)
        if (rTriggerDown) session.keyEvent(KC_TRIGGER_R, false, KEY_VALUE_UNUSED)
        lTriggerDown = false
        rTriggerDown = false
        if (hatLeft) session.keyEvent(KC_DPAD_LEFT, false, KEY_VALUE_UNUSED)
        if (hatRight) session.keyEvent(KC_DPAD_RIGHT, false, KEY_VALUE_UNUSED)
        if (hatUp) session.keyEvent(KC_DPAD_UP, false, KEY_VALUE_UNUSED)
        if (hatDown) session.keyEvent(KC_DPAD_DOWN, false, KEY_VALUE_UNUSED)
        hatLeft = false
        hatRight = false
        hatUp = false
        hatDown = false
        panelNavPrev = false
        panelNavNext = false
        slotInput.releaseAll()
        stopRumble()
    }

    private fun isPanelKey(
        keyCode: Int
    ): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_MODE -> true

            else -> false
        }

    /** U04: keys mean what [xendroid.compose.gamepad.MenuButtons] says (A/B may be swapped);
     *  Esc used to activate the selection here (it was listed under confirm first). */
    private fun panelKeyDown(
        nav: PanelNav,
        keyCode: Int
    ): Boolean =
        when (xendroid.compose.gamepad.MenuButtons.intentOf(keyCode, swapConfirm)) {
            xendroid.compose.gamepad.MenuButtons.Intent.PREVIOUS -> {
                movePanelSelection(nav, -1)
                true
            }
            xendroid.compose.gamepad.MenuButtons.Intent.NEXT -> {
                movePanelSelection(nav, 1)
                true
            }
            xendroid.compose.gamepad.MenuButtons.Intent.CONFIRM -> {
                nav.activate(
                    if (menuState.value.open && !hasGuestPrompt()) menuState.value.selected
                    else panelSelectedState.intValue
                )
                true
            }
            xendroid.compose.gamepad.MenuButtons.Intent.CANCEL -> {
                nav.cancel()
                true
            }
            else -> false
        }

    /** U04: the menu button layout (read once per game) and the pace of a held direction. */
    private val swapConfirm by lazy { xendroid.compose.gamepad.MenuButtonPrefs.swapConfirm(this) }
    private val navRepeat = xendroid.compose.gamepad.NavRepeat()

    private var panelNavPrev = false
    private var panelNavNext = false

    private fun panelHat(
        nav: PanelNav,
        event: MotionEvent
    ) {
        val y =
            event.getAxisValue(
                MotionEvent.AXIS_HAT_Y
            ) +
                event.getAxisValue(
                    MotionEvent.AXIS_Y
                )

        val x =
            event.getAxisValue(
                MotionEvent.AXIS_HAT_X
            ) +
                event.getAxisValue(
                    MotionEvent.AXIS_X
                )

        val prev =
            y < -0.5f ||
                x < -0.5f

        val next =
            y > 0.5f ||
                x > 0.5f

        if (prev != panelNavPrev) {
            panelNavPrev = prev
            if (prev && navRepeat.press(-1, SystemClock.uptimeMillis())) movePanelSelection(nav, -1)
        }

        if (next != panelNavNext) {
            panelNavNext = next
            if (next && navRepeat.press(1, SystemClock.uptimeMillis())) movePanelSelection(nav, 1)
        }
        if (!panelNavPrev && !panelNavNext) navRepeat.release()
    }

    private fun movePanelSelection(
        nav: PanelNav,
        delta: Int
    ) {
        if (nav.count <= 0) return

        if (menuState.value.open && !hasGuestPrompt()) {
            menuState.value = menuState.value.move(delta)
            return
        }

        val next =
            panelSelectedState.intValue +
                delta

        panelSelectedState.intValue =
            (
                (next % nav.count) +
                    nav.count
                ) % nav.count
    }

    private fun answerMessageBox(
        button: Int
    ) {
        val req =
            messageBoxRequestState.value
                ?: return

        session.messageBoxSubmit(
            req.id,
            button
        )

        messageBoxRequestState.value =
            null
    }

    private fun chooseDisc(
        path: String
    ) {
        val req =
            discRequestState.value
                ?: return

        session.discSubmit(
            req.id,
            true,
            path
        )

        discRequestState.value =
            null
    }

    private fun cancelDisc() {
        val req =
            discRequestState.value
                ?: return

        session.discSubmit(
            req.id,
            false,
            ""
        )

        discRequestState.value =
            null
    }

    private fun isControllerEvent(event: KeyEvent): Boolean =
        event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    /**
     * U10: the guest keyboard with a controller, Xbox 360 style: D-pad/stick move the highlight,
     * confirm types it, X deletes, Y adds a space, LB/RB move the cursor, L3 shift, R3 symbols,
     * Start answers, Back/Select cancels; cancel (B) deletes, and cancels once the text is empty.
     */
    private fun keyboardKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val grid = keyboardGrid.value
        val step = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> 0 to -1
            KeyEvent.KEYCODE_DPAD_DOWN -> 0 to 1
            KeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1 to 0
            else -> null
        }
        if (step != null) {
            if (event.repeatCount == 0) gridRepeat.release()
            if (gridRepeat.press(keyCode, event.eventTime)) keyboardGrid.value = grid.move(step.first, step.second)
            return true
        }
        if (event.repeatCount > 0) return true
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_X -> keyboardGrid.value = grid.backspace()
            KeyEvent.KEYCODE_BUTTON_Y -> keyboardGrid.value = grid.type(" ")
            KeyEvent.KEYCODE_BUTTON_L1 -> keyboardGrid.value = grid.caretLeft()
            KeyEvent.KEYCODE_BUTTON_R1 -> keyboardGrid.value = grid.caretRight()
            KeyEvent.KEYCODE_BUTTON_THUMBL -> keyboardGrid.value = grid.toggleShift()
            KeyEvent.KEYCODE_BUTTON_THUMBR -> keyboardGrid.value = grid.togglePage()
            KeyEvent.KEYCODE_BUTTON_START -> acceptKeyboard(grid.text)
            KeyEvent.KEYCODE_BUTTON_SELECT -> cancelKeyboard()
            else -> when (xendroid.compose.gamepad.MenuButtons.intentOf(keyCode, swapConfirm)) {
                xendroid.compose.gamepad.MenuButtons.Intent.CONFIRM -> {
                    val (next, outcome) = grid.press()
                    when (outcome) {
                        xendroid.compose.ui.keyboard.KeyboardGrid.Outcome.DONE -> acceptKeyboard(next.text)
                        xendroid.compose.ui.keyboard.KeyboardGrid.Outcome.CANCEL -> cancelKeyboard()
                        xendroid.compose.ui.keyboard.KeyboardGrid.Outcome.NONE -> keyboardGrid.value = next
                    }
                }
                xendroid.compose.gamepad.MenuButtons.Intent.CANCEL ->
                    if (grid.text.isEmpty()) cancelKeyboard() else keyboardGrid.value = grid.backspace()
                else -> return false
            }
        }
        return true
    }

    /** U10: the stick or hat over the keyboard grid, in two dimensions, at the menu pace. */
    private fun keyboardHat(event: MotionEvent) {
        val x = event.getAxisValue(MotionEvent.AXIS_HAT_X) + event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y) + event.getAxisValue(MotionEvent.AXIS_Y)
        val dx = if (x < -0.5f) -1 else if (x > 0.5f) 1 else 0
        val dy = if (y < -0.5f) -1 else if (y > 0.5f) 1 else 0
        val direction = (dy + 1) * 3 + (dx + 1)
        if (direction == gridHatDirection) return
        gridHatDirection = direction
        gridRepeat.release()
        if (direction != 4 && gridRepeat.press(direction, SystemClock.uptimeMillis())) {
            keyboardGrid.value = keyboardGrid.value.move(dx, dy)
        }
    }

    private fun acceptKeyboard(
        text: String
    ) {
        val req =
            keyboardRequestState.value
                ?: return

        // U10: valid UTF-16 only (a lone surrogate would become invalid UTF-8 in the guest).
        session.keyboardSubmit(
            req.id,
            true,
            xendroid.compose.ui.keyboard.KeyboardGrid.sanitize(text)
        )

        keyboardRequestState.value =
            null
    }

    private fun cancelKeyboard() {
        val req =
            keyboardRequestState.value
                ?: return

        session.keyboardSubmit(
            req.id,
            false,
            ""
        )

        keyboardRequestState.value =
            null
    }
}

@Composable
private fun rememberOverlayContrast(
    brightness: StateFlow<Float>
): State<Float> {
    val anim =
        remember {
            Animatable(0f)
        }

    LaunchedEffect(brightness) {
        brightness.collect { b ->
            val target =
                (
                    (b - 0.5f) / 0.4f
                )
                    .coerceIn(
                        0f,
                        1f
                    )
                    .let {
                        (
                            it * 8f
                        )
                            .roundToInt() / 8f
                    }

            if (
                target !=
                    anim.targetValue
            ) {
                launch {
                    anim.animateTo(
                        target,
                        tween(400)
                    )
                }
            }
        }
    }

    return anim.asState()
}
