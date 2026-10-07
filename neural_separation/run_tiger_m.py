#!/usr/bin/env python3
"""Thin wrapper around the released DAVE/TIGER-M inference entry point.

TIGER-M is the first baseline only. Its two outputs are anonymous; VoiceBeam's
target-speaker assignment comes later.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run DAVE TIGER-M on a 16 kHz mono mixture."
    )
    parser.add_argument("--dave-root", type=Path, required=True)
    parser.add_argument("--mixture", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=Path("neural_separation/output"))
    parser.add_argument("--ckpt", type=Path, default=None)
    parser.add_argument("--device", default=None)
    args = parser.parse_args()

    inference = args.dave_root / "inference.py"
    if not inference.is_file():
        raise FileNotFoundError(f"DAVE inference.py not found: {inference}")

    cmd = [
        sys.executable,
        str(inference),
        "--mixture",
        str(args.mixture),
        "--out",
        str(args.out),
    ]
    if args.ckpt is not None:
        cmd += ["--ckpt", str(args.ckpt)]
    if args.device is not None:
        cmd += ["--device", args.device]

    print("Running:", " ".join(cmd))
    return subprocess.call(cmd, cwd=args.dave_root)


if __name__ == "__main__":
    raise SystemExit(main())
