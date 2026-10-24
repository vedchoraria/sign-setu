"""ASL reader trainer with GPU augmentation and optional distillation from teacher checkpoints."""
import os
import pathlib
import pickle
import sys

import numpy as np
import torch
from torch import nn

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from features import ARMS, ARMS_FLIPPED, FRAMES
from train import STRONG, SignNet, batch, evaluate

TMAX = 80
KD_T, KD_W = 2.0, float(os.environ.get("SIGN_KD", 0.7))


def pack(samples, aspect, dev):
    """Clips as padded GPU tensors: hands and pose (N, TMAX, P, 2), presence (N, TMAX, 3), length (N,)."""
    n = len(samples)
    lr = torch.zeros(n, TMAX, 42, 2)
    po = torch.zeros(n, TMAX, 33, 2)
    has = torch.zeros(n, TMAX, 3, dtype=torch.bool)
    ln = torch.zeros(n, dtype=torch.long)
    k = np.array([1.0, 1.0 / aspect], np.float32)
    for i, s in enumerate(samples):
        t = len(s["pose"])
        idx = np.arange(t) if t <= TMAX else (np.arange(TMAX) * (t - 1) * 2 + TMAX - 1) // (2 * (TMAX - 1))
        lr[i, :len(idx), :21] = torch.from_numpy(s["left"][idx] * k)
        lr[i, :len(idx), 21:] = torch.from_numpy(s["right"][idx] * k)
        po[i, :len(idx)] = torch.from_numpy(s["pose"][idx] * k)
        has[i, :len(idx)] = torch.from_numpy(np.stack([s["has_left"][idx], s["has_right"][idx], s["has_pose"][idx]], 1))
        ln[i] = len(idx)
    return lr.to(dev), po.to(dev), has.to(dev), ln.to(dev)


ARM_IDX = torch.tensor(ARMS)
FLIP = torch.arange(33)
FLIP[ARMS] = torch.tensor(ARMS_FLIPPED)


