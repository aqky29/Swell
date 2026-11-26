package com.music.swell.ui.components.liquidglass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.music.swell.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.music.swell.data.model.Song
import com.music.swell.ui.icons.BitChordIcons
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlin.math.sign
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Padanan `TabBarSearchableLayout._kSpring` di `liquid_glass_widgets`:
 * `SpringDescription(mass: 1.0, stiffness: 350.0, damping: 30.0)` ->
 * `dampingRatio = 30 / (2 * sqrt(1 * 350)) ≈ 0.8018f`, `stiffness = 350f`.
 */
private const val MORPH_SPRING_DAMPING = 0.80f
private const val MORPH_SPRING_STIFFNESS = 350f

/** Morph duration when the user asked for less motion; see [rememberReduceMotion]. */
private const val REDUCED_MORPH_MS = 140

/**
 * iOS 27 menciutkan bar dalam ~300ms tetapi mengembangkannya kembali dalam ~200ms, keduanya
 * dengan sedikit pantulan (`GlassSpring.snappy`, bounce 0.15). Kekakuan dari
 * `(2π / durasi)²`, lihat [SearchableBarMorphCalculator].
 */
private const val COLLAPSE_SPRING_STIFFNESS = 439f
private const val EXPAND_SPRING_STIFFNESS = 987f
private const val SNAPPY_SPRING_DAMPING = 0.85f

/**
 * iOS tetap membiaskan latar selama kaca berubah bentuk. Set `false` bila GPU tidak sanggup:
 * lensa lalu memudar keluar-masuk bersama drop shadow, bukan mati dan menyala mendadak.
 */
private const val REFRACTION_DURING_MORPH = true

/** Drop shadow (BlurMaskFilter, mahal saat ukuran berubah) memudar selama morph. */
private const val MORPH_EFFECT_FADE_OUT_MS = 90
private const val MORPH_EFFECT_FADE_IN_MS = 200

/**
 * Kapsul tab, Search, tombol ×, dan mini player digambar sebagai satu kaca yang menyatu
 * seperti tetesan saat berdekatan (LiquidGlassGroup). `false` = tiap elemen menggambar kacanya
 * sendiri seperti sebelumnya.
 */
private const val MERGE_GLASS_SHAPES = true

/** Jarak smooth-min amoeba (dp) saat Tab ↔ Search atau Search ↔ × membelah & menyatu. */
private const val AMOEBA_MERGE_K_DP = 38f

/** Jarak smooth-min kecil (dp) untuk MiniPlayerPill agar tidak melengket ke atas bar di celah 5.dp. */
private const val ACCESSORY_MERGE_K_DP = 4f

private fun smoothstep(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** Rentang (dp) tepi kapsul melewati orb Search selama orb itu lepas atau terserap. */
private const val ORB_PINCH_DP = 6f

/** Seberapa dalam (dp) mini player masuk ke baris bar sebelum sisinya sepenuhnya menyingkir. */
private const val ACCESSORY_YIELD_RAMP_DP = 10f

/**
 * Gembung vertikal kapsul dari kecepatan perubahan lebarnya (dp/detik): menciut cepat membuatnya
 * sedikit lebih tinggi (hingga 7%), memekar cepat sedikit lebih pipih (hingga 3%).
 */
private fun liquidBulge(widthVelocityDpPerSec: Float): Float =
    (-widthVelocityDpPerSec * 0.00004f).coerceIn(-0.03f, 0.07f)
private const val GROUP_SLOT_TABS = 0
private const val GROUP_SLOT_SEARCH = 1
private const val GROUP_SLOT_DISMISS = 2
private const val GROUP_SLOT_ACCESSORY = 3
private const val GROUP_SLOT_COUNT = 4

/** Orb kaca menggembung saat ditekan seperti Liquid Glass iOS (bukan mengecil gaya Material). */
private const val ORB_PRESSED_SCALE = 1.1f
private val OrbPressSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 400f)

// Ambang "morph masih berjalan". Harus lebih besar dari residual spring Compose
// (~0.01) supaya efek tidak ikut pudar permanen setelah animasi selesai.
private const val MORPH_EFFECT_DP_EPSILON = 0.5f
private const val MORPH_EFFECT_FRACTION_EPSILON = 0.02f

/**
 * Padanan 1:1 `GlassTabBar.searchable` (`TabBarSearchableLayout` + `SearchableTabIndicator` +
 * `SearchPill` + `DismissPill`) di package Flutter `liquid_glass_widgets` yang digunakan
 * pada `lib/screens/home_screen.dart` di project MusikkU.
 *
 * Seluruh elemen berada di dalam **satu ruang koordinat kontinu (`BoxWithConstraints`)**
 * tanpa `AnimatedContent` / `AnimatedVisibility` yang memutus pohon komposisi:
 * 1. **Slide / Drag Tab Indicator (`TabDragGestureMixin` + `AnimatedGlassIndicator`)**:
 *    Pengguna dapat menggeser (slide/drag) jari secara horizontal di sepanjang kapsul tab
 *    maupun mengetuk langsung, lengkap dengan *rubber-band overdrag*, *velocity fling snapping*,
 *    ekspansi lensa kaca (`thickness` + `indicatorExpansion`), *jelly squash & stretch*,
 *    pembesaran ikon (`magnification = 1.15x`), serta goyangan lateral kapsul (`barSwayOffset`).
 * 2. **Morph Scroll ke Mini Mode & Klik Orb Tab**:
 *    Saat scroll turun, kapsul 3-tab menyusut mulus (`tabWidthAnim` -> `50.dp`) menjadi orb
 *    ikon tab aktif (beraksen), orb Search di kanan menyusut (`64.dp` -> `50.dp`), dan **satu-satunya**
 *    instans `MiniPlayerPill` meluncur turun dari atas navbar ke celah tengah di antara orb tab
 *    dan orb Search. Saat orb tab diklik, `MiniPlayerPill` meluncur naik kembali sambil
 *    orb tab memekar menjadi kapsul 3-tab.
 * 3. **Morph Buka / Tutup Search & Fokus Keyboard**:
 *    Saat orb Search di kanan diklik, kapsul tab di kiri menyusut menjadi orb ikon tab aktif (`50.dp`),
 *    sementara orb Search di kanan bergeser ke kiri (`searchLeft` -> `58.dp`) dan memekar
 *    lebarnya (`searchWidth` -> `totalW - 58.dp`) menjadi kolom input pencarian. Saat kolom
 *    input difokuskan, kolom bergeser ke `left = 0.dp` dan tombol kaca bulat `DismissPill` (`×`)
 *    memekar di ujung kanan.
 */
