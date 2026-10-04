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

    /** L06: the user's collections (JSON, see [GameCollections]). */
    private val collectionsKey = stringPreferencesKey("game_collections")

    val collections: Flow<List<GameCollection>> = appContext.dataStore.data.map { GameCollections.decode(it[collectionsKey]) }

    /** Read-modify-write in one DataStore transaction; [change] may throw to refuse (nothing is written). */
    suspend fun editCollections(change: (List<GameCollection>) -> List<GameCollection>) {
        appContext.dataStore.edit { prefs ->
            prefs[collectionsKey] = GameCollections.encode(change(GameCollections.decode(prefs[collectionsKey])))
        }
    }

    /** A data bundle's collections: games are added, never removed (L08). */
    suspend fun mergeCollections(incoming: List<GameCollection>) {
        if (incoming.isNotEmpty()) editCollections { GameCollections.merged(it, incoming) }
    }

    /** L03: every games folder, in the user's order; an older install's single folder comes first. */
    private val gameDirPathsKey = stringPreferencesKey("game_dir_paths")

    val gameDirPaths: Flow<List<String>> =
        appContext.dataStore.data.map { LibraryRoots.decode(it[gameDirPathsKey], it[gameDirPathKey]) }

    /** Adds a games folder (absolute host path); one already listed is not added twice. */
    suspend fun addGameDirPath(path: String) {
        appContext.dataStore.edit { prefs ->
            val roots = LibraryRoots.decode(prefs[gameDirPathsKey], prefs[gameDirPathKey])
            prefs[gameDirPathsKey] = LibraryRoots.encode(LibraryRoots.add(roots, path))
        }
    }

    /** Stops scanning a folder; its files are never touched. */
    suspend fun removeGameDirPath(path: String) {
        appContext.dataStore.edit { prefs ->
            val roots = LibraryRoots.decode(prefs[gameDirPathsKey], prefs[gameDirPathKey])
            prefs[gameDirPathsKey] = LibraryRoots.encode(LibraryRoots.remove(roots, path))
        }
    }
}
