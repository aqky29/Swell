package com.music.swell.ui.components.liquidglass.morph

import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.max
import kotlin.math.min

/**
 * One frame of the morph's shapes and material, in the overlay's pixel coordinates.
 *
 * [incoming] is the menu's glass and [source] the trigger's lobe; while [hasSource] is set the
 * two are fused into one surface, with [smoothing] as how far the fusing reaches.
 */
internal class MorphSurfaceState {
  val incoming = RectF()
  val source = RectF()
  var hasSource = true
  var radiusX = 0f
  var radiusY = 0f
  var sourceRadius = 0f
  var smoothing = 0f

  /** Field grid cell size, fixed for a whole opening so the neck does not shimmer as it moves. */
  var lockedFieldStep = 0f
  var lensHeight = 0f
  var lensAmount = 0f

  /** 0 is the resting trigger's material, 1 the open menu's. */
  var materialProgress = 0f
  var surfaceOpacity = 1f
  var dark = true

  /** Opaque surface and no lens: the glass replaced by a flat fill (Solid mode). */
  var solid = false

  /**
   * The resting control's own look. The reference's control is a 1dp frost with no body; Swell's
   * is whatever Settings → Liquid Glass asks for. The morph starts from it and ends as the menu.
   */
  var controlBlur = 1f
  var hasControlTint = false
  var controlTint = 0

  /** The finger's light on the surface: where, how far it reaches, and how strongly it lifts. */
  var pressX = 0f
  var pressY = 0f
  var pressRadius = 1f
  var poolGain = 0f
  var pressLift = 0f
  var rimLift = 0f

  /** How much of the lens is in force, 0 to 1: a flat face that still has the material's colour. */
  var lensScale = 1f

  /** False draws the face with no rim, for glass that sits under another surface's rim. */
  var rimEnabled = true
}

