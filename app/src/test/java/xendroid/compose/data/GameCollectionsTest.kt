package xendroid.compose.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GameCollectionsTest {
    private val halo = "title:4D5307E6:-:0"
    private val braid = "uri:/g/Braid/default.xex"

    @Test fun namesAreCleanUniqueAndBounded() {
        assertEquals("Co-op night", GameCollections.cleanName("  Co-op \t night  "))
        assertEquals(null, GameCollections.cleanName("   "))
        assertEquals(GameCollections.MAX_NAME, GameCollections.cleanName("x".repeat(100))!!.length)
        val one = GameCollections.create(emptyList(), " RPGs ", first = halo)
        assertEquals(listOf(GameCollection("RPGs", listOf(halo))), one)
        val duplicate = assertThrows(CollectionRefusedException::class.java) { GameCollections.create(one, "rpgs") }
        assertEquals("There is already a collection called \"rpgs\"", duplicate.message)
        assertEquals(CollectionRefusedException.Why.DUPLICATE to "rpgs", duplicate.why to duplicate.name)
        assertThrows(IllegalArgumentException::class.java) { GameCollections.create(one, " ") }
        val full = (1..GameCollections.MAX_COLLECTIONS).fold(emptyList<GameCollection>()) { acc, i -> GameCollections.create(acc, "C$i") }
        assertThrows(IllegalArgumentException::class.java) { GameCollections.create(full, "One more") }
    }

    @Test fun gamesGoInAndOutWithoutTouchingOtherCollections() {
        var collections = GameCollections.create(GameCollections.create(emptyList(), "RPGs"), "Co-op")
        collections = GameCollections.setMember(collections, "rpgs", halo, member = true)
        collections = GameCollections.setMember(collections, "RPGs", halo, member = true)   // once
        collections = GameCollections.setMember(collections, "Co-op", braid, member = true)
        collections = GameCollections.setMember(collections, "Missing", braid, member = true) // no such collection
        assertEquals(listOf("RPGs"), GameCollections.namesOf(collections, halo))
        assertEquals(listOf(listOf(halo), listOf(braid)), collections.map { it.members })
        collections = GameCollections.setMember(collections, "RPGs", halo, member = false)
        assertEquals(emptyList<String>(), GameCollections.namesOf(collections, halo))
        // Deleting a collection removes only the group.
        assertEquals(listOf(GameCollection("RPGs")), GameCollections.delete(collections, "CO-OP"))
    }

    @Test fun renamingKeepsMembersAndRefusesATakenName() {
        val collections = GameCollections.setMember(
            GameCollections.create(GameCollections.create(emptyList(), "RPGs"), "Racing"), "RPGs", halo, true)
        assertEquals(listOf("Role-playing", "Racing"), GameCollections.rename(collections, "RPGs", "Role-playing").map { it.name })
        assertEquals(listOf(halo), GameCollections.rename(collections, "RPGs", "Role-playing").first().members)
        assertEquals("rpgs", GameCollections.rename(collections, "RPGs", "rpgs").first().name)   // same, other case
        assertThrows(IllegalArgumentException::class.java) { GameCollections.rename(collections, "RPGs", "racing") }
    }

    @Test fun storedValuesAreReadDefensively() {
        val collections = listOf(GameCollection("RPGs", listOf(halo, halo, "")), GameCollection("rpgs", listOf(braid)),
            GameCollection("  ", listOf(braid)), GameCollection("Co-op", listOf(braid)))
        val stored = GameCollections.encode(collections)
        assertEquals(listOf(GameCollection("RPGs", listOf(halo)), GameCollection("Co-op", listOf(braid))),
            GameCollections.decode(stored))
        assertEquals(emptyList<GameCollection>(), GameCollections.decode("{ damaged"))
        assertEquals(emptyList<GameCollection>(), GameCollections.decode(null))
        assertEquals(listOf(GameCollection("A")), GameCollections.decode("""{"version":7,"collections":[{"name":"A"}],"x":1}"""))
    }

    @Test fun importingABundleAddsGamesAndNeverRemoves() {
        val current = listOf(GameCollection("RPGs", listOf(halo)), GameCollection("Racing"))
        val incoming = listOf(GameCollection("rpgs", listOf(braid, halo)), GameCollection("Co-op", listOf(braid)))
        assertEquals(listOf(GameCollection("RPGs", listOf(halo, braid)), GameCollection("Racing"), GameCollection("Co-op", listOf(braid))),
            GameCollections.merged(current, incoming))
        assertEquals(current, GameCollections.merged(current, emptyList()))
        val full = (1..GameCollections.MAX_COLLECTIONS).map { GameCollection("C$it") }
        assertEquals(full, GameCollections.merged(full, listOf(GameCollection("New"))))
    }
}
