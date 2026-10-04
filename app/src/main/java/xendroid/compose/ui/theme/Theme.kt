package xendroid.compose.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.design.LocalInputMode
import xendroid.compose.ui.design.XdTheme

/**
 * The app's theme: the redesign's dark tokens (ui/design, docs/ui-redesign/bc) for touch or
 * controller [mode], with the Material theme built from them, so screens not yet redrawn take the
 * same colours and fonts. One dark look on purpose: [darkTheme] is kept for callers and ignored.
 * With a [scale] (15l), what it holds is drawn larger or smaller; [NaturalSize] brings back the
 * system's size inside it.
 */
@Composable
fun xendroidTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    scale: UiScale = UiScale.DEFAULT,
    mode: InputMode = LocalInputMode.current,
    content: @Composable () -> Unit,
) {
    XdTheme(mode) {
        if (scale == UiScale.DEFAULT) content()
        else {
            val natural = LocalNaturalDensity.current ?: LocalDensity.current
            val configuration = LocalConfiguration.current
            val shortSide = minOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()
            val scaled = remember(natural, scale, shortSide) { UiScaling.density(natural, scale, shortSide) }
            CompositionLocalProvider(LocalDensity provides scaled, LocalNaturalDensity provides natural, content = content)
        }
    }
}

/** The system's density where a [xendroidTheme] scale is in effect; null where none is. */
val LocalNaturalDensity = staticCompositionLocalOf<Density?> { null }

/**
 * Draws [content] at the system's size even inside a scaled theme: for what must match the game,
 * like the touch editor, whose controls show the size they have over the game.
 */
@Composable
fun NaturalSize(content: @Composable () -> Unit) {
    val natural = LocalNaturalDensity.current
    if (natural == null) content()
    else CompositionLocalProvider(LocalDensity provides natural, LocalNaturalDensity provides null, content = content)
}
