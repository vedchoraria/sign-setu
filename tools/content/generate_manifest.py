#!/usr/bin/env python3
"""
Scans app/src/main/assets/content/ and generates or updates manifest.json.
Calculates SHA-256 digests for all asset files (json, ogg, webp) for runtime
integrity validation (SEC-54) and outputs the overall release manifest hash.
Runs fully offline with zero external dependencies.
"""

import hashlib
import json
import pathlib
import sys

CONTENT_DIR = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "content"
MANIFEST_PATH = CONTENT_DIR / "manifest.json"


def sha256_file(path: pathlib.Path) -> str:
    hasher = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def generate_manifest(write_mode: bool = False):
    if not CONTENT_DIR.exists():
        print(f"Error: Content directory not found at {CONTENT_DIR}", file=sys.stderr)
        sys.exit(1)

    file_hashes = {}
    for file_path in sorted(CONTENT_DIR.rglob("*")):
        if file_path.is_file() and file_path != MANIFEST_PATH:
            rel_path = file_path.relative_to(CONTENT_DIR).as_posix()
            file_hashes[rel_path] = sha256_file(file_path)

    # Sort keys strictly by ASCII byte order (e.g. '.' before '/')
    sorted_files = dict(sorted(file_hashes.items(), key=lambda item: item[0]))

    manifest_data = {
        "files": sorted_files,
        "version": 1
    }

    if write_mode:
        with open(MANIFEST_PATH, "w", encoding="utf-8", newline="\n") as f:
            json.dump(manifest_data, f, indent=1)
        print(f"Manifest written to: {MANIFEST_PATH}")
    else:
        # Verification mode
        if not MANIFEST_PATH.exists():
            print(f"Error: {MANIFEST_PATH} does not exist", file=sys.stderr)
            sys.exit(1)
        with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
            existing = json.load(f)
        existing_files = existing.get("files", {})
        mismatches = []
        for path, expected_hash in existing_files.items():
            actual_hash = file_hashes.get(path)
            if actual_hash != expected_hash:
                mismatches.append((path, expected_hash, actual_hash))
        
        missing = set(file_hashes.keys()) - set(existing_files.keys())
        orphaned = set(existing_files.keys()) - set(file_hashes.keys())

        if mismatches or missing or orphaned:
            print(f"Verification FAILED: {len(mismatches)} hash mismatches, {len(missing)} unindexed, {len(orphaned)} missing files.")
            sys.exit(1)
        else:
            print(f"Verification PASSED: All {len(existing_files)} content files match manifest.json exactly.")

    manifest_hash = sha256_file(MANIFEST_PATH)
    with open(MANIFEST_PATH, "rb") as f:
        content_bytes = f.read()
    version_tag = hashlib.sha256(content_bytes).hexdigest()[:12]

    print(f"Total content files: {len(file_hashes)}")
    print(f"Content Manifest SHA-256: {manifest_hash}")
    print(f"Version Tag (first 12 hex chars): {version_tag}")


if __name__ == "__main__":
    write_flag = "--write" in sys.argv
    generate_manifest(write_mode=write_flag)
