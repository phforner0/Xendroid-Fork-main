package xendroid.compose.shots

import xendroid.compose.data.Game
import xendroid.compose.data.GameFormat

/**
 * The example library of the prints: titles and Title IDs from the bundled patches and
 * GAME_COMPAT.md (as in the HTML prototype); media IDs, play times, ratings and session numbers
 * are made up.
 */
data class SampleGame(
    val titleId: String,
    val name: String,
    val format: GameFormat,
    val path: String,
    /** Background, middle and accent colours of the generated art. */
    val palette: Triple<Int, Int, Int>,
    val motif: String,
    /** The player picked a cover (else only the game's own 64 px icon). */
    val customCover: Boolean,
    val favorite: Boolean = false,
    val collections: List<String> = emptyList(),
    /** Recency rank (1 = last played), 0 = never played. */
    val lastRank: Int = 0,
    val playedMinutes: Int = 0,
    val runs: Int = 0,
    val discCount: Int = 1,
    val mediaId: String = "00000000",
) {
    fun game(iconCacheName: String?): Game = Game(
        launchUri = path, name = name, format = format, iconCacheName = iconCacheName, titleId = titleId,
        mediaId = mediaId, discNumber = if (discCount > 1) 1 else 0, discCount = if (discCount > 1) discCount else 0,
    )
}

object SampleLibrary {
    private const val ROOT = "/storage/emulated/0/Games/Xbox 360"
    val collections = listOf("Co-op local", "Corrida", "Zerar em 2026", "Arcade")

    val games: List<SampleGame> = listOf(
        SampleGame("4D5307E6", "Halo 3", GameFormat.ISO, "$ROOT/Halo 3.iso", Triple(0xFF081F2B.toInt(), 0xFF1D6F86.toInt(), 0xFFF0C96A.toInt()),
            "rings", customCover = true, favorite = true, collections = listOf("Co-op local"), lastRank = 1, playedMinutes = 872, runs = 23, mediaId = "7A1C3E52"),
        SampleGame("4D5309C9", "Forza Horizon", GameFormat.ISO, "$ROOT/Forza Horizon.iso", Triple(0xFF260D06.toInt(), 0xFFC4501C.toInt(), 0xFFFFD27A.toInt()),
            "sun", customCover = true, favorite = true, collections = listOf("Corrida"), lastRank = 2, playedMinutes = 395, runs = 31, mediaId = "2B90D4A1"),
        SampleGame("5454082B", "Red Dead Redemption", GameFormat.ISO, "$ROOT/Red Dead Redemption (Disc 1).iso", Triple(0xFF2A1005.toInt(), 0xFF8E3A14.toInt(), 0xFFF2B35A.toInt()),
            "mountains", customCover = true, favorite = true, collections = listOf("Zerar em 2026"), lastRank = 4, playedMinutes = 1934, runs = 41, discCount = 2, mediaId = "D41E0C77"),
        SampleGame("544307D5", "Ninja Gaiden II", GameFormat.ISO, "$ROOT/Ninja Gaiden II.iso", Triple(0xFF14070A.toInt(), 0xFF7D1020.toInt(), 0xFFF4E2C0.toInt()),
            "slash", customCover = false, lastRank = 5, playedMinutes = 268, runs = 9, mediaId = "9C2240B8"),
        SampleGame("584108FF", "Geometry Wars: Retro Evolved 2", GameFormat.GOD, "$ROOT/XBLA/584108FF", Triple(0xFF04050A.toInt(), 0xFF1C0F3A.toInt(), 0xFF3EF0FF.toInt()),
            "neon", customCover = false, favorite = true, collections = listOf("Arcade"), lastRank = 3, playedMinutes = 77, runs = 12),
        SampleGame("4D5307F1", "Fable II", GameFormat.GOD, "$ROOT/Fable II/4D5307F1", Triple(0xFF0D1A0F.toInt(), 0xFF2F6B3A.toInt(), 0xFFE8D58A.toInt()),
            "bokeh", customCover = true, collections = listOf("Zerar em 2026"), lastRank = 7, playedMinutes = 610, runs = 14, mediaId = "41E7B0C3"),
        SampleGame("545407F2", "Grand Theft Auto IV", GameFormat.ZAR, "$ROOT/GTA IV.zar", Triple(0xFF0C1418.toInt(), 0xFF3A5260.toInt(), 0xFFE9E2CF.toInt()),
            "grid", customCover = true, lastRank = 8, playedMinutes = 130, runs = 6, mediaId = "A07F11D2"),
        SampleGame("4D530805", "Alan Wake", GameFormat.ISO, "$ROOT/Alan Wake.iso", Triple(0xFF05080E.toInt(), 0xFF1B2C4A.toInt(), 0xFFCFE1FF.toInt()),
            "bokeh", customCover = false, collections = listOf("Zerar em 2026"), mediaId = "6E31A9F0"),
        SampleGame("4D5307DF", "Blue Dragon", GameFormat.ISO, "$ROOT/Blue Dragon (Disc 1).iso", Triple(0xFF061A2D.toInt(), 0xFF1A5A9A.toInt(), 0xFF9FE3FF.toInt()),
            "bands", customCover = true, collections = listOf("Zerar em 2026"), lastRank = 10, playedMinutes = 44, runs = 3, discCount = 3, mediaId = "18BD02E4"),
        SampleGame("4E4D0859", "Tekken Tag Tournament 2", GameFormat.ISO, "$ROOT/Tekken Tag Tournament 2.iso", Triple(0xFF190606.toInt(), 0xFFA8161B.toInt(), 0xFFFFE08A.toInt()),
            "slash", customCover = true, collections = listOf("Arcade"), lastRank = 6, playedMinutes = 58, runs = 4, mediaId = "55C0E81B"),
        SampleGame("4D5307F2", "Viva Piñata", GameFormat.XEX_FOLDER, "$ROOT/Viva Pinata/default.xex", Triple(0xFF2A0A2D.toInt(), 0xFFC2338F.toInt(), 0xFFFFD84A.toInt()),
            "pinata", customCover = false, mediaId = "C3A90F14"),
        SampleGame("4D53082D", "Gears of War 2", GameFormat.ISO, "$ROOT/Gears of War 2.iso", Triple(0xFF121314.toInt(), 0xFF4A4F52.toInt(), 0xFFC9A46A.toInt()),
            "shards", customCover = true, collections = listOf("Co-op local"), lastRank = 11, playedMinutes = 21, runs = 2, mediaId = "0F6D2C95"),
        SampleGame("4D5308AB", "Gears of War 3", GameFormat.ISO, "$ROOT/Gears of War 3.iso", Triple(0xFF191312.toInt(), 0xFF5B1A17.toInt(), 0xFFD8D1C4.toInt()),
            "shards", customCover = false, collections = listOf("Co-op local"), lastRank = 9, playedMinutes = 95, runs = 5, mediaId = "E28A6B30"),
        SampleGame("425607E5", "Toy Story 3", GameFormat.ZAR, "$ROOT/Toy Story 3.zar", Triple(0xFF0A2246.toInt(), 0xFF2F6FD1.toInt(), 0xFFFFD34D.toInt()),
            "toy", customCover = false, collections = listOf("Co-op local"), lastRank = 12, playedMinutes = 6, runs = 1, mediaId = "7D02B6EE"),
    )

    fun byId(titleId: String): SampleGame = games.first { it.titleId == titleId }
}
