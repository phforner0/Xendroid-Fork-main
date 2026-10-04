package xendroid.compose.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xendroid.compose.R
import xendroid.compose.shots.FakeCore
import xendroid.compose.shots.Fixture
import xendroid.compose.shots.Phone
import xendroid.compose.shots.ShotApp
import xendroid.compose.shots.app
import xendroid.compose.shots.screen
import xendroid.compose.shots.shot
import xendroid.compose.ui.about.AboutScreen
import xendroid.compose.ui.about.DeviceInfo
import xendroid.compose.ui.design.InputMode

/**
 * Second round of device feedback, "depois": About with a real phone's core report (a Snapdragon
 * with dozens of CPU features and hundreds of Vulkan extensions) summed up, the full report on
 * request.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class)
class Ajustes2Shots {
    @get:Rule val compose = createComposeRule()

    private fun about(mode: InputMode, fullReport: Boolean = false) {
        FakeCore.deviceInfo = REPORT
        DeviceInfo.forgetCoreReport()
        compose.app(mode) { AboutScreen(onBack = {}) }
        Fixture.settle(20)
        compose.waitForIdle()
        if (fullReport) {
            compose.onAllNodesWithText(Fixture.context.getString(R.string.xd_ab_full_report)).onFirst().performClick()
            compose.waitForIdle()
        }
    }

    @Config(qualifiers = Phone.LAND) @Test fun aboutLand() { about(InputMode.TOUCH); compose.shot("ajustes-2/depois-sobre") }
    @Config(qualifiers = Phone.PORT) @Test fun aboutPort() { about(InputMode.TOUCH); compose.shot("ajustes-2/depois-sobre-retrato") }
    @Config(qualifiers = Phone.LAND) @Test fun aboutReport() { about(InputMode.TOUCH, fullReport = true); compose.screen("ajustes-2/depois-sobre-relato-completo") }

    private companion object {
        val FEATURES = listOf("fp", "asimd", "evtstrm", "aes", "pmull", "sha1", "sha2", "crc32", "atomics", "fphp", "asimdhp", "cpuid",
            "asimdrdm", "jscvt", "fcma", "lrcpc", "dcpop", "sha3", "sm3", "sm4", "asimddp", "sha512", "sve", "asimdfhm", "dit", "uscat",
            "ilrcpc", "flagm", "ssbs", "sb", "paca", "pacg", "dcpodp", "sve2", "sveaes", "svepmull", "svebitperm", "svesha3", "svesm4",
            "flagm2", "frint", "svei8mm", "svebf16", "i8mm", "bf16", "dgh", "bti", "ecv", "afp", "wfxt", "mte", "mte3")
        val EXTENSIONS = (1..212).map { i ->
            listOf("VK_KHR_", "VK_EXT_", "VK_QCOM_", "VK_ANDROID_")[i % 4] + listOf("swapchain", "robustness2", "descriptor_indexing",
                "shader_float16_int8", "fragment_density_map", "external_memory", "timeline_semaphore", "custom_border_color")[i % 8] + "_$i"
        }
        val REPORT = "CPU [cortex-x4*1+cortex-a720*3+cortex-a720*2+cortex-a520*2(armv9.2-a)]:\n" +
            FEATURES.joinToString("") { "    * $it\n" } + "\nGPU [Adreno (TM) 825(Vulkan: 1.3.284)]:\n" +
            EXTENSIONS.joinToString("") { "    * $it\n" }
    }
}
