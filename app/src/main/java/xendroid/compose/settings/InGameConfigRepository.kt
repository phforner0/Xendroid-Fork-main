package xendroid.compose.settings

data class FpsConfigSnapshot(
    val titleId: String? = null,
    val globalLimit: Int? = null,
    val gameLimit: Int? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

/** Persistent settings only. Applying live changes remains the host/session's responsibility. */
class InGameConfigRepository(private val store: ConfigStore) {
    fun fpsSnapshot(titleId: String?): FpsConfigSnapshot {
        val global = store.openLiveSnapshot()
        val globalLimit = try { global.getInt("GPU", "framerate_limit", 60).coerceAtLeast(0) }
            finally { global.closeDiscard() }
        val gameLimit = if (titleId == null) null else {
            val game = store.openGameConfig(titleId)
            try { game.getString("GPU", "framerate_limit")?.let { ConfigValueShape.parseInt(it, 60).coerceAtLeast(0) } }
            finally { game.closeDiscard() }
        }
        return FpsConfigSnapshot(titleId, globalLimit, gameLimit)
    }

    fun saveGameFps(titleId: String, limit: Int) {
        require(limit >= 0)
        store.editGameConfig(titleId) { it.putInt("GPU", "framerate_limit", limit) }
    }

    fun saveGlobalFps(limit: Int) {
        require(limit >= 0)
        store.editLiveConfig { it.putInt("GPU", "framerate_limit", limit) }
    }

    /** Remove just the FPS override, preserving all other game settings. */
    fun inheritGlobalFps(titleId: String): Int {
        val global = fpsSnapshot(null).globalLimit!! // validate before editing the game file
        store.editGameConfig(titleId) { it.remove("GPU", "framerate_limit") }
        return global
    }
}
