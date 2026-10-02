package xendroid.compose.gamepad

import android.view.KeyEvent
import kotlin.math.abs

/** One motion sample as the game reads it: sticks -1..1 (Y down positive), triggers 0..1, hat -1..1. */
data class PadAxes(
    val lx: Float = 0f, val ly: Float = 0f, val rx: Float = 0f, val ry: Float = 0f,
    val lt: Float = 0f, val rt: Float = 0f, val hatX: Float = 0f, val hatY: Float = 0f,
)

/** What the game receives from [PadAxes], by the rules every slot uses (see [SlotInputRouter]). */
data class GameView(
    val lx: Float, val ly: Float, val rx: Float, val ry: Float,
    val ltPressed: Boolean, val rtPressed: Boolean, val dpad: Set<String>,
)

data class TestedDevice(
    val id: Int,
    val name: String,
    val descriptor: String,
    val vendor: Int,
    val product: Int,
    val sources: List<String>,
    val canVibrate: Boolean,
    val hasGyro: Boolean,
    val connected: Boolean = true,
    val pressed: Set<String> = emptySet(),
    /** Buttons pressed at least once during this test: the checklist. */
    val seen: Set<String> = emptySet(),
    val axes: PadAxes = PadAxes(),
)

/**
 * U05: the state of the controller test screen, fed with plain values (the screen turns
 * Android events into calls), so what it shows, the dead zone included, is tested on the JVM.
 * A controller that comes back (same descriptor, new id) is recognized as a reconnection and
 * keeps its checklist.
 */
class ControllerTestModel(private val deadzone: Float = 0.08f, private val maxLog: Int = 30) {
    private val devices = LinkedHashMap<Int, TestedDevice>()
    private val log = ArrayDeque<String>()

    val all: List<TestedDevice> get() = devices.values.toList()
    val events: List<String> get() = log.toList()

    fun connected(device: TestedDevice) {
        val previous = devices.values.firstOrNull { it.descriptor == device.descriptor && it.id != device.id }
        if (previous != null) {
            devices.remove(previous.id)
            devices[device.id] = device.copy(seen = previous.seen)
            note("${device.name}: reconnected")
        } else {
            val known = devices[device.id]
            devices[device.id] = device.copy(seen = known?.seen.orEmpty())
            if (known == null || !known.connected) note("${device.name}: connected")
        }
    }

    fun disconnected(id: Int) {
        val device = devices[id] ?: return
        devices[id] = device.copy(connected = false, pressed = emptySet(), axes = PadAxes())
        note("${device.name}: disconnected")
    }

    fun key(id: Int, keyCode: Int, down: Boolean) {
        val device = devices[id] ?: return
        val name = buttonName(keyCode)
        devices[id] = if (down) device.copy(pressed = device.pressed + name, seen = device.seen + name)
            else device.copy(pressed = device.pressed - name)
    }

    fun motion(id: Int, axes: PadAxes) {
        val device = devices[id] ?: return
        val view = gameView(axes)
        // A hat D-pad or a pulled trigger counts on the checklist like a button.
        val extra = view.dpad + listOfNotNull("LT".takeIf { view.ltPressed }, "RT".takeIf { view.rtPressed })
        devices[id] = device.copy(axes = axes, seen = device.seen + extra)
    }

    fun gameView(axes: PadAxes): GameView {
        fun stick(v: Float) = if (!v.isFinite() || abs(v) < deadzone) 0f else v.coerceIn(-1f, 1f)
        val dpad = buildSet {
            if (axes.hatX < -0.5f) add(DPAD_LEFT)
            if (axes.hatX > 0.5f) add(DPAD_RIGHT)
            if (axes.hatY < -0.5f) add(DPAD_UP)
            if (axes.hatY > 0.5f) add(DPAD_DOWN)
        }
        return GameView(stick(axes.lx), stick(axes.ly), stick(axes.rx), stick(axes.ry),
            axes.lt > 0.5f, axes.rt > 0.5f, dpad)
    }

    private fun note(text: String) {
        log.addFirst(text)
        while (log.size > maxLog) log.removeLast()
    }

    companion object {
        const val DPAD_UP = "D-pad ↑"
        const val DPAD_DOWN = "D-pad ↓"
        const val DPAD_LEFT = "D-pad ←"
        const val DPAD_RIGHT = "D-pad →"

        /** The buttons an Xbox 360 game uses, for the checklist. */
        val CHECKLIST = listOf("A", "B", "X", "Y", "LB", "RB", "LT", "RT", "L3", "R3", "Start", "Back",
            DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT)

        fun buttonName(keyCode: Int): String = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "A"
            KeyEvent.KEYCODE_BUTTON_B -> "B"
            KeyEvent.KEYCODE_BUTTON_X -> "X"
            KeyEvent.KEYCODE_BUTTON_Y -> "Y"
            KeyEvent.KEYCODE_BUTTON_L1 -> "LB"
            KeyEvent.KEYCODE_BUTTON_R1 -> "RB"
            KeyEvent.KEYCODE_BUTTON_L2 -> "LT"
            KeyEvent.KEYCODE_BUTTON_R2 -> "RT"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
            KeyEvent.KEYCODE_BUTTON_START -> "Start"
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> "Back"
            KeyEvent.KEYCODE_BUTTON_MODE -> "Guide"
            KeyEvent.KEYCODE_DPAD_UP -> DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> DPAD_RIGHT
            else -> "Key $keyCode"
        }
    }
}
