package com.music.swell.playback.smart

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartModelOptimizationTest {

    @Test
    fun `regularizeBeats fills short dropped-beat gaps without altering steady beats`() {
        // 120 BPM (0.5 s interval) with beat at 2.0 s dropped and beats at 4.0, 4.5 s dropped
        val raw = listOf(0.5, 1.0, 1.5, /* 2.0 missing */ 2.5, 3.0, 3.5, /* 4.0, 4.5 missing */ 5.0, 5.5)
        val reg = BeatTracker.regularizeBeats(raw, beatInterval = 0.5)

        val expected = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5)
        assertEquals(expected.size, reg.size)
        for (i in expected.indices) {
            assertTrue(abs(expected[i] - reg[i]) < 1e-6, "beat[$i] expected ${expected[i]} got ${reg[i]}")
        }
    }

    @Test
    fun `regularizeBeats leaves long silence gaps untouched`() {
        // 6-beat gap (3.0 s at 120 BPM) exceeds the 4-beat max hole fill
        val raw = listOf(0.5, 1.0, 1.5, 2.0, 5.0, 5.5, 6.0)
        val reg = BeatTracker.regularizeBeats(raw, beatInterval = 0.5)
        assertEquals(raw, reg)
    }

    @Test
    fun `regularizeDownbeats restores dropped bar and removes rogue off-beat in 4-4`() {
        val fps = 50.0
        // 16 beats at 120 BPM (0.5 s apart): downbeats belong at indices 0, 4, 8, 12
        val beats = (0 until 16).map { it * 0.5 }
        val logits = FloatArray(500) { -4.0f }
        // Strong downbeat peaks at indices 0, 4, 12; index 8 dropped (weak), rogue peak at index 5
        for (idx in listOf(0, 4, 12)) {
            logits[(beats[idx] * fps).toInt()] = 3.0f
        }
        logits[(beats[5] * fps).toInt()] = 1.2f

        val rawDownbeats = listOf(beats[0], beats[4], beats[5], beats[12])
        val regularized = BeatTracker.regularizeDownbeats(beats, logits, rawDownbeats, fps)

        assertEquals(listOf(beats[0], beats[4], beats[8], beats[12]), regularized)
    }

    @Test
    fun `regularizeDownbeats locks 3-4 waltz meter cleanly`() {
        val fps = 50.0
        val beats = (0 until 12).map { it * 0.5 }
        val logits = FloatArray(400) { -4.0f }
        // Downbeats on indices 1, 4, 7, 10 (3/4 meter with pickup beat at 0)
        for (idx in listOf(1, 4, 7, 10)) {
            logits[(beats[idx] * fps).toInt()] = 3.5f
        }

        val rawDownbeats = listOf(beats[1], beats[4], beats[10]) // beat 7 missing
        val regularized = BeatTracker.regularizeDownbeats(beats, logits, rawDownbeats, fps)

        assertEquals(listOf(beats[1], beats[4], beats[7], beats[10]), regularized)
    }

    @Test
    fun `regularizeDownbeats falls back to raw downbeats on 5-4 meter`() {
        val fps = 50.0
        val beats = (0 until 15).map { it * 0.5 }
        val logits = FloatArray(500) { -4.0f }
        val rawDownbeats = listOf(beats[0], beats[5], beats[10])
        for (t in rawDownbeats) {
            logits[(t * fps).toInt()] = 3.5f
        }

        val regularized = BeatTracker.regularizeDownbeats(beats, logits, rawDownbeats, fps)
        assertEquals(rawDownbeats, regularized)
    }

    @Test
    fun `reduceToBandCurve weights by magnitude so quiet noise bins do not trigger false vocals`() {
        val bins = 64
        val frames = 4
        val fftSize = 128
        val sampleRate = 44100.0
        // lowBin = floor(200 * 128 / 44100) = 0, highBin = ceil(4000 * 128 / 44100) = 12
        val total = VocalSpectrogram.CHANNELS * bins * frames
        val mixBuf = ByteBuffer.allocateDirect(total * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val tgtBuf = ByteBuffer.allocateDirect(total * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        val mixArr = FloatArray(total) { 0.001f } // tiny noise floor everywhere
        val tgtArr = FloatArray(total) { 0.0009f } // 90% ratio on noise bins!

        // Dominant instrumental bin (bin 4) has high mix energy and near-zero vocal energy
        for (ch in 0 until VocalSpectrogram.CHANNELS) {
            for (f in 0 until frames) {
                val idx = (ch * bins + 4) * frames + f
                mixArr[idx] = 10.0f
                tgtArr[idx] = 0.05f
            }
        }
        mixBuf.put(mixArr)
        tgtBuf.put(tgtArr)
        mixBuf.clear()
        tgtBuf.clear()

        val curve = VocalTracker.reduceToBandCurve(mixBuf, tgtBuf, bins, frames, fftSize, sampleRate)
        assertEquals(frames, curve.size)
        for (v in curve) {
            assertTrue(v < 0.02f, "Expected low energy-weighted vocal score on instrumental, got $v")
        }
    }

    @Test
    fun `smoothVocalCurve removes single-frame spike while preserving sustained vocal`() {
        val raw = floatArrayOf(0.05f, 0.04f, 0.90f, 0.05f, 0.06f, 0.80f, 0.85f, 0.82f, 0.05f)
        val smoothed = VocalTracker.smoothVocalCurve(raw)

        // Single-frame spike at index 2 is removed
        assertTrue(smoothed[2] < 0.1f, "spike should be removed, got ${smoothed[2]}")
        // Sustained vocal frames at indices 5..7 are preserved
        assertTrue(smoothed[6] > 0.8f, "sustained vocal should remain high, got ${smoothed[6]}")
    }

    @Test
    fun `hasExactLyrics accepts word-synced spans and rejects open line-synced spans`() {
        val exact = listOf(
            LoopMix.Span(5.0, 8.0, certainUntil = 8.0),
            LoopMix.Span(10.0, 14.0, certainUntil = 14.0),
            LoopMix.Span(16.0, 20.0, certainUntil = 20.0),
            LoopMix.Span(22.0, 25.0, certainUntil = 25.0),
        )
        assertTrue(VocalTracker.hasExactLyrics(exact))

        val openEnded = listOf(
            LoopMix.Span(5.0, 12.0, certainUntil = 7.0),
            LoopMix.Span(12.0, 20.0, certainUntil = 14.0),
            LoopMix.Span(20.0, 28.0, certainUntil = 22.0),
            LoopMix.Span(28.0, 35.0, certainUntil = 30.0),
        )
        assertFalse(VocalTracker.hasExactLyrics(openEnded))
    }

    @Test
    fun `vocalCurveFromLyrics marks sung spans high and instrumental gaps low`() {
        val spans = listOf(
            LoopMix.Span(2.0, 4.0, certainUntil = 4.0),
            LoopMix.Span(8.0, 10.0, certainUntil = 10.0),
        )
        val curve = VocalTracker.vocalCurveFromLyrics(
            spans = spans,
            startSeconds = 0.0,
            durationSeconds = 12.0,
            frameRate = 10.0,
        )
        assertEquals(120, curve.size)
        // Instrumental before first span (t = 1.0s -> frame 10)
        assertEquals(0.02f, curve[10])
        // Inside first sung span (t = 3.0s -> frame 30)
        assertEquals(0.85f, curve[30])
        // Instrumental between spans (t = 6.0s -> frame 60)
        assertEquals(0.02f, curve[60])
        // Inside second sung span (t = 9.0s -> frame 90)
        assertEquals(0.85f, curve[90])
    }

    @Test
    fun `harmonicPitchShift leaves Camelot-compatible keys untouched`() {
        val cMaj = TrackAnalysis(key = "C major", keyConfidence = 0.8)
        val aMin = TrackAnalysis(key = "A minor", keyConfidence = 0.8) // relative minor of C major
        val gMaj = TrackAnalysis(key = "G major", keyConfidence = 0.8) // fifth
        val fMaj = TrackAnalysis(key = "F major", keyConfidence = 0.8) // fourth

        assertEquals(0f, LoopMix.harmonicPitchShift(cMaj, cMaj))
        assertEquals(0f, LoopMix.harmonicPitchShift(cMaj, aMin))
        assertEquals(0f, LoopMix.harmonicPitchShift(cMaj, gMaj))
        assertEquals(0f, LoopMix.harmonicPitchShift(cMaj, fMaj))
    }

    @Test
    fun `harmonicPitchShift rescues minor-second and tritone clashes by +-1 semitone`() {
        val cMaj = TrackAnalysis(key = "C major", keyConfidence = 0.8)
        val cSharpMaj = TrackAnalysis(key = "C# major", keyConfidence = 0.8) // +1 st -> shift -1
        val bMaj = TrackAnalysis(key = "B major", keyConfidence = 0.8)       // -1 st -> shift +1
        val fSharpMaj = TrackAnalysis(key = "F# major", keyConfidence = 0.8) // tritone -> shift -1 (to F)
        val bFlatMin = TrackAnalysis(key = "Bb minor", keyConfidence = 0.8)  // rel major Db (+1 st) -> shift -1

        assertEquals(-1f, LoopMix.harmonicPitchShift(cMaj, cSharpMaj))
        assertEquals(+1f, LoopMix.harmonicPitchShift(cMaj, bMaj))
        assertEquals(-1f, LoopMix.harmonicPitchShift(cMaj, fSharpMaj))
        assertEquals(-1f, LoopMix.harmonicPitchShift(cMaj, bFlatMin))
    }

    @Test
    fun `harmonicPitchShift returns zero when key confidence is too low`() {
        val trusted = TrackAnalysis(key = "C major", keyConfidence = 0.8)
        val uncertain = TrackAnalysis(key = "C# major", keyConfidence = 0.1)
        assertEquals(0f, LoopMix.harmonicPitchShift(trusted, uncertain))
        assertEquals(0f, LoopMix.harmonicPitchShift(uncertain, trusted))
    }
}
