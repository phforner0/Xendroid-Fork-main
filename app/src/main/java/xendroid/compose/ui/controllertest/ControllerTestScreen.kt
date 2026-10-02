package xendroid.compose.ui.controllertest

import xendroid.compose.ui.rumbleLabel
import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xendroid.compose.gamepad.ControllerTestModel
import xendroid.compose.gamepad.GamepadCapture
import xendroid.compose.gamepad.PadAxes
import xendroid.compose.gamepad.RumbleIntensity
import xendroid.compose.gamepad.RumbleSettings
import xendroid.compose.gamepad.rumbleAmplitude
import xendroid.compose.gamepad.TestedDevice

/** How long B must be held to leave with a controller only. */
private const val HOLD_TO_LEAVE_MS = 1000L

/**
 * U05: every connected controller, live: buttons (with a checklist of the ones a game uses),
 * sticks with the dead zone and what the game would get, triggers, hat, gyro when the controller
 * has one, and vibration only when asked. Inputs here go to no game and no other screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val model = remember { ControllerTestModel() }
    var devices by remember { mutableStateOf(emptyList<TestedDevice>()) }
    var events by remember { mutableStateOf(emptyList<ControllerTestModel.Event>()) }
    val gyro = remember { mutableStateMapOf<Int, FloatArray>() }
    // U08: each controller's own rumble intensity (the game reads it at the next start). Only its
    // own file is written here: the default lives with the game process's options.
    val rumblePrefs = remember { context.getSharedPreferences(RumbleSettings.DEVICES_PREFS, Context.MODE_PRIVATE) }
    var rumble by remember {
        mutableStateOf(RumbleSettings.decode(
            context.getSharedPreferences(RumbleSettings.PREFS, Context.MODE_PRIVATE).getString(RumbleSettings.DEFAULT_KEY, null),
            rumblePrefs.getString(RumbleSettings.DEVICES_KEY, null)))
    }
    fun publish() { devices = model.all; events = model.events }

    DisposableEffect(Unit) {
        val input = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val handler = Handler(Looper.getMainLooper())
        val gyroListeners = HashMap<Int, Pair<android.hardware.SensorManager, SensorEventListener>>()
        fun add(id: Int) {
            val device = InputDevice.getDevice(id)?.takeIf(::isController) ?: return
            model.connected(describe(context, device))
            if (Build.VERSION.SDK_INT >= 31 && id !in gyroListeners) {
                val sensors = device.sensorManager
                sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensor ->
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) { gyro[id] = event.values.copyOf(3) }
                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                    }
                    if (sensors.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)) {
                        gyroListeners[id] = sensors to listener
                    }
                }
            }
            publish()
        }
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = add(deviceId)
            override fun onInputDeviceChanged(deviceId: Int) = add(deviceId)
            override fun onInputDeviceRemoved(deviceId: Int) {
                gyroListeners.remove(deviceId)?.let { (sensors, l) -> sensors.unregisterListener(l) }
                gyro.remove(deviceId)
                model.disconnected(deviceId)
                publish()
            }
        }
        input.registerInputDeviceListener(listener, handler)
        input.inputDeviceIds.forEach(::add)
        GamepadCapture.listener = { event -> handle(event, model, onBack).also { if (it) publish() } }
        onDispose {
            GamepadCapture.listener = null
            input.unregisterInputDeviceListener(listener)
            gyroListeners.values.forEach { (sensors, l) -> sensors.unregisterListener(l) }
        }
    }
    BackHandler(onBack = onBack)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.lib_menu_test_controllers)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.ct_intro), style = MaterialTheme.typography.bodySmall)
            }
            if (devices.isEmpty()) item { Text(stringResource(R.string.ct_none)) }
            items(devices, key = { it.id }) { device ->
                DeviceCard(device, model, gyro[device.id], rumble,
                    onRumble = {
                        rumble = rumble.cycleDevice(device.descriptor)
                        rumblePrefs.edit().putString(RumbleSettings.DEVICES_KEY, rumble.encodeDevices()).apply()
                    },
                ) { vibrate(device.id, rumble.forDevice(device.descriptor)) }
            }
            if (events.isNotEmpty()) {
                item { Text(stringResource(R.string.ct_connections), style = MaterialTheme.typography.titleSmall) }
                items(events.take(10)) { Text(eventText(it), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun isController(device: InputDevice): Boolean {
    val sources = device.sources
    return !device.isVirtual && (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
}

private fun describe(context: Context, device: InputDevice): TestedDevice {
    val sources = device.sources
    val names = listOfNotNull(
        context.getString(R.string.ct_src_gamepad).takeIf { sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD },
        context.getString(R.string.ct_src_joystick).takeIf { sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK },
        context.getString(R.string.ct_src_dpad).takeIf { sources and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD },
        context.getString(R.string.ct_src_keyboard).takeIf { sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD },
    )
    val vibrates = if (Build.VERSION.SDK_INT >= 31) device.vibratorManager.vibratorIds.isNotEmpty()
        else @Suppress("DEPRECATION") device.vibrator.hasVibrator()
    val gyro = Build.VERSION.SDK_INT >= 31 && device.sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    return TestedDevice(device.id, device.name, device.descriptor, device.vendorId, device.productId, names, vibrates, gyro)
}

/** True when the event belonged to a controller (it is then the test's alone). */
private fun handle(event: InputEvent, model: ControllerTestModel, onBack: () -> Unit): Boolean {
    val controller = event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    if (!controller) return false   // the phone's own Back and touch keep working
    when (event) {
        is KeyEvent -> {
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BUTTON_B &&
                event.eventTime - event.downTime >= HOLD_TO_LEAVE_MS) {
                onBack()
                return true
            }
            if (event.repeatCount == 0) model.key(event.deviceId, event.keyCode, event.action == KeyEvent.ACTION_DOWN)
        }
        is MotionEvent -> if (event.action == MotionEvent.ACTION_MOVE) model.motion(event.deviceId, PadAxes(
            event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y),
            event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ),
            maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)),
            maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)),
            event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y)))
    }
    return true
}

