package xendroid.compose

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.core.FrontendLaunch
import xendroid.compose.ui.library.ACTION_LAUNCH_GAME
import xendroid.compose.ui.library.EXTRA_GAME_URI
import xendroid.compose.core.SessionLogs
import xendroid.compose.saves.BackupSync
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import xendroid.compose.ui.AppNavHost
import xendroid.compose.ui.theme.xendroidTheme
import xendroid.compose.settings.ConfigStore
import xendroid.compose.settings.seedTouchOverlayDefault
import xendroid.compose.updater.LatestVersionDialog
import xendroid.compose.updater.UpdateDialog
import xendroid.compose.updater.UpdateResult
import xendroid.compose.updater.checkForUpdates
import xendroid.compose.updater.shouldCheckForUpdates
import xendroid.compose.updater.saveLastCheck
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread


class MainActivity : ComponentActivity() {

    companion object {
        // One rotation per main-process lifetime = the app-session boundary.
        private val sessionRotated = AtomicBoolean(false)
    }

    private var updateResult by mutableStateOf<UpdateResult?>(null)
    private var backupSyncJob: Job? = null
    override fun onStart() {
        super.onStart()
        if (backupSyncJob?.isActive != true) backupSyncJob = lifecycleScope.launch {
            BackupSync.publishAutomatic(applicationContext)
        }
    }

    /** Compose's standard click/back keys for controller-driven frontend screens.
     * The emulator Activity keeps its separate guest/menu input routing. */
    // This is the public Activity/Window callback. AndroidX annotates its internal
    // implementation class RestrictTo, but overriding the platform callback is valid.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // U05: the controller test sees controllers raw and alone.
        if (xendroid.compose.gamepad.GamepadCapture.listener?.invoke(event) == true) return true
        // U04: A clicks and B goes back, or the other way round when the user swapped them.
        val code = xendroid.compose.gamepad.MenuButtons.frontendKey(event.keyCode,
            xendroid.compose.gamepad.MenuButtonPrefs.swapConfirm(this)) ?: return super.dispatchKeyEvent(event)
        val translated = KeyEvent(
            event.downTime, event.eventTime, event.action, code, event.repeatCount,
            event.metaState, event.deviceId, event.scanCode, event.flags, event.source,
        )
        return super.dispatchKeyEvent(translated)
    }

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (xendroid.compose.gamepad.GamepadCapture.listener?.invoke(event) == true) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        relaunchIfAsked(intent)
    }

    /** 15e: "Try again" from the game process: the same game, discs and loading picture, in a new
     *  :emu once the old one is gone. Only this app's own game process asks. */
    private fun relaunchIfAsked(request: Intent?) {
        if (request?.action != EmulatorHostActivity.ACTION_RELAUNCH_GAME || referrer?.host != packageName) return
        val game = request.getStringExtra(EXTRA_GAME_URI)?.takeIf { it.isNotBlank() } ?: return
        val launch = Intent(ACTION_LAUNCH_GAME).apply {
            setPackage(packageName)
            EmulatorHostActivity.RELAUNCH_EXTRAS.forEach { key ->
                request.getStringExtra(key)?.let { putExtra(key, it) }
                request.getStringArrayExtra(key)?.let { putExtra(key, it) }
            }
            if (game.startsWith("content://")) {
                clipData = android.content.ClipData.newRawUri("game", android.net.Uri.parse(game))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        lifecycleScope.launch {
            xendroid.compose.core.GameRelaunch.awaitEmuGone(applicationContext)
            runCatching { startActivity(launch) }.onFailure { Log.w("MainActivity", "Starting the game again failed", it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Same intent shape as the in-app launch, which routes through the
        // manifest filter into the host's own task. Finishing here instead
        // collapses that task and the emulator is paused before it draws.
        // Resolved for the :emu process: a real path, or the URI itself (with the read
        // grant) - never this process's /proc/self/fd/<n>, which :emu cannot use.
        // 15e: a "Try again" from the game process carries the game too, but waits for the old
        // :emu to end (relaunchIfAsked below), so it is not a hand-off.
        val relaunch = intent?.action == EmulatorHostActivity.ACTION_RELAUNCH_GAME
        val frontendGame = if (relaunch) null else FrontendLaunch.resolveForHandOff(this, intent)
        if (frontendGame != null) {
            startActivity(
                Intent(ACTION_LAUNCH_GAME).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_GAME_URI, frontendGame)
                    if (frontendGame.startsWith("content://")) {
                        clipData = android.content.ClipData.newRawUri("game", android.net.Uri.parse(frontendGame))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            )
        }

        if (!sessionRotated.getAndSet(true)) {
            val appContext = applicationContext

            thread(name = "SessionLogs") {
                runCatching { SessionLogs.startAppSession(appContext) }
                // Close game runs whose :emu process died without finishing them.
                runCatching {
                    xendroid.compose.sessions.SessionRuns.store()
                        .reconcile(xendroid.compose.sessions.SessionRuns.fates(appContext))
                }

                // Pre-warm so settings doesn't pay the delay-load System.loadLibrary.
                runCatching { EmulatorRuntime.ensureLoaded() }
                // Needs the native config, so it follows ensureLoaded on this same thread.
                runCatching { seedTouchOverlayDefault(appContext, ConfigStore(appContext)) }
            }
        }

        if (savedInstanceState == null) relaunchIfAsked(intent)

        val container = AppContainer(applicationContext)

        enableEdgeToEdge()

       setContent {
            xendroidTheme {

                AppNavHost(container)

                LaunchedEffect(Unit) {
                    if (frontendGame != null) return@LaunchedEffect
                    // A debug build's -debug versionName never matches a release tag, so the
                    // check always reports an update - to an APK whose .debug-suffixed package
                    // could not replace this install anyway.
                    if (BuildConfig.DEBUG) return@LaunchedEffect
                    if (!shouldCheckForUpdates(applicationContext)) {
                        Log.d("Updater", "Skipping update check (less than 5 minutes)")
                        return@LaunchedEffect
                    }

                    try {
                        val result = checkForUpdates(applicationContext)

                        // An automatic check only interrupts the user for a real update;
                        // "you are up to date" belongs to an explicit check.
                        updateResult = result.takeIf { it is UpdateResult.Available }

                        // Export check result only if github replied with a valid response
                        saveLastCheck(applicationContext)
                    } catch (e: Exception) {
                        Log.e(
                            "Updater",
                            "Failed to check updates",
                            e
                        )
                    }
                }

                when (val result = updateResult) {
                    is UpdateResult.Available -> {
                        UpdateDialog(
                            release = result.release,
                            onDismiss = { updateResult = null }
                        )
                    }

                    is UpdateResult.Latest -> {
                        LatestVersionDialog(
                            commitHash = result.commitHash,
                            onDismiss = { updateResult = null }
                        )
                    }

                    is UpdateResult.Cooldown, null -> {}
                }
            }
        }
    }
}
