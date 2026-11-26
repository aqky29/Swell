"""
GPU/AMP training + ONNX export + per-channel INT8 MatMul quantization for Swell Stage 3 models.

Usage examples (Google Colab / Kaggle GPU):

  # 1. Smoke test (runs end-to-end on CPU/GPU in ~10s, verifies ONNX + ORT parity):
  python tools/ml/train_and_export.py --mode svad --smoke-test --out-dir /tmp/svad_smoke
  python tools/ml/train_and_export.py --mode multitask --smoke-test --out-dir /tmp/mt_smoke

  # 2. Train ultra-light SVAD (~0.5 MB INT8) on MUSDB18-HQ / distilled shards:
  python tools/ml/train_and_export.py \
      --mode svad \
      --data-dir /content/shards \
      --out-dir /content/out_svad \
      --epochs 40 --batch-size 16 --lr 1.5e-3

  # 3. Train unified Multi-Task model (~2.2 MB INT8: Beat + Downbeat + Vocal + Structure):
  python tools/ml/train_and_export.py \
      --mode multitask \
      --data-dir /content/shards \
      --out-dir /content/out_multitask \
      --epochs 60 --batch-size 8 --lr 1e-3
"""

from __future__ import annotations

import argparse
import os
import random
import time
from pathlib import Path
from typing import Dict, List

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset

from frontend import N_MELS
from model import NUM_SECTIONS, SwellMultiTaskNet, SwellSvadNet, multitask_loss


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


