# Lightweight ASR comparison, 1 Oct 2026

Six identical Indian-English TIE_shorts clips, 289 reference words, raw Transcript references; technical lecture speech, not microphone speech. x86 host CPU, 2 threads. RTF = processing seconds/audio seconds; not phone latency. Lower is better. Models were not tuned to this corpus.

| Model | Weight files, decimal MB | Errors / words | WER | Host RTF | Mode |
|---|---:|---:|---:|---:|---|
| tiny.en-q5_1 | 32.2 | 21/289 | 7.27% | 0.120 | Windowed offline |
| base.en-q5_1 | 59.7 | 20/289 | 6.92% | 0.238 | Windowed offline |
| moonshine-tiny | 124.0 | 44/289 | 15.22% | 0.043 | Offline utterance |
| nemo80 | 132.1 | 95/289 | 32.87% | 0.291 | Native streaming |
| zipformer-large | 70.9 | 131/289 | 45.33% | 0.177 | Native streaming |
| small-q4_0 | 145.5 | 30/289 | 10.38% | 0.411 | Windowed offline |

Prior matched six-clip runs: current Zipformer 20M 47.06% WER, RTF 0.089; Small q5_1 190.1 MB, 9.00% WER, RTF 0.736. Runs occurred at different times. Whisper CLI timing includes process/model load; sherpa recognizers are loaded once before timing. Do not use these cross-runtime RTF values as a precise speed ranking. Weight size excludes app/native libraries and RAM working set.

Tiny.en is the recommended lightweight test default: 32.2 MB, 21 errors. Base.en has 20 errors, only one fewer, at 59.7 MB and about twice the host decode time. The tiny sample does not prove Tiny/Base beat Small on broader accents. English-only models are suitable only for the current English caption scope. Both require endpoint/window logic; they are not native streaming. Sustained app latency/backlog remains unmeasured.

Native-streaming options tested did not match Tiny/Base accuracy on this sample. Moonshine Tiny was fast but 15.22% WER, and its tested sherpa path is offline, not native streaming. No phone latency, RAM, heat or GPU measurement. Distil-Whisper/Parakeet were not benchmarked in this comparison.

Small q4_0 reduces weights from 190.1 to 145.5 MB (about 23.5%), with 30 errors vs 26 for q5_1: +1.38 percentage points WER here. Tiny/Base save much more space on this sample. q4_0 was quantized from original Small f16 with the pinned whisper.cpp tool; re-quantizing q5 is not supported.

Sources: https://huggingface.co/datasets/raianand/TIE_shorts ; https://huggingface.co/ggerganov/whisper.cpp ; https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models ; https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html ; https://github.com/k2-fsa/sherpa-onnx/blob/master/python-api-examples/online-nemo-streaming-ctc-decode.py ; https://github.com/k2-fsa/sherpa-onnx/blob/master/python-api-examples/offline-moonshine-decode-files.py

Benchmark script, exact hypotheses and per-clip results retained in this branch under docs/asr/lightweight-data.
