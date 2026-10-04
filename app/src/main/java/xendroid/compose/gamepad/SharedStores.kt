package xendroid.compose.gamepad

import android.util.Log
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import java.io.File

/**
 * Stores both processes write (the touch layouts, the control options). A multi-process store
 * needs its native counter; where that library cannot load (the JVM of the screenshot tests,
 * a broken install) a one-process store keeps the app working instead of failing every write.
 */
internal object SharedStores {
    val multiProcess: Boolean by lazy {
        runCatching { System.loadLibrary("datastore_shared_counter") }
            .onFailure { Log.w("SharedStores", "No multi-process store here; using a one-process one", it) }
            .isSuccess
    }

    fun <T> create(serializer: Serializer<T>, migrations: List<DataMigration<T>> = emptyList(), file: () -> File): DataStore<T> =
        if (multiProcess) MultiProcessDataStoreFactory.create(serializer = serializer, migrations = migrations, produceFile = file)
        else DataStoreFactory.create(serializer = serializer, migrations = migrations, produceFile = file)
}
