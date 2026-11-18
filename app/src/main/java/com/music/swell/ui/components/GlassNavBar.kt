/*
 * The bar's structure and its inline/expanded behaviour are
 * EchoMusicApp/Echo-Music's AppFloatingNavBar + FloatingMiniPlayer (GPL-3.0),
 * over the FloatingTabBar vendored in [com.music.swell.ui.components.floatingtabbar].
 * BitChord's own tabs, song model, transport and haptics are wired through it in
 * place of Echo's Screens routing and PlayerConnection.
 */
@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.music.swell.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.swell.ui.components.liquidglass.currentSwellGlass
import coil3.compose.AsyncImage
import com.music.swell.R
import com.music.swell.data.model.ROW_ART_PX
import com.music.swell.data.model.Song
import com.music.swell.data.model.artworkAt
import com.music.swell.ui.components.floatingtabbar.FloatingTabBar
import com.music.swell.ui.components.floatingtabbar.FloatingTabBarDefaults
import com.music.swell.ui.components.floatingtabbar.FloatingTabBarScrollConnection
import com.music.swell.ui.haptics.Haptic
import com.music.swell.ui.haptics.rememberHaptics
import com.music.swell.ui.player.PlayerDock

/**
 * The liquid glass navigation bar: the iOS 26 shape where the now playing
 * controls and the tabs are one component rather than two stacked bars.
 *
 * Expanded, it is a full width now playing pill sitting over the tab pill and a
 * separate circular Search tab. Scrolling down collapses it inline — the tabs
 * fold down to just the selected one, the now playing controls narrow into the
 * gap between it and Search, and the whole thing becomes a single row the width
 * of the screen. Scrolling back up expands it. The fold is driven by
 * [scrollConnection], which the page's scroll has to be dispatched into for any
 * of this to move; see MainActivity's `nestedScroll`.
 *
 * Every surface here samples the app backdrop through [Modifier.liquidGlass], so
 * this is only ever used where that is supported and switched on — off either,
 * MainActivity draws [MiniPlayer] and [FloatingBottomBar] instead.
 *
 * [song] null means nothing is playing, and the accessory is simply absent: the
 * bar is then the tab pill and Search alone, and the collapse still works.
 */
@Composable
fun GlassNavBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    /** @see com.music.swell.data.listentogether.ListenTogether.State.controlsLocked */
    controlsLocked: Boolean = false,
    onBlockedControl: () -> Unit = {},
    /** Where the player's artwork lands when it closes into this bar. */
    dock: PlayerDock? = null,
    /** Dragging the bar's player up into the full player. */
    pull: MiniPlayerPull? = null,
    modifier: Modifier = Modifier,
    /** The Search tab's query; the bar's expanded search pill is its field. */
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchSubmit: () -> Unit = {},
    searchPlaceholder: String = "",
    searchFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    /**
     * A page (album, artist, ...) opened from the results is over the Search tab.
     * The bar is then the tabs again with Search selected, not the field: the
     * field belongs to the results, and its close would close the page with them.
     */
    searchCovered: Boolean = false,
    /** Back to the results under [searchCovered], query and all. */
    onSearchResume: () -> Unit = {},
) {
    val haptics = rememberHaptics()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backdropState = com.music.swell.ui.components.liquidglass.LocalLiquidGlassBackdropState.current
        ?: com.music.swell.ui.components.liquidglass.rememberLiquidGlassBackdropState()

    var previousTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    if (selectedIndex in 0..2) {
        previousTab = selectedIndex
    }

    val isSearchActive = selectedIndex == 3 && !searchCovered
    val isMiniMode = scrollConnection.isInline && !isSearchActive
    val currentTab = when (selectedIndex) {
        0 -> com.music.swell.ui.components.liquidglass.MusicTab.HOME
        1 -> com.music.swell.ui.components.liquidglass.MusicTab.EXPLORE
        2 -> com.music.swell.ui.components.liquidglass.MusicTab.LIBRARY
        else -> com.music.swell.ui.components.liquidglass.MusicTab.fromIndex(previousTab)
    }

    var searchFocused by remember { mutableStateOf(false) }
    val isSearchCovered = selectedIndex == 3 && searchCovered
    val uiState = remember(currentTab, isMiniMode, isSearchActive, isSearchCovered, song, searchFocused, searchQuery) {
        com.music.swell.ui.components.liquidglass.NavbarUiState(
            selectedTab = currentTab,
            isMiniMode = isMiniMode,
            isSearchActive = isSearchActive,
            isSearchFocused = isSearchActive && searchFocused,
            searchQuery = searchQuery,
            hasActiveTrack = song != null,
            isSearchCovered = isSearchCovered
        )
    }

    val glass = currentSwellGlass()

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        com.music.swell.ui.components.liquidglass.LiquidGlassSearchableTabBar(
            uiState = uiState,
            currentSong = song,
            isPlaying = isPlaying,
            isLoading = isLoading,
            isDark = isDark,
            backdropState = backdropState,
            barGlassSettings = glass.bar,
            pillGlassSettings = glass.pill,
            glassMode = glass.mode,
            onTabSelected = { tab ->
                if (tab.index != selectedIndex) {
                    haptics.play(Haptic.Select)
                }
                previousTab = tab.index
                onTabSelected(tab.index)
                scrollConnection.expand()
            },
            onDismissMiniMode = {
                scrollConnection.expand()
            },
            onSearchToggle = { active ->
                if (active) {
                    haptics.play(Haptic.Select)
                    if (isSearchCovered) onSearchResume() else onTabSelected(3)
                } else {
                    onTabSelected(previousTab)
                }
            },
            onSearchQueryChanged = onSearchQueryChange,
            onSearchFocusChanged = { searchFocused = it },
            onTogglePlay = {
                haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                onPlayPause()
            },
            onNextTrack = {
                if (!controlsLocked) {
                    haptics.play(Haptic.SkipNext)
                    onNext()
                } else {
                    onBlockedControl()
                }
            },
            onTapMiniPlayer = onExpand,
            playerDock = dock,
            modifier = Modifier.fillMaxWidth(),
            searchPlaceholder = searchPlaceholder,
            onSearchSubmit = onSearchSubmit,
            searchFocusRequester = searchFocusRequester,
        )
    }
}

