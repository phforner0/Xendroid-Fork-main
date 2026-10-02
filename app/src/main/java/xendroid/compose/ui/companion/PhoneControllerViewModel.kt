package xendroid.compose.ui.companion

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xendroid.compose.companion.CompanionClient
import xendroid.compose.companion.CompanionPadLink
import xendroid.compose.gamepad.RumbleIntensity
import xendroid.compose.gamepad.rumbleAmplitude

/**
 * "Use this phone as a controller" (I06): this phone joins a game running XenDroid on
 * another phone of the same network and plays as P2–P4 with its touch pad. Lives in the
 * main process; the connection survives rotation (this ViewModel), not leaving the screen.
 */
class PhoneControllerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("phone_controller", Context.MODE_PRIVATE)

    /** Stable per install, so a phone that reconnects gets its player slot back. */
    private val clientId: ByteArray = prefs.getString("client_id", null)
        ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
        ?.takeIf { it.size == 16 }
        ?: ByteArray(16).also { id ->
            SecureRandom().nextBytes(id)
            prefs.edit { putString("client_id", Base64.encodeToString(id, Base64.NO_WRAP)) }
        }

    val address = mutableStateOf(prefs.getString("address", "").orEmpty())
    val code = mutableStateOf("")
    val name = mutableStateOf(prefs.getString("name", "").orEmpty())
    val intensity = mutableStateOf(RumbleIntensity.parse(prefs.getString("rumble", null)))
    @Volatile private var intensityNow = intensity.value

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
    }.getOrNull()?.takeIf { it.hasVibrator() }

    private val link = CompanionPadLink(
        newClient = { target, code, name, onRumble, onClosed ->
            CompanionClient(target.host, target.port, code, name, clientId, onRumble, onClosed)
        },
        // The client's reader thread: vibration is driven from the main thread.
        onRumble = { motors ->
            lastMotors = motors
            viewModelScope.launch(Dispatchers.Main) { vibrate(rumbleAmplitude(motors, intensityNow)) }
        },
    )
    @Volatile private var lastMotors = 0L
    val state: StateFlow<CompanionPadLink.State> = link.state

    fun connect() {
        prefs.edit { putString("address", address.value.trim()); putString("name", name.value.trim()) }
        val (where, pairing, who) = Triple(address.value, code.value, name.value)
        viewModelScope.launch(Dispatchers.IO) { link.connect(where, pairing, who) }
    }

    fun key(key: Int, pressed: Boolean, value: Int) = link.key(key, pressed, value)

    fun refresh() = link.refresh()

    fun leave() {
        link.leave()
        vibrate(0)
    }

    fun cycleIntensity() {
        intensity.value = intensity.value.next()
        intensityNow = intensity.value
        prefs.edit { putString("rumble", intensity.value.name) }
        if (state.value is CompanionPadLink.State.Playing) vibrate(rumbleAmplitude(lastMotors, intensityNow))
    }

    private var amplitude = 0
    private var vibration: Job? = null

    /** Main thread. The game only sends changes: one-second shots, renewed while it lasts, stop by themselves if this process dies. */
    private fun vibrate(next: Int) {
        if (next == amplitude) return
        amplitude = next
        vibration?.cancel()
        vibration = null
        val motor = vibrator ?: return
        if (next == 0) {
            motor.cancel()
            return
        }
        vibration = viewModelScope.launch {
            while (isActive) {
                motor.vibrate(VibrationEffect.createOneShot(1_000, next))
                delay(900)
            }
        }
    }

    override fun onCleared() {
        link.leave()
        vibration?.cancel()
        vibrator?.cancel()
    }
}
