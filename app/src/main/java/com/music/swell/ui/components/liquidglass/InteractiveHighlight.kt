package com.music.swell.ui.components.liquidglass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.music.swell.ui.components.liquidglass.morph.MorphPress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pelacak gestur drag/touch tanpa mengonsumsi event terlalu dini
 * (diadaptasi langsung dari `DragGestureInspector.kt` di Kyant0/AndroidLiquidGlass).
 */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        val drag = initialDown

        onDragStart(down)
        onDrag(drag, Offset.Zero)
        val upEvent = drag(
            pointerId = drag.id,
            onDrag = { onDrag(it, it.positionChange()) }
        )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) {
            return null
        }
        if (change.changedToUpIgnoreConsumed()) {
            return change
        }
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) {
                return dragEvent
            }
        }
    }
}

/**
 * Efek kilau sentuh interaktif (radial specular bloom) yang mengikuti posisi jari
 * (diadaptasi langsung dari `InteractiveHighlight.kt` pada Kyant0/AndroidLiquidGlass).
 */
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    private val pressProgressAnimationSpec = spring<Float>(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring<Offset>(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShader(
            """
            uniform float2 size;
            uniform float4 color;
            uniform float radius;
            uniform float2 position;

            half4 main(float2 coord) {
                // The pill's light: a gaussian pool under the finger, not a flat bloom.
                float2 v = coord - position;
                float intensity = exp(-dot(v, v) / (2.0 * radius * radius));
                return color * intensity;
            }
            """
        )
    } else {
        null
    }

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            val pos = position(size, positionAnimation.value)
            if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Same light as the resting pill takes under a finger: a small lift of the whole
                // face and a pool that follows the touch.
                drawRect(
                    Color.White.copy(MorphPress.LightLift * progress),
                    blendMode = BlendMode.Plus
                )
                shader.apply {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("color", 1f, 1f, 1f, MorphPress.LightPool * progress)
                    setFloatUniform("radius", MorphPress.LightRadius * density)
                    setFloatUniform(
                        "position",
                        pos.x.fastCoerceIn(0f, size.width),
                        pos.y.fastCoerceIn(0f, size.height)
                    )
                }
                drawRect(
                    ShaderBrush(shader),
                    blendMode = BlendMode.Plus
                )
            } else {
                val r = (size.minDimension * 1.5f).coerceAtLeast(1f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.22f * progress), Color.Transparent),
                        center = pos,
                        radius = r
                    ),
                    blendMode = BlendMode.Plus
                )
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            }
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}

/**
 * Pengatur animasi geser dan skala elastis tab pill
 * (diadaptasi langsung dari `DampedDragAnimation.kt` pada Kyant0/AndroidLiquidGlass).
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float = 0.001f,
    val initialScale: Float = 1f,
    val pressedScale: Float = 78f / 56f,
    val onDragStarted: (DampedDragAnimation.(position: Offset) -> Unit)? = null,
    val onDragStopped: (DampedDragAnimation.() -> Unit)? = null,
    val onDrag: (DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit)? = null
) {
    private val valueAnimationSpec = spring<Float>(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring<Float>(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring<Float>(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring<Float>(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring<Float>(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()

    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val progress: Float get() = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted?.invoke(this@DampedDragAnimation, down.position)
                press()
            },
            onDragEnd = {
                onDragStopped?.invoke(this@DampedDragAnimation)
                release()
            },
            onDragCancel = {
                onDragStopped?.invoke(this@DampedDragAnimation)
                release()
            }
        ) { change, dragAmount ->
            onDrag?.invoke(this@DampedDragAnimation, size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            withFrameNanos { }
            // Lensa tetap mengembang selama indikator masih meluncur, baru mengempis saat
            // jaraknya ke target < 2.5% rentang (sama seperti Kyant0).
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(newValue: Float) {
        val target = newValue.coerceIn(valueRange)
        animationScope.launch {
            launch {
                valueAnimation.animateTo(target, valueAnimationSpec) {
                    updateVelocity()
                }
            }
        }
    }

    fun animateToValue(newValue: Float) {
        val target = newValue.coerceIn(valueRange)
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            System.currentTimeMillis(),
            Offset(value, 0f)
        )
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
        val targetVelocity = velocityTracker.calculateVelocity().x / span
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}