/**
 * Builds the AGSL effects that draw the morphing glass: the material over a backdrop, and the
 * shadow under it. A port of the reference's `GlassRenderer` minus its RenderNode plumbing.
 *
 * While the two shapes are fused, the neck between them comes from a Gaussian-smoothed
 * smooth-union field built on the CPU ([MorphGaussianField]) and read back as a texture; that
 * blur is what makes the neck round and liquid rather than a sharp crease.
 *
 * Uniforms are copied into a [RenderEffect] when it is created, so a fresh effect is built
 * every frame; the compiled [RuntimeShader] behind it is reused.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class MorphRenderer(private val density: Float) {
  private val material = RuntimeShader(MorphMaterialShader)
  // Only the menu draws these, so a resting control never compiles them.
  private val shadow by lazy(LazyThreadSafetyMode.NONE) { RuntimeShader(MorphShadowShader) }
  private val portal by lazy(LazyThreadSafetyMode.NONE) { RuntimeShader(MorphPortalShader) }
  private val union = RectF()
  private val field = MorphGaussianField(density * FieldStep)
  private val texture = MorphFieldTexture()
  private var texturePrepared = false

  /** The shadow's shader, ready to paint [shadowRect]; shape coordinates are the overlay's. */
  val shadowShader: Shader get() = shadow
  val shadowRect = RectF()

  /**
   * Binds the shapes and sizes the shadow. Call once per frame before [materialEffect] and
   * before drawing [shadowShader].
   */
  fun prepare(state: MorphSurfaceState) {
    // The CPU field only matters while two shapes are being fused.
    val fusing = state.hasSource && state.smoothing > FieldSmoothingMinimum
    val built = fusing && run {
      if (!texturePrepared) {
        texture.preallocate()
        texturePrepared = true
      }
      field.update(
        state.incoming, state.radiusX, state.radiusY,
        state.source, state.sourceRadius, state.smoothing, state.lockedFieldStep,
      )
    }
    texture.update(if (built) field else null)
    bindField(material, state)
    bindField(portal, state)
    bindField(shadow, state)

    union.set(state.incoming)
    if (state.hasSource) union.union(state.source)
    val t = state.materialProgress.coerceIn(0f, 1f)
    val shadowRadius = (4f + 16f * t) * density
    val guard = shadowRadius * 1.6f + 6f * density + state.smoothing
    shadowRect.set(union.left - guard, union.top - guard, union.right + guard, union.bottom + guard)
    shadow.setFloatUniform("shadowParams", shadowRadius, .04f + .52f * t, 7f * density, .28f)
    shadow.setFloatUniform("surfaceOpacity", state.surfaceOpacity)
  }

  /**
   * [prepare] for a control that is only ever one shape at rest: binds the material alone, with
   * no neck and no shadow to build.
   */
  fun prepareResting(state: MorphSurfaceState) {
    texture.update(null)
    bindField(material, state)
  }

  private fun bindField(shader: RuntimeShader, state: MorphSurfaceState) {
    val a = state.incoming
    shader.setFloatUniform("center", a.centerX(), a.centerY())
    shader.setFloatUniform("halfSize", a.width() / 2f, a.height() / 2f)
    shader.setFloatUniform("cornerRadii", state.radiusX, state.radiusY)
    val b = if (state.hasSource) state.source else a
    shader.setFloatUniform("secondaryEnabled", if (state.hasSource) 1f else 0f)
    shader.setFloatUniform("secondaryCenter", b.centerX(), b.centerY())
    shader.setFloatUniform("secondaryHalfSize", b.width() / 2f, b.height() / 2f)
    shader.setFloatUniform("secondaryRadii", state.sourceRadius, state.sourceRadius)
    shader.setFloatUniform("smoothing", state.smoothing)
    texture.bind(shader)
  }

  /**
   * The material over the backdrop: the backdrop is blurred, then the shader bends, tints and
   * rims it inside the fused shape. The layer this is applied to starts at
   * ([nodeLeft], [nodeTop]) in the overlay and is [nodeWidth] by [nodeHeight] pixels.
   *
   * [state] must have been through [prepare] this frame.
   */
  fun materialEffect(
    state: MorphSurfaceState,
    nodeLeft: Float,
    nodeTop: Float,
    nodeWidth: Float,
    nodeHeight: Float,
  ): RenderEffect {
    val t = state.materialProgress.coerceIn(0f, 1f)
    val clearing = min(t * 6f, 1f)
    val base = state.controlBlur + (MorphBlur - state.controlBlur) * clearing
    val blurRadius = max(base + (MenuBlur - base) * t * t * t, .01f) * density

    val control = if (state.dark) MorphRecipe.ControlDark else MorphRecipe.ControlLight
    val menu = if (state.dark) MorphRecipe.MenuDark else MorphRecipe.MenuLight
    fun mix(a: Float, b: Float) = a + (b - a) * t

    material.setFloatUniform("nodeOrigin", nodeLeft, nodeTop)
    material.setFloatUniform("sampleExtent", nodeWidth, nodeHeight)
    material.setFloatUniform("density", density)
    material.setFloatUniform(
      "face", mix(control.saturation, menu.saturation), mix(control.black, menu.black),
      mix(control.white, menu.white), mix(control.rollOff, menu.rollOff),
    )
    val tintProgress = min(t * 3f, 1f)
    if (state.solid) {
      val fill = if (state.dark) SolidDark else SolidLight
      material.setFloatUniform("faceFill", fill, fill, fill, 1f)
      material.setFloatUniform("tint", 0f, 0f, 0f, 0f)
    } else {
      material.setFloatUniform("faceFill", 1f, 1f, 1f, mix(control.fillAlpha, menu.fillAlpha))
      if (state.hasControlTint) {
        val from = state.controlTint
        fun toward(start: Int, end: Float) = start / 255f + (end - start / 255f) * tintProgress
        material.setFloatUniform(
          "tint",
          toward((from shr 16) and 0xFF, menu.tint),
          toward((from shr 8) and 0xFF, menu.tint),
          toward(from and 0xFF, menu.tint),
          toward((from ushr 24) and 0xFF, menu.tintAlpha),
        )
      } else {
        material.setFloatUniform(
          "tint", menu.tint, menu.tint, menu.tint,
          control.tintAlpha + (menu.tintAlpha - control.tintAlpha) * tintProgress,
        )
      }
    }

    union.set(state.incoming)
    if (state.hasSource) union.union(state.source)
    val extent = min(union.width(), union.height()) / density
    val innerHeight = min(extent * .25f, 20f) * density
    val innerAmount = min(extent * .5f, 60f) * density
    val outerHeight = max(16f, extent * .2f) * density
    val outerAmount = max(16f, extent * .25f) * density
    if (state.solid) {
      material.setFloatUniform("lensHeight", 0f)
      material.setFloatUniform("lensAmount", 0f)
      material.setFloatUniform("outerHeight", 0f)
      material.setFloatUniform("outerAmount", 0f)
    } else {
      val lens = state.lensScale.coerceIn(0f, 1f)
      material.setFloatUniform("lensHeight", max(state.lensHeight, innerHeight) * lens)
      material.setFloatUniform("lensAmount", min(state.lensAmount, -innerAmount) * lens)
      material.setFloatUniform("outerHeight", outerHeight * lens)
      material.setFloatUniform("outerAmount", -outerAmount * lens)
    }
    material.setFloatUniform("keyThreshold", if (state.dark) .2588f else -.2756f)
    val rim = if (state.rimEnabled) 1f else 0f
    material.setFloatUniform("highlightAlpha", rim)
    material.setFloatUniform("rimAlpha", rim * if (state.dark) MorphRecipe.RimAlphaDark else MorphRecipe.RimAlphaLight)
    material.setFloatUniform("surfaceOpacity", state.surfaceOpacity)
    material.setFloatUniform("press", state.pressX, state.pressY, state.pressRadius, state.poolGain)
    material.setFloatUniform("pressLift", state.pressLift)
    material.setFloatUniform("rimLift", state.rimLift)

    val shade = RenderEffect.createRuntimeShaderEffect(material, "content")
    // A backdrop that arrives already blurred needs no second pass.
    if (state.controlBlur <= 0f && t <= 0f) return shade
    val blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
    return RenderEffect.createChainEffect(shade, blur)
  }

  /**
   * Bends what is drawn inside the fused shape the way the glass bends the backdrop, so the
   * lobe's content and the menu's rows are drawn through the same lens while the shape is
   * moving. The layer this is applied to starts at ([nodeLeft], [nodeTop]) in the overlay.
   * [blurRadius] is the extra softening the reference adds while the portal is opening.
   *
   * [state] must have been through [prepare] this frame.
   */
  fun portalEffect(state: MorphSurfaceState, nodeLeft: Float, nodeTop: Float, blurRadius: Float): RenderEffect {
    portal.setFloatUniform("nodeOrigin", nodeLeft, nodeTop)
    portal.setFloatUniform("height", state.lensHeight)
    portal.setFloatUniform("amount", state.lensAmount)
    val bend = RenderEffect.createRuntimeShaderEffect(portal, "content")
    return if (blurRadius > .01f) {
      RenderEffect.createChainEffect(
        bend, RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.DECAL),
      )
    } else bend
  }

  private companion object {
    // The reference's defaults: the menu's frost, the trigger's, and the blur it passes through.
    const val FieldStep = 4f
    const val FieldSmoothingMinimum = .05f
    const val MenuBlur = 16f
    const val MorphBlur = 1.2f
    const val SolidDark = 0x1C / 255f
    const val SolidLight = 0xF2 / 255f
  }
}
