# VoiceBeam Neural Separation Design

## Current limitation

The current system computes a target probability from lip activity and speaker match, then applies a smooth gain to the mixed signal. This suppresses the mixture but does not reconstruct the target source.

Conceptually:

    x(t) = target(t) + interferer(t) + noise(t)
    y(t) = g(t) * x(t)

When target and interferer overlap, the same waveform contains both sources. A gain cannot in general recover only target(t).

## Near-term prototype

    camera
      |
      +--> selected face / lip timeline
      |
    microphone
      |
      +--> TIGER-M --> s1, s2
                         |
                   +-----+------+
                   |            |
              speaker emb   speaker emb
                   |            |
                   +-----+------+
                         |
                   target assignment
                         |
                         v
                    target.wav

The target assignment can initially use the enrolled voiceprint. Lip activity should be a temporal consistency signal rather than a hard mute gate.

## Final architecture

    camera-selected face
             |
       visual encoder
             |
        target cue  --------+
                             |
    target voice embedding --+--> causal target-speaker extractor
                             |
    microphone mixture ------+
                             |
                             v
                      target waveform
                             |
                         headphones
                             |
                            ASR

The final model should consume the target cue during separation, not merely after it.

## Research decision

Use DAVE TIGER-M as the first neural baseline because its released backbone is only 2.56M parameters, has a runnable inference entry point, and is Apache-2.0. This answers the first concrete question: can a genuine neural separator solve the overlap failure case for VoiceBeam?

Then compare it against a direct audio-visual target-speaker extraction model. That direct model is the better final architecture because it can condition the separation itself on the selected face.

## First decisive experiment

Record or synthesize a two-speaker clip where Alice and Bob speak simultaneously.

    Alice + Bob
         |
    point camera at Alice
         |
         v
    neural separation
         |
         v
      Alice.wav

Evaluate the separated target with SI-SNR/SI-SDR, interference suppression, and downstream ASR WER.

Do not optimize for Android until the separation signal itself is demonstrably better.

## Deployment constraint

After the desktop prototype is successful, profile streaming latency, memory, CPU/NPU/GPU load, and battery/heat on the actual iQOO device. Only then choose the Android runtime format and quantization strategy.
