package xendroid.compose.ui.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * What the game is doing before its first frame (U09): the screen is otherwise black
 * while the core starts, the title loads and pipelines are created. Facts only, from
 * the core's own counters; gone with the first guest frame.
 */
fun bootStatusText(titleActive: Boolean, pipelinesCreated: Long, creatingNow: Long, elapsedSeconds: Long): String =
    when {
        !titleActive -> "Starting the game… $elapsedSeconds s"
        pipelinesCreated > 0 || creatingNow > 0 -> "Preparing graphics: $pipelinesCreated pipelines created… $elapsedSeconds s"
        else -> "Waiting for the first frame… $elapsedSeconds s"
    }

@Composable
fun BootStatusLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .padding(16.dp)
            .background(Color(0x99000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
