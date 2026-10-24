"""Placeholder speech for the content bundle with Indic Parler-TTS, until fluent speakers record it. See tools/README.md."""
import json
import pathlib
import sys

import numpy as np
import soundfile as sf
import torch
from parler_tts import ParlerTTSForConditionalGeneration
from scipy.signal import resample_poly
from transformers import AutoTokenizer

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "content"
OUT_RATE = 24_000  # Opus-supported rate; plenty for speech

# Named voices from the model card where known; otherwise the description alone picks a voice.
VOICES = {
    "hi": "Divya", "bn": "Aditi", "ne": "Amrita", "as": "Sita", "or": "Debjani", "ta": "Jaya",
    "te": "Lalitha", "kn": "Anu", "ml": "Anjali", "mr": "Sunita", "gu": "Neha", "pa": "Gurpreet", "en": "Mary",
}
# Indic Parler-TTS has no Gondi or Halbi voice; those subjects get captions and pictures until volunteers record them.
UNVOICED = {"gon", "hlb"}
STYLE = "speaks in a calm, warm and slow voice, close to the microphone, with very clear audio and no background noise."


def jobs():
    languages = json.loads((ASSETS / "languages.json").read_text(encoding="utf-8"))["languages"]
    for lang in languages:
        yield lang["code"], lang["greeting"], ASSETS / "audio" / "greet" / f"{lang['code']}.ogg"
    triage = json.loads((ASSETS / "triage.json").read_text(encoding="utf-8"))["questions"]
    for q in triage:
        for code, text in q["text"].items():
            yield code, text, ASSETS / "audio" / code / f"{q['id']}.ogg"
    subject_ui = json.loads((ASSETS / "subject_ui.json").read_text(encoding="utf-8"))
    for code, text in subject_ui["is_this_it"].items():
        yield code, text, ASSETS / "audio" / code / "is_this_it.ogg"
    phrases = json.loads((ASSETS / "phrases.json").read_text(encoding="utf-8"))["phrases"]
    for p in phrases:
        for slot in p["slots"]:
            for code, text in slot["text"].items():
                yield code, text, ASSETS / "audio" / code / f"phrase.{p['id']}.{slot['card']}.ogg"


def main(force: bool) -> None:
    todo = [j for j in jobs() if j[0] not in UNVOICED and (force or not j[2].exists())]
    if not todo:
        print("nothing to generate")
        return
    device = "cuda:0" if torch.cuda.is_available() else "cpu"
    model = ParlerTTSForConditionalGeneration.from_pretrained("ai4bharat/indic-parler-tts").to(device)
    prompt_tok = AutoTokenizer.from_pretrained("ai4bharat/indic-parler-tts")
    desc_tok = AutoTokenizer.from_pretrained(model.config.text_encoder._name_or_path)
    rate = model.config.sampling_rate
    for code, text, path in todo:
        speaker = VOICES.get(code)
        description = f"{speaker} {STYLE}" if speaker else f"A female speaker {STYLE}"
        d = desc_tok(description, return_tensors="pt").to(device)
        p = prompt_tok(text, return_tensors="pt").to(device)
        # Same seed for consistent takes; the model sometimes returns a near-empty take, so try the next seed.
        for seed in (7, 8, 9):
            torch.manual_seed(seed)
            with torch.no_grad():
                audio = model.generate(
                    input_ids=d.input_ids, attention_mask=d.attention_mask,
                    prompt_input_ids=p.input_ids, prompt_attention_mask=p.attention_mask,
                )
            wav = trim(audio.cpu().numpy().squeeze().astype(np.float32), rate)
            if len(wav) / rate >= 0.8:
                break
        wav = resample_poly(wav, OUT_RATE, rate).astype(np.float32)
        wav = 0.89 * wav / max(1e-6, float(np.max(np.abs(wav))))
        path.parent.mkdir(parents=True, exist_ok=True)
        sf.write(path, wav, OUT_RATE, format="OGG", subtype="OPUS")
        print(f"{path.relative_to(ASSETS)}  {len(wav) / OUT_RATE:.1f}s")


def trim(wav: np.ndarray, rate: int, threshold: float = 0.01, pad_s: float = 0.15) -> np.ndarray:
    loud = np.where(np.abs(wav) > threshold * np.max(np.abs(wav)))[0]
    if loud.size == 0:
        return wav
    pad = int(pad_s * rate)
    return wav[max(0, loud[0] - pad): loud[-1] + pad]


if __name__ == "__main__":
    main(force="--force" in sys.argv)
