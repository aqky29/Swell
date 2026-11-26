# Swell Stage 3 ML Training & Distillation Pipeline (Colab / Kaggle GPU)

This folder provides a self-contained PyTorch training, knowledge-distillation, and ONNX export pipeline for Swell's next-generation Automix models.

Both architectures consume Swell's **exact C++ `MelSpectrogram` contract** (`native/analyzer/mel_spectrogram.cpp`: 22,050 Hz mono, `n_fft=1024`, `hop=441` = 50 fps, 128 unit-peak Slaney mel bands `30..11,000 Hz`, `log1p(1000 * magnitude)`), so no second STFT or stereo buffer is needed on Android/Desktop.

## Models Included

| Mode | Architecture | Params | Exported Q8 Size | Outputs | Purpose |
|---|---|---:|---:|---|---|
| `--mode svad` | `SwellSvadNet` (2D Conv stem + Dilated TCN) | ~0.45 M | **~0.5–0.8 MB** | `vocal [1, T]` | Drop-in ultra-fast replacement for 9 MB Open-Unmix `VocalTracker` (shares `BeatTracker`'s MelSpectrogram) |
| `--mode multitask` | `SwellMultiTaskNet` (2D Conv + TCN + 4× MobileConformer) | ~1.8 M | **~2.0–2.5 MB** | `beat`, `downbeat`, `vocal`, `boundary`, `section [1, T, 6]` | Single-pass Apple-Music-tier analyzer (beat + downbeat + vocal + song structure) |

## Quickstart on Google Colab / Kaggle (T4 / L4 / P100 GPU)

### 1. Install Dependencies
```bash
git clone https://github.com/aqky29/Swell.git
cd Swell
pip install -q torch torchaudio soundfile soxr onnx onnxruntime onnxscript numpy
# Optional teachers for distillation on unlabeled audio:
pip install -q beat-this demucs
```

### 2. Verify Pipeline with Fast Smoke Test
```bash
python tools/ml/train_and_export.py --mode svad --smoke-test --out-dir /tmp/smoke_svad
python tools/ml/train_and_export.py --mode multitask --smoke-test --out-dir /tmp/smoke_mt
```

### 3. Prepare Training Shards (`.npz`)

**Option A — MUSDB18-HQ (exact ground-truth vocal stems) + Beat This! teacher:**
```bash
python tools/ml/prepare_distill_data.py \
  --musdb-root /content/musdb18hq \
  --out-dir /content/shards \
  --beat-teacher \
  --device cuda
```

**Option B — Distill from any unlabeled music folder (offline teacher -> 100% original student weights):**
```bash
python tools/ml/prepare_distill_data.py \
  --audio-dir /content/music_corpus \
  --out-dir /content/shards \
  --beat-teacher \
  --demucs-teacher \
  --device cuda
```

### 4. Train & Export Quantized ONNX

**Train SVAD (~15–30 min on Colab T4):**
```bash
python tools/ml/train_and_export.py \
  --mode svad \
  --data-dir /content/shards \
  --out-dir /content/out_svad \
  --epochs 40 --batch-size 16 --lr 1.5e-3
```
Produces `/content/out_svad/swell_svad_q8.onnx` (~0.6 MB).

**Train Multi-Task (~1–2 hours on Colab T4):**
```bash
python tools/ml/train_and_export.py \
  --mode multitask \
  --data-dir /content/shards \
  --out-dir /content/out_multitask \
  --epochs 60 --batch-size 8 --lr 1e-3
```
Produces `/content/out_multitask/swell_multitask_q8.onnx` (~2.2 MB).

## Notes
- Only `MatMul` layers are dynamically quantized to per-channel `INT8`; `Conv2d` / `Conv1d` layers stay `FP32` to preserve beat and vocal peak accuracy on Android ONNX Runtime.
- `multitask_loss` masks missing labels per sample, so you can freely mix shards from MUSDB18-HQ (vocal), Beat This! datasets (beat/downbeat), and Harmonix Set (section boundaries/labels) in the same `--data-dir`.