@Composable
fun LiquidGlassSearchableTabBar(
    uiState: NavbarUiState,
    currentSong: Song?,
    isLoading: Boolean = false,
    isPlaying: Boolean,
    isDark: Boolean,
    backdropState: LiquidGlassBackdropState,
    barGlassSettings: LiquidGlassSettings,
    pillGlassSettings: LiquidGlassSettings,
    glassMode: GlassEffectMode,
    onTabSelected: (MusicTab) -> Unit,
    onDismissMiniMode: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    onNextTrack: () -> Unit,
    onTapMiniPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    playerDock: com.music.swell.ui.player.PlayerDock? = null,
    searchPlaceholder: String = "Cari lagu, artis, atau album...",
    onSearchSubmit: () -> Unit = {},
    /** Lets the host focus the field itself, e.g. when Search is opened from elsewhere. */
    searchFocusRequester: FocusRequester? = null
) {
    val focusManager = LocalFocusManager.current
    val ownFocusRequester = remember { FocusRequester() }
    val focusRequester = searchFocusRequester ?: ownFocusRequester
    // Hasil pencarian diketuk dan halamannya terbuka: field tidak lagi tampil, jadi
    // keyboard ikut turun alih-alih tetap mengetik ke field yang tersembunyi.
    LaunchedEffect(uiState.isSearchCovered) {
        if (uiState.isSearchCovered) focusManager.clearFocus()
    }

    BoxWithConstraints(
        modifier = modifier
            // Layer sendiri: animasi morph hanya merekam ulang navbar, bukan seluruh layar.
            .graphicsLayer()
            .fillMaxWidth()
            .navigationBarsPadding()
            // Dasar 20.dp: iOS 27 menaruh bar ~21pt dari dasar layar.
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp)
    ) {
        val totalWidthDp = maxWidth.value
        val density = LocalDensity.current
        // Satu blur latar untuk seluruh grup (tab, search, mini player), lihat SharedGlassBackdrop.
        // Region setinggi kontainer terbesar (mini player di atas bar) + margin efek tekan.
        val sharedBackdrop = rememberSharedGlassBackdrop(
            backdropState = backdropState,
            blurRadius = barGlassSettings.blurRadiusDp.dp,
            enabled = glassMode != GlassEffectMode.SOLID,
            saturation = barGlassSettings.backdropSaturation(glassMode)
        )
        val sharedRegionHeightPx = with(density) {
            (SearchableBarMorphCalculator.DEFAULT_BAR_HEIGHT_DP +
                SearchableBarMorphCalculator.DEFAULT_ACCESSORY_SPACING_DP +
                SearchableBarMorphCalculator.DEFAULT_ACCESSORY_HEIGHT_DP).dp.toPx()
        }
        val sharedRegionMarginPx = with(density) { 24.dp.toPx() }

        // Semua kapsul digambar sebagai satu kaca yang bisa menyatu seperti amoeba (iOS
        // GlassEffectContainer). Hanya di Premium + AGSL; mode lain menggambar per elemen.
        val barBlurPx = with(density) { barGlassSettings.blurRadiusDp.dp.toPx() }
        val pillBlurPx = with(density) { pillGlassSettings.blurRadiusDp.dp.toPx() }
        val glassGroup = rememberLiquidGlassGroup(
            slotCount = GROUP_SLOT_COUNT,
            enabled = MERGE_GLASS_SHAPES &&
                glassMode == GlassEffectMode.PREMIUM &&
                sharedBackdrop.matches(barBlurPx) &&
                sharedBackdrop.matches(pillBlurPx)
        )
        val amoebaMerge = glassGroup.enabled

        val targets = remember(totalWidthDp, uiState, amoebaMerge) {
            SearchableBarMorphCalculator.computeTargets(
                totalWidthDp = totalWidthDp,
                uiState = uiState,
                amoebaMerge = amoebaMerge
            )
        }

        // Tiga sumbu pegas utama dari SearchableBottomBarController (_tabWCtrl, _searchLeftCtrl, _searchWCtrl).
        // Menciut karena scroll (mini mode) dan kembali darinya memakai pegas iOS 27 yang tidak
        // simetris; morph pencarian (buka maupun tutup) tetap memakai _kSpring.
        var lastMorphWasSearch by remember { mutableStateOf(false) }
        if (uiState.isSearchActive) {
            lastMorphWasSearch = true
        } else if (uiState.isMiniMode) {
            lastMorphWasSearch = false
        }
        val miniMorph = uiState.isMiniMode || !lastMorphWasSearch
        val reduceMotion = rememberReduceMotion()
        val morphSpring: FiniteAnimationSpec<Float> = remember(uiState.isTabCapsuleCollapsed, miniMorph, reduceMotion) {
            when {
                // Reduce motion: one short ease, no overshoot and no bounce on the morph.
                reduceMotion -> tween(durationMillis = REDUCED_MORPH_MS, easing = FastOutSlowInEasing)
                !miniMorph -> spring<Float>(
                    dampingRatio = MORPH_SPRING_DAMPING,
                    stiffness = MORPH_SPRING_STIFFNESS
                )
                uiState.isTabCapsuleCollapsed -> spring(
                    dampingRatio = SNAPPY_SPRING_DAMPING,
                    stiffness = COLLAPSE_SPRING_STIFFNESS
                )
                else -> spring(
                    dampingRatio = SNAPPY_SPRING_DAMPING,
                    stiffness = EXPAND_SPRING_STIFFNESS
                )
            }
        }
        // Animatable, bukan animateFloatAsState: kecepatannya dipakai untuk menggembungkan kaca.
        val tabWidthAnim = remember { Animatable(targets.targetTabWidthDp) }
        val currentMorphSpring by rememberUpdatedState(morphSpring)
        LaunchedEffect(targets.targetTabWidthDp) {
            tabWidthAnim.animateTo(targets.targetTabWidthDp, currentMorphSpring)
        }

        // Semua nilai animasi di bawah disimpan sebagai State dan hanya dibaca di fase layout/draw
        // (lambda modifier). Membacanya saat komposisi membuat seluruh navbar, termasuk tiap kaca
        // dan mini player, recompose di setiap frame morph; kini satu frame cukup layout + draw.
        val searchLeft = animateFloatAsState(
            targetValue = targets.targetSearchLeftDp,
            animationSpec = morphSpring,
            label = "morphSearchLeftDp"
        )
        val searchWidth = animateFloatAsState(
            targetValue = targets.targetSearchWidthDp,
            animationSpec = morphSpring,
            label = "morphSearchWidthDp"
        )
        // Baris mini iOS 27: orb tab menjorok dari tepi dan seluruh baris naik ke garis tengah bar.
        val tabLeft = animateFloatAsState(
            targetValue = targets.targetTabLeftDp,
            animationSpec = morphSpring,
            label = "morphTabLeftDp"
        )
        val rowLift = animateFloatAsState(
            targetValue = targets.targetRowLiftDp,
            animationSpec = morphSpring,
            label = "morphRowLiftDp"
        )

        // Tinggi bar ikut pegas yang sama dengan lebarnya. Tween 200ms terpisah membuat kapsul
        // sudah pipih sebelum selesai menciut, sehingga bentuknya terasa berubah dalam dua tahap.
        val barHeight = animateFloatAsState(
            targetValue = targets.targetBarHeightDp,
            animationSpec = morphSpring,
            label = "morphBarHeightDp"
        )

        // Dua sumbu interpolasi MiniPlayerPill. Mini player menumpang pegas yang sama dengan
        // kapsul agar keduanya tiba bersamaan (iOS 27), bukan tween 300ms yang tertinggal.
        val accessoryT = animateFloatAsState(
            targetValue = targets.targetAccessoryInlineFraction,
            animationSpec = morphSpring,
            label = "morphAccessoryInlineT"
        )
        val barCollapseT = animateFloatAsState(
            targetValue = targets.targetBarCollapseFraction,
            animationSpec = morphSpring,
            label = "morphBarCollapseT"
        )
        val accessoryAlpha = animateFloatAsState(
            targetValue = if (uiState.isMiniPlayerVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            label = "morphAccessoryAlpha"
        )

        // Skala kemunculan DismissPill (×) saat kolom pencarian fokus
        val dismissPillScale = animateFloatAsState(
            targetValue = if (targets.isDismissPillVisible) 1f else 0f,
            animationSpec = if (reduceMotion) {
                tween(durationMillis = REDUCED_MORPH_MS, easing = FastOutSlowInEasing)
            } else {
                spring(dampingRatio = 0.75f, stiffness = MORPH_SPRING_STIFFNESS)
            },
            label = "dismissPillScale"
        )

        // Search tinggal di dalam kapsul tab selama bar penuh; orb-nya muncul saat kapsul menciut,
        // bersilang pudar dengan slot Search di kapsul (dua ikon sempat tampak bersamaan, iOS 27).
        val searchOrbFade = animateFloatAsState(
            targetValue = if (uiState.isTabCapsuleCollapsed) 1f else 0f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            label = "searchOrbVisibility"
        )

        // Saat kolom cari fokus, SearchPill meluncur ke left = 0.dp sehingga orb tab kiri mengecil halus di belakangnya
        val tabPillVisibility = animateFloatAsState(
            targetValue = if (uiState.isSearchActive && uiState.isSearchFocused) 0f else 1f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            label = "tabPillFocusVisibility"
        )

        // Progres membentangnya kapsul tab (1f = penuh 3 tab, 0f = menciut jadi orb 50.dp)
        val normalBarH = SearchableBarMorphCalculator.DEFAULT_BAR_HEIGHT_DP
        val fullExpandedTabW = if (amoebaMerge) {
            val normalSearchW = (normalBarH * 1.67f).coerceIn(normalBarH, totalWidthDp * 0.45f)
            (totalWidthDp - normalSearchW + normalBarH).coerceIn(normalBarH, totalWidthDp)
        } else {
            totalWidthDp.coerceAtLeast(normalBarH)
        }
        val collapsedTabW = SearchableBarMorphCalculator.DEFAULT_SEARCH_BAR_HEIGHT_DP
        val tabExpandFraction: () -> Float = {
            ((tabWidthAnim.value - collapsedTabW) / (fullExpandedTabW - collapsedTabW).coerceAtLeast(1f))
                .coerceIn(0f, 1f)
        }

        // Visibilitas luar pil Search:
        // - Saat amoebaMerge aktif: MorphingGlassSearchPill adalah SATU-SATUNYA ikon Search
        //   (slot ke-4 saat Normal -> orb saat Mini Mode -> input saat Search), jadi selalu 1f.
        // - Saat fallback non-grup: tetap gunakan pinch/crossfade agar tidak menimpa label tab.
        fun searchOrbVisibility(): Float {
            if (amoebaMerge) return 1f
            val separationDp = searchLeft.value - (tabLeft.value + tabWidthAnim.value)
            return if (searchWidth.value <= barHeight.value + 4f) {
                ((separationDp + ORB_PINCH_DP) / (2f * ORB_PINCH_DP)).coerceIn(0f, 1f)
            } else {
                val tabCleared = (1f - ((tabExpandFraction() - 0.15f) / 0.30f).coerceIn(0f, 1f))
                (searchOrbFade.value * tabCleared).coerceIn(0f, 1f)
            }
        }

        // Tidak pernah lebih sempit dari lebarnya saat sebaris: menyempit lalu melebar lagi
        // terlihat memompa, dan judulnya sempat terpotong jadi "…".
        val accessoryMinWidthDp = totalWidthDp - 2f * (
            SearchableBarMorphCalculator.MINI_EDGE_INSET_DP +
                SearchableBarMorphCalculator.MINI_ORB_DP +
                SearchableBarMorphCalculator.MINI_SPACING_DP
            )

        fun accessoryBounds(): AccessoryBounds {
            val frame = SearchableBarMorphCalculator.computeAccessoryMorphFrame(
                accessoryT = accessoryT.value,
                barCollapseT = barCollapseT.value
            )
            val lift = rowLift.value
            val barH = barHeight.value
            // Mini player tidak menumpuk di atas kapsul tab atau orb Search saat turun ke baris
            // yang sama: sisinya terdorong tepi kaca tetangganya, seperti tetesan yang saling menyingkir.
            val rowOverlapDp = minOf(frame.bottomOffsetDp + frame.heightDp, lift + barH) -
                maxOf(frame.bottomOffsetDp, lift)
            val sharesRow = (rowOverlapDp / ACCESSORY_YIELD_RAMP_DP).coerceIn(0f, 1f)
            val rightInsetDp = maxOf(
                frame.rightInsetDp,
                (totalWidthDp - searchLeft.value + SearchableBarMorphCalculator.MINI_SPACING_DP) *
                    sharesRow * searchOrbVisibility()
            )
            val leftInsetDp = maxOf(
                frame.leftInsetDp,
                (tabLeft.value + tabWidthAnim.value + SearchableBarMorphCalculator.MINI_SPACING_DP) *
                    sharesRow * tabPillVisibility.value
            ).coerceAtMost(
                (totalWidthDp - rightInsetDp - accessoryMinWidthDp).coerceAtLeast(frame.leftInsetDp)
            )
            return AccessoryBounds(
                leftInsetDp = leftInsetDp,
                rightInsetDp = rightInsetDp,
                bottomOffsetDp = frame.bottomOffsetDp,
                heightDp = frame.heightDp,
                totalContainerHeightDp = frame.totalContainerHeightDp
            )
        }

        val hasActiveTrack = uiState.hasActiveTrack
        fun containerHeightDp(): Float {
            val rowHeightDp = barHeight.value + rowLift.value
            return if (hasActiveTrack) {
                maxOf(accessoryBounds().totalContainerHeightDp, rowHeightDp)
            } else {
                rowHeightDp
            }
        }

        // Animasi morph masih berjalan? Efek mahal dipudarkan (bukan dimatikan) selama transisi.
        // derivedStateOf: dihitung tiap frame, tetapi komposisi hanya ikut saat jawabannya berubah.
        val isMorphing by remember(targets) {
            derivedStateOf {
                abs(tabWidthAnim.value - targets.targetTabWidthDp) > MORPH_EFFECT_DP_EPSILON ||
                    abs(searchLeft.value - targets.targetSearchLeftDp) > MORPH_EFFECT_DP_EPSILON ||
                    abs(searchWidth.value - targets.targetSearchWidthDp) > MORPH_EFFECT_DP_EPSILON ||
                    abs(barHeight.value - targets.targetBarHeightDp) > MORPH_EFFECT_DP_EPSILON ||
                    abs(accessoryT.value - targets.targetAccessoryInlineFraction) > MORPH_EFFECT_FRACTION_EPSILON ||
                    abs(barCollapseT.value - targets.targetBarCollapseFraction) > MORPH_EFFECT_FRACTION_EPSILON ||
                    abs(tabLeft.value - targets.targetTabLeftDp) > MORPH_EFFECT_DP_EPSILON ||
                    abs(rowLift.value - targets.targetRowLiftDp) > MORPH_EFFECT_DP_EPSILON
            }
        }
        // Dibaca hanya di fase draw/layer lewat lambda, jadi tidak memicu recompose tiap frame.
        val morphEffectFade = animateFloatAsState(
            targetValue = if (isMorphing) 0f else 1f,
            animationSpec = tween(
                durationMillis = if (isMorphing) MORPH_EFFECT_FADE_OUT_MS else MORPH_EFFECT_FADE_IN_MS,
                easing = FastOutSlowInEasing
            ),
            label = "morphEffectFade"
        )
        val shadowAlpha: () -> Float = remember { { morphEffectFade.value } }
        val lensStrength: () -> Float = remember {
            if (REFRACTION_DURING_MORPH) ({ 1f }) else ({ morphEffectFade.value })
        }

        val tabSlot = glassGroup.slot(GROUP_SLOT_TABS)
        val searchSlot = glassGroup.slot(GROUP_SLOT_SEARCH)
        val dismissSlot = glassGroup.slot(GROUP_SLOT_DISMISS)
        val accessorySlot = glassGroup.slot(GROUP_SLOT_ACCESSORY)
        // Hanya ambang muncul/hilang yang sampai ke komposisi, bukan nilai tiap frame.
        val accessoryShown by remember { derivedStateOf { accessoryAlpha.value > 0.01f } }
        val showAccessory = hasActiveTrack && accessoryShown
        val showTabCapsule by remember { derivedStateOf { tabPillVisibility.value > 0.01f } }
        val showDismissPill by remember { derivedStateOf { dismissPillScale.value > 0.02f } }
        accessorySlot.bindOuterTransform {
            alpha = if (showAccessory) accessoryAlpha.value else 0f
        }
        tabSlot.bindOuterTransform {
            val visibility = tabPillVisibility.value
            alpha = if (showTabCapsule) visibility else 0f
            val scale = 0.85f + 0.15f * visibility
            scaleX = scale
            scaleY = scale * (1f + liquidBulge(tabWidthAnim.velocity))
        }
        // Saat amoebaMerge aktif, Search selalu hadir (1f) sebagai belahan kanan kapsul utuh saat
        // Normal (overlap tepat barHeightDp = 2*R -> rata 100%) dan membentuk leher amoeba saat memisah.
        searchSlot.bindOuterTransform {
            alpha = 1f
        }
        dismissSlot.bindOuterTransform {
            val scale = dismissPillScale.value
            alpha = if (showDismissPill) scale.coerceIn(0f, 1f) else 0f
            scaleX = scale
            scaleY = scale
        }

        // Jarak smooth-min dinamis (dp): besar (38dp) selama amoeba membelah/menyatu, lalu melandai
        // ke 0 tepat sebelum celah diam 14dp agar kapsul diam tetap tajam tanpa benjolan sisa.
        val isSearchActiveState by rememberUpdatedState(uiState.isSearchActive)
        val mergeK01Dp: () -> Float = {
            val gap01 = searchLeft.value - (tabLeft.value + tabWidthAnim.value)
            val snapFade = if (isSearchActiveState) {
                1f - smoothstep((gap01 - 10f) / 3.6f)
            } else {
                1f - smoothstep((gap01 - 24f) / 30f)
            }
            AMOEBA_MERGE_K_DP * snapFade
        }
        val mergeK12Dp: () -> Float = {
            val dismissLeft = totalWidthDp - barHeight.value
            val gap12 = dismissLeft - (searchLeft.value + searchWidth.value)
            val snapFade = 1f - smoothstep((gap12 - 10f) / 3.6f)
            AMOEBA_MERGE_K_DP * snapFade
        }
        val mergeKAccDp: () -> Float = { ACCESSORY_MERGE_K_DP }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .layoutSizeDp(height = ::containerHeightDp)
                .sharedGlassBackdropSource(
                    shared = sharedBackdrop,
                    regionHeightPx = sharedRegionHeightPx,
                    marginPx = sharedRegionMarginPx
                )
        ) {
            // ── 0. Permukaan kaca gabungan (di bawah konten semua elemen) ──
            LiquidGlassGroupSurface(
                group = glassGroup,
                sharedBackdrop = sharedBackdrop,
                backdropState = backdropState,
                settings = barGlassSettings,
                isDark = isDark,
                mergeK01Dp = mergeK01Dp,
                mergeK12Dp = mergeK12Dp,
                mergeKAccDp = mergeKAccDp,
                modifier = Modifier.matchParentSize()
            )

            // Setiap elemen dibungkus Box tanpa transformasi yang mencatat posisinya ke slot grup;
            // transformasi (skala, alpha) dipasang di dalamnya dan dilaporkan lewat bind*Transform.

            // ── 1. MiniPlayerPill Tunggal (Meluncur antara EXPANDED di atas & INLINE di tengah) ──
            if (showAccessory) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .layout { measurable, constraints ->
                            val bounds = accessoryBounds()
                            val left = bounds.leftInsetDp.dp.roundToPx()
                            val right = bounds.rightInsetDp.dp.roundToPx()
                            val bottom = bounds.bottomOffsetDp.dp.roundToPx()
                            val height = bounds.heightDp.dp.roundToPx().coerceAtLeast(0)
                            val width = (constraints.maxWidth - left - right).coerceAtLeast(0)
                            val placeable = measurable.measure(Constraints.fixed(width, height))
                            val layoutHeight = constraints.constrainHeight(height + bottom)
                            layout(constraints.maxWidth, layoutHeight) {
                                placeable.place(left, layoutHeight - bottom - height)
                            }
                        }
                        .liquidGlassGroupSlot(accessorySlot)
                ) {
                    MiniPlayerPill(
                        song = currentSong,
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        isDark = isDark,
                        backdropState = backdropState,
                        glassSettings = pillGlassSettings,
                        glassMode = glassMode,
                        sharedBackdrop = sharedBackdrop,
                        shadowAlpha = shadowAlpha,
                        lensStrength = lensStrength,
                        onTogglePlay = onTogglePlay,
                        onNextTrack = onNextTrack,
                        onTapPill = onTapMiniPlayer,
                        playerDock = playerDock,
                        barHeight = null,
                        groupSlot = accessorySlot,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = accessoryAlpha.value }
                    )
                }
            }

            // ── 2. Kapsul Tab Kiri (Morphing 3-Tab Slide Capsule <-> Orb Ikon Tab Aktif) ──
            if (showTabCapsule) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(tabLeft.value.dp.roundToPx(), -rowLift.value.dp.roundToPx()) }
                        .zIndex(if (uiState.isTabCapsuleCollapsed) 0f else 1f)
                        .layoutSizeDp(
                            width = { tabWidthAnim.value.coerceAtLeast(1f) },
                            height = { barHeight.value }
                        )
                        .liquidGlassGroupSlot(tabSlot)
                ) {
                    MorphingGlassTabsCapsule(
                        selectedTab = uiState.selectedTab,
                        searchSelected = uiState.isSearchCovered,
                        isCollapsed = uiState.isTabCapsuleCollapsed,
                        isMiniMode = uiState.isMiniMode && !uiState.isSearchActive,
                        expandFraction = tabExpandFraction,
                        widthDp = { tabWidthAnim.value.coerceAtLeast(1f) },
                        barHeightDp = { barHeight.value },
                        fullBarWidthDp = totalWidthDp,
                        ownsSearchIcon = !amoebaMerge,
                        isDark = isDark,
                        backdropState = backdropState,
                        glassSettings = barGlassSettings,
                        glassMode = glassMode,
                        sharedBackdrop = sharedBackdrop,
                        shadowAlpha = shadowAlpha,
                        lensStrength = lensStrength,
                        onTabSelected = onTabSelected,
                        onSearchTap = { onSearchToggle(true) },
                        onCollapsedTap = {
                            if (uiState.isSearchActive) {
                                focusManager.clearFocus()
                                onSearchToggle(false)
                            } else {
                                onDismissMiniMode()
                            }
                        },
                        groupSlot = tabSlot,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                clip = false
                                val visibility = tabPillVisibility.value
                                alpha = visibility
                                val scale = 0.85f + 0.15f * visibility
                                scaleX = scale
                                // Kaca cair menjaga volumenya: menggembung saat menciut cepat, sedikit
                                // memipih saat memekar, lalu kembali bulat begitu pegasnya berhenti.
                                scaleY = scale * (1f + liquidBulge(tabWidthAnim.velocity))
                            }
                    )
                }
            }

            // ── 3. Kapsul Search Kanan (Morphing Search Slot/Orb <-> Expanded Search Field) ──
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(searchLeft.value.dp.roundToPx(), -rowLift.value.dp.roundToPx()) }
                    .zIndex(if (uiState.isTabCapsuleCollapsed) 1f else 0f)
                    .layoutSizeDp(
                        width = { searchWidth.value.coerceAtLeast(1f) },
                        height = { barHeight.value }
                    )
                    .liquidGlassGroupSlot(searchSlot)
            ) {
                MorphingGlassSearchPill(
                    query = uiState.searchQuery,
                    isSearchActive = uiState.isSearchActive,
                    isSearchFocused = uiState.isSearchFocused,
                    currentWidthDp = { searchWidth.value },
                    amoebaMerge = amoebaMerge,
                    tabExpandFraction = tabExpandFraction,
                    searchSelected = uiState.isSearchCovered,
                    fullBarWidthDp = totalWidthDp,
                    isDark = isDark,
                    backdropState = backdropState,
                    glassSettings = barGlassSettings,
                    glassMode = glassMode,
                    sharedBackdrop = sharedBackdrop,
                    shadowAlpha = shadowAlpha,
                    lensStrength = lensStrength,
                    focusRequester = focusRequester,
                    onExpandSearch = { onSearchToggle(true) },
                    onQueryChanged = onSearchQueryChanged,
                    onFocusChanged = onSearchFocusChanged,
                    onCloseSearch = {
                        focusManager.clearFocus()
                        onSearchToggle(false)
                    },
                    onSubmit = {
                        onSearchSubmit()
                        focusManager.clearFocus()
                    },
                    placeholder = searchPlaceholder,
                    groupSlot = searchSlot,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val visibility = searchOrbVisibility()
                            alpha = visibility
                            val scale = if (searchSlot != null) 1f else (0.85f + 0.15f * visibility)
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }

            // ── 4. Tombol Batal Kaca Bulat Terpisah (DismissPill × di Kanan Saat Fokus) ──
            if (showDismissPill) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .layoutSizeDp(width = { barHeight.value }, height = { barHeight.value })
                        .liquidGlassGroupSlot(dismissSlot)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = dismissPillScale.value
                                scaleX = scale
                                scaleY = scale
                                alpha = scale.coerceIn(0f, 1f)
                            }
                    ) {
                        GlassCircleOrbButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.search_cancel),
                            iconTint = if (isDark) Color.White else Color(0xFF141416),
                            isDark = isDark,
                            backdropState = backdropState,
                            glassSettings = barGlassSettings,
                            glassMode = glassMode,
                            sharedBackdrop = sharedBackdrop,
                            groupSlot = dismissSlot,
                            onClick = {
                                focusManager.clearFocus()
                                onSearchToggle(false)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Letak mini player pada satu frame morph, lihat [SearchableBarMorphCalculator.computeAccessoryMorphFrame]. */
private class AccessoryBounds(
    val leftInsetDp: Float,
    val rightInsetDp: Float,
    val bottomOffsetDp: Float,
    val heightDp: Float,
    val totalContainerHeightDp: Float
)

/**
 * `width()` / `height()` yang nilainya dibaca saat layout, bukan saat komposisi: ukuran yang
 * berubah tiap frame animasi cukup mengukur ulang elemen ini tanpa recompose. `null` = sumbu itu
 * mengikuti batasan dari induk.
 */
private fun Modifier.layoutSizeDp(
    width: (() -> Float)? = null,
    height: (() -> Float)? = null
): Modifier = layout { measurable, constraints ->
    val w = width?.let { constraints.constrainWidth(it().dp.roundToPx()) }
    val h = height?.let { constraints.constrainHeight(it().dp.roundToPx()) }
    val placeable = measurable.measure(
        Constraints(
            minWidth = w ?: constraints.minWidth,
            maxWidth = w ?: constraints.maxWidth,
            minHeight = h ?: constraints.minHeight,
            maxHeight = h ?: constraints.maxHeight
        )
    )
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * Kapsul tab kiri dengan perilaku 1:1 `LiquidBottomTabs` di Kyant0/AndroidLiquidGlass
 * (`catalog/components/LiquidBottomTabs.kt`):
 *
 * - **Tap tab**: indikator meluncur dengan pegas `DampedDragAnimation` sambil lensanya
 *   mengembang (`pressProgress`), lalu mengempis saat hampir tiba di tab tujuan.
 * - **Tekan & geser indikator**: indikator langsung mengembang (`78 / 56`), mengikuti jari secara
 *   kontinu dengan *jelly squash & stretch* dari kecepatannya, lalu *snap* ke tab terdekat saat dilepas.
 * - **Panel bergeser** maks `4.dp` searah tarikan dan memantul kembali; seluruh kapsul membesar
 *   `+16.dp` selama lensa aktif; kilau `InteractiveHighlight` mengikuti posisi indikator.
 * - **Ikon beraksen di dalam lensa**: baris tab beraksen yang tidak terlihat direkam ke
 *   `GraphicsLayer` (setara `layerBackdrop`) dan dibiaskan oleh lensa indikator, sehingga hanya
 *   bagian ikon yang tertutup lensa yang berwarna aksen dan membesar `1.2x`.
 *
 * Saat `isCollapsed == true` (scroll ke Mini Mode atau membuka Search), kapsul menyusut
 * menjadi orb `50.dp` berikon tab aktif (beraksen saat Mini Mode, polos saat Search).
 */
@Composable
private fun MorphingGlassTabsCapsule(
    selectedTab: MusicTab,
    /** Search terpilih di kapsul: indikator diam di slot Search, lihat [NavbarUiState.isSearchCovered]. */
    searchSelected: Boolean = false,
    isCollapsed: Boolean,
    isMiniMode: Boolean,
    /** Dibaca hanya di layout/draw, seperti [widthDp] dan [barHeightDp]: berubah tiap frame morph. */
    expandFraction: () -> Float,
    widthDp: () -> Float,
    barHeightDp: () -> Float,
    /** Lebar penuh baris bar (untuk grid 4 slot saat amoebaMerge aktif dan kapsul tab 310.dp). */
    fullBarWidthDp: Float,
    /** `false` saat amoebaMerge aktif: ikon Search tunggal digambar oleh MorphingGlassSearchPill. */
    ownsSearchIcon: Boolean = true,
    isDark: Boolean,
    backdropState: LiquidGlassBackdropState,
    glassSettings: LiquidGlassSettings,
    glassMode: GlassEffectMode,
    sharedBackdrop: SharedGlassBackdrop?,
    shadowAlpha: () -> Float,
    lensStrength: () -> Float,
    onTabSelected: (MusicTab) -> Unit,
    onSearchTap: () -> Unit,
    onCollapsedTap: () -> Unit,
    groupSlot: LiquidGlassGroupSlot? = null,
    modifier: Modifier = Modifier
) {
    val tabCount = MusicTab.entries.size
    // Slot terakhir milik Search: ikut membagi lebar. Indikator boleh digeser ke sana
    // (lensanya membiaskan ikon Search), tetapi dilepas di sana membuka pencarian, bukan tab.
    val slotCount = tabCount + 1
    val searchSlot = tabCount
    val density = LocalDensity.current
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val currentOnCollapsedTap by rememberUpdatedState(onCollapsedTap)
    val currentOnSearchTap by rememberUpdatedState(onSearchTap)
    // Slot tempat indikator diam: tab aktif, atau Search saat halaman hasil pencarian terbuka.
    val restIndex = if (searchSelected) searchSlot else selectedTab.index
    val currentRestIndex by rememberUpdatedState(restIndex)

    val contentColor = if (isDark) Color.White else Color.Black
    val accentColor = if (isDark) Color(0xFF0091FF) else Color(0xFF0088FF)

    val tabsLayer = rememberGraphicsLayer()
    var groupOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            groupOriginInRoot = coords.positionInRoot()
        }
    ) {
        val pad4Px = with(density) { 4.dp.toPx() }
        val currentWidthDp by rememberUpdatedState(widthDp)
        val currentBarHeightDp by rememberUpdatedState(barHeightDp)
        val widthPx: () -> Float = { with(density) { currentWidthDp().dp.toPx() } }
        val gridWidthDp: () -> Float = { if (ownsSearchIcon) currentWidthDp() else fullBarWidthDp }
        val gridWidthPx: () -> Float = { with(density) { gridWidthDp().dp.toPx() } }
        val tabWidthPx: () -> Float = { ((gridWidthPx() - pad4Px * 2f) / slotCount).coerceAtLeast(1f) }
        // Kyant0: bar 64.dp dengan padding 4.dp, indikator & baris beraksen 56.dp
        val rowHeightPx: () -> Float = {
            (with(density) { currentBarHeightDp().dp.toPx() } - pad4Px * 2f).coerceAtLeast(1f)
        }

        val animationScope = rememberCoroutineScope()
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / widthPx().coerceAtLeast(1f)).coerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() } * sign(fraction) * EaseOut.transform(abs(fraction))
            }
        }

        var currentIndex by remember { mutableIntStateOf(restIndex) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = restIndex.toFloat(),
                valueRange = 0f..searchSlot.toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.roundToInt().coerceIn(0, searchSlot)
                    if (targetIndex == searchSlot) {
                        // Indikator kembali ke tab aktif sementara kapsul berubah jadi pencarian.
                        animateToValue(currentIndex.toFloat())
                        currentOnSearchTap()
                    } else {
                        currentIndex = targetIndex
                        animateToValue(targetIndex.toFloat())
                    }
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    // Sentuhan dibaca di dalam layer indikator yang membesar saat ditekan,
                    // jadi gerak jari tiba mengecil ~1/1.39; dikembalikan ke skala layar agar
                    // lensa tetap di bawah jari dan bisa mencapai slot Search.
                    val fingerDx = dragAmount.x * indicatorLayerScaleX(this)
                    updateValue(
                        (targetValue + fingerDx / tabWidthPx())
                            .coerceIn(0f, searchSlot.toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + fingerDx)
                    }
                }
            )
        }
        LaunchedEffect(restIndex) {
            currentIndex = restIndex
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    if (index != currentRestIndex && index != searchSlot) {
                        currentOnTabSelected(MusicTab.fromIndex(index))
                    }
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        (dampedDragAnimation.value + 0.5f) * tabWidthPx() + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // Tekan orb saat kapsul menciut (Mini Mode / Search)
        var orbIsDown by remember { mutableStateOf(false) }
        val orbPressScale by animateFloatAsState(
            targetValue = if (orbIsDown && isCollapsed) ORB_PRESSED_SCALE else 1f,
            animationSpec = OrbPressSpring,
            label = "orbPressScale"
        )
        // Bentuk kaca grup mengikuti transformasi kapsul di bawah (geser panel, ekspansi lensa, tekan).
        val expandPx = with(density) { 16.dp.toPx() }
        groupSlot.bindInnerTransform {
            translationX = panelOffset
            val progress = if (isCollapsed) 0f else dampedDragAnimation.pressProgress
            val scale = lerp(1f, 1f + expandPx / size.width.coerceAtLeast(1f), progress) * orbPressScale
            scaleX = scale
            scaleY = scale
        }
        var orbTouchOffset by remember { mutableStateOf(Offset.Unspecified) }

        // Isi kapsul bergantian, tidak bertumpuk: deretan tab habis memudar (sambil mengerut ke
        // dalam kaca) sebelum ikon orb muncul, jadi tidak ada empat ikon pucat di bawah ikon orb.
        val currentExpandFraction by rememberUpdatedState(expandFraction)
        val tabsContentAlpha: () -> Float = { ((currentExpandFraction() - 0.3f) / 0.45f).coerceIn(0f, 1f) }
        val collapsedLogoAlpha: () -> Float = { (1f - currentExpandFraction() / 0.3f).coerceIn(0f, 1f) }
        val showTabs by remember { derivedStateOf { tabsContentAlpha() > 0.01f } }
        val showCollapsedLogo by remember { derivedStateOf { collapsedLogoAlpha() > 0.01f } }
        // Baris aksen + indikator lensa hanya berguna saat tab masih cukup terlihat.
        // Melewatinya di akhir morph menghemat satu rekaman tabsLayer + draw contentLayer.
        val showTabLayers by remember { derivedStateOf { tabsContentAlpha() > 0.25f } }

        // ── 1. Kapsul kaca utama (bar) ──
        LiquidGlassBox(
            backdropState = backdropState,
            settings = glassSettings,
            mode = glassMode,
            isDark = isDark,
            cornerRadius = CapsuleCornerRadius,
            touchOffset = orbTouchOffset,
            sharedBackdrop = sharedBackdrop,
            shadowAlpha = shadowAlpha,
            lensStrength = lensStrength,
            drawSurface = groupSlot == null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = panelOffset
                    val progress = if (isCollapsed) 0f else dampedDragAnimation.pressProgress
                    val scale = lerp(1f, 1f + expandPx / size.width.coerceAtLeast(1f), progress) * orbPressScale
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if (isCollapsed) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                orbIsDown = true
                                orbTouchOffset = down.position
                                var upChange: androidx.compose.ui.input.pointer.PointerInputChange? = null
                                var wasCancelled = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        wasCancelled = true
                                        break
                                    }
                                    orbTouchOffset = change.position
                                    if (!change.pressed) {
                                        upChange = change
                                        break
                                    }
                                }
                                orbIsDown = false
                                orbTouchOffset = Offset.Unspecified
                                val releasePos = upChange?.position
                                val inBounds = !wasCancelled &&
                                    releasePos != null &&
                                    releasePos.x in -20f..(size.width + 20f) &&
                                    releasePos.y in -20f..(size.height + 20f)
                                if (inBounds) {
                                    currentOnCollapsedTap()
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showTabs) interactiveHighlight.modifier else Modifier)
            ) {
                // A. Baris tab biasa (warna konten polos, dapat diketuk)
                if (showTabs) {
                    val threeTabWidthDp = 4f + ((fullBarWidthDp - 8f) / slotCount) * tabCount
                    Row(
                        modifier = Modifier
                            .then(
                                if (ownsSearchIcon) {
                                    Modifier.fillMaxSize().padding(4.dp)
                                } else {
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .width(threeTabWidthDp.dp)
                                        .fillMaxHeight()
                                        .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                                }
                            )
                            .graphicsLayer {
                                val contentAlpha = tabsContentAlpha()
                                alpha = contentAlpha
                                // Alpha per-op, bukan layer offscreen per frame selama fade (ikon & label tidak bertumpuk)
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                val s = 0.85f + 0.15f * contentAlpha
                                scaleX = s
                                scaleY = s
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MusicTab.entries.forEach { tab ->
                            KyantBottomTab(
                                tab = tab,
                                color = contentColor,
                                onClick = if (isCollapsed) null else ({ currentIndex = tab.index })
                            )
                        }
                        if (ownsSearchIcon) {
                            KyantBottomItem(
                                icon = Icons.Rounded.Search,
                                label = stringResource(R.string.search),
                                color = contentColor,
                                onClick = if (isCollapsed) null else onSearchTap
                            )
                        }
                    }
                }

                // B. Ikon saat kapsul menciut jadi orb (ikon tab aktif: beraksen saat scroll, polos saat search)
                if (showCollapsedLogo) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val logoAlpha = collapsedLogoAlpha()
                                alpha = logoAlpha
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                val s = 0.75f + 0.25f * logoAlpha
                                scaleX = s
                                scaleY = s
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isMiniMode,
                            transitionSpec = {
                                (fadeIn(tween(180)) + scaleIn(initialScale = 0.75f, animationSpec = tween(180)))
                                    .togetherWith(
                                        fadeOut(tween(140)) + scaleOut(targetScale = 0.75f, animationSpec = tween(140))
                                    )
                            },
                            label = "collapsedOrbLogoTransition"
                        ) { mini ->
                            // Seperti tab bar iOS 26: orb menampilkan ikon tab aktif (beraksen saat
                            // minimize), jadi pengguna tetap tahu sedang di tab mana.
                            Icon(
                                imageVector = if (searchSelected) Icons.Rounded.Search else selectedTab.iconVector(filled = true),
                                contentDescription = stringResource(if (mini) R.string.navbar_expand else R.string.search_close),
                                tint = if (mini) accentColor else contentColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showTabLayers) {
            // ── 2. Baris tab beraksen tak terlihat, direkam ke tabsLayer (Kyant0 `layerBackdrop`) ──
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .layout { measurable, _ ->
                        val w = gridWidthPx().roundToInt().coerceAtLeast(1)
                        val h = with(density) { currentBarHeightDp().dp.roundToPx() }.coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .padding(vertical = 4.dp)
                    .clearAndSetSemantics {}
                    .graphicsLayer { translationX = panelOffset }
                    .drawWithContent {
                        tabsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                    }
            ) {
                LiquidGlassBox(
                    backdropState = backdropState,
                    settings = glassSettings.copy(
                        depthEffect = false,
                        innerShadowAlpha = 0f,
                        drawSpecularRim = false
                    ),
                    mode = glassMode,
                    isDark = isDark,
                    cornerRadius = CapsuleCornerRadius,
                    refractionProgress = { dampedDragAnimation.pressProgress },
                    sharedBackdrop = sharedBackdrop,
                    shadowAlpha = shadowAlpha,
                    lensStrength = lensStrength,
                    modifier = Modifier
                        .fillMaxSize()
                        .kyantHighlight(alpha = { dampedDragAnimation.pressProgress })
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(interactiveHighlight.modifier)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MusicTab.entries.forEach { tab ->
                            KyantBottomTab(
                                tab = tab,
                                color = accentColor,
                                onClick = null,
                                scale = { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) },
                                filled = true
                            )
                        }
                        // Bukan tab: beraksen hanya saat halaman hasil pencarian sedang terbuka.
                        KyantBottomItem(
                            icon = Icons.Rounded.Search,
                            label = stringResource(R.string.search),
                            color = if (searchSelected) accentColor else contentColor,
                            onClick = null,
                            scale = { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) }
                        )
                    }
                }
            }

            // ── 3. Indikator lensa kaca yang dapat ditekan & digeser ──
            LiquidGlassTabIndicator(
                    backdropState = backdropState,
                    tabsLayer = tabsLayer,
                    groupOriginInRoot = { groupOriginInRoot },
                    tabsLayerOffset = { Offset(panelOffset, pad4Px) },
                    translation = {
                        Offset(pad4Px + dampedDragAnimation.value * tabWidthPx() + panelOffset, pad4Px)
                    },
                    scaleX = { indicatorLayerScaleX(dampedDragAnimation) },
                    scaleY = {
                        val velocity = dampedDragAnimation.velocity / 10f
                        dampedDragAnimation.scaleY * (1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f))
                    },
                    pressProgress = { dampedDragAnimation.pressProgress },
                    isDark = isDark,
                    mode = glassMode,
                    modifier = Modifier
                        .graphicsLayer { alpha = tabsContentAlpha() }
                        .then(
                            if (isCollapsed) {
                                Modifier
                            } else {
                                Modifier
                                    .then(interactiveHighlight.gestureModifier)
                                    .then(dampedDragAnimation.modifier)
                            }
                        )
                        .layout { measurable, constraints ->
                            val width = constraints.constrainWidth(tabWidthPx().roundToInt())
                            val height = constraints.constrainHeight(rowHeightPx().roundToInt())
                            val placeable = measurable.measure(Constraints.fixed(width, height))
                            layout(width, height) { placeable.place(0, 0) }
                        }
            )
        }
    }
}

