package com.music.swell.ui.components

import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.music.swell.R
import com.music.swell.auth.GoogleAccountSession
import com.music.swell.auth.YouTubeProfile
import com.music.swell.data.model.Account
import com.music.swell.ui.components.liquidglass.GlassEffectMode
import com.music.swell.ui.components.liquidglass.LocalLiquidGlassBackdropState
import com.music.swell.ui.components.liquidglass.currentSwellGlass
import com.music.swell.ui.components.liquidglass.morph.MorphFrame
import com.music.swell.ui.components.liquidglass.morph.MorphMotion
import com.music.swell.ui.components.liquidglass.morph.MorphRenderer
import com.music.swell.ui.components.liquidglass.morph.MorphSurfaceState
import com.music.swell.ui.components.liquidglass.morph.MorphPress
import com.music.swell.ui.components.liquidglass.morph.MorphRenderers
import com.music.swell.ui.components.liquidglass.morph.MorphTuning
import com.music.swell.ui.components.liquidglass.rememberLiquidGlassBackdropState
import com.music.swell.ui.components.liquidglass.glassSurfaceTint
import com.music.swell.ui.components.liquidglass.rememberReduceMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where the top bar's glass pill and its buttons sit, and how much of them the morphing menu
 * covers.
 *
 * The pill holds the bar's actions — the download indicator, search, sort, more, the account
 * photo. A menu opens the way the reference opens one from a button inside a pill: the whole
 * pill is the glass that folds away, and the button that was pressed is only where the
 * droplet comes out of.
 *
 * The bar writes [pillBounds] and [bounds] when it lays out, notes where the finger went down
 * with [notePress], and records what the pill shows into [contentLayer]; [PillGlassMenu] reads
 * them once, when a menu opens.
 * [coverage] goes the other way: the menu raises it as its glass takes over from the pill,
 * and the pill fades by the same amount, so the hand-over is one cross-fade rather than a pop.
 */
@Stable
class PillMenuAnchor internal constructor(
    /**
     * The pill's icons, photo and download ring as last drawn, kept live while the pill is
     * hidden so the lobe can show them — shrinking with it — instead of a copy that froze.
     */
    internal val contentLayer: GraphicsLayer,
) {
    /** The account button's rect in window coordinates; [Rect.Zero] while it is not on screen. */
    var bounds: Rect = Rect.Zero

    /** The whole glass pill's rect in window coordinates; [Rect.Zero] when there is none. */
    var pillBounds: Rect = Rect.Zero

    /** 0 = the pill shows by itself, 1 = the menu's glass fully stands in for it. */
    var coverage by mutableFloatStateOf(0f)

    /** The pill's jelly while it has one: the morph picks up the swell the tap left on it. */
    internal var press: MorphPress? = null

    private var pressWindow: Offset = Offset.Unspecified
    private var pressAt = 0L

    /** Where the finger last went down on the pill, in window coordinates. */
    internal fun notePress(window: Offset) {
        pressWindow = window
        pressAt = SystemClock.uptimeMillis()
    }

    /** The last press, if it was just now: the button a tap opened a menu from. */
    internal fun recentPress(): Offset? =
        pressWindow.takeIf { it.isSpecified && SystemClock.uptimeMillis() - pressAt < PRESS_MEMORY_MS }
}

private const val PRESS_MEMORY_MS = 1500L

@Composable
fun rememberPillMenuAnchor(): PillMenuAnchor {
    val layer = rememberGraphicsLayer()
    return remember(layer) { PillMenuAnchor(layer) }
}

/**
 * The account picker, as a [PillGlassMenu] that grows out of the account button.
 *
 * Needs API 33 (AGSL); below that the caller keeps using [AccountProfileSelector].
 */
