package xendroid.compose.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** L06: a named group of games the user makes ("RPGs", "Co-op"). Members are [Game.identityKey]s,
 *  so a moved or renamed file stays in its collections. */
@Serializable
data class GameCollection(val name: String, val members: List<String> = emptyList())

/** Pure rules for collections; the list itself lives in DataStore as [encode]d JSON. */
object GameCollections {
    const val MAX_COLLECTIONS = 50
    const val MAX_NAME = 40
    const val MAX_MEMBERS = 5000
    private const val MAX_KEY = 4096

    @Serializable
    private data class Stored(val version: Int = 1, val collections: List<GameCollection> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** A damaged or foreign value reads as no collections; names and members are cleaned. */
    fun decode(stored: String?): List<GameCollection> {
        if (stored.isNullOrBlank()) return emptyList()
        val raw = runCatching { json.decodeFromString(Stored.serializer(), stored).collections }.getOrDefault(emptyList())
        return sanitize(raw)
    }

    fun encode(collections: List<GameCollection>): String =
        json.encodeToString(Stored.serializer(), Stored(collections = sanitize(collections)))

    /** Spaces collapsed, at most [MAX_NAME] characters; null for a blank name. */
    fun cleanName(name: String): String? = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).trim().ifEmpty { null }

    fun find(collections: List<GameCollection>, name: String): GameCollection? =
        collections.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** A new collection, optionally starting with [first]. Names are unique ignoring case. */
    fun create(collections: List<GameCollection>, name: String, first: String? = null): List<GameCollection> {
        val clean = requireNotNull(cleanName(name)) { "Give the collection a name" }
        require(find(collections, clean) == null) { "There is already a collection called \"$clean\"" }
        require(collections.size < MAX_COLLECTIONS) { "At most $MAX_COLLECTIONS collections" }
        return collections + GameCollection(clean, listOfNotNull(first?.takeIf { it.length <= MAX_KEY }))
    }

    /** Removes the collection only; its games stay in the library and in other collections. */
    fun delete(collections: List<GameCollection>, name: String): List<GameCollection> =
        collections.filterNot { it.name.equals(name, ignoreCase = true) }

    fun rename(collections: List<GameCollection>, from: String, to: String): List<GameCollection> {
        val clean = requireNotNull(cleanName(to)) { "Give the collection a name" }
        val other = find(collections, clean)
        require(other == null || other.name.equals(from, ignoreCase = true)) {
            "There is already a collection called \"$clean\""
        }
        return collections.map { if (it.name.equals(from, ignoreCase = true)) it.copy(name = clean) else it }
    }

    /** Puts [key] in or out of the collection [name]; an unknown collection changes nothing. */
    fun setMember(collections: List<GameCollection>, name: String, key: String, member: Boolean): List<GameCollection> =
        collections.map { collection ->
            if (!collection.name.equals(name, ignoreCase = true)) return@map collection
            when {
                member && key !in collection.members -> {
                    require(collection.members.size < MAX_MEMBERS) { "\"${collection.name}\" is full ($MAX_MEMBERS games)" }
                    require(key.length <= MAX_KEY) { "Invalid game" }
                    collection.copy(members = collection.members + key)
                }
                !member -> collection.copy(members = collection.members - key)
                else -> collection
            }
        }

    fun namesOf(collections: List<GameCollection>, key: String): List<String> =
        collections.filter { key in it.members }.map { it.name }

    /** A data bundle's import (L08): collections are matched by name ignoring case, games are
     *  added and never removed, new collections are appended while there is room. */
    fun merged(current: List<GameCollection>, incoming: List<GameCollection>): List<GameCollection> {
        var result = sanitize(current)
        for (collection in sanitize(incoming)) {
            val existing = find(result, collection.name)
            result = if (existing == null) {
                if (result.size >= MAX_COLLECTIONS) continue
                result + collection
            } else {
                result.map { if (it === existing) it.copy(members = (it.members + collection.members).distinct().take(MAX_MEMBERS)) else it }
            }
        }
        return result
    }

    private fun sanitize(collections: List<GameCollection>): List<GameCollection> {
        val seen = HashSet<String>()
        return collections.mapNotNull { collection ->
            val name = cleanName(collection.name) ?: return@mapNotNull null
            if (!seen.add(name.lowercase())) return@mapNotNull null
            GameCollection(name, collection.members.filter { it.isNotBlank() && it.length <= MAX_KEY }.distinct().take(MAX_MEMBERS))
        }.take(MAX_COLLECTIONS)
    }
}
