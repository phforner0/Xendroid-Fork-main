package xendroid.compose.bundle

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test
import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.compatibility.CompatibilityReport
import xendroid.compose.compatibility.TitleCompatibility

class DataBundleTest {
    private fun report(status: CompatStatus, at: Long, note: String = "") = CompatibilityReport(status, note, "b", "g", createdAt = at)

    private val sample = DataBundle(
        globalConfig = "[GPU]\nframerate_limit = 30\n",
        gameConfigs = mapOf("4D5309C9" to "[GPU]\nreadback_resolve = \"fast\"\n"),
        gamepadLayout = """{"version":2}""",
        favorites = setOf("title:4D5309C9:-:0", "uri:/storage/x.iso"),
        librarySort = "RECENT",
        compatibility = mapOf("4D5309C9" to TitleCompatibility(titleId = "4D5309C9",
            reports = listOf(report(CompatStatus.PLAYABLE, 2), report(CompatStatus.INTRO, 1)))),
    )

    private fun bytes(bundle: DataBundle) = ByteArrayOutputStream().also { DataBundles.write(it, bundle, "test", 99) }.toByteArray()

    /** Rewrites a bundle's entries through [edit] (name -> bytes), keeping or breaking its manifest. */
    private fun tampered(source: ByteArray, edit: (MutableMap<String, ByteArray>) -> Unit): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(source)).use { zip ->
            while (true) { val e = zip.nextEntry ?: break; entries[e.name] = zip.readBytes() }
        }
        edit(entries)
        return ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip -> entries.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() } }
        }.toByteArray()
    }

    private fun readFails(data: ByteArray): String =
        assertThrows(BundleException::class.java) { DataBundles.read(ByteArrayInputStream(data)) }.message!!

    @Test fun aWrittenBundleReadsBackTheSame() {
        assertEquals(sample, DataBundles.read(ByteArrayInputStream(bytes(sample))))
        assertEquals(DataBundle(), DataBundles.read(ByteArrayInputStream(bytes(DataBundle()))))
    }

    @Test fun onlyIntactBundlesOfThisFormatAreRead() {
        val good = bytes(sample)
        assertTrue(readFails(tampered(good) { it["saves/4D5309C9/slot.bin"] = byteArrayOf(1) }).startsWith("Unexpected entry"))
        assertTrue(readFails(tampered(good) { it["../escape.toml"] = byteArrayOf(1) }).startsWith("Unexpected entry"))
        assertEquals("Not a XenDroid data bundle (no manifest)", readFails(tampered(good) { it.remove("manifest.json") }))
        assertEquals("config/global.toml is damaged (checksum)",
            readFails(tampered(good) { it["config/global.toml"] = "[GPU]\nframerate_limit = 60\n".toByteArray() }))
        assertEquals("Entries do not match the manifest", readFails(tampered(good) { it.remove("gamepad/layout.json") }))
        assertTrue(readFails(tampered(good) {
            it["manifest.json"] = String(it.getValue("manifest.json")).replace("\"version\":1", "\"version\":2").toByteArray()
        }).startsWith("Made by a newer XenDroid"))
        assertEquals("Not a XenDroid data bundle", readFails(tampered(good) {
            it["manifest.json"] = String(it.getValue("manifest.json")).replace("xendroid-data-bundle", "other").toByteArray()
        }))
        val notUtf8 = DataBundle(globalConfig = "x")
        assertEquals("config/global.toml is not UTF-8 text", readFails(tampered(bytes(notUtf8)) {
            // Valid checksum, invalid text: the manifest is rewritten for the new bytes.
            val bad = byteArrayOf(0xC3.toByte(), 0x28)
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bad).joinToString("") { b -> "%02x".format(b) }
            it["config/global.toml"] = bad
            it["manifest.json"] = String(it.getValue("manifest.json"))
                .replace(Regex("\"sha256\":\"[0-9a-f]{64}\",\"size\":1"), "\"sha256\":\"$digest\",\"size\":2").toByteArray()
        }))
        assertEquals("config/global.toml is too large", readFails(tampered(good) { it["config/global.toml"] = ByteArray(1024 * 1024 + 1) }))
    }

    @Test fun importReplacesConfigsAddsFavoritesAndMergesResults() {
        val current = DataBundle(
            globalConfig = "[GPU]\nframerate_limit = 60\n",
            gameConfigs = mapOf("4D5309C9" to "old", "415607E6" to "kept"),
            gamepadLayout = """{"version":2,"globals":{"opacity":0.3}}""",
            favorites = setOf("title:415607E6:-:0"),
            librarySort = "NAME_ASC",
            compatibility = mapOf("4D5309C9" to TitleCompatibility(titleId = "4D5309C9",
                reports = listOf(report(CompatStatus.INTRO, 1), report(CompatStatus.BOOTS, 3)))),
        )
        val after = DataBundles.merged(current, sample)
        assertEquals(sample.globalConfig, after.globalConfig)
        assertEquals(mapOf("4D5309C9" to sample.gameConfigs.getValue("4D5309C9"), "415607E6" to "kept"), after.gameConfigs)
        assertEquals(sample.gamepadLayout, after.gamepadLayout)
        assertEquals(current.favorites + sample.favorites, after.favorites)       // added, none removed
        // Same result in both is kept once; newest first.
        assertEquals(listOf(3L, 2L, 1L), after.compatibility.getValue("4D5309C9").reports.map { it.createdAt })

        val plan = DataBundles.plan(current, sample)
        assertEquals(6, plan.changes)
        assertTrue(plan.lines.contains("Per-game settings: 0 new, 1 replaced, 0 unchanged, 1 kept"))
        assertTrue(plan.lines.contains("Favorites: 2 added, none removed"))
        assertTrue(plan.lines.contains("Compatibility results: 1 added"))
        assertTrue(plan.lines.any { it.startsWith("Emulator settings: replaced (2 lines differ)") })
        assertTrue(plan.lines.last().startsWith("Kept from this device"))
    }

    @Test fun importingTheCurrentStateChangesNothing() {
        val plan = DataBundles.plan(sample, sample)
        assertEquals(0, plan.changes)
        assertTrue(plan.lines.contains("Emulator settings: unchanged"))
        assertEquals(sample, DataBundles.merged(sample, sample))
        // A bundle without some parts keeps those parts.
        val partial = DataBundles.plan(sample, DataBundle(favorites = setOf("title:4D5309C9:-:0")))
        assertEquals(0, partial.changes)
        assertTrue(partial.lines.contains("Emulator settings: not in the bundle, kept"))
        assertTrue(partial.lines.contains("Touch controls: not in the bundle, kept"))
    }

    @Test fun collectionsTravelAndOnlyGainGames() {
        val rpgs = xendroid.compose.data.GameCollection("RPGs", listOf("title:4D5309C9:-:0"))
        val withCollections = sample.copy(collections = listOf(rpgs))
        assertEquals(withCollections, DataBundles.read(ByteArrayInputStream(bytes(withCollections))))
        val onlyCollections = DataBundle(collections = listOf(rpgs))
        assertEquals(onlyCollections, DataBundles.read(ByteArrayInputStream(bytes(onlyCollections))))

        val current = DataBundle(collections = listOf(xendroid.compose.data.GameCollection("rpgs", listOf("uri:/x.iso"))))
        assertEquals(listOf(xendroid.compose.data.GameCollection("rpgs", listOf("uri:/x.iso", "title:4D5309C9:-:0"))),
            DataBundles.merged(current, onlyCollections).collections)
        val plan = DataBundles.plan(current, onlyCollections)
        assertTrue(plan.lines.contains("Collections: 0 new, 1 game(s) added, none removed"))
        assertEquals(1, plan.changes)
        assertEquals(0, DataBundles.plan(onlyCollections, onlyCollections).changes)
    }

    @Test fun compatibilityHistoryStaysBounded() {
        val many = TitleCompatibility(titleId = "4D5309C9", reports = (1..30).map { report(CompatStatus.IN_GAME, it.toLong()) })
        val after = DataBundles.merged(DataBundle(), DataBundle(compatibility = mapOf("4D5309C9" to many)))
        assertEquals(20, after.compatibility.getValue("4D5309C9").reports.size)
        assertEquals(30L, after.compatibility.getValue("4D5309C9").reports.first().createdAt)
    }
}
