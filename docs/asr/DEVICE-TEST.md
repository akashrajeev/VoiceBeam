# Offline ASR test build

Separate debug app ID: com.akashrajeev.voicebeam.asrexperiment. Installs alongside the regular app; it does not replace it or copy existing recordings/settings. Tiny.en is the fresh-install default; Tiny.en q5_1 (32.2 MB) and Base.en q5_1 (59.7 MB) are packaged. Small and Turbo are optional imported files, not bundled. First use copies the selected weights into private storage; allow several GB of free space. No audio upload, model download or paid service at runtime.

This is a CPU-only (2 threads), windowed-decode experiment, not a validated live-caption release. GPU is not enabled. Keep the regular VoiceBeam app.

## Test on the iQOO

1. Install the debug APK from this experiment's successful CI build. Android may ask permission to install from the download app. Open the separate test app and grant camera/mic permissions.
2. Complete onboarding. Turn airplane mode on (Wi-Fi off) to demonstrate offline behavior. Keep the same noise-removal setting for every comparison.
3. In Settings > Captions, choose Tiny.en, then Android Settings > Apps > this test app > Force stop; reopen. Do not just swipe it away. Model loading may take time on first open.
4. In a quiet room, speak a fixed 30-second passage at normal speed. Note missed/substituted words, delay from speaking to first caption, and delay after you stop talking. Repeat with the same passage and Base.en, using Force stop/reopen after selecting it.
5. Run 2 minutes of continuous speech and check whether caption delay grows. Stop if the phone gets uncomfortably hot or freezes. Do not rely on these captions for a meeting or accessibility until tested.
6. Report exact iQOO model, RAM, Android version, Tiny.en/Base.en delay estimates, which words changed, whether delay grows over 2 minutes, and heat/crashes. A screen recording with consent from anyone recorded can help; no private audio is needed.

Optional ADB diagnostics (if available):

```
adb logcat -c
# Run the passage, then:
adb logcat -d -s VoiceBeamAsr AndroidRuntime > voicebeam-asr-log.txt
```

`audioMs` is input duration and `decodeMs` is CPU decode duration for that window. Their ratio is not full end-to-end latency. Repeated overlapping partial decodes, capture, denoising and UI add work. Host WER and speed numbers do not establish phone results. Test includes no GPU measurement.

## Optional larger models

Download the original model file yourself only if desired, then use Settings > Captions > Import Small/Turbo weight file. The app checks the exact tested file size and SHA-256 before installing it. No automatic download and no audio upload. Force stop/reopen after changing selection. Optional imported weights consume extra storage but do not enlarge the APK.

Small q5_1 file: https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin (190,085,487 bytes)
Turbo q5_0 file: https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin (574,041,195 bytes)
