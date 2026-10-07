from __future__ import annotations

from pathlib import Path

import numpy as np
import soundfile as sf


SR = 16_000


def _load(path: str | Path) -> np.ndarray:
    audio, sr = sf.read(path, dtype='float32')
    if audio.ndim > 1:
        audio = audio.mean(axis=1)
    if sr != SR:
        raise ValueError(f'{path}: expected {SR} Hz, got {sr} Hz')
    return audio


def _rms(x: np.ndarray, eps: float = 1e-8) -> float:
    return float(np.sqrt(np.mean(x * x) + eps))


def _fit(x: np.ndarray, n: int, rng: np.random.Generator) -> np.ndarray:
    if len(x) == n:
        return x.copy()
    if len(x) > n:
        start = int(rng.integers(0, len(x) - n + 1))
        return x[start : start + n].copy()
    out = np.zeros(n, dtype=np.float32)
    out[: len(x)] = x
    return out


def mix_pair(
    target_path: str | Path,
    interferer_path: str | Path,
    seconds: float = 2.0,
    sir_db: float = 0.0,
    noise_path: str | Path | None = None,
    snr_db: float | None = None,
    seed: int = 0,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    '''Create a deterministic target/interferer/noise mixture at 16 kHz.

    Returns (mixture, target, interferer_plus_noise).
    '''
    rng = np.random.default_rng(seed)
    n = int(round(seconds * SR))
    target = _fit(_load(target_path), n, rng)
    interferer = _fit(_load(interferer_path), n, rng)

    target_rms = _rms(target)
    desired_other_rms = target_rms / (10.0 ** (sir_db / 20.0))
    interferer *= desired_other_rms / max(_rms(interferer), 1e-8)

    other = interferer.copy()
    if noise_path is not None:
        noise = _fit(_load(noise_path), n, rng)
        effective_snr = 10.0 if snr_db is None else snr_db
        desired_noise_rms = target_rms / (10.0 ** (effective_snr / 20.0))
        noise *= desired_noise_rms / max(_rms(noise), 1e-8)
        other += noise

    mixture = target + other
    peak = max(float(np.max(np.abs(mixture))), 1e-6)
    if peak > 0.99:
        scale = 0.99 / peak
        mixture *= scale
        target *= scale
        other *= scale

    return mixture.astype(np.float32), target.astype(np.float32), other.astype(np.float32)
