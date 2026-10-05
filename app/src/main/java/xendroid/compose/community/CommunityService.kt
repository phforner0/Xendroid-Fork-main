package xendroid.compose.community

import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.compatibility.DeviceFacts

/**
 * 15b: what the per-game settings screen does with the community server, built only when the
 * build names one. Every call that reaches the network is one the player asked for.
 */
class CommunityService(
    private val client: CommunityClient,
    private val store: CommunityStore,
    /** The server's host, as the screen names it. */
    val server: String,
    private val appVersionCode: Int,
    private val appBuild: String,
) {
    /** What the screen shows for a game, from the last list kept (no network). */
    data class View(
        val fetchedAt: Long?,
        val listing: CommunityConfigs.Listing,
        val myVotes: Map<String, Int>,
        val mine: Map<String, OwnUpload>,
    )

    fun cached(titleId: String, facts: DeviceFacts?): View = view(titleId, store.cached(titleId), facts)

    /** Asks the server again (sends the Title ID). */
    fun refresh(titleId: String, facts: DeviceFacts?): View = view(titleId, store.cache(titleId, client.list(titleId)), facts)

    private fun view(titleId: String, cached: CachedList?, facts: DeviceFacts?): View {
        val listing = cached?.let { CommunityConfigs.parseList(it.text, titleId, facts) }
            ?: CommunityConfigs.Listing(emptyList(), 0, emptyList())
        val mine = store.uploads().filter { it.titleId == titleId.uppercase() }.associateBy { it.configId }
        return View(cached?.fetchedAt, listing, store.votes(), mine)
    }

    fun draft(titleId: String, name: String, note: String, result: CompatStatus?, overrides: Map<String, String>,
              facts: DeviceFacts?): CommunityConfigs.Draft =
        CommunityConfigs.draft(titleId, name, note, result, overrides, facts, appVersionCode, appBuild)

    /** Sends [upload] and keeps its delete token here. */
    fun share(upload: CommunityUpload): OwnUpload =
        store.addUpload(upload.titleId, upload.name, client.upload(upload))

    /** [vote] 1 helped, -1 did not, 0 takes it back. */
    fun vote(configId: String, vote: Int): CommunityConfigs.Votes {
        val votes = client.vote(configId, CommunityConfigs.voterId(store.secret(), configId), vote)
        store.setVote(configId, vote)
        return votes
    }

    /** Deletes a config this phone shared; one the server no longer has is forgotten here too. */
    fun delete(configId: String) {
        val own = store.uploads().firstOrNull { it.configId == configId } ?: return
        try {
            client.delete(configId, own.deleteToken)
        } catch (e: CommunityException) {
            if (e.kind != CommunityException.Kind.NOT_FOUND) throw e
        }
        store.removeUpload(configId)
    }
}
