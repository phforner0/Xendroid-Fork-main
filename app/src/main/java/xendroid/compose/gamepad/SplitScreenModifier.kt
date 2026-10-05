package xendroid.compose.gamepad

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset

/** 15c: puts the element exactly on [rect] (layout pixels) of a full-size Box. */
fun Modifier.atRect(rect: PxRect): Modifier = this
    .offset { IntOffset(rect.left, rect.top) }
    .layout { measurable, _ ->
        val placeable = measurable.measure(Constraints.fixed(rect.width.coerceAtLeast(0), rect.height.coerceAtLeast(0)))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
