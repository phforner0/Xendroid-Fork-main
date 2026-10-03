package xendroid.compose.community

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import xendroid.compose.compatibility.CatalogHttp

/** Why a call to the community server failed; the screen says each in its language. */
class CommunityException(val kind: Kind, val detail: String? = null, cause: Throwable? = null) :
    IOException("community server: $kind${detail?.let { ": $it" } ?: ""}", cause) {
    enum class Kind { NETWORK, REJECTED, FORBIDDEN, NOT_FOUND, BUSY, SERVER, INVALID_ANSWER }
}

/**
 * 15b: the community server's protocol v1 (docs/community-configs.md). HTTPS only, no
 * redirects followed, answers size-capped; requests carry only what each call needs.
 */
class CommunityClient(
    baseUrl: String,
    allowLoopbackHttp: Boolean = false,
    private val http: OkHttpClient = defaultHttp,
) {
    private val base: String = CommunityConfigs.baseUrl(baseUrl, allowLoopbackHttp)
        ?: throw IllegalArgumentException("the community server address must be https://")

    /** The configs shared for [titleId] (the raw list, to cache and parse). Sends the Title ID only. */
    fun list(titleId: String): String {
        val title = titleId.uppercase()
        require(CommunityConfigs.isTitleId(title)) { "Invalid Title ID" }
        val request = Request.Builder().url("$base/v1/titles/$title/configs").header("Accept", JSON).build()
        return call(request, CommunityConfigs.MAX_LIST_BYTES)
    }

    fun upload(upload: CommunityUpload): CommunityConfigs.Receipt {
        val request = Request.Builder().url("$base/v1/configs").header("Accept", JSON)
            .post(CommunityConfigs.encodeUpload(upload).toRequestBody(JSON_TYPE)).build()
        return CommunityConfigs.parseReceipt(call(request, MAX_SMALL_ANSWER))
            ?: throw CommunityException(CommunityException.Kind.INVALID_ANSWER)
    }

    /** [vote]: 1 helped, -1 did not, 0 takes the vote back. */
    fun vote(configId: String, voter: String, vote: Int): CommunityConfigs.Votes {
        require(CommunityConfigs.isConfigId(configId) && vote in -1..1 && voter.matches(Regex("[0-9a-f]{32}")))
        val body = """{"voter":"$voter","vote":$vote}"""
        val request = Request.Builder().url("$base/v1/configs/$configId/vote").header("Accept", JSON)
            .post(body.toRequestBody(JSON_TYPE)).build()
        return CommunityConfigs.parseVotes(call(request, MAX_SMALL_ANSWER))
            ?: throw CommunityException(CommunityException.Kind.INVALID_ANSWER)
    }

    fun delete(configId: String, deleteToken: String) {
        require(CommunityConfigs.isConfigId(configId) && CommunityConfigs.isDeleteToken(deleteToken))
        val request = Request.Builder().url("$base/v1/configs/$configId").header("Accept", JSON)
            .header("X-Delete-Token", deleteToken).delete().build()
        call(request, MAX_SMALL_ANSWER)
    }

    private fun call(request: Request, max: Int): String {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw CommunityException(CommunityException.Kind.NETWORK, cause = e)
        }
        response.use {
            val text = try {
                read(it, max)
            } catch (e: IOException) {
                throw CommunityException(if (it.isSuccessful) CommunityException.Kind.INVALID_ANSWER
                    else kindOf(it.code), cause = e)
            }
            if (!it.isSuccessful) throw CommunityException(kindOf(it.code), CommunityConfigs.serverReason(text))
            return text
        }
    }

    private fun read(response: Response, max: Int): String {
        val body = response.body ?: return ""
        if (body.contentLength() > max) throw IOException("the answer is too large")
        return body.byteStream().use { CatalogHttp.readCapped(it, max) }.toString(Charsets.UTF_8)
    }

    private fun kindOf(code: Int): CommunityException.Kind = when (code) {
        400, 409, 413, 422 -> CommunityException.Kind.REJECTED
        401, 403 -> CommunityException.Kind.FORBIDDEN
        404, 410 -> CommunityException.Kind.NOT_FOUND
        429, 503 -> CommunityException.Kind.BUSY
        else -> CommunityException.Kind.SERVER
    }

    companion object {
        private const val JSON = "application/json"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_SMALL_ANSWER = 4 * 1024

        private val defaultHttp by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(40, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
    }
}
