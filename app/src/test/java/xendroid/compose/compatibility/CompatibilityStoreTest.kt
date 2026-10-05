package xendroid.compose.compatibility

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xendroid.compose.driver.DriverIdentity

class CompatibilityStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private var now = 1_000L
    private val driver = DriverIdentity("0x5143", "0x44050A00", "0x80C00000", "1.4.318", 18,
        "turnip", "Mesa 25.3", "Adreno 825", "0".repeat(32))

    @Test fun reportsAreKeptNewestFirstWithTheirContext() {
        val store = CompatibilityStore(folder.root, clock = { now })
        assertNull(store.get("4d5309c9").latest)
        store.report("4d5309c9", CompatStatus.INTRO, "  stops at the title screen  ", "77011a0c+local.abc-debug", "Adreno 825", null)
        now += 1_000
        val after = store.report("4D5309C9", CompatStatus.PLAYABLE, "", "77011a0c+local.def-debug", "Adreno 825", driver)
        assertEquals(listOf(CompatStatus.PLAYABLE, CompatStatus.INTRO), after.reports.map { it.status })
        assertEquals(driver.key, after.latest!!.driverKey)
        assertEquals("stops at the title screen", store.get("4D5309C9").reports[1].note)
        assertEquals("4D5309C9", store.get("4d5309c9").titleId)
    }

    @Test fun editionAndDiscAreKeptOnlyWhenValid() {
        val store = CompatibilityStore(folder.root, clock = { now++ })
        val good = store.report("4D5309C9", CompatStatus.PLAYABLE, "", "b", "g", null, mediaId = "1a2b3c4d", disc = 2).latest!!
        assertEquals("1A2B3C4D", good.mediaId)
        assertEquals(2, good.disc)
        val unknown = store.report("4D5309C9", CompatStatus.PLAYABLE, "", "b", "g", null, mediaId = "00000000", disc = 0).latest!!
        assertNull(unknown.mediaId)
        assertNull(unknown.disc)
        assertNull(store.report("4D5309C9", CompatStatus.BOOTS, "", "b", "g", null, mediaId = "../x").latest!!.mediaId)
        // Reports written before editions were recorded still decode.
        File(folder.root, "415607E6.json").writeText(
            """{"titleId":"415607E6","reports":[{"status":"INTRO","build":"b","gpu":"g","createdAt":1}]}""")
        assertNull(store.get("415607E6").latest!!.mediaId)
    }

    @Test fun historyIsBoundedAndTitlesAreValidated() {
        val store = CompatibilityStore(folder.root, clock = { now++ }, maxReports = 3)
        repeat(5) { store.report("415607E6", CompatStatus.IN_GAME, "run $it", "b", "g", null) }
        assertEquals(listOf("run 4", "run 3", "run 2"), store.get("415607E6").reports.map { it.note })
        assertThrows(IllegalArgumentException::class.java) { store.get("../../x") }
        assertThrows(IllegalArgumentException::class.java) { store.report("00000000", CompatStatus.BOOTS, "", "b", "g", null) }
    }

    @Test fun allTitlesAreListedAndAMergedHistoryReplacesOne() {
        val store = CompatibilityStore(folder.root, clock = { now++ }, maxReports = 2)
        store.report("4D5309C9", CompatStatus.BOOTS, "", "b", "g", null)
        store.report("415607E6", CompatStatus.PLAYABLE, "", "b", "g", null)
        File(folder.root, "notes.txt").writeText("not a title")
        File(folder.root, "58410889.json").writeText("{ damaged")
        assertEquals(setOf("4D5309C9", "415607E6"), store.all().keys)
        val merged = TitleCompatibility(titleId = "x", reports = (1..3).map {
            CompatibilityReport(CompatStatus.IN_GAME, "r$it", "b", "g", createdAt = it.toLong())
        })
        store.replace("4d5309c9", merged)
        val stored = store.get("4D5309C9")
        assertEquals("4D5309C9", stored.titleId)
        assertEquals(listOf("r1", "r2"), stored.reports.map { it.note })   // bounded like any history
    }

    @Test fun aDamagedFileReadsAsUnratedAndIsReplacedByTheNextReport() {
        val store = CompatibilityStore(folder.root)
        File(folder.root, "4D5309C9.json").writeText("{ damaged")
        assertNull(store.get("4D5309C9").latest)
        store.report("4D5309C9", CompatStatus.BOOTS, "", "b", "g", null)
        assertEquals(CompatStatus.BOOTS, store.get("4D5309C9").latest!!.status)
    }
}
