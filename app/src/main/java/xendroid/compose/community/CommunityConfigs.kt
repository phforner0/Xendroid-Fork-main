package xendroid.compose.community

import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.compatibility.DeviceFacts
import xendroid.compose.compatibility.LoadedProfile
import xendroid.compose.compatibility.ProfileEvidence
import xendroid.compose.compatibility.ProfileSource
import xendroid.compose.compatibility.SettingsProfile
import xendroid.compose.compatibility.SettingsProfiles
import xendroid.compose.core.LogRedactor

/** The phone a config was shared from, as the sender's app described it. */
@Serializable
data class CommunityDevice(
    val manufacturer: String = "",
    val model: String = "",
    val soc: String = "",
    val gpu: String = "",
    val driver: String = "",
    val androidSdk: Int = 0,
)

/** One shared config as the server lists it. */
@Serializable
data class CommunityConfig(
    val id: String,
    val titleId: String,
    val name: String,
    /** What the settings fix, in the sender's words. */
    val note: String,
    /** A [CompatStatus] name: what the sender saw with these settings. */
    val result: String,
    /** "Section|name" -> value, as the settings store them. */
    val settings: Map<String, String>,
    val device: CommunityDevice = CommunityDevice(),
    val appVersionCode: Int = 0,
    val appBuild: String = "",
    /** YYYY-MM-DD (UTC), set by the server. */
    val createdAt: String,
    val votesUp: Int = 0,
    val votesDown: Int = 0,
)

/** What sharing sends, all of it: no account, no install id, no file name or path. */
@Serializable
data class CommunityUpload(
    val titleId: String,
    val name: String,
    val note: String,
    val result: String,
    val settings: Map<String, String>,
    val device: CommunityDevice,
    val appVersionCode: Int,
    val appBuild: String,
)

/**
 * 15b: settings other players shared for a game. Off unless the build names a server
 * (-PxendroidCommunityUrl, HTTPS); the server is only contacted when the player searches,
 * votes, shares or deletes. A shared config becomes a C05 profile marked as not reviewed: the
 * same allow-list and values, the same preview of every change, the player's own settings
 * kept, "Restore previous" after. Configs are ranked by how close the sender's phone is to
 * this one. The rules are pure, so they are tested on the JVM.
 */
object CommunityConfigs {
    const val FORMAT = "xendroid-community-configs"
    const val VERSION = 1
    const val MAX_LIST_BYTES = 512 * 1024
    const val MAX_CONFIGS = 100
    const val MAX_NAME = 60
    const val MAX_NOTE = 500
    const val MAX_FIELD = 64
    /** Shown at once; the rest behind "Show all". */
    const val FIRST_SHOWN = 10
    /** A config is hidden (and counted) once this many voters, and most of them, said it did not help. */
    const val HIDE_DOWNVOTES = 5

