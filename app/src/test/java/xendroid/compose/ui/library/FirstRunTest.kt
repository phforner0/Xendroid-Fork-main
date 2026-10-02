package xendroid.compose.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xendroid.compose.ui.library.FirstRun.Status

class FirstRunTest {
    @Test fun aSupportedPhonePassesAndMissingVulkanOrArm64Blocks() {
        val ok = FirstRun.deviceChecks("Adreno (TM) 750", listOf("arm64-v8a", "armeabi-v7a"), 35)
        assertTrue(ok.all { it.status == Status.OK })
        assertEquals("Adreno (TM) 750", ok.first().detail)

        val noVulkan = FirstRun.deviceChecks(null, listOf("arm64-v8a"), 35)
        assertEquals(Status.BLOCKED, noVulkan.first { it.title == "Vulkan GPU" }.status)
        val x86 = FirstRun.deviceChecks("SwiftShader", listOf("x86_64"), 35)
        assertEquals(Status.BLOCKED, x86.first { it.title == "64-bit ARM" }.status)
        val android10 = FirstRun.deviceChecks("Mali-G78", listOf("arm64-v8a"), 29)
        assertEquals(Status.WARNING, android10.first { it.title == "Android" }.status)
    }

    @Test fun theGameFolderIsAWarningUntilSet() {
        assertEquals(Status.OK, FirstRun.folderCheck(ready = true, supported = true).status)
        assertEquals(Status.WARNING, FirstRun.folderCheck(ready = false, supported = true).status)
        assertTrue(FirstRun.folderCheck(ready = false, supported = false).detail.contains("Android 11"))
    }

    @Test fun thePhonesLocaleMapsOntoTheConsoleLists() {
        val brazil = FirstRun.guestLocale("pt", "BR")
        assertEquals("9", brazil.languageValue)
        assertEquals("pt", brazil.languageLabel)
        assertEquals("BR", brazil.countryLabel)
        assertTrue(brazil.countryValue!!.toInt() in 1..109)
        assertEquals("1", FirstRun.guestLocale("EN", "us").languageValue)
        assertEquals("103", FirstRun.guestLocale("en", "US").countryValue)
        assertEquals("15", FirstRun.guestLocale("no", "NO").languageValue)   // Bokmål
        // No console entry: that half is kept as it is.
        val unknown = FirstRun.guestLocale("eo", "AQ")
        assertNull(unknown.languageValue)
        assertNull(unknown.countryValue)
        assertFalse(unknown.any)
    }
}
