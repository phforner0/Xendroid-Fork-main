package xendroid.compose.patches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Round 2: hashes over several lines and the version a file says it is for. */
class PatchTomlParserTest {
    private fun parse(name: String, header: String) = PatchTomlParser.parse(name, header + """

        [[patch]]
            name = "60 FPS"
            is_enabled = false
    """.trimIndent())!!

    @Test fun readsAHashArrayOverSeveralLinesWithoutTheCommentedOnes() {
        val file = parse("415607D2 - Quake 4.patch.toml", """
            title_name = "Quake 4"
            title_id = "415607D2" # AV-2002
            hash = [
                "4768B579A3C5F134", # Original, default.xex
                "2B6EE9E95E23E2A5"  # Rerelease, default.xex
                #"52276FB138B533AF" # RSA checks removed
            ]
            #media_id = [
            #    "00000000", # Disc
            #]
        """.trimIndent())
        assertEquals(listOf("4768B579A3C5F134", "2B6EE9E95E23E2A5"), file.hashes)
        assertEquals(1, file.entries.size)
        assertEquals(PatchVersion.Match.YOURS, PatchVersion.match(file.hashes, listOf("2B6EE9E95E23E2A5")))
    }

    @Test fun theVersionComesFromTheNameOrTheComments() {
        assertEquals("TU 2", parse("58410873 - Undertow (TU2).patch.toml", """
            title_name = "Undertow" # TU2
            title_id = "58410873"
            hash = "47804ACFCFBC2165" # default.xex
        """.trimIndent()).versionLabel)
        assertEquals("TU 1", parse("354807D1 - Painkiller.patch.toml", """
            title_name = "Painkiller: Hell & Damnation" # TU1
            title_id = "354807D1"
            hash = "56209C4732826FCF" # default.xex
        """.trimIndent()).versionLabel)
        assertEquals("TU 5", parse("4D5309C9 - Forza.patch.toml", """
            title_name = "Forza"
            title_id = "4D5309C9"
            hash = [
                "336A44DC03EC8782", # Title Update 5, default.xex
            ]
        """.trimIndent()).versionLabel)
        assertNull(parse("4D5307E6 - Halo 3.patch.toml", """
            title_name = "Halo 3"
            title_id = "4D5307E6" # MS-2022
            hash = "19EB90F06A070ED6" # default.xex
        """.trimIndent()).versionLabel)
    }

    @Test fun aCommentInsideQuotesIsPartOfTheValue() {
        val file = parse("12345678 - Test.patch.toml", """
            title_name = "Game #1" # TU3
            title_id = "12345678"
        """.trimIndent())
        assertEquals("Game #1", file.titleName)
        assertEquals("TU 3", file.versionLabel)
    }
}