/**
 * The now playing controls docked into [GlassNavBar] as its accessory.
 *
 * Two densities of the same row rather than two components, so the shared
 * element carrying it between the bar's states has one thing to interpolate:
 * [isInline] narrows the artwork, drops the artist line and drops the skip
 * button, which is what makes it fit the collapsed row's height.
 *
 * The content is [MiniPlayer]'s — same artwork, same transport, same haptics —
 * and not Echo's, which reaches into a player connection this app doesn't have.
 * The press response is Echo's, and belongs to the glass rather than the row:
 * a surface you can push on is the whole point of the material.
 */
@Composable
private fun GlassNowPlaying(
    song: Song,
    isInline: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    contentColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    controlsLocked: Boolean,
    onBlockedControl: () -> Unit,
    dock: PlayerDock?,
    pull: MiniPlayerPull?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val pressSource = remember { MutableInteractionSource() }
    val isPressed by pressSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accessoryPressScale",
    )

    val artSize = if (isInline) 32.dp else 40.dp
    val glyphSlot = if (isInline) 32.dp else 40.dp
    val glyphSize = if (isInline) 24.dp else 32.dp

    Box(
        // Inline, the accessory is stretched to the row's height by the tab bar
        // (`fillMaxHeight` on a row measured at IntrinsicSize.Max), and this row
        // is shorter than that — the tab pill beside it, with a 25dp glyph in
        // 10dp of padding, is the tallest thing in the row and sets the height.
        // A Box defaults to TopStart, so the artwork and the transport sat a
        // couple of dp above the pill's centre line. Expanded the Box wraps its
        // content, so centring is a no-op there.
        contentAlignment = Alignment.Center,
        // The gestures and the tap belong to the whole glass surface, not the
        // row centred in it. Inline, that row is shorter than the pill it sits
        // in, and a pull started in the strip above or below it went nowhere —
        // on a bar only 45dp tall to begin with.
        modifier = Modifier
            // Outside the press scale, which it lifts along with everything else.
            .miniPlayerGestures(
                pull = pull,
                onNext = {
                    haptics.play(Haptic.SkipNext)
                    onNext()
                },
                onPrevious = {
                    haptics.play(Haptic.SkipPrevious)
                    onPrevious()
                },
                locked = controlsLocked,
                onBlocked = onBlockedControl,
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(modifier)
            .clickable(
                interactionSource = pressSource,
                indication = null,
                onClick = onExpand,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isInline) 8.dp else 12.dp,
                    vertical = if (isInline) 4.dp else 8.dp,
                ),
        ) {
            AsyncImage(
                model = rememberRemoteArtworkUrl(song)?.artworkAt(ROW_ART_PX),
                contentDescription = null,
                modifier = Modifier
                    .size(artSize)
                    // Whichever of the two densities is on screen is the one
                    // the player flies to: only one is composed at rest.
                    .playerDockArt(dock, if (isInline) 6.dp else 8.dp)
                    .clip(RoundedCornerShape(if (isInline) 6.dp else 8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(if (isInline) 8.dp else 10.dp))
            if (isInline) {
                Text(
                    text = song.title,
                    // The same size the expanded row sets it in. Collapsing the
                    // bar drops the artist line and the skip button, not the
                    // title's weight in the row — a title that shrank as well
                    // would read as a different component rather than the same
                    // one folded up, and the shared element carrying it between
                    // the two states has one less thing to interpolate.
                    //
                    // It costs nothing in height: the row is stretched to the
                    // tab pill's 45dp either way, and titleMedium's line box
                    // still clears the 32dp artwork beside it.
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    // The inline row shares its width with the tab pill and the
                    // Search circle, so most titles will not fit at this size.
                    // Cut with an ellipsis rather than wrapped or scaled.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(Modifier.weight(1f)) {
                    ExplicitSongTitle(
                        song = song,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isLoading) {
                Box(Modifier.size(glyphSlot), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = contentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(if (isInline) 18.dp else 22.dp),
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                        onPlayPause()
                    },
                    modifier = Modifier.size(glyphSlot),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        tint = contentColor,
                        modifier = Modifier.size(glyphSize),
                    )
                }
            }
            // Dropped inline: the collapsed row is sharing its width with the
            // tab pill and the Search circle, and the title is what has to
            // survive that, not a second transport button.
            if (!isInline) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        haptics.play(Haptic.SkipNext)
                        onNext()
                    },
                    enabled = !controlsLocked,
                    modifier = Modifier.size(glyphSlot),
                ) {
                    MiniPlayerNextGlyph(
                        tint = contentColor.copy(alpha = if (controlsLocked) 0.3f else 1f),
                    )
                }
            }
        }
    }
}
