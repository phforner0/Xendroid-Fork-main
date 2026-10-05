package xendroid.compose.compatibility

import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What a publisher signs: [payload] is the base64 of the exact bytes [signature] covers. */
@Serializable
data class CatalogEnvelope(val format: String, val version: Int, val keyId: String, val payload: String, val signature: String)

/** [count] reports with the same result on one build, GPU and driver. */
@Serializable
data class CatalogReport(
    val status: CompatStatus,
    val build: String,
    val gpu: String,
    val driver: String = "",
    val android: Int? = null,
    val date: String,
    val note: String = "",
    val count: Int = 1,
)

@Serializable
data class CatalogPayload(
    val format: String,
    val version: Int,
    /** The catalog URL it was signed for: a copy served from anywhere else is refused. */
    val origin: String,
    /** Grows with every publication: an older one is never taken over a newer copy. */
    val sequence: Long,
    val generatedAt: Long,
    val expiresAt: Long,
    val titles: Map<String, List<CatalogReport>> = emptyMap(),
)

/** A catalog this build trusts: the HTTPS [url] and the publisher's P-256 keys by key id. */
data class CatalogConfig(val url: String, val keys: Map<String, PublicKey>) {
    companion object {
        /**
         * From the build ([url] and "keyId:base64 X.509 key,..." [keys]); null when either is
         * empty (no publisher: the catalog is off) or anything is malformed.
         */
        fun parse(url: String, keys: String): CatalogConfig? {
            if (url.isBlank() || keys.isBlank()) return null
            if (!url.startsWith("https://") || url.length > 512 || url.any { it.isWhitespace() }) return null
            val parsed = keys.split(',').map { it.trim() }.filter { it.isNotEmpty() }.associate { entry ->
                val id = entry.substringBefore(':', "")
                val key = CompatCatalog.publicKey(entry.substringAfter(':', ""))
                if (id.isBlank() || id.length > 32 || key == null) return null
                id to key
            }
            return parsed.takeIf { it.isNotEmpty() }?.let { CatalogConfig(url, it) }
        }
    }
}

/**
 * C04: the read-only compatibility catalog, signed and versioned. A copy is taken only when
 * the signature of a key this build pins matches, it was signed for this very URL, and it is
 * not older than the copy already kept (no rollback). It stays usable offline: fresh until the
 * publisher's expiry or [MAX_FRESH_MS] after download, then shown as out of date, then
 * dropped. Results are never merged across builds, GPUs or drivers: each setup is its own
 * group, this phone's first. Pure, so every rule is tested on the JVM.
 */
object CompatCatalog {
    const val ENVELOPE_FORMAT = "xendroid-catalog-envelope"
    const val FORMAT = "xendroid-compat-catalog"
    const val VERSION = 1
    const val MAX_BYTES = 4 * 1024 * 1024
    const val MAX_TITLES = 20_000
    const val MAX_REPORTS_PER_TITLE = 50
    /** However late the publisher's expiry, a copy older than this is out of date. */
    const val MAX_FRESH_MS = 7L * 24 * 3600 * 1000
    /** Out of date for longer than this, a copy is not shown at all. */
    const val MAX_STALE_MS = 90L * 24 * 3600 * 1000
    /** A catalog dated further ahead than this is refused (a wrong clock, or a forged date). */
    private const val MAX_CLOCK_SKEW_MS = 24L * 3600 * 1000

