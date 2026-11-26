"""
Prepares `.npz` training shards for Swell Stage 3 models (`SwellSvadNet` and `SwellMultiTaskNet`).

Supports two clean data pipelines:
  1. `--musdb-root /path/to/musdb18hq`
     Reads ground-truth `mixture.wav` and `vocals.wav` stems from MUSDB18-HQ to produce
     exact 50 fps frame-level vocal activity targets (`vocal` in [0, 1]) alongside
     Swell's exact C++-parity Log-Mel spectrogram.

  2. `--audio-dir /path/to/songs` (Knowledge Distillation — works on any music folder!)
     Generates soft/hard pseudo-labels offline on Colab/Kaggle GPU using strong teachers:
       - Beat/Downbeat teacher: `beat_this` (`final0`, MIT)
       - Vocal teacher: `demucs` (`htdemucs`) or `openunmix` (`umxhq`) run offline ONLY to
         label training spectrograms; the trained student weights are 100% original.

Each output `.npz` shard contains:
  - `mel`:       float16[T, 128]   (cast to float32 in DataLoader to save 2x disk)
  - `vocal`:     float32[T]        (present if vocal labels available)
  - `beat`:      float32[T]        (present if beat labels available)
  - `downbeat`:  float32[T]        (present if downbeat labels available)
"""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Optional

import numpy as np

from frontend import FRAME_RATE, HOP_LENGTH, SAMPLE_RATE, compute_log_mel, load_mono_22k

AUDIO_EXTS = {".wav", ".flac", ".mp3", ".ogg", ".opus", ".m4a"}


def frame_rms(samples: np.ndarray, hop: int = HOP_LENGTH, win: int = 1024) -> np.ndarray:
    """Computes frame-aligned RMS at 50 fps matching `compute_log_mel` frame count."""
    pad = win // 2
    padded = np.pad(samples.astype(np.float32), (pad, pad), mode="reflect")
    frames = (padded.size - win) // hop + 1
    stride = padded.strides[0]
    framed = np.lib.stride_tricks.as_strided(padded, shape=(frames, win), strides=(stride * hop, stride))
    return np.sqrt(np.mean(framed * framed, axis=1) + 1e-12).astype(np.float32)


def vocal_labels_from_stem(
    vocal_pcm: np.ndarray,
    mix_pcm: np.ndarray,
    abs_db_floor: float = -42.0,
    ratio_threshold: float = 0.08,
) -> np.ndarray:
    """
    Converts a clean vocal stem + mixture into continuous soft vocal presence targets in [0, 1]
    at 50 fps. Combines absolute vocal loudness (ignoring silent noise floor) with vocal-to-mix
    ratio so soft verses are captured cleanly without flagging stem bleed as singing.
    """
    v_rms = frame_rms(vocal_pcm)
    m_rms = frame_rms(mix_pcm)
    n = min(len(v_rms), len(m_rms))
    v_rms, m_rms = v_rms[:n], m_rms[:n]

    v_db = 20.0 * np.log10(v_rms + 1e-8)
    ratio = v_rms / (m_rms + 1e-6)
    # Smooth logistic transition around thresholds -> soft distillation target in [0, 1]
    loud_gate = 1.0 / (1.0 + np.exp(-(v_db - abs_db_floor) / 4.0))
    ratio_gate = 1.0 / (1.0 + np.exp(-(ratio - ratio_threshold) / 0.04))
    return (loud_gate * ratio_gate).astype(np.float32)


def build_from_musdb(musdb_root: Path, out_dir: Path, beat_teacher: Optional[object] = None) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    tracks = sorted(p for p in musdb_root.rglob("mixture.wav") if (p.parent / "vocals.wav").is_file())
    print(f"Found {len(tracks)} MUSDB18-HQ tracks under {musdb_root}")

    for idx, mix_path in enumerate(tracks, 1):
        track_dir = mix_path.parent
        split = track_dir.parent.name if track_dir.parent.name in ("train", "test") else "train"
        split_dir = out_dir / split
        split_dir.mkdir(parents=True, exist_ok=True)
        out_file = split_dir / f"{track_dir.name}.npz"
        if out_file.exists():
            continue

        mix_pcm, _ = load_mono_22k(mix_path)
        vox_pcm, _ = load_mono_22k(track_dir / "vocals.wav")
        mel = compute_log_mel(mix_pcm, SAMPLE_RATE)
        vocal = vocal_labels_from_stem(vox_pcm, mix_pcm)
        t = min(mel.shape[0], vocal.shape[0])
        payload = {"mel": mel[:t].astype(np.float16), "vocal": vocal[:t]}

        if beat_teacher is not None:
            b_probs, db_probs = beat_teacher(mix_pcm)
            t2 = min(t, len(b_probs), len(db_probs))
            payload["mel"] = payload["mel"][:t2]
            payload["vocal"] = payload["vocal"][:t2]
            payload["beat"] = b_probs[:t2]
            payload["downbeat"] = db_probs[:t2]

        np.savez_compressed(out_file, **payload)
        print(f"[{idx}/{len(tracks)}] Saved {out_file} ({payload['mel'].shape[0] / FRAME_RATE:.1f}s)")


