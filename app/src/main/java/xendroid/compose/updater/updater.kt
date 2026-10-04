package xendroid.compose.updater

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.annotations.SerializedName
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import xendroid.compose.BuildConfig

data class GithubAsset(
    @SerializedName("name") val name: String? = null,
    @SerializedName("size") val size: Long = 0,
    @SerializedName("browser_download_url") val downloadUrl: String? = null,
    @SerializedName("digest") val digest: String? = null,
)

data class GithubRelease(
    @SerializedName("name") val name: String? = null,
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val changelog: String? = null,
    @SerializedName("html_url") val releaseUrl: String,
    @SerializedName("prerelease") val prerelease: Boolean = false,
    @SerializedName("draft") val draft: Boolean = false,
    @SerializedName("assets") val assets: List<GithubAsset>? = null,
) {
    fun toFeed() = FeedRelease(tagName, name, changelog, releaseUrl, prerelease, draft,
        assets.orEmpty().mapNotNull { a ->
            val url = a.downloadUrl?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            ReleaseAsset(a.name.orEmpty(), a.size, url, parseAssetDigest(a.digest))
        })
}

sealed class UpdateResult {
    data class Available(val release: FeedRelease) : UpdateResult()
    data class Latest(val commitHash: String) : UpdateResult()
    data class Cooldown(val remainingMillis: Long) : UpdateResult()
}

interface GithubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(@Path("owner") owner: String, @Path("repo") repo: String): GithubRelease

    @GET("repos/{owner}/{repo}/releases")
    suspend fun releases(@Path("owner") owner: String, @Path("repo") repo: String,
                         @Query("per_page") perPage: Int = 10): List<GithubRelease>
}

private const val PREFS_NAME = "updater"
private const val KEY_LAST_CHECK = "last_update_check"
private const val KEY_CHANNEL = "channel"
private const val KEY_SKIPPED = "skipped_version_code"
private const val UPDATE_INTERVAL = 5 * 60 * 1000L

private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

fun getRemainingCooldown(context: Context): Long {
    val lastCheck = prefs(context).getLong(KEY_LAST_CHECK, 0L)
    return (UPDATE_INTERVAL - (System.currentTimeMillis() - lastCheck)).coerceAtLeast(0L)
}

fun shouldCheckForUpdates(context: Context): Boolean =
    System.currentTimeMillis() - prefs(context).getLong(KEY_LAST_CHECK, 0L) >= UPDATE_INTERVAL

/** When the app last looked for an update (null: never). */
fun lastUpdateCheck(context: Context): Long? = prefs(context).getLong(KEY_LAST_CHECK, 0L).takeIf { it > 0 }

fun saveLastCheck(context: Context) {
    prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
}

fun updateChannel(context: Context): UpdateChannel =
    prefs(context).getString(KEY_CHANNEL, null)?.let { runCatching { UpdateChannel.valueOf(it) }.getOrNull() } ?: UpdateChannel.STABLE

fun setUpdateChannel(context: Context, channel: UpdateChannel) {
    prefs(context).edit().putString(KEY_CHANNEL, channel.name).apply()
}

/** R04: this release and older ones are not offered again; a newer one is. */
fun skipVersion(context: Context, versionCode: Int) {
    prefs(context).edit().putInt(KEY_SKIPPED, versionCode).apply()
}

private val http = OkHttpClient.Builder()
    .addInterceptor(
        // Never log response bodies: logcat is shelved into shareable diagnostics.
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
    )
    .build()

private val api = Retrofit.Builder()
    .baseUrl("https://api.github.com/")
    .client(http)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(GithubApi::class.java)

/** The release feed for this build, or null when this package has no published channel. */
fun updateRepository(): Pair<String, String>? {
    if (!BuildConfig.APPLICATION_ID.endsWith(".fork")) return null
    val parts = BuildConfig.UPDATE_REPOSITORY.split('/')
    if (parts.size != 2 || parts.any { it.isBlank() }) return null
    return parts[0] to parts[1]
}

suspend fun checkForUpdates(context: Context): UpdateResult {
    val (owner, repo) = updateRepository() ?: return UpdateResult.Latest(BuildConfig.VERSION_NAME)
    val channel = updateChannel(context)
    if (channel == UpdateChannel.OFF) return UpdateResult.Latest(BuildConfig.VERSION_NAME)
    Log.d("Updater", "Checking $owner/$repo ($channel)")
    val releases = if (channel == UpdateChannel.PREVIEW) api.releases(owner, repo) else listOf(api.latestRelease(owner, repo))
    val skipped = prefs(context).getInt(KEY_SKIPPED, 0).takeIf { it > 0 }
    val offer = chooseUpdate(releases.map { it.toFeed() }, channel, BuildConfig.VERSION_CODE,
        BuildConfig.VERSION_NAME.lowercase(), skipped)
    return offer?.let { UpdateResult.Available(it) } ?: UpdateResult.Latest(BuildConfig.VERSION_NAME)
}

