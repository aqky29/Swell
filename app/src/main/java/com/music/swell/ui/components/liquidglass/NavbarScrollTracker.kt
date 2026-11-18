package com.music.swell.ui.components.liquidglass

import androidx.annotation.StringRes
import com.music.swell.R

/**
 * Representasi tab utama pada navbar (padanan `_tabs` di `HomeScreen` MusikkU).
 */
enum class MusicTab(val index: Int, @StringRes val labelRes: Int) {
    HOME(0, R.string.home),
    EXPLORE(1, R.string.explore),
    LIBRARY(2, R.string.library);

    companion object {
        fun fromIndex(index: Int): MusicTab =
            entries.firstOrNull { it.index == index } ?: HOME
    }
}

/**
 * Penempatan aksesori MiniPlayerPill terhadap tab bar
 * (padanan `GlassTabBarAccessoryPlacement` di `liquid_glass_widgets`).
 */
enum class AccessoryPlacement {
    /** MiniPlayerPill mengambang penuh di atas tab bar (mode normal). */
    EXPANDED,

    /** MiniPlayerPill menyatu sebaris di samping tombol tab yang menciut (mode mini saat scroll turun). */
    INLINE
}

/**
 * Snapshot state dari `LiquidGlassSearchableTabBar`.
 */
data class NavbarUiState(
    val selectedTab: MusicTab = MusicTab.HOME,
    val isMiniMode: Boolean = false,
    val isSearchActive: Boolean = false,
    val isSearchFocused: Boolean = false,
    val searchQuery: String = "",
    val hasActiveTrack: Boolean = true,
    /**
     * Halaman (album, artis, ...) dibuka dari hasil pencarian. Bar kembali jadi tab biasa
     * dengan Search sebagai item terpilih; mengetuknya kembali ke hasil, bukan pencarian baru.
     */
    val isSearchCovered: Boolean = false
) {
    /**
     * Padanan `isSearchActive: _miniMode || _searching` di `home_screen.dart`:
     * saat `_miniMode` atau `_searching` aktif, kapsul tab menciut jadi tombol bulat di kiri.
     */
    val isTabCapsuleCollapsed: Boolean
        get() = isMiniMode || isSearchActive

    /**
     * Padanan `bottomAccessoryPlacement: _miniMode && !_searching ? inline : expanded`
     * di `home_screen.dart`.
     */
    val accessoryPlacement: AccessoryPlacement
        get() = if (isMiniMode && !isSearchActive) {
            AccessoryPlacement.INLINE
        } else {
            AccessoryPlacement.EXPANDED
        }

    /**
     * Padanan `bottomAccessoryEnabled: hasTrack && !_searchFocused` di `home_screen.dart`.
     */
    val isMiniPlayerVisible: Boolean
        get() = hasActiveTrack && !isSearchFocused
}

/**
 * State machine scroll histeresis & transisi navbar yang mereplikasi logika
 * `_onPhoneTabScroll`, `_dismissMiniMode`, `onTabSelected`, dan `onSearchToggle`
 * dari `lib/screens/home_screen.dart` di MusikkU.
 */