@Composable
fun AccountGlassMenu(
    open: Boolean,
    anchor: PillMenuAnchor,
    account: Account?,
    accounts: List<GoogleAccountSession>,
    activeAccountId: String?,
    activeProfileId: String?,
    onSelect: (GoogleAccountSession, YouTubeProfile) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (GoogleAccountSession) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    var managing by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (open) managing = false }
    PillGlassMenu(
        open = open,
        anchor = anchor,
        title = stringResource(R.string.switch_account),
        onDismiss = onDismiss,
        // The droplet comes out of the account button itself.
        fromPress = false,
        // With the bar off screen, the menu grows out of the account photo.
        lobeFallback = { AccountAvatarFace(account = account, contentDescription = null) },
    ) {
        AccountMenuRows(
            accounts = accounts,
            activeAccountId = activeAccountId,
            activeProfileId = activeProfileId,
            managing = managing,
            onManagingChange = { managing = it },
            onSelect = onSelect,
            onAddAccount = onAddAccount,
            onRemoveAccount = onRemoveAccount,
            onOpenSettings = onOpenSettings,
            onDismiss = onDismiss,
        )
    }
}

/**
 * A liquid-glass menu that grows out of a button in the top bar's pill and folds back into it,
 * ported from the kotlin-liquid-glass menu.
 *
 * Two glass shapes are fused into one surface: the pill's *lobe*, which shrinks and drifts
 * toward the menu, and the menu itself, which swells out of the pressed button as a droplet and
 * settles into its rounded rectangle. Between them the glass stretches into a neck. The rows
 * are scaled with the glass — zoomed in and blurred at first, sharpening as it opens — rather
 * than uncovered by it. The motion is the reference's own 20-channel spring timeline.
 *
 * [content] is the rows. [fromPress] makes the droplet come out of where the finger last went
 * down on the pill ([PillMenuAnchor.notePress]); without it the account button's own rect is
 * used. [lobeFallback] is what the lobe shows when there is no pill on screen.
 *
 * Needs API 33 (AGSL). Nothing is composed while the menu is closed, and while it is open and
 * at rest nothing runs per frame. Reduce motion swaps the morph for one short cross-fade.
 */
@Composable
fun PillGlassMenu(
    open: Boolean,
    anchor: PillMenuAnchor,
    title: String,
    onDismiss: () -> Unit,
    fromPress: Boolean = true,
    lobeFallback: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val density = LocalDensity.current.density
    val reduceMotion by rememberUpdatedState(rememberReduceMotion())
    val session = remember(density) { AccountMenuSession(density) }
    // Stays true through the closing animation, so the menu can fold back before it leaves.
    var rendered by remember { mutableStateOf(false) }

    LaunchedEffect(open) {
        if (open) {
            session.reset(reduceMotion)
            rendered = true
            session.runOpen(anchor)
        } else if (rendered) {
            session.runClose(anchor)
            rendered = false
        }
    }

    if (rendered) {
        PillGlassMenuSurface(
            session = session,
            anchor = anchor,
            title = title,
            onDismiss = onDismiss,
            fromPress = fromPress,
            lobeFallback = lobeFallback,
            content = content,
        )
    }
}

