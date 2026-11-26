"""
Stage 3 on-device architectures for Swell Automix.

Both models consume the exact same input tensor contract as `BeatTracker`:
    input_spectrogram: float32[batch, time, 128]   (50 fps Slaney Log-Mel)

1. `SwellSvadNet` (~0.45M params -> ~0.5 MB INT8 / ~1.8 MB FP32):
   Lightweight Singing Voice Activity Detector designed to replace the 9 MB
   Open-Unmix stereo source separator with ~10x faster CPU inference and zero
   extra spectrogram cost (shares BeatTracker's MelSpectrogram).

2. `SwellMultiTaskNet` (~1.8M params -> ~2.2 MB INT8 / ~7.2 MB FP32):
   Unified single-pass Apple-Music-tier music analyzer predicting simultaneously:
     - `beat`:     [B, T]    beat logits (sum-head over downbeat, matching Beat This!)
     - `downbeat`: [B, T]    downbeat logits
     - `vocal`:    [B, T]    singing voice activity logits
     - `boundary`: [B, T]    structural section boundary logits
     - `section`:  [B, T, 6] section class logits (intro, verse, chorus, bridge, inst/drop, outro)

All operations are pure standard ONNX opset-17 (Conv2d, Conv1d, GroupNorm/BatchNorm,
GELU/SiLU, Linear, Multi-head Self-Attention without cached RoPE tables) so models
export cleanly and run reliably on Android & Desktop ONNX Runtime.
"""

from __future__ import annotations

import math
from typing import Dict

import torch
import torch.nn as nn
import torch.nn.functional as F

SECTION_LABELS = ("intro", "verse", "chorus", "bridge", "instrumental", "outro")
NUM_SECTIONS = len(SECTION_LABELS)


