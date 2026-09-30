# Offline ASR experiment - host benchmark results (x86 CI-class host, NOT a phone)

Date: 2026-10-01. Branch: exp/offline-asr. Commit base: c4443a2.

## Corpus
- raianand/TIE_shorts (Hugging Face, Apache-2.0), test_0 shard.
- 6 distinct-speaker clips, <=24s each, Indian-English technical lecture speech.
- Reference: raw `Transcript` field. The `Normalised_Transcript` field is corrupt
  (e.g. "second -> two n d") and was NOT used.
- Google AI Svarah was tried first but is gated (HTTP 401); TIE_shorts used instead.

## Setup
- Zipformer baseline: sherpa-onnx-streaming-zipformer-en-20M-2023-02-17 (the model
  VoiceBeam ships today), run on the same 6 clips, 16 kHz mono.
- Whisper small: ggml-small-q5_1.bin (190,085,487 B), whisper.cpp rev
  6e4ab854f67f743900934a703d5603419384c961, CPU, windowed/offline decode.
- Whisper turbo: ggml-large-v3-turbo-q5_0.bin (574,041,195 B), same harness.
  Run on 2 clips only (slow on host CPU).

## Results (edit-distance errors / reference words)

| Backend                     | Clips | Errors/Words | WER (approx) | RTF (decode time / audio) |
|-----------------------------|-------|--------------|--------------|---------------------------|
| Zipformer 20M (baseline)    | 6     | 136 / 289    | ~47%         | 0.089                     |
| Whisper small q5_1          | 6     | 26 / 289     | ~9.0%        | 0.736                     |
| Whisper large-v3-turbo q5_0 | 2     | 5 / 87       | ~5.7%        | 2.725                     |

Per-clip numbers and transcripts: /tmp artifacts not committed; raw logs kept
out of the repo. Reproduce with scripts/host_asr_bench.py + fetch_whisper.sh.

## Measured vs unmeasured - read before citing
- MEASURED on x86 host CPU only. Nothing here is a phone measurement. No iQOO
  or any Android device latency/heat/memory number exists yet; on-device
  relative costs will differ (big.LITTLE cores, NNAPI/GPU off).
- Zipformer WER (~47%) is much worse than Whisper small (~9%) on this
  Indian-English corpus - expected, the 20M model is tiny and en-US biased.
- Whisper small RTF 0.736 means ~0.74s decode per 1s audio on this host with
  2 threads. That is borderline for live captions even before phone slowdown;
  backlog-accumulation risk is documented in docs/asr/EXPERIMENT.md.
- Turbo q5_0 RTF 2.725 on host: far too slow for real-time on CPU. Kept as an
  accuracy ceiling reference, not a shippable default.
- Whisper path is windowed/offline decode, not true streaming; latency to first
  caption is window-length bound. Zipformer remains the default; Whisper is a
  debug-only selectable backend.
- Turbo ran on only 2 of 6 clips (87 words); treat its WER as indicative, not
  comparable-powered.
