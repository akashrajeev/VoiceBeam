# VoiceBeam Neural Source Separation Lab

This branch is the isolated research track for true neural target-speaker extraction for VoiceBeam.

## Goal

VoiceBeam's product goal is: point the camera at the person you want to hear and recover that person's speech from a multi-speaker mixture.

The current Android pipeline uses a target gate based on face/lip activity and speaker similarity followed by time-varying gain. That can suppress other speech, but it cannot reconstruct a target source when two people talk simultaneously.

This lab changes the architecture to:

    Camera target
        +
    target speaker identity
        +
    mixed microphone signal
        |
        v
    neural source separation / target-speaker extraction
        |
        v
    reconstructed target waveform

## Phase 0: neural separation baseline

The first backend is the TIGER-M separator from the 2026 DAVE project. DAVE publishes a 2.56M-parameter two-source audio separator with an Apache-2.0 codebase and bundled checkpoint.

Important: TIGER-M is an audio-only separator. It produces two anonymous streams and does not know which stream belongs to the camera-selected person. We therefore treat it as the separation backbone, not the final VoiceBeam model.

Reference: https://github.com/TaurenMountain/DAVE

## Phase 1: target assignment

For each separated stream:

1. Compute a speaker embedding.
2. Compare it with the enrolled target speaker embedding.
3. Use the camera-selected face and lip activity as an additional temporal cue.
4. Choose the stream corresponding to the target.
5. Feed only that stream to headphones and ASR.

This is materially different from the current gain gate because the neural separator reconstructs individual sources before target selection.

## Phase 2: direct audio-visual target-speaker extraction

The research endpoint is a causal or low-latency audio-visual target-speaker extractor that consumes the mixture plus the selected face/lip sequence and optionally the target voice embedding, then directly produces one target waveform.

Candidate research families include AV-TSE, AV-SepFormer, and SEANet-style architectures.

## Success criteria

- simultaneous two-speaker overlap produces a distinct target waveform
- target speech remains intelligible when the interferer is louder
- non-target speech is strongly reduced
- target-absent scenes do not hallucinate speech
- latency is bounded enough for interactive listening
- CPU/NPU/GPU load is acceptable on the target iQOO device

Do not use WER alone as the separation metric. Track SI-SNR or SI-SDR improvement, target-vs-interferer suppression, and downstream ASR WER.

## Current status

- research branch created from VoiceBeam/main
- separation research is isolated from the Android enhancement work
- no Android runtime changes are made on this branch
- no claim of real-time phone performance yet
- direct AV-TSE is the final target
