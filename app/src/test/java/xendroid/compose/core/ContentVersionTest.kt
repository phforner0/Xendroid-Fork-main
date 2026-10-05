package xendroid.compose.core

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContentVersionTest {
    @get:Rule val tmp = TemporaryFolder()

    private val packed = (1 shl 28) or (0 shl 24) or (48 shl 8) or 2      // 1.0.48.2

    /** An XEX2 header: two optional headers, the execution info's data at 0x40. */
    private fun xex(version: Int): ByteArray = ByteBuffer.allocate(0x60).apply {
        put("XEX2".toByteArray()); putInt(0); putInt(0x1000); putInt(0); putInt(0x50)
        putInt(2)
        putInt(0x00010100); putInt(0x12345678)                 // another header first
        putInt(0x00040006); putInt(0x40)                       // the execution info
        position(0x40); putInt(0x7A1C3E52); putInt(version); putInt(0); putInt(0x4D5307E6)
    }.array()

    @Test fun formatsLikeTheConsole() {
        assertEquals("1.0.48.2", ContentVersion.format(packed))
        assertEquals("2.15.65535.255", ContentVersion.format((2 shl 28) or (15 shl 24) or (65535 shl 8) or 255))
        assertEquals("a negative word too", "9.0.1.0", ContentVersion.format((9 shl 28) or (1 shl 8)))
    }

    @Test fun readsAPackagesHeader() {
        val file = tmp.newFile("TU_4D5307E6")
        file.writeBytes(ByteBuffer.allocate(0x400).apply {
            put("LIVE".toByteArray()); position(0x358); putInt(packed)
        }.array())
        assertEquals("1.0.48.2", ContentVersion.ofPackage(file))
        val other = tmp.newFile("notes.txt").apply { writeBytes(ByteArray(0x400)) }
        assertNull(ContentVersion.ofPackage(other))
    }

    @Test fun readsAnInstalledUpdatesPatch() {
        val dir = tmp.newFolder("TU_4D5307E6")
        File(dir, "media").mkdirs()
        File(dir, "media/readme.txt").writeText("not this")
        File(dir, "default.xexp").writeBytes(xex(packed))
        assertEquals("1.0.48.2", ContentVersion.ofInstalled(dir))
        assertNull(ContentVersion.ofInstalled(tmp.newFolder("empty")))
    }
}