    private const val MAX_VOTES = 1_000_000
    private val ID = Regex("[a-z0-9]{8,32}")
    private val TITLE = Regex("[0-9A-F]{8}")
    private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val TOKEN = Regex("[A-Za-z0-9_-]{16,128}")
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }
    private val uploadJson = Json { encodeDefaults = true }

    /** How close the sender's phone is to this one, closest first. */
    enum class Match { MODEL, SOC, GPU, GPU_FAMILY, OTHER }

    data class Entry(val config: CommunityConfig, val profile: LoadedProfile, val match: Match, val status: CompatStatus)

    /** [entries] best first; [hidden]: mostly downvoted; [skipped]: what broke a rule, with why. */
    data class Listing(val entries: List<Entry>, val hidden: Int, val skipped: List<String>)

    data class Receipt(val id: String, val deleteToken: String)

    data class Votes(val up: Int, val down: Int)

    /** Why a share cannot go yet; the dialog says each in its language. */
    enum class DraftProblem { NOTHING_TO_SHARE, TOO_MANY_SETTINGS, NAME_LENGTH, NOTE_LENGTH, NO_RESULT }

    /** [shared]: this game's own settings a config may carry; [notShared]: the rest of them, kept
     *  off the server; [upload]: exactly what will be sent, null while there are [problems]. */
    data class Draft(
        val shared: Map<String, String>,
        val notShared: List<String>,
        val device: CommunityDevice,
        val problems: Set<DraftProblem>,
        val upload: CommunityUpload?,
    )

    /**
     * The server's address as the build names it: HTTPS only (an http one on this machine for
     * tests), with no credentials, query or fragment. Null = not usable, and the feature stays off.
     */
    fun baseUrl(raw: String, allowLoopbackHttp: Boolean = false): String? {
        val text = raw.trim().trimEnd('/')
        if (text.isEmpty()) return null
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val loopback = host == "127.0.0.1" || host == "localhost" || host == "[::1]"
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && !(allowLoopbackHttp && scheme == "http" && loopback)) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        return text
    }

    fun isConfigId(id: String): Boolean = ID.matches(id)

    fun isTitleId(titleId: String): Boolean = TITLE.matches(titleId) && titleId != "00000000"

    /** Reads the server's list for [titleId]; whatever breaks a rule is skipped with the reason. */
    fun parseList(text: String, titleId: String, facts: DeviceFacts?): Listing {
        val title = titleId.uppercase()
        if (text.length > MAX_LIST_BYTES) return Listing(emptyList(), 0, listOf("the list is larger than 512 KB"))
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return Listing(emptyList(), 0, listOf("the server did not send a list of configs"))
        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val version = number(root["version"])
        if (format != FORMAT || version != VERSION) {
            return Listing(emptyList(), 0, listOf("format \"${format.orEmpty().take(40)}\" version $version is not supported"))
        }
        val listedTitle = (root["titleId"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.uppercase()
        if (listedTitle != title) return Listing(emptyList(), 0, listOf("the list is for another game"))
        val configs = root["configs"] as? JsonArray ?: return Listing(emptyList(), 0, listOf("the list has no configs"))
        val skipped = ArrayList<String>()
        if (configs.size > MAX_CONFIGS) skipped += "more than $MAX_CONFIGS configs; the rest were skipped"
        val entries = ArrayList<Entry>()
        val ids = HashSet<String>()
        var hidden = 0
        for (element in configs.take(MAX_CONFIGS)) {
            val config = runCatching { json.decodeFromJsonElement(CommunityConfig.serializer(), element) }.getOrNull()
            if (config == null) {
                skipped += "a config the app cannot read"
                continue
            }
            val label = config.id.take(32)
            val problem = configProblem(config, title)
                ?: SettingsProfiles.problems(toProfile(config), ProfileSource.COMMUNITY).firstOrNull()
            when {
                problem != null -> skipped += "$label: $problem"
                !ids.add(config.id) -> skipped += "$label: the same id twice"
                mostlyDownvoted(config) -> hidden++
                else -> entries += Entry(config, LoadedProfile(toProfile(config), ProfileSource.COMMUNITY, config.id),
                    match(config.device, facts), CompatStatus.valueOf(config.result))
            }
        }
        return Listing(rank(entries), hidden, skipped)
    }

    private fun configProblem(c: CommunityConfig, title: String): String? = when {
        !ID.matches(c.id) -> "the id is not one the server gives"
        c.titleId.uppercase() != title -> "for another game"
        CompatStatus.entries.none { it.name == c.result } -> "unknown result \"${c.result.take(20)}\""
        c.name.isBlank() || c.name.trim().length > MAX_NAME -> "name must be 1-$MAX_NAME characters"
        c.note.isBlank() || c.note.trim().length > MAX_NOTE -> "note must be 1-$MAX_NOTE characters"
        with(c.device) { listOf(manufacturer, model, soc, gpu, driver).any { it.length > MAX_FIELD } || androidSdk !in 0..99 } ->
            "the phone's description is too long"
        c.appBuild.length > MAX_FIELD || c.appVersionCode < 0 -> "the app version is not valid"
        !DATE.matches(c.createdAt) -> "the date is not YYYY-MM-DD"
        c.votesUp !in 0..MAX_VOTES || c.votesDown !in 0..MAX_VOTES -> "the vote counts are not valid"
        else -> null
    }

    fun mostlyDownvoted(c: CommunityConfig): Boolean = c.votesDown >= HIDE_DOWNVOTES && c.votesDown > 2 * c.votesUp

    /** A config as a C05 profile: the note is its reason, the sender's run its test. */
    fun toProfile(c: CommunityConfig): SettingsProfile = SettingsProfile(
        id = "community-${c.id}",
        name = c.name.trim(),
        titleIds = listOf(c.titleId.uppercase()),
        reason = c.note.trim(),
        settings = c.settings,
        evidence = listOf(ProfileEvidence(
            result = CompatStatus.entries.firstOrNull { it.name == c.result }?.label ?: c.result.take(200),
            build = c.appBuild.ifBlank { "build ${c.appVersionCode}" },
            gpu = c.device.gpu.ifBlank { "unknown GPU" },
            driver = c.device.driver,
            date = c.createdAt,
        )),
    )

    /** Closest phone first, then the best voted, then the newest. */
    fun rank(entries: List<Entry>): List<Entry> = entries.sortedWith(
        compareBy<Entry> { it.match.ordinal }
            .thenByDescending { it.config.votesUp - it.config.votesDown }
            .thenByDescending { it.config.createdAt }
            .thenBy { it.config.id },
    )

    fun match(d: CommunityDevice, f: DeviceFacts?): Match {
        if (f == null) return Match.OTHER
        val gpu = gpuKey(d.gpu)
        val family = gpuFamily(d.gpu)
        return when {
            same(d.model, f.model) && (d.manufacturer.isBlank() || same(d.manufacturer, f.manufacturer)) -> Match.MODEL
            same(d.soc, f.soc) -> Match.SOC
            gpu != null && gpu == gpuKey(f.gpu) -> Match.GPU
            family != null && family == gpuFamily(f.gpu) -> Match.GPU_FAMILY
            else -> Match.OTHER
        }
    }

    private fun same(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() && a.trim().equals(b.trim(), ignoreCase = true)

    /** "Adreno (TM) 740" and "adreno 740" name the same GPU. */
    fun gpuKey(gpu: String?): String? =
        gpu?.lowercase()?.replace("(tm)", " ")?.replace(Regex("[^a-z0-9]+"), " ")?.trim()?.ifEmpty { null }

    /** The GPU's generation (Adreno 7xx, Mali-G7xx, Xclipse 9xx); null when not recognised. */
    fun gpuFamily(gpu: String?): String? {
        val key = gpuKey(gpu) ?: return null
        listOf(Regex("\\b(adreno) (\\d)\\d\\d\\b"), Regex("\\b(mali|immortalis) g(\\d)"), Regex("\\b(xclipse) (\\d)\\d\\d\\b"))
            .forEach { pattern -> pattern.find(key)?.let { return "${it.groupValues[1]} ${it.groupValues[2]}" } }
        return null
    }

    /**
     * What sharing this game's settings would send: only its own settings a profile may carry
     * (C05's allow-list and values), the name and note the player typed (the note through the
     * log redactor: no paths, addresses or e-mails), what they saw, the phone and the app build.
     */
    fun draft(
        titleId: String,
        name: String,
        note: String,
        result: CompatStatus?,
        overrides: Map<String, String>,
        facts: DeviceFacts?,
        appVersionCode: Int,
        appBuild: String,
    ): Draft {
        val shared = overrides.filter { (key, value) -> SettingsProfiles.valueProblem(key, value) == null }.toSortedMap()
        val notShared = overrides.keys.filter { it !in shared }.sorted()
        val device = CommunityDevice(
            manufacturer = facts?.manufacturer.orEmpty().trim().take(MAX_FIELD),
            model = facts?.model.orEmpty().trim().take(MAX_FIELD),
            soc = facts?.soc.orEmpty().trim().take(MAX_FIELD),
            gpu = facts?.gpu.orEmpty().trim().take(MAX_FIELD),
            driver = LogRedactor.redact(facts?.driverLabel.orEmpty().trim()).take(MAX_FIELD),
            androidSdk = facts?.androidSdk?.takeIf { it in 0..99 } ?: 0,
        )
        val cleanName = name.trim()
        val cleanNote = LogRedactor.redact(note.trim())
        val problems = buildSet {
            if (shared.isEmpty()) add(DraftProblem.NOTHING_TO_SHARE)
            if (shared.size > SettingsProfiles.MAX_SETTINGS) add(DraftProblem.TOO_MANY_SETTINGS)
            if (cleanName.isEmpty() || cleanName.length > MAX_NAME) add(DraftProblem.NAME_LENGTH)
            if (cleanNote.isEmpty() || cleanNote.length > MAX_NOTE) add(DraftProblem.NOTE_LENGTH)
            if (result == null) add(DraftProblem.NO_RESULT)
        }
        val upload = if (problems.isNotEmpty() || result == null) null else CommunityUpload(
            titleId = titleId.uppercase(), name = cleanName, note = cleanNote, result = result.name,
            settings = shared, device = device, appVersionCode = appVersionCode.coerceAtLeast(0),
            appBuild = appBuild.trim().take(MAX_FIELD),
        )
        return Draft(shared, notShared, device, problems, upload)
    }

    /** Every field, defaults included: what the consent dialog listed is what goes out. */
    fun encodeUpload(upload: CommunityUpload): String = uploadJson.encodeToString(CommunityUpload.serializer(), upload)

    /** A JSON number (not a string of digits) that fits an Int. */
    private fun number(element: kotlinx.serialization.json.JsonElement?): Int? =
        (element as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    /** The server's answer to a share: the config's id and the token that deletes it. */
    fun parseReceipt(text: String): Receipt? = runCatching {
        val root = json.parseToJsonElement(text) as JsonObject
        val id = (root["id"] as JsonPrimitive).takeIf { it.isString }!!.content
        val token = (root["deleteToken"] as JsonPrimitive).takeIf { it.isString }!!.content
        Receipt(id, token).takeIf { ID.matches(id) && TOKEN.matches(token) }
    }.getOrNull()

    fun parseVotes(text: String): Votes? = runCatching {
        val root = json.parseToJsonElement(text) as JsonObject
        val up = number(root["votesUp"])
        val down = number(root["votesDown"])
        if (up == null || down == null || up !in 0..MAX_VOTES || down !in 0..MAX_VOTES) null else Votes(up, down)
    }.getOrNull()

    /**
     * Who votes, as the server sees it: a hash of a secret kept on this phone and the config, so
     * one vote per phone counts for each config, and the votes of one phone on different configs
     * cannot be linked to each other.
     */
    fun voterId(secret: ByteArray, configId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(secret)
        digest.update(":".toByteArray())
        digest.update(configId.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }

    /** The server's reason for refusing, bounded and on one line, for the message. */
    fun serverReason(text: String): String? = runCatching {
        ((json.parseToJsonElement(text) as JsonObject)["error"] as JsonPrimitive).content
    }.getOrNull()?.replace(Regex("[\\p{Cntrl}]+"), " ")?.trim()?.take(200)?.ifEmpty { null }

    fun isDeleteToken(token: String): Boolean = TOKEN.matches(token)
}
