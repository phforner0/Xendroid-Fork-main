package xendroid.compose.driver

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The installed driver packages as the driver choices list them. */
class InstalledDriversTest {
    private fun root() = Files.createTempDirectory("driver").toFile()

    @Test fun packages_from_the_installer_and_the_older_importer() {
        val root = root()
        val hashed = File(root, "a".repeat(64)).apply { mkdirs() }
        File(hashed, "libvulkan_freedreno.so").writeText("elf")
        File(hashed, "meta.json").writeText("""{"name":"Turnip","driverVersion":"v25.3.0","libraryName":"libvulkan_freedreno.so"}""")
        File(hashed, "xendroid-installation.json").writeText("""{"version":1,"archiveSha256":"x","library":"libvulkan_freedreno.so","files":[]}""")
        val older = File(root, "turnip-24.1").apply { mkdirs() }
        File(older, "vulkan.ad07XX.so").writeText("elf")
        File(root, ".install-123").mkdirs()
        File(root, "empty").mkdirs()

        val list = InstalledDrivers.list(root)
        assertEquals(2, list.size)
        val turnip = list.first { it.verifiedLayout }
        assertEquals("Turnip", turnip.name)
        assertEquals("v25.3.0", turnip.version)
        assertEquals(File(hashed, "libvulkan_freedreno.so"), turnip.library)
        val old = list.first { !it.verifiedLayout }
        assertEquals("turnip-24.1", old.name)
        assertNull(old.version)
    }

    @Test fun a_hashed_folder_without_metadata_is_named_by_its_library() {
        val root = root()
        val dir = File(root, "b".repeat(64)).apply { mkdirs() }
        File(dir, "libvulkan_custom.so").writeText("elf")
        assertEquals("libvulkan_custom", InstalledDrivers.read(dir)?.name)
    }

    @Test fun a_library_outside_the_package_is_refused() {
        val root = root()
        val dir = File(root, "evil").apply { mkdirs() }
        File(dir, "meta.json").writeText("""{"libraryName":"../../etc/x.so"}""")
        assertNull(InstalledDrivers.read(dir))
    }

    @Test fun names_for_the_rows() {
        val root = root()
        val dir = File(root, "c".repeat(64)).apply { mkdirs() }
        val lib = File(dir, "lib.so").apply { writeText("elf") }
        File(dir, "meta.json").writeText("""{"name":"Mesa Turnip","driverVersion":"26.0"}""")
        assertEquals("Mesa Turnip 26.0", InstalledDrivers.nameOf(lib.absolutePath, root))
        assertNull(InstalledDrivers.nameOf("", root))
        assertFalse(InstalledDrivers.nameOf("/somewhere/else/x.so", root).isNullOrEmpty())
        assertTrue(InstalledDrivers.nameOf("/somewhere/else/x.so", root) == "else")
    }
}
