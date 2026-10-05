package xendroid.compose.saves

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupReplicationTest {
    @get:Rule val folder = TemporaryFolder()
    private class Sink : BackupReplicaSink {
        val objects = mutableMapOf<String, ByteArray>()
        var corrupt = false
        override fun exists(name: String) = name in objects
        override fun read(name: String) = ByteArrayInputStream(objects.getValue(name))
        override fun create(name: String) = object : ByteArrayOutputStream() {
            override fun close() { objects[name] = if (corrupt) byteArrayOf(0) else toByteArray() }
        }
        override fun removeOwnFailedUpload(name: String) { objects.remove(name) }
    }
    @Test fun immutableUploadsDeduplicateAndKeepDifferentVersions() {
        val sink = Sink()
        val archive = folder.newFile().apply { writeText("first backup") }
        val first = BackupReplication.publish("4D5309C9", archive, sink)
        assertEquals(first, BackupReplication.publish("4D5309C9", archive, sink))
        archive.writeText("second backup")
        val second = BackupReplication.publish("4D5309C9", archive, sink)
        assertNotEquals(first, second)
        assertEquals("first backup", sink.objects[first]!!.decodeToString())
    }
    @Test fun verificationFailureRemovesOnlyThisUploadNotOtherBackups() {
        val sink = Sink().apply { objects["unrelated"] = byteArrayOf(3); corrupt = true }
        val archive = folder.newFile().apply { writeText("my backup") }
        assertThrows(IllegalArgumentException::class.java) { BackupReplication.publish("4D5309C9", archive, sink) }
        assertEquals(setOf("unrelated"), sink.objects.keys)
    }
}