class NavbarScrollTracker(
    initialTab: MusicTab = MusicTab.HOME,
    val miniDownThresholdPx: Float = DEFAULT_MINI_DOWN_THRESHOLD_PX,
    val miniUpThresholdPx: Float = DEFAULT_MINI_UP_THRESHOLD_PX,
    val topSafeZonePx: Float = DEFAULT_TOP_SAFE_ZONE_PX
) {
    private var scrollDownAccum: Float = 0f
    private var scrollUpAccum: Float = 0f

    var state: NavbarUiState = NavbarUiState(selectedTab = initialTab)
        private set

    /**
     * Memproses delta scroll konten vertikal.
     * Konvensi [scrollDeltaPx] sama dengan `ScrollUpdateNotification.scrollDelta` di Flutter:
     * - `scrollDeltaPx > 0`: pengguna menggulir ke bawah (konten bergerak ke atas, `scrollOffsetPx` bertambah).
     * - `scrollDeltaPx < 0`: pengguna menggulir ke atas (konten bergerak ke bawah, `scrollOffsetPx` berkurang).
     *
     * @return `true` bila `isMiniMode` berubah akibat scroll ini.
     */
    fun onScrollDelta(scrollDeltaPx: Float, scrollOffsetPx: Float): Boolean {
        if (state.isSearchActive || scrollDeltaPx == 0f) return false

        val safeOffset = scrollOffsetPx.coerceAtLeast(0f)
        if (scrollDeltaPx > 0f) {
            // Arah berbalik: akumulasi naik direset agar tidak terbawa
            scrollUpAccum = 0f
            scrollDownAccum += scrollDeltaPx
            if (!state.isMiniMode &&
                safeOffset > topSafeZonePx &&
                scrollDownAccum >= miniDownThresholdPx
            ) {
                scrollDownAccum = 0f
                state = state.copy(isMiniMode = true)
                return true
            }
        } else {
            scrollDownAccum = 0f
            scrollUpAccum += -scrollDeltaPx
            val nearTop = safeOffset <= topSafeZonePx
            if (state.isMiniMode && (nearTop || scrollUpAccum >= miniUpThresholdPx)) {
                scrollUpAccum = 0f
                state = state.copy(isMiniMode = false)
                return true
            }
        }
        return false
    }

    /**
     * Membentangkan kembali navbar dari mini mode di posisi scroll saat ini
     * (padanan `_dismissMiniMode` di `home_screen.dart`).
     */
    fun dismissMiniMode() {
        scrollDownAccum = 0f
        scrollUpAccum = 0f
        if (state.isMiniMode) {
            state = state.copy(isMiniMode = false)
        }
    }

    /**
     * Saat tab dipilih (atau tombol kapsul kiri diketuk saat mini mode):
     * - Bila di mini mode (tanpa search) dan mengetuk tab aktif / tombol chevron, hanya keluar dari mini mode.
     * - Selain itu berpindah ke [tab] dan mereset search & mini mode.
     */
    fun onTabSelected(tab: MusicTab) {
        if (tab == state.selectedTab && state.isMiniMode && !state.isSearchActive) {
            dismissMiniMode()
            return
        }
        scrollDownAccum = 0f
        scrollUpAccum = 0f
        state = state.copy(
            selectedTab = tab,
            isMiniMode = false,
            isSearchActive = false,
            isSearchFocused = false,
            searchQuery = ""
        )
    }

    /**
     * Membuka atau menutup mode pencarian (`onSearchToggle` di `home_screen.dart`).
     */
    fun setSearchActive(active: Boolean) {
        scrollDownAccum = 0f
        scrollUpAccum = 0f
        state = if (active) {
            state.copy(isSearchActive = true)
        } else {
            state.copy(
                isSearchActive = false,
                isMiniMode = false,
                isSearchFocused = false,
                searchQuery = ""
            )
        }
    }

    fun onSearchFocusChanged(focused: Boolean) {
        state = state.copy(
            isSearchFocused = focused,
            isSearchActive = if (focused) true else state.isSearchActive
        )
    }

    fun onSearchQueryChanged(query: String) {
        state = state.copy(searchQuery = query)
    }

    fun setHasActiveTrack(hasTrack: Boolean) {
        state = state.copy(hasActiveTrack = hasTrack)
    }

    companion object {
        const val DEFAULT_MINI_DOWN_THRESHOLD_PX: Float = 50f
        const val DEFAULT_MINI_UP_THRESHOLD_PX: Float = 24f
        const val DEFAULT_TOP_SAFE_ZONE_PX: Float = 50f
    }
}

/**
 * Fisika indikator tab yang dapat di-slide/drag (padanan 1:1 dari
 * `DraggableIndicatorPhysics` di `lib/utils/draggable_indicator_physics.dart`
 * dan `TabDragGestureMixin` di `lib/src/widgets/surfaces/tab_bar_drag_gesture_mixin.dart`
 * pada package Flutter `liquid_glass_widgets`).
 */
object DraggableIndicatorPhysics {
    const val DEFAULT_RUBBER_BAND_RESISTANCE: Float = 0.4f
    const val DEFAULT_MAX_OVERDRAG: Float = 0.3f
    const val DEFAULT_VELOCITY_THRESHOLD: Float = 0.5f
    const val DEFAULT_MAX_DISTORTION: Float = 0.8f
    const val DEFAULT_VELOCITY_SCALE: Float = 10f
    const val DEFAULT_SWAY_VELOCITY_THRESHOLD_PX: Float = 4.0f
    const val DEFAULT_SWAY_SCALE: Float = 0.08f
    const val DEFAULT_MAX_SWAY_PX: Float = 0.75f

