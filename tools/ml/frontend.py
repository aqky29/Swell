"""
Exact Python mirror of Swell's C++ `ComputeBeatSpectrogram`
(`native/analyzer/mel_spectrogram.cpp`).

Every constant matches the on-device front end so models trained here see the
exact same feature distribution at Android/Desktop inference time:
  - Mono 22,050 Hz float32 PCM
  - n_fft = 1024, hop = 441 (exact 50.0 frames/sec)
  - Periodic Hann window (`N`, not `N - 1`)
  - Reflect padding of `n_fft // 2` on both ends
  - Normalized STFT magnitude (`|rfft| / sqrt(n_fft)`)
  - 128 Slaney mel bands from 30 Hz to 11,000 Hz, unit-peak triangles (norm=None)
  - `log1p(1000.0 * max(mel_energy, 1e-10))`
"""

from __future__ import annotations

import functools
import math
from pathlib import Path
from typing import Tuple

import numpy as np

SAMPLE_RATE = 22_050
N_FFT = 1024
HOP_LENGTH = 441
FRAME_RATE = SAMPLE_RATE / HOP_LENGTH  # 50.0 fps
N_MELS = 128
F_MIN = 30.0
F_MAX = 11_000.0
LOG_MULTIPLIER = 1000.0
AMPLITUDE_FLOOR = 1e-10


def _hz_to_mel(hz: np.ndarray | float) -> np.ndarray:
    hz_arr = np.asarray(hz, dtype=np.float64)
    f_sp = 200.0 / 3.0
    min_log_hz = 1000.0
    min_log_mel = min_log_hz / f_sp
    logstep = math.log(6.4) / 27.0
    return np.where(
        hz_arr >= min_log_hz,
        min_log_mel + np.log(np.maximum(hz_arr, 1e-12) / min_log_hz) / logstep,
        hz_arr / f_sp,
    )


def _mel_to_hz(mel: np.ndarray | float) -> np.ndarray:
    mel_arr = np.asarray(mel, dtype=np.float64)
    f_sp = 200.0 / 3.0
    min_log_hz = 1000.0
    min_log_mel = min_log_hz / f_sp
    logstep = math.log(6.4) / 27.0
    return np.where(
        mel_arr >= min_log_mel,
        min_log_hz * np.exp(logstep * (mel_arr - min_log_mel)),
        f_sp * mel_arr,
    )


@functools.lru_cache(maxsize=2)
def mel_filterbank(sample_rate: int = SAMPLE_RATE) -> np.ndarray:
    """Returns `[N_FFT // 2 + 1, N_MELS]` unit-peak Slaney mel matrix (float64)."""
    bins = N_FFT // 2 + 1
    mel_min = float(_hz_to_mel(F_MIN))
    mel_max = float(_hz_to_mel(F_MAX))
    edges = _mel_to_hz(np.linspace(mel_min, mel_max, N_MELS + 2))
    fft_freqs = np.arange(bins, dtype=np.float64) * (sample_rate / N_FFT)

    fb = np.zeros((bins, N_MELS), dtype=np.float64)
    for m in range(N_MELS):
        left, center, right = edges[m], edges[m + 1], edges[m + 2]
        rising = (fft_freqs - left) / (center - left) if center > left else np.zeros_like(fft_freqs)
        falling = (right - fft_freqs) / (right - center) if right > center else np.zeros_like(fft_freqs)
        fb[:, m] = np.maximum(0.0, np.minimum(rising, falling))
    return fb


@functools.lru_cache(maxsize=2)
def periodic_hann_window(size: int = N_FFT) -> np.ndarray:
    """Periodic Hann window matching `torch.hann_window(size, periodic=True)` and C++."""
    idx = np.arange(size, dtype=np.float64)
    return 0.5 - 0.5 * np.cos(2.0 * math.pi * idx / size)


def compute_log_mel(samples: np.ndarray, sample_rate: int = SAMPLE_RATE) -> np.ndarray:
    """
    Computes Swell's `[frames, 128]` float32 log-mel spectrogram from mono PCM.

    Parameters
    ----------
    samples : np.ndarray
        1D float32 mono waveform at `SAMPLE_RATE` (22,050 Hz).
    sample_rate : int
        Must match `SAMPLE_RATE` (22,050 Hz).
    """
    if abs(sample_rate - SAMPLE_RATE) > 1:
        raise ValueError(f"Expected sample_rate={SAMPLE_RATE}, got {sample_rate}")
    x = np.asarray(samples, dtype=np.float32).reshape(-1)
    pad = N_FFT // 2
    if x.size <= pad + 1:
        return np.zeros((0, N_MELS), dtype=np.float32)

    padded = np.pad(x, (pad, pad), mode="reflect")
    frames = (padded.size - N_FFT) // HOP_LENGTH + 1

    # Strided view [frames, N_FFT]
    stride = padded.strides[0]
    framed = np.lib.stride_tricks.as_strided(
        padded,
        shape=(frames, N_FFT),
        strides=(stride * HOP_LENGTH, stride),
    )
    window = periodic_hann_window(N_FFT)
    norm = math.sqrt(float(N_FFT))
    mag = np.abs(np.fft.rfft(framed.astype(np.float64) * window[None, :], axis=1)) / norm
    mel = mag @ mel_filterbank(sample_rate)
    out = np.log1p(LOG_MULTIPLIER * np.maximum(mel, AMPLITUDE_FLOOR))
    return out.astype(np.float32)


def load_mono_22k(path: str | Path) -> Tuple[np.ndarray, int]:
    """Loads any audio file (wav/flac/mp3/ogg/opus) as mono float32 at 22,050 Hz."""
    path = Path(path)
    try:
        import soundfile as sf
        import soxr

        audio, sr = sf.read(str(path), dtype="float32", always_2d=True)
        mono = audio.mean(axis=1)
        if sr != SAMPLE_RATE:
            mono = soxr.resample(mono, sr, SAMPLE_RATE, quality="HQ").astype(np.float32)
        return mono, SAMPLE_RATE
    except Exception:
        # Fallback via torchaudio / librosa if installed
        import torchaudio

        wav, sr = torchaudio.load(str(path))
        mono_t = wav.mean(dim=0)
        if sr != SAMPLE_RATE:
            mono_t = torchaudio.functional.resample(mono_t, sr, SAMPLE_RATE)
        return mono_t.numpy().astype(np.float32), SAMPLE_RATE
