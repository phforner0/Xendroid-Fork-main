package xendroid.compose.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.dataStore by preferencesDataStore(name = "xendroid_prefs")

/** Persists the real-path (All Files Access) games dir as an absolute host path. */
class PreferencesStore(private val appContext: Context) {

    /** Real-path (All Files Access) games dir. Presence of this key selects a games
     *  folder; absence is NoFolder. */
    private val gameDirPathKey = stringPreferencesKey("game_dir_path")
    private val favoriteIdsKey = stringSetPreferencesKey("favorite_game_ids")
    private val librarySortKey = stringPreferencesKey("library_sort")

    val favoriteIds: Flow<Set<String>> = appContext.dataStore.data.map { it[favoriteIdsKey].orEmpty() }
    val librarySort: Flow<String> = appContext.dataStore.data.map { it[librarySortKey] ?: "NAME_ASC" }

    /** Read-modify-write inside one DataStore transaction (keyed by [Game.identityKey]). */
    suspend fun toggleFavorite(game: Game) {
        appContext.dataStore.edit { prefs ->
            prefs[favoriteIdsKey] = toggledFavorites(game, prefs[favoriteIdsKey].orEmpty())
        }
    }

    suspend fun setLibrarySort(sort: String) {
        appContext.dataStore.edit { it[librarySortKey] = sort }
    }

    /** Adds favorites (a data bundle's import) in one transaction; none is ever removed. */
    suspend fun addFavorites(ids: Set<String>) {
        if (ids.isEmpty()) return
        appContext.dataStore.edit { prefs -> prefs[favoriteIdsKey] = prefs[favoriteIdsKey].orEmpty() + ids }
    }

    /** The persisted real-path games dir (absolute host path), or null. */
    val gameDirPath: Flow<String?> =
        appContext.dataStore.data.map { it[gameDirPathKey] }

    /** Store (or update) the real-path games dir (absolute host path). */
    suspend fun setGameDirPath(path: String) {
        appContext.dataStore.edit {
            it[gameDirPathKey] = path
        }
    }
}