@Composable
private fun PillGlassMenuSurface(
    session: AccountMenuSession,
    anchor: PillMenuAnchor,
    title: String,
    onDismiss: () -> Unit,
    fromPress: Boolean,
    lobeFallback: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = currentSwellGlass()
    val backdrop = LocalLiquidGlassBackdropState.current ?: rememberLiquidGlassBackdropState()
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    // Taken once per opening: the bar keeps reporting while the menu is up, and the
    // morph must not chase it.
    val buttonBounds = remember { anchor.bounds }
    val pillBounds = remember { anchor.pillBounds }
    // Where the finger went down on the pill, if this menu was opened by a tap on it.
    val pressPoint = remember { if (fromPress) anchor.recentPress() else null }
    // What the lobe is: the whole pill when there is one, else just the button.
    val lobeBounds = if (!pillBounds.isEmpty) pillBounds else buttonBounds
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var naturalHeight by remember { mutableIntStateOf(0) }
    val ink = glassIconColor(glass.isDark)
    SideEffect {
        session.dark = glass.isDark
        session.solid = glass.mode == GlassEffectMode.SOLID
        session.press = anchor.press
        session.controlBlur = glass.pill.blurRadiusDp
        session.controlTint = glassSurfaceTint(glass.pill, glass.isDark).toArgb()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                windowOrigin = it.positionInWindow()
                rootOrigin = it.positionInRoot()
            },
    ) {
        val windowWidth = constraints.maxWidth.toFloat()
        val windowHeight = constraints.maxHeight.toFloat()
        val gutter = with(density) { BAR_GUTTER.toPx() }
        val margin = with(density) { MENU_MARGIN.toPx() }
        val buttonSize = with(density) { BUTTON_SIZE.toPx() }
        val menuWidth = min(with(density) { MENU_WIDTH.toPx() }, windowWidth - 2f * margin)
            .coerceAtLeast(buttonSize)
        val maxMenuHeight = (
            windowHeight - statusBarTop - with(density) { (TopBarContentHeight + MENU_BOTTOM_MARGIN).toPx() }
            ).coerceAtLeast(with(density) { 200.dp.toPx() })

        // Once the rows have a height: where the menu goes, and the glass starts moving.
        LaunchedEffect(naturalHeight, windowWidth, windowHeight, windowOrigin, session.generation) {
            if (naturalHeight <= 0) return@LaunchedEffect
            fun inOverlay(rect: Rect) = RectF(
                rect.left - windowOrigin.x, rect.top - windowOrigin.y,
                rect.right - windowOrigin.x, rect.bottom - windowOrigin.y,
            )
            val source = if (lobeBounds.isEmpty) {
                // Opened with nothing of the bar on screen: grow from where the button usually is.
                val top = statusBarTop + (with(density) { TopBarContentHeight.toPx() } - buttonSize) / 2f
                RectF(windowWidth - gutter - buttonSize, top, windowWidth - gutter, top + buttonSize)
            } else {
                inOverlay(lobeBounds)
            }
            // The droplet comes out of the button that was pressed, not out of the middle of the
            // pill: the account button's own rect, or a button-sized square under the finger.
            val through = if (!fromPress) {
                if (buttonBounds.isEmpty) RectF(source) else inOverlay(buttonBounds)
            } else {
                val half = min(source.height() / 2f, with(density) { PRESS_RADIUS.toPx() })
                val x = (pressPoint?.x?.minus(windowOrigin.x) ?: source.centerX())
                    .coerceIn(source.left + half, max(source.right - half, source.left + half))
                val y = (pressPoint?.y?.minus(windowOrigin.y) ?: source.centerY())
                    .coerceIn(source.top + half, max(source.bottom - half, source.top + half))
                RectF(x - half, y - half, x + half, y + half)
            }
            val height = min(naturalHeight.toFloat(), maxMenuHeight)
            // Right edge on the bar's own edge, like the button; the menu opens left and down.
            val right = max(windowWidth - gutter, source.right)
            val left = (right - menuWidth).coerceIn(margin, max(windowWidth - menuWidth - margin, margin))
            val top = source.top.coerceIn(0f, max(windowHeight - height - margin, 0f))
            session.place(
                source = source,
                through = through,
                target = RectF(left, top, left + menuWidth, top + height),
                cornerRadius = with(density) { MENU_CORNER.toPx() },
                captureMargin = with(density) { CAPTURE_MARGIN.toPx() },
                window = RectF(0f, 0f, windowWidth, windowHeight),
            )
        }

        // Taps outside the menu close it. No dim: the reference's menu has none.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        )

        // The fused glass and its shadow, in a layer just big enough for both shapes.
        if (session.ready) {
            val renderer = session.renderer
            if (renderer != null) {
                val capture = session.capture
                val captureWidth = capture.width().roundToInt().coerceAtLeast(1)
                val captureHeight = capture.height().roundToInt().coerceAtLeast(1)
                val shadowBrush = remember(renderer) { ShaderBrush(renderer.shadowShader) }
                Box(
                    modifier = Modifier
                        .layout { measurable, _ ->
                            val placeable = measurable.measure(Constraints.fixed(captureWidth, captureHeight))
                            layout(captureWidth, captureHeight) {
                                placeable.place(capture.left.roundToInt(), capture.top.roundToInt())
                            }
                        }
                        .drawBehind {
                            session.tick
                            session.prepare(anchor.coverage)
                            val rect = renderer.shadowRect
                            // The shadow shader works in the overlay's coordinates.
                            translate(left = -capture.left, top = -capture.top) {
                                drawRect(
                                    brush = shadowBrush,
                                    topLeft = Offset(rect.left, rect.top),
                                    size = Size(rect.width(), rect.height()),
                                )
                            }
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                session.tick
                                session.prepare(anchor.coverage)
                                renderEffect = renderer.materialEffect(
                                    state = session.state,
                                    nodeLeft = capture.left,
                                    nodeTop = capture.top,
                                    nodeWidth = size.width,
                                    nodeHeight = size.height,
                                ).asComposeRenderEffect()
                            }
                            .drawBehind {
                                session.tick
                                @Suppress("UNUSED_VARIABLE")
                                val changed = backdrop.drawTick
                                if (session.solid) return@drawBehind
                                val bounds = backdrop.rootBounds
                                if (bounds.width <= 0f) return@drawBehind
                                translate(
                                    left = bounds.left - (rootOrigin.x + capture.left),
                                    top = bounds.top - (rootOrigin.y + capture.top),
                                ) {
                                    drawLayer(backdrop.contentLayer)
                                }
                            },
                    )
                }
            }
        }

        // The rows keep their natural size and are scaled with the glass, so what is inside
        // is the menu, shrunk, rather than a menu being uncovered.
        CompositionLocalProvider(LocalContentColor provides ink) {
          // While the glass moves, the rows and the lobe's content are drawn through its lens.
          PortalLayer(session = session, anchor = anchor) {
            Box(
                modifier = Modifier
                    .width(with(density) { menuWidth.toDp() })
                    .heightIn(max = with(density) { maxMenuHeight.toDp() })
                    .onSizeChanged { naturalHeight = it.height }
                    // The glass has no touch handling of its own; without this a tap on the
                    // padding would fall through and close the menu.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = title }
                    .graphicsLayer {
                        session.tick
                        if (!session.ready) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        val frame = session.frame()
                        val target = session.target
                        val shown = frame.bounds
                        val zoom = frame.contentZoom
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = shown.width() / target.width() * zoom
                        scaleY = shown.height() / target.height() * zoom
                        translationX = shown.left + shown.width() * .5f * (1f - zoom)
                        translationY = shown.top + shown.height() * .5f * (1f - zoom)
                        alpha = frame.rowsOpacity
                        renderEffect = blurOrNull(frame.contentBlur / max(min(scaleX, scaleY), MIN_ROWS_SCALE))
                    },
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), content = content)
            }

            // What the pill showed, carried by the lobe: it shrinks and zooms as the lobe does.
            // With no pill (the bar not on screen) that is just the account photo.
            val hasPill = !pillBounds.isEmpty
            Box(
                modifier = Modifier
                    .size(
                        with(density) { (if (lobeBounds.isEmpty) buttonSize else lobeBounds.width).toDp() },
                        with(density) { (if (lobeBounds.isEmpty) buttonSize else lobeBounds.height).toDp() },
                    )
                    .graphicsLayer {
                        session.tick
                        if (!session.ready) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        session.prepare(anchor.coverage)
                        val frame = session.frame()
                        // The prepared lobe, not the engine's: it carries the tap's swell.
                        val lobe = session.state.source
                        val width = size.width
                        val height = size.height
                        transformOrigin = TransformOrigin.Center
                        scaleX = lobe.width() / width * frame.sourceZoom
                        scaleY = lobe.height() / height * frame.sourceZoom
                        translationX = lobe.centerX() - width / 2f
                        translationY = lobe.centerY() - height / 2f
                        alpha = frame.sourceOpacity
                        renderEffect = blurOrNull(frame.sourceBlur)
                    }
                    // Inside the layer above, so the recorded pill is scaled, blurred and
                    // faded with it.
                    .then(
                        if (hasPill) {
                            Modifier.drawBehind { drawLayer(anchor.contentLayer) }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!hasPill) lobeFallback?.invoke()
            }
          }
        }
    }
}

