# First Experiment — Neural Separation

## 0. Checkout only this branch

    git fetch origin
    git checkout neural-source-separation
    git pull

Do not switch or merge `tse-overlap` for this experiment.

## 1. Make one controlled test

Create a 16 kHz mono target WAV and interferer WAV. Make a 2–5 second mixture with equal RMS energy (0 dB SIR). Keep the clean target WAV as ground truth.

Minimum fixture:

    target.wav
    interferer.wav
    mix.wav

The same fixture must be used for every separator.

## 2. Run DAVE/TIGER-M baseline

DAVE bundles TIGER-M and its inference script; no second separation repository is required.

    git clone --depth 1 https://github.com/TaurenMountain/DAVE.git /tmp/DAVE
    cd /tmp/DAVE
    python -m pip install torch numpy soundfile
    python inference.py --mixture /path/to/mix.wav --out /path/to/dave_out

Expected output:

    dave_out/<name>/s1.wav
    dave_out/<name>/s2.wav

DAVE returns anonymous streams; do not assume s1 is the target.

## 3. Run PS4 target-speaker extraction

PS4 is the more important baseline because it is explicitly target-conditioned.

    git clone --depth 1 https://github.com/TaurenMountain/PS4.git /tmp/PS4
    cd /tmp/PS4
    python -m pip install torch torchaudio numpy
    wget https://huggingface.co/TaurenMountain/PS4/resolve/main/checkpoint_epoch037.pt
    python inference.py --checkpoint checkpoint_epoch037.pt --mix /path/to/mix.wav --enroll /path/to/target_enrollment.wav --output /path/to/ps4_result.wav

The enrollment file should be a separate clean recording of the target speaker when possible. Do not use the evaluation target waveform as the enrollment file.

## 4. Score both outputs

Run the branch evaluator:

    PYTHONPATH=neural_separation/src python neural_separation/evaluate.py --mixture /path/to/mix.wav --target /path/to/target.wav --estimate /path/to/ps4_result.wav

For DAVE, first decide which stream is the target using an independent speaker encoder; then score that stream. Never use the clean target to choose the stream.

## 5. Listen

Create a three-way listening test:

    raw mixture
    best separated stream
    PS4 target output

The decisive question is not 'does it sound cleaner?' but 'can I still hear the target when the other speaker talks at the same time?'

## 6. Only after the baseline

Then train CompactAVTSE on the same fixture family:

    mixture + target speaker embedding + visual cue -> target waveform

Start with 0 dB SIR, then -5 dB and +5 dB.

Do not start Android integration before one model shows a clear separation win.

## Acceptance gate

Proceed only when at least one neural TSE baseline shows all of:

- positive SI-SDR improvement over the mixture
- visible/reproducible reduction of the interferer during simultaneous speech
- target-speaker identity retained in the extracted waveform
- no obvious target hallucination when the target is absent

After this gate, the next branch milestone is camera-conditioned extraction.
