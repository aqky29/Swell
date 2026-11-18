package com.music.swell.ui.components.liquidglass

import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import android.graphics.RectF as AndroidRectF
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.music.swell.ui.components.liquidglass.morph.MorphPress
import com.music.swell.ui.components.liquidglass.morph.MorphRenderers
import com.music.swell.ui.components.liquidglass.morph.MorphSurfaceState
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pipeline Efek Liquid Glass mengacu pada arsitektur [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).
 *
 * 1. **Signed Distance Field (SDF) Rounded Rect**:
 *    `sdRoundedRect` & `gradSdRoundedRect` menghitung jarak bertanda dan gradien normal 2D analitik.
 * 2. **Refraksi Lensa Bola (`circleMap`)**:
 *    Fungsi pemetaan sferis analitik `circleMap(x) = 1.0 - sqrt(1.0 - x * x)` pada sabuk tepi
 *    (`refractionHeight` & `refractionAmount`), sementara interior kapsul tetap jernih.
 * 3. **Dispersi Prisma Multi-Spektral 7 Warna (Chromatic Aberration)**:
 *    Pengambilan sampel terbobot fraksional pada spektrum Red, Orange, Yellow, Green, Cyan, Blue,
 *    dan Purple dengan modulasi kuadratik pusat `(centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y)`.
 * 4. **Directional Specular Highlight AGSL (`DefaultHighlightShaderString`)**:
 *    Menghitung kilau sudut dan tepi kapsul secara dinamis melalui produk titik `dot(grad, normal)`
 *    dan eksponen `pow(abs(d), falloff)`.
 * 5. **Inner Shadow & Drop Shadow**:
 *    Menciptakan kedalaman ketebalan dinding kaca (*glass wall depth*) di sepanjang batas kontur.
 * 6. **Vibrancy**:
 *    Peningkatan saturasi optik 1.5x di dalam shader refraksi (`vibrant()`) agar rona latar
 *    belakang yang dibiaskan tetap cerah tanpa render pass color filter terpisah.
 */
object LiquidGlassAgsl {

    private const val KYANT_ROUNDED_RECT_SDF: String = """
        float radiusAt(float2 coord, float4 radii) {
            if (coord.x >= 0.0) {
                if (coord.y <= 0.0) return radii.y;
                else return radii.z;
            } else {
                if (coord.y <= 0.0) return radii.x;
                else return radii.w;
            }
        }

        float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
            float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
            float outside = length(max(cornerCoord, 0.0)) - radius;
            float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
            return outside + inside;
        }

        float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
            float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
            if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
                return sign(coord) * normalize(max(cornerCoord, 0.0));
            } else {
                float gradX = step(cornerCoord.y, cornerCoord.x);
                return sign(coord) * float2(gradX, 1.0 - gradX);
            }
        }
    """

    /**
     * Shader Refraksi Lensa Kaca Kyant0 dengan Dispersi Kromatik Multi-Spektral.
     * Padanan `RoundedRectRefractionWithDispersionShaderString` di Kyant0/AndroidLiquidGlass.
     */
    const val SHADER_SOURCE: String = """
        uniform shader content;

        uniform float2 size;
        uniform float2 offset;
        uniform float4 cornerRadii;
        uniform float refractionHeight;
        uniform float refractionAmount;
        uniform float depthEffect;
        uniform float chromaticAberration;
        uniform float saturation;

        $KYANT_ROUNDED_RECT_SDF

        half4 vibrant(half4 c) {
            if (saturation == 1.0 || c.a <= 0.0) return c;
            half3 rgb = c.rgb / c.a;
            half l = dot(rgb, half3(0.213, 0.715, 0.072));
            return half4(clamp(mix(half3(l), rgb, half(saturation)), 0.0, 1.0) * c.a, c.a);
        }

        float circleMap(float x) {
            return 1.0 - sqrt(1.0 - x * x);
        }

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = (coord + offset) - halfSize;
            float radius = radiusAt(coord, cornerRadii);

            float sd = sdRoundedRect(centeredCoord, halfSize, radius);
            if (-sd >= refractionHeight) {
                return vibrant(content.eval(coord));
            }
            sd = min(sd, 0.0);

            float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

            float2 refractedCoord = coord + d * grad;
            if (chromaticAberration <= 0.0001) {
                return vibrant(content.eval(refractedCoord));
            }

            float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
            float2 dispersedCoord = d * grad * dispersionIntensity;

            half4 color = half4(0.0);

            half4 red = vibrant(content.eval(refractedCoord + dispersedCoord));
            color.r += red.r / 3.5;
            color.a += red.a / 7.0;

            half4 orange = vibrant(content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0)));
            color.r += orange.r / 3.5;
            color.g += orange.g / 7.0;
            color.a += orange.a / 7.0;

            half4 yellow = vibrant(content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0)));
            color.r += yellow.r / 3.5;
            color.g += yellow.g / 3.5;
            color.a += yellow.a / 7.0;

            half4 green = vibrant(content.eval(refractedCoord));
            color.g += green.g / 3.5;
            color.a += green.a / 7.0;

            half4 cyan = vibrant(content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0)));
            color.g += cyan.g / 3.5;
            color.b += cyan.b / 3.0;
            color.a += cyan.a / 7.0;

            half4 blue = vibrant(content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0)));
            color.b += blue.b / 3.0;
            color.a += blue.a / 7.0;

            half4 purple = vibrant(content.eval(refractedCoord - dispersedCoord));
            color.r += purple.r / 7.0;
            color.b += purple.b / 3.0;
            color.a += purple.a / 7.0;

            return color;
        }
    """

    /**
     * Shader untuk Active Indicator Pill yang menggunakan formula refraksi lensa sferis Kyant0.
     */
    const val INDICATOR_DROPLET_SHADER_SOURCE: String = SHADER_SOURCE

    /**
     * Shader Directional Specular Highlight Kyant0 (`DefaultHighlightShaderString`).
     */
    const val HIGHLIGHT_SHADER_SOURCE: String = """
        uniform float2 size;
        uniform float4 cornerRadii;
        uniform float4 color;
        uniform float angle;
        uniform float falloff;

        $KYANT_ROUNDED_RECT_SDF

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = coord - halfSize;
            float radius = radiusAt(coord, cornerRadii);

            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
            float2 normal = float2(cos(angle), sin(angle));
            float d = dot(grad, normal);
            float intensity = pow(abs(d), falloff);
            return color * intensity;
        }
    """