    /**
     * Memetakan indeks tab `(0 .. itemCount - 1)` ke koordinat alignment horizontal `[-1f, 1f]`.
     * Contoh untuk 3 tab: `0 -> -1.0f`, `1 -> 0.0f`, `2 -> +1.0f`.
     */
    fun computeAlignment(index: Int, itemCount: Int): Float {
        if (itemCount <= 1) return 0f
        val relativeIndex = (index.toFloat() / (itemCount - 1).toFloat()).coerceIn(0f, 1f)
        return (relativeIndex * 2f) - 1f
    }

    /**
     * Menerapkan resistensi karet (rubber-band resistance) bergaya iOS 26 saat
     * pengguna menarik indikator melewati batas kiri (`< 0f`) atau kanan (`> 1f`).
     */
    fun applyRubberBandResistance(
        value: Float,
        resistance: Float = DEFAULT_RUBBER_BAND_RESISTANCE,
        maxOverdrag: Float = DEFAULT_MAX_OVERDRAG
    ): Float {
        return when {
            value < 0f -> {
                val overdrag = -value
                val resisted = (overdrag * resistance).coerceIn(0f, maxOverdrag)
                -resisted
            }
            value > 1f -> {
                val overdrag = value - 1f
                val resisted = (overdrag * resistance).coerceIn(0f, maxOverdrag)
                1f + resisted
            }
            else -> value
        }
    }

    /**
     * Memetakan posisi jari lokal `localX` (dalam piksel terhadap lebar kapsul tab `widthPx`)
     * menjadi nilai alignment indikator `[-1f, 1f]` lengkap dengan *rubber-band overdrag*.
     */
    fun alignmentFromLocalPosition(
        localX: Float,
        widthPx: Float,
        itemCount: Int
    ): Float {
        if (itemCount <= 1 || widthPx <= 1f) return 0f
        val indicatorWidth = 1f / itemCount.toFloat()
        val draggableRange = (1f - indicatorWidth).coerceAtLeast(0.0001f)
        val padding = indicatorWidth / 2f

        val rawRelativeX = (localX / widthPx).coerceIn(0f, 1f)
        val normalizedX = (rawRelativeX - padding) / draggableRange
        val adjustedRelativeX = applyRubberBandResistance(normalizedX)
        return (adjustedRelativeX * 2f) - 1f
    }

    /**
     * Memetakan posisi ketukan diskrit (`onTapDown` / `onTapUp`) ke indeks tab `(0 .. itemCount - 1)`
     * dengan membagi lebar kapsul menjadi `itemCount` bagian sama rata.
     */
    fun tabIndexFromLocalPosition(
        localX: Float,
        widthPx: Float,
        itemCount: Int
    ): Int {
        if (itemCount <= 1 || widthPx <= 1f) return 0
        val fraction = localX / widthPx
        return kotlin.math.floor((fraction * itemCount).toDouble()).toInt().coerceIn(0, itemCount - 1)
    }

    /**
     * Menentukan tab tujuan saat jari dilepas dari drag (`onBarDragEnd` di `TabDragGestureMixin`),
     * menggabungkan posisi alignment terakhir `[-1f, 1f]` dan kecepatan lontaran (`velocityPxPerSec`).
     */
    fun resolveSnapTabIndex(
        tabXAlign: Float,
        velocityPxPerSec: Float,
        barWidthPx: Float,
        itemCount: Int,
        velocityThreshold: Float = DEFAULT_VELOCITY_THRESHOLD
    ): Int {
        if (itemCount <= 1) return 0
        val relX = (tabXAlign + 1f) / 2f
        val positionIndex = kotlin.math.round(relX * (itemCount - 1)).toInt().coerceIn(0, itemCount - 1)

        val rawVelX = if (barWidthPx > 1f && velocityPxPerSec.isFinite()) {
            velocityPxPerSec / barWidthPx
        } else {
            0f
        }

        return when {
            rawVelX > velocityThreshold && positionIndex < itemCount - 1 -> positionIndex + 1
            rawVelX < -velocityThreshold && positionIndex > 0 -> positionIndex - 1
            else -> positionIndex
        }
    }