def augmented(lr, po, has, ln, g):
    """train.features' augmentation and featurize, batched: (B, FRAMES, FEATURES)."""
    b, dev = len(ln), ln.device
    u = lambda *s: torch.rand(*s, device=dev, generator=g)
    crop = (ln * 15 // 100 + 1).float()
    a = (u(b) * crop).long()
    e = (u(b) * crop).long()
    span = (ln - a - e).clamp(min=1)
    m = (span.float() * (0.8 + 0.4 * u(b))).long().clamp(min=2)
    mmax = int(m.max())
    keep = a[:, None] + (u(b, mmax) * span[:, None]).long()
    keep = torch.where(torch.arange(mmax, device=dev)[None] < m[:, None], keep, torch.full_like(keep, 10 ** 6))
    keep = keep.sort(1).values
    valid = keep < 10 ** 6
    keep = keep.clamp(max=TMAX - 1)
    i = torch.arange(FRAMES, device=dev)
    pick = keep.gather(1, (i[None] * (m[:, None] - 1) * 2 + FRAMES - 1) // (2 * (FRAMES - 1)))
    rows = torch.arange(b, device=dev)[:, None]
    hands, pose, h = lr[rows, pick], po[rows, pick], has[rows, pick]
    kp, kh = po[rows, keep], has[rows, keep, 2] & valid

    flip = u(b) < 0.5
    sx = torch.where(flip, -1.0, 1.0)[:, None, None, None]
    hands = hands * torch.stack([sx[..., 0], torch.ones_like(sx[..., 0])], -1)
    hands = torch.where(flip[:, None, None, None], hands.roll(21, 2), hands)
    pose = pose * torch.stack([sx[..., 0], torch.ones_like(sx[..., 0])], -1)
    pose = torch.where(flip[:, None, None, None], pose[:, :, FLIP.to(dev)], pose)
    kp = kp * torch.stack([sx[..., 0], torch.ones_like(sx[..., 0])], -1)
    kp = torch.where(flip[:, None, None, None], kp[:, :, FLIP.to(dev)], kp)
    h = torch.where(flip[:, None, None], h[..., [1, 0, 2]], h)

    th = (u(b) - 0.5) * 0.4
    sc = 0.9 + 0.2 * u(b, 2)
    c, s = th.cos(), th.sin()
    mt = torch.stack([torch.stack([c * sc[:, 0], s * sc[:, 0]], 1), torch.stack([-s * sc[:, 1], c * sc[:, 1]], 1)], 1)
    mt = mt[:, None]  # points @ m.T as (x, y) @ mt
    hands = hands @ mt
    pose = pose @ mt
    kp = kp @ mt
    hands = hands + 0.003 * torch.randn(hands.shape, device=dev, generator=g)
    if STRONG:  # as train.py: each hand moved and resized against the body
        sw = (kp[:, :, 11] - kp[:, :, 12]).norm(dim=-1).mean(1)[:, None, None, None]
        parts = []
        for part in (hands[:, :, :21], hands[:, :, 21:]):
            c = part.mean(2, keepdim=True)
            parts.append(c + (part - c) * (0.85 + 0.35 * u(b)[:, None, None, None]) + 0.12 * sw * torch.randn(b, 1, 1, 2, device=dev, generator=g))
        hands = torch.cat(parts, 2)
    return featurize(hands, pose, h, kp[:, :, [11, 12]], kh)


def featurize(hands, pose, h, shoulders, sh_ok):
    """features.featurize on tensors; shoulders/sh_ok are over every kept frame, as the centre and scale use."""
    w = sh_ok.float()[..., None]
    cnt = w.sum(1).clamp(min=1)
    center = (((shoulders[:, :, 0] + shoulders[:, :, 1]) / 2) * w).sum(1) / cnt
    scale = ((shoulders[:, :, 0] - shoulders[:, :, 1]).norm(dim=-1) * w[..., 0]).sum(1) / cnt[:, 0]
    ok = (sh_ok.any(1) & (scale > 1e-6)).float()
    scale = scale.clamp(min=1e-6)
    norm = lambda p: (p - center[:, None, None]) / scale[:, None, None, None]
    hf = h.float()
    left = norm(hands[:, :, :21]) * hf[..., 0, None, None]
    right = norm(hands[:, :, 21:]) * hf[..., 1, None, None]
    arms = norm(pose[:, :, ARM_IDX.to(pose.device)]) * hf[..., 2, None, None]
    x = torch.cat([left.flatten(2), right.flatten(2), arms.flatten(2), hf[..., :2]], 2)
    return x * ok[:, None, None]


def main(cache: pathlib.Path, out: pathlib.Path, epochs: int, teachers: list) -> None:
    with open(cache, "rb") as f:
        cache = pickle.load(f)
    aspect, samples = cache["aspect"], cache["samples"]
    labels = sorted({s["label"] for s in samples})
    index = {l: i for i, l in enumerate(labels)}
    train = [s for s in samples if s["split"] == "train"]
    test = [s for s in samples if s["split"] == "test"]
    del cache, samples
    dev = torch.device("cuda")
    torch.manual_seed(int(os.environ.get("SIGN_SEED", 7)))
    g = torch.Generator(dev)
    g.manual_seed(int(os.environ.get("SIGN_SEED", 7)))
    xt = batch(test, aspect).to(dev)
    yt = torch.tensor([index[s["label"]] for s in test], device=dev)
    lr, po, has, ln = pack(train, aspect, dev)
    y = torch.tensor([index[s["label"]] for s in train], device=dev)
    del train
    if teachers:
        nets = []
        for p in teachers:
            ck = torch.load(p, weights_only=True)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch
            net = SignNet(len(labels), hidden=ck["state"]["embed.weight"].shape[0]).to(dev)
            net.load_state_dict(ck["state"])
            nets.append(net.eval())
        print("teachers", [round(evaluate(n, xt, yt)[0], 4) for n in nets], flush=True)
    model = SignNet(len(labels)).to(dev)
    bs = 128
    opt = torch.optim.AdamW(model.parameters(), lr=2e-3, weight_decay=0.05)
    steps = epochs * ((len(y) + bs - 1) // bs)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=2e-3, total_steps=steps)
    ce = nn.CrossEntropyLoss(label_smoothing=0.1)
    for epoch in range(epochs):
        model.train()
        order = torch.randperm(len(y), device=dev, generator=g)
        for k in range(0, len(order), bs):
            ids = order[k:k + bs]
            x = augmented(lr[ids], po[ids], has[ids], ln[ids], g)
            yy = y[ids]
            logits = model(x)
            loss = ce(logits, yy)
            if teachers:
                with torch.no_grad():
                    p = torch.stack([n(x).div(KD_T).softmax(1) for n in nets]).mean(0)
                kd = nn.functional.kl_div(logits.div(KD_T).log_softmax(1), p, reduction="batchmean") * KD_T ** 2
                loss = (1 - KD_W) * loss + KD_W * kd
            opt.zero_grad()
            loss.backward()
            opt.step()
            sched.step()
        if epoch % 25 == 24 or epoch == epochs - 1:
            top1, top3 = evaluate(model, xt, yt)
            print(f"epoch {epoch + 1}: loss {loss.item():.3f} test top-1 {top1:.4f} top-3 {top3:.4f}", flush=True)
    torch.save({"state": model.cpu().state_dict(), "labels": labels, "top1": top1, "top3": top3, "epochs": epochs}, out)  # nosemgrep: trailofbits.python.pickles-in-pytorch.pickles-in-pytorch


if __name__ == "__main__":
    a = sys.argv[1:]
    main(pathlib.Path(a[0]), pathlib.Path(a[1]), int(a[2]), a[3:])