    /**
     * Shader Ambient Highlight Kyant0 (`AmbientHighlightShaderString`).
     */
    const val AMBIENT_HIGHLIGHT_SHADER_SOURCE: String = """
        uniform float2 size;
        uniform float4 cornerRadii;
        uniform float angle;
        uniform float falloff;

        $KYANT_ROUNDED_RECT_SDF

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = coord - halfSize;
            float radius = radiusAt(coord, cornerRadii);

            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
            float2 normal = float2(cos(angle), sin(angle));
            float d = dot(grad, normal);
            float intensity = pow(abs(d), falloff);
            float t = step(0.0, d);
            return half4(t, t, t, 1.0) * intensity;
        }
    """
}

/**
 * State penampung layer feed latar belakang untuk sampling real-time.
 */
@Stable
class LiquidGlassBackdropState(
    val contentLayer: GraphicsLayer
) {
    var rootBounds by mutableStateOf(Rect.Zero)
    var drawTick by androidx.compose.runtime.mutableIntStateOf(0)
        internal set

    fun notifyContentChanged() {
        drawTick++
    }
}

@Composable
fun rememberLiquidGlassBackdropState(): LiquidGlassBackdropState {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer) { LiquidGlassBackdropState(graphicsLayer) }
}

/** Backdrop halaman yang disampel semua permukaan kaca; disediakan sekali di MainActivity. */
val LocalLiquidGlassBackdropState = androidx.compose.runtime.staticCompositionLocalOf<LiquidGlassBackdropState?> { null }

/**
 * Rekam konten feed ke dalam [backdropState] tanpa render pass ganda.
 * [backgroundColor] mengisi dasar rekaman (warna window tema) agar halaman yang
 * latarnya transparan tidak terekam bolong di bawah kaca.
 */
fun Modifier.liquidGlassBackdropSource(
    backdropState: LiquidGlassBackdropState,
    backgroundColor: Color = Color.Unspecified
): Modifier =
    this
        .onGloballyPositioned { coords ->
            backdropState.rootBounds = coords.boundsInRoot()
        }
        .drawWithContent {
            backdropState.contentLayer.record {
                if (backgroundColor != Color.Unspecified) {
                    drawRect(backgroundColor)
                }
                this@drawWithContent.drawContent()
            }
            drawLayer(backdropState.contentLayer)
        }

/**
 * Backdrop ter-blur yang dipakai bersama oleh sekelompok elemen kaca yang berdekatan
 * (kapsul tab, search pill, mini player).
 *
 * Tanpa ini, setiap elemen mem-blur ulang latar di bawahnya sendiri setiap kali ukurannya
 * berubah, sehingga saat navbar morph GPU menjalankan rantai vibrancy + blur untuk 4–5 elemen
 * per frame. Di sini region di bawah seluruh grup direkam sekali ke [layer] (dengan efek
 * blur + vibrancy). Layer itu hanya direkam ulang bila region berpindah, dan HWUI hanya
 * mem-blur ulang bila konten di bawahnya berubah (misalnya saat scroll). Elemen cukup
 * menggambar potongan layer ini lalu menerapkan shader refraksinya.
 *
 * Seluruhnya berjalan di [SHARED_RESOLUTION_SCALE] resolusi: konten diputar ulang dan di-blur
 * pada seperempat jumlah piksel, lalu [drawBlurred] meregangkannya kembali. Blur sudah membuang
 * detail halus, jadi hasilnya sama, tetapi RenderThread per frame scroll jauh lebih ringan.
 * Layer biasa dengan efek di-composite pada resolusi perangkat saat digambar membesar, maka
 * [upscaled] (tekstur offscreen setengah ukuran) membungkusnya agar blur benar-benar terjadi
 * di resolusi rendah.
 */
@Stable
class SharedGlassBackdrop internal constructor(
    internal val layer: GraphicsLayer,
    private val upscaled: GraphicsLayer,
    internal val backdropState: LiquidGlassBackdropState,
    val blurRadiusPx: Float,
    val enabled: Boolean,
    val saturation: Float = VIBRANCY_SATURATION
) {
    /** Region yang direkam ke [layer], dalam koordinat root. */
    internal var regionInRoot by mutableStateOf(Rect.Zero)

    private var recordedRegion = Rect.Zero
    private var recordedSource = Rect.Zero

    init {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Vibrancy lewat colorFilter layer (satu blit dengan hasil blur), bukan efek rantai
            // terpisah yang menambah satu render pass offscreen penuh.
            val radius = blurRadiusPx * SHARED_RESOLUTION_SCALE
            layer.colorFilter = createVibrancyColorFilter(saturation)
            layer.renderEffect = if (radius > 0.5f) {
                AndroidRenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            } else {
                null
            }
        }
    }

    /** `true` bila elemen dengan blur [blurRadiusPx] boleh memakai backdrop bersama ini. */
    fun matches(blurRadiusPx: Float): Boolean = enabled && this.blurRadiusPx == blurRadiusPx

    internal fun recordIfNeeded(scope: DrawScope) {
        val region = regionInRoot
        val source = backdropState.rootBounds
        if (region.width <= 0f || region.height <= 0f || source.width <= 0f) return
        if (region == recordedRegion && source == recordedSource) return
        with(scope) {
            val size = IntSize(
                ceil(region.width * SHARED_RESOLUTION_SCALE).toInt(),
                ceil(region.height * SHARED_RESOLUTION_SCALE).toInt()
            )
            layer.record(size) {
                scale(SHARED_RESOLUTION_SCALE, pivot = Offset.Zero) {
                    translate(left = source.left - region.left, top = source.top - region.top) {
                        drawLayer(backdropState.contentLayer)
                    }
                }
            }
            upscaled.record(size) { drawLayer(layer) }
        }
        recordedRegion = region
        recordedSource = source
    }

    /** Gambar region ter-blur pada ukuran aslinya, berpangkal di sudut kiri atas region. */
    internal fun DrawScope.drawBlurred() {
        scale(1f / SHARED_RESOLUTION_SCALE, pivot = Offset.Zero) { drawLayer(upscaled) }
    }
}

