package xendroid.compose.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class, qualifiers = Phone.LAND)
class SmokeShots {
    @get:Rule val compose = createComposeRule()

    @Test fun renders() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0F1214))) { Text("XenDroid", color = Color.White) }
        }
        compose.shot("smoke")
    }
}
