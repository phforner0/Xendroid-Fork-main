package xendroid.compose.ui.keymap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.focusRing

/**
 * 15o (Bannerlator `4344ef49`): an Xbox 360 controller drawn on the mapping screen and in the
 * controller test, each of the 16 game buttons where it sits on the real one. Positions are
 * fractions of the drawing (x of its width, y of its height, which is the width / [ASPECT]);
 * the sticks' presses are the sticks. Pure, so the JVM tests check that every button is there
 * and none covers another.
 */
object DrawnController {
    const val ASPECT = 1.6f

    /** A button's center ([x], [y]) and size ([w] of the width, [h] of the height). */
    data class Spot(val index: Int, val x: Float, val y: Float, val w: Float, val h: Float, val round: Boolean) {
        fun contains(px: Float, py: Float): Boolean {
            val dx = (px - x) / (w / 2f)
            val dy = (py - y) / (h / 2f)
            return if (round) dx * dx + dy * dy <= 1f else kotlin.math.abs(dx) <= 1f && kotlin.math.abs(dy) <= 1f
        }
    }

    /** A circle of [width] (of the drawing's width) is this tall (of its height). */
    private fun tall(width: Float) = width * ASPECT
    /** The drawing is laid out on a 640 × 400 grid, the shape of the controller below. */
    private fun x(v: Float) = v / 640f
    private fun y(v: Float) = v / 400f

    val SPOTS: List<Spot> = listOf(
        Spot(14, x(190f), y(26f), 0.10f, 0.075f, round = false),      // LT
        Spot(15, x(450f), y(26f), 0.10f, 0.075f, round = false),      // RT
        Spot(10, x(192f), y(62f), 0.13f, 0.06f, round = false),       // LB
        Spot(11, x(448f), y(62f), 0.13f, 0.06f, round = false),       // RB
        Spot(12, x(172f), y(156f), 0.10f, tall(0.10f), round = true), // left stick (press)
        Spot(8, x(282f), y(156f), 0.05f, tall(0.05f), round = true),  // Back
        Spot(9, x(358f), y(156f), 0.05f, tall(0.05f), round = true),  // Start
        Spot(7, x(478f), y(116f), 0.06f, tall(0.06f), round = true),  // Y
        Spot(6, x(440f), y(154f), 0.06f, tall(0.06f), round = true),  // X
        Spot(5, x(516f), y(154f), 0.06f, tall(0.06f), round = true),  // B
        Spot(4, x(478f), y(192f), 0.06f, tall(0.06f), round = true),  // A
        Spot(1, x(250f), y(204f), 0.044f, 0.07f, round = false),      // d-pad up
        Spot(0, x(221f), y(233f), 0.044f, 0.07f, round = false),      // d-pad left
        Spot(2, x(279f), y(233f), 0.044f, 0.07f, round = false),      // d-pad right
        Spot(3, x(250f), y(262f), 0.044f, 0.07f, round = false),      // d-pad down
        Spot(13, x(400f), y(234f), 0.10f, tall(0.10f), round = true), // right stick (press)
    )

    /** The button drawn at ([x], [y]) (fractions), or null. */
    fun at(x: Float, y: Float): Int? = SPOTS.firstOrNull { it.contains(x, y) }?.index

    /** What is printed on each button; the d-pad's arms carry an [arrow] instead. */
    fun mark(index: Int): String = when (index) {
        4 -> "A"; 5 -> "B"; 6 -> "X"; 7 -> "Y"
        8 -> "◀"; 9 -> "▶"
        10 -> "LB"; 11 -> "RB"; 12 -> "L3"; 13 -> "R3"; 14 -> "LT"; 15 -> "RT"
        else -> ""
    }

    /** The arrow on a d-pad arm (drawn, not part of [mark]). */
    fun arrow(index: Int): String = when (index) { 0 -> "←"; 1 -> "↑"; 2 -> "→"; 3 -> "↓"; else -> "" }

    /** The face buttons' colors, as on the controller. */
    fun color(index: Int): Color? = when (index) {
        4 -> Color(0xFF5DBB46); 5 -> Color(0xFFE5483A); 6 -> Color(0xFF3B82D6); 7 -> Color(0xFFF2C230)
        else -> null
    }

    /** The controller's outline on the 640 × 400 grid. */
    internal const val BODY = "M205 86C255 74 385 74 435 86C515 96 560 124 588 186C616 254 632 326 600 358C572 386 528 366 494 326" +
        "C470 298 440 284 400 284L240 284C200 284 170 298 146 326C112 366 68 386 40 358C8 326 24 254 52 186C80 124 125 96 205 86Z"
}

/** How a drawn button stands: against the usual mapping, or (in the test) pressed now / once. */
internal enum class ButtonState { USUAL, CHANGED, UNBOUND, SHARED, PRESSED, SEEN }

/**
 * The drawn controller. [state] colours each button; [caption] writes a key under it; [onPick]
 * makes the buttons tappable (the mapping), null leaves them as a picture (the test), where
 * [stick] moves the sticks' knobs (-1..1) and [trigger] fills LT and RT (0..1).
 */