    /**
     * Menghitung faktor distorsi *jelly squash & stretch* `(scaleX, scaleY)` berdasarkan
     * kecepatan pergerakan indikator (padanan `DraggableIndicatorPhysics.buildJellyTransform`).
     *
     * Saat bergerak cepat secara horizontal:
     * - `scaleX` sedikit memipih sesuai kecepatan (`1.0 - distortion * 0.5`)
     * - `scaleY` sedikit memuai tegak lurus arah gerak (`1.0 + distortion * 0.3`)
     */
    fun computeJellyScale(
        velocityUnitsPerSec: Float,
        maxDistortion: Float = DEFAULT_MAX_DISTORTION,
        velocityScale: Float = DEFAULT_VELOCITY_SCALE
    ): Pair<Float, Float> {
        val speed = kotlin.math.abs(velocityUnitsPerSec)
        if (speed == 0f || !speed.isFinite()) return 1f to 1f

        val distortionFactor = (speed / velocityScale).coerceIn(0f, 1f) * maxDistortion
        val scaleX = 1f - (distortionFactor * 0.5f)
        val scaleY = 1f + (distortionFactor * 0.3f)
        return scaleX to scaleY
    }

    /**
     * Menghitung goyangan lateral halus (`barSwayOffset`) pada badan kapsul tab
     * saat pil indikator digeser cepat (padanan `onBarDragUpdate` di `TabDragGestureMixin`).
     */
    fun computeBarSwayOffset(
        dragDeltaPx: Float,
        velocityThresholdPx: Float = DEFAULT_SWAY_VELOCITY_THRESHOLD_PX,
        swayScale: Float = DEFAULT_SWAY_SCALE,
        maxSwayPx: Float = DEFAULT_MAX_SWAY_PX
    ): Float {
        if (!dragDeltaPx.isFinite()) return 0f
        return if (kotlin.math.abs(dragDeltaPx) > velocityThresholdPx) {
            (dragDeltaPx * swayScale).coerceIn(-maxSwayPx, maxSwayPx)
        } else {
            0f
        }
    }

    /**
     * Stabilisasi kurva *lens pinch / fade* kuadratik `1 - (1 - f)^2`
     * (padanan `stablePinchFade` pada `AnimatedGlassIndicator`).
     */
    fun computeStableLensFade(thickness: Float): Float {
        val f = thickness.coerceIn(0f, 1f)
        return 1f - (1f - f) * (1f - f)
    }

    /**
     * Mengonversi parameter `durationMillis` dan `bounce` dari `GlassSpring` Flutter
     * (`SpringDescription.withDurationAndBounce`) menjadi pasangan `(stiffness, dampingRatio)`
     * untuk `spring()` Jetpack Compose:
     * - `stiffness = (2 * PI / durationSec)^2`
     * - `dampingRatio = 1 - bounce`
     *
     * Contoh preset `liquid_glass_widgets`:
     * - `GlassSpring.interactive(150ms, bounce = 0.14)` -> `stiffness ≈ 1754.6f`, `dampingRatio = 0.86f`
     * - `GlassSpring.snappy(350ms, bounce = 0.15)` -> `stiffness ≈ 322.3f`, `dampingRatio = 0.85f`
     * - `GlassSpring.snappy(300ms, bounce = 0.15)` -> `stiffness ≈ 438.6f`, `dampingRatio = 0.85f`
     * - `GlassSpring.smooth(250ms, bounce = 0.0)` -> `stiffness ≈ 631.7f`, `dampingRatio = 1.0f`
     */
    fun springParamsFromDurationAndBounce(
        durationMillis: Int,
        bounce: Float
    ): Pair<Float, Float> {
        val durationSec = (durationMillis.coerceAtLeast(1) / 1000.0)
        val omega = (2.0 * kotlin.math.PI) / durationSec
        val stiffness = (omega * omega).toFloat()
        val dampingRatio = (1f - bounce).coerceIn(0.05f, 2f)
        return stiffness to dampingRatio
    }

