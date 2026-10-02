package xendroid.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import xendroid.compose.data.MissingTitles.Reason

class TitleRegistryTest {
    @get:Rule val temp = TemporaryFolder()
    private var now = 1_000_000L
    private val hour = 60L * 60 * 1000

    private fun registry(dir: File = File(temp.root, "library"), max: Int = 5000) = TitleRegistry(dir, { now }, max)
    private fun game(path: String, name: String, title: String?, disc: Int = 0) =
        Game(path, name, GameFormat.ISO, titleId = title, discNumber = disc, discCount = if (disc > 0) 2 else 0)

    @Test fun aScanRecordsEachTitleOnceAndAnUnchangedRescanWritesNothing() {
        val registry = registry()
        val library = listOf(
            game("/g/Lost Odyssey 2.iso", "Lost Odyssey", "4D5307E6", disc = 2),
            game("/g/Lost Odyssey 1.iso", "Lost Odyssey", "4d5307e6", disc = 1),
            game("/g/Homebrew/default.xex", "Homebrew", null),
            game("/g/Placeholder.iso", "Placeholder", "00000000"),
        )
        assertTrue(registry.record(library))
        assertEquals(listOf(KnownTitle("4D5307E6", "Lost Odyssey", "/g/Lost Odyssey 1.iso", now)), registry.all())
        now += hour
        assertFalse(registry.record(library))
        now += 24 * hour
        assertTrue(registry.record(library))           // the "last seen" day moved
        assertTrue(registry.record(listOf(game("/h/LO1.iso", "Lost Odyssey", "4D5307E6", disc = 1))))
        assertEquals("/h/LO1.iso", registry.all().single().lastPath)
        // A new process reads what was written.
        assertEquals(registry.all(), registry().all())
    }

    @Test fun eachMissingTitleSaysWhy() {
        val known = listOf(
            KnownTitle("11111111", "Deleted", "/g/Deleted.iso", 1),
            KnownTitle("22222222", "On the SD card", "/storage/ABCD-1234/Xbox/Game.iso", 1),
            KnownTitle("33333333", "Folder removed", "/old/Game.iso", 1),
            KnownTitle("44444444", "Present", "/g/Present.iso", 1),
            KnownTitle("55555555", "Hidden", "/g/Hidden.iso", 1, hidden = true),
            KnownTitle("66666666", "Not a path", "content://x/1", 1),
        )
        val missing = MissingTitles.find(known, played = emptyMap(), present = setOf("44444444"),
            roots = listOf("/g", "/storage/ABCD-1234/Xbox"), unavailableRoots = listOf("/storage/ABCD-1234/Xbox"),
            exists = { it == "/old/Game.iso" })
        assertEquals(
            listOf("Deleted" to Reason.FILE_GONE, "Folder removed" to Reason.OUTSIDE_FOLDERS,
                "Not a path" to Reason.FILE_GONE, "On the SD card" to Reason.FOLDER_AWAY),
            missing.map { it.name to it.reason })
        // A sibling folder whose name starts like a root is not inside it.
        assertEquals(Reason.OUTSIDE_FOLDERS, MissingTitles.reasonFor("/games2/x.iso", listOf("/games"), emptyList()) { true })
    }

    @Test fun titlesKnownOnlyFromTheirRunsGetAReadableName() {
        val known = listOf(KnownTitle("11111111", "From the scan", "/g/a.iso", 1))
        val played = mapOf(
            "11111111" to "/other/a.iso",                                   // the registry wins
            "22222222" to "/g/Halo 3.ISO",
            "33333333" to "/g/Braid/default.xex",
            "4d5307e6" to "/g/4D5307E6/00007000/0123456789ABCDEF0123456789ABCDEF01234567",
            "nonsense" to "/g/x.iso",
        )
        val names = MissingTitles.find(known, played, present = emptySet(), roots = listOf("/g"),
            unavailableRoots = emptyList(), exists = { false }).associate { it.titleId to it.name }
        assertEquals(mapOf("11111111" to "From the scan", "22222222" to "Halo 3", "33333333" to "Braid",
            "4D5307E6" to "Title 4D5307E6"), names)
    }

    @Test fun removingFromTheListLastsUntilTheGameIsFoundAgain() {
        val registry = registry()
        registry.record(listOf(game("/g/a.iso", "A", "11111111")))
        fun missing(r: TitleRegistry, present: Set<String> = emptySet()) =
            MissingTitles.find(r.all(), mapOf("22222222" to "/g/b.iso"), present, listOf("/g"), emptyList()) { false }
        val before = missing(registry)
        assertEquals(listOf("11111111", "22222222"), before.map { it.titleId })
        before.forEach(registry::hide)
        assertEquals(emptyList<MissingTitle>(), missing(registry))
        assertEquals(emptyList<MissingTitle>(), missing(registry()))   // kept on disk
        // The file shows up again: it is recorded and visible; gone again, it is reported again.
        registry.record(listOf(game("/g/b.iso", "B", "22222222")))
        assertEquals(emptyList<MissingTitle>(), missing(registry, present = setOf("22222222")))
        assertEquals(listOf("22222222"), missing(registry).map { it.titleId })
    }

    @Test fun damagedFilesAndForeignIdsAreIgnored() {
        val dir = temp.newFolder("library")
        File(dir, "titles.json").writeText("{ not json")
        assertEquals(emptyList<KnownTitle>(), registry(dir).all())
        File(dir, "titles.json").writeText(
            """{"version":1,"titles":[{"titleId":"abcdef01","name":"A","lastPath":"/a","lastSeenAt":1},""" +
                """{"titleId":"../evil","name":"B","lastPath":"/b","lastSeenAt":1},{"titleId":"00000000","name":"C","lastPath":"/c","lastSeenAt":1}],"later":true}""")
        assertEquals(listOf("ABCDEF01"), registry(dir).all().map { it.titleId })
    }

    @Test fun theOldestTitlesGoFirstWhenTheListIsFull() {
        val registry = registry(max = 2)
        registry.record(listOf(game("/g/a.iso", "A", "11111111")))
        now += 1
        registry.record(listOf(game("/g/b.iso", "B", "22222222")))
        now += 1
        registry.record(listOf(game("/g/c.iso", "C", "33333333")))
        assertEquals(setOf("22222222", "33333333"), registry.all().map { it.titleId }.toSet())
    }
}
