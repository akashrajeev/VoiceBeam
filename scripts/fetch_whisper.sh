#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
REV=6e4ab854f67f743900934a703d5603419384c961
if [ ! -f third_party/whisper.cpp/include/whisper.h ]; then
  mkdir -p third_party/whisper.cpp
  curl -fL "https://github.com/ggml-org/whisper.cpp/archive/$REV.tar.gz" | tar -xz --strip-components=1 -C third_party/whisper.cpp
fi
# Package only lightweight English weights. Optional models are manually imported.
mkdir -p app/src/main/assets/models/whisper
rm -f app/src/main/assets/models/whisper/ggml-small*.bin app/src/main/assets/models/whisper/ggml-large*.bin
for MODEL in tiny.en-q5_1 base.en-q5_1; do
  DEST="app/src/main/assets/models/whisper/ggml-$MODEL.bin"
  if [ ! -s "$DEST" ]; then
    curl -fL "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-$MODEL.bin" -o "$DEST.tmp"
    mv "$DEST.tmp" "$DEST"
  fi
done