private fun blurOrNull(radiusPx: Float): RenderEffect? =
    if (radiusPx > 0.01f) BlurEffect(radiusPx, radiusPx, TileMode.Decal) else null

@Composable
private fun AccountMenuRows(
    accounts: List<GoogleAccountSession>,
    activeAccountId: String?,
    activeProfileId: String?,
    managing: Boolean,
    onManagingChange: (Boolean) -> Unit,
    onSelect: (GoogleAccountSession, YouTubeProfile) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (GoogleAccountSession) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ink = LocalContentColor.current
    Text(
        text = stringResource(R.string.switch_account),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 6.dp),
    )
    accounts.forEach { account ->
        Text(
            text = account.email.ifBlank { account.name.ifBlank { stringResource(R.string.accounts) } },
            style = MaterialTheme.typography.labelMedium,
            color = ink.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(20.dp, 10.dp, 20.dp, 2.dp),
        )
        account.profiles.forEach { profile ->
            ProfileRow(
                profile = profile,
                selected = account.accountId == activeAccountId && profile.profileId == activeProfileId,
                managing = managing,
                onClick = { onSelect(account, profile); onDismiss() },
                onRemove = { onRemoveAccount(account) },
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        thickness = 0.5.dp,
        color = ink.copy(alpha = 0.14f),
    )
    SelectorAction(Icons.Rounded.Add, stringResource(R.string.add_account), onAddAccount)
    SelectorAction(Icons.Rounded.ManageAccounts, stringResource(R.string.manage_accounts)) {
        onManagingChange(!managing)
    }
    SelectorAction(Icons.Rounded.Settings, stringResource(R.string.settings), onOpenSettings)
    Spacer(Modifier.height(8.dp))
}

/**
 * Draws [content] through the glass's lens while the glass is moving, as the reference's
 * foreground does: a layer just big enough for the two shapes, with the portal shader applied
 * to everything in it. [content] keeps the overlay's coordinates: it is measured as if the
 * layer were the whole window and placed so its origin stays where the window's is.
 */
@Composable
private fun PortalLayer(
    session: AccountMenuSession,
    anchor: PillMenuAnchor,
    content: @Composable () -> Unit,
) {
    val renderer = session.renderer
    val capture = session.capture
    val ready = session.ready
    val left = if (ready) capture.left.roundToInt() else 0
    val top = if (ready) capture.top.roundToInt() else 0
    val width = if (ready) capture.width().roundToInt().coerceAtLeast(1) else 1
    val height = if (ready) capture.height().roundToInt().coerceAtLeast(1) else 1
    Layout(
        content = content,
        modifier = Modifier
            .offset { IntOffset(left, top) }
            .graphicsLayer {
                session.tick
                if (renderer == null || !session.ready || !session.warping) {
                    renderEffect = null
                    return@graphicsLayer
                }
                session.prepare(anchor.coverage)
                renderEffect = renderer.portalEffect(
                    state = session.state,
                    nodeLeft = left.toFloat(),
                    nodeTop = top.toFloat(),
                    blurRadius = session.frame().portalBlur,
                ).asComposeRenderEffect()
            },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(width, height) { placeables.forEach { it.place(-left, -top) } }
    }
}

/**
 * One opening-to-closing of the menu: the reference's motion engine, the rects it moves
 * between, and the loop that advances it one frame at a time.
 *
 * Everything the draw code reads each frame is a plain field behind [tick], which the loop
 * raises after every step, so a frame redraws layers and never recomposes.
 */
@Stable
private class AccountMenuSession(private val density: Float) {
    private var motion = MorphMotion()
    val state = MorphSurfaceState()
    val source = RectF()
    private val through = RectF()
    val target = RectF()
    val capture = RectF()
    // Made at the first opening, and shared by every menu: compiling the shaders is the one
    // slow part, and only one menu is up at a time.
    val renderer: MorphRenderer? by lazy(LazyThreadSafetyMode.NONE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) MorphRenderers.obtain(density) else null
    }

    var dark = true
    var solid = false

    /** The resting pill's frost (dp) and body, so the morph starts from what the pill showed. */
    var controlBlur = 1f
    var controlTint: Int? = null

    /** The pill's jelly, if it has one: the lobe carries its swell into the morph. */
    var press: MorphPress? = null

    /** The motion is still moving: what is drawn on the glass goes through its lens. */
    var animating = false
        private set
    val warping: Boolean get() = animating && !reduce && !solid

    /** The geometry is placed and the motion initialised: the layers may start drawing. */
    var ready by mutableStateOf(false)
        private set

    /** Raised after every step; the layers read it so they redraw. */
    var tick by mutableLongStateOf(0L)
        private set

    /** Bumped on every opening, so placement runs again for the same measured rows. */
    var generation by mutableIntStateOf(0)
        private set

    private var reduce = false
    private var reduceValue = 0f
    private val reduceFrame = MorphFrame()
    private var cornerRadius = 0f
    private var preparedTick = -1L
    private var preparedOpacity = Float.NaN
    private var fieldStep = 0f

    fun reset(reduceMotion: Boolean) {
        motion = MorphMotion()
        reduce = reduceMotion
        reduceValue = 0f
        ready = false
        animating = false
        preparedTick = -1L
        generation++
    }

    /** Fixes the rects. The first time, also starts the motion from the button. */
    fun place(
        source: RectF,
        through: RectF,
        target: RectF,
        cornerRadius: Float,
        captureMargin: Float,
        window: RectF,
    ) {
        this.source.set(source)
        this.through.set(through)
        this.target.set(target)
        this.cornerRadius = cornerRadius
        capture.set(source)
        capture.union(target)
        capture.inset(-captureMargin, -captureMargin)
        // The neck's grid keeps one cell size for the whole opening, so it cannot shimmer as the
        // shapes move; the reference derives it from the two shapes plus a margin, before the
        // window clips anything.
        val field = RectF(source).apply {
            union(target)
            inset(-FIELD_MARGIN_DP * density, -FIELD_MARGIN_DP * density)
        }
        fieldStep = max(
            NECK_SIGMA_DP * density / FIELD_CELLS_PER_SIGMA,
            max(field.width(), field.height()) / FIELD_CELLS,
        )
        capture.intersect(window)
        if (reduce) {
            ready = true
        } else if (!motion.initialized) {
            motion.initialize(
                source, target, cornerRadius, density, through, false,
                MorphTuning(MorphTuning.ToolbarAbsorbDuration),
            )
            motion.retarget(true)
            ready = true
        } else {
            motion.updateTarget(target, cornerRadius)
        }
    }

    /** The frame to draw: the engine's, or a plain cross-fade's under reduce motion. */
    fun frame(): MorphFrame {
        if (!reduce) return motion.frame
        val f = reduceFrame
        val p = reduceValue.coerceIn(0f, 1f)
        f.bounds.set(target)
        f.radiusX = min(cornerRadius, min(target.width(), target.height()) / 2f)
        f.radiusY = f.radiusX
        f.contentProgress = p
        f.rowsOpacity = p
        f.sourceLobe.set(source)
        f.sourceRadius = min(source.width(), source.height()) / 2f
        f.smoothing = 0f
        f.sourceOpacity = 1f - p
        f.sourceBlur = 0f
        f.sourceZoom = 1f
        f.contentBlur = 0f
        f.contentZoom = 1f
        f.portalBlur = 0f
        f.lensHeight = 0f
        f.lensAmount = -0.001f * density
        return f
    }

    /** Fills [state] for this frame and binds the shaders, once per frame however often asked. */
    fun prepare(opacity: Float) {
        if (preparedTick == tick && preparedOpacity == opacity) return
        preparedTick = tick
        preparedOpacity = opacity
        val renderer = renderer ?: return
        val f = frame()
        state.incoming.set(f.bounds)
        state.radiusX = f.radiusX
        state.radiusY = f.radiusY
        // Under reduce motion there is no lobe to fuse with.
        state.hasSource = !reduce
        state.source.set(f.sourceLobe)
        state.sourceRadius = min(f.sourceLobe.width(), f.sourceLobe.height()) / 2f
        // A tap leaves the pill swollen for a moment; the lobe it becomes starts just as big.
        press?.takeIf { !reduce }?.let { p ->
            val swell = p.jelly.swell(source.height(), density, 1f) - 1f
            if (swell > 0.0001f) {
                state.source.inset(-state.source.width() * swell / 2f, -state.source.height() * swell / 2f)
                state.sourceRadius = min(state.source.width(), state.source.height()) / 2f
            }
        }
        state.smoothing = f.smoothing
        state.lockedFieldStep = fieldStep
        state.lensHeight = f.lensHeight
        state.lensAmount = f.lensAmount
        state.materialProgress = f.contentProgress
        state.surfaceOpacity = opacity
        state.dark = dark
        state.solid = solid
        state.controlBlur = controlBlur
        state.hasControlTint = controlTint != null
        state.controlTint = controlTint ?: 0
        renderer.prepare(state)
    }

    suspend fun runOpen(anchor: PillMenuAnchor) {
        snapshotFlow { ready }.first { it }
        coroutineScope {
            launch { fadeCoverage(anchor, 1f, COVER_IN_MS) }
            if (reduce) animateReduce(1f) else runMotion()
        }
    }

    suspend fun runClose(anchor: PillMenuAnchor) {
        if (ready) {
            if (reduce) {
                animateReduce(0f)
            } else {
                // The lobe folds back to where the pill is, taking the menu with it.
                motion.updateSource(source, through)
                motion.retarget(false)
                runMotion()
            }
            // The glass now sits exactly on the button: hand the photo back to the bar.
            fadeCoverage(anchor, 0f, COVER_OUT_MS)
        }
        anchor.coverage = 0f
    }

    private suspend fun fadeCoverage(anchor: PillMenuAnchor, to: Float, millis: Int) {
        animate(anchor.coverage, to, animationSpec = tween(millis)) { value, _ ->
            anchor.coverage = value
            tick++
        }
    }

    private suspend fun animateReduce(to: Float) {
        animate(reduceValue, to, animationSpec = tween(REDUCED_MS)) { value, _ ->
            reduceValue = value
            tick++
        }
    }

    private suspend fun runMotion() {
        animating = true
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_STEP_SECONDS)
            last = now
            val settled = motion.advanceBy(seconds)
            if (settled) animating = false
            tick++
            if (settled) return
        }
    }
}

