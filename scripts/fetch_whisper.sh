#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
REV=6e4ab854f67f743900934a703d5603419384c961
if [ ! -f third_party/whisper.cpp/include/whisper.h ]; then
  mkdir -p third_party/whisper.cpp
  curl -fL "https://github.com/ggml-org/whisper.cpp/archive/$REV.tar.gz" | tar -xz --strip-components=1 -C third_party/whisper.cpp
fi
# Models live in private app storage after a one-time asset copy. No runtime download.
mkdir -p app/src/main/assets/models/whisper
MODEL=${VB_WHISPER_MODEL:-small-q5_1}
case "$MODEL" in small-q5_1|large-v3-turbo-q5_0|both) ;; *) echo "Unsupported experiment model" >&2; exit 1;; esac
MODELS="$MODEL"
[ "$MODEL" != both ] || MODELS="small-q5_1 large-v3-turbo-q5_0"
for MODEL in $MODELS; do
DEST="app/src/main/assets/models/whisper/ggml-$MODEL.bin"
if [ ! -s "$DEST" ]; then
  curl -fL "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-$MODEL.bin" -o "$DEST.tmp"
  mv "$DEST.tmp" "$DEST"
fi
done
