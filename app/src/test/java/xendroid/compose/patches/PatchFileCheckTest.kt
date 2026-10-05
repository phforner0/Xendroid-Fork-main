package xendroid.compose.patches

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchFileCheckTest {
    private val head = "title_name = \"Halo 3\"\ntitle_id = \"4D5307E6\"\nhash = \"19EB90F06A070ED6\"\n"

    private fun refused(text: String): PatchFileException =
        assertThrows(PatchFileException::class.java) { PatchFileCheck.read(text) }

    /** The emulator loads every bundled file, so the check must read every one of them alike. */
    @Test fun everyBundledCatalogFileIsRead() {
        val dir = listOf(File("../patches/xenia-canary/patches"), File("patches/xenia-canary/patches")).first { it.isDirectory }
        val files = dir.listFiles { f -> f.name.endsWith(".patch.toml") }!!.sortedBy { it.name }
        assertTrue(files.size > 400)
        val failures = files.mapNotNull { file ->
            val text = file.readText()
            runCatching {
                val spec = PatchFileCheck.read(text)
                val shown = PatchTomlParser.parse(file.name, text)!!
                check(spec.titleId == shown.titleId.uppercase()) { "title ${spec.titleId} vs ${shown.titleId}" }
                check(spec.patches.map { it.name } == shown.entries.map { it.name }) { "patch names differ" }
                check(spec.patches.all { it.writes.isNotEmpty() || it.name.isNotEmpty() })
            }.exceptionOrNull()?.let { "${file.name}: ${it.message}" }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test fun whatWouldCrashTheEmulatorIsRefusedWithItsLine() {
        assertEquals(1, refused(head).line)                                                     // no [[patch]]
        assertTrue(refused(head).message!!.contains("no [[patch]]"))
        val noAddress = refused("$head\n[[patch]]\nname = \"x\"\n[[patch.be32]]\nvalue = 0x1\n")
        assertEquals(7, noAddress.line)
        assertTrue(noAddress.message!!.contains("no address"))
        assertEquals(8, refused("$head\n[[patch]]\nname = \"x\"\n[[patch.be32]]\naddress = \"0x82000000\"\nvalue = 1\n").line)
        assertEquals(9, refused("$head\n[[patch]]\nname = \"x\"\n[[patch.be32]]\naddress = 0x82000000\nvalue = 1.5\n").line)
        assertEquals(9, refused("$head\n[[patch]]\nname = \"x\"\n[[patch.string]]\naddress = 0x82000000\nvalue = 7\n").line)
        assertEquals(9, refused("$head\n[[patch]]\nname = \"x\"\n[[patch.array]]\naddress = 0x82000000\nvalue = \"zz\"\n").line)
        assertEquals(7, refused("$head\n[[patch]]\nname = \"x\"\nis_enabled = \"yes\"\n").line)
        assertEquals(6, refused("$head\n[[patch]]\nname = 3\n").line)
        assertEquals(5, refused("$head\n[[patch]]\ndesc = \"no name\"\n").line)                // name is required
        assertEquals(7, refused("$head\n[[patch]]\nname = \"x\"\n[[patch.be24]]\n").line)       // unknown type
        assertEquals(5, refused("$head\n[[patch.be32]]\naddress = 1\nvalue = 1\n").line)        // no [[patch]] above
        assertEquals(5, refused("$head\n[patch]\nname = \"x\"\n").line)
        assertEquals(1, refused("title_id = \"4D5307E6\"\nhash = \"1\"\n[[patch]]\nname = \"x\"\n").line)   // no title_name
        assertEquals(2, refused("title_name = \"x\"\ntitle_id = \"HALO\"\nhash = \"1\"\n[[patch]]\nname = \"x\"\n").line)
        assertEquals(3, refused("title_name = \"x\"\ntitle_id = \"4D5307E6\"\nhash = []\n[[patch]]\nname = \"x\"\n").line)
        assertEquals(7, refused("$head\n[[patch]]\nname = \"x\"\nname = \"y\"\n").line)        // set twice
        assertEquals(6, refused("$head\n[[patch]]\nname = \"x\n").line)                        // unclosed text
        assertEquals(6, refused("$head\n[[patch]]\nname = \"x\" trailing\n").line)
        assertEquals(9, refused("$head\n[[patch]]\nname = \"x\"\n[[patch.be64]]\naddress = 0x0\nvalue = 0xFFFFFFFFFFFFFFFF\n").line)
    }

    @Test fun writesAreSizedLikeTheEmulatorDoes() {
        val text = head + """
            [[patch]] # comment after a header
                name = "All kinds"
                desc = '''
                   lVar5 = "a multi-line text that looks like a key"
                '''
                is_enabled = true
                [[patch.be8]]
                    address = 0x82000000
                    value = 0xFF
                [[patch.be16]]
                    address = 0x82000010
                    value = 1_000
                [[patch.be64]]
                    address = 0x82000020
                    value = -1
                [[patch.f32]]
                    address = 0x82000030
                    value = 1.5e2
                [[patch.f64]]
                    address = 0x82000040
                    value = 2
                [[patch.string]]
                    address = 0x82000050
                    value = "héllo!"
                [[patch.u16string]]
                    address = 0x82000060
                    value = 'hey'
                [[patch.array]]
                    address = 0x82000070
                    value = "0xDEADBEEF01"
        """.trimIndent()
        val spec = PatchFileCheck.read(text)
        assertEquals(listOf(1, 2, 8, 4, 8, 7, 6, 5), spec.patches.single().writes.map { it.size })
        // Like PatchDB, only the low 32 bits of an address count.
        assertEquals(0x8340043CL, PatchFileCheck.read("$head[[patch]]\nname = \"x\"\n[[patch.be8]]\naddress = 0x18340043c\nvalue = 1\n")
            .patches.single().writes.single().address)
        assertTrue(spec.patches.single().enabled)
        val listed = PatchFileCheck.read(head.replace("hash = \"19EB90F06A070ED6\"",
            "hash = [\n  \"19EB90F06A070ED6\", # retail\n  'ABCDEF',\n]") + "[[patch]]\nname = \"x\"\n")
        assertEquals(listOf("19EB90F06A070ED6", "ABCDEF"), listed.hashes)
    }

    @Test fun overlappingWritesOfPatchesOnTogetherAreConflicts() {
        fun file(hash: String, vararg patches: Triple<String, Boolean, List<PatchWrite>>) =
            PatchFileSpec("4D5307E6", "Halo 3", listOf(hash), patches.map { PatchSpec(it.first, it.second, it.third) })
        val catalog = file("AAAA",
            Triple("60 FPS", true, listOf(PatchWrite(0x82000000, 4))),
            Triple("30 FPS", false, listOf(PatchWrite(0x82000000, 4))),
            Triple("No HUD", true, listOf(PatchWrite(0x82000100, 2))))
        val mine = file("AAAA", Triple("Faster", true, listOf(PatchWrite(0x82000002, 1))),
            Triple("Elsewhere", true, listOf(PatchWrite(0x82000104, 4))))
        val otherVersion = file("BBBB", Triple("Old 60 FPS", true, listOf(PatchWrite(0x82000000, 4))))
        assertEquals(listOf(PatchConflict("60 FPS (catalog)", "Faster (yours)", 0x82000002)),
            PatchFileCheck.conflicts(listOf("catalog" to catalog, "yours" to mine, "TU" to otherVersion)))
        // Within one file, two on together collide too.
        val both = file("AAAA", Triple("60 FPS", true, listOf(PatchWrite(0x82000000, 4))),
            Triple("30 FPS", true, listOf(PatchWrite(0x82000000, 4))))
        assertEquals(1, PatchFileCheck.conflicts(listOf("catalog" to both)).size)
    }
}