/** Skala resolusi blur bersama; lihat [SharedGlassBackdrop]. */
private const val SHARED_RESOLUTION_SCALE = 0.5f

/**
 * Whether surfaces are drawn with the morph's material, the resting pill's glass, rather than
 * Kyant's lens. It needs AGSL, and Solid mode is a flat fill that never uses a lens.
 */
fun morphGlassAvailable(mode: GlassEffectMode): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mode == GlassEffectMode.PREMIUM

/** The saturation a shared backdrop applies: none where the material saturates by itself. */
fun LiquidGlassSettings.backdropSaturation(mode: GlassEffectMode): Float =
    if (morphGlassAvailable(mode)) 1f else vibrancy

@Composable
fun rememberSharedGlassBackdrop(
    backdropState: LiquidGlassBackdropState,
    blurRadius: Dp,
    enabled: Boolean,
    saturation: Float = VIBRANCY_SATURATION
): SharedGlassBackdrop {
    val layer = rememberGraphicsLayer()
    val upscaled = rememberGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
    val blurRadiusPx = with(LocalDensity.current) { blurRadius.toPx() }
    val supported = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return remember(layer, upscaled, backdropState, blurRadiusPx, supported, saturation) {
        SharedGlassBackdrop(layer, upscaled, backdropState, blurRadiusPx, supported, saturation)
    }
}

/**
 * Pasang pada kontainer grup kaca. Region backdrop bersama = lebar kontainer, setinggi
 * [regionHeightPx] dihitung dari tepi bawahnya, ditambah [marginPx] di setiap sisi (ruang untuk
 * efek tekan yang membesarkan elemen). Tepi bawah dipakai sebagai jangkar karena tinggi
 * kontainer navbar ikut beranimasi sementara tepi bawahnya tetap.
 */
fun Modifier.sharedGlassBackdropSource(
    shared: SharedGlassBackdrop,
    regionHeightPx: Float,
    marginPx: Float
): Modifier =
    if (!shared.enabled) {
        this
    } else {
        this
            .onGloballyPositioned { coords ->
                val b = coords.boundsInRoot()
                val source = shared.backdropState.rootBounds
                val region = Rect(
                    left = b.left - marginPx,
                    top = b.bottom - regionHeightPx - marginPx,
                    right = b.right + marginPx,
                    bottom = b.bottom + marginPx
                )
                shared.regionInRoot = if (source.width > 0f) region.intersect(source) else region
            }
            .drawWithContent {
                shared.recordIfNeeded(this)
                drawContent()
            }
    }

/**
 * Lingkup batas kapsul induk untuk overlay indikator yang menonjol keluar.
 */
@Stable
class LiquidGlassCapsuleScope(
    val boxBoundsInRoot: Rect,
    val boxSize: Size
)

/**
 * Filter warna Vibrancy Kyant0: meningkatkan saturasi 1.5x untuk menjaga kejernihan rona kaca.
 */
fun createVibrancyColorFilter(saturation: Float = 1.5f): ColorFilter {
    val invSat = 1f - saturation
    val r = 0.213f * invSat
    val g = 0.715f * invSat
    val b = 0.072f * invSat

    val c = 1.0f
    val t = (0.5f - c * 0.5f) * 255f
    val s = saturation

    val cr = c * r
    val cg = c * g
    val cb = c * b
    val cs = c * s

    val colorMatrix = ColorMatrix(
        floatArrayOf(
            cr + cs, cg, cb, 0f, t,
            cr, cg + cs, cb, 0f, t,
            cr, cg, cb + cs, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )
    )
    return ColorFilter.colorMatrix(colorMatrix)
}

/**
 * Modifier untuk menggambar Directional Specular Highlight Kyant0 pada tepi rounded rect.
 */
fun Modifier.drawKyantHighlight(
    cornerRadiusPx: Float,
    isDark: Boolean,
    enabled: Boolean = true,
    highlightAlpha: Float = 0.70f,
    angleDeg: Float = 135f,
    falloff: Float = 1.0f
): Modifier = this.drawWithCache {
    if (!enabled || highlightAlpha <= 0.01f || size.width <= 1f || size.height <= 1f) {
        return@drawWithCache onDrawWithContent { drawContent() }
    }

    val widthPx = size.width
    val heightPx = size.height
    val r = min(cornerRadiusPx, min(widthPx, heightPx) * 0.5f)
    val strokeWidthPx = 1.0.dp.toPx()

    val highlightColor = if (isDark) {
        Color.White.copy(alpha = highlightAlpha)
    } else {
        Color.White.copy(alpha = highlightAlpha.coerceAtMost(0.95f))
    }

    val outlinePaint = Paint().apply {
        style = PaintingStyle.Stroke
        this.strokeWidth = strokeWidthPx
        this.color = highlightColor
        this.blendMode = BlendMode.Plus
    }

    val outline = Outline.Rounded(
        RoundRect(
            left = strokeWidthPx * 0.5f,
            top = strokeWidthPx * 0.5f,
            right = widthPx - strokeWidthPx * 0.5f,
            bottom = heightPx - strokeWidthPx * 0.5f,
            cornerRadius = CornerRadius(r, r)
        )
    )

    onDrawWithContent {
        drawContent()
        HighlightShaders.apply(outlinePaint, widthPx, heightPx, r, highlightColor, angleDeg, falloff)
        drawContext.canvas.drawOutline(outline, outlinePaint)
    }
}

/**
 * Modifier untuk menggambar Inner Shadow halus di sepanjang dinding dalam kapsul (efek tebal kaca Kyant0).
 */
