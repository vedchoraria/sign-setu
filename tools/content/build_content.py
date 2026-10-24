"""Renders picture cards and writes the content hash manifest. See tools/README.md for the flags."""
import hashlib
import io
import json
import pathlib
import sys

import resvg_py
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
SYMBOLS = ROOT / "content" / "symbols"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "content"
CARD_PX = 320


def render_cards(only: set[str] | None = None) -> None:
    mapping = json.loads((SYMBOLS / "map.json").read_text(encoding="utf-8"))
    cards = json.loads((ASSETS / "cards.json").read_text(encoding="utf-8"))["cards"]
    if only:
        cards = [c for c in cards if c["id"] in only]
    missing = [c["id"] for c in cards if c["id"] not in mapping]
    if missing:
        sys.exit(f"cards without a symbol in map.json: {missing}")
    out = ASSETS / "cards"
    out.mkdir(parents=True, exist_ok=True)
    for card in cards:
        svg = SYMBOLS / mapping[card["id"]]
        png = bytes(resvg_py.svg_to_bytes(svg_path=str(svg), width=CARD_PX, height=CARD_PX))
        Image.open(io.BytesIO(png)).save(out / f"{card['id']}.webp", "WEBP", quality=88, method=6)
    print(f"rendered {len(cards)} cards")


def normalise_text() -> None:
    # Git checks text out with LF (.gitattributes); hashing LF bytes keeps the manifest valid on every clone.
    for path in ASSETS.rglob("*.json"):
        raw = path.read_bytes()
        if b"\r\n" in raw:
            path.write_bytes(raw.replace(b"\r\n", b"\n"))


def current_manifest() -> dict:
    files = {}
    for path in sorted(ASSETS.rglob("*")):
        if path.is_file() and path.name != "manifest.json":
            files[path.relative_to(ASSETS).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    return {"version": 1, "files": files}


def write_manifest() -> None:
    normalise_text()
    manifest = current_manifest()
    (ASSETS / "manifest.json").write_bytes(json.dumps(manifest, indent=1, sort_keys=True).encode("utf-8"))
    print(f"manifest: {len(manifest['files'])} files")


def check_manifest() -> bool:
    """True when the checked-in manifest matches the files on disk. Never normalises or writes, so CI can use it."""
    manifest = current_manifest()
    try:
        existing = json.loads((ASSETS / "manifest.json").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        print("manifest.json is missing or unreadable; run build_content.py --manifest-only")
        return False
    if existing == manifest:
        print(f"manifest is current: {len(manifest['files'])} files")
        return True
    old = existing.get("files", {}) if isinstance(existing, dict) else {}
    new = manifest["files"]
    added = sorted(set(new) - set(old))
    removed = sorted(set(old) - set(new))
    changed = sorted(k for k in set(old) & set(new) if old[k] != new[k])
    print("manifest is stale; run build_content.py --manifest-only")
    for label, paths in (("added", added), ("removed", removed), ("changed", changed)):
        print(f"  {label}: {', '.join(paths) if paths else '-'}")
    return False


if __name__ == "__main__":
    if "--check" in sys.argv:
        sys.exit(0 if check_manifest() else 1)
    if "--only" in sys.argv:
        render_cards(set(sys.argv[sys.argv.index("--only") + 1].split(",")))
    elif "--manifest-only" not in sys.argv:
        render_cards()
    write_manifest()
