package xendroid.compose.ui.library

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import xendroid.compose.R
import xendroid.compose.data.Game
import xendroid.compose.data.isFavorite
import xendroid.compose.gamepad.MenuButtonPrefs
import xendroid.compose.gamepad.NavRepeat
import xendroid.compose.sessions.TitleActivity
import xendroid.compose.sessions.formatPlayTime

/**
 * The library as a bar of covers for a controller or a couch (15a): the focused game's art
 * behind everything, its cover and play time at the top, the bar of covers with the focused
 * one enlarged at the left, and under it that game's actions. Keys and taps go through
 * [CarouselNav]; the games, their order and the filters are the grid's.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCarousel(
    games: List<Game>,
    viewModel: GameLibraryViewModel,
    coverRevision: Int,
    favorites: Set<String>,
    activity: Map<String, TitleActivity>,
    lastFocusedId: String?,
    focusAllowed: Boolean,
    restoreFocus: Int,
    onFocused: (String) -> Unit,
    onLaunch: (Game) -> Unit,
    onDetails: (Game) -> Unit,
    onSwitchView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val swapConfirm = remember { MenuButtonPrefs.swapConfirm(context) }
    var stored by remember(games.map { it.stableId }) {
        mutableStateOf(CarouselNav(games.indexOfFirst { it.stableId == lastFocusedId }.coerceAtLeast(0)))
    }
    // Read clamped: a list that shrank (a filter, a rescan) keeps the focus inside it.
    val nav = stored.clamped(games.size)
    val focused = games.getOrNull(nav.game) ?: return
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = nav.game)
    val focusRequester = remember { FocusRequester() }
    val repeat = remember { NavRepeat() }

    LaunchedEffect(nav.game) {
        onFocused(focused.stableId)
        listState.animateScrollToItem(nav.game)
    }
    LaunchedEffect(restoreFocus, focusAllowed) {
        if (focusAllowed) runCatching { focusRequester.requestFocus() }
    }

    fun run(command: CarouselNav.Command): Boolean = when (command) {
        CarouselNav.Command.None -> true
        is CarouselNav.Command.Launch -> { games.getOrNull(command.index)?.let(onLaunch); true }
        is CarouselNav.Command.Details -> { games.getOrNull(command.index)?.let(onDetails); true }
        is CarouselNav.Command.ToggleFavorite -> { games.getOrNull(command.index)?.let(viewModel::toggleFavorite); true }
        CarouselNav.Command.SwitchView -> { onSwitchView(); true }
        CarouselNav.Command.PassBack -> false
    }

    Box(
        modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val key = CarouselNav.keyOf(event.nativeKeyEvent.keyCode, swapConfirm) ?: return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) {
                    if (key == CarouselKey.LEFT || key == CarouselKey.RIGHT) repeat.release()
                    // The up of a key the carousel took is its own too (no click lands elsewhere).
                    return@onPreviewKeyEvent key != CarouselKey.CANCEL || nav.action != CarouselAction.PLAY
                }
                if (key == CarouselKey.LEFT || key == CarouselKey.RIGHT) {
                    // U04: a held direction walks at a readable pace, not at the key repeat rate.
                    if (!repeat.press(if (key == CarouselKey.LEFT) -1 else 1, SystemClock.uptimeMillis())) return@onPreviewKeyEvent true
                } else if (event.nativeKeyEvent.repeatCount > 0) {
                    return@onPreviewKeyEvent key != CarouselKey.CANCEL
                }
                val (next, command) = nav.on(key, games.size)
                stored = next
                run(command)
            },
    ) {
        val coverModel = remember(focused.stableId, coverRevision) { viewModel.iconFileOrFallback(focused) }
        // The focused game's art, dimmed (and blurred from Android 12), under everything.
        AsyncImage(
            model = ImageRequest.Builder(context).data(coverModel).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.35f,
            modifier = Modifier.fillMaxSize().blur(28.dp),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))),
            ),
        )
        Column(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(coverModel).build(),
                    contentDescription = focused.name,
                    modifier = Modifier.size(140.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        (if (isFavorite(focused, favorites)) "★ " else "") + focused.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val details = listOfNotNull(
                        focused.titleId?.let { stringResource(R.string.lib_title_id, it) },
                        focused.format.name,
                        if (focused.isMultiDisc) stringResource(R.string.lib_disc_of, focused.discNumber, focused.discCount) else null,
                    )
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    val played = focused.titleId?.uppercase()?.let { activity[it] }
                    Text(
                        if (played == null) stringResource(R.string.lib_not_played)
                        else pluralStringResource(R.plurals.lib_last_played, played.runs,
                            java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(played.lastPlayedAt)),
                            formatPlayTime(played.playedMs), played.runs),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(start = 24.dp, end = 240.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            ) {
                itemsIndexed(games, key = { _, game -> game.stableId }) { index, game ->
                    val isFocused = index == nav.game
                    val size by animateDpAsState(if (isFocused) 132.dp else 96.dp, label = "coverSize")
                    val model = remember(game.stableId, coverRevision) { viewModel.iconFileOrFallback(game) }
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(model).build(),
                        contentDescription = game.name,
                        modifier = Modifier
                            .size(size)
                            .clip(RoundedCornerShape(8.dp))
                            .border(if (isFocused) 3.dp else 0.dp,
                                if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {
                                    val (next, command) = nav.tap(index, games.size)
                                    stored = next
                                    run(command)
                                },
                                onLongClick = { stored = CarouselNav(index); onDetails(game) },
                            ),
                    )
                }
            }
            // The focused game's column: its actions, under the cross.
            Column(Modifier.padding(start = 24.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CarouselAction.entries.forEach { action ->
                    val selected = action == nav.action
                    Text(
                        when (action) {
                            CarouselAction.PLAY -> stringResource(R.string.lib_play)
                            CarouselAction.DETAILS -> stringResource(R.string.lib_details)
                            CarouselAction.FAVORITE -> stringResource(
                                if (isFavorite(focused, favorites)) R.string.lib_unfavorite else R.string.lib_favorite)
                        },
                        style = if (selected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                stored = nav.copy(action = action)
                                run(stored.on(CarouselKey.CONFIRM, games.size).second)
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.lib_carousel_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 10.dp),
            )
        }
    }
}
