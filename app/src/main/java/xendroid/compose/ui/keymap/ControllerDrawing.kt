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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 15o (Bannerlator `4344ef49`): an Xbox 360 controller drawn on the mapping screen, each of the
 * 16 game buttons where it sits on the real one. Positions are fractions of the drawing (x of
 * its width, y of its height, which is the width / [ASPECT]); the sticks' presses are the sticks.
 * Pure, so the JVM tests check that every button is there and none covers another.
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

    val SPOTS: List<Spot> = listOf(
        Spot(14, 0.22f, 0.07f, 0.12f, 0.10f, round = false),      // LT
        Spot(15, 0.78f, 0.07f, 0.12f, 0.10f, round = false),      // RT
        Spot(10, 0.22f, 0.19f, 0.18f, 0.08f, round = false),      // LB
        Spot(11, 0.78f, 0.19f, 0.18f, 0.08f, round = false),      // RB
        Spot(12, 0.25f, 0.42f, 0.11f, tall(0.11f), round = true), // left stick (press)
        Spot(8, 0.42f, 0.42f, 0.06f, 0.07f, round = true),        // Back
        Spot(9, 0.58f, 0.42f, 0.06f, 0.07f, round = true),        // Start
        Spot(7, 0.75f, 0.31f, 0.065f, tall(0.065f), round = true), // Y
        Spot(6, 0.69f, 0.42f, 0.065f, tall(0.065f), round = true), // X
        Spot(5, 0.81f, 0.42f, 0.065f, tall(0.065f), round = true), // B
        Spot(4, 0.75f, 0.53f, 0.065f, tall(0.065f), round = true), // A
        Spot(1, 0.37f, 0.585f, 0.045f, 0.07f, round = false),     // d-pad up
        Spot(0, 0.33f, 0.66f, 0.05f, 0.07f, round = false),       // d-pad left
        Spot(2, 0.41f, 0.66f, 0.05f, 0.07f, round = false),       // d-pad right
        Spot(3, 0.37f, 0.735f, 0.045f, 0.07f, round = false),     // d-pad down
        Spot(13, 0.63f, 0.66f, 0.11f, tall(0.11f), round = true), // right stick (press)
    )

    /** The button drawn at ([x], [y]) (fractions), or null. */
    fun at(x: Float, y: Float): Int? = SPOTS.firstOrNull { it.contains(x, y) }?.index

    /** What is printed on each button; the d-pad's arms carry no text. */
    fun mark(index: Int): String = when (index) {
        4 -> "A"; 5 -> "B"; 6 -> "X"; 7 -> "Y"
        8 -> "◀"; 9 -> "▶"
        10 -> "LB"; 11 -> "RB"; 12 -> "L3"; 13 -> "R3"; 14 -> "LT"; 15 -> "RT"
        else -> ""
    }

    /** The face buttons' colors, as on the controller. */
    fun color(index: Int): Color? = when (index) {
        4 -> Color(0xFF5DBB46); 5 -> Color(0xFFE5483A); 6 -> Color(0xFF3B82D6); 7 -> Color(0xFFF2C230)
        else -> null
    }
}

/**
 * The drawn controller: tap a button to bind it. [state] tells how each button stands:
 * unbound and sharing a key with another stand out, changed from the usual key is tinted.
 */
@Composable
internal fun ControllerDrawing(
    label: @Composable (Int) -> String,
    binding: (Int) -> String,
    state: (Int) -> ButtonState,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(DrawnController.ASPECT)) {
        val width = maxWidth
        val height = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val body = colors.surfaceVariant
            // The shell and its two grips, then the guide button (not a game button).
            drawRoundRect(body, Offset(size.width * 0.08f, size.height * 0.22f),
                Size(size.width * 0.84f, size.height * 0.52f), CornerRadius(size.width * 0.1f))
            drawOval(body, Offset(size.width * 0.06f, size.height * 0.48f), Size(size.width * 0.3f, size.height * 0.5f))
            drawOval(body, Offset(size.width * 0.64f, size.height * 0.48f), Size(size.width * 0.3f, size.height * 0.5f))
            drawCircle(colors.outlineVariant, size.width * 0.03f, Offset(size.width * 0.5f, size.height * 0.3f))
        }
        DrawnController.SPOTS.forEach { spot ->
            val name = label(spot.index)
            val description = "$name: ${binding(spot.index)}"
            val shape = if (spot.round) CircleShape else RoundedCornerShape(30)
            val look = state(spot.index)
            Box(
                Modifier
                    .offset(width * (spot.x - spot.w / 2f), height * (spot.y - spot.h / 2f))
                    .size(width * spot.w, height * spot.h)
                    .background(when (look) {
                        ButtonState.UNBOUND, ButtonState.SHARED -> colors.errorContainer
                        ButtonState.CHANGED -> colors.primaryContainer
                        ButtonState.USUAL -> colors.surface
                    }, shape)
                    .border(1.5.dp,
                        if (look == ButtonState.SHARED || look == ButtonState.UNBOUND) colors.error else colors.outline, shape)
                    .clickable { onPick(spot.index) }
                    .semantics { contentDescription = description; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                val mark = DrawnController.mark(spot.index)
                if (mark.isNotEmpty()) {
                    Text(mark, color = DrawnController.color(spot.index) ?: colors.onSurface,
                        fontSize = if (mark.length > 1) 11.sp else 14.sp)
                }
            }
        }
    }
}

/** How a drawn button stands against the usual mapping. */
internal enum class ButtonState { USUAL, CHANGED, UNBOUND, SHARED }
