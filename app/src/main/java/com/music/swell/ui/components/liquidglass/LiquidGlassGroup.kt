package com.music.swell.ui.components.liquidglass

import android.graphics.RenderEffect as AndroidRenderEffect
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.music.swell.ui.components.liquidglass.morph.MorphGroupGlass
import com.music.swell.ui.components.liquidglass.morph.MorphGroupLook
import com.music.swell.ui.components.liquidglass.morph.MorphRenderers

/**
 * Grup kaca yang menyatu seperti tetesan air, padanan `GlassEffectContainer` di iOS 26.
 *
 * Semua bentuk dalam grup digambar oleh SATU shader sebagai gabungan SDF halus (smooth-min):
 * saat dua kapsul berjarak kurang dari ~setengah [GROUP_MERGE_DISTANCE], tepinya melebur
 * membentuk leher cairan, lalu memisah lagi saat menjauh. Materialnya sama dengan pill di pojok
 * kanan atas (lensa, wajah, tint, rim vertikal; lihat `MorphGroupShader`) dan dihitung dari
 * bentuk gabungan itu, jadi kilau ikut menyatu.
 *
 * Elemen anggota hanya menggambar kontennya (ikon, teks, kilau sentuh) dengan
 * `LiquidGlassBox(drawSurface = false)`; permukaannya digambar [LiquidGlassGroupSurface].
 */
@Stable
class LiquidGlassGroup internal constructor(
    private val slots: List<LiquidGlassGroupSlot>,
    val enabled: Boolean
) {
    /** Slot ke-[index], atau `null` bila grup nonaktif (elemen kembali menggambar kacanya sendiri). */
    fun slot(index: Int): LiquidGlassGroupSlot? = if (enabled) slots[index] else null

    internal val allSlots: List<LiquidGlassGroupSlot> get() = slots

    companion object {
        /** Jumlah bentuk yang didukung shader. */
        const val MAX_SHAPES = 4
    }
}

@Composable
fun rememberLiquidGlassGroup(slotCount: Int, enabled: Boolean): LiquidGlassGroup {
    require(slotCount in 1..LiquidGlassGroup.MAX_SHAPES)
    val supported = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val slots = remember(slotCount) { List(slotCount) { LiquidGlassGroupSlot() } }
    return remember(slots, supported) { LiquidGlassGroup(slots, supported) }
}

/**
 * Transformasi bentuk kaca, setara properti `graphicsLayer` elemen (pivot di tengah).
 * [alpha] < 1 menggerus bentuk ke dalam sehingga elemen yang memudar ikut mencair, bukan
 * sekadar transparan.
 */
class GlassShapeTransform internal constructor() {
    var size: Size = Size.Zero
        internal set
    var scaleX = 1f
    var scaleY = 1f
    var translationX = 0f
    var translationY = 0f
    var alpha = 1f

    internal fun reset(size: Size) {
        this.size = size
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
        alpha = 1f
    }
}

/**
 * Satu bentuk di dalam grup. Posisinya diambil dari [liquidGlassGroupSlot] pada pembungkus
 * yang tidak bertransformasi; transformasi luar (milik induk) dan dalam (milik elemen, mis. efek
 * tekan) didaftarkan lewat [bindOuterTransform] / [bindInnerTransform] dan dibaca ulang saat
 * permukaan grup digambar, sehingga tidak perlu menunggu recompose.
 */
@Stable
class LiquidGlassGroupSlot internal constructor() {
    internal var rect by mutableStateOf(Rect.Zero)
    internal var outer by mutableStateOf<(GlassShapeTransform.() -> Unit)?>(null)
    internal var inner by mutableStateOf<(GlassShapeTransform.() -> Unit)?>(null)
}

/** Catat posisi & ukuran slot. Pasang pada pembungkus tanpa `graphicsLayer`, anak langsung grup. */
fun Modifier.liquidGlassGroupSlot(slot: LiquidGlassGroupSlot?): Modifier =
    if (slot == null) {
        this
    } else {
        this.onPlaced { coords ->
            slot.rect = Rect(coords.positionInParent(), coords.size.toSize())
        }
    }

@Composable
fun LiquidGlassGroupSlot?.bindOuterTransform(block: GlassShapeTransform.() -> Unit) =
    bindTransform(outer = true, block = block)

@Composable
fun LiquidGlassGroupSlot?.bindInnerTransform(block: GlassShapeTransform.() -> Unit) =
    bindTransform(outer = false, block = block)

@Composable
private fun LiquidGlassGroupSlot?.bindTransform(outer: Boolean, block: GlassShapeTransform.() -> Unit) {
    val slot = this ?: return
    val current = rememberUpdatedState(block)
    DisposableEffect(slot, outer) {
        val transform: GlassShapeTransform.() -> Unit = { current.value(this) }
        if (outer) slot.outer = transform else slot.inner = transform
        onDispose {
            if (outer) slot.outer = null else slot.inner = null
        }
    }
}

/**
 * Jarak smooth-min. Kontur hanya berubah saat celah < nilai ini, dan baru tersambung saat celah
 * < setengahnya, jadi kapsul hanya melebur saat hampir bersentuhan. 12dp terlalu lebay: celah
 * 8dp saat diam ikut menggembung dan leher cairan terlihat sepanjang morph.
 */
private val GROUP_MERGE_DISTANCE = 4.dp

/** Ruang di luar grup untuk efek tekan yang membesarkan bentuk. */
private val GROUP_SURFACE_MARGIN = 28.dp

/**
 * Permukaan kaca seluruh grup. Pasang sebagai anak PERTAMA wadah grup dengan
 * `Modifier.matchParentSize()`, sehingga digambar di bawah konten setiap elemen.
 */
