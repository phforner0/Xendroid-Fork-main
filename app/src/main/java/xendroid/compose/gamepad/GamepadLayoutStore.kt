package xendroid.compose.gamepad

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.DataMigration
import androidx.datastore.core.MultiProcessDataStoreFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import android.util.Log

// Legacy store is read once by migration; all new main/:emu writes use a
// MultiProcessDataStore. A distinct NAME alone did not make the old store multi-process.
private val Context.gamepadStore by preferencesDataStore(name = "xendroid_gamepad")

class GamepadLayoutStore(private val appContext: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("gamepad_config_v1")
    private val store: DataStore<GamepadConfigDto> = stores.computeIfAbsent(appContext.filesDir.absolutePath) {
        MultiProcessDataStoreFactory.create(
            serializer = GamepadConfigSerializer,
            migrations = listOf(object : DataMigration<GamepadConfigDto> {
                override suspend fun shouldMigrate(currentData: GamepadConfigDto) = currentData.version < 2
                override suspend fun migrate(currentData: GamepadConfigDto): GamepadConfigDto {
                    val previous = appContext.gamepadStore.data.first()[key]
                    return previous?.let { runCatching { json.decodeFromString<GamepadConfigDto>(it) }.getOrNull() }
                        ?.copy(version = 2) ?: GamepadConfigDto(version = 2)
                }
                override suspend fun cleanUp() = Unit // retain the old file for rollback
            }),
            produceFile = { File(appContext.filesDir, "datastore/gamepad-runtime-v2.json") },
        )
    }

    val config: Flow<GamepadConfigDto> = store.data.catch {
        if (it is kotlinx.coroutines.CancellationException) throw it
        Log.w("GamepadLayoutStore", "Layout unreadable; previous file preserved", it)
        emit(GamepadConfigDto(version = 2))
    }

    suspend fun save(cfg: GamepadConfigDto) {
        store.updateData { cfg.copy(version = 2) }
    }

    companion object { private val stores = ConcurrentHashMap<String, DataStore<GamepadConfigDto>>() }
}

object GamepadConfigSerializer : Serializer<GamepadConfigDto> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override val defaultValue = GamepadConfigDto(version = 0)
    override suspend fun readFrom(input: InputStream): GamepadConfigDto {
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(bytes.size() + n <= 1024 * 1024) { "Gamepad layout is too large" }
            bytes.write(buffer, 0, n)
        }
        return json.decodeFromString(bytes.toString("UTF-8"))
    }
    override suspend fun writeTo(t: GamepadConfigDto, output: OutputStream) {
        output.write(json.encodeToString(GamepadConfigDto.serializer(), t.copy(version = 2)).toByteArray())
    }
}