def load_beat_teacher(device: str):
    """Loads official Beat This! final0 as an offline beat/downbeat teacher."""
    import torch
    from beat_this.inference import Audio2Frames

    teacher = Audio2Frames(checkpoint_path="final0", device=device, float16=(device == "cuda"))

    def infer(pcm_22k: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
        b_logits, db_logits = teacher(pcm_22k, SAMPLE_RATE)
        b_prob = torch.sigmoid(b_logits).cpu().numpy().astype(np.float32)
        db_prob = torch.sigmoid(db_logits).cpu().numpy().astype(np.float32)
        return (b_prob > 0.5).astype(np.float32), (db_prob > 0.5).astype(np.float32)

    return infer


def load_demucs_vocal_teacher(device: str):
    """Loads Demucs htdemucs offline ONLY to generate vocal stems for student distillation."""
    import torch
    from demucs.pretrained import get_model
    from demucs.apply import apply_model
    import torchaudio

    model = get_model("htdemucs").to(device).eval()
    vocal_idx = model.sources.index("vocals")

    def separate_vocal_22k(audio_path: Path) -> np.ndarray:
        wav, sr = torchaudio.load(str(audio_path))
        if wav.shape[0] == 1:
            wav = wav.repeat(2, 1)
        elif wav.shape[0] > 2:
            wav = wav[:2]
        if sr != model.samplerate:
            wav = torchaudio.functional.resample(wav, sr, model.samplerate)
        with torch.no_grad():
            stems = apply_model(model, wav.unsqueeze(0).to(device), split=True, overlap=0.25)[0]
        vocals_mono = stems[vocal_idx].mean(dim=0).cpu()
        vocals_22k = torchaudio.functional.resample(vocals_mono, model.samplerate, SAMPLE_RATE)
        return vocals_22k.numpy().astype(np.float32)

    return separate_vocal_22k


def build_from_audio_dir(
    audio_dir: Path,
    out_dir: Path,
    device: str,
    use_beat_teacher: bool,
    use_demucs_teacher: bool,
) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    files = sorted(p for p in audio_dir.rglob("*") if p.suffix.lower() in AUDIO_EXTS)
    print(f"Found {len(files)} audio files under {audio_dir}")

    beat_teacher = load_beat_teacher(device) if use_beat_teacher else None
    vocal_teacher = load_demucs_vocal_teacher(device) if use_demucs_teacher else None

    for idx, path in enumerate(files, 1):
        split = "val" if idx % 10 == 0 else "train"
        split_dir = out_dir / split
        split_dir.mkdir(parents=True, exist_ok=True)
        out_file = split_dir / f"{path.stem}.npz"
        if out_file.exists():
            continue

        mix_pcm, _ = load_mono_22k(path)
        mel = compute_log_mel(mix_pcm, SAMPLE_RATE)
        t = mel.shape[0]
        payload = {"mel": mel.astype(np.float16)}

        if vocal_teacher is not None:
            vox_pcm = vocal_teacher(path)
            vocal = vocal_labels_from_stem(vox_pcm, mix_pcm)
            t = min(t, len(vocal))
            payload["vocal"] = vocal[:t]

        if beat_teacher is not None:
            b, db = beat_teacher(mix_pcm)
            t = min(t, len(b), len(db))
            payload["beat"] = b[:t]
            payload["downbeat"] = db[:t]

        payload["mel"] = payload["mel"][:t]
        for k in ("vocal", "beat", "downbeat"):
            if k in payload:
                payload[k] = payload[k][:t]

        np.savez_compressed(out_file, **payload)
        print(f"[{idx}/{len(files)}] Distilled {out_file.name} ({t / FRAME_RATE:.1f}s)")


def main() -> None:
    p = argparse.ArgumentParser(description="Prepare Swell Stage 3 `.npz` training shards.")
    p.add_argument("--out-dir", type=Path, required=True, help="Output directory for train/val `.npz` shards.")
    p.add_argument("--musdb-root", type=Path, default=None, help="Path to MUSDB18-HQ root directory.")
    p.add_argument("--audio-dir", type=Path, default=None, help="Path to unlabeled audio folder for distillation.")
    p.add_argument("--beat-teacher", action="store_true", help="Attach Beat This! final0 beat/downbeat labels.")
    p.add_argument("--demucs-teacher", action="store_true", help="Use Demucs htdemucs offline for vocal labels.")
    p.add_argument("--device", default="cuda", help="cuda or cpu")
    args = p.parse_args()

    if args.musdb_root:
        beat_t = load_beat_teacher(args.device) if args.beat_teacher else None
        build_from_musdb(args.musdb_root, args.out_dir, beat_teacher=beat_t)
    elif args.audio_dir:
        build_from_audio_dir(
            args.audio_dir,
            args.out_dir,
            device=args.device,
            use_beat_teacher=args.beat_teacher,
            use_demucs_teacher=args.demucs_teacher,
        )
    else:
        p.error("Provide either --musdb-root or --audio-dir.")


if __name__ == "__main__":
    main()