    private val json = Json { ignoreUnknownKeys = false; isLenient = false }
    private val TITLE = Regex("[0-9A-F]{8}")
    private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    /** The P-256 field prime: the only curve accepted. */
    private val P256 = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)

    sealed interface Verified {
        data class Ok(val payload: CatalogPayload, val keyId: String) : Verified
        data class Refused(val reason: String) : Verified
    }

    enum class Freshness { FRESH, STALE, EXPIRED }

    /** A P-256 public key from base64 X.509 (SubjectPublicKeyInfo); null for anything else. */
    fun publicKey(base64: String): PublicKey? = runCatching {
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64.trim())))
        val field = (key as ECPublicKey).params.curve.field
        key.takeIf { field is ECFieldFp && field.p == P256 }
    }.getOrNull()

    /**
     * Checks [bytes] (an envelope) for [origin] against the pinned [keys]; [minSequence] is the
     * sequence of the copy already kept, if any.
     */
    fun verify(bytes: ByteArray, origin: String, keys: Map<String, PublicKey>, minSequence: Long?, now: Long): Verified {
        if (bytes.size > MAX_BYTES) return Verified.Refused("larger than ${MAX_BYTES / (1024 * 1024)} MB")
        val envelope = runCatching { json.decodeFromString(CatalogEnvelope.serializer(), bytes.toString(Charsets.UTF_8)) }
            .getOrElse { return Verified.Refused("not a catalog") }
        if (envelope.format != ENVELOPE_FORMAT || envelope.version != VERSION) {
            return Verified.Refused("catalog format version ${envelope.version} is not supported by this build")
        }
        val key = keys[envelope.keyId] ?: return Verified.Refused("signed with a key this build does not trust")
        val payloadBytes = runCatching { Base64.getDecoder().decode(envelope.payload) }.getOrElse { return Verified.Refused("damaged payload") }
        val signature = runCatching { Base64.getDecoder().decode(envelope.signature) }.getOrElse { return Verified.Refused("damaged signature") }
        val valid = runCatching {
            Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(payloadBytes); verify(signature) }
        }.getOrDefault(false)
        if (!valid) return Verified.Refused("the signature does not match")
        // Only signed bytes are parsed from here on.
        val payload = runCatching { json.decodeFromString(CatalogPayload.serializer(), payloadBytes.toString(Charsets.UTF_8)) }
            .getOrElse { return Verified.Refused("the signed content is not a catalog this build reads") }
        if (payload.format != FORMAT || payload.version != VERSION) {
            return Verified.Refused("catalog content version ${payload.version} is not supported by this build")
        }
        if (payload.origin != origin) return Verified.Refused("signed for another catalog (${payload.origin.take(80)})")
        if (minSequence != null && payload.sequence < minSequence) {
            return Verified.Refused("older than the copy already here (${payload.sequence} < $minSequence)")
        }
        if (payload.generatedAt > now + MAX_CLOCK_SKEW_MS) return Verified.Refused("dated in the future; check the phone's clock")
        if (payload.expiresAt <= payload.generatedAt) return Verified.Refused("expires before it was made")
        contentProblem(payload)?.let { return Verified.Refused(it) }
        return Verified.Ok(payload.copy(titles = payload.titles.mapKeys { it.key.uppercase() }), envelope.keyId)
    }

    private fun contentProblem(p: CatalogPayload): String? {
        if (p.titles.size > MAX_TITLES) return "more than $MAX_TITLES titles"
        for ((title, reports) in p.titles) {
            val id = title.uppercase()
            if (!TITLE.matches(id) || id == "00000000") return "invalid Title ID ${title.take(16)}"
            if (reports.size > MAX_REPORTS_PER_TITLE) return "more than $MAX_REPORTS_PER_TITLE results for $id"
            reports.forEach { r ->
                if (r.build.isBlank() || r.build.length > 128 || r.gpu.isBlank() || r.gpu.length > 128 ||
                    r.driver.length > 200 || r.note.length > 500 || !DATE.matches(r.date) || r.count !in 1..100_000 ||
                    (r.android != null && r.android !in 21..99)) return "a damaged result for $id"
            }
        }
        return null
    }

    /** Fresh until the publisher's expiry (at most [MAX_FRESH_MS] after download), then out of
     *  date for [MAX_STALE_MS], then gone. */
    fun freshness(payload: CatalogPayload, fetchedAt: Long, now: Long): Freshness {
        val freshUntil = minOf(payload.expiresAt, fetchedAt + MAX_FRESH_MS)
        return when {
            now <= freshUntil -> Freshness.FRESH
            now <= freshUntil + MAX_STALE_MS -> Freshness.STALE
            else -> Freshness.EXPIRED
        }
    }

    /** Results of one setup: a build, a GPU and a driver; never merged with another. */
    data class SetupResults(
        val build: String,
        val gpu: String,
        val driver: String,
        val counts: Map<CompatStatus, Int>,
        val latestDate: String,
        val notes: List<String>,
        /** Same build and GPU as this phone. */
        val thisSetup: Boolean,
        val sameGpu: Boolean,
    ) {
        /** "Playable ×3 · In-game with problems ×1", best result first. */
        val summary: String get() = counts.entries.sortedByDescending { it.key.ordinal }
            .joinToString(" · ") { (status, n) -> "${status.label} ×$n" }
    }

    /** [titleId]'s results grouped by setup: this build and GPU first, then this GPU, then the newest. */
    fun resultsFor(payload: CatalogPayload, titleId: String, build: String, gpu: String?): List<SetupResults> {
        val reports = payload.titles[titleId.uppercase()].orEmpty()
        fun norm(s: String) = s.trim().lowercase()
        return reports.groupBy { Triple(it.build.trim(), norm(it.gpu), norm(it.driver)) }.map { (key, group) ->
            val sameGpu = gpu != null && key.second == norm(gpu)
            SetupResults(
                build = key.first, gpu = group.first().gpu.trim(), driver = group.first().driver.trim(),
                counts = group.groupBy { it.status }.mapValues { (_, list) -> list.sumOf { it.count } },
                latestDate = group.maxOf { it.date },
                notes = group.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct().take(3),
                thisSetup = sameGpu && key.first == build.trim(),
                sameGpu = sameGpu,
            )
        }.sortedWith(compareByDescending<SetupResults> { it.thisSetup }.thenByDescending { it.sameGpu }.thenByDescending { it.latestDate })
    }
}
