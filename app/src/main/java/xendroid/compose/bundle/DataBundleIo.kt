package xendroid.compose.bundle

import android.content.Context
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import xendroid.compose.Application
import xendroid.compose.BuildConfig
import xendroid.compose.compatibility.CompatibilityStore
import xendroid.compose.core.EmulatorRuntime
import xendroid.compose.data.PreferencesStore
import xendroid.compose.gamepad.GamepadConfigDto
import xendroid.compose.gamepad.GamepadLayoutStore
import xendroid.compose.settings.ConfigFileTransaction
import xendroid.compose.settings.ConfigHandle
import xendroid.compose.settings.ConfigStore

/** Android side of [DataBundles]: where each part lives, export/import through SAF, backups. */
object DataBundleIo {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val gameConfigName = Regex("([0-9A-Fa-f]{8})\\.config\\.toml")
    private const val KEEP_BACKUPS = 5

    /**
     * Paths that only mean something on the device that wrote them: storage roots
     * (content_root redirects saves), the custom driver library, trace/sound files.
     * They never leave in a bundle, and an import keeps this device's own values.
     */
    private val devicePaths = listOf("Storage" to "cache_root", "Storage" to "content_root", "Storage" to "storage_root",
        "Vulkan" to "vulkan_lib_path", "CPU" to "trace_function_data_path", "General" to "notification_sound_path")

    /** [text] with the device paths taken from [local] (removed when [local] has none). */
    private fun withPathsOf(text: String, local: String?): String {
        val values = local?.let { source ->
            val handle = ConfigHandle.openString(source)
            try { devicePaths.associateWith { (section, name) -> handle.getString(section, name) } } finally { handle.closeDiscard() }
        }.orEmpty()
        val handle = ConfigHandle.openString(text)
        try {
            devicePaths.forEach { key ->
                val value = values[key]
                if (value != null) handle.putString(key.first, key.second, value) else handle.remove(key.first, key.second)
            }
        } catch (e: Exception) {
            handle.closeDiscard()
            throw e
        }
        return handle.closeString()
    }

    private fun compatibility() = CompatibilityStore(File(Application.get_internal_data_dir(), "compatibility"))
    private fun backupDir(context: Context) = File(context.filesDir, "data-bundles")

    /** The current settings, layout and library metadata, as a bundle. */
    suspend fun snapshot(context: Context): DataBundle = withContext(Dispatchers.IO) {
        val config = ConfigStore(context)
        val global = config.globalConfigFile()
        val games = File(global.parentFile, "config").listFiles().orEmpty()
            .mapNotNull { f -> gameConfigName.matchEntire(f.name)?.let { it.groupValues[1].uppercase() to f } }
            .filter { (id, f) -> id != "00000000" && f.isFile && f.length() <= 1024 * 1024 }
            .associate { (id, f) -> id to f.readText() }
        val prefs = PreferencesStore(context)
        val layout = GamepadLayoutStore(context).config.first()
        DataBundle(
            globalConfig = global.takeIf { it.isFile && it.length() <= 1024 * 1024 }?.readText(),
            gameConfigs = games,
            gamepadLayout = json.encodeToString(GamepadConfigDto.serializer(), layout),
            favorites = prefs.favoriteIds.first(),
            librarySort = prefs.librarySort.first(),
            compatibility = compatibility().all(),
        )
    }

    /** Writes the current state to [uri], without device paths; returns how many parts went in. */
    suspend fun export(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        EmulatorRuntime.ensureLoaded()
        val current = snapshot(context)
        val bundle = current.copy(
            globalConfig = current.globalConfig?.let { withPathsOf(it, null) },
            gameConfigs = current.gameConfigs.mapValues { (_, text) -> withPathsOf(text, null) },
        )
        context.contentResolver.openOutputStream(uri, "w")?.use {
            DataBundles.write(it, bundle, BuildConfig.VERSION_NAME, System.currentTimeMillis())
        } ?: throw BundleException("Cannot write to the chosen file")
        parts(bundle)
    }

    /** Reads and validates [uri] and compares it with the current state; nothing is written. */
    suspend fun preview(context: Context, uri: Uri): Pair<DataBundle, ImportPlan> = withContext(Dispatchers.IO) {
        val incoming = context.contentResolver.openInputStream(uri)?.use(DataBundles::read)
            ?: throw BundleException("Cannot read the chosen file")
        validate(incoming)
        incoming to DataBundles.plan(snapshot(context), incoming)
    }

    /**
     * Applies [incoming] (already previewed). Every part is validated before anything is
     * written, and the current state is saved first as a backup bundle the user can
     * import to go back. Returns the backup's name.
     */
    suspend fun import(context: Context, incoming: DataBundle): String = withContext(Dispatchers.IO) {
        validate(incoming)
        val current = snapshot(context)
        val backup = writeBackup(context, current)
        val config = ConfigStore(context)
        // Under each file's lock: this device's paths are re-read from the file being replaced.
        incoming.globalConfig?.let { text ->
            ConfigFileTransaction.update(config.globalConfigFile()) { original -> withPathsOf(text, original) }
        }
        incoming.gameConfigs.forEach { (id, text) ->
            ConfigFileTransaction.update(config.perGameConfigFile(id)) { original -> withPathsOf(text, original) }
        }
        incoming.gamepadLayout?.let {
            GamepadLayoutStore(context).save(json.decodeFromString(GamepadConfigDto.serializer(), it))
        }
        val prefs = PreferencesStore(context)
        prefs.addFavorites(incoming.favorites)
        incoming.librarySort?.let { prefs.setLibrarySort(it) }
        val store = compatibility()
        val merged = DataBundles.merged(current, incoming)
        incoming.compatibility.keys.forEach { id -> merged.compatibility[id]?.let { store.replace(id, it) } }
        backup.name
    }

    /** TOML through the emulator's own parser and the layout through its schema, before any write. */
    private fun validate(bundle: DataBundle) {
        EmulatorRuntime.ensureLoaded()
        val tomls = listOfNotNull(bundle.globalConfig?.let { "emulator settings" to it }) +
            bundle.gameConfigs.map { (id, text) -> "settings of $id" to text }
        tomls.forEach { (what, text) ->
            val handle = try {
                ConfigHandle.openString(text)
            } catch (e: Exception) {
                throw BundleException("The $what in the bundle are not valid TOML")
            }
            handle.closeDiscard()
        }
        bundle.gamepadLayout?.let {
            runCatching { json.decodeFromString(GamepadConfigDto.serializer(), it) }
                .getOrElse { throw BundleException("The touch control layout in the bundle is damaged") }
        }
    }

    private fun writeBackup(context: Context, current: DataBundle): File {
        val dir = backupDir(context).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "backup-$stamp.zip")
        val temporary = File(dir, ".${file.name}.tmp")
        try {
            temporary.outputStream().use { DataBundles.write(it, current, BuildConfig.VERSION_NAME, System.currentTimeMillis()) }
            check(temporary.renameTo(file)) { "Could not keep a backup of the current settings" }
        } finally {
            temporary.delete()
        }
        dir.listFiles { f -> f.name.startsWith("backup-") && f.name.endsWith(".zip") }.orEmpty()
            .sortedByDescending { it.name }.drop(KEEP_BACKUPS).forEach { it.delete() }
        return file
    }

    private fun parts(bundle: DataBundle): Int =
        (if (bundle.globalConfig != null) 1 else 0) + bundle.gameConfigs.size + (if (bundle.gamepadLayout != null) 1 else 0) +
            (if (bundle.favorites.isNotEmpty() || bundle.librarySort != null) 1 else 0) + bundle.compatibility.size
}
