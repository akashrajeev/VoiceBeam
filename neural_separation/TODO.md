# VoiceBeam Neural Separation TODO

## Milestone 1 — prove separation

- [ ] Obtain DAVE TIGER-M checkout and checkpoint.
- [ ] Build a two-speaker test set with known clean target/interferer references.
- [ ] Run baseline separation on 16 kHz mixtures.
- [ ] Measure SI-SNRi/SI-SDRi and target-vs-interferer suppression.
- [ ] Listen to target output while both speakers overlap.

## Milestone 2 — connect the VoiceBeam target

- [ ] Reuse the enrolled target speaker embedding protocol.
- [ ] Compute an embedding for s1 and s2.
- [ ] Match each stream to the enrolled target.
- [ ] Add lip-activity consistency to prevent stream swaps.
- [ ] Handle target-absent and low-confidence scenes explicitly.

## Milestone 3 — make it genuinely camera-conditioned

- [ ] Extract a synchronized mouth/face feature sequence for the tapped face.
- [ ] Train or adapt an audio-visual target-speaker extraction model.
- [ ] Evaluate at negative, 0 and positive target/interferer SNR.
- [ ] Include simultaneous speech and target-absent examples during training.

## Milestone 4 — streaming and Android

- [ ] Convert the winning model to a causal/chunked runtime.
- [ ] Export to an Android-compatible format.
- [ ] Quantize only after measuring separation degradation.
- [ ] Integrate with the existing AudioPipeline.
- [ ] Measure first-target latency, sustained latency, CPU/NPU/GPU load and heat.

## Split of work

Enhancement track: existing Android/main work.
Neural separation track: this branch and its future standalone repository.

Do not merge experimental separator code into the enhancement track until a clean A/B comparison exists.
