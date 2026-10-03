package xendroid.compose.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.compatibility.DeviceFacts
import xendroid.compose.compatibility.ProfileSource
import xendroid.compose.compatibility.SettingsProfiles
import xendroid.compose.community.CommunityConfigs.DraftProblem
import xendroid.compose.community.CommunityConfigs.Match

class CommunityConfigsTest {
    private val s23 = DeviceFacts("Adreno (TM) 740", "Qualcomm Vulkan 0.762 · Adreno (TM) 740", "system",
        "samsung", "SM-S911B", 34, 120, soc = "SM8550")

    private fun config(
        id: String = "0123456789abcdef",
        title: String = "4D5307E6",
        settings: String = """{"GPU|framerate_limit":"30","Console|widescreen":"true"}""",
        device: String = """{"manufacturer":"samsung","model":"SM-S911B","soc":"SM8550","gpu":"Adreno (TM) 740","driver":"Qualcomm 0.762","androidSdk":34}""",
        result: String = "PLAYABLE",
        name: String = "Steady 30",
        up: Int = 0,
        down: Int = 0,
        date: String = "2026-10-01",
        extra: String = "",
    ) = """{"id":"$id","titleId":"$title","name":"$name","note":"Holds 30 FPS where 60 stutters","result":"$result",
        |"settings":$settings,"device":$device,"appVersionCode":120,"appBuild":"1.2.0","createdAt":"$date",
        |"votesUp":$up,"votesDown":$down$extra}""".trimMargin()

    private fun list(vararg configs: String, title: String = "4D5307E6", version: Int = 1) =
        """{"format":"xendroid-community-configs","version":$version,"titleId":"$title","configs":[${configs.joinToString(",")}]}"""

    @Test fun aSharedConfigBecomesAProfileNotReviewedByXenDroid() {
        val listing = CommunityConfigs.parseList(list(config()), "4d5307e6", s23)
        assertEquals(emptyList<String>(), listing.skipped)
        val entry = listing.entries.single()
        assertEquals(ProfileSource.COMMUNITY, entry.profile.source)
        assertEquals("community-0123456789abcdef", entry.profile.profile.id)
        assertEquals(listOf("4D5307E6"), entry.profile.profile.titleIds)
        assertEquals("Holds 30 FPS where 60 stutters", entry.profile.profile.reason)
        assertEquals(mapOf("GPU|framerate_limit" to "30", "Console|widescreen" to "true"), entry.profile.profile.settings)
        assertEquals(CompatStatus.PLAYABLE, entry.status)
        assertEquals(Match.MODEL, entry.match)
        // Applying it goes through the C05 plan: the player's own value stays.
        val plan = SettingsProfiles.plan(entry.profile.profile, mapOf("Console|widescreen" to "false"), emptyMap())
        assertEquals(mapOf("GPU|framerate_limit" to "30"), plan.writes)
        assertEquals(SettingsProfiles.Kind.YOURS, plan.lines.single { it.key == "Console|widescreen" }.kind)
    }

    @Test fun whatAProfileMayNotChangeIsSkippedWithWhy() {
        val listing = CommunityConfigs.parseList(list(
            config(id = "aaaaaaaa", settings = """{"Vulkan|vulkan_lib_path":"/sdcard/evil.so"}"""),
            config(id = "bbbbbbbb", settings = """{"GPU|framerate_limit":"50"}"""),
            config(id = "cccccccc", settings = """{"APU|xma_decoder":"fake"}"""),
            config(id = "dddddddd", title = "415607E6"),
            config(id = "eeeeeeee", result = "GREAT"),
            config(id = "BAD-ID"),
            config(id = "ffffffff", date = "1 Oct"),
            config(id = "gggggggg", name = ""),
            config(id = "hhhhhhhh", up = -1),
            config(id = "iiiiiiii", device = """{"model":"${"x".repeat(65)}"}"""),
            config(id = "jjjjjjjj"),
            config(id = "jjjjjjjj"),
            """{"id":"kkkkkkkk"}""",
        ), "4D5307E6", s23)
        assertEquals(listOf("jjjjjjjj"), listing.entries.map { it.config.id })
        val why = listing.skipped.joinToString("\n")
        assertEquals(why, 12, listing.skipped.size)
        assertTrue(why, why.contains("aaaaaaaa: Vulkan|vulkan_lib_path is not a setting profiles may change"))
        assertTrue(why, why.contains("bbbbbbbb: GPU|framerate_limit must be one of"))
        assertTrue(why, why.contains("cccccccc: APU|xma_decoder = fake is not allowed"))
        assertTrue(why, why.contains("dddddddd: for another game"))
        assertTrue(why, why.contains("eeeeeeee: unknown result"))
        assertTrue(why, why.contains("BAD-ID: the id is not one the server gives"))
        assertTrue(why, why.contains("jjjjjjjj: the same id twice"))
        assertTrue(why, why.contains("a config the app cannot read"))
    }