fun Modifier.drawKyantInnerShadow(
    cornerRadiusPx: Float,
    isDark: Boolean,
    enabled: Boolean = true,
    radiusDp: Float = 8f,
    alpha: Float = 0.35f
): Modifier = this.drawWithCache {
    if (!enabled || alpha <= 0.01f || radiusDp <= 0.1f || size.width <= 1f || size.height <= 1f) {
        return@drawWithCache onDrawWithContent { drawContent() }
    }

    val widthPx = size.width
    val heightPx = size.height
    val r = min(cornerRadiusPx, min(widthPx, heightPx) * 0.5f)
    val blurPx = radiusDp.dp.toPx()

    val shadowColor = if (isDark) {
        Color.Black.copy(alpha = alpha.coerceIn(0f, 1f))
    } else {
        Color(0xFF333333).copy(alpha = (alpha * 0.7f).coerceIn(0f, 1f))
    }

    val strokePaint = Paint().apply {
        style = PaintingStyle.Stroke
        strokeWidth = blurPx
        color = shadowColor
        if (blurPx > 0.5f) {
            asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
    }

    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = size.toRect(),
                cornerRadius = CornerRadius(r, r)
            )
        )
    }

    onDrawWithContent {
        drawContent()
        drawContext.canvas.save()
        drawContext.canvas.clipPath(path)
        drawContext.canvas.drawPath(path, strokePaint)
        drawContext.canvas.restore()
    }
}

/**
 * Sudut kapsul penuh. [LiquidGlassBox] memangkas sudutnya ke setengah sisi terpendek pada frame
 * itu, jadi kapsul yang tingginya beranimasi tidak perlu recompose hanya untuk sudut baru.
 */
val CapsuleCornerRadius: Dp = 1000.dp

/**
 * Komponen utama permukaan kaca cair berbasis pipeline Kyant0/AndroidLiquidGlass.
 */
