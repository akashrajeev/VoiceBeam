# VoiceBeam Neural Source Separation — Research Decision

Date: 2026-10-08

## Product objective

VoiceBeam is not a generic speech enhancer. The target experience is:

> Point the camera at the person you want to hear and hear that person's speech even when other people are speaking at the same time.

That requires target-speaker extraction, not only a gain gate.

## Current VoiceBeam

Current main-branch flow:

camera -> face tracking -> lip activity
microphone -> denoising -> VAD
speaker embedding -> voice match
all cues -> TargetGate -> time-varying gain

The target gate scales the mixed waveform. When target and interferer overlap, the mixture still contains both sources. A scalar gain cannot generally reconstruct only the target source.

## Research findings

| Approach | Evidence | Strength | Limitation for VoiceBeam | Decision |
| --- | --- | --- | --- | --- |
| DAVE / TIGER-M | 2.56M parameter two-source separator; official 2026 AVSE challenge system | tiny, runnable, Apache-2.0 | audio-only; two anonymous streams; optimized for Chinese meeting speech | sanity-check baseline |
| PS4 | 2026 REAL-T rank 2; best submitted speaker similarity and timing F1; 71,771 real conversational training samples | explicit target-speaker extraction from mixture + enrollment; English and Chinese | audio-only; device suitability and license must be assessed separately | strongest audio-only TSE baseline |
| Plug-and-Steer | Interspeech 2026; freezes audio-only separator and learns a small Latent Steering Matrix to route the visual target | separates high-fidelity reconstruction from noisy visual target selection | research implementation depends on larger backbones | architecture direction |
| SEANet | 12.95 dB test SI-SDR on VoxMix in its open comparison | strong direct AV-TSE quality reference; MIT code | heavier/non-causal for our phone goal | quality reference |
| Swift-Net | 0.5M-parameter causal AV separation family | excellent size/latency direction | CC BY-NC-SA repository/weights | architecture inspiration only |
| 2S-AVTSE | about 1.6M parameters and 1.90 GMac/s reported; explicitly edge-oriented | closest published edge deployment shape | target cue is a compact visual VAD; separate license/code assessment needed | deployment blueprint |
| VoiceFilter-Lite | 2.2 MB reported and 8-bit real-time mobile TSE | strongest evidence that streaming TSE can be made phone-friendly | feature/ASR-oriented, not a general waveform extractor | mobile design reference |

## Revised strategy

TIGER-M is only the first sanity check.

PS4 should be benchmarked immediately after TIGER-M because it directly performs target-speaker extraction from a target enrollment utterance. Its 2026 REAL-T result makes it a much more relevant audio-only reference for VoiceBeam than a blind separator.

The main VoiceBeam research direction is a compact causal target-conditioned extractor, plus a decoupled-separation experiment inspired by Plug-and-Steer.

The final architecture should make the camera-selected face influence target selection/extraction rather than merely trigger a post-separation mute.

## Branch prototype

CompactAVTSE is an original compact research implementation in this branch.

Inputs:
- 16 kHz mono microphone mixture
- enrolled target speaker embedding
- synchronized visual cue sequence

Architecture:
- mixture STFT
- compact audio projection
- speaker conditioning
- visual conditioning
- causal dilated TCN
- complex ratio mask
- iSTFT target waveform reconstruction

The default configuration is under approximately 1.2M parameters for a 192-dimensional speaker embedding. The current wrapper uses centered STFT for simplicity; strict streaming STFT/overlap-add is a later deployment step.

## Training ladder

Stage A — audio TSE:
mixture + speaker embedding -> target waveform

Stage B — visual-assisted TSE:
mixture + speaker embedding + target lip activity -> target waveform

Stage C — learned visual TSE:
mixture + speaker embedding + learned synchronized mouth features -> target waveform

Stage D — decoupled steering:
audio separator + visual steering module -> selected target stream

Stage E — deployment:
causal streaming state + mobile runtime + quantization

Stage A is mandatory. If speaker-conditioned extraction does not work reliably, adding vision does not solve the core problem.

## Data

Primary AV reference: VoxMix / VoxCeleb2 and LRS2/LRS3 synchronized talking-face data.

Real conversational TSE reference: REAL-PS4.

Indian-English evaluation: held-out NPTEL Indian-English speakers, kept completely separate from training.

Augmentation should cover SIR/SNR variation, room impulse responses, additive noise, target/interferer overlap from 0% to 100%, target-absent scenes, visual dropout, visual/audio mismatch, face occlusion and turn-away.

## Critical evaluation conditions

- target only
- equal-energy simultaneous speech
- interferer louder than target
- target louder than interferer
- target absent
- wrong face selected
- face temporarily missing
- noisy/reverberant overlap
- Indian-English accented speech

## Metrics

Separation: SI-SDR, SI-SDRi, and target-vs-interferer suppression.

Target identity: speaker embedding cosine similarity, wrong-speaker rejection, and target-output correctness.

Speech quality: STOI, PESQ where meaningful, and downstream ASR WER.

Streaming: real-time factor, algorithmic latency, end-to-end audio/caption latency, peak memory, sustained CPU/NPU/GPU use, dropped blocks and thermal/battery impact on the actual iQOO device.

## Acceptance test

> Camera selects Alice -> Alice and Bob speak simultaneously -> output contains Alice with substantially reduced Bob -> an independent speaker model identifies the output as Alice -> ASR on the output improves.

Do not integrate any separator into Android until it wins this test on speaker-disjoint data.

## References

DAVE / TIGER-M: https://github.com/TaurenMountain/DAVE
PS4: https://github.com/TaurenMountain/PS4
Plug-and-Steer: https://github.com/kaistmm/Plug-and-Steer
SEANet: https://github.com/TaoRuijie/SEANet
Swift-Net: https://github.com/JusperLee/Swift-Net
2S-AVTSE: https://arxiv.org/abs/2505.22229
MeanFlow-TSE: https://arxiv.org/abs/2512.18572

MeanFlow-TSE is kept as a future research option. One-step generative TSE is interesting for latency, but it is not the first deployment path.