    /**
     * Menghitung faktor pembesaran ikon tab (`magnification`) dan intensitas warna
     * berdasarkan kedekatan dengan pusat lensa indikator (`alignment`) serta ketebalan aktif (`thickness`).
     *
     * Di `TabBarSearchableLayout._buildTabRow` (Flutter), ikon terpilih memiliki warna aktif penuh
     * saat diam (`thickness == 0f`) dan membesar secara proporsional terhadap `thickness`
     * (`lerpDouble(1.0, magnification, thickness)`) saat kapsul disentuh atau di-slide.
     */
    fun computeTabItemVisuals(
        tabIndex: Int,
        itemCount: Int,
        currentAlignment: Float,
        thickness: Float,
        maxMagnification: Float = 1.15f
    ): Pair<Float, Float> {
        val tabAlign = computeAlignment(tabIndex, itemCount)
        val step = if (itemCount > 1) 2f / (itemCount - 1).toFloat() else 2f
        val proximity = (1f - (kotlin.math.abs(currentAlignment - tabAlign) / step)).coerceIn(0f, 1f)
        val clampedThickness = thickness.coerceIn(0f, 1f)
        // Saat diam (thickness = 0), skala = 1.0f (tidak bengkak). Saat disentuh/di-slide (thickness -> 1),
        // tab yang berada di bawah lensa membesar hingga maxMagnification (1.15f).
        val scale = 1f + (maxMagnification - 1f) * proximity * clampedThickness
        return scale to proximity
    }

    /**
     * Menghitung batas geometri (dalam Dp relatif terhadap sudut kiri-atas kapsul tab)
     * untuk buble/pil indikator kaca aktif (`AnimatedGlassIndicator` di `liquid_glass_widgets`).
     *
     * Saat `thickness > 0f` (disentuh / di-slide), buble indikator memuai melampaui batas
     * kapsul (`leftDp < 0f`, `topDp < 0f`, `bottomDp > capsuleHeightDp`, `rightDp > capsuleWidthDp`)
     * sehingga menonjol keluar dari area kapsul navbar tanpa terpotong.
     */
    fun computeExpandedIndicatorBounds(
        capsuleWidthDp: Float,
        capsuleHeightDp: Float,
        itemCount: Int,
        currentAlignment: Float,
        thickness: Float,
        trackPaddingDp: Float = 4f,
        maxExpandHorizDp: Float = 15f,
        maxExpandVertDp: Float = 12f,
        jellyScaleX: Float = 1f,
        jellyScaleY: Float = 1f
    ): ExpandedIndicatorBounds {
        val safeCount = itemCount.coerceAtLeast(1)
        val trackWidthDp = (capsuleWidthDp - trackPaddingDp * 2f).coerceAtLeast(1f)
        val trackHeightDp = (capsuleHeightDp - trackPaddingDp * 2f).coerceAtLeast(1f)
        val slotWidthDp = trackWidthDp / safeCount.toFloat()
        val travelRangeDp = (trackWidthDp - slotWidthDp).coerceAtLeast(0f)

        val normalizedFraction = (currentAlignment + 1f) * 0.5f
        val baseLeftDp = trackPaddingDp + travelRangeDp * normalizedFraction

        val safeThickness = if (thickness.isFinite()) thickness.coerceAtLeast(0f) else 0f
        val expandHoriz = maxExpandHorizDp * safeThickness
        val expandVert = maxExpandVertDp * safeThickness

        val rawLeftDp = baseLeftDp - expandHoriz
        val rawTopDp = trackPaddingDp - expandVert
        val rawWidthDp = (slotWidthDp + expandHoriz * 2f).coerceAtLeast(1f)
        val rawHeightDp = (trackHeightDp + expandVert * 2f).coerceAtLeast(1f)

        val safeScaleX = if (jellyScaleX.isFinite() && jellyScaleX > 0.1f) jellyScaleX else 1f
        val safeScaleY = if (jellyScaleY.isFinite() && jellyScaleY > 0.1f) jellyScaleY else 1f
        val effScaleX = 1f + (safeScaleX - 1f) * safeThickness.coerceIn(0f, 1f)
        val effScaleY = 1f + (safeScaleY - 1f) * safeThickness.coerceIn(0f, 1f)

        val centerX = rawLeftDp + rawWidthDp * 0.5f
        val centerY = rawTopDp + rawHeightDp * 0.5f
        val finalWidthDp = (rawWidthDp * effScaleX).coerceAtLeast(1f)
        val finalHeightDp = (rawHeightDp * effScaleY).coerceAtLeast(1f)

        return ExpandedIndicatorBounds(
            leftDp = centerX - finalWidthDp * 0.5f,
            topDp = centerY - finalHeightDp * 0.5f,
            widthDp = finalWidthDp,
            heightDp = finalHeightDp
        )
    }
}