@Composable
fun LiquidGlassBox(
    backdropState: LiquidGlassBackdropState,
    settings: LiquidGlassSettings,
    mode: GlassEffectMode,
    isDark: Boolean,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    touchOffset: Offset = Offset.Unspecified,
    recordedCapsuleLayer: GraphicsLayer? = null,
    refractionProgress: (() -> Float)? = null,
    sharedBackdrop: SharedGlassBackdrop? = null,
    shadowAlpha: () -> Float = { 1f },
    lensStrength: () -> Float = { 1f },
    // false: permukaan (latar, tint, rim, bayangan) digambar LiquidGlassGroupSurface;
    // elemen ini hanya menggambar konten & kilau sentuh di atasnya.
    drawSurface: Boolean = true,
    unclippedOverlay: (@Composable BoxScope.(LiquidGlassCapsuleScope) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val blurRadiusPx = with(density) { settings.blurRadiusDp.dp.toPx() }
    val refractionHeightPx = with(density) { settings.refractionHeight.dp.toPx() }
    val refractionAmountPx = with(density) { settings.refractionAmount.dp.toPx() }

    var boxBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var boxSize by remember { mutableStateOf(Size.Zero) }

    val shape = if (cornerRadius >= CapsuleCornerRadius) {
        RoundedCornerShape(percent = 50)
    } else {
        RoundedCornerShape(cornerRadius)
    }

    // The morph's material draws the surface wherever it can; Kyant's lens is the fallback.
    val useMorph = morphGlassAvailable(mode)
    val morphRenderer = remember(density.density, useMorph) {
        if (useMorph && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            MorphRenderers.resting(density.density)
        } else {
            null
        }
    }
    val morphState = remember { MorphSurfaceState() }
    val kyantShaderHolder = remember(useMorph) {
        if (!useMorph && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            KyantRefractionShaderHolder()
        } else {
            null
        }
    }

    val isAgslActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        mode == GlassEffectMode.PREMIUM &&
        kyantShaderHolder != null

    // Latar sudah di-blur + vibrancy oleh grup; elemen ini cukup menerapkan refraksi.
    val shared = sharedBackdrop?.takeIf { mode != GlassEffectMode.SOLID && it.matches(blurRadiusPx) }

    val containerColor = glassSurfaceTint(settings, isDark)

    // Kilau sentuh gaya iOS: cahaya memancar dari titik jari selama ditekan, lalu meredup
    // di posisi terakhir setelah dilepas.
    val touchGlow = animateFloatAsState(
        targetValue = if (touchOffset.isSpecified && mode != GlassEffectMode.SOLID) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 300f),
        label = "touchGlow"
    )
    val glowCenter = remember { GlowCenter() }
    if (touchOffset.isSpecified) glowCenter.offset = touchOffset

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                boxBoundsInRoot = coords.boundsInRoot()
                boxSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
            }
    ) {
        // 1. Permukaan Kaca Kapsul
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (drawSurface && !useMorph && settings.dropShadow && mode != GlassEffectMode.SOLID) {
                        Modifier.kyantShadow(alpha = shadowAlpha, cornerRadiusPx = cornerRadiusPx)
                    } else {
                        Modifier
                    }
                )
                .clip(shape)
                .drawKyantInnerShadow(
                    cornerRadiusPx = cornerRadiusPx,
                    isDark = isDark,
                    enabled = drawSurface && !useMorph && mode != GlassEffectMode.SOLID,
                    radiusDp = settings.innerShadowRadiusDp,
                    alpha = settings.innerShadowAlpha
                )
                .then(
                    if (settings.useKyantHighlight) {
                        // Highlight.Default Kyant0: 0.5dp, putih 50%, 45°, BlendMode.Plus
                        val highlightOn = drawSurface && !useMorph && settings.drawSpecularRim && mode != GlassEffectMode.SOLID
                        Modifier.kyantHighlight(
                            alpha = { if (highlightOn) settings.highlightAlpha else 0f },
                            angleDeg = settings.highlightAngleDeg,
                            falloff = settings.highlightFalloff,
                            cornerRadiusPx = cornerRadiusPx
                        )
                    } else {
                        Modifier.drawKyantHighlight(
                            cornerRadiusPx = cornerRadiusPx,
                            isDark = isDark,
                            enabled = drawSurface && !useMorph && settings.drawSpecularRim && mode != GlassEffectMode.SOLID,
                            highlightAlpha = settings.highlightAlpha,
                            angleDeg = settings.highlightAngleDeg,
                            falloff = settings.highlightFalloff
                        )
                    }
                )
        ) {
            // Lapisan Latar Kaca (Backdrop Refraction + Blur + Vibrancy)
            if (drawSurface) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (recordedCapsuleLayer != null) {
                                Modifier.drawWithContent {
                                    recordedCapsuleLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawLayer(recordedCapsuleLayer)
                                }
                            } else {
                                Modifier
                            }
                        )
                        .graphicsLayer {
                            // Pakai ukuran layer frame ini, bukan `boxSize` dari onGloballyPositioned
                            // yang baru terisi setelah layout (tertinggal satu frame saat morph).
                            val width = size.width
                            val height = size.height
                            if (mode == GlassEffectMode.SOLID || width <= 1f || height <= 1f) {
                                renderEffect = null
                                return@graphicsLayer
                            }

                            val lensScale = lensStrength().coerceIn(0f, 1f) *
                                (refractionProgress?.invoke()?.coerceIn(0f, 1f) ?: 1f)
                            if (useMorph && morphRenderer != null) {
                                // The resting pill's glass: lens, face, body and a vertical rim, from one
                                // shader. A backdrop the group already blurred is not blurred twice.
                                val state = morphState
                                state.incoming.set(0f, 0f, width, height)
                                state.source.set(state.incoming)
                                state.hasSource = false
                                val radius = min(cornerRadiusPx, min(width, height) / 2f)
                                state.radiusX = radius
                                state.radiusY = radius
                                state.sourceRadius = radius
                                state.smoothing = 0f
                                state.lockedFieldStep = 0f
                                state.lensHeight = 0f
                                state.lensAmount = 0f
                                state.materialProgress = 0f
                                state.surfaceOpacity = 1f
                                state.dark = isDark
                                state.solid = false
                                state.controlBlur = if (shared != null) 0f else settings.blurRadiusDp
                                state.hasControlTint = true
                                state.controlTint = containerColor.toArgb()
                                state.lensScale = lensScale
                                state.rimEnabled = settings.drawSpecularRim
                                state.pressX = 0f
                                state.pressY = 0f
                                state.pressRadius = 1f
                                state.poolGain = 0f
                                state.pressLift = 0f
                                state.rimLift = 0f
                                morphRenderer.prepareResting(state)
                                renderEffect = morphRenderer
                                    .materialEffect(state, 0f, 0f, width, height)
                                    .asComposeRenderEffect()
                            } else if (shared != null && (!isAgslActive || lensScale <= 0.001f)) {
                                // Latar sudah ter-blur dan lensa diam (refraksi 0): shader hanya akan
                                // menyalin piksel, jadi lewati satu render pass offscreen.
                                renderEffect = null
                            } else if (isAgslActive) {
                                renderEffect = kyantShaderHolder!!.buildRenderEffect(
                                    width = width,
                                    height = height,
                                    cornerRadiusPx = min(cornerRadiusPx, min(width, height) / 2f),
                                    refractionHeightPx = refractionHeightPx * lensScale,
                                    refractionAmountPx = refractionAmountPx * lensScale,
                                    depthEffect = settings.depthEffect,
                                    chromaticAberration = settings.chromaticAberration,
                                    blurRadiusPx = if (shared != null) 0f else blurRadiusPx,
                                    saturation = if (shared == null) settings.vibrancy else 1f
                                )?.asComposeRenderEffect()
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadiusPx > 0.5f) {
                                renderEffect = AndroidRenderEffect.createBlurEffect(
                                    blurRadiusPx,
                                    blurRadiusPx,
                                    Shader.TileMode.CLAMP
                                ).asComposeRenderEffect()
                            } else {
                                renderEffect = null
                            }
                        }
                        .drawWithCache {
                            onDrawBehind {
                                @Suppress("UNUSED_VARIABLE")
                                val tick = backdropState.drawTick

                                if (shared != null) {
                                    val region = shared.regionInRoot
                                    if (region.width > 0f && boxBoundsInRoot.width > 0f) {
                                        translate(
                                            left = region.left - boxBoundsInRoot.left,
                                            top = region.top - boxBoundsInRoot.top
                                        ) {
                                            with(shared) { drawBlurred() }
                                        }
                                    }
                                } else if (mode != GlassEffectMode.SOLID &&
                                    boxBoundsInRoot.width > 0f &&
                                    backdropState.rootBounds.width > 0f
                                ) {
                                    val dx = backdropState.rootBounds.left - boxBoundsInRoot.left
                                    val dy = backdropState.rootBounds.top - boxBoundsInRoot.top
                                    translate(left = dx, top = dy) {
                                        drawLayer(backdropState.contentLayer)
                                    }
                                }

                                // Surface Tint Kyant0; the morph's material lays the body itself.
                                if (!useMorph) {
                                    val fill = if (mode == GlassEffectMode.SOLID) {
                                        if (isDark) Color(ChromeGlass.SOLID_DARK_COLOR) else Color(ChromeGlass.SOLID_LIGHT_COLOR)
                                    } else {
                                        containerColor
                                    }
                                    drawRect(color = fill)
                                } else if (mode == GlassEffectMode.SOLID) {
                                    drawRect(color = if (isDark) Color(ChromeGlass.SOLID_DARK_COLOR) else Color(ChromeGlass.SOLID_LIGHT_COLOR))
                                }
                            }
                        }
                )
            }

            // Lapisan Konten Kapsul (kilau sentuh di atas latar, di bawah ikon)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        val glow = touchGlow.value
                        if (glow > 0.001f && glowCenter.offset.isSpecified) {
                            drawTouchGlow(glowCenter.offset, glow)
                        }
                        drawContent()
                    },
                content = content
            )
        }

        // 2. Overlay Unclipped (untuk Indikator Tab Aktif yang menonjol keluar)
        if (unclippedOverlay != null) {
            val scope = remember(boxBoundsInRoot, boxSize) {
                LiquidGlassCapsuleScope(boxBoundsInRoot = boxBoundsInRoot, boxSize = boxSize)
            }
            unclippedOverlay(scope)
        }
    }
}

/** Tint permukaan Kyant0, dipakai elemen tunggal maupun [LiquidGlassGroupSurface]. */
fun glassContainerColor(isDark: Boolean): Color = if (isDark) {
    Color(0xFF121212).copy(alpha = 0.40f)
} else {
    Color(0xFFFAFAFA).copy(alpha = 0.40f)
}

