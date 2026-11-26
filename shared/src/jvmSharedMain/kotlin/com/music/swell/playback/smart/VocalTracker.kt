/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard).
 *
 * Copyright (C) 2026 SFG545 (original Orchard implementation)
 * Copyright (C) 2026 Kushagra Singh (BitChord adaptation)
 *
 * Orchard's original source is licensed under the GNU Affero General Public
 * License, version 3 or later. Per AGPLv3 section 13, this file is combined
 * here into BitChord -- a work licensed under the GNU General Public
 * License, version 3 or later -- and remains itself governed by the AGPLv3
 * as part of that combination.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.music.swell.playback.smart

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.floor

/** The linear-frequency STFT front end open-unmix was trained on. */
object VocalSpectrogram {
    val available: Boolean get() = MelSpectrogram.available
    val bins: Int by lazy { if (available) nativeBins() else 2049 }
    val sampleRate: Double by lazy { if (available) nativeSampleRate() else 44_100.0 }
    val hop: Int by lazy { if (available) nativeHop() else 1024 }
    val fftSize: Int by lazy { if (available) nativeFftSize() else 4096 }
    val frameRate: Double get() = sampleRate / hop

    /**
     * Computes the magnitude STFT for planar stereo at [sampleRate].
     *
     * Returns null when the library is missing, the rate is wrong, or the input is shorter than one
     * padded frame, all of which mean "no mask available" rather than an error.
     */
    fun compute(left: FloatArray, right: FloatArray, rate: Double = sampleRate): Spectrogram? {
        if (!available || left.isEmpty() || left.size != right.size) return null
        val values = nativeCompute(left, right, rate)
        if (values.isEmpty()) return null
        return Spectrogram(values, frames = values.size / (CHANNELS * bins), bins = bins)
    }

    /**
     * Bin-major, flattened: channel c, bin b, frame f is at `(c * bins + b) * frames + f`. That
     * ordering is not the natural one for an STFT computed a frame at a time; it is chosen to
     * match the model's `[1, 2, bins, frames]` tensor exactly, so nothing has to transpose.
     */
    data class Spectrogram(val values: FloatArray, val frames: Int, val bins: Int) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Spectrogram && frames == other.frames &&
                bins == other.bins && values.contentEquals(other.values))

        override fun hashCode(): Int = 31 * (31 * values.contentHashCode() + frames) + bins
    }

    const val CHANNELS = 2

    @JvmStatic private external fun nativeCompute(left: FloatArray, right: FloatArray, rate: Double): FloatArray
    @JvmStatic private external fun nativeBins(): Int
    @JvmStatic private external fun nativeSampleRate(): Double
    @JvmStatic private external fun nativeHop(): Int
    @JvmStatic private external fun nativeFftSize(): Int
}

/**
 * Vocal-presence tracking with open-unmix's "vocals" target (Stöter & Liutkus, Inria/SigSep).
 *
 * The transition policy uses this to avoid mixing two vocals over each other: a blend where both
 * tracks are singing is the one case that reliably sounds wrong however well the beats line up.
 *
 * Chosen because its **weights** are MIT, confirmed on the Zenodo deposit rather than inferred from
 * the code repository. Meta's htdemucs separates better but releases its pretrained weights under
 * CC-BY-NC-4.0, which a distributed application cannot ship, and its ONNX export additionally has
 * unresolved blockers around complex-valued STFT ops.
 *
 * Only the vocals target is used. open-unmix trains four independent checkpoints; BitChord needs to
 * know how much vocal content is present at an instant, not to reconstruct four stems.
 */