def spec_augment(mel: torch.Tensor, max_freq_mask: int = 16, max_time_mask: int = 60) -> torch.Tensor:
    """In-place friendly SpecAugment (freq & time masking + mild loudness jitter)."""
    x = mel.clone()
    t, f = x.shape
    # Gain jitter in log1p space (mild brightness shift)
    x = torch.clamp(x + random.uniform(-0.15, 0.15), min=0.0)
    # 2 frequency masks
    for _ in range(2):
        w = random.randint(0, min(max_freq_mask, f // 4))
        if w > 0:
            f0 = random.randint(0, f - w)
            x[:, f0 : f0 + w] = 0.0
    # 2 time masks
    for _ in range(2):
        w = random.randint(0, min(max_time_mask, t // 6))
        if w > 0:
            t0 = random.randint(0, t - w)
            x[t0 : t0 + w, :] = 0.0
    return x


class ShardDataset(Dataset):
    """Randomly crops `chunk_frames` (default 1500 = 30s @ 50 fps) from `.npz` track shards."""

    def __init__(self, shard_paths: List[Path], chunk_frames: int = 1500, augment: bool = True):
        self.paths = shard_paths
        self.chunk_frames = chunk_frames
        self.augment = augment

    def __len__(self) -> int:
        return len(self.paths)

    def __getitem__(self, index: int) -> Dict[str, torch.Tensor]:
        data = np.load(self.paths[index])
        mel = data["mel"].astype(np.float32)  # [T, 128]
        total = mel.shape[0]
        c = self.chunk_frames

        if total >= c:
            start = random.randint(0, total - c) if self.augment else 0
            end = start + c
            pad_after = 0
        else:
            start, end = 0, total
            pad_after = c - total

        def slice_1d(key: str, fill: float = 0.0, dtype=np.float32) -> tuple[torch.Tensor, bool]:
            if key not in data:
                return torch.full((c,), fill, dtype=torch.float32 if dtype == np.float32 else torch.long), False
            arr = data[key][start:end].astype(dtype)
            if pad_after > 0:
                arr = np.pad(arr, (0, pad_after), constant_values=fill)
            return torch.from_numpy(arr), True

        mel_crop = mel[start:end]
        if pad_after > 0:
            mel_crop = np.pad(mel_crop, ((0, pad_after), (0, 0)), constant_values=0.0)
        mel_t = torch.from_numpy(mel_crop)
        if self.augment:
            mel_t = spec_augment(mel_t)

        vocal, has_vocal = slice_1d("vocal", 0.0, np.float32)
        beat, has_beat = slice_1d("beat", 0.0, np.float32)
        downbeat, has_db = slice_1d("downbeat", 0.0, np.float32)
        boundary, has_bnd = slice_1d("boundary", 0.0, np.float32)
        section, has_sec = slice_1d("section", -100, np.int64)

        return {
            "mel": mel_t,
            "vocal": vocal,
            "has_vocal": torch.tensor(has_vocal, dtype=torch.bool),
            "beat": beat,
            "has_beat": torch.tensor(has_beat, dtype=torch.bool),
            "downbeat": downbeat,
            "has_downbeat": torch.tensor(has_db, dtype=torch.bool),
            "boundary": boundary,
            "has_boundary": torch.tensor(has_bnd, dtype=torch.bool),
            "section": section,
            "has_section": torch.tensor(has_sec, dtype=torch.bool),
        }


class SyntheticSmokeDataset(Dataset):
    """Minimal synthetic dataset for fast CPU/GPU end-to-end smoke verification."""

    def __init__(self, count: int = 8, chunk_frames: int = 250):
        self.count = count
        self.chunk_frames = chunk_frames

    def __len__(self) -> int:
        return self.count

    def __getitem__(self, index: int) -> Dict[str, torch.Tensor]:
        rng = np.random.default_rng(index + 1)
        t = self.chunk_frames
        mel = rng.uniform(0.0, 2.0, size=(t, N_MELS)).astype(np.float32)
        beat = np.zeros(t, dtype=np.float32)
        downbeat = np.zeros(t, dtype=np.float32)
        vocal = np.zeros(t, dtype=np.float32)
        boundary = np.zeros(t, dtype=np.float32)
        section = np.zeros(t, dtype=np.int64)

        beat[::25] = 1.0
        downbeat[::100] = 1.0
        vocal[50:180] = 1.0
        boundary[125] = 1.0
        section[125:] = 2
        mel[50:180, 25:75] += 2.0
        mel[::25, 2:16] += 2.5

        return {
            "mel": torch.from_numpy(mel),
            "vocal": torch.from_numpy(vocal),
            "has_vocal": torch.tensor(True),
            "beat": torch.from_numpy(beat),
            "has_beat": torch.tensor(True),
            "downbeat": torch.from_numpy(downbeat),
            "has_downbeat": torch.tensor(True),
            "boundary": torch.from_numpy(boundary),
            "has_boundary": torch.tensor(True),
            "section": torch.from_numpy(section),
            "has_section": torch.tensor(True),
        }


def compute_vocal_f1(logits: torch.Tensor, targets: torch.Tensor, threshold: float = 0.5) -> float:
    preds = (torch.sigmoid(logits) >= threshold).float()
    truth = (targets >= 0.5).float()
    tp = (preds * truth).sum().item()
    fp = (preds * (1.0 - truth)).sum().item()
    fn = ((1.0 - preds) * truth).sum().item()
    return (2.0 * tp) / max(2.0 * tp + fp + fn, 1e-6)


def export_and_quantize(
    model: nn.Module,
    mode: str,
    out_dir: Path,
    verify_frames: int = 750,
) -> tuple[Path, Path]:
    """Exports dynamic-length ONNX opset 17 + per-channel INT8 MatMul quantized model & verifies in ORT."""
    import onnx
    import onnxruntime as ort
    from onnxruntime.quantization import QuantType, quantize_dynamic
    from onnxruntime.quantization.shape_inference import quant_pre_process

    model_cpu = model.cpu().eval()
    base = "swell_svad" if mode == "svad" else "swell_multitask"
    raw_path = out_dir / f"{base}_raw.onnx"
    fp32_path = out_dir / f"{base}_fp32.onnx"
    q8_path = out_dir / f"{base}_q8.onnx"

    dummy = torch.randn(1, 300, N_MELS, dtype=torch.float32)
    if mode == "svad":
        output_names = ["vocal"]
        dynamic_axes = {"input_spectrogram": {1: "time"}, "vocal": {1: "time"}}
    else:
        output_names = ["beat", "downbeat", "vocal", "boundary", "section"]
        dynamic_axes = {
            "input_spectrogram": {1: "time"},
            "beat": {1: "time"},
            "downbeat": {1: "time"},
            "vocal": {1: "time"},
            "boundary": {1: "time"},
            "section": {1: "time"},
        }

    with torch.no_grad():
        torch.onnx.export(
            model_cpu,
            (dummy,),
            str(raw_path),
            input_names=["input_spectrogram"],
            output_names=output_names,
            dynamic_axes=dynamic_axes,
            opset_version=17,
            do_constant_folding=True,
            dynamo=False,
        )

    onnx.checker.check_model(str(raw_path))
    quant_pre_process(
        input_model_path=str(raw_path),
        output_model_path=str(fp32_path),
        skip_optimization=False,
        skip_onnx_shape=False,
        skip_symbolic_shape=True,
        auto_merge=True,
    )
    quantize_dynamic(
        model_input=str(fp32_path),
        model_output=str(q8_path),
        op_types_to_quantize=["MatMul"],
        per_channel=True,
        weight_type=QuantType.QInt8,
    )
    # Strip redundant value_info metadata to keep APK size minimal
    m = onnx.load(str(q8_path))
    del m.graph.value_info[:]
    onnx.save(m, str(q8_path))
    raw_path.unlink(missing_ok=True)

    # Verify numerical parity and benchmark CPU latency in ONNX Runtime
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = 2
    opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL

    s_fp = ort.InferenceSession(str(fp32_path), opts, providers=["CPUExecutionProvider"])
    s_q8 = ort.InferenceSession(str(q8_path), opts, providers=["CPUExecutionProvider"])

    test_x = np.random.default_rng(0).standard_normal((1, verify_frames, N_MELS)).astype(np.float32)
    y_fp = s_fp.run(None, {"input_spectrogram": test_x})
    s_q8.run(None, {"input_spectrogram": test_x})  # warmup
    t0 = time.time()
    y_q8 = s_q8.run(None, {"input_spectrogram": test_x})
    dt_ms = (time.time() - t0) * 1000.0

    corrs = [float(np.corrcoef(a.ravel(), b.ravel())[0, 1]) for a, b in zip(y_fp, y_q8)]
    print(f"\n=== Export Verified ({mode}) ===")
    print(f"FP32 : {fp32_path} ({os.path.getsize(fp32_path) / 1e6:.2f} MB)")
    print(f"Q8   : {q8_path} ({os.path.getsize(q8_path) / 1e6:.2f} MB)")
    print(f"Bench (1, {verify_frames}, 128) = {dt_ms:.1f} ms | Q8/FP32 corrs = {[round(c, 5) for c in corrs]}")
    return fp32_path, q8_path


def main() -> None:
    p = argparse.ArgumentParser(description="Train and export Swell Stage 3 ONNX models.")
    p.add_argument("--mode", choices=["svad", "multitask"], required=True)
    p.add_argument("--data-dir", type=Path, default=None, help="Directory containing train/ and val/ .npz shards.")
    p.add_argument("--out-dir", type=Path, required=True)
    p.add_argument("--epochs", type=int, default=40)
    p.add_argument("--batch-size", type=int, default=16)
    p.add_argument("--chunk-frames", type=int, default=1500)
    p.add_argument("--lr", type=float, default=1.5e-3)
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--smoke-test", action="store_true", help="Run 2-epoch synthetic smoke test + ONNX export.")
    args = p.parse_args()

    set_seed(args.seed)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    if args.smoke_test:
        train_ds: Dataset = SyntheticSmokeDataset(count=4, chunk_frames=250)
        val_ds: Dataset = SyntheticSmokeDataset(count=2, chunk_frames=250)
        epochs = 2
        batch_size = 2
        verify_frames = 300
    else:
        if args.data_dir is None:
            p.error("--data-dir is required unless --smoke-test is set.")
        train_paths = sorted((args.data_dir / "train").glob("*.npz"))
        val_paths = sorted((args.data_dir / "val").glob("*.npz")) or sorted((args.data_dir / "test").glob("*.npz"))
        if not train_paths:
            raise FileNotFoundError(f"No .npz shards found in {args.data_dir / 'train'}")
        if not val_paths:
            split = max(1, len(train_paths) // 10)
            val_paths, train_paths = train_paths[:split], train_paths[split:]
        train_ds = ShardDataset(train_paths, chunk_frames=args.chunk_frames, augment=True)
        val_ds = ShardDataset(val_paths, chunk_frames=args.chunk_frames, augment=False)
        epochs = args.epochs
        batch_size = args.batch_size
        verify_frames = 750

    train_loader = DataLoader(train_ds, batch_size=batch_size, shuffle=True, num_workers=0, drop_last=False)
    val_loader = DataLoader(val_ds, batch_size=batch_size, shuffle=False, num_workers=0)

    model: nn.Module = SwellSvadNet() if args.mode == "svad" else SwellMultiTaskNet()
    model = model.to(device)
    params = sum(par.numel() for par in model.parameters())
    print(f"Mode={args.mode} | Device={device} | Params={params / 1e6:.2f}M")

    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-2)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=max(1, epochs))
    use_amp = device.type == "cuda"
    scaler = torch.amp.GradScaler("cuda", enabled=use_amp)

    best_val = float("inf")
    best_ckpt = args.out_dir / f"best_{args.mode}.pt"

    for epoch in range(1, epochs + 1):
        model.train()
        train_loss = 0.0
        steps = 0
        for batch in train_loader:
            batch = {k: v.to(device) for k, v in batch.items()}
            optimizer.zero_grad(set_to_none=True)
            with torch.amp.autocast(device_type=device.type, enabled=use_amp):
                if args.mode == "svad":
                    logits = model(batch["mel"])
                    loss = F.binary_cross_entropy_with_logits(logits, batch["vocal"])
                else:
                    outs = model(batch["mel"])
                    loss, _ = multitask_loss(outs, batch)
            scaler.scale(loss).backward()
            scaler.unscale_(optimizer)
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            scaler.step(optimizer)
            scaler.update()
            train_loss += float(loss.detach())
            steps += 1
        scheduler.step()

        # Validation
        model.eval()
        val_loss = 0.0
        val_f1s = []
        vsteps = 0
        with torch.no_grad():
            for batch in val_loader:
                batch = {k: v.to(device) for k, v in batch.items()}
                if args.mode == "svad":
                    logits = model(batch["mel"])
                    loss = F.binary_cross_entropy_with_logits(logits, batch["vocal"])
                    val_f1s.append(compute_vocal_f1(logits, batch["vocal"]))
                else:
                    outs = model(batch["mel"])
                    loss, _ = multitask_loss(outs, batch)
                    if batch["has_vocal"].any():
                        val_f1s.append(compute_vocal_f1(outs[2][batch["has_vocal"]], batch["vocal"][batch["has_vocal"]]))
                val_loss += float(loss)
                vsteps += 1

        avg_train = train_loss / max(1, steps)
        avg_val = val_loss / max(1, vsteps)
        avg_f1 = float(np.mean(val_f1s)) if val_f1s else 0.0
        print(f"Epoch {epoch:03d}/{epochs:03d} | train={avg_train:.4f} | val={avg_val:.4f} | vocal_f1={avg_f1:.3f}")

        if avg_val <= best_val:
            best_val = avg_val
            torch.save(model.state_dict(), best_ckpt)

    model.load_state_dict(torch.load(best_ckpt, map_location="cpu", weights_only=True))
    export_and_quantize(model, args.mode, args.out_dir, verify_frames=verify_frames)


if __name__ == "__main__":
    main()
