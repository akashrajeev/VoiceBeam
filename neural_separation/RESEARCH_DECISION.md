# VoiceBeam Neural Source Separation — Research Decision

Date: 2026-10-08

## Product objective

VoiceBeam is not a generic speech enhancer. The target experience is:

> Point the camera at the person you want to hear and hear that person's speech even when other people are speaking at the same time.

That requires target-speaker extraction, not only a gain gate.

## What the current VoiceBeam does

Current main-branch flow:

camera -> face tracking -> lip activity
microphone -> denoising -> VAD
speaker embedding -> voice match
all cues -> TargetGate -> time-varying gain

The target gate scales the mixed waveform. When target and interferer overlap, the mixture still contains both sources. A scalar gain cannot generally reconstruct only the target source.

## Research findings

| Approach | Evidence | Strength | Limitation for VoiceBeam | Decision |
| --- | --- | --- | --- | --- |
| DAVE / TIGER-M | 2.56M parameter two-source separator; real-world AVSE challenge system | tiny, runnable, Apache-2.0 | audio-only; two anonymous streams; released model trained for Chinese meeting speech | baseline only |
| SEANet | 12.95 dB test SI-SDR on VoxMix; 4 AV architectures and lip features released | direct AV target extraction; MIT code | research-scale/non-causal compared with our phone goal | quality reference |
| Swift-Net | 0.5M params; 20.68G MACs for Swift-6; 13.3 dB SI-SNRi on LRS2 and causal design | excellent low-latency AV separation direction | repository is CC BY-NC-SA; not ideal for product code | architecture inspiration |
| 2S-AVTSE | about 1.6M params and 1.90 GMac/s; 1.46 ms M1 Pro / 2.9 ms i5-12450H per frame in reported ONNX test | explicitly designed for edge real-time AVTSE | visual input is simplified to target VVAD; paper code is not the cleanest starting point | strongest deployment reference |
| VoiceFilter-Lite | 2.2 MB reported; 8-bit real-time on-device targeted voice separation | proven mobile TSE concept | enhances ASR features rather than reconstructing an audio waveform | design reference |
| SpeakerBeam-SS | lightweight causal Conv-TasNet + S4D; reported 78% RTF reduction vs causal Conv-TasNet at matched performance | excellent audio-only streaming TSE direction | requires separate visual conditioning for VoiceBeam | streaming audio backbone reference |

## Chosen implementation path

Do NOT make TIGER-M the final VoiceBeam separator.

Do NOT start by porting a large Transformer AV separator to Android.

Build a compact target-conditioned complex-ratio-mask model with:

1. mixed microphone STFT
2. enrolled target speaker embedding
3. selected-face visual speech cue
4. causal temporal mask network
5. complex mask -> target waveform

The first visual cue is the existing VoiceBeam lip-activity timeline. The next model revision can consume a learned mouth feature sequence. Keeping the input abstraction small lets the separator be trained before the phone vision stack is changed.

## Why this architecture

Speaker conditioning answers: who should be extracted?
Visual conditioning answers: when is that selected face producing speech?
Mixture acoustics answer: what acoustic source is present?
Complex masking answers: what target waveform should be reconstructed?

This separates target extraction from the existing UI/gating logic and makes overlap a first-class training condition.

## Training ladder

Stage A — audio TSE:
mixture + speaker embedding -> target waveform

Stage B — visual-assisted TSE:
mixture + speaker embedding + target lip activity -> target waveform

Stage C — visual target embedding:
mixture + speaker embedding + learned lip sequence -> target waveform

Stage D — deployment:
causal streaming state + ONNX/mobile runtime + quantization

Stage A is important because it gives a clean ablation: if the separator cannot extract a target with a reliable speaker embedding, adding camera features will not rescue the architecture.

## Datasets

Primary AV training/evaluation reference: VoxMix/VoxCeleb2, because SEANet provides a reproducible AV-TSE setup and reported baselines.

Primary synchronized visual sources: LRS2/LRS3 and VoxCeleb2-style talking-face data.

Additional Indian-English stress test: construct held-out mixtures from the existing NPTEL Indian-English corpus. Keep this as a separate evaluation set rather than contaminating training.

Noise/reverb: sample speech-shaped noise, MUSAN/DNS-style noise, random RIRs, and target/interferer SNR/SIR variation.

## Critical training examples

- target louder than interferer
- interferer louder than target
- equal-energy overlap
- target-only speech
- interferer-only speech while target face is visible
- target absent
- stationary background noise
- transient non-speech events
- visual/audio desynchronization
- face temporarily occluded
- target face turns away

## Losses

Primary: negative SI-SDR/SI-SDR improvement.
Secondary: complex-spectrogram reconstruction loss.
Deployment-oriented: mild mask regularization and optional speaker-embedding consistency loss.
Do not optimize perceptual metrics first; first prove target/source fidelity.

## Metrics

Report SI-SDRi or SI-SDR, target-vs-interferer suppression, STOI, PESQ when appropriate, and downstream ASR WER.

For streaming, also report algorithmic latency, real-time factor, peak memory, dropped frames, CPU/NPU/GPU load, and sustained thermal behavior.

## Final architecture target

camera -> selected face -> visual encoder ----+
                                               |
enrolled voice -> speaker encoder -------------+-> target extractor -> target waveform -> headphones
                                               |
microphone mixture ----------------------------+
                                               |
                                               +-> ASR

Deployment is a later milestone. The separator must first win the overlap experiment on desktop hardware.