class VocalTracker(
    /**
     * Where the model lives, resolved lazily.
     *
     * A path rather than a [Context]: this is the only thing the tracker ever
     * wanted the platform for, and each application already knows how to put
     * its own copy of the weights somewhere readable — unpacked from assets on
     * Android, from the jar's resources on the desktop.
     */
    private val modelPath: () -> String,
    /** How many threads inference may use; the vocal-activity pass is the expensive one. */
    private val inferenceThreads: () -> Int,
) {

    @Volatile private var session: OrtSession? = null
    @Volatile private var sessionThreads = 0
    private val lock = Any()

    private fun session(): OrtSession? {
        val threads = inferenceThreads()
        session?.takeIf { sessionThreads == threads }?.let { return it }
        synchronized(lock) {
            session?.takeIf { sessionThreads == threads }?.let { return it }
            runCatching { session?.close() }
            session = null
            return runCatching {
                val file = File(modelPath())
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(threads)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    // Same reasoning as BeatTracker: the arena retains every block it allocates for
                    // the life of the session, which a backgrounded music player cannot justify.
                    setCPUArenaAllocator(false)
                    setMemoryPatternOptimization(false)
                }
                OrtEnvironment.getEnvironment().createSession(file.absolutePath, options)
                    .also {
                        session = it
                        sessionThreads = threads
                    }
            }.onFailure { AnalysisLog.warn("Vocal model unavailable; no mask will be produced", it) }
                .getOrNull()
        }
    }

    /**
     * Returns one vocal-presence value per STFT frame in [0, 1], or null when unavailable.
     *
     * The patched dynamic-length graph (`[1, 2, bins, time]`) accepts any frame count up to
     * [MAX_FRAMES] (~32.5 s), so short windows allocate and compute only their real length rather
     * than zero-padding out to a fixed 960-frame tensor.
     */
    fun track(left: FloatArray, right: FloatArray, rate: Double): FloatArray? {
        if (!VocalSpectrogram.available) return null
        val resampledLeft = MelSpectrogram.resample(left, rate, VocalSpectrogram.sampleRate) ?: return null
        val resampledRight = MelSpectrogram.resample(right, rate, VocalSpectrogram.sampleRate) ?: return null

        val started = System.currentTimeMillis()
        val spectrogram = VocalSpectrogram.compute(resampledLeft, resampledRight) ?: return null
        val frames = spectrogram.frames
        if (frames <= 0 || frames > MAX_FRAMES) {
            AnalysisLog.warn("Window of $frames frames outside supported range 1..$MAX_FRAMES")
            return null
        }
        val active = session() ?: return null

        return runCatching {
            val bins = spectrogram.bins
            // Direct, sized to the actual window length rather than a fixed 960 frames: keeps the
            // mix tensor off the Java heap and cuts both native RAM and LSTM inference cost
            // proportionally on shorter windows.
            val backing = ByteBuffer
                .allocateDirect(VocalSpectrogram.CHANNELS * bins * frames * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            backing.asFloatBuffer().put(spectrogram.values)
            val environment = OrtEnvironment.getEnvironment()
            val shape = longArrayOf(1, VocalSpectrogram.CHANNELS.toLong(), bins.toLong(), frames.toLong())

            // A fresh view per reader rather than rewinding one: FloatBuffer's own
            // rewind() and position(int) are Java 9 covariant overrides that
            // Android's FloatBuffer does not declare, so they compile against the
            // current SDK and throw NoSuchMethodError on the API 28 devices this
            // has to run on. asFloatBuffer() hands back a view at position 0 and
            // has been there since API 1.
            OnnxTensor.createTensor(environment, backing.asFloatBuffer(), shape).use { tensor ->
                active.run(mapOf(active.inputNames.first() to tensor)).use { outputs ->
                    // The output tensor's buffer, not `outputs.get(0).value`: that
                    // property boxes this [1, 2, bins, frames] result into
                    // one FloatArray per bin per channel when the only thing read
                    // from it is a band average.
                    val target = (outputs.get(0) as OnnxTensor).floatBuffer
                    val raw = reduceToBandCurve(backing.asFloatBuffer(), target, bins, frames)
                    val curve = smoothVocalCurve(raw)
                    AnalysisLog.warn("vocal mask $frames frames in " +
                            "${System.currentTimeMillis() - started}ms",
                    )
                    curve
                }
            }
        }.onFailure { AnalysisLog.warn("Vocal inference failed", it) }.getOrNull()
    }

    fun release() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
            sessionThreads = 0
        }
    }

    companion object {
        private const val TAG = "BitChordVocalTracker"

        /**
         * Dynamic-length Open-Unmix vocals model (`[1, 2, 2049, time]`), patched from the fixed-960
         * export with identical weights. A new name ensures existing installs unpack the dynamic
         * graph instead of reusing the fixed-length copy in `filesDir`.
         */
        const val MODEL_ASSET = "vocals_umxhq_dyn_int8.onnx"

        /** Retired fixed-length unpacked model deleted from app storage on first use. */
        const val RETIRED_MODEL_ASSET = "vocals_umxhq_int8.onnx"

        /** Upper bound on frames per single pass (~32.5 s) to keep DirectByteBuffer RAM bounded. */
        const val MAX_FRAMES = 1400

        /** Legacy fixed frame count kept for reference. */
        const val FIXED_FRAMES = 960

        /** The band a vocal actually occupies; below and above it the mask says little. */
        const val LOW_HZ = 200.0
        const val HIGH_HZ = 4000.0

        /**
         * Computes the magnitude-weighted vocal share across [LOW_HZ]..[HIGH_HZ] and both channels:
         * `sum(min(target, mix)) / sum(mix)`.
         *
         * Weighting by bin magnitude prevents quiet noise bins from inflating the score on pure
         * instrumentals while keeping the output naturally calibrated on `[0, 1]`.
         */
        fun reduceToBandCurve(
            mix: FloatBuffer,
            target: FloatBuffer,
            bins: Int,
            frames: Int,
            fftSize: Int = VocalSpectrogram.fftSize,
            sampleRate: Double = VocalSpectrogram.sampleRate,
        ): FloatArray {
            val lowBin = floor(LOW_HZ * fftSize / sampleRate)
                .toInt().coerceAtLeast(0)
            val highBin = ceil(HIGH_HZ * fftSize / sampleRate)
                .toInt().coerceAtMost(bins - 1)
            if (highBin <= lowBin || frames <= 0) return FloatArray(0)

            val curve = FloatArray(frames)
            for (frame in 0 until frames) {
                var targetSum = 0.0
                var mixSum = 0.0
                for (channel in 0 until VocalSpectrogram.CHANNELS) {
                    for (bin in lowBin..highBin) {
                        // Both buffers carry the model's [1, 2, bins, frames]
                        // layout, so one index reads the same cell of each. Absolute
                        // get, so neither view's position matters.
                        val index = (channel * bins + bin) * frames + frame
                        val mixValue = mix.get(index).toDouble()
                        if (mixValue <= 1e-6) continue
                        val targetValue = target.get(index).toDouble().coerceIn(0.0, mixValue)
                        targetSum += targetValue
                        mixSum += mixValue
                    }
                }
                curve[frame] = if (mixSum > 1e-6) (targetSum / mixSum).toFloat().coerceIn(0f, 1f) else 0f
            }
            return curve
        }

        /**
         * 3-frame (~70 ms) median filter that removes isolated single-frame percussive spikes
         * (snare/clap transients) without smearing vocal onsets or offsets.
         */
        fun smoothVocalCurve(raw: FloatArray): FloatArray {
            if (raw.size < 3) return raw.copyOf()
            val out = FloatArray(raw.size)
            out[0] = raw[0]
            out[raw.lastIndex] = raw[raw.lastIndex]
            for (i in 1 until raw.lastIndex) {
                val a = raw[i - 1]
                val b = raw[i]
                val c = raw[i + 1]
                out[i] = maxOf(minOf(a, b), minOf(maxOf(a, b), c))
            }
            return out
        }

        /**
         * True when [spans] came from word-synced or explicitly end-timed lyrics (every span's
         * [LoopMix.Span.certainUntil] covers its full end), so no open-line tail needs the vocal
         * ONNX model to vouch for it.
         */
        fun hasExactLyrics(spans: List<LoopMix.Span>): Boolean =
            spans.size >= 4 && spans.all { it.end > it.start && it.certainUntil >= it.end - 1e-3 }

        /**
         * Synthesizes a vocal-presence curve over `[startSeconds, startSeconds + durationSeconds)`
         * from exact sung lyric [spans], matching the scale of [reduceToBandCurve] (`0.85` while
         * singing, `0.02` during instrumental stretches).
         */
        fun vocalCurveFromLyrics(
            spans: List<LoopMix.Span>,
            startSeconds: Double,
            durationSeconds: Double,
            frameRate: Double = VocalSpectrogram.frameRate,
        ): FloatArray {
            val frames = (durationSeconds * frameRate).toInt()
            if (frames <= 0 || frameRate <= 0.0) return FloatArray(0)
            val curve = FloatArray(frames) { 0.02f }
            val endSeconds = startSeconds + durationSeconds
            for (span in spans) {
                if (span.end <= startSeconds || span.start >= endSeconds) continue
                val from = floor((span.start - startSeconds) * frameRate).toInt().coerceIn(0, frames - 1)
                val to = ceil((span.end - startSeconds) * frameRate).toInt().coerceIn(from + 1, frames)
                for (f in from until to) {
                    curve[f] = 0.85f
                }
            }
            return curve
        }
    }
}
