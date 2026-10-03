package xendroid.compose.ui.ingame

import androidx.compose.ui.res.pluralStringResource
import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * What the game is doing before its first frame (U09): the screen is otherwise black
 * while the core starts, the title loads and pipelines are created. Facts only, from
 * the core's own counters; gone with the first guest frame. [BootStatusLabel] says it in
 * the shown language (U02).
 */
data class BootStatus(val stage: Stage, val pipelines: Long = 0, val seconds: Long = 0) {
    enum class Stage { EMULATOR, GAME, GRAPHICS, FIRST_FRAME }
}

fun bootStatus(titleActive: Boolean, pipelinesCreated: Long, creatingNow: Long, elapsedSeconds: Long): BootStatus =
    when {
        !titleActive -> BootStatus(BootStatus.Stage.GAME, seconds = elapsedSeconds)
        pipelinesCreated > 0 || creatingNow > 0 -> BootStatus(BootStatus.Stage.GRAPHICS, pipelinesCreated, elapsedSeconds)
        else -> BootStatus(BootStatus.Stage.FIRST_FRAME, seconds = elapsedSeconds)
    }

@Composable
private fun bootStatusText(status: BootStatus): String = when (status.stage) {
    BootStatus.Stage.EMULATOR -> stringResource(R.string.boot_emulator)
    BootStatus.Stage.GAME -> stringResource(R.string.boot_game, status.seconds)
    BootStatus.Stage.GRAPHICS -> pluralStringResource(R.plurals.boot_graphics, status.pipelines.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), status.pipelines, status.seconds)
    BootStatus.Stage.FIRST_FRAME -> stringResource(R.string.boot_first_frame, status.seconds)
}

/** [onCancel]: leave without waiting (Back opens the menu, which has Exit too); null hides it. */
@Composable
fun BootStatusLabel(status: BootStatus, modifier: Modifier = Modifier, onCancel: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .padding(16.dp)
            .background(Color(0x99000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(bootStatusText(status), color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
        if (onCancel != null) {
            TextButton(onClick = onCancel, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.common_cancel), color = Color.White) }
        }
    }
}