    @Test fun aListInAnotherFormatOrForAnotherGameIsSkippedWhole() {
        assertTrue(CommunityConfigs.parseList(list(config(), version = 2), "4D5307E6", s23).skipped.single().contains("version 2"))
        assertEquals("the list is for another game",
            CommunityConfigs.parseList(list(config(), title = "415607E6"), "4D5307E6", s23).skipped.single())
        assertEquals("the server did not send a list of configs", CommunityConfigs.parseList("<html>", "4D5307E6", s23).skipped.single())
        assertTrue(CommunityConfigs.parseList("x".repeat(CommunityConfigs.MAX_LIST_BYTES + 1), "4D5307E6", s23).entries.isEmpty())
        // A field a later server adds is ignored; the version says when the meaning changes.
        assertEquals(1, CommunityConfigs.parseList(list(config(extra = ""","reports":3""")), "4D5307E6", s23).entries.size)
        val many = (0 until 101).map { config(id = "c%07d".format(it)) }.toTypedArray()
        val capped = CommunityConfigs.parseList(list(*many), "4D5307E6", s23)
        assertEquals(CommunityConfigs.MAX_CONFIGS, capped.entries.size)
        assertTrue(capped.skipped.single().contains("more than 100"))
    }

    @Test fun closestPhoneFirstThenVotesThenNewest() {
        val other = """{"manufacturer":"Google","model":"Pixel 8","soc":"Tensor G3","gpu":"Mali-G715","androidSdk":34}"""
        val sameGpu = """{"manufacturer":"OnePlus","model":"CPH2449","soc":"SM8550-AB","gpu":"Adreno 740","androidSdk":34}"""
        val sameFamily = """{"manufacturer":"Xiaomi","model":"23049PCD8G","soc":"SM7475","gpu":"Adreno (TM) 725","androidSdk":34}"""
        val sameSoc = """{"manufacturer":"samsung","model":"SM-S916B","soc":"SM8550","gpu":"Adreno (TM) 740","androidSdk":34}"""
        val listing = CommunityConfigs.parseList(list(
            config(id = "other111", device = other, up = 50),
            config(id = "family11", device = sameFamily),
            config(id = "gpu11111", device = sameGpu, up = 1),
            config(id = "gpu22222", device = sameGpu, up = 3, down = 1),
            config(id = "gpu33333", device = sameGpu, up = 2, date = "2026-10-02"),
            config(id = "soc11111", device = sameSoc),
            config(id = "model111"),
        ), "4D5307E6", s23)
        assertEquals(listOf("model111", "soc11111", "gpu33333", "gpu22222", "gpu11111", "family11", "other111"),
            listing.entries.map { it.config.id })
        assertEquals(listOf(Match.MODEL, Match.SOC, Match.GPU, Match.GPU, Match.GPU, Match.GPU_FAMILY, Match.OTHER),
            listing.entries.map { it.match })
        // A phone not known yet sees them all as other hardware, best voted first.
        val unknown = CommunityConfigs.parseList(list(config(id = "aaaaaaaa", up = 1), config(id = "bbbbbbbb", up = 2)), "4D5307E6", null)
        assertEquals(listOf("bbbbbbbb", "aaaaaaaa"), unknown.entries.map { it.config.id })
        assertTrue(unknown.entries.all { it.match == Match.OTHER })
    }