/**
 * Geometri kotak buble indikator tab relatif terhadap sudut kiri-atas kapsul navbar.
 * Nilai [topDp] negatif dan [bottomDp] `> capsuleHeightDp` menunjukkan buble menonjol
 * keluar dari area kapsul seperti lensa air `AnimatedGlassIndicator` di Flutter.
 */
data class ExpandedIndicatorBounds(
    val leftDp: Float,
    val topDp: Float,
    val widthDp: Float,
    val heightDp: Float
) {
    val rightDp: Float
        get() = leftDp + widthDp

    val bottomDp: Float
        get() = topDp + heightDp
}

/**
 * Target geometri untuk animasi morph kontinu antara kapsul Tab, kapsul Search,
 * tombol Dismiss (`×`), dan `MiniPlayerPill` (padanan `SearchablePillLayout` di
 * `lib/widgets/surfaces/shared/tab_bar_searchable_controller.dart` dan
 * `TabBarSearchableLayout` di `lib/src/widgets/surfaces/tab_bar_searchable_layout.dart`).
 */
data class SearchablePillMorphTargets(
    /** Lebar target kapsul tab di sisi kiri (dp). */
    val targetTabWidthDp: Float,
    /** Posisi `left` target dari kapsul pencarian (dp). */
    val targetSearchLeftDp: Float,
    /** Lebar target dari kapsul pencarian (dp). */
    val targetSearchWidthDp: Float,
    /** Tinggi target baris pil utama (`64.dp` saat normal, `50.dp` saat mini / search). */
    val targetBarHeightDp: Float,
    /** Apakah pil tombol batal (`×`) di sisi kanan sedang tampil. */
    val isDismissPillVisible: Boolean,
    /** Lebar yang dicadangkan untuk tombol batal (`50.dp + 8.dp` saat tampil). */
    val dismissReserveDp: Float,
    /** Target progres inline aksesori (`0f` = mengambang di atas, `1f` = menyatu sebaris di tengah). */
    val targetAccessoryInlineFraction: Float,
    /** Target progres ciut tinggi bar (`0f` = tinggi penuh `64.dp`, `1f` = tinggi ringkas `50.dp`). */
    val targetBarCollapseFraction: Float,
    /** Jarak kiri orb tab dari tepi bar (dp): menjorok ke dalam di baris mini iOS 27. */
    val targetTabLeftDp: Float = 0f,
    /** Seberapa tinggi baris orb diangkat dari dasar bar (dp), agar berpusat di garis tengah bar penuh. */
    val targetRowLiftDp: Float = 0f
)

/**
 * Frame geometri hasil interpolasi real-time untuk `MiniPlayerPill` (bottomAccessory)
 * agar meluncur mulus dari atas navbar (`EXPANDED`) ke sela antara orb chevron kiri
 * dan orb search kanan (`INLINE`), serta sebaliknya saat tombol panah diklik.
 */
data class AccessoryMorphFrame(
    val leftInsetDp: Float,
    val rightInsetDp: Float,
    val bottomOffsetDp: Float,
    val totalContainerHeightDp: Float,
    /** Tinggi mini player: penuh di atas bar, sedikit lebih pendek saat sebaris. */
    val heightDp: Float
)

/**
 * Kalkulator layout morph murni (bebas ketergantungan UI framework, dapat diuji unit 100%)
 * yang menyamakan perilaku morph `GlassTabBar.searchable` pada project Flutter MusikkU.
 */
object SearchableBarMorphCalculator {
    /** iOS 27 ~61pt (dulu 64.dp ala Kyant0). */
    const val DEFAULT_BAR_HEIGHT_DP: Float = 60f
    const val DEFAULT_SEARCH_BAR_HEIGHT_DP: Float = 50f
    const val DEFAULT_ACCESSORY_HEIGHT_DP: Float = 50f
    const val DEFAULT_SPACING_DP: Float = 8f
    /** Celah mini player ke bar: iOS 27 sangat rapat, ~5pt. */
    const val DEFAULT_ACCESSORY_SPACING_DP: Float = 5f
    const val SEARCH_EXPAND_CONTENT_THRESHOLD_DP: Float = 90f

