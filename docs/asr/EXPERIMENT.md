# Offline caption experiment

Branch only. Default recognizer remains Zipformer. Debug settings select Small or Turbo after app restart. Selecting unbundled weights gives a model-load error, never silently uploads audio or substitutes a cloud service.

Whisper small q5_1 weights: 190,085,487 bytes; turbo q5_0: 574,041,195 bytes. JNI CPU decoding uses two threads. Model copy is atomic. Partial decode every two seconds of input; utterance ends after 0.9 seconds of silence or eight seconds. This is a windowed experiment, not true streaming or a proven phone-latency result. The current implementation may accumulate backlog and split words at window boundaries; not suitable for release until measured and corrected.

CI builds only this branch and uploads artifacts. It never updates ci-results/ci-build, never pushes main, and does not create a release. Default package contains small only; set VB_WHISPER_MODEL for turbo weights locally. Runtime has no model download or remote audio path.

Public held-out sample source: https://huggingface.co/datasets/raianand/TIE_shorts (Apache-2.0). First accessible test_0 shard, first 12 distinct speakers whose clips are <=24 seconds. Technical lecture speech, not Svarah or a phone microphone. Raw Transcript is used because Normalised_Transcript contains reference corruption (example: second -> two n d). Selection and hypotheses must be retained; no model tuning on these clips. Svarah download returned 401 and was not used.

Before release: reference audit, larger accent set, identical raw/denoised comparisons, sustained concurrent camera/audio device benchmarks, backlog protection, stable partials and forced-boundary overlap, silence hallucination testing, microphone/headset routing, UI pixel inspection.

Oct 1 follow-up: separate debug test app ID, fresh-install Small default, both Small and Turbo weights packaged. Release defaults remain Zipformer. Experiment CI now runs a focused packaged-model/JNI/selection screenshot smoke test, not the full product regression suite. See DEVICE-TEST.md. Main remains untouched.
