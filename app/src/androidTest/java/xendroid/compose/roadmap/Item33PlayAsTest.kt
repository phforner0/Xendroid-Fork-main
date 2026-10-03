package xendroid.compose.roadmap

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import xendroid.compose.MainActivity
import xendroid.compose.R
import xendroid.compose.core.ContentPaths
import xendroid.compose.data.ProfilePick
import xendroid.compose.settings.ConfigStore
import xendroid.compose.ui.profile.ProfileManagerViewModel
import xendroid.compose.ui.profile.ProfileManagerViewModel.ListState
import xendroid.compose.ui.profile.ProfileManagerViewModel.OpState

/**
 * Roadmap item 33 (U11), the part before the boot, with the phone's `gameDir`: with two profiles
 * (A plays as P1, B is set as P2 in Profiles), tapping a game asks "Play as" with both listed and
 * B marked "· P2"; choosing B says it moves to P1 and P2 signs in nobody; Cancel starts nothing.
 * The game sheet says "Signs in as A".
 *
 * Left for the phone (group C): the game opening with the chosen profile, "Don't ask again", P2
 * signed in inside a local multiplayer game.
 */
@RunWith(AndroidJUnit4::class)
class Item33PlayAsTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = Device.context

    private fun waitFor(text: String) = compose.waitUntil(60_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun twoProfilesAskWhoPlaysAndCancelStartsNothing() {
        Library(listOf(GameRun.gameDir())).use { library ->
            val game = library.viewModel().rescan("the games folder").games.firstOrNull()
            assumeTrue("no game in the games folder", game != null)
            val profiles = ProfileManagerViewModel(context, ConfigStore(context))
            fun op(what: String, action: () -> Unit): OpState {
                profiles.dismiss()
                action()
                return Device.await(profiles.opState, what) { it is OpState.Done || it is OpState.Failed }
            }
            fun xuidOf(tag: String) = (Device.await(profiles.listState, "$tag listed") {
                it is ListState.Loaded && it.profiles.any { p -> p.gamertag == tag }
            } as ListState.Loaded).profiles.single { it.gamertag == tag }.xuid
            val made = ArrayList<String>()
            try {
                listOf("RoadmapA", "RoadmapB").forEach { tag ->
                    assertTrue(op("creating $tag") { profiles.create(tag, 1, 103, null) } is OpState.Done)
                    made += xuidOf(tag)
                }
                val (a, b) = made
                assertTrue(op("A as P1") { profiles.setActive(a) } is OpState.Done)
                assertTrue(op("B as P2") { profiles.setPlayer(1, b) } is OpState.Done)
                context.getSharedPreferences(ProfilePick.PREFS, 0).edit().putBoolean(ProfilePick.ASK, true).commit()

                ActivityScenario.launch(MainActivity::class.java).use {
                    waitFor(game!!.name)
                    compose.onAllNodesWithText(game.name).onFirst().performClick()
                    waitFor(Device.string(R.string.playas_title))
                    compose.onNodeWithText("RoadmapA").assertExists()
                    compose.onNodeWithText("RoadmapB · P2").performClick()
                    waitFor(Device.string(R.string.playas_moves, 2))
                    compose.onNodeWithText(Device.string(R.string.common_cancel)).performClick()
                    compose.waitUntil(10_000) {
                        compose.onAllNodesWithText(Device.string(R.string.playas_title)).fetchSemanticsNodes().isEmpty()
                    }
                }
                assertTrue("Cancel started a game", Device.shell("pidof ${context.packageName}:emu").isBlank())

                ActivityScenario.launch(MainActivity::class.java).use {
                    waitFor(game!!.name)
                    compose.onAllNodesWithText(game.name).onFirst().performTouchInput { longClick() }
                    waitFor(Device.string(R.string.lib_signs_in_as, "RoadmapA"))
                }
            } finally {
                made.forEach { File(ContentPaths.contentRoot(), it).deleteRecursively() }
                profiles.refresh()      // player slots naming a profile that is gone are cleared
                Device.await(profiles.listState, "profiles listed again") { it is ListState.Loaded }
            }
        }
    }
}