    // Baris mini iOS 27 (navbar menciut saat scroll), diukur dari rekaman: orb ~45pt,
    // mini player ~48pt sedikit lebih tinggi dari orb, celah ~15pt, dan seluruh baris
    // menjorok ~33pt dari tepi layar (bar penuh ~25pt), berpusat di garis tengah bar penuh.
    const val MINI_ORB_DP: Float = 44f
    const val MINI_ACCESSORY_HEIGHT_DP: Float = 48f
    const val MINI_SPACING_DP: Float = 14f
    /** Tambahan di atas padding tepi navbar (20dp), jadi total ~30dp dari tepi layar. */
    const val MINI_EDGE_INSET_DP: Float = 10f

    /** Pengangkatan baris mini agar orb berpusat di garis tengah bar penuh. */
    fun miniRowLiftDp(barHeightDp: Float = DEFAULT_BAR_HEIGHT_DP): Float =
        ((barHeightDp - MINI_ORB_DP) / 2f).coerceAtLeast(0f)

    /**
     * Menghitung target pegas (`targetTabW`, `targetSearchLeft`, `targetSearchW`, `targetBarHeight`)
     * persis seperti `SearchableBottomBarController.computeLayout` di Flutter.
     */
    fun computeTargets(
        totalWidthDp: Float,
        uiState: NavbarUiState,
        barHeightDp: Float = DEFAULT_BAR_HEIGHT_DP,
        searchBarHeightDp: Float = DEFAULT_SEARCH_BAR_HEIGHT_DP,
        spacingDp: Float = DEFAULT_SPACING_DP,
        showsCancelButton: Boolean = true
    ): SearchablePillMorphTargets {
        val safeTotalW = totalWidthDp.coerceAtLeast(barHeightDp * 2f + spacingDp)
        // Di home_screen.dart: isSearchActive pada GlassTabBar.searchable = _miniMode || _searching
        val isBarCollapsed = uiState.isTabCapsuleCollapsed
        // Di home_screen.dart: expandWhenActive = !_miniMode || _searching (yaitu uiState.isSearchActive)
        val expandSearch = uiState.isSearchActive

        val targetH = if (isBarCollapsed) searchBarHeightDp else barHeightDp
        val dismissVisible = uiState.isSearchActive && uiState.isSearchFocused && showsCancelButton
        val dismissReserve = if (showsCancelButton) targetH + spacingDp else 0f

        // Saat normal: kapsul tab selebar bar, dengan Search sebagai slot terakhir di dalamnya
        // (iOS 27). Search Orb menunggu tersembunyi di ujung kanan kapsul dan baru lepas sebagai
        // orb sendiri saat kapsul menciut (Mini Mode atau Search).
        val targetTabW = if (!isBarCollapsed) safeTotalW else targetH

        // Posisi kiri Search Pill:
        // - Normal / Mini Mode: berada di ujung kanan (safeTotalW - targetH)
        // - Search Aktif (belum fokus keyboard): berada di kanan orb tab 50.dp (targetTabW + spacing)
        // - Search Fokus: meluncur ke tepi paling kiri (0.dp) sementara DismissPill (x) muncul di kanan
        val targetSearchLeft = when {
            !isBarCollapsed || !expandSearch -> (safeTotalW - targetH).coerceAtLeast(0f)
            uiState.isSearchFocused -> 0f
            else -> targetTabW + spacingDp
        }

        // Lebar Search Pill:
        // - Normal: 64.dp (lingkaran)
        // - Mini Mode: 50.dp (lingkaran)
        // - Search Aktif: membentang memenuhi sisa ruang (dikurangi ruang DismissPill saat fokus)
        val targetSearchW = when {
            !isBarCollapsed || !expandSearch -> targetH
            else -> (safeTotalW - targetSearchLeft - (if (dismissVisible) dismissReserve else 0f))
                .coerceAtLeast(targetH)
        }

        val accessoryInline = if (uiState.accessoryPlacement == AccessoryPlacement.INLINE) 1f else 0f
        val barCollapse = if (isBarCollapsed) 1f else 0f

        // Baris mini (scroll, bukan pencarian): orb lebih kecil, menjorok dari tepi, diangkat.
        if (uiState.isMiniMode && !uiState.isSearchActive) {
            return SearchablePillMorphTargets(
                targetTabWidthDp = MINI_ORB_DP,
                targetSearchLeftDp = (safeTotalW - MINI_EDGE_INSET_DP - MINI_ORB_DP).coerceAtLeast(0f),
                targetSearchWidthDp = MINI_ORB_DP,
                targetBarHeightDp = MINI_ORB_DP,
                isDismissPillVisible = false,
                dismissReserveDp = 0f,
                targetAccessoryInlineFraction = accessoryInline,
                targetBarCollapseFraction = barCollapse,
                targetTabLeftDp = MINI_EDGE_INSET_DP,
                targetRowLiftDp = miniRowLiftDp(barHeightDp)
            )
        }

        return SearchablePillMorphTargets(
            targetTabWidthDp = targetTabW,
            targetSearchLeftDp = targetSearchLeft,
            targetSearchWidthDp = targetSearchW,
            targetBarHeightDp = targetH,
            isDismissPillVisible = dismissVisible,
            dismissReserveDp = if (dismissVisible) dismissReserve else 0f,
            targetAccessoryInlineFraction = accessoryInline,
            targetBarCollapseFraction = barCollapse
        )
    }