@Composable
fun LiquidGlassGroupSurface(
    group: LiquidGlassGroup,
    sharedBackdrop: SharedGlassBackdrop,
    backdropState: LiquidGlassBackdropState,
    settings: LiquidGlassSettings,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!group.enabled || !sharedBackdrop.enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val density = LocalDensity.current
    val marginPx = with(density) { GROUP_SURFACE_MARGIN.roundToPx() }
    val holder = remember { GlassGroupShaderHolder() }
    val outerScratch = remember { GlassShapeTransform() }
    val innerScratch = remember { GlassShapeTransform() }
    var originInRoot by remember { mutableStateOf(Offset.Zero) }

    val tint = glassSurfaceTint(settings, isDark)
    // The material is the resting pill's: lens, face, tint and rim come from the morph's recipe,
    // so the bar reads as the same glass as the pill and the menus that grow out of it.
    val look = remember(density, isDark, tint) {
        MorphGroupLook(
            density = density.density,
            dark = isDark,
            mergeDistance = with(density) { GROUP_MERGE_DISTANCE.toPx() },
            bodyArgb = tint.toArgb(),
        )
    }

    // Layer dikelola sendiri dan direkam ulang setiap kali permukaan digambar. Versi dengan
    // Modifier.graphicsLayer + penempatan negatif sempat tergambar dua kali (salinan basi di
    // posisi wadah pada frame awal), dan shader ini menggambar bentuk dari uniform sehingga
    // salinan itu terlihat sebagai kapsul hantu.
    val layer = rememberGraphicsLayer()
    Box(
        modifier = modifier
            .onGloballyPositioned { originInRoot = it.positionInRoot() }
            .drawBehind {
                val layerWidth = size.width.toInt() + marginPx * 2
                val layerHeight = size.height.toInt() + marginPx * 2
                if (layerWidth <= marginPx * 2 || layerHeight <= marginPx * 2) return@drawBehind

                val shapes = holder.shapes
                group.allSlots.forEachIndexed { i, slot ->
                    val r = slot.rect
                    if (r.width <= 0f || r.height <= 0f) {
                        holder.alphas[i] = 0f
                        return@forEachIndexed
                    }
                    outerScratch.reset(r.size)
                    slot.outer?.invoke(outerScratch)
                    innerScratch.reset(r.size)
                    slot.inner?.invoke(innerScratch)
                    // Dalam lalu luar, keduanya berpivot di tengah bentuk yang sama.
                    val sx = outerScratch.scaleX * innerScratch.scaleX
                    val sy = outerScratch.scaleY * innerScratch.scaleY
                    val tx = outerScratch.scaleX * innerScratch.translationX + outerScratch.translationX
                    val ty = outerScratch.scaleY * innerScratch.translationY + outerScratch.translationY
                    shapes[i * 4] = r.center.x + tx + marginPx
                    shapes[i * 4 + 1] = r.center.y + ty + marginPx
                    shapes[i * 4 + 2] = r.width / 2f * sx
                    shapes[i * 4 + 3] = r.height / 2f * sy
                    holder.alphas[i] = (outerScratch.alpha * innerScratch.alpha).coerceIn(0f, 1f)
                }
                for (i in group.allSlots.size until LiquidGlassGroup.MAX_SHAPES) holder.alphas[i] = 0f
                val effect = holder.buildRenderEffect(look, layerWidth.toFloat(), layerHeight.toFloat())
                    ?: return@drawBehind

                @Suppress("UNUSED_VARIABLE")
                val tick = backdropState.drawTick
                val region = sharedBackdrop.regionInRoot
                val layerLeft = originInRoot.x - marginPx
                val layerTop = originInRoot.y - marginPx
                layer.renderEffect = effect.asComposeRenderEffect()
                layer.topLeft = IntOffset(-marginPx, -marginPx)
                layer.record(IntSize(layerWidth, layerHeight)) {
                    if (region.width > 0f && region.height > 0f) {
                        translate(left = region.left - layerLeft, top = region.top - layerTop) {
                            with(sharedBackdrop) { drawBlurred() }
                        }
                    }
                }
                drawLayer(layer)
            }
    )
}

/**
 * Builds the bar's RenderEffect. A new effect is made only when a shape, the size or the look
 * changed: `createRuntimeShaderEffect` copies the uniforms when it is made, so an earlier effect
 * does not follow later changes, while the compiled shader behind it is shared and reused.
 */
private class GlassGroupShaderHolder {
    val shapes = FloatArray(LiquidGlassGroup.MAX_SHAPES * 4)
    val alphas = FloatArray(LiquidGlassGroup.MAX_SHAPES)

    private val glass: MorphGroupGlass? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) MorphRenderers.bar() else null
    private val lastShapes = FloatArray(shapes.size) { Float.NaN }
    private val lastAlphas = FloatArray(alphas.size) { Float.NaN }
    private var lastLook: MorphGroupLook? = null
    private var lastWidth = -1f
    private var lastHeight = -1f
    private var cachedEffect: AndroidRenderEffect? = null

    fun buildRenderEffect(look: MorphGroupLook, width: Float, height: Float): AndroidRenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val glass = glass ?: return null
        if (alphas.all { it <= 0.02f }) return null.also { cachedEffect = null; lastLook = null }
        val cached = cachedEffect
        if (cached != null && look === lastLook && width == lastWidth && height == lastHeight &&
            shapes.contentEquals(lastShapes) && alphas.contentEquals(lastAlphas)
        ) {
            return cached
        }
        shapes.copyInto(lastShapes)
        alphas.copyInto(lastAlphas)
        lastLook = look
        lastWidth = width
        lastHeight = height
        return glass.effect(shapes, alphas, width, height, look).also { cachedEffect = it }
    }
}
