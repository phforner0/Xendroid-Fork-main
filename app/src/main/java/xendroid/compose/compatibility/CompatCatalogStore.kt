package xendroid.compose.compatibility

import java.io.File
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xendroid.compose.archive.ArchiveFiles
import xendroid.compose.archive.withDirectoryLock

/**
 * C04: the kept copy of the catalog and its refresh. The copy is the envelope exactly as
 * downloaded, verified again on every read (one code path, and a damaged file is never
 * trusted); [fetch] is the only network access, and it runs only when asked to refresh.
 */
class CompatCatalogStore(
    private val dir: File,
    private val config: CatalogConfig,
    private val fetch: (String) -> ByteArray,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Serializable
    private data class Kept(val fetchedAt: Long, val envelope: String)

    data class Copy(val payload: CatalogPayload, val fetchedAt: Long, val freshness: CompatCatalog.Freshness)

    sealed interface Refresh {
        data class Updated(val sequence: Long) : Refresh
        /** The publisher still serves the copy already here. */
        data object Unchanged : Refresh
        /** Downloaded but not taken; the copy already here stays. */
        data class Refused(val reason: String) : Refresh
        data class Failed(val reason: String) : Refresh
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val file get() = File(dir, "catalog.json")

    /** The kept copy, unless it is missing, no longer verifies or expired long ago. */
    fun copy(): Copy? = withDirectoryLock(dir) { read() }?.takeIf { it.freshness != CompatCatalog.Freshness.EXPIRED }

    private fun read(): Copy? {
        val kept = runCatching {
            if (!file.isFile || file.length() > CompatCatalog.MAX_BYTES * 2L) null
            else json.decodeFromString(Kept.serializer(), file.readText())
        }.getOrNull() ?: return null
        val verified = CompatCatalog.verify(kept.envelope.toByteArray(), config.url, config.keys, null, clock())
        val payload = (verified as? CompatCatalog.Verified.Ok)?.payload ?: return null
        return Copy(payload, kept.fetchedAt, CompatCatalog.freshness(payload, kept.fetchedAt, clock()))
    }

    fun refresh(): Refresh {
        val bytes = try {
            fetch(config.url)
        } catch (e: IOException) {
            return Refresh.Failed(e.message ?: "no connection")
        }
        return withDirectoryLock(dir) {
            val current = read()
            when (val verified = CompatCatalog.verify(bytes, config.url, config.keys, current?.payload?.sequence, clock())) {
                is CompatCatalog.Verified.Refused -> Refresh.Refused(verified.reason)
                is CompatCatalog.Verified.Ok -> {
                    val unchanged = current != null && verified.payload.sequence == current.payload.sequence
                    // The same publication again only renews the download date of the copy already here.
                    val envelope = if (unchanged) json.decodeFromString(Kept.serializer(), file.readText()).envelope
                        else bytes.toString(Charsets.UTF_8)
                    ArchiveFiles.atomicText(file, json.encodeToString(Kept.serializer(), Kept(clock(), envelope)))
                    if (unchanged) Refresh.Unchanged else Refresh.Updated(verified.payload.sequence)
                }
            }
        }
    }
}
