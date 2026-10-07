#!/usr/bin/env python3
"""Run the lightweight TIGER-M neural separation baseline.

The model implementation and checkpoint are intentionally kept outside the
VoiceBeam application until the research result is validated.

Example:

    python neural_separation/run_tiger_m.py --dave-root /path/to/DAVE --mixture samples/mix.wav --out neural_separation/output

DAVE's released TIGER-M accepts 16 kHz mono audio and returns two anonymous
separated streams. Stream order is not target speaker identity.
"""

from __future__ import annotations

import argparse
import importlib.util
import sys
from pathlib import Path


def load_dave_inference(dave_root: Path):
    inference_py = dave_root / "inference.py"
    if not inference_py.is_file():
        raise FileNotFoundError(
            f"Expected DAVE inference.py at {inference_py}. "
            "Clone https://github.com/TaurenMountain/DAVE first."
        )

    sys.path.insert(0, str(dave_root / "src"))
    spec = importlib.util.spec_from_file_location("dave_inference", inference_py)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load {inference_py}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Separate a 16 kHz two-speaker mixture with DAVE TIGER-M."
    )
    parser.add_argument("--dave-root", type=Path, required=True)
    parser.add_argument("--mixture", type=Path, required=True)
    parser.add_argument("--ckpt", type=Path, default=None)
    parser.add_argument("--out", type=Path, default=Path("neural_separation/output"))
    parser.add_argument("--device", default=None)
    args = parser.parse_args()

    import torch

    dave = load_dave_inference(args.dave_root)
    device = args.device or ("cuda" if torch.cuda.is_available() else "cpu")
    ckpt = args.ckpt or (args.dave_root / "model" / "real_avse_tiger_m.ckpt")

    if not ckpt.is_file():
        raise FileNotFoundError(f"TIGER-M checkpoint not found: {ckpt}")
    if not args.mixture.is_file():
        raise FileNotFoundError(f"Mixture not found: {args.mixture}")

    model = dave.load_model(str(ckpt), device)
    output_dir = dave.separate(model, str(args.mixture), str(args.out), device)

    print(f"Separated mixture -> {output_dir}/s1.wav and {output_dir}/s2.wav")
    print("Next step: target assignment using the camera-selected speaker.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
