# VoiceBeam

Tap a face. Hear only them.

VoiceBeam is an Android app for people who struggle to follow one voice in a noisy room. Point the camera at the room, tap the person you want to hear, and VoiceBeam plays their voice into your earphones with the noise and other voices turned down. It also shows live captions and can save the cleaned conversation.

Everything runs on the phone. No account, no internet, nothing uploaded.

## How it works

- **Face and lip tracking**: MediaPipe Face Landmarker follows every face and measures how much each mouth is moving.
- **Noise removal**: GTCRN speech enhancement cleans the mic signal in real time.
- **Who is talking**: lip movement of the tapped face, plus a voice fingerprint (CAM++) that VoiceBeam learns from moments when only that person's lips move. A smooth gate turns everyone else down by the "Quiet others" amount.
- **Captions**: streaming Zipformer speech recognition (sherpa-onnx), split into "Speaker 1" and "Others".
- **Saving**: audio only (.m4a), audio + video (camera video with the clean voice, captions burned in or as .srt), or captions only. Sessions can be replayed, compared raw vs clean, and exported.
- **Stage captions**: optional page on the local Wi-Fi so a laptop can show big captions.

## Build

```
bash scripts/fetch_models.sh     # downloads the open-source models and sherpa-onnx
./gradlew assembleRelease
```

Kotlin + Jetpack Compose, minSdk 26.
