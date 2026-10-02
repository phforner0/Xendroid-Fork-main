package xendroid.compose.ui.companion

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import xendroid.compose.companion.CompanionPadLink.State
import xendroid.compose.gamepad.GamepadConfigDto
import xendroid.compose.gamepad.GamepadController
import xendroid.compose.gamepad.GamepadOverlay
import xendroid.compose.gamepad.Kc

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** "Use this phone as a controller": a form to join, then the touch pad, full screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneControllerScreen(vm: PhoneControllerViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val playing = state as? State.Playing
    if (playing != null) {
        PhonePad(vm, playing)
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Use this phone as a controller") },
                navigationIcon = {
                    IconButton(onClick = { vm.leave(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val connecting = state is State.Connecting
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "On the phone running the game: open its menu (Back), Controls → Phone controllers. " +
                    "Type the address and the code it shows. Both phones must be on the same Wi-Fi or hotspot. " +
                    "This phone plays as P2, P3 or P4 with its touch pad. Experimental.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = vm.address.value, onValueChange = { vm.address.value = it.take(21) },
                label = { Text("Game address (IP:port)") }, singleLine = true, enabled = !connecting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.code.value, onValueChange = { text -> vm.code.value = text.filter(Char::isDigit).take(6) },
                label = { Text("Code (6 digits)") }, singleLine = true, enabled = !connecting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.name.value, onValueChange = { vm.name.value = it.take(32) },
                label = { Text("Name shown on the game (optional)") }, singleLine = true, enabled = !connecting,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = vm::cycleIntensity) {
                Text("Vibration on this phone: ${vm.intensity.value.label}")
            }
            (state as? State.Idle)?.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (connecting) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Connecting…")
                    OutlinedButton(onClick = vm::leave) { Text("Cancel") }
                }
            } else {
                Button(onClick = vm::connect, modifier = Modifier.fillMaxWidth()) { Text("Connect") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "The game only listens on its local network, never the internet. Ten wrong codes lock pairing " +
                    "until phone controllers are turned off and on there (a new code).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Full-screen landscape pad; the game's buttons stay released while this phone is away. */
@Composable
private fun PhonePad(vm: PhoneControllerViewModel, playing: State.Playing) {
    val context = LocalContext.current
    val view = LocalView.current
    val controller = remember { GamepadController(context.applicationContext) }
    val config by controller.config.collectAsState(initial = GamepadConfigDto())
    val controls = remember(config) { controller.controlsFor(config, landscape = true) }

    BackHandler { vm.leave() }
    LaunchedEffect(Unit) {
        while (true) {
            vm.refresh()
            delay(1_000)
        }
    }
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val insets = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = previousOrientation
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        GamepadOverlay(
            controls = controls,
            // Nothing behind the pad here: keep it clearly visible.
            opacity = config.globals.opacity.coerceAtLeast(0.6f),
            onKeyEvent = { key, pressed, value ->
                if (pressed && value == Kc.VALUE_UNUSED && config.globals.hapticsEnabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                vm.key(key, pressed, value)
            },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "P${playing.slot + 1}" + (playing.latencyMs?.let { " · $it ms" } ?: "") + " · Leave",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                .background(Color.White.copy(alpha = 0.12f), MaterialTheme.shapes.small)
                .clickable(onClick = vm::leave)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
