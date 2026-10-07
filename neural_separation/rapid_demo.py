#!/usr/bin/env python3
from __future__ import annotations

import argparse
import subprocess
import tempfile
from pathlib import Path

import numpy as np
import soundfile as sf

SR = 16000


def run(cmd: list[str]) -> None:
    print('+', ' '.join(cmd))
    subprocess.run(cmd, check=True)


def ffmpeg_audio(video: Path, wav: Path) -> None:
    run([
        'ffmpeg', '-y', '-i', str(video),
        '-vn', '-ac', '1', '-ar', str(SR), '-c:a', 'pcm_s16le', str(wav)
    ])


def rms(x: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(x), dtype=np.float64) + 1e-10))


def load_audio(path: Path) -> np.ndarray:
    x, sr = sf.read(path, dtype='float32')
    if x.ndim > 1:
        x = x.mean(axis=1)
    if sr != SR:
        raise ValueError(f'{path}: expected {SR} Hz, got {sr}')
    return x


def make_mixture(target_wav: Path, other_wav: Path, out_wav: Path, sir_db: float) -> np.ndarray:
    target = load_audio(target_wav)
    other = load_audio(other_wav)
    n = min(len(target), len(other))
    target = target[:n]
    other = other[:n]

    target_level = rms(target)
    desired_other = target_level / (10.0 ** (sir_db / 20.0))
    other = other * (desired_other / max(rms(other), 1e-8))
    mixture = target + other

    peak = max(float(np.max(np.abs(mixture))), 1e-6)
    if peak > 0.99:
        scale = 0.99 / peak
        target = target * scale
        mixture = mixture * scale

    sf.write(out_wav, mixture.astype(np.float32), SR)
    return target.astype(np.float32)


def make_video_with_mixed_audio(target_video: Path, mixed_wav: Path, out_video: Path) -> None:
    run([
        'ffmpeg', '-y',
        '-i', str(target_video),
        '-i', str(mixed_wav),
        '-map', '0:v:0', '-map', '1:a:0',
        '-c:v', 'copy', '-c:a', 'aac', '-b:a', '128k',
        '-shortest', str(out_video)
    ])


def si_sdr(estimate: np.ndarray, target: np.ndarray) -> float:
    n = min(len(estimate), len(target))
    estimate = estimate[:n].astype(np.float64)
    target = target[:n].astype(np.float64)
    target = target - target.mean()
    estimate = estimate - estimate.mean()
    denom = np.dot(target, target) + 1e-12
    projection = np.dot(estimate, target) / denom * target
    residual = estimate - projection
    return float(10.0 * np.log10((np.dot(projection, projection) + 1e-12) / (np.dot(residual, residual) + 1e-12)))


def extract_output_audio(path: Path, out_wav: Path) -> None:
    if path.suffix.lower() == '.wav':
        out_wav.write_bytes(path.read_bytes())
        return
    run([
        'ffmpeg', '-y', '-i', str(path),
        '-vn', '-ac', '1', '-ar', str(SR), '-c:a', 'pcm_s16le', str(out_wav)
    ])


def find_audio_output(root: Path) -> Path:
    candidates = list(root.rglob('*.wav')) + list(root.rglob('*.WAV'))
    if candidates:
        return max(candidates, key=lambda p: p.stat().st_mtime)
    videos = list(root.rglob('*.mp4')) + list(root.rglob('*.MP4')) + list(root.rglob('*.mov')) + list(root.rglob('*.MOV'))
    if videos:
        return max(videos, key=lambda p: p.stat().st_mtime)
    raise FileNotFoundError(f'No WAV/video output found in {root}')


def main() -> int:
    parser = argparse.ArgumentParser(description='Rapid VoiceBeam AV-TSE overlap experiment')
    parser.add_argument('--target-video', type=Path, required=True)
    parser.add_argument('--interferer-video', type=Path, required=True)
    parser.add_argument('--sir', type=float, default=0.0, help='target/interferer level in dB')
    parser.add_argument('--out', type=Path, default=Path('neural_separation/results'))
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix='voicebeam_tse_'))
    target_wav = work / 'target.wav'
    other_wav = work / 'interferer.wav'
    mixed_wav = work / 'mixture.wav'
    mixed_video = args.out / f'mixture_{args.sir:+g}dB.mp4'.replace('+', 'p').replace('-', 'm')

    ffmpeg_audio(args.target_video, target_wav)
    ffmpeg_audio(args.interferer_video, other_wav)
    target_reference = make_mixture(target_wav, other_wav, mixed_wav, args.sir)
    make_video_with_mixed_audio(args.target_video, mixed_wav, mixed_video)

    print('Running pretrained ClearVoice AV_MossFormer2_TSE_16K...')
    from clearvoice import ClearVoice

    model = ClearVoice(
        task='target_speaker_extraction',
        model_names=['AV_MossFormer2_TSE_16K'],
    )
    model(mixed_video, online_write=True, output_path=str(args.out))

    produced = find_audio_output(args.out)
    output_wav = args.out / f'extracted_{args.sir:+g}dB.wav'.replace('+', 'p').replace('-', 'm')
    extract_output_audio(produced, output_wav)

    extracted = load_audio(output_wav)
    mixture = load_audio(mixed_wav)
    mixture_score = si_sdr(mixture, target_reference)
    extracted_score = si_sdr(extracted, target_reference)
    print(f'mixture SI-SDR:  {mixture_score:.2f} dB')
    print(f'extract SI-SDR:   {extracted_score:.2f} dB')
    print(f'SI-SDR improvement: {extracted_score - mixture_score:.2f} dB')
    print(f'Output: {output_wav}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
