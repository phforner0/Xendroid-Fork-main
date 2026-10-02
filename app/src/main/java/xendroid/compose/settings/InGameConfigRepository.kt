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

    /** Whether guest vblanks are capped at 50/60 Hz for [titleId]: its own setting, else the
     *  global one (on by default). Read when a run starts, as the core read it at boot. */
    fun guestRefreshCap(titleId: String?): Boolean {
        val game = titleId?.let { id ->
            val handle = store.openGameConfig(id)
            try { handle.getString("GPU", "guest_display_refresh_cap")?.let { ConfigValueShape.parseBool(it, true) } }
            finally { handle.closeDiscard() }
        }
        if (game != null) return game
        val global = store.openLiveSnapshot()
        return try { global.getBool("GPU", "guest_display_refresh_cap", true) } finally { global.closeDiscard() }
    }

    /** U01: the Vulkan driver setting for [titleId] ("" = the system driver): its own, else the global one. */
    fun driverPath(titleId: String?): String {
        val game = titleId?.let { id ->
            val handle = store.openGameConfig(id)
            try { handle.getString("Vulkan", "vulkan_lib_path") } finally { handle.closeDiscard() }
        }
        if (game != null) return game
        val global = store.openLiveSnapshot()
        return try { global.getString("Vulkan", "vulkan_lib_path").orEmpty() } finally { global.closeDiscard() }
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
