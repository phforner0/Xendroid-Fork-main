package xendroid.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import xendroid.compose.R
import xendroid.compose.gamepad.RumbleIntensity

/** U02: a rumble intensity as shown (the enum's own label stays English, for logs). */
@Composable
fun rumbleLabel(intensity: RumbleIntensity): String = stringResource(when (intensity) {
    RumbleIntensity.OFF -> R.string.rumble_off
    RumbleIntensity.LOW -> R.string.rumble_low
    RumbleIntensity.MEDIUM -> R.string.rumble_medium
    RumbleIntensity.HIGH -> R.string.rumble_high
})
