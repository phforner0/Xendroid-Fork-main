package xendroid.compose.core

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SanitizedSessionExportTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun redactsCurrentAndArchivedLogsWithoutEmbeddingOriginalZipOrBinaryTrace() {
        val history = File(folder.root, "session_20260929-100000.zip")
        ZipOutputStream(history.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("xe.log"))
            zip.write("Gamertag: Alice Spark\nxuid: 0123456789abcdef\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("exit-trace-0.tombstone.pb"))
            zip.write("BINARY_PRIVATE_DATA".toByteArray())
            zip.closeEntry()
        }
        val live = folder.newFile("xe.log").apply {
            writeText("ip=192.168.1.5 email=a@example.org access_token=secret1234\n" +
                "{\"access_token\":\"jsonSecret123\"} Bearer bearerSecret99\n" +
                "file /storage/emulated/0/ROMs/MyGame.iso\n" + "x".repeat(9000) + "\n")
        }
        val result = SanitizedSessionExport.create(
            File(folder.root, "share.zip"), listOf(history), listOf(live to "xe.log"),
        )!!

        val entries = mutableMapOf<String, String>()
        ZipInputStream(result.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(setOf("sessions/session_20260929-100000/xe.log", "current/xe.log"), entries.keys)
        val text = entries.values.joinToString("\n")
        for (privateValue in listOf("Alice Spark", "0123456789abcdef", "192.168.1.5",
            "a@example.org", "secret1234", "jsonSecret123", "bearerSecret99",
            "MyGame.iso", "BINARY_PRIVATE_DATA")) {
            assertFalse("Leaked $privateValue", text.contains(privateValue))
        }
        assertTrue(text.contains("[identity]"))
        assertTrue(text.contains("[credential]"))
        assertTrue(text.contains("[storage-path]"))
        assertTrue(text.contains("[long line omitted]"))
        assertTrue(history.isFile && live.readText().contains("secret1234"))
    }

    @Test fun xeniaProfileLogFormatsDoNotLeakXuidOrGamertag() {
        val lines = listOf(
            "i> F8000004 Loaded Alice Spark (GUID: E000000012345678) to slot 0",
            "w> F8000004 User Bob Stone (XUID: E000000087654321) doesn't have profile GPD!",
            "i> F8000004 LoadAccount: Loading Account: E000000011112222",
            "i> F8000004 FindProfiles: Adding profile E000000055556666 to profile list",
            "logged_profile_slot_0_xuid = \"E000000033334444\"",
            "xuid=0xE000000077778888",
        )
        val text = lines.joinToString("\n") { LogRedactor.redact(it) }
        for (secret in listOf("Alice Spark", "Bob Stone", "E000000012345678", "E000000087654321",
            "E000000011112222", "E000000055556666", "E000000033334444", "E000000077778888")) {
            assertFalse("Leaked $secret in:\n$text", text.contains(secret))
        }
        assertTrue(text.contains("logged_profile_slot_0_xuid = [identity]"))
        // Diagnostic hashes without an identity keyword stay readable.
        assertEquals("Translated VS 0123456789ABCDEF in 3 ms",
            LogRedactor.redact("Translated VS 0123456789ABCDEF in 3 ms"))
    }

    @Test fun allowedEntriesAfterABinaryTraceAreStillExported() {
        val history = File(folder.root, "session_20260930-080000.zip")
        ZipOutputStream(history.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("exit-trace-0.tombstone.pb")); zip.write(ByteArray(64) { 7 }); zip.closeEntry()
            zip.putNextEntry(ZipEntry(SessionLogs.leftoverEntryName(0, "session_20260929-221500-xe.log")))
            zip.write("leftover log line\n".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("exit-info.txt")); zip.write("reason=CRASH_NATIVE\n".toByteArray()); zip.closeEntry()
        }
        val result = SanitizedSessionExport.create(File(folder.root, "after-binary.zip"), listOf(history), emptyList())!!
        val names = mutableListOf<String>()
        ZipInputStream(result.inputStream()).use { zip -> while (true) names.add((zip.nextEntry ?: break).name) }
        assertEquals(listOf(
            "sessions/session_20260930-080000/previous-0-20260929-221500-xe.log",
            "sessions/session_20260930-080000/exit-info.txt"), names)
    }

    @Test fun leftoverEntryNamesAreUniqueAcrossFailedShelves() {
        val names = listOf("session_20260929-221500-xe.log", "session_20260929-221500-logcat.txt",
            "session_20260930-090000-xe.log", "session_odd-xe.log").mapIndexed(SessionLogs::leftoverEntryName)
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.none { it == "xe.log" || it == "logcat.txt" })
    }

    @Test fun emptyInputDoesNotCreateShareArchive() {
        val dest = File(folder.root, "empty.zip")
        assertEquals(null, SanitizedSessionExport.create(dest, emptyList(), emptyList()))
        assertFalse(dest.exists())
    }
}
