package xendroid.compose.ui.ingame

import androidx.compose.ui.res.pluralStringResource
import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable

/**
 * What the game is doing before its first frame (U09): the screen is otherwise black
 * while the core starts, the title loads and pipelines are created. Facts only, from
 * the core's own counters; gone with the first guest frame. [GameLoadingScreen] (15e)
 * says it in the shown language (U02).
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

/** 15e: how far along the start is, for the loading screen's bar: one step per stage. */
fun bootProgress(stage: BootStatus.Stage): Float = when (stage) {
    BootStatus.Stage.EMULATOR -> 0.1f
    BootStatus.Stage.GAME -> 0.35f
    BootStatus.Stage.GRAPHICS -> 0.65f
    BootStatus.Stage.FIRST_FRAME -> 0.9f
}

/** 15e: past this, the loading screen says the start is still going (and why it can be slow). */
const val BOOT_STILL_WORKING_SECONDS = 15L

@Composable
internal fun bootStatusText(status: BootStatus): String = when (status.stage) {
    BootStatus.Stage.EMULATOR -> stringResource(R.string.boot_emulator)
    BootStatus.Stage.GAME -> stringResource(R.string.boot_game, status.seconds)
    BootStatus.Stage.GRAPHICS -> pluralStringResource(R.plurals.boot_graphics, status.pipelines.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), status.pipelines, status.seconds)
    BootStatus.Stage.FIRST_FRAME -> stringResource(R.string.boot_first_frame, status.seconds)
}
