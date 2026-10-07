# VoiceBeam Neural Separation Experiment Plan

## Objective

Determine which architecture gives the best path to:

    camera selects Person A
              +
       Person A voice
              +
       A+B simultaneous speech
              |
              v
       Person A waveform

## A/B/C/D baselines

### A — no separator
Current VoiceBeam mixture/gain pipeline. This establishes the overlap baseline.

### B — DAVE / TIGER-M
Run the released 2-source audio separator. Measure whether neural separation improves the raw mixture before any target assignment. This is an anonymous-source baseline.

### C — PS4 target-speaker extraction
Run the released PS4 inference path with the mixture and a clean target enrollment utterance. This is the strongest immediate audio-only target-extraction reference because the model is explicitly conditioned on the target speaker.

### D — CompactAVTSE
Train the branch's compact complex-mask model on the same source pairs using target speaker embedding plus a visual cue.

## Evaluation matrix

| Condition | SIR | Visual condition | Required result |
| --- | ---: | --- | --- |
| target only | n/a | target visible | identity preserved |
| equal overlap | 0 dB | target visible | target extracted |
| interferer louder | -5 dB | target visible | target remains intelligible |
| target louder | +5 dB | target visible | low interference |
| target absent | n/a | target visible | no hallucinated target |
| visual dropout | 0 dB | face missing | stable output / safe fallback |
| visual mismatch | 0 dB | wrong face | reject wrong target |
| noisy overlap | 0 dB | target visible | separation survives noise |

## Metrics

1. Mixture SI-SDR.
2. Estimated-target SI-SDR.
3. SI-SDRi over the mixture.
4. Interferer suppression.
5. Target speaker similarity.
6. Downstream ASR WER on the estimated target.
7. Streaming real-time factor and end-to-end algorithmic latency.
8. Peak memory.

## Most important metric

Do not crown a model by SI-SDR alone. A separator that produces a clean but wrong speaker is a VoiceBeam failure.

Use an independent target-speaker embedding model on the estimated waveform and compare it with the target enrollment. Add explicit wrong-speaker rejection tests.

## Dataset policy

Train and validation speakers must be disjoint from the final test speakers.
Keep an Indian-English held-out set separate from public AV training data.
Use exact clean target references for objective evaluation.
Record the random seed and SIR/SNR for every fixture.

## Decision gate

Do not integrate any separator into Android until it beats the current VoiceBeam overlap baseline on a speaker-disjoint test set and survives target-absent/wrong-target tests.

After quality is established, optimize only the winning architecture for causal streaming and the iQOO device.
