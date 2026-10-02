package xendroid.compose.data

import kotlinx.serialization.Serializable

/**
 * One library entry. [launchUri] is the per-format absolute host path handed to the emulator:
 *  - ISO/ZAR: the file's own absolute path
 *  - XEX folder: the default.xex CHILD path (NOT the folder path)
 *  - GOD: the container path
 * [iconCacheName] is the cacheDir filename of the extracted PNG blob, or null
 * (-> app_icon fallback). GOD reads the icon from the container header; ISO and
 * XEX folders extract it from default.xex's XDBF resource. ZAR has no icon.
 */
@Serializable
data class Game(
    val launchUri: String,
    val name: String,
    val format: GameFormat,
    val iconCacheName: String? = null,
    val titleId: String? = null,
    val mediaId: String? = null,
    /** 1-based; 0 when the header does not state it. */
    val discNumber: Int = 0,
    /** 0 when the header does not state it. */
    val discCount: Int = 0,
) {
    /** Part of a multi-disc set, per its own header. */
    val isMultiDisc: Boolean get() = discCount > 1
    /** Stable id derived from the launch uri (used for shortcut ids, list keys). */
    val stableId: String get() = launchUri

    /**
     * Identity that survives moving or renaming the file: Title ID + Media ID + disc when
     * the header states them; the launch URI only for games without a readable Title ID.
     * Used for user metadata (favorites), never for locating the file.
     */
    val identityKey: String get() = titleId?.takeIf { it.isNotBlank() && it != "00000000" }
        ?.let { "title:${it.uppercase()}:${mediaId?.uppercase()?.ifBlank { null } ?: "-"}:$discNumber" }
        ?: "uri:$launchUri"
}

/** Favorites written before [Game.identityKey] existed hold the raw launch URI. */
fun isFavorite(game: Game, favorites: Set<String>): Boolean =
    game.identityKey in favorites || game.launchUri in favorites

/** New favorite set after toggling [game]; a legacy URI entry is replaced, not duplicated. */
fun toggledFavorites(game: Game, favorites: Set<String>): Set<String> =
    if (isFavorite(game, favorites)) favorites - game.identityKey - game.launchUri
    else favorites + game.identityKey
