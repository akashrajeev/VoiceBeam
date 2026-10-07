from __future__ import annotations

import torch

from voicebeam_tse import CompactAVTSE, si_sdr_loss


def test_forward_backward_audio_and_visual() -> None:
    model = CompactAVTSE(
        speaker_dim=192,
        visual_dim=1,
        channels=32,
        blocks=3,
    )

    mixture = torch.randn(2, 16000)
    speaker = torch.randn(2, 192)
    visual = torch.rand(2, 50)

    estimated_audio_only = model(mixture, speaker)
    estimated_visual = model(mixture, speaker, visual)

    assert estimated_audio_only.shape == mixture.shape
    assert estimated_visual.shape == mixture.shape

    loss = si_sdr_loss(
        estimated_visual,
        torch.randn_like(estimated_visual),
    )
    loss.backward()
    assert torch.isfinite(loss)


def test_parameter_budget() -> None:
    model = CompactAVTSE(
        speaker_dim=192,
        visual_dim=1,
        channels=192,
        blocks=8,
    )
    params = sum(p.numel() for p in model.parameters())
    assert params < 1_200_000
