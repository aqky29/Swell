package com.music.swell.desktop

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.music.swell.playback.smart.BeatTracker
import com.music.swell.playback.smart.VocalTracker
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Whether the Beat This! and Open-Unmix ONNX models load and run in this build. */
class DesktopBeatModelTest {

    @Test
    fun `the beat model loads in this build`() {
        DesktopAnalysisRuntime.ensureStarted()
        if (!DesktopAnalysisRuntime.available) {
            println("analysis library unavailable — skipping")
            return
        }
        val model = File(DesktopAnalysisRuntime.modelPath(BeatTracker.MODEL_ASSET))
        assertTrue(model.isFile && model.length() > 0, "the model was not unpacked")

        val outcome = runCatching {
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            OrtEnvironment.getEnvironment().createSession(model.absolutePath, options).use { session ->
                session.inputNames.first()
            }
        }
        outcome.exceptionOrNull()?.let { println("beat model load failed: ${it.message}") }
        assertTrue(
            outcome.isSuccess,
            "the beat model would not load, so Automix has no meter: ${outcome.exceptionOrNull()?.message}",
        )
    }

    @Test
    fun `the dynamic vocal model loads and runs on a short window`() {
        DesktopAnalysisRuntime.ensureStarted()
        if (!DesktopAnalysisRuntime.available) {
            println("analysis library unavailable — skipping")
            return
        }
        val model = File(DesktopAnalysisRuntime.modelPath(VocalTracker.MODEL_ASSET))
        assertTrue(model.isFile && model.length() > 0, "the vocal model was not unpacked")

        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        env.createSession(model.absolutePath, options).use { session ->
            val frames = 64
            val bins = 2049
            val backing = ByteBuffer.allocateDirect(2 * bins * frames * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            val shape = longArrayOf(1L, 2L, bins.toLong(), frames.toLong())
            OnnxTensor.createTensor(env, backing.asFloatBuffer(), shape).use { tensor ->
                session.run(mapOf(session.inputNames.first() to tensor)).use { outputs ->
                    val outTensor = outputs.get(0) as OnnxTensor
                    val outShape = outTensor.info.shape
                    assertEquals(listOf(1L, 2L, bins.toLong(), frames.toLong()), outShape.toList())
                }
            }
        }
    }
}
