from __future__ import annotations

import torch
from torch import Tensor, nn
import torch.nn.functional as F


class CausalConv1d(nn.Module):
    """Left-padded convolution: current output never reads future time steps."""

    def __init__(
        self,
        channels_in: int,
        channels_out: int,
        kernel: int,
        dilation: int = 1,
        groups: int = 1,
    ) -> None:
        super().__init__()
        if kernel < 1:
            raise ValueError("kernel must be >= 1")
        self.left = (kernel - 1) * dilation
        self.conv = nn.Conv1d(
            channels_in,
            channels_out,
            kernel,
            dilation=dilation,
            groups=groups,
        )

    def forward(self, x: Tensor) -> Tensor:
        return self.conv(F.pad(x, (self.left, 0)))


class TCNBlock(nn.Module):
    """Low-cost gated temporal block conditioned on the target identity/cue."""

    def __init__(self, channels: int, dilation: int) -> None:
        super().__init__()
        self.norm = nn.GroupNorm(1, channels)
        self.in_proj = nn.Conv1d(channels, channels * 2, 1)
        self.depthwise = CausalConv1d(
            channels,
            channels,
            kernel=5,
            dilation=dilation,
            groups=channels,
        )
        self.out_proj = nn.Conv1d(channels, channels, 1)

    def forward(self, x: Tensor, cond: Tensor) -> Tensor:
        z = self.norm(x)
        a, b = self.in_proj(z).chunk(2, dim=1)
        z = torch.tanh(a + cond) * torch.sigmoid(b)
        z = self.depthwise(z)
        z = self.out_proj(z)
        return x + z


class CompactAVTSE(nn.Module):
    """Compact target-speaker extractor for VoiceBeam research.

    Inputs
    ------
    mixture:
        [B, samples], mono 16 kHz mixture.
    speaker_embedding:
        [B, speaker_dim], enrolled target speaker representation.
    visual_features:
        [B, T] or [B, visual_dim, T]. Optional synchronized features from
        the face selected by the camera.

    Output
    ------
    target waveform [B, samples].

    The model predicts a complex ratio mask on the mixture STFT and
    reconstructs one target waveform. The temporal separator is causal.
    The STFT wrapper currently uses an 8 ms centered look-ahead at 16 kHz;
    a streaming STFT/overlap-add wrapper will remove that implementation
    look-ahead during the deployment phase.
    """

    def __init__(
        self,
        speaker_dim: int,
        visual_dim: int = 1,
        n_fft: int = 256,
        hop: int = 128,
        channels: int = 192,
        blocks: int = 8,
    ) -> None:
        super().__init__()
        if n_fft % 2 or n_fft <= hop:
            raise ValueError("n_fft must be even and greater than hop")

        self.n_fft = n_fft
        self.hop = hop
        self.freq_bins = n_fft // 2 + 1
        self.spec_channels = self.freq_bins * 2

        self.register_buffer(
            "window",
            torch.hann_window(n_fft),
            persistent=False,
        )

        self.audio_in = nn.Conv1d(self.spec_channels, channels, 1)

        self.speaker = nn.Sequential(
            nn.Linear(speaker_dim, channels),
            nn.SiLU(),
            nn.Linear(channels, channels),
        )

        self.visual = nn.Sequential(
            nn.Conv1d(visual_dim, 32, 1),
            nn.SiLU(),
            nn.Conv1d(32, channels, 1),
        )

        self.blocks = nn.ModuleList(
            [TCNBlock(channels, 2**i) for i in range(blocks)]
        )
        self.mask = nn.Conv1d(channels, self.spec_channels, 1)

    def forward(
        self,
        mixture: Tensor,
        speaker_embedding: Tensor,
        visual_features: Tensor | None = None,
    ) -> Tensor:
        if mixture.ndim != 2:
            raise ValueError("mixture must have shape [B, samples]")
        if speaker_embedding.ndim != 2:
            raise ValueError("speaker_embedding must have shape [B, D]")

        batch, samples = mixture.shape
        window = self.window.to(device=mixture.device, dtype=mixture.dtype)

        spec = torch.stft(
            mixture,
            n_fft=self.n_fft,
            hop_length=self.hop,
            win_length=self.n_fft,
            window=window,
            center=True,
            return_complex=True,
        )

        x = torch.cat((spec.real, spec.imag), dim=1)
        x = self.audio_in(x)

        speaker = self.speaker(
            F.normalize(speaker_embedding, dim=-1)
        ).unsqueeze(-1)

        if visual_features is None:
            visual_features = torch.zeros(
                batch,
                1,
                x.shape[-1],
                device=x.device,
                dtype=x.dtype,
            )
        elif visual_features.ndim == 2:
            visual_features = visual_features.unsqueeze(1)
        elif visual_features.ndim != 3:
            raise ValueError(
                "visual_features must have shape [B, T] or [B, C, T]"
            )

        visual_features = visual_features.to(dtype=x.dtype, device=x.device)
        visual_features = F.interpolate(
            visual_features,
            size=x.shape[-1],
            mode="linear",
            align_corners=False,
        )
        visual = self.visual(visual_features)

        condition = speaker + visual
        for block in self.blocks:
            x = block(x, condition)

        mask = 2.0 * torch.tanh(self.mask(x))
        mask_real, mask_imag = mask.chunk(2, dim=1)

        estimated = torch.complex(
            mask_real * spec.real - mask_imag * spec.imag,
            mask_real * spec.imag + mask_imag * spec.real,
        )

        return torch.istft(
            estimated,
            n_fft=self.n_fft,
            hop_length=self.hop,
            win_length=self.n_fft,
            window=window,
            center=True,
            length=samples,
        )
