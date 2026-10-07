#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DAVE_DIR="${ROOT}/.third_party/DAVE"

if [[ -d "${DAVE_DIR}/.git" ]]; then
  echo "DAVE already present at ${DAVE_DIR}"
  exit 0
fi

mkdir -p "${ROOT}/.third_party"
git clone --depth 1 https://github.com/TaurenMountain/DAVE.git "${DAVE_DIR}"

echo "DAVE/TIGER-M available at ${DAVE_DIR}"
echo "Run: python neural_separation/run_tiger_m.py --dave-root ${DAVE_DIR} --mixture <16kHz-mix.wav>"
