from __future__ import annotations

import numpy as np
import soundfile as sf

from voicebeam_tse.mixer import SR, mix_pair


def test_mix_pair(tmp_path) -> None:
    t = np.sin(2 * np.pi * 220 * np.arange(SR, dtype=np.float32) / SR)
    i = np.sin(2 * np.pi * 330 * np.arange(SR, dtype=np.float32) / SR)
    target_path = tmp_path / 'target.wav'
    interferer_path = tmp_path / 'interferer.wav'
    sf.write(target_path, t, SR)
    sf.write(interferer_path, i, SR)

    mix, target, other = mix_pair(
        target_path,
        interferer_path,
        seconds=1.0,
        sir_db=0.0,
        seed=7,
    )

    assert mix.shape == target.shape == other.shape == (SR,)
    assert np.isfinite(mix).all()
