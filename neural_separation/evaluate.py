from __future__ import annotations

import argparse
from pathlib import Path

import soundfile as sf
import torch

from voicebeam_tse.losses import si_sdr


def load_wav(path: Path) -> torch.Tensor:
    audio, sr = sf.read(path, dtype='float32')
    if audio.ndim > 1:
        audio = audio.mean(axis=1)
    if sr != 16_000:
        raise ValueError(f'{path}: expected 16000 Hz, got {sr}')
    return torch.from_numpy(audio).unsqueeze(0)


def main() -> int:
    parser = argparse.ArgumentParser(description='Evaluate one separated VoiceBeam waveform.')
    parser.add_argument('--mixture', type=Path, required=True)
    parser.add_argument('--target', type=Path, required=True)
    parser.add_argument('--estimate', type=Path, required=True)
    args = parser.parse_args()

    mixture = load_wav(args.mixture)
    target = load_wav(args.target)
    estimate = load_wav(args.estimate)

    n = min(mixture.shape[-1], target.shape[-1], estimate.shape[-1])
    mixture, target, estimate = mixture[..., :n], target[..., :n], estimate[..., :n]

    mixture_score = float(si_sdr(mixture, target).item())
    estimate_score = float(si_sdr(estimate, target).item())
    improvement = estimate_score - mixture_score

    print(f'mixture_si_sdr_db={mixture_score:.3f}')
    print(f'estimate_si_sdr_db={estimate_score:.3f}')
    print(f'si_sdr_improvement_db={improvement:.3f}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