/** Tint tubuh kaca: pilihan Clear/Tinted pengguna bila ada, selain itu tint bawaan. */
fun glassSurfaceTint(settings: LiquidGlassSettings, isDark: Boolean): Color =
    settings.surfaceTintArgb?.let { Color(it) } ?: glassContainerColor(isDark)

/** Posisi kilau sentuh terakhir; bukan state agar memindahkannya tidak memicu recompose. */
private class GlowCenter {
    var offset: Offset = Offset.Unspecified
}

/**
 * Sama dengan kilau `InteractiveHighlight`, yaitu cahaya pill di pojok kanan atas: seluruh wajah
 * terangkat sedikit, ditambah genangan gaussian di bawah jari (tiga pita gradien kira-kira
 * exp(-d²/2σ²)).
 */
private fun DrawScope.drawTouchGlow(center: Offset, progress: Float) {
    drawRect(Color.White.copy(alpha = MorphPress.LightLift * progress), blendMode = BlendMode.Plus)
    val pos = Offset(center.x.coerceIn(0f, size.width), center.y.coerceIn(0f, size.height))
    val pool = MorphPress.LightPool * progress
    drawRect(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = pool),
            1f / 3f to Color.White.copy(alpha = pool * 0.607f),
            2f / 3f to Color.White.copy(alpha = pool * 0.135f),
            1f to Color.Transparent,
            center = pos,
            radius = (3f * MorphPress.LightRadius * density).coerceAtLeast(1f)
        ),
        blendMode = BlendMode.Plus
    )
}

/**
 * Port `Shadow` Kyant0 (`com.kyant.backdrop.shadow`): bayangan jatuh ter-blur dengan offset
 * `radius / 6`, dan area bentuk tidak ikut digambari agar tidak tembus ke kaca.
 *
 * Kyant0 menghapus area bentuk dengan `BlendMode.Clear` di layer offscreen. Di sini area itu
 * di-`clipOut` langsung (klip rounded-rect analitik di GPU), jadi tidak ada render pass
 * tambahan per elemen kaca: hasilnya sama, tapi jauh lebih murah saat ukuran kapsul
 * berubah setiap frame (morph navbar) pada GPU tile-based seperti Mali.
 */
fun Modifier.kyantShadow(
    alpha: () -> Float,
    radius: Dp = 24.dp,
    color: Color = Color.Black.copy(alpha = 0.1f),
    cornerRadiusPx: Float = Float.POSITIVE_INFINITY
): Modifier = this.drawWithCache {
    val radiusPx = radius.toPx()
    val offsetY = radiusPx / 6f
    val cornerPx = min(cornerRadiusPx, size.minDimension / 2f)
    val paint = Paint().apply {
        this.color = color
        if (radiusPx > 0f) asFrameworkPaint().maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
    }
    val shapePath = Path().apply {
        addRoundRect(RoundRect(size.toRect(), CornerRadius(cornerPx, cornerPx)))
    }
    onDrawWithContent {
        val a = alpha().coerceIn(0f, 1f)
        if (a > 0.001f) {
            paint.alpha = color.alpha * a
            clipPath(shapePath, ClipOp.Difference) {
                val canvas = drawContext.canvas
                canvas.drawRoundRect(0f, offsetY, size.width, size.height + offsetY, cornerPx, cornerPx, paint)
            }
        }
        drawContent()
    }
}

private data class InnerShadowRecordKey(
    val width: Float,
    val height: Float,
    val cornerPx: Float,
    val radiusPx: Float,
    val color: Color
)

private class InnerShadowRecorder {
    var key: InnerShadowRecordKey? = null
    var clip: Path? = null
    var paint: Paint? = null
}

/**
 * Port `InnerShadow` Kyant0: kapsul diisi warna bayangan, salinannya yang bergeser ke bawah
 * sejauh `radius` dihapus, hasilnya di-blur (`TileMode.Decal`) lalu diklip ke kapsul.
 *
 * Layer offscreen hanya direkam ulang saat ukuran, radius, sudut, atau warna berubah; alpha
 * hanya di-set sebagai properti layer. Layer di-`remember` agar `renderEffect` blur tidak
 * hilang ketika node detach lalu attach kembali (padanan fix `prevRadius` di Kyant0).
 */
@Composable
fun Modifier.kyantInnerShadow(
    radiusPx: () -> Float,
    alpha: () -> Float,
    color: Color = Color.Black.copy(alpha = 0.15f)
): Modifier {
    val layer = rememberGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
    val recorder = remember { InnerShadowRecorder() }
    return this.drawWithContent {
        drawContent()
        val r = radiusPx()
        val a = alpha()
        if (r <= 0.01f || a <= 0.001f) return@drawWithContent

        val width = size.width
        val height = size.height
        val cornerPx = size.minDimension / 2f
        val key = InnerShadowRecordKey(width, height, cornerPx, r, color)

        layer.alpha = a.coerceAtMost(1f)
        if (key != recorder.key) {
            val paint = recorder.paint ?: Paint().also { recorder.paint = it }
            paint.color = color
            val clip = recorder.clip ?: Path().also { recorder.clip = it }
            clip.reset()
            clip.addRoundRect(RoundRect(size.toRect(), CornerRadius(cornerPx, cornerPx)))
            layer.renderEffect = BlurEffect(r, r, TileMode.Decal)
            layer.record {
                val canvas = drawContext.canvas
                canvas.save()
                canvas.clipPath(clip)
                canvas.drawRoundRect(0f, 0f, width, height, cornerPx, cornerPx, paint)
                canvas.translate(0f, r)
                canvas.drawRoundRect(0f, 0f, width, height, cornerPx, cornerPx, ShadowMaskPaint)
                canvas.restore()
            }
            recorder.key = key
        }
        val clip = recorder.clip ?: return@drawWithContent
        clipPath(clip) {
            drawLayer(layer)
        }
    }
}

/**
 * Port `Highlight.Default` Kyant0: garis tepi `0.5dp`, putih 50%, sudut cahaya `45°`,
 * `BlendMode.Plus`, dengan opasitas lapisan [alpha].
 *
 * Garis digambar langsung dengan `BlendMode.Plus` (tanpa layer offscreen), dan shader AGSL-nya
 * dipakai bersama ([HighlightShaders]) alih-alih dikompilasi ulang setiap ukuran berubah.
 * Blur `0.25dp` Kyant0 (setengah piksel, setara anti-aliasing) tidak dipakai: pada garis stroke,
 * `BlurMaskFilter` memaksa Skia membuat mask + blur offscreen tiap kali ukuran kapsul berubah.
 */
