from __future__ import annotations

import torch
from torch import Tensor


def si_sdr(estimate: Tensor, target: Tensor, eps: float = 1e-8) -> Tensor:
    """Scale-invariant SDR in dB for [B, samples] tensors."""
    target_energy = torch.sum(target * target, dim=-1, keepdim=True) + eps
    scale = torch.sum(estimate * target, dim=-1, keepdim=True) / target_energy
    projection = scale * target
    residual = estimate - projection

    ratio = (torch.sum(projection * projection, dim=-1) + eps) / (
        torch.sum(residual * residual, dim=-1) + eps
    )
    return 10.0 * torch.log10(ratio + eps)


def si_sdr_loss(estimate: Tensor, target: Tensor) -> Tensor:
    return -si_sdr(estimate, target).mean()


def spectral_l1(
    estimate: Tensor,
    target: Tensor,
    n_fft: int = 256,
    hop: int = 128,
) -> Tensor:
    """Log-compressed magnitude loss for stable early training."""
    window = torch.hann_window(
        n_fft,
        device=estimate.device,
        dtype=estimate.dtype,
    )
    est = torch.stft(
        estimate,
        n_fft=n_fft,
        hop_length=hop,
        win_length=n_fft,
        window=window,
        center=True,
        return_complex=True,
    )
    ref = torch.stft(
        target,
        n_fft=n_fft,
        hop_length=hop,
        win_length=n_fft,
        window=window,
        center=True,
        return_complex=True,
    )
    return (
        torch.log1p(est.abs()) - torch.log1p(ref.abs())
    ).abs().mean()


def total_loss(
    estimate: Tensor,
    target: Tensor,
    si_weight: float = 1.0,
    spectral_weight: float = 0.1,
) -> Tensor:
    return (
        si_weight * si_sdr_loss(estimate, target)
        + spectral_weight * spectral_l1(estimate, target)
    )
