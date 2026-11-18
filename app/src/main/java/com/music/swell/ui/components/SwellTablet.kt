package com.music.swell.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.swell.R
import com.music.swell.ui.components.liquidglass.GlassEffectMode
import com.music.swell.ui.components.liquidglass.InlineGlassTab
import com.music.swell.ui.components.liquidglass.LiquidGlassBox
import com.music.swell.ui.components.liquidglass.LiquidGlassInlineTabBar
import com.music.swell.ui.components.liquidglass.LiquidGlassSettings
import com.music.swell.ui.components.liquidglass.LocalLiquidGlassBackdropState
import com.music.swell.ui.components.liquidglass.currentSwellGlass
import com.music.swell.ui.components.liquidglass.rememberLiquidGlassBackdropState
import kotlin.math.max
import kotlin.math.min

/**
 * Swell's tablet chrome, ported from the liquid_glass_navbar reference: a
 * glass capsule of tabs across the top instead of the phone's bottom bar, a
 * glass sidebar that slides in from the left and pushes the page over, and
 * the mini player alone at the foot.
 */
object SwellTabletMetrics {
    val TopNavigationHeight = 56.dp
    val TopNavigationTop = 8.dp
    val MiniPlayerMaxWidth = 680.dp
    const val SIDEBAR_ANIMATION_MS = 300

    /** Shortest side under 600dp is a phone; anything else gets the tablet chrome. */
    fun isTablet(width: Dp, height: Dp): Boolean = min(width.value, height.value) >= 600f

    fun sidebarWidth(windowWidth: Dp): Dp = if (windowWidth < 1100.dp) 224.dp else 248.dp

    /** How far the page moves right while the sidebar is open. */
    fun contentBleed(sidebarWidth: Dp, progress: Float): Dp =
        (sidebarWidth + 16.dp) * progress.coerceIn(0f, 1f)

    /** 350dp, or what is left beside the bar's neighbours, never under 220dp. */
    fun topNavigationWidth(available: Dp): Dp =
        min(350f, max(220f, available.value)).dp

    /** 680dp, opening out to the whole width as the sidebar comes in. */
    fun miniPlayerMaxWidth(available: Dp, progress: Float): Dp =
        MiniPlayerMaxWidth + (available - MiniPlayerMaxWidth).coerceAtLeast(0.dp) * progress.coerceIn(0f, 1f)
}

/** Slot indices of [SwellTabletTopBar]: sidebar toggle, the three root tabs, Search. */
const val TABLET_TOP_SIDEBAR = 0
const val TABLET_TOP_SEARCH = 4

/**
 * The tablet's top navigation: [LiquidGlassInlineTabBar] holding the sidebar
 * toggle, Home / Explore / Library and Search, drawn with the same lens as the
 * phone's navbar and the user's glass tuning.
 *
 * @param selectedTab the app's tab index (0..3), or null for none.
 */
@Composable
fun SwellTabletTopBar(
    tabs: List<BottomTab>,
    selectedTab: Int?,
    onToggleSidebar: () -> Unit,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentSwellGlass()
    val backdropState = LocalLiquidGlassBackdropState.current ?: rememberLiquidGlassBackdropState()
    val openSidebar = stringResource(R.string.open_sidebar)
    val slots = listOf(
        InlineGlassTab(icon = SidebarLeftIcon, contentDescription = openSidebar, selectable = false),
    ) + tabs.take(3).map { InlineGlassTab(label = it.label) } + InlineGlassTab(
        icon = tabs.getOrNull(3)?.icon,
        contentDescription = tabs.getOrNull(3)?.label,
    )
    LiquidGlassInlineTabBar(
        tabs = slots,
        selectedIndex = selectedTab?.let { it + 1 },
        onTabSelected = { index ->
            if (index == TABLET_TOP_SIDEBAR) onToggleSidebar() else onTabSelected(index - 1)
        },
        isDark = glass.isDark,
        backdropState = backdropState,
        glassSettings = glass.bar,
        glassMode = glass.mode,
        barHeight = SwellTabletMetrics.TopNavigationHeight,
        modifier = modifier,
    )
}

/** One row of [SwellSidebar]. */
@Immutable
data class SidebarEntry(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val activeIcon: ImageVector = icon,
)

/**
 * The tablet's glass sidebar: the Swell header with its close toggle, a search
 * button, [entries], then [footer] (Settings) under a divider.
 *
 * Pinned to the left edge with no rounded corners, so it is thin frost rather
 * than a lens; see [sidebarGlass].
 *
 * @param activeKey the [SidebarEntry.key] of the page on screen, if it has one.
 */