/** A short pulse at the controller's rumble intensity, only because the user asked for it. */
private fun vibrate(id: Int, intensity: RumbleIntensity) {
    val device = InputDevice.getDevice(id) ?: return
    val amplitude = rumbleAmplitude(0xFFFFL, intensity)
    if (amplitude == 0) return
    val effect = VibrationEffect.createOneShot(300, amplitude)
    runCatching {
        if (Build.VERSION.SDK_INT >= 31) device.vibratorManager.defaultVibrator.vibrate(effect)
        else @Suppress("DEPRECATION") device.vibrator.vibrate(effect)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(device: TestedDevice, model: ControllerTestModel, gyro: FloatArray?, rumble: RumbleSettings,
                       onRumble: () -> Unit, onVibrate: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(device.name + if (device.connected) "" else " · " + stringResource(R.string.ct_disconnected), style = MaterialTheme.typography.titleMedium)
            Text("%04X:%04X · %s".format(device.vendor, device.product, device.sources.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ControllerTestModel.CHECKLIST.forEach { button ->
                    val pressed = button in device.pressed
                    AssistChip(
                        onClick = {},
                        label = { Text(if (button in device.seen && !pressed) "$button ✓" else button) },
                        colors = if (pressed) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            else AssistChipDefaults.assistChipColors(),
                    )
                }
            }
            val game = model.gameView(device.axes)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Stick(device.axes.lx, device.axes.ly)
                Stick(device.axes.rx, device.axes.ry)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ct_game_gets, game.lx, game.ly, game.rx, game.ry),
                        style = MaterialTheme.typography.bodySmall)
                    Text("LT %.2f".format(device.axes.lt) + if (game.ltPressed) " " + stringResource(R.string.ct_pressed) else "", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { device.axes.lt.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    Text("RT %.2f".format(device.axes.rt) + if (game.rtPressed) " " + stringResource(R.string.ct_pressed) else "", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { device.axes.rt.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                }
            }
            if (device.hasGyro) {
                Text(gyro?.let { stringResource(R.string.ct_gyro, it[0], it[1], it[2]) } ?: stringResource(R.string.ct_gyro_wait),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (device.canVibrate) {
                val own = rumble.perDevice[device.descriptor]
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onRumble) {
                        Text(stringResource(R.string.ct_game_rumble, own?.let { rumbleLabel(it) } ?: stringResource(R.string.ct_rumble_default)))
                    }
                    if (device.connected) OutlinedButton(onClick = onVibrate, enabled = rumble.forDevice(device.descriptor) != RumbleIntensity.OFF) {
                        Text(stringResource(R.string.ct_vibrate))
                    }
                }
            } else Text(stringResource(R.string.ct_no_motor), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun eventText(event: ControllerTestModel.Event): String = stringResource(when (event.kind) {
    ControllerTestModel.Event.Kind.CONNECTED -> R.string.ct_event_connected
    ControllerTestModel.Event.Kind.RECONNECTED -> R.string.ct_event_reconnected
    ControllerTestModel.Event.Kind.DISCONNECTED -> R.string.ct_event_disconnected
}, event.name)

/** The stick's raw position over its dead zone: inside the inner circle the game gets nothing. */
@Composable
private fun Stick(x: Float, y: Float) {
    val outline = MaterialTheme.colorScheme.outline
    val dot = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(72.dp)) {
        val radius = size.minDimension / 2f
        drawCircle(outline, radius, style = Stroke(2f))
        drawCircle(outline, radius * 0.08f, style = Stroke(2f))
        drawCircle(dot, 6.dp.toPx(), center + Offset(x.coerceIn(-1f, 1f) * radius, y.coerceIn(-1f, 1f) * radius))
    }
}