private val BUTTON_SIZE = 44.dp

/** How big a droplet comes out of a pressed button: the pill's half height, as the reference's. */
private val PRESS_RADIUS = 22.dp

/** The reference's minimum menu width: narrow enough for a menu, wide enough for a name. */
private val MENU_WIDTH = 250.dp
private val MENU_MARGIN = 16.dp

/** The reference's menu corner radius. */
private val MENU_CORNER = 34.dp
private val MENU_BOTTOM_MARGIN = 96.dp

/** Room around the two shapes for the frost's blur, the rim and the shadow to land in. */
private val CAPTURE_MARGIN = 72.dp
private const val MIN_ROWS_SCALE = .25f

/** The neck's grid: the reference's blur reach, cells per blur radius, and cells across. */
private const val NECK_SIGMA_DP = 20f
private const val FIELD_CELLS_PER_SIGMA = 4f
private const val FIELD_CELLS = 64f
private const val FIELD_MARGIN_DP = 48f

/** Longest step the loop takes, so a stalled frame cannot throw the springs. */
private const val MAX_STEP_SECONDS = .05f
private const val COVER_IN_MS = 60
private const val COVER_OUT_MS = 90

/** Reduce motion: one short ease, the same length as the navbar's. */
private const val REDUCED_MS = 140
