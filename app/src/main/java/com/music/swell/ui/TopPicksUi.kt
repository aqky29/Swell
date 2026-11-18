package com.music.swell.ui

import android.widget.Toast
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.music.swell.R
import com.music.swell.data.TopPicks
import com.music.swell.data.YtMusicRepository
import com.music.swell.data.model.HomeShelf
import com.music.swell.data.model.UiState
import com.music.swell.data.settings.AppSettings
import com.music.swell.data.model.PlaybackSourceType
import com.music.swell.data.model.TopPick
import com.music.swell.data.model.TopPickKind
import com.music.swell.data.model.TopPickMood
import com.music.swell.playback.beginRadioQueue
import com.music.swell.playback.commitRadioQueue
import com.music.swell.playback.playSongs
import kotlinx.coroutines.launch

/**
 * Home's Top Picks row and Find Your Mood, with everything they need to act:
 * the picks, whether their artwork may move, what a tap does, and the mood
 * grid's own page.
 *
 * Built by [rememberTopPicksUi] rather than inline in BitChordApp, which sits
 * at the JVM method-size limit — this keeps its share there to one call.
 */
@Stable
class TopPicksUi internal constructor(
    val picks: List<TopPick>?,
    val loading: Boolean,
    /** Off with the app in the background or Now Playing over the page. */
    val artworkMotion: Boolean,
    /** A Top Picks card: opens the release, or starts the station behind it. */
    val open: (TopPick, String) -> Unit,
    /** A Find Your Mood tile: that mood's station. */
    val playMood: (TopPickMood, String) -> Unit,
    val moodsGridState: LazyGridState,
    private val moodsOpenState: MutableState<Boolean>,
    /** Content Preferences: Home's Recents shelf is hidden. */
    val hideRecents: Boolean,
    /** Content Preferences: neither shelf is drawn without its callback. */
    val showTopPicks: Boolean,
    val showMoods: Boolean,
) {
    /** What Home draws of [state]: the feed, less what Content Preferences hides. */
    fun visibleHome(state: UiState<List<HomeShelf>>): UiState<List<HomeShelf>> {
        val loaded = state as? UiState.Success ?: return state
        if (!hideRecents) return state
        return UiState.Success(loaded.data.filterNot { it.title == YtMusicRepository.RECENT_TITLE })
    }

    /** Whether Find Your Mood's Show all grid is the page on screen. */
    var moodsOpen: Boolean
        get() = moodsOpenState.value
        set(value) {
            moodsOpenState.value = value
        }
}

/**
 * @param onNewPlayRequest called as a station is about to replace the queue,
 *   so a play started a moment earlier (a radio lookup still in flight) knows
 *   it has been overtaken — the caller's own play-request bookkeeping.
 */
@Composable
fun rememberTopPicksUi(
    viewModel: MainViewModel,
    controller: MediaController?,
    showNowPlaying: Boolean,
    refusedByHost: () -> Boolean,
    onNewPlayRequest: () -> Unit,
): TopPicksUi {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picks by viewModel.topPicks.collectAsStateWithLifecycle()
    val loading by viewModel.topPicksLoading.collectAsStateWithLifecycle()
    // The Top Picks and mood artwork loop forever; nobody sees it with the
    // app in the background or Now Playing pulled up over the page.
    val appForeground = rememberIsForeground()
    // Top Picks turns over each hour; coming back to the app after a
    // turn-over is an open too.
    LaunchedEffect(appForeground) {
        if (appForeground) viewModel.refreshTopPicksIfStale()
    }
    val currentController by rememberUpdatedState(controller)
    val currentRefused by rememberUpdatedState(refusedByHost)
    val currentNewPlay by rememberUpdatedState(onNewPlayRequest)
    val currentPicks by rememberUpdatedState(picks)
    val moodsOpen = remember { mutableStateOf(false) }
    val hideRecents by AppSettings.hideRecents.collectAsStateWithLifecycle()
    val hideTopPicks by AppSettings.hideTopPicks.collectAsStateWithLifecycle()
    val hideHomeMoods by AppSettings.hideHomeMoods.collectAsStateWithLifecycle()
    val moodsGridState = rememberLazyGridState()
    var generation by remember { mutableIntStateOf(0) }

    val open: (TopPick, String) -> Unit = remember(viewModel) {
        { pick, label ->
            val browseId = pick.browseId
            if (pick.kind == TopPickKind.LATEST_RELEASE && browseId != null) {
                viewModel.openDetail(
                    browseId = browseId,
                    title = pick.title,
                    subtitle = pick.artistName.orEmpty(),
                    thumbnailUrl = pick.images.firstOrNull(),
                )
            } else if (currentController != null && !currentRefused()) {
                // A station is YouTube Music's own queue for it — the
                // Supermix, the artist's Mix, a mood playlist's radio —
                // fetched only now, and loaded before the player is touched
                // so a failed request leaves the current queue playing.
                currentNewPlay()
                val request = ++generation
                scope.launch {
                    val songs = TopPicks.queue(pick).getOrNull().orEmpty()
                    if (request != generation) return@launch
                    val activeController = currentController ?: return@launch
                    if (songs.isEmpty()) {
                        Toast.makeText(context, R.string.couldnt_load_tracks, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val sourceId = pick.watch?.playlistId ?: pick.mood?.name
                    val station = songs.map {
                        it.copy(
                            radioName = label,
                            playbackSource = label,
                            playbackSourceType = PlaybackSourceType.HOME,
                            playbackSourceId = sourceId,
                        )
                    }
                    activeController.beginRadioQueue()
                    activeController.playSongs(station, 0)
                    activeController.commitRadioQueue()
                }
            }
        }
    }
    val playMood: (TopPickMood, String) -> Unit = remember(open) {
        { mood, title ->
            // Steered by the same scene words the Mood for You card was built with.
            val hints = currentPicks?.firstOrNull { it.kind == TopPickKind.MOOD }?.hints
            open(
                if (hints != null) TopPicks.moodStation(mood, hints) else TopPicks.moodStation(mood),
                title,
            )
        }
    }
    return TopPicksUi(
        picks = picks,
        loading = loading,
        artworkMotion = appForeground && !showNowPlaying,
        open = open,
        playMood = playMood,
        moodsGridState = moodsGridState,
        moodsOpenState = moodsOpen,
        hideRecents = hideRecents,
        showTopPicks = !hideTopPicks,
        showMoods = !hideHomeMoods,
    )
}
