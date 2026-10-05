package xendroid.compose.settings

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigFileTransactionTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun failedTransformKeepsOriginalBytesAndLeavesNoStagedFile() {
        val file = folder.newFile("title.config.toml").apply { writeText("[GPU]\nframe_limit = [invalid") }
        val before = file.readBytes()
        assertThrows(IllegalArgumentException::class.java) {
            ConfigFileTransaction.update(file) { throw IllegalArgumentException("Cannot parse") }
        }
        assertArrayEquals(before, file.readBytes())
        assertFalse(folder.root.listFiles()!!.any { it.extension == "tmp" })
    }

    @Test fun concurrentReadModifyWritesCannotLoseAnotherWritersUpdate() {
        val file = folder.newFile("counter").apply { writeText("0") }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..4).map {
                pool.submit { repeat(12) { ConfigFileTransaction.update(file) { text -> ((text!!.toInt()) + 1).toString() } } }
            }
            tasks.forEach { it.get(20, TimeUnit.SECONDS) }
            assertEquals("48", file.readText())
        } finally { pool.shutdownNow() }
    }

    @Test fun missingFileCanBeCreatedAndExplicitlyRemovedWithoutTouchingSiblings() {
        val file = File(folder.root, "config/title.toml")
        ConfigFileTransaction.update(file) { assertNull(it); "[GPU]\nframerate_limit = 30\n" }
        val sibling = File(file.parentFile, "other.toml").apply { writeText("unchanged") }
        ConfigFileTransaction.update(file) { null }
        assertFalse(file.exists())
        assertEquals("unchanged", sibling.readText())
    }
}
