package xendroid.compose.settings

import org.junit.Assert.*
import org.junit.Test

class SettingContractTest {
    @Test fun everySchemaOptionHasAnHonestApplyContract() {
        SettingsSchema.allSettings.forEach {
            val c = settingContract(it, "globally", true)
            assertTrue(c.available)
            assertEquals(if (it.name == "dump_session_logs") SettingApply.IMMEDIATE_ACTION else SettingApply.NEXT_LAUNCH, c.apply)
            assertTrue(c.label.isNotEmpty())
        }
    }
    @Test fun nativeLiveSettersDoNotChangeDiskEditorApplyTiming() {
        val s = SettingsSchema.allSettings.single { it.key == "GPU|framerate_limit" }
        val contract = settingContract(s, "for this game", true)
        assertEquals(LiveSetter.FPS_LIMIT, contract.liveSetter)
        assertEquals(SettingApply.NEXT_LAUNCH, contract.apply)
        val driver = SettingsSchema.allSettings.single { it.name == "vulkan_lib_path" }
        assertFalse(settingContract(driver, "globally", false).available)
    }
}
