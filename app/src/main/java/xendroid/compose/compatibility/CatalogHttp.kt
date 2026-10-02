package xendroid.compose.compatibility

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** C04: the catalog download. HTTPS only, never downgraded by a redirect, size-capped; it
 *  sends nothing about the player (a plain GET of one file). */
object CatalogHttp {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .build()
    }

    fun fetch(url: String): ByteArray {
        if (!url.startsWith("https://")) throw IOException("the catalog address is not HTTPS")
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("the catalog server answered ${response.code}")
            val body = response.body ?: throw IOException("the catalog server sent nothing")
            if (body.contentLength() > CompatCatalog.MAX_BYTES) throw IOException("the catalog is too large")
            return body.byteStream().use { readCapped(it, CompatCatalog.MAX_BYTES) }
        }
    }

    /** Reads at most [max] bytes; more is an error, never a silently cut copy. */
    fun readCapped(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > max) throw IOException("the catalog is too large")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
