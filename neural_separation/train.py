from __future__ import annotations

import argparse
from pathlib import Path

import torch
from torch.utils.data import DataLoader

from voicebeam_tse.data import TSEManifestDataset
from voicebeam_tse.losses import total_loss
from voicebeam_tse.model import CompactAVTSE


def main() -> int:
    p = argparse.ArgumentParser(description='Train the compact VoiceBeam target-speaker extractor.')
    p.add_argument('--train-manifest', required=True)
    p.add_argument('--val-manifest', required=True)
    p.add_argument('--speaker-dim', type=int, required=True)
    p.add_argument('--visual-dim', type=int, default=1)
    p.add_argument('--out', type=Path, default=Path('checkpoints/tse'))
    p.add_argument('--epochs', type=int, default=50)
    p.add_argument('--batch-size', type=int, default=8)
    p.add_argument('--workers', type=int, default=2)
    p.add_argument('--seconds', type=float, default=2.0)
    p.add_argument('--lr', type=float, default=2e-4)
    p.add_argument('--sir-min', type=float, default=-5.0)
    p.add_argument('--sir-max', type=float, default=5.0)
    p.add_argument('--device', default='cuda' if torch.cuda.is_available() else 'cpu')
    p.add_argument('--amp', action='store_true')
    args = p.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    device = torch.device(args.device)

    train_ds = TSEManifestDataset(
        args.train_manifest,
        seconds=args.seconds,
        sir_min_db=args.sir_min,
        sir_max_db=args.sir_max,
        visual_dim=args.visual_dim,
    )
    val_ds = TSEManifestDataset(
        args.val_manifest,
        seconds=args.seconds,
        sir_min_db=args.sir_min,
        sir_max_db=args.sir_max,
        visual_dim=args.visual_dim,
        deterministic=True,
    )

    train_loader = DataLoader(train_ds, batch_size=args.batch_size, shuffle=True, num_workers=args.workers, pin_memory=device.type == 'cuda')
    val_loader = DataLoader(val_ds, batch_size=args.batch_size, shuffle=False, num_workers=args.workers, pin_memory=device.type == 'cuda')

    model = CompactAVTSE(speaker_dim=args.speaker_dim, visual_dim=args.visual_dim).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    scaler = torch.amp.GradScaler('cuda', enabled=args.amp and device.type == 'cuda')

    best = float('inf')
    for epoch in range(1, args.epochs + 1):
        model.train()
        train_total = 0.0
        for mixture, target, speaker, visual in train_loader:
            mixture = mixture.to(device, non_blocking=True)
            target = target.to(device, non_blocking=True)
            speaker = speaker.to(device, non_blocking=True)
            visual = visual.to(device, non_blocking=True)
            optimizer.zero_grad(set_to_none=True)
            with torch.autocast(device_type=device.type, dtype=torch.float16, enabled=args.amp and device.type == 'cuda'):
                estimate = model(mixture, speaker, visual)
                loss = total_loss(estimate, target)
            scaler.scale(loss).backward()
            scaler.unscale_(optimizer)
            torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
            scaler.step(optimizer)
            scaler.update()
            train_total += float(loss.detach())

        model.eval()
        val_total = 0.0
        with torch.no_grad():
            for mixture, target, speaker, visual in val_loader:
                mixture = mixture.to(device)
                target = target.to(device)
                speaker = speaker.to(device)
                visual = visual.to(device)
                estimate = model(mixture, speaker, visual)
                val_total += float(total_loss(estimate, target))

        train_loss = train_total / max(len(train_loader), 1)
        val_loss = val_total / max(len(val_loader), 1)
        print(f'epoch={epoch:03d} train={train_loss:.4f} val={val_loss:.4f}')

        if val_loss < best:
            best = val_loss
            torch.save(
                {'model': model.state_dict(), 'speaker_dim': args.speaker_dim, 'visual_dim': args.visual_dim, 'n_fft': model.n_fft, 'hop': model.hop},
                args.out / 'best.pt',
            )

    return 0


if __name__ == '__main__':
    raise SystemExit(main())
