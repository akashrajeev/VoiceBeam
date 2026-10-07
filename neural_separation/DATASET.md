# Dataset format

The training entry point expects CSV manifests with these columns:

    target_wav,interferer_wav,speaker_embedding,visual_features

target_wav: clean target speech, 16 kHz mono.

interferer_wav: a different speaker, 16 kHz mono.

speaker_embedding: a precomputed target-speaker embedding stored as a NumPy .npy vector. The dimension is passed with --speaker-dim.

visual_features: optional NumPy .npy sequence for the camera-selected target. A 1-D array is interpreted as one feature channel. Multi-channel arrays may be shaped [T, C] or [C, T]. The current first experiment uses the existing VoiceBeam lip-activity score as a one-channel cue.

## Recommended data split

Do not split utterances from the same speaker across train and test when evaluating generalization. Prefer speaker-disjoint validation/test identities.

## Mixture curriculum

Start with 2 s crops and SIR uniformly sampled from -5 to +5 dB with no added noise.

Then add target/interferer SIR from -10 to +10 dB, MUSAN/DNS-style noise at 0 to 20 dB SNR, random room impulse responses, target-absent examples, partial visual failures, and delayed visual cues.

Use the clean target waveform during training for SI-SDR and spectral supervision.