/** Downloads the release's APK, verified against its published size and SHA-256. */
suspend fun downloadUpdate(context: Context, release: FeedRelease, onProgress: (Float) -> Unit): File =
    withContext(Dispatchers.IO) {
        val asset = apkAsset(release) ?: throw UpdateDownloadException(UpdateDownloadException.Reason.NO_APK)
        val sha = asset.sha256 ?: throw UpdateDownloadException(UpdateDownloadException.Reason.NO_SHA256)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // one update at a time
        http.newCall(Request.Builder().url(asset.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw UpdateDownloadException(UpdateDownloadException.Reason.HTTP, response.code.toLong())
            val body = response.body ?: throw UpdateDownloadException(UpdateDownloadException.Reason.EMPTY)
            downloadVerified(body.byteStream(), asset.size, sha, File(dir, "update.apk")) { bytes ->
                onProgress(bytes.toFloat() / asset.size)
            }
        }
    }

/** U02: why the download cannot be used, in the shown language. */
private fun downloadFailureText(context: Context, e: UpdateDownloadException): String = when (e.reason) {
    UpdateDownloadException.Reason.NO_APK -> context.getString(R.string.upd_err_no_apk)
    UpdateDownloadException.Reason.NO_SHA256 -> context.getString(R.string.upd_err_no_sha256)
    UpdateDownloadException.Reason.HTTP -> context.getString(R.string.upd_err_http, e.args[0])
    UpdateDownloadException.Reason.EMPTY -> context.getString(R.string.upd_err_empty)
    UpdateDownloadException.Reason.UNEXPECTED_SIZE -> context.getString(R.string.upd_err_size, e.args[0])
    UpdateDownloadException.Reason.CANCELLED -> context.getString(R.string.upd_err_cancelled)
    UpdateDownloadException.Reason.TOO_LARGE -> context.getString(R.string.upd_err_too_large)
    UpdateDownloadException.Reason.STOPPED_EARLY -> context.getString(R.string.upd_err_stopped_early, e.args[0], e.args[1])
    UpdateDownloadException.Reason.MISMATCH -> context.getString(R.string.upd_err_mismatch)
    UpdateDownloadException.Reason.NOT_KEPT -> context.getString(R.string.upd_err_not_kept)
}

fun cleanChangelog(text: String): String {
    return text
        .replace(Regex("(?s)\\*\\*Full Changelog\\*\\*:.*"), "")
        .replace(Regex("https?://\\S+"), "")
        .replace("## What's Changed", "")
        .replace(Regex("\\* "), "• ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

/** R03/R04: notes, then download + verify + system installer; or skip, later, or the release page. */
@Composable
fun UpdateDialog(
    release: FeedRelease,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val asset = apkAsset(release)
    val verifiable = asset?.sha256 != null

    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text(stringResource(R.string.upd_available)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text((release.title ?: release.tagName) + if (release.prerelease) " " + stringResource(R.string.upd_preview_tag) else "")
                asset?.let {
                    Text("${it.size / (1024 * 1024)} MB · " +
                        if (verifiable) stringResource(R.string.upd_verifiable)
                        else stringResource(R.string.upd_not_verifiable))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(release.notes?.let(::cleanChangelog) ?: stringResource(R.string.upd_no_changelog))
                progress?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { it })
                }
                failure?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(it)
                }
            }
        },
        confirmButton = {
            if (verifiable) TextButton(enabled = progress == null, onClick = {
                failure = null
                progress = 0f
                scope.launch {
                    try {
                        val apk = downloadUpdate(context, release) { progress = it }
                        val refusal = withContext(Dispatchers.IO) { UpdateInstaller.verify(context, apk) }
                        if (refusal != null) {
                            apk.delete()
                            failure = context.getString(R.string.upd_not_installed_why, refusal)
                        } else {
                            UpdateInstaller.install(context, apk)
                            onDismiss()
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        failure = (e as? UpdateDownloadException)?.let { downloadFailureText(context, it) }
                            ?: context.getString(R.string.upd_download_failed, e.message ?: e.javaClass.simpleName)
                    } finally {
                        progress = null
                    }
                }
            }) { Text(stringResource(R.string.upd_download_install)) }
            else TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
                onDismiss()
            }) { Text(stringResource(R.string.upd_release_page)) }
        },
        dismissButton = {
            Column {
                TextButton(enabled = progress == null, onClick = {
                    release.tag?.versionCode?.let { skipVersion(context, it) }
                    onDismiss()
                }) { Text(stringResource(R.string.upd_skip)) }
                TextButton(enabled = progress == null, onClick = onDismiss) { Text(stringResource(R.string.upd_later)) }
            }
        }
    )
}

@Composable
fun LatestVersionDialog(
    commitHash: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upd_none)) },
        text = { Text(stringResource(R.string.upd_latest, commitHash)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } }
    )
}

@Composable
fun CooldownDialog(
    remainingMillis: Long,
    onDismiss: () -> Unit
) {
    val minutes = remainingMillis / 1000 / 60
    val seconds = (remainingMillis / 1000) % 60
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upd_cooldown_title)) },
        text = { Text(stringResource(R.string.upd_cooldown, minutes, seconds)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } }
    )
}
