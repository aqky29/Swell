package com.music.swell.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Roughly the corner of a phone's display, which the card has grown to by the
 * time it covers the screen — so the last frame before the clip comes off
 * changes nothing anyone can see.
 */
private val SCREEN_CORNER = 40.dp

/**
 * The player sheet growing out of the mini player, as iOS 27 opens it, rather
 * than sliding up over the bar.
 *
 * The sheet still slides — [PlayerSheetMotion] moves it, the finger pulls it,
 * the artwork flies on [PlayerDock] — and this only cuts what of it shows: a
 * card that starts as the mini player's surface, its width and its round ends,
 * and widens and drops its bottom edge to the screen's as the player opens.
 * Its top is the player's own top edge, rounded, so the two read as one card.
 *
 * Above that top edge only the flying artwork is drawn, while it is still down
 * in the bar; the card lets it through there rather than cutting it off.
 *
 * Read at draw only, off the sheet's offset: a frame of the sheet's travel
 * re-clips one layer and recomposes nothing. Off with the player fully open,
 * with no glass mini player on screen to grow from, or when the player keeps
 * its plain slide ([PlayerDock.attached] false).
 */
@Composable
fun Modifier.growFromMiniPlayer(dock: PlayerDock, enabled: Boolean): Modifier {
    if (!enabled) return this
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val path = remember { Path() }
    val card = remember { Path() }
    val above = remember { Path() }
    return this
        .onPlaced { placed[0] = it }
        .drawWithContent {
            val open = dock.openFraction()
            val coordinates = placed[0]?.takeIf { it.isAttached }
            val pill = if (open < 1f && dock.attached) dock.miniPillOnScreen() else null
            if (pill == null || coordinates == null || dock.sheetOffset == null) {
                drawContent()
                return@drawWithContent
            }
            // Wider sooner than it rises, as the card in the reference does:
            // it is most of the screen's width by the time it is halfway up.
            val grow = 1f - (1f - open) * (1f - open)
            val pillTopLeft = coordinates.screenToLocal(pill.topLeft)
            val pillBottomRight = coordinates.screenToLocal(pill.bottomRight)
            // The screen in this layer's terms: it is offset by the slide.
            val screenTop = coordinates.screenToLocal(Offset.Zero).y
            val left = lerp(pillTopLeft.x, 0f, grow)
            val right = lerp(pillBottomRight.x, size.width, grow)
            val top = lerp(pillTopLeft.y, screenTop, grow)
            val bottom = lerp(pillBottomRight.y, screenTop + size.height, grow)
            if (right <= left || bottom <= top) return@drawWithContent
            val radius = lerp(pill.height / 2f, SCREEN_CORNER.toPx(), grow)
                .coerceAtMost((right - left) / 2f)
            val corner = CornerRadius(radius)

            card.rewind()
            card.addRoundRect(RoundRect(left, 0f.coerceAtMost(bottom), right, bottom, corner))
            above.rewind()
            if (top < 0f) above.addRect(Rect(left, top, right, 0f.coerceAtMost(bottom)))
            path.rewind()
            path.op(card, above, PathOperation.Union)
            clipPath(path) { this@drawWithContent.drawContent() }
        }
}
