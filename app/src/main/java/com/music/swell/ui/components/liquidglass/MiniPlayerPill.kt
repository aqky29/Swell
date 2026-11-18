package com.music.swell.ui.components.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.music.swell.ui.components.playerDockArt
import com.music.swell.ui.components.playerDockPill
import com.music.swell.ui.player.PlayerDock
import com.music.swell.data.model.Song
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.music.swell.R
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Padanan `MiniPlayerPill` di `lib/widgets/mini_player_pill.dart` pada project Flutter MusikkU.
 *
 * Menggunakan `LiquidGlassBox` terpisah (`useOwnLayer: true`) di luar kapsul tab bar agar
 * mematuhi Golden Rule #2 (tidak menumpuk kaca refraktif di dalam kaca refraktif).
 */
@Composable
fun MiniPlayerPill(
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    isDark: Boolean,
    backdropState: LiquidGlassBackdropState,
    glassSettings: LiquidGlassSettings,
    glassMode: GlassEffectMode,
    onTogglePlay: () -> Unit,
    onNextTrack: () -> Unit,
    onTapPill: () -> Unit,
    modifier: Modifier = Modifier,
    /** Told where the pill is, for the player sheet to grow out of it. */
    playerDock: PlayerDock? = null,
    /** `null`: tingginya dari [modifier] dan boleh berubah tiap frame (navbar), tetap berbentuk kapsul. */
    barHeight: Dp? = 54.dp,
    sharedBackdrop: SharedGlassBackdrop? = null,
    shadowAlpha: () -> Float = { 1f },
    lensStrength: () -> Float = { 1f },
    groupSlot: LiquidGlassGroupSlot? = null
) {
    if (song == null) return
    // Dipasang sendiri tanpa navbar yang menyediakan backdrop bersama (tablet): pil ini membuat
    // satu untuk dirinya. Dibutuhkan tinggi tetap agar region-nya bisa dihitung dari tepi bawah.
    val density = LocalDensity.current
    val ownBackdrop = rememberSharedGlassBackdrop(
        backdropState = backdropState,
        blurRadius = glassSettings.blurRadiusDp.dp,
        enabled = sharedBackdrop == null && groupSlot == null && barHeight != null &&
            glassMode != GlassEffectMode.SOLID,
        saturation = glassSettings.backdropSaturation(glassMode)
    )
    val ownShared = ownBackdrop.takeIf { it.enabled }
    val shared = sharedBackdrop ?: ownShared
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    val textColor = if (isDark) Color.White else Color(0xFF141416)
    val subColor = if (isDark) Color.White.copy(alpha = 0.62f) else Color(0xFF686870)
    val iconColor = if (isDark) Color.White else Color(0xFF141416)

    val fourDpPx = with(LocalDensity.current) { 4.dp.toPx() }
    groupSlot.bindInnerTransform {
        pillPressTransform(size, fourDpPx, interactiveHighlight) { sx, sy, tx, ty ->
            scaleX = sx
            scaleY = sy
            translationX = tx
            translationY = ty
        }
    }

    // Interaksi `LiquidButton` Kyant0: membesar +4dp saat ditekan, ikut tertarik elastis
    // ke arah jari (tanh), dan kilau InteractiveHighlight mengikuti posisi sentuhan.
    LiquidGlassBox(
        backdropState = backdropState,
        settings = glassSettings,
        mode = glassMode,
        isDark = isDark,
        cornerRadius = barHeight?.let { it / 2 } ?: CapsuleCornerRadius,
        sharedBackdrop = shared,
        shadowAlpha = shadowAlpha,
        lensStrength = lensStrength,
        drawSurface = groupSlot == null,
        modifier = modifier
            .fillMaxWidth()
            .then(if (barHeight != null) Modifier.height(barHeight) else Modifier)
            .then(
                if (ownShared != null && barHeight != null) {
                    Modifier.sharedGlassBackdropSource(
                        shared = ownShared,
                        regionHeightPx = with(density) { barHeight.toPx() },
                        marginPx = with(density) { 24.dp.toPx() }
                    )
                } else {
                    Modifier
                }
            )
            .playerDockPill(playerDock)
            .graphicsLayer {
                pillPressTransform(size, fourDpPx, interactiveHighlight) { sx, sy, tx, ty ->
                    scaleX = sx
                    scaleY = sy
                    translationX = tx
                    translationY = ty
                }
            }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onTapPill
            )
            .then(interactiveHighlight.gestureModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(interactiveHighlight.modifier)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork persegi sudut membulat 8.dp (sama seperti MiniPlayerPill Flutter)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .playerDockArt(playerDock, 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222226)),
                contentAlignment = Alignment.Center
            ) {
                if (song.thumbnailUrl != null) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Judul & Artis
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = subColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tombol Play / Pause / Loading
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(36.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = iconColor
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Tombol Next Track
            IconButton(
                onClick = onNextTrack,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.player_next_song),
                    tint = iconColor.copy(alpha = if (isDark) 0.72f else 0.60f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Transformasi tekan `LiquidButton` Kyant0: membesar +4dp dan tertarik elastis (tanh) ke arah
 * jari. Dipakai `graphicsLayer` pil dan slot grup kaca agar bentuk kacanya ikut persis.
 */
private inline fun pillPressTransform(
    size: Size,
    fourDpPx: Float,
    highlight: InteractiveHighlight,
    apply: (scaleX: Float, scaleY: Float, translationX: Float, translationY: Float) -> Unit
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) {
        apply(1f, 1f, 0f, 0f)
        return
    }
    val scale = lerp(1f, 1f + fourDpPx / height, highlight.pressProgress)

    val maxOffset = size.minDimension
    val initialDerivative = 0.05f
    val offset = highlight.offset
    val maxDragScale = fourDpPx / height
    val offsetAngle = atan2(offset.y, offset.x)
    apply(
        scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
            (width / height).coerceAtMost(1f),
        scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
            (height / width).coerceAtMost(1f),
        maxOffset * tanh(initialDerivative * offset.x / maxOffset),
        maxOffset * tanh(initialDerivative * offset.y / maxOffset)
    )
}
