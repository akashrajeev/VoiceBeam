#!/usr/bin/env bash
# Downloads the open-source models and the sherpa-onnx Android library.
# Everything runs on the phone; nothing is called over the network at runtime.
set -euo pipefail
cd "$(dirname "$0")/.."
SHERPA_VERSION=1.13.8
ASSETS=app/src/main/assets/models
mkdir -p "$ASSETS" app/libs
REL=https://github.com/k2-fsa/sherpa-onnx/releases/download

if [ ! -f app/libs/sherpa-onnx.aar ]; then
  curl -fL -o app/libs/sherpa-onnx.aar "$REL/v$SHERPA_VERSION/sherpa-onnx-$SHERPA_VERSION.aar"
fi

# 1. Streaming speech recognition (English, 20M params, int8)
ASR=sherpa-onnx-streaming-zipformer-en-20M-2023-02-17
if [ ! -f "$ASSETS/asr/tokens.txt" ]; then
  tmp=$(mktemp -d)
  curl -fL -o "$tmp/asr.tar.bz2" "$REL/asr-models/$ASR.tar.bz2"
  tar -xjf "$tmp/asr.tar.bz2" -C "$tmp"
  mkdir -p "$ASSETS/asr"
  cp "$tmp/$ASR/encoder-epoch-99-avg-1.int8.onnx" "$ASSETS/asr/encoder.onnx"
  cp "$tmp/$ASR/decoder-epoch-99-avg-1.onnx" "$ASSETS/asr/decoder.onnx"
  cp "$tmp/$ASR/joiner-epoch-99-avg-1.int8.onnx" "$ASSETS/asr/joiner.onnx"
  cp "$tmp/$ASR/tokens.txt" "$ASSETS/asr/tokens.txt"
  mkdir -p app/src/androidTest/assets
  cp "$tmp/$ASR/test_wavs/0.wav" app/src/androidTest/assets/test_speech.wav || true
  rm -rf "$tmp"
fi

# 2. Speech enhancement (GTCRN, ~0.5 MB)
[ -f "$ASSETS/gtcrn.onnx" ] || curl -fL -o "$ASSETS/gtcrn.onnx" "$REL/speech-enhancement-models/gtcrn_simple.onnx"

# 3. Speaker embedding (CAM++ trained on VoxCeleb, English)
[ -f "$ASSETS/speaker.onnx" ] || curl -fL -o "$ASSETS/speaker.onnx" "$REL/speaker-recongition-models/3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"

# 4. Face + lip landmarks (MediaPipe Face Landmarker)
[ -f "$ASSETS/face_landmarker.task" ] || curl -fL -o "$ASSETS/face_landmarker.task" "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"

# Noisy sample for the instrumented denoiser test
mkdir -p app/src/androidTest/assets
[ -f app/src/androidTest/assets/noisy_speech.wav ] || curl -fL -o app/src/androidTest/assets/noisy_speech.wav "$REL/speech-enhancement-models/speech_with_noise.wav" || true

ls -la "$ASSETS" "$ASSETS/asr"