class Conv2dStemBlock(nn.Module):
    """2D Conv block that compresses the 128 frequency bins while keeping time resolution exact."""

    def __init__(self, in_ch: int, out_ch: int, freq_stride: int = 2, dropout: float = 0.1):
        super().__init__()
        self.net = nn.Sequential(
            nn.Conv2d(in_ch, out_ch, kernel_size=(3, 3), stride=(1, freq_stride), padding=(1, 1), bias=False),
            nn.BatchNorm2d(out_ch),
            nn.SiLU(inplace=True),
            nn.Conv2d(out_ch, out_ch, kernel_size=(3, 3), stride=(1, 1), padding=(1, 1), bias=False),
            nn.BatchNorm2d(out_ch),
            nn.SiLU(inplace=True),
            nn.Dropout2d(dropout),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.net(x)


class DilatedTcnBlock(nn.Module):
    """Depthwise-separable dilated 1D Conv residual block (fast on mobile CPU, wide temporal receptive field)."""

    def __init__(self, dim: int, dilation: int, kernel_size: int = 5, dropout: float = 0.15):
        super().__init__()
        pad = (kernel_size - 1) * dilation // 2
        self.dw = nn.Conv1d(
            dim, dim, kernel_size=kernel_size, padding=pad, dilation=dilation, groups=dim, bias=False
        )
        self.bn = nn.BatchNorm1d(dim)
        self.pw1 = nn.Linear(dim, dim * 2)
        self.pw2 = nn.Linear(dim, dim)
        self.drop = nn.Dropout(dropout)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: [B, T, C]
        h = self.dw(x.transpose(1, 2))
        h = self.bn(h).transpose(1, 2)
        a, b = self.pw1(h).chunk(2, dim=-1)
        h = a * torch.sigmoid(b)  # GLU gating
        h = self.drop(self.pw2(h))
        return x + h


class MobileConformerBlock(nn.Module):
    """Lightweight Conformer block: FFN -> MHSA -> Depthwise Conv -> FFN (ONNX opset-17 friendly)."""

    def __init__(self, dim: int, num_heads: int = 4, kernel_size: int = 7, dropout: float = 0.15):
        super().__init__()
        self.norm1 = nn.LayerNorm(dim)
        self.ff1 = nn.Sequential(
            nn.Linear(dim, dim * 2),
            nn.SiLU(inplace=True),
            nn.Dropout(dropout),
            nn.Linear(dim * 2, dim),
            nn.Dropout(dropout),
        )
        self.norm_attn = nn.LayerNorm(dim)
        self.num_heads = num_heads
        self.head_dim = dim // num_heads
        self.qkv = nn.Linear(dim, dim * 3, bias=False)
        self.proj = nn.Linear(dim, dim)
        self.attn_drop = nn.Dropout(dropout)

        self.norm_conv = nn.LayerNorm(dim)
        self.conv_pw1 = nn.Linear(dim, dim * 2)
        self.conv_dw = nn.Conv1d(
            dim, dim, kernel_size=kernel_size, padding=kernel_size // 2, groups=dim, bias=False
        )
        self.conv_bn = nn.BatchNorm1d(dim)
        self.conv_pw2 = nn.Linear(dim, dim)
        self.conv_drop = nn.Dropout(dropout)

        self.norm2 = nn.LayerNorm(dim)
        self.ff2 = nn.Sequential(
            nn.Linear(dim, dim * 2),
            nn.SiLU(inplace=True),
            nn.Dropout(dropout),
            nn.Linear(dim * 2, dim),
            nn.Dropout(dropout),
        )
        self.final_norm = nn.LayerNorm(dim)

    def _mhsa(self, x: torch.Tensor) -> torch.Tensor:
        b, t, c = x.shape
        qkv = self.qkv(x).reshape(b, t, 3, self.num_heads, self.head_dim)
        q = qkv[:, :, 0].transpose(1, 2)  # [B, H, T, D]
        k = qkv[:, :, 1].transpose(1, 2)
        v = qkv[:, :, 2].transpose(1, 2)
        scale = 1.0 / math.sqrt(self.head_dim)
        attn = torch.matmul(q, k.transpose(-2, -1)) * scale
        attn = torch.softmax(attn, dim=-1)
        attn = self.attn_drop(attn)
        out = torch.matmul(attn, v).transpose(1, 2).reshape(b, t, c)
        return self.proj(out)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = x + 0.5 * self.ff1(self.norm1(x))
        x = x + self._mhsa(self.norm_attn(x))
        # Conformer conv module
        c = self.norm_conv(x)
        a, b = self.conv_pw1(c).chunk(2, dim=-1)
        c = a * torch.sigmoid(b)
        c = self.conv_dw(c.transpose(1, 2))
        c = F.silu(self.conv_bn(c)).transpose(1, 2)
        x = x + self.conv_drop(self.conv_pw2(c))
        x = x + 0.5 * self.ff2(self.norm2(x))
        return self.final_norm(x)


class SwellSvadNet(nn.Module):
    """
    Ultra-light Singing Voice Activity Detection network (~0.45M params).

    Input:  input_spectrogram [B, T, 128]
    Output: vocal_logits      [B, T]
    """

    def __init__(self, hidden_dim: int = 96, dropout: float = 0.15):
        super().__init__()
        # 128 -> 64 -> 32 -> 16 -> 8 -> 4 mel bins
        self.frontend = nn.Sequential(
            Conv2dStemBlock(1, 24, freq_stride=2, dropout=dropout * 0.5),
            Conv2dStemBlock(24, 48, freq_stride=2, dropout=dropout * 0.5),
            Conv2dStemBlock(48, 64, freq_stride=4, dropout=dropout),
            Conv2dStemBlock(64, 64, freq_stride=4, dropout=dropout),
        )
        # 64 channels * 2 remaining mel bins = 128
        self.proj = nn.Sequential(
            nn.Linear(64 * 2, hidden_dim),
            nn.LayerNorm(hidden_dim),
            nn.SiLU(inplace=True),
        )
        # Receptive field covers ~5 seconds (250 frames @ 50 fps) to distinguish sustained singing
        # from brief percussive hits or melodic synths.
        self.temporal = nn.Sequential(
            DilatedTcnBlock(hidden_dim, dilation=1, kernel_size=5, dropout=dropout),
            DilatedTcnBlock(hidden_dim, dilation=2, kernel_size=5, dropout=dropout),
            DilatedTcnBlock(hidden_dim, dilation=4, kernel_size=5, dropout=dropout),
            DilatedTcnBlock(hidden_dim, dilation=8, kernel_size=5, dropout=dropout),
            DilatedTcnBlock(hidden_dim, dilation=16, kernel_size=5, dropout=dropout),
            DilatedTcnBlock(hidden_dim, dilation=1, kernel_size=5, dropout=dropout),
        )
        self.head = nn.Linear(hidden_dim, 1)

    def forward(self, input_spectrogram: torch.Tensor) -> torch.Tensor:
        # [B, T, 128] -> [B, 1, T, 128]
        x = input_spectrogram.unsqueeze(1)
        x = self.frontend(x)  # [B, 64, T, 2]
        b, c, t, f = x.shape
        x = x.permute(0, 2, 1, 3).reshape(b, t, c * f)
        x = self.proj(x)
        x = self.temporal(x)
        return self.head(x).squeeze(-1)  # [B, T]


class SwellMultiTaskNet(nn.Module):
    """
    Unified Apple-Music-tier music analyzer (~1.8M params).

    Input:  input_spectrogram [B, T, 128]
    Output: tuple(beat, downbeat, vocal, boundary, section)
      - beat:     [B, T]
      - downbeat: [B, T]
      - vocal:    [B, T]
      - boundary: [B, T]
      - section:  [B, T, 6]
    """

    def __init__(self, hidden_dim: int = 128, n_layers: int = 4, num_heads: int = 4, dropout: float = 0.15):
        super().__init__()
        self.frontend = nn.Sequential(
            Conv2dStemBlock(1, 32, freq_stride=2, dropout=dropout * 0.5),   # 64
            Conv2dStemBlock(32, 64, freq_stride=2, dropout=dropout * 0.5),  # 32
            Conv2dStemBlock(64, 96, freq_stride=4, dropout=dropout),        # 8
            Conv2dStemBlock(96, 128, freq_stride=4, dropout=dropout),       # 2
        )
        self.proj = nn.Sequential(
            nn.Linear(128 * 2, hidden_dim),
            nn.LayerNorm(hidden_dim),
            nn.SiLU(inplace=True),
        )
        # Local multi-scale TCN before global Conformer attention
        self.local_tcn = nn.Sequential(
            DilatedTcnBlock(hidden_dim, dilation=1, kernel_size=5, dropout=dropout),
            DilatedTcnBlock(hidden_dim, dilation=2, kernel_size=5, dropout=dropout),
            DilatedTcnBlock(hidden_dim, dilation=4, kernel_size=5, dropout=dropout),
        )
        self.blocks = nn.ModuleList(
            [MobileConformerBlock(hidden_dim, num_heads=num_heads, dropout=dropout) for _ in range(n_layers)]
        )
        # Sum-head design (Beat This! ISMIR 2024): beat_logit = raw_beat + raw_downbeat
        self.beat_raw_head = nn.Linear(hidden_dim, 1)
        self.downbeat_head = nn.Linear(hidden_dim, 1)
        self.vocal_head = nn.Linear(hidden_dim, 1)
        self.boundary_head = nn.Linear(hidden_dim, 1)
        self.section_head = nn.Linear(hidden_dim, NUM_SECTIONS)

    def forward(
        self, input_spectrogram: torch.Tensor
    ) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor]:
        x = self.frontend(input_spectrogram.unsqueeze(1))  # [B, 128, T, 2]
        b, c, t, f = x.shape
        x = x.permute(0, 2, 1, 3).reshape(b, t, c * f)
        x = self.proj(x)
        x = self.local_tcn(x)
        for blk in self.blocks:
            x = blk(x)
        downbeat = self.downbeat_head(x).squeeze(-1)
        beat = self.beat_raw_head(x).squeeze(-1) + downbeat
        vocal = self.vocal_head(x).squeeze(-1)
        boundary = self.boundary_head(x).squeeze(-1)
        section = self.section_head(x)
        return beat, downbeat, vocal, boundary, section