    @Test fun gpuNamesAndGenerations() {
        assertEquals("adreno 740", CommunityConfigs.gpuKey("Adreno (TM) 740"))
        assertEquals(CommunityConfigs.gpuKey("adreno 740"), CommunityConfigs.gpuKey("Adreno (TM) 740"))
        assertEquals("adreno 7", CommunityConfigs.gpuFamily("Adreno (TM) 750"))
        assertEquals("adreno 8", CommunityConfigs.gpuFamily("Adreno (TM) 830"))
        assertEquals("mali 7", CommunityConfigs.gpuFamily("Mali-G715"))
        assertEquals("immortalis 9", CommunityConfigs.gpuFamily("Immortalis-G925"))
        assertEquals("xclipse 9", CommunityConfigs.gpuFamily("Samsung Xclipse 940"))
        assertNull(CommunityConfigs.gpuFamily("PowerVR B-Series BXM-8-256"))
        assertNull(CommunityConfigs.gpuFamily(null))
        assertNull(CommunityConfigs.gpuKey("  "))
        // A model shared without a manufacturer still matches; another maker's same model name does not.
        assertEquals(Match.MODEL, CommunityConfigs.match(CommunityDevice(model = "sm-s911b"), s23))
        assertNotEquals(Match.MODEL, CommunityConfigs.match(CommunityDevice(manufacturer = "acme", model = "SM-S911B"), s23))
    }

    @Test fun mostlyDownvotedConfigsAreHiddenAndCounted() {
        val listing = CommunityConfigs.parseList(list(
            config(id = "aaaaaaaa", up = 1, down = 5),
            config(id = "bbbbbbbb", up = 3, down = 5),
            config(id = "cccccccc", up = 0, down = 4),
        ), "4D5307E6", s23)
        assertEquals(1, listing.hidden)
        assertEquals(listOf("bbbbbbbb", "cccccccc"), listing.entries.map { it.config.id })
    }

