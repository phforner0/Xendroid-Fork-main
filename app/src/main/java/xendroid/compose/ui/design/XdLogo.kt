package xendroid.compose.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The app's mark, as on top of the rail: a green swirl in a rounded square. */
@Composable
fun XdLogo(size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.sweepGradient(listOf(Color(0xFF9BE37F), Color(0xFF107C10), Color(0xFF3DBB2E), Color(0xFF9BE37F))))
    )
}