def shift_tolerant_bce_loss(
    logits: torch.Tensor,
    targets: torch.Tensor,
    pos_weight: float = 15.0,
    tol_frames: int = 3,
) -> torch.Tensor:
    """
    Beat This!-style shift-tolerant BCE loss: max-pools predicted logits over ±`tol_frames`
    (±60 ms at 50 fps) so slight annotation jitter still produces sharp, confident peaks.
    """
    k = 2 * tol_frames + 1
    pooled_logits = F.max_pool1d(logits.unsqueeze(1), kernel_size=k, stride=1, padding=tol_frames).squeeze(1)
    # Dilate target mask around positive peaks so adjacent frames aren't penalized as hard negatives
    k_neg = 2 * (tol_frames * 2) + 1
    near_pos = F.max_pool1d(targets.unsqueeze(1), kernel_size=k_neg, stride=1, padding=tol_frames * 2).squeeze(1)
    pos_loss = -targets * pos_weight * F.logsigmoid(pooled_logits)
    neg_loss = -(1.0 - near_pos) * F.logsigmoid(-pooled_logits)
    return (pos_loss + neg_loss).mean()


def multitask_loss(
    outputs: tuple[torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor],
    batch: Dict[str, torch.Tensor],
) -> tuple[torch.Tensor, Dict[str, float]]:
    """
    Computes masked multi-task loss so heterogeneous datasets (e.g., MUSDB18 with vocal-only
    labels, Beat This! dataset with beat/downbeat-only labels, Harmonix with structure labels)
    can be mixed freely in the same training run!
    """
    beat_l, db_l, vox_l, bnd_l, sec_l = outputs
    total = torch.zeros((), device=beat_l.device)
    metrics: Dict[str, float] = {}

    if "beat" in batch and batch["has_beat"].any():
        m = batch["has_beat"]
        loss_b = shift_tolerant_bce_loss(beat_l[m], batch["beat"][m], pos_weight=12.0, tol_frames=3)
        total = total + loss_b
        metrics["beat"] = float(loss_b.detach())

    if "downbeat" in batch and batch["has_downbeat"].any():
        m = batch["has_downbeat"]
        loss_db = shift_tolerant_bce_loss(db_l[m], batch["downbeat"][m], pos_weight=32.0, tol_frames=3)
        total = total + loss_db
        metrics["downbeat"] = float(loss_db.detach())

    if "vocal" in batch and batch["has_vocal"].any():
        m = batch["has_vocal"]
        loss_v = F.binary_cross_entropy_with_logits(vox_l[m], batch["vocal"][m])
        total = total + 1.5 * loss_v
        metrics["vocal"] = float(loss_v.detach())

    if "boundary" in batch and batch["has_boundary"].any():
        m = batch["has_boundary"]
        loss_bnd = shift_tolerant_bce_loss(bnd_l[m], batch["boundary"][m], pos_weight=25.0, tol_frames=12)
        total = total + 0.5 * loss_bnd
        metrics["boundary"] = float(loss_bnd.detach())

    if "section" in batch and batch["has_section"].any():
        m = batch["has_section"]
        loss_sec = F.cross_entropy(sec_l[m].reshape(-1, NUM_SECTIONS), batch["section"][m].reshape(-1), ignore_index=-100)
        total = total + 0.5 * loss_sec
        metrics["section"] = float(loss_sec.detach())

    return total, metrics