/**
 * Padanan `LiquidBottomTab` Kyant0: kolom ikon `28.dp` + label `12.sp` yang mengisi satu slot,
 * tanpa indikasi tekan; [scale] dipakai baris beraksen untuk efek pembesaran di bawah lensa.
 */
@Composable
private fun RowScope.KyantBottomTab(
    tab: MusicTab,
    color: Color,
    onClick: (() -> Unit)?,
    scale: () -> Float = { 1f },
    filled: Boolean = false
) = KyantBottomItem(
    icon = tab.iconVector(filled),
    label = stringResource(tab.labelRes),
    color = color,
    onClick = onClick,
    scale = scale,
    role = Role.Tab
)

/** Satu slot kapsul tab: tab musik, atau Search yang menumpang di ujungnya. */
@Composable
private fun RowScope.KyantBottomItem(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: (() -> Unit)?,
    scale: () -> Float = { 1f },
    role: Role = Role.Button
) {
    Column(
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        role = role,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (onClick != null) label else null,
            tint = color,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

/**
 * Padanan `SearchPill` di `tab_bar_searchable_internal.dart`.
 *
 * Saat tidak aktif atau lebarnya masih `< 90.dp`, tampil sebagai orb bulat dengan ikon Search.
 * Saat memekar melampaui `90.dp`, memperlihatkan input teks pencarian dengan transisi alpha mulus.
 */
@Composable
private fun MorphingGlassSearchPill(
    query: String,
    isSearchActive: Boolean,
    isSearchFocused: Boolean,
    /** Lebar pil saat ini; dibaca hanya di draw dan untuk ambang tata letak, berubah tiap frame. */
    currentWidthDp: () -> Float,
    /** `true` saat amoebaMerge aktif: pil ini memegang satu-satunya ikon Search (slot 4 -> orb -> input). */
    amoebaMerge: Boolean = false,
    tabExpandFraction: () -> Float = { 0f },
    searchSelected: Boolean = false,
    fullBarWidthDp: Float = 0f,
    isDark: Boolean,
    backdropState: LiquidGlassBackdropState,
    glassSettings: LiquidGlassSettings,
    glassMode: GlassEffectMode,
    sharedBackdrop: SharedGlassBackdrop?,
    shadowAlpha: () -> Float,
    lensStrength: () -> Float,
    focusRequester: FocusRequester,
    onExpandSearch: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onCloseSearch: () -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
    groupSlot: LiquidGlassGroupSlot? = null,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var touchOffset by remember { mutableStateOf(Offset.Unspecified) }
    val currentOnExpandSearch by androidx.compose.runtime.rememberUpdatedState(onExpandSearch)
    val currentIsSearchActive by rememberUpdatedState(isSearchActive)
    val currentTabExpand by rememberUpdatedState(tabExpandFraction)

    val isFusedNormal by remember(amoebaMerge, isSearchActive) {
        derivedStateOf { amoebaMerge && !isSearchActive && currentTabExpand() > 0.5f }
    }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) {
            when {
                isFusedNormal -> 1.0f
                isSearchActive -> 1.03f
                else -> ORB_PRESSED_SCALE
            }
        } else {
            1.0f
        },
        animationSpec = OrbPressSpring,
        label = "searchPillPressScale"
    )
    groupSlot.bindInnerTransform {
        scaleX = pressScale
        scaleY = pressScale
    }

    val textColor = if (isDark) Color.White else Color(0xFF141416)
    val hintColor = if (isDark) Color.White.copy(alpha = 0.54f) else Color.Black.copy(alpha = 0.45f)
    val accentColor = if (isDark) Color(0xFF0091FF) else Color(0xFF0088FF)
    val collapsedIconColor = when {
        amoebaMerge && searchSelected && isFusedNormal -> accentColor
        amoebaMerge && isFusedNormal -> if (isDark) Color.White else Color.Black
        isDark -> Color.White.copy(alpha = 0.90f)
        else -> Color.Black.copy(alpha = 0.75f)
    }

    val widthDp by rememberUpdatedState(currentWidthDp)
    val expandedContentAlpha: () -> Float = {
        if (!currentIsSearchActive) {
            0f
        } else {
            val widthReady = ((widthDp() - SearchableBarMorphCalculator.SEARCH_EXPAND_CONTENT_THRESHOLD_DP) / 70f)
                .coerceIn(0f, 1f)
            val tabCleared = if (amoebaMerge) {
                1f - ((currentTabExpand() - 0.15f) / 0.30f).coerceIn(0f, 1f)
            } else {
                1f
            }
            (widthReady * tabCleared).coerceIn(0f, 1f)
        }
    }
    val collapsedIconAlpha: () -> Float = { (1f - expandedContentAlpha() * 1.5f).coerceIn(0f, 1f) }
    val slotLabelAlpha: () -> Float = {
        if (!amoebaMerge) {
            0f
        } else {
            (((currentTabExpand() - 0.35f) / 0.45f).coerceIn(0f, 1f) * (1f - expandedContentAlpha()))
                .coerceIn(0f, 1f)
        }
    }
    val wideEnough by remember {
        derivedStateOf { widthDp() >= SearchableBarMorphCalculator.SEARCH_EXPAND_CONTENT_THRESHOLD_DP }
    }
    val isExpandedLayout = isSearchActive && wideEnough
    val showCollapsedIcon by remember { derivedStateOf { collapsedIconAlpha() > 0.01f } }
    val showSlotLabel by remember { derivedStateOf { slotLabelAlpha() > 0.02f } }

    // Opening Search means typing: focus the field (and so raise the keyboard)
    // as soon as the pill has grown wide enough to hold it. Not when it opens on
    // a query already there: that is coming back to the results from a page
    // opened out of them, and the results, not the keyboard, are what you want.
    val currentQuery by rememberUpdatedState(query)
    LaunchedEffect(isExpandedLayout) {
        if (isExpandedLayout && currentQuery.isEmpty()) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }

    LiquidGlassBox(
        backdropState = backdropState,
        settings = glassSettings,
        mode = glassMode,
        isDark = isDark,
        cornerRadius = CapsuleCornerRadius,
        touchOffset = touchOffset,
        sharedBackdrop = sharedBackdrop,
        shadowAlpha = shadowAlpha,
        lensStrength = lensStrength,
        drawSurface = groupSlot == null,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .pointerInput(isSearchActive) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    touchOffset = down.position
                    var upChange: androidx.compose.ui.input.pointer.PointerInputChange? = null
                    var wasCancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            wasCancelled = true
                            break
                        }
                        if (change.isConsumed && change.pressed) {
                            wasCancelled = true
                            break
                        }
                        touchOffset = change.position
                        if (!change.pressed) {
                            upChange = change
                            break
                        }
                    }
                    isPressed = false
                    touchOffset = Offset.Unspecified
                    val releasePos = upChange?.position
                    val inside = !wasCancelled &&
                        releasePos != null &&
                        releasePos.x in -12f..(size.width + 12f) &&
                        releasePos.y in -12f..(size.height + 12f)
                    if (inside) {
                        if (!isSearchActive) {
                            currentOnExpandSearch()
                        } else {
                            focusRequester.requestFocus()
                        }
                    }
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Ikon Search tunggal: sejajar slot ke-4 (+ label) saat menyatu Normal, lalu
            //    bergeser mulus ke tengah orb saat Mini Mode sebelum memudar menjadi kolom input.
            if (showCollapsedIcon) {
                val slot4WidthDp = if (amoebaMerge && fullBarWidthDp > 0f) {
                    ((fullBarWidthDp - 8f) / (MusicTab.entries.size + 1)).coerceAtLeast(44f)
                } else {
                    0f
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .layout { measurable, constraints ->
                            val fusedBlend = if (amoebaMerge && slot4WidthDp > 0f) {
                                ((currentTabExpand() - 0.35f) / 0.65f).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val targetW = if (fusedBlend > 0.001f) {
                                lerp(constraints.maxWidth.toFloat(), slot4WidthDp.dp.toPx(), fusedBlend)
                                    .roundToInt()
                                    .coerceIn(1, constraints.maxWidth.coerceAtLeast(1))
                            } else {
                                constraints.maxWidth.coerceAtLeast(1)
                            }
                            val endPadPx = (4.dp.toPx() * fusedBlend).roundToInt()
                            val placeable = measurable.measure(
                                Constraints.fixed(targetW, constraints.maxHeight.coerceAtLeast(1))
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                val x = (constraints.maxWidth - targetW - endPadPx).coerceAtLeast(0)
                                placeable.place(x, 0)
                            }
                        }
                        .graphicsLayer {
                            alpha = collapsedIconAlpha()
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.search_field_description),
                            tint = collapsedIconColor,
                            modifier = Modifier.size(if (showSlotLabel) 28.dp else 24.dp)
                        )
                        if (showSlotLabel) {
                            Text(
                                text = stringResource(R.string.search),
                                color = collapsedIconColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                                modifier = Modifier.graphicsLayer {
                                    alpha = slotLabelAlpha()
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                }
                            )
                        }
                    }
                }
            }

            // 2. Baris Input Pencarian saat memekar (>= 90.dp)
            if (isExpandedLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp)
                        .graphicsLayer {
                            alpha = expandedContentAlpha()
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = hintColor,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = hintColor,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChanged,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = textColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(Color(0xFFFF2D55)),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { onFocusChanged(it.isFocused) }
                        )
                    }

                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChanged("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.clear_search),
                                tint = hintColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (!isSearchFocused) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.cancel),
                            color = textColor.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onCloseSearch
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCircleOrbButton(
    icon: ImageVector,
    contentDescription: String,
    iconTint: Color,
    isDark: Boolean,
    backdropState: LiquidGlassBackdropState,
    glassSettings: LiquidGlassSettings,
    glassMode: GlassEffectMode,
    sharedBackdrop: SharedGlassBackdrop? = null,
    groupSlot: LiquidGlassGroupSlot? = null,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    var touchOffset by remember { mutableStateOf(Offset.Unspecified) }
    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)

    val scale by animateFloatAsState(
        targetValue = if (isPressed) ORB_PRESSED_SCALE else 1.0f,
        animationSpec = OrbPressSpring,
        label = "orbButtonScale"
    )
    groupSlot.bindInnerTransform {
        scaleX = scale
        scaleY = scale
    }

    LiquidGlassBox(
        backdropState = backdropState,
        settings = glassSettings,
        mode = glassMode,
        isDark = isDark,
        cornerRadius = CapsuleCornerRadius,
        touchOffset = touchOffset,
        sharedBackdrop = sharedBackdrop,
        drawSurface = groupSlot == null,
        // Ukurannya dari induk, yang mengikuti tinggi bar tiap frame tanpa recompose.
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    touchOffset = down.position
                    var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
                    var wasCancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            wasCancelled = true
                            break
                        }
                        if (change.isConsumed && change.pressed) {
                            wasCancelled = true
                            break
                        }
                        touchOffset = change.position
                        if (!change.pressed) {
                            up = change
                            break
                        }
                    }
                    isPressed = false
                    touchOffset = Offset.Unspecified
                    val releasePos = up?.position
                    if (!wasCancelled &&
                        releasePos != null &&
                        releasePos.x in -12f..(this.size.width + 12f) &&
                        releasePos.y in -12f..(this.size.height + 12f)
                    ) {
                        currentOnClick()
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Ikon Cupertino seperti tab bar referensi Flutter: versi garis untuk tab biasa,
 * versi terisi ([filled]) untuk tab di bawah lensa dan orb tab aktif.
 */
private fun MusicTab.iconVector(filled: Boolean = false): ImageVector = when (this) {
    MusicTab.HOME -> if (filled) BitChordIcons.TabHouseFill else BitChordIcons.TabHouse
    MusicTab.EXPLORE -> if (filled) BitChordIcons.TabCompassFill else BitChordIcons.TabCompass
    MusicTab.LIBRARY -> if (filled) BitChordIcons.TabAlbumsFill else BitChordIcons.TabAlbums
}

/** Skala horizontal layer indikator: membesar saat ditekan, memanjang mengikuti kecepatan. */
private fun indicatorLayerScaleX(animation: DampedDragAnimation): Float {
    val velocity = animation.velocity / 10f
    return animation.scaleX / (1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f))
}
