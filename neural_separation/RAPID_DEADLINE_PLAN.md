# Rapid Finale Experiment

This is the deadline path for tonight/tomorrow morning.

## Goal

Produce one real demo:

    target video (target face visible)
    +
    target audio + interferer audio mixed together
    -> AV_MossFormer2_TSE_16K
    -> target-extracted audio

ClearVoice exposes the pretrained `AV_MossFormer2_TSE_16K` model through the `target_speaker_extraction` task. The model is explicitly configured for 16 kHz audio with a lip cue and ResNet18 visual reference encoder. Official examples pass a video containing the target face and write an output video. The model automatically downloads its checkpoint through the ClearVoice interface.

## Why this is our deadline model

- no training tonight
- true neural target-speaker extraction
- target is selected visually by the video
- output is reconstructed speech, not a scalar gain
- already packaged for inference

## Test fixture

Record two short videos:

    target.mp4       # target speaker talking to camera
    interferer.mp4   # second speaker talking

Keep the target video as the visual stream. Extract both audio tracks, mix them at the desired SIR, and replace the target video audio with that mixture.

Then run the AV-TSE model on the mixed video.

## Required tests

Run at least:

    +5 dB target over interferer
     0 dB equal energy
    -5 dB interferer over target

Also run one target-absent test if time allows.

## Acceptance demo

Play the source mixture first.
Then play the model output.

While both speakers talk simultaneously, the output should preserve the camera-selected target and substantially suppress the interferer.

Do not claim a numeric improvement until a clean target reference exists for the same time segment.

## Install

    python -m pip install clearvoice

Optional:

    ffmpeg -version

ClearVoice documents the PyPI install and the `AV_MossFormer2_TSE_16K` model. It can operate directly on WAV/video inputs.

## Run

    python rapid_demo.py --target-video target.mp4 --interferer-video interferer.mp4 --sir 0

The script creates a mixed target-view video and invokes ClearVoice TSE.

## Tomorrow morning

1. Run the 0 dB overlap demo.
2. Run -5 dB and +5 dB.
3. Save the three input/output examples.
4. Measure target similarity with an independent speaker encoder.
5. Feed extracted audio to VoiceBeam ASR and record WER/qualitative results.

Only after this demo is working should we decide whether to fine-tune or replace the separator with our compact model.
