"""Feature extraction shared by training and export; LandmarkSignReader.kt must match it exactly. Input is Holistic
normalised image points: two hands of 21 points and a pose of 33, each possibly missing."""
import numpy as np

FRAMES = 32
ARMS = [11, 12, 13, 14, 15, 16]  # shoulders, elbows, wrists
ARMS_FLIPPED = [12, 11, 14, 13, 16, 15]
FEATURES = 21 * 2 * 2 + len(ARMS) * 2 + 2  # left hand, right hand, arms, two presence flags


def resample_index(n: int, frames: int = FRAMES) -> np.ndarray:
    """Nearest source frame for each output frame, in integer arithmetic so Kotlin rounds identically."""
    i = np.arange(frames)
    return (i * (n - 1) * 2 + (frames - 1)) // (2 * (frames - 1))


def isotropic(left, right, pose, aspect: float):
    """Scales y by height/width so both axes share one unit; aspect is frame width / height."""
    k = np.array([1.0, 1.0 / aspect])
    return left * k, right * k, pose * k


def featurize(left, right, pose, has_left, has_right, has_pose):
    """left, right: (N, 21, 2); pose: (N, 33, 2) isotropic; has_*: (N,) bool. Returns (FRAMES, FEATURES) or None."""
    if not has_pose.any():
        return None
    p = pose[has_pose]
    center = ((p[:, 11] + p[:, 12]) / 2).mean(0)
    scale = np.linalg.norm(p[:, 11] - p[:, 12], axis=1).mean()
    if scale < 1e-6:
        return None
    idx = resample_index(len(pose))
    out = np.zeros((FRAMES, FEATURES), np.float32)
    for t, j in enumerate(idx):
        if has_left[j]:
            out[t, 0:42] = ((left[j] - center) / scale).reshape(-1)
            out[t, 96] = 1
        if has_right[j]:
            out[t, 42:84] = ((right[j] - center) / scale).reshape(-1)
            out[t, 97] = 1
        if has_pose[j]:
            out[t, 84:96] = ((pose[j, ARMS] - center) / scale).reshape(-1)
    return out


def featurize_hands(left, right, pose, has_left, has_right, has_pose):
    """Like featurize, but centred on all hand points and scaled by the mean wrist to middle-knuckle length, so close-ups
    without shoulders read the same; arm features stay zero."""
    present = [h[m] for h, m in ((left, has_left), (right, has_right)) if m.any()]
    if not present:
        return None
    pts = np.concatenate(present)
    center = pts.reshape(-1, 2).mean(0)
    scale = np.linalg.norm(pts[:, 0] - pts[:, 9], axis=1).mean()
    if scale < 1e-6:
        return None
    idx = resample_index(len(left))
    out = np.zeros((FRAMES, FEATURES), np.float32)
    for t, j in enumerate(idx):
        if has_left[j]:
            out[t, 0:42] = ((left[j] - center) / scale).reshape(-1)
            out[t, 96] = 1
        if has_right[j]:
            out[t, 42:84] = ((right[j] - center) / scale).reshape(-1)
            out[t, 97] = 1
    return out


# Format 2, the fingerspelling reader (CtcSpellReader.kt must match): one hand per frame at a fixed rate.
SPELL_FPS = 15
SPELL_MAX = 300  # 20 s, the sign window's limit
SPELL_FEATURES = 21 * 2 + 2 + 1  # hand points from the wrist, wrist motion, presence


def spell_resample(ts) -> np.ndarray:
    """Source frame for each output frame at SPELL_FPS by nearest timestamp (ms, ties to the earlier frame)."""
    ts = np.asarray(ts, np.int64)
    n = min(int((ts[-1] - ts[0]) * SPELL_FPS // 1000) + 1, SPELL_MAX)
    out, i = np.zeros(n, int), 0
    for k in range(n):
        target = ts[0] + k * 1000 // SPELL_FPS
        while i + 1 < len(ts) and abs(ts[i + 1] - target) < abs(ts[i] - target):
            i += 1
        out[k] = i
    return out


def spell_features(ts, left, right, has_left, has_right, aspect: float) -> np.ndarray:
    """One hand per frame (the one seen more, a left mirrored to a right), from the wrist in wrist to middle-knuckle
    lengths, plus the wrist's step since the previous frame. Returns (frames, SPELL_FEATURES)."""
    idx = spell_resample(ts)
    use_left = has_left[idx].sum() > has_right[idx].sum()
    hand, has = (left, has_left) if use_left else (right, has_right)
    k = np.array([-1.0 if use_left else 1.0, 1.0 / aspect])
    out = np.zeros((len(idx), SPELL_FEATURES), np.float32)
    prev = None
    for t, j in enumerate(idx):
        p = hand[j] * k if has[j] else None
        size = float(np.hypot(*(p[9] - p[0]))) if p is not None else 0.0
        if size < 1e-6:
            prev = None
            continue
        out[t, :42] = ((p - p[0]) / size).reshape(-1)
        if prev is not None:
            out[t, 42:44] = (p[0] - prev) / size
        out[t, 44] = 1
        prev = p[0]
    return out
