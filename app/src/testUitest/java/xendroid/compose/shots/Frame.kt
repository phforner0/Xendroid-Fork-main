package xendroid.compose.shots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import xendroid.compose.ui.design.InputMode
import xendroid.compose.ui.design.LocalInputMode
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.XdToastState
import xendroid.compose.ui.theme.xendroidTheme

/** Composes [content] as the app does: theme in [mode], toasts, and waits for images to load. */
fun ComposeContentTestRule.app(mode: InputMode = InputMode.TOUCH, content: @Composable () -> Unit) {
    setContent {
        CompositionLocalProvider(LocalInputMode provides mode, LocalXdToast provides XdToastState()) {
            xendroidTheme(mode = mode) { content() }
        }
    }
    waitForIdle()
    Thread.sleep(250)
    waitForIdle()
}
