package xendroid.compose.userdata

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xendroid.compose.userdata.GameDataView.Kind

class GameDataViewTest {
    @get:Rule val folder = TemporaryFolder()

    private fun file(path: String, text: String = "x"): File =
        File(folder.root, path).apply { parentFile!!.mkdirs(); writeText(text) }

    private fun view(): GameDataView {
        val files = UserDataFiles(folder.root) { AutoCloseable {} }
        return GameDataView(folder.root, files::isVisible)
    }

    @Test fun aGamesDataIsGatheredFromWhereTheCoreKeepsIt() {
        file("content/E03000002B7C4D1A/4D5309C9/00000001/save.dat")
        file("content/E03000002B7C4D1A/FFFE07D1/00010000/E03000002B7C4D1A")      // the dashboard's: a profile
        file("content/0000000000000000/4D5309C9/00000002/dlc.pkg")
        file("content/0000000000000000/4D5309C9/000B0000/tu.bin")
        file("content/E030000000000099/4D5309C9/00000001/other.dat")
        file("content/E030000000000099/.xendroid-trash/4D5309C9/old.dat")             // bookkeeping, hidden
        file("config/4D5309C9.config.toml")
        file("config/58410889.config.toml")                                             // config only: still a game
        file("patches/4D5309C9 - Forza Motorsport 3.patch.toml")
        file("patches/4D5309C9 - mine - 60 FPS.patch.toml")
        file("patches/AAAAAAAA - Elsewhere.patch.toml")                                 // patches alone do not list a game
        file("cache/4D5309C9/shaders.bin")                                              // caches stay out

        val view = view()
        assertEquals(listOf("4D5309C9", "58410889"), view.titles())
        val entries = view.entries("4d5309c9")
        assertEquals(listOf(Kind.CONSOLE_DATA, Kind.PROFILE_DATA, Kind.PROFILE_DATA, Kind.CONFIG, Kind.PATCH, Kind.PATCH),
            entries.map { it.kind })
        assertEquals(listOf(null, "E030000000000099", "E03000002B7C4D1A", null, null, null), entries.map { it.owner })
        assertEquals(File(folder.root, "content/0000000000000000/4D5309C9"), entries.first().file)
        assertEquals(listOf("4D5309C9 - Forza Motorsport 3.patch.toml", "4D5309C9 - mine - 60 FPS.patch.toml"),
            entries.filter { it.kind == Kind.PATCH }.map { it.file.name })
    }

    @Test fun eachRealFileKnowsTheGameItBelongsTo() {
        val save = file("content/E03000002B7C4D1A/4D5309C9/00000001/save.dat")
        val config = file("config/4D5309C9.config.toml")
        val patch = file("patches/4D5309C9 - Forza.patch.toml")
        val view = view()
        assertEquals("4D5309C9", view.titleOf(save))
        assertEquals("4D5309C9", view.titleOf(save.parentFile!!.parentFile!!))
        assertEquals("4D5309C9", view.titleOf(config))
        assertEquals("4D5309C9", view.titleOf(patch))
        assertNull(view.titleOf(file("content/E03000002B7C4D1A/FFFE07D1/00010000/p")))   // system title
        assertNull(view.titleOf(file("logs/xe.log")))
        assertNull(view.titleOf(folder.root))
        assertNull(view.titleOf(File(folder.root.parentFile, "elsewhere/4D5309C9.config.toml")))
        assertNull(view.titleOf(file("content/E03000002B7C4D1A/.xendroid-trash/x")))
    }

    @Test fun onlyGameTitleIdsAreAccepted() {
        assertTrue(GameDataView.isGameTitle("4D5309C9"))
        listOf("00000000", "FFFE07D1", "4d5309c9", "4D5309C", "../x").forEach { assertTrue(it, !GameDataView.isGameTitle(it)) }
        assertEquals(emptyList<String>(), view().titles())
    }
}
