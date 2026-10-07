from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import torch
from torch import Tensor
from torch.utils.data import Dataset

from .mixer import SR, mix_pair


@dataclass(frozen=True)
class ManifestRow:
    target_wav: Path
    interferer_wav: Path
    speaker_embedding: Path
    visual_features: Path | None


def read_manifest(path: str | Path) -> list[ManifestRow]:
    rows: list[ManifestRow] = []
    with Path(path).open(newline='', encoding='utf-8') as handle:
        for row in csv.DictReader(handle):
            visual = row.get('visual_features', '').strip()
            rows.append(
                ManifestRow(
                    target_wav=Path(row['target_wav']),
                    interferer_wav=Path(row['interferer_wav']),
                    speaker_embedding=Path(row['speaker_embedding']),
                    visual_features=Path(visual) if visual else None,
                )
            )
    if not rows:
        raise ValueError(f'Manifest is empty: {path}')
    return rows


class TSEManifestDataset(Dataset[tuple[Tensor, Tensor, Tensor, Tensor]]):
    def __init__(
        self,
        manifest: str | Path,
        seconds: float = 2.0,
        sir_min_db: float = -5.0,
        sir_max_db: float = 5.0,
        visual_dim: int = 1,
        deterministic: bool = False,
        seed: int = 0,
    ) -> None:
        self.rows = read_manifest(manifest)
        self.seconds = seconds
        self.sir_min_db = sir_min_db
        self.sir_max_db = sir_max_db
        self.visual_dim = visual_dim
        self.deterministic = deterministic
        self.seed = seed

    def __len__(self) -> int:
        return len(self.rows)

    def _rng(self, idx: int) -> np.random.Generator:
        return np.random.default_rng(self.seed + idx if self.deterministic else None)

    def __getitem__(self, idx: int) -> tuple[Tensor, Tensor, Tensor, Tensor]:
        row = self.rows[idx]
        rng = self._rng(idx)
        seed = int(rng.integers(0, 2**31 - 1))
        sir = float(rng.uniform(self.sir_min_db, self.sir_max_db))
        mixture, target, _ = mix_pair(
            row.target_wav,
            row.interferer_wav,
            seconds=self.seconds,
            sir_db=sir,
            seed=seed,
        )

        speaker = np.load(row.speaker_embedding).astype(np.float32).reshape(-1)

        if row.visual_features is None:
            frames = max(1, int(round(self.seconds * 125)))
            visual = np.zeros((self.visual_dim, frames), dtype=np.float32)
        else:
            raw = np.load(row.visual_features).astype(np.float32)
            if raw.ndim == 1:
                raw = raw[:, None]
            if raw.shape[-1] == self.visual_dim:
                visual = raw.T
            elif raw.shape[0] == self.visual_dim:
                visual = raw
            else:
                raise ValueError(
                    f'{row.visual_features}: incompatible shape {raw.shape} for visual_dim={self.visual_dim}'
                )

        return (
            torch.from_numpy(mixture),
            torch.from_numpy(target),
            torch.from_numpy(speaker),
            torch.from_numpy(visual),
        )
