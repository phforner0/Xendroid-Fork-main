package xendroid.compose.community

import java.io.File
import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import xendroid.compose.archive.ArchiveFiles
import xendroid.compose.archive.withDirectoryLock

/** A config this phone shared, with the token that deletes it (kept only here). */
@Serializable
data class OwnUpload(val configId: String, val titleId: String, val name: String, val deleteToken: String, val uploadedAt: Long)

/** The last list the server sent for a game, shown until the player searches again. */
@Serializable
data class CachedList(val titleId: String, val fetchedAt: Long, val text: String)

/**
 * 15b bookkeeping, private files (tested on the JVM): the secret votes are derived from, the
 * configs this phone shared and their delete tokens, its votes, and the last list per game.
 * Nothing here is sent anywhere as is.
 */
class CommunityStore(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val title = Regex("[0-9A-F]{8}")
    private val uploadsList = ListSerializer(OwnUpload.serializer())
    private val votesMap = MapSerializer(String.serializer(), Int.serializer())

    /** 32 random bytes made on first use; votes are hashes of it (see [CommunityConfigs.voterId]). */
    fun secret(): ByteArray = withDirectoryLock(dir) {
        val file = File(dir, SECRET)
        val known = runCatching { file.readText().trim() }.getOrNull()
            ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
        if (known != null) {
            known.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } else {
            ByteArray(32).also { bytes ->
                random.nextBytes(bytes)
                ArchiveFiles.atomicText(file, bytes.joinToString("") { "%02x".format(it) })
            }
        }
    }

    fun uploads(): List<OwnUpload> = withDirectoryLock(dir) { readUploads() }

    fun addUpload(titleId: String, name: String, receipt: CommunityConfigs.Receipt): OwnUpload = withDirectoryLock(dir) {
        val upload = OwnUpload(receipt.id, titleId.uppercase(), name.take(CommunityConfigs.MAX_NAME), receipt.deleteToken, clock())
        val list = (readUploads().filter { it.configId != receipt.id } + upload).takeLast(MAX_UPLOADS)
        ArchiveFiles.atomicText(File(dir, UPLOADS), json.encodeToString(uploadsList, list))
        upload
    }

    fun removeUpload(configId: String) = withDirectoryLock(dir) {
        val list = readUploads()
        if (list.any { it.configId == configId }) {
            ArchiveFiles.atomicText(File(dir, UPLOADS), json.encodeToString(uploadsList, list.filter { it.configId != configId }))
        }
    }

    fun votes(): Map<String, Int> = withDirectoryLock(dir) { readVotes() }

    /** [vote] 0 forgets it. The newest [MAX_VOTES] are kept. */
    fun setVote(configId: String, vote: Int) = withDirectoryLock(dir) {
        val votes = LinkedHashMap(readVotes())
        votes.remove(configId)
        if (vote != 0) votes[configId] = vote.coerceIn(-1, 1)
        val kept = votes.entries.toList().takeLast(MAX_VOTES).associate { it.key to it.value }
        ArchiveFiles.atomicText(File(dir, VOTES), json.encodeToString(votesMap, kept))
    }

    fun cached(titleId: String): CachedList? {
        val file = listFile(titleId)
        return withDirectoryLock(dir) {
            runCatching {
                if (!file.isFile || file.length() > CommunityConfigs.MAX_LIST_BYTES * 2L) null
                else json.decodeFromString(CachedList.serializer(), file.readText()).takeIf { it.titleId == titleId.uppercase() }
            }.getOrNull()
        }
    }

    fun cache(titleId: String, text: String): CachedList {
        val list = CachedList(titleId.uppercase(), clock(), text)
        val file = listFile(titleId)
        withDirectoryLock(dir) { ArchiveFiles.atomicText(file, json.encodeToString(CachedList.serializer(), list)) }
        return list
    }

    private fun listFile(titleId: String): File {
        val t = titleId.uppercase()
        require(title.matches(t) && t != "00000000") { "Invalid Title ID" }
        return File(File(dir, LISTS), "$t.json")
    }

    private fun readUploads(): List<OwnUpload> = runCatching {
        val file = File(dir, UPLOADS)
        if (!file.isFile || file.length() > 1024 * 1024) emptyList()
        else json.decodeFromString(uploadsList, file.readText())
            .filter { CommunityConfigs.isConfigId(it.configId) && CommunityConfigs.isDeleteToken(it.deleteToken) }
    }.getOrDefault(emptyList())

    private fun readVotes(): Map<String, Int> = runCatching {
        val file = File(dir, VOTES)
        if (!file.isFile || file.length() > 1024 * 1024) emptyMap()
        else json.decodeFromString(votesMap, file.readText()).filter { (id, v) -> CommunityConfigs.isConfigId(id) && v in -1..1 && v != 0 }
    }.getOrDefault(emptyMap())

    companion object {
        const val MAX_UPLOADS = 500
        const val MAX_VOTES = 2000
        private const val SECRET = "secret"
        private const val UPLOADS = "uploads.json"
        private const val VOTES = "votes.json"
        private const val LISTS = "lists"
    }
}