fun Modifier.kyantHighlight(
    alpha: () -> Float,
    width: Dp = 0.5.dp,
    color: Color = Color.White.copy(alpha = 0.5f),
    angleDeg: Float = 45f,
    falloff: Float = 1f,
    cornerRadiusPx: Float = Float.POSITIVE_INFINITY
): Modifier = this.drawWithCache {
    val cornerPx = min(cornerRadiusPx, size.minDimension / 2f)
    val widthPx = width.toPx()
    val paint = Paint().apply {
        style = PaintingStyle.Stroke
        strokeWidth = ceil(min(widthPx, size.minDimension / 2f)) * 2f
        this.color = color
        blendMode = BlendMode.Plus
        isAntiAlias = true
    }
    val clip = Path().apply {
        addRoundRect(RoundRect(size.toRect(), CornerRadius(cornerPx, cornerPx)))
    }
    val shapeW = size.width
    val shapeH = size.height
    onDrawWithContent {
        drawContent()
        val a = alpha()
        if (a <= 0.001f) return@onDrawWithContent
        paint.alpha = color.alpha * a.coerceAtMost(1f)
        HighlightShaders.apply(paint, shapeW, shapeH, cornerPx, Color(color.red, color.green, color.blue, 1f), angleDeg, falloff)
        clipPath(clip) {
            drawContext.canvas.drawRoundRect(0f, 0f, shapeW, shapeH, cornerPx, cornerPx, paint)
        }
    }
}

/**
 * Satu instance [RuntimeShader] highlight dipakai bersama semua elemen kaca. Membuat
 * `RuntimeShader` baru berarti mengompilasi AGSL di thread UI; saat navbar morph, ukuran
 * kapsul berubah tiap frame sehingga dulu terjadi beberapa kompilasi per frame.
 *
 * Aman dipakai bersama: uniform di-set tepat sebelum `draw*`, dan kanvas merekam salinan
 * shader native saat itu juga.
 */
private object HighlightShaders {
    private val shader: RuntimeShader? by lazy(LazyThreadSafetyMode.NONE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(LiquidGlassAgsl.HIGHLIGHT_SHADER_SOURCE)
        } else {
            null
        }
    }

    fun apply(
        paint: Paint,
        width: Float,
        height: Float,
        cornerPx: Float,
        color: Color,
        angleDeg: Float,
        falloff: Float
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val s = shader ?: return
        s.setFloatUniform("size", width, height)
        s.setFloatUniform("cornerRadii", cornerPx, cornerPx, cornerPx, cornerPx)
        s.setFloatUniform("color", color.red, color.green, color.blue, color.alpha)
        s.setFloatUniform("angle", (angleDeg * (PI / 180f)).toFloat())
        s.setFloatUniform("falloff", falloff)
        paint.asFrameworkPaint().shader = s
    }
}

private val ShadowMaskPaint = Paint().apply { blendMode = BlendMode.Clear }

/**
 * Indikator tab aktif, port Box indikator di `LiquidBottomTabs.kt` (Kyant0/AndroidLiquidGlass).
 *
 * Lensa mengambil sampel gabungan backdrop halaman + [tabsLayer] (baris tab beraksen yang
 * tidak terlihat), sehingga ikon yang berada tepat di bawah lensa tampil beraksen & membesar.
 * Transformasi (translasi + skala) dihitung di sini, dan backdrop digambar dengan skala terbalik
 * (setara `InverseLayerScope` Kyant0) agar sampelnya tetap sejajar 1:1 dengan layar.
 *
 * @param groupOriginInRoot posisi root dari induk tempat indikator diletakkan di (0, 0).
 * @param tabsLayerOffset posisi [tabsLayer] relatif terhadap induk yang sama.
 * @param translation posisi kiri-atas indikator (sebelum skala) relatif terhadap induk.
 */