    @Test fun sharingSendsOnlyWhatAProfileMayCarry() {
        val overrides = mapOf(
            "GPU|framerate_limit" to "30",
            "Vulkan|vulkan_lib_path" to "/storage/emulated/0/drivers/turnip.so",
            "Logging|log_level" to "4",
            "Console|widescreen" to "true",
            "GPU|draw_resolution_scale_x" to "9",
        )
        val draft = CommunityConfigs.draft("4d5307e6", "  Steady 30 ", "Fixes stutter; log at /storage/emulated/0/xenia/x.log",
            CompatStatus.PLAYABLE, overrides, s23, 120, "1.2.0")
        assertEquals(emptySet<DraftProblem>(), draft.problems)
        assertEquals(mapOf("Console|widescreen" to "true", "GPU|framerate_limit" to "30"), draft.shared)
        assertEquals(listOf("GPU|draw_resolution_scale_x", "Logging|log_level", "Vulkan|vulkan_lib_path"), draft.notShared)
        val upload = draft.upload!!
        assertEquals("4D5307E6", upload.titleId)
        assertEquals("Steady 30", upload.name)
        assertEquals("Fixes stutter; log at [storage-path]", upload.note)
        assertEquals("PLAYABLE", upload.result)
        assertEquals(CommunityDevice("samsung", "SM-S911B", "SM8550", "Adreno (TM) 740",
            "Qualcomm Vulkan 0.762 · Adreno (TM) 740", 34), upload.device)
        val sent = CommunityConfigs.encodeUpload(upload)
        assertFalse(sent, sent.contains("vulkan_lib_path") || sent.contains("turnip") || sent.contains("log_level"))
        // What goes out is exactly the protocol's fields.
        val fields = Regex("\"(\\w+)\":").findAll(sent).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("titleId", "name", "note", "result", "settings", "device", "appVersionCode", "appBuild",
            "manufacturer", "model", "soc", "gpu", "driver", "androidSdk"), fields)
    }

    @Test fun aShareWaitsForANameANoteAResultAndSettings() {
        fun problems(name: String = "n", note: String = "x", result: CompatStatus? = CompatStatus.PLAYABLE,
                     overrides: Map<String, String> = mapOf("GPU|framerate_limit" to "30")) =
            CommunityConfigs.draft("4D5307E6", name, note, result, overrides, null, 1, "b").problems
        assertEquals(setOf(DraftProblem.NAME_LENGTH), problems(name = " "))
        assertEquals(setOf(DraftProblem.NAME_LENGTH), problems(name = "x".repeat(61)))
        assertEquals(setOf(DraftProblem.NOTE_LENGTH), problems(note = ""))
        assertEquals(setOf(DraftProblem.NOTE_LENGTH), problems(note = "x".repeat(501)))
        assertEquals(setOf(DraftProblem.NO_RESULT), problems(result = null))
        assertEquals(setOf(DraftProblem.NOTHING_TO_SHARE), problems(overrides = mapOf("Logging|log_level" to "4")))
        val many = SettingsProfiles.ALLOWED_KEYS.associateWith { key ->
            when (val s = xendroid.compose.settings.SettingsSchema.byKey.getValue(key)) {
                is xendroid.compose.settings.Setting.Bool -> "true"
                is xendroid.compose.settings.Setting.IntRange -> s.min.toString()
                is xendroid.compose.settings.Setting.ListChoice -> s.options.first { it.value !in setOf("fake") }.value
                else -> ""
            }
        }
        assertEquals(setOf(DraftProblem.TOO_MANY_SETTINGS), problems(overrides = many))
        assertNull(CommunityConfigs.draft("4D5307E6", "", "", null, emptyMap(), null, 1, "b").upload)
    }

    @Test fun votesCannotBeLinkedAcrossConfigs() {
        val secret = ByteArray(32) { it.toByte() }
        val a = CommunityConfigs.voterId(secret, "aaaaaaaa")
        assertTrue(a.matches(Regex("[0-9a-f]{32}")))
        assertEquals(a, CommunityConfigs.voterId(secret, "aaaaaaaa"))
        assertNotEquals(a, CommunityConfigs.voterId(secret, "bbbbbbbb"))
        assertNotEquals(a, CommunityConfigs.voterId(ByteArray(32), "aaaaaaaa"))
    }

    @Test fun onlyAnHttpsServer() {
        assertEquals("https://community.example.org/xd", CommunityConfigs.baseUrl(" https://community.example.org/xd/ "))
        assertNull(CommunityConfigs.baseUrl(""))
        assertNull(CommunityConfigs.baseUrl("http://community.example.org"))
        assertNull(CommunityConfigs.baseUrl("https://user:pw@community.example.org"))
        assertNull(CommunityConfigs.baseUrl("https://community.example.org/?a=1"))
        assertNull(CommunityConfigs.baseUrl("https://community.example.org/#x"))
        assertNull(CommunityConfigs.baseUrl("ftp://community.example.org"))
        assertNull(CommunityConfigs.baseUrl("http://127.0.0.1:8080"))
        assertEquals("http://127.0.0.1:8080", CommunityConfigs.baseUrl("http://127.0.0.1:8080", allowLoopbackHttp = true))
        assertNull(CommunityConfigs.baseUrl("http://10.0.0.2:8080", allowLoopbackHttp = true))
    }

    @Test fun answersAreCheckedBeforeUse() {
        assertEquals(CommunityConfigs.Receipt("0123456789abcdef", "A".repeat(32)),
            CommunityConfigs.parseReceipt("""{"id":"0123456789abcdef","deleteToken":"${"A".repeat(32)}"}"""))
        assertNull(CommunityConfigs.parseReceipt("""{"id":"../x","deleteToken":"${"A".repeat(32)}"}"""))
        assertNull(CommunityConfigs.parseReceipt("""{"id":"0123456789abcdef","deleteToken":"short"}"""))
        assertNull(CommunityConfigs.parseReceipt("nope"))
        assertEquals(CommunityConfigs.Votes(2, 1), CommunityConfigs.parseVotes("""{"votesUp":2,"votesDown":1}"""))
        assertNull(CommunityConfigs.parseVotes("""{"votesUp":-2,"votesDown":1}"""))
        assertNull(CommunityConfigs.parseVotes("""{"votesUp":"2","votesDown":1}"""))
        assertEquals("name must be 1-60 characters", CommunityConfigs.serverReason("""{"error":"name must be 1-60 characters"}"""))
        assertEquals("a b", CommunityConfigs.serverReason("{\"error\":\"a\\u0007b\"}"))
        assertNull(CommunityConfigs.serverReason("<html>"))
        assertNotNull(CommunityConfigs.serverReason("""{"error":"${"x".repeat(500)}"}""")?.takeIf { it.length == 200 })
    }
}
