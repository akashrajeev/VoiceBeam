# Offline ASR test build

Separate debug app ID: com.akashrajeev.voicebeam.asrexperiment. Installs alongside the regular app; it does not replace it or copy existing recordings/settings. Small is the fresh-install default. Both Small q5_1 and Turbo q5_0 are packaged, so the APK will be large (over 800 MB including baseline models/native libraries). First use copies the selected weights into private storage; allow several GB of free space. No audio upload, model download or paid service at runtime.

This is a CPU-only (2 threads), windowed-decode experiment, not a validated live-caption release. GPU is not enabled. Keep the regular VoiceBeam app.

## Test on the iQOO

1. Install the debug APK from this experiment's successful CI build. Android may ask permission to install from the download app. Open the separate test app and grant camera/mic permissions.
2. Complete onboarding. Turn airplane mode on (Wi-Fi off) to demonstrate offline behavior. Keep the same noise-removal setting for every comparison.
3. In Settings > Captions, choose Small, then Android Settings > Apps > this test app > Force stop; reopen. Do not just swipe it away. Model loading may take time on first open.
4. In a quiet room, speak a fixed 30-second passage at normal speed. Note missed/substituted words, delay from speaking to first caption, and delay after you stop talking. Repeat with the same passage and Turbo, using Force stop/reopen after selecting it.
5. Run 2 minutes of continuous speech and check whether caption delay grows. Stop if the phone gets uncomfortably hot or freezes. Do not rely on these captions for a meeting or accessibility until tested.
6. Report exact iQOO model, RAM, Android version, Small/Turbo delay estimates, which words changed, whether delay grows over 2 minutes, and heat/crashes. A screen recording with consent from anyone recorded can help; no private audio is needed.

Optional ADB diagnostics (if available):

```
adb logcat -c
# Run the passage, then:
adb logcat -d -s VoiceBeamAsr AndroidRuntime > voicebeam-asr-log.txt
```

`audioMs` is input duration and `decodeMs` is CPU decode duration for that window. Their ratio is not full end-to-end latency. Repeated overlapping partial decodes, capture, denoising and UI add work. Host WER and speed numbers do not establish phone results. Test includes no GPU measurement.