@Composable
fun LiquidGlassTabIndicator(
    backdropState: LiquidGlassBackdropState,
    tabsLayer: GraphicsLayer?,
    groupOriginInRoot: () -> Offset,
    tabsLayerOffset: () -> Offset,
    translation: () -> Offset,
    scaleX: () -> Float,
    scaleY: () -> Float,
    pressProgress: () -> Float,
    isDark: Boolean,
    mode: GlassEffectMode,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val refractionHeightPx = with(density) { 10.dp.toPx() }
    val refractionAmountPx = with(density) { 14.dp.toPx() }
    val innerShadowRadiusPx = with(density) { 8.dp.toPx() }

    val lensHolder = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) KyantRefractionShaderHolder() else null
    }
    val isLensActive = lensHolder != null && mode == GlassEffectMode.PREMIUM
    // iOS 27 sinks the selected tab into the glass in dark mode: a capsule darker than the
    // bar (measured ~23 vs ~42 luminance), not the lighter iOS 26 one.
    val restingColor = if (isDark) Color.Black.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.1f)

    Box(
        modifier = Modifier
            .graphicsLayer {
                val t = translation()
                translationX = t.x
                translationY = t.y
                this.scaleX = scaleX()
                this.scaleY = scaleY()
            }
            .then(modifier)
            .kyantInnerShadow(
                radiusPx = { innerShadowRadiusPx * pressProgress() },
                alpha = pressProgress
            )
            .kyantShadow(alpha = pressProgress)
            .kyantHighlight(alpha = pressProgress)
            .clip(RoundedCornerShape(percent = 50))
            .drawWithContent {
                drawContent()
                val p = pressProgress()
                drawRect(restingColor, alpha = (1f - p).coerceIn(0f, 1f))
                drawRect(Color.Black.copy(alpha = 0.03f * p.coerceIn(0f, 1f)))
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val p = pressProgress()
                    renderEffect = if (isLensActive && p > 0.001f && size.width > 1f && size.height > 1f &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        lensHolder!!.buildRenderEffect(
                            width = size.width,
                            height = size.height,
                            cornerRadiusPx = size.minDimension / 2f,
                            refractionHeightPx = refractionHeightPx * p,
                            refractionAmountPx = refractionAmountPx * p,
                            depthEffect = false,
                            chromaticAberration = 0.5f,
                            blurRadiusPx = 0f,
                            saturation = 1f
                        )?.asComposeRenderEffect()
                    } else {
                        null
                    }
                }
                .drawBehind {
                    val p = pressProgress()
                    val sx = scaleX().takeIf { it > 0.01f } ?: 1f
                    val sy = scaleY().takeIf { it > 0.01f } ?: 1f
                    val t = translation()
                    // Sudut kiri-atas indikator di layar (setelah skala terhadap pusat), relatif ke induk
                    val topLeft = Offset(
                        t.x + size.width * (1f - sx) / 2f,
                        t.y + size.height * (1f - sy) / 2f
                    )
                    // Diam, indikator tidak lebih besar dari slotnya dan seluruhnya tertutup
                    // [tabsLayer] (kaca beraksen yang buram). Menggambar halaman di bawahnya hanya
                    // memutar ulang seluruh display list halaman tiap frame tanpa terlihat; halaman
                    // baru perlu saat lensa ditekan, membesar melewati baris, dan membiaskannya.
                    val lensOut = p > 0.001f || sx > 1.001f || sy > 1.001f
                    scale(1f / sx, 1f / sy, pivot = Offset.Zero) {
                        if (mode != GlassEffectMode.SOLID && backdropState.rootBounds.width > 0f &&
                            (tabsLayer == null || lensOut)
                        ) {
                            // Dibaca di sini saja: diam, scroll halaman tidak perlu merekam ulang indikator.
                            @Suppress("UNUSED_VARIABLE")
                            val tick = backdropState.drawTick
                            val origin = groupOriginInRoot()
                            translate(
                                left = backdropState.rootBounds.left - origin.x - topLeft.x,
                                top = backdropState.rootBounds.top - origin.y - topLeft.y
                            ) {
                                drawLayer(backdropState.contentLayer)
                            }
                        }
                        if (tabsLayer != null) {
                            val o = tabsLayerOffset()
                            translate(left = o.x - topLeft.x, top = o.y - topLeft.y) {
                                drawLayer(tabsLayer)
                            }
                        }
                    }
                }
        )
    }
}

/** Saturasi vibrancy Kyant0 yang diterapkan langsung di dalam shader refraksi. */
private const val VIBRANCY_SATURATION = 1.5f

/**
 * Holder pembangun Android RenderEffect yang menggabungkan:
 * Blur (TileMode CLAMP) -> Kyant Refraction Shader, dengan vibrancy (saturasi 1.5x)
 * dihitung di dalam shader sehingga tidak ada render pass color filter terpisah.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class KyantRefractionShaderHolder {
    private val runtimeShader = RuntimeShader(LiquidGlassAgsl.SHADER_SOURCE)

    private var lastW = -1f
    private var lastH = -1f
    private var lastR = -1f
    private var lastRefHeight = -1f
    private var lastRefAmount = -1f
    private var lastDepth = false
    private var lastDisp = -1f
    private var lastBlur = -1f
    private var lastSaturation = -1f
    private var cachedShaderEffect: AndroidRenderEffect? = null
    private var cachedBlurEffect: AndroidRenderEffect? = null
    private var cachedEffect: AndroidRenderEffect? = null

    fun buildRenderEffect(
        width: Float,
        height: Float,
        cornerRadiusPx: Float,
        refractionHeightPx: Float,
        refractionAmountPx: Float,
        depthEffect: Boolean,
        chromaticAberration: Float,
        blurRadiusPx: Float,
        saturation: Float = VIBRANCY_SATURATION
    ): AndroidRenderEffect? {
        if (width <= 1f || height <= 1f) return null

        // Saat ukuran berubah (morph/press), lewati 7 sampel chromatic dispersion.
        // Efek tetap satu objek; hanya uniform yang diperbarui.
        val resizing = width != lastW || height != lastH
        val dispersion = if (resizing) 0f else max(chromaticAberration, 0f)
        val uniformsChanged = resizing ||
            cornerRadiusPx != lastR ||
            refractionHeightPx != lastRefHeight ||
            refractionAmountPx != lastRefAmount ||
            depthEffect != lastDepth ||
            dispersion != lastDisp ||
            saturation != lastSaturation

        if (uniformsChanged) {
            runtimeShader.setFloatUniform("size", width, height)
            runtimeShader.setFloatUniform("offset", 0f, 0f)
            runtimeShader.setFloatUniform("cornerRadii", cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx)
            runtimeShader.setFloatUniform("refractionHeight", max(refractionHeightPx, 0.5f))
            runtimeShader.setFloatUniform("refractionAmount", -refractionAmountPx)
            runtimeShader.setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
            runtimeShader.setFloatUniform("chromaticAberration", dispersion)
            runtimeShader.setFloatUniform("saturation", saturation)

            lastW = width
            lastH = height
            lastR = cornerRadiusPx
            lastRefHeight = refractionHeightPx
            lastRefAmount = refractionAmountPx
            lastDepth = depthEffect
            lastDisp = dispersion
            lastSaturation = saturation
            // createRuntimeShaderEffect menyalin uniform saat dibuat (copy-on-write Skia),
            // jadi effect lama tidak ikut berubah. Buat ulang; murah karena AGSL tidak
            // dikompilasi ulang, hanya objek image filter baru.
            cachedShaderEffect = null
        }

        val blurChanged = blurRadiusPx != lastBlur
        if (blurChanged) {
            cachedBlurEffect = if (blurRadiusPx > 0.5f) {
                AndroidRenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
            } else {
                null
            }
            lastBlur = blurRadiusPx
        }

        if (cachedShaderEffect == null || cachedEffect == null || blurChanged) {
            val shaderEffect = cachedShaderEffect ?: AndroidRenderEffect
                .createRuntimeShaderEffect(runtimeShader, "content")
                .also { cachedShaderEffect = it }
            val blurEffect = cachedBlurEffect
            cachedEffect = if (blurEffect != null) {
                AndroidRenderEffect.createChainEffect(shaderEffect, blurEffect)
            } else {
                shaderEffect
            }
        }
        return cachedEffect
    }
}
