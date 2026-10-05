package xendroid.compose.community

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.compatibility.DeviceFacts
import xendroid.compose.compatibility.SettingsProfiles

/** The reference server (tools/community-server) and the app agree on the protocol. */
class CommunityServerContractTest {
    @get:Rule val folder = TemporaryFolder()

    private val dir = listOf(File("../tools/community-server"), File("tools/community-server")).first { it.isDirectory }
    private val contract = Json.parseToJsonElement(File(dir, "contract.json").readText()) as JsonObject

    @Test fun theServerChecksTheSameRules() {
        assertEquals(CommunityConfigs.FORMAT, contract["format"]!!.jsonPrimitive.content)
        assertEquals(CommunityConfigs.VERSION, contract["version"]!!.jsonPrimitive.int)
        assertEquals(SettingsProfiles.ALLOWED_KEYS.sorted(), (contract["allowedKeys"] as JsonArray).map { it.jsonPrimitive.content })
        assertEquals(CompatStatus.entries.map { it.name }.toSet(), (contract["results"] as JsonArray).map { it.jsonPrimitive.content }.toSet())
        val limits = contract["limits"] as JsonObject
        assertEquals(CommunityConfigs.MAX_NAME, limits["name"]!!.jsonPrimitive.int)
        assertEquals(CommunityConfigs.MAX_NOTE, limits["note"]!!.jsonPrimitive.int)
        assertEquals(SettingsProfiles.MAX_SETTINGS, limits["settings"]!!.jsonPrimitive.int)
        assertEquals(CommunityConfigs.MAX_FIELD, limits["field"]!!.jsonPrimitive.int)
        assertEquals(CommunityConfigs.MAX_CONFIGS, limits["listed"]!!.jsonPrimitive.int)
    }

    /** The app's client against the real reference server, when python3 is on this machine. */
    @Test fun endToEndAgainstTheReferenceServer() {
        val python = runCatching { ProcessBuilder("python3", "--version").start().waitFor(10, TimeUnit.SECONDS) }.getOrDefault(false)
        assumeTrue("python3 is not available", python)
        val process = ProcessBuilder("python3", File(dir, "server.py").path, "--port", "0",
            "--db", File(folder.root, "community.sqlite3").path).redirectError(File(folder.root, "server.log")).start()
        try {
            val first = process.inputStream.bufferedReader().readLine().orEmpty()
            val base = Regex("http://127\\.0\\.0\\.1:\\d+").find(first)?.value ?: error("the server did not start: $first")
            val facts = DeviceFacts("Adreno (TM) 740", "Qualcomm Vulkan 0.762", "system", "samsung", "SM-S911B", 34, 120, soc = "SM8550")
            val other = facts.copy(manufacturer = "Google", model = "Pixel 8", soc = "Tensor G3", gpu = "Mali-G715")
            val service = CommunityService(CommunityClient(base, allowLoopbackHttp = true),
                CommunityStore(File(folder.root, "community")), "127.0.0.1", 120, "1.2.0")

            val draft = service.draft("4D5307E6", "Steady 30", "Holds 30 FPS where 60 stutters", CompatStatus.PLAYABLE,
                mapOf("GPU|framerate_limit" to "30", "Console|widescreen" to "true", "Logging|log_level" to "4"), facts)
            val mine = service.share(draft.upload!!)
            service.share(service.draft("4D5307E6", "From a Pixel", "Fewer stalls", CompatStatus.IN_GAME,
                mapOf("GPU|framerate_limit" to "45"), other).upload!!)

            val listed = service.refresh("4D5307E6", facts)
            assertEquals(emptyList<String>(), listed.listing.skipped)
            assertEquals(listOf("Steady 30", "From a Pixel"), listed.listing.entries.map { it.config.name })
            val entry = listed.listing.entries.first()
            assertEquals(CommunityConfigs.Match.MODEL, entry.match)
            assertEquals(mapOf("Console|widescreen" to "true", "GPU|framerate_limit" to "30"), entry.profile.profile.settings)
            assertTrue(mine.configId in listed.mine)

            assertEquals(CommunityConfigs.Votes(1, 0), service.vote(mine.configId, 1))
            assertEquals(CommunityConfigs.Votes(0, 1), service.vote(mine.configId, -1))
            assertEquals(CommunityConfigs.Votes(0, 0), service.vote(mine.configId, 0))

            service.delete(mine.configId)
            assertEquals(listOf("From a Pixel"), service.refresh("4D5307E6", facts).listing.entries.map { it.config.name })
            assertTrue(service.refresh("415607E6", facts).listing.entries.isEmpty())
        } finally {
            process.destroy()
            process.waitFor(5, TimeUnit.SECONDS)
        }
    }
}