@Composable
internal fun ControllerDrawing(
    label: @Composable (Int) -> String,
    binding: (Int) -> String,
    state: (Int) -> ButtonState,
    onPick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    caption: (Int) -> String? = { null },
    onFocus: ((Int) -> Unit)? = null,
    stick: ((Int) -> Offset)? = null,
    trigger: ((Int) -> Float)? = null,
) {
    val c = Xd.colors
    val body = remember { PathParser().parsePathString(DrawnController.BODY).toPath() }
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(DrawnController.ASPECT)) {
        val width = maxWidth
        val height = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            scale(size.width / 640f, size.height / 400f, pivot = Offset.Zero) {
                drawPath(body, Color(0xFF232A27))
                drawPath(body, Color.White.copy(alpha = 0.07f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                drawCircle(Color(0xFF3A433F), 17f, Offset(320f, 116f))
                drawCircle(Color(0xFF6FD45B).copy(alpha = 0.55f), 17f, Offset(320f, 116f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
            }
        }
        DrawnController.SPOTS.forEach { spot ->
            val name = label(spot.index)
            val description = "$name: ${binding(spot.index)}"
            val shape = if (spot.round) CircleShape else RoundedCornerShape(30)
            val look = state(spot.index)
            val face = DrawnController.color(spot.index)
            val (bg, border, ink) = when (look) {
                ButtonState.USUAL -> Triple(Color(0xFF323B37), Color.White.copy(alpha = 0.12f), face ?: c.fg)
                ButtonState.CHANGED -> Triple(c.acc.copy(alpha = 0.24f).compositeOver(Color(0xFF323B37)), c.acc, face ?: c.fg)
                ButtonState.SHARED -> Triple(Color(0xFFE9A23B).copy(alpha = 0.28f).compositeOver(Color(0xFF323B37)), Color(0xFFE9A23B), face ?: c.fg)
                ButtonState.UNBOUND -> Triple(Color(0xFF1B211F), Color.White.copy(alpha = 0.28f), c.fg3)
                ButtonState.PRESSED -> Triple(c.acc, c.acc, c.onAcc)
                ButtonState.SEEN -> Triple(Color(0xFF323B37), c.acc.copy(alpha = 0.7f), face ?: c.fg)
            }
            val knob = if (spot.index == 12 || spot.index == 13) stick?.invoke(spot.index) else null
            val fill = if (spot.index == 14 || spot.index == 15) trigger?.invoke(spot.index)?.coerceIn(0f, 1f) else null
            Box(
                Modifier
                    .offset(width * (spot.x - spot.w / 2f), height * (spot.y - spot.h / 2f))
                    .size(width * spot.w, height * spot.h)
                    .then(if (onPick != null) Modifier.focusRing(shape) else Modifier)
                    .clip(shape)
                    .background(bg)
                    .then(if (fill != null && fill > 0f) Modifier.drawBehind {
                        drawRect(c.acc.copy(alpha = 0.55f), topLeft = Offset(0f, size.height * (1f - fill)), size = Size(size.width, size.height * fill))
                    } else Modifier)
                    .border(if (look == ButtonState.USUAL || look == ButtonState.SEEN) 1.dp else 1.5.dp, border, shape)
                    .then(if (onPick != null) Modifier.clickable(role = Role.Button) { onPick(spot.index) }
                        .onFocusChanged { if (it.isFocused) onFocus?.invoke(spot.index) } else Modifier)
                    .semantics { contentDescription = description; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                if (knob != null) {
                    val r = width * spot.w * 0.18f
                    Box(Modifier.offset(width * spot.w * 0.3f * knob.x.coerceIn(-1f, 1f), height * spot.h * 0.3f * knob.y.coerceIn(-1f, 1f))
                        .size(r * 2).clip(CircleShape).background(if (look == ButtonState.PRESSED) c.onAcc else c.acc))
                }
                val mark = DrawnController.mark(spot.index).ifEmpty { DrawnController.arrow(spot.index) }
                if (mark.isNotEmpty() && knob == null) {
                    Text(mark, color = ink, fontSize = if (mark.length > 2) 9.sp else if (mark.length > 1) 11.sp else 14.sp,
                        style = XdText.labelSm, maxLines = 1)
                }
            }
            val under = if (spot.index in 0..3) null else caption(spot.index)
            if (under != null) {
                // The triggers' key goes beside them (the bumpers are right below); the others' under them.
                val (x, align) = when (spot.index) {
                    14 -> (spot.x - spot.w / 2f - 0.165f) to TextAlign.End
                    15 -> (spot.x + spot.w / 2f + 0.005f) to TextAlign.Start
                    else -> (spot.x - 0.08f) to TextAlign.Center
                }
                val y = if (spot.index == 14 || spot.index == 15) height * (spot.y - 0.032f) else height * (spot.y + spot.h / 2f) + 2.dp
                Text(under, style = XdText.mono.copy(fontSize = 9.5.sp), color = when (look) {
                    ButtonState.SHARED -> Color(0xFFE9A23B)
                    ButtonState.UNBOUND -> c.fg3
                    else -> c.acc
                }, textAlign = align, maxLines = 1,
                    modifier = Modifier.offset(width * x, y).width(width * 0.16f))
            }
        }
    }
}