    /**
     * Menghitung posisi animasi `MiniPlayerPill` berdasarkan progres `accessoryT` (`0f..1f`)
     * dan `barCollapseT` (`0f..1f`) persis seperti dual `TweenAnimationBuilder` pada
     * `TabBarSearchableLayout` di `tab_bar_searchable_layout.dart` (baris 1142-1220).
     */
    fun computeAccessoryMorphFrame(
        accessoryT: Float,
        barCollapseT: Float,
        barHeightDp: Float = DEFAULT_BAR_HEIGHT_DP,
        searchBarHeightDp: Float = DEFAULT_SEARCH_BAR_HEIGHT_DP,
        accessoryHeightDp: Float = DEFAULT_ACCESSORY_HEIGHT_DP,
        spacingDp: Float = DEFAULT_SPACING_DP,
        accessorySpacingDp: Float = DEFAULT_ACCESSORY_SPACING_DP
    ): AccessoryMorphFrame {
        val clampedAccessoryT = accessoryT.coerceIn(0f, 1f)
        val clampedSearchT = barCollapseT.coerceIn(0f, 1f)

        // Saat inline (Mini Mode), MiniPlayerPill berada tepat di antara orb tab di kiri dan
        // orb search di kanan pada baris mini iOS 27 (lihat MINI_*).
        val inlineSideInset = MINI_EDGE_INSET_DP + MINI_ORB_DP + MINI_SPACING_DP
        val leftInset = lerpFloat(0f, inlineSideInset, clampedAccessoryT)
        val rightInset = lerpFloat(0f, inlineSideInset, clampedAccessoryT)
        val height = lerpFloat(accessoryHeightDp, MINI_ACCESSORY_HEIGHT_DP, clampedAccessoryT)

        // Saat expanded (di atas bar), posisi bawah mengikuti tinggi bar saat ini + jarak celah.
        val currentBarH = lerpFloat(barHeightDp, searchBarHeightDp, clampedSearchT)
        val expandedBottom = currentBarH + accessorySpacingDp
        // Sebaris: berpusat di garis tengah yang sama dengan orb baris mini.
        val rowCenter = miniRowLiftDp(barHeightDp) + MINI_ORB_DP / 2f
        val inlineBottom = rowCenter - MINI_ACCESSORY_HEIGHT_DP / 2f

        // Turun belakangan, naik duluan: saat menciut kapsul tab menyempit dulu dan mini player
        // baru turun ke barisnya di 30% akhir, dan saat memekar mini player sudah naik sebelum
        // kapsul melebar di bawahnya. Dengan gerak linear keduanya bertumpuk di tengah transisi.
        val dropT = ((clampedAccessoryT - 0.7f) / 0.3f).coerceIn(0f, 1f)
        val verticalT = dropT * dropT * (3f - 2f * dropT)
        val bottomOffset = lerpFloat(expandedBottom, inlineBottom, verticalT)
        val expandedTotalH = expandedBottom + accessoryHeightDp
        val totalHeight = lerpFloat(expandedTotalH, inlineBottom + MINI_ACCESSORY_HEIGHT_DP, verticalT)

        return AccessoryMorphFrame(
            leftInsetDp = leftInset,
            rightInsetDp = rightInset,
            bottomOffsetDp = bottomOffset,
            totalContainerHeightDp = totalHeight,
            heightDp = height
        )
    }

    private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
        start + (stop - start) * fraction
}