@Composable
fun SwellSidebar(
    width: Dp,
    entries: List<SidebarEntry>,
    footer: List<SidebarEntry>,
    activeKey: String?,
    searching: Boolean,
    onSelect: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = currentSwellGlass()
    val isDark = glass.isDark
    val backdropState = LocalLiquidGlassBackdropState.current ?: rememberLiquidGlassBackdropState()
    val accent = if (isDark) Color(0xFF0091FF) else Color(0xFF0088FF)
    val secondary = if (isDark) Color(0xFFE5E5EA) else Color(0xFF45454D)
    val fg = if (isDark) Color.White else Color(0xFF141416)

    LiquidGlassBox(
        backdropState = backdropState,
        settings = sidebarGlass(glass.bar, isDark, glass.mode),
        mode = glass.mode,
        isDark = isDark,
        cornerRadius = 0.dp,
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .drawWithContent {
                drawContent()
                val stroke = 0.7.dp.toPx()
                drawRect(
                    color = if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f),
                    topLeft = Offset(size.width - stroke, 0f),
                    size = Size(stroke, size.height),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = null,
                        tint = Color(0xFFE8163A),
                        modifier = Modifier.size(width = 34.dp, height = 22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        color = fg,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(SidebarLeftIcon, contentDescription = stringResource(R.string.close_sidebar), tint = fg)
                }
            }

            SidebarSearchButton(
                searching = searching,
                accent = accent,
                secondary = secondary,
                isDark = isDark,
                onClick = onSearch,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 12.dp),
            ) {
                entries.forEach { entry ->
                    SidebarTile(entry, selected = !searching && activeKey == entry.key, accent, isDark, onSelect)
                }
                if (footer.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 6.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
                        thickness = 0.7.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f),
                    )
                    footer.forEach { entry ->
                        SidebarTile(entry, selected = !searching && activeKey == entry.key, accent, isDark, onSelect)
                    }
                }
            }
        }
    }
}

/**
 * Thin (~35%) frost with a strong blur and no lens, so the page behind reads
 * faintly without edge distortion. The rim is swapped for a right-edge hairline:
 * against the screen edge it would look like a frame.
 */
private fun sidebarGlass(base: LiquidGlassSettings, isDark: Boolean, mode: GlassEffectMode): LiquidGlassSettings =
    if (mode == GlassEffectMode.SOLID) {
        base
    } else {
        base.copy(
            glassColorArgb = if (isDark) 0x591C1C1EL else 0x66F2F2F7L,
            blurRadiusDp = 24f,
            chromaticAberration = 0f,
            refractiveIndex = 1f,
            refractionHeight = 0f,
            refractionAmount = 0f,
            drawSpecularRim = false,
            highlightAlpha = 0f,
        )
    }

@Composable
private fun SidebarSearchButton(
    searching: Boolean,
    accent: Color,
    secondary: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fg = if (searching) accent else secondary
    val bg = when {
        searching -> accent.copy(alpha = 0.12f)
        isDark -> Color.White.copy(alpha = 0.08f)
        else -> Color.Black.copy(alpha = 0.05f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(bg)
            .semantics { selected = searching }
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(com.music.swell.ui.icons.BitChordIcons.Search, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = stringResource(R.string.search_music), color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SidebarTile(
    entry: SidebarEntry,
    selected: Boolean,
    accent: Color,
    isDark: Boolean,
    onSelect: (String) -> Unit,
) {
    val fg by animateColorAsState(
        targetValue = when {
            selected -> accent
            isDark -> Color(0xFFF5F5F7)
            else -> Color(0xFF202024)
        },
        animationSpec = tween(180),
        label = "sidebarTileFg",
    )
    val bg by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = if (isDark) 0.22f else 0.14f) else Color.Transparent,
        animationSpec = tween(180),
        label = "sidebarTileBg",
    )
    Row(
        modifier = Modifier
            .padding(bottom = 2.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab) { onSelect(entry.key) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) entry.activeIcon else entry.icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = entry.label,
            color = fg,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** `sidebar.left`: a rounded rectangle with a panel down its left side. */
val SidebarLeftIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SidebarLeft",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 4.5f)
            horizontalLineTo(18f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 21f, y1 = 7.5f)
            verticalLineTo(16.5f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 18f, y1 = 19.5f)
            horizontalLineTo(6f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 3f, y1 = 16.5f)
            verticalLineTo(7.5f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 6f, y1 = 4.5f)
            close()
            moveTo(9.5f, 4.5f)
            verticalLineTo(19.5f)
        }
    }.build()
}
