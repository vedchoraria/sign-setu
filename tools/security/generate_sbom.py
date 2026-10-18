#!/usr/bin/env python3
"""
Generates a CycloneDX 1.5 JSON Software Bill of Materials (SBOM) for Sign Setu.
Parses app/gradle.lockfile to extract all pinned Gradle dependencies and
includes vendored C++ native components (llama.cpp, SQLCipher) and vision models.
Designed for CERT-In empanelled security audit compliance (OWASP MASVS-L2 / SEC-50).
Runs fully offline with zero network calls.
"""

import json
import pathlib
import sys
import uuid
from datetime import datetime, timezone

PROJECT_ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCKFILE_PATH = PROJECT_ROOT / "app" / "gradle.lockfile"
OUTPUT_SBOM_PATH = PROJECT_ROOT / "build" / "reports" / "cyclonedx-sbom.json"

VENDORED_COMPONENTS = [
    {
        "type": "library",
        "name": "llama.cpp",
        "version": "0.5.0",
        "description": "Port of Facebook's LLaMA model in C/C++ vendored for offline Gemma 3 inference",
        "purl": "pkg:generic/llama.cpp@0.5.0",
        "scope": "optional",
        "hashes": [
            {
                "alg": "SHA-256",
                "content": "7fe450e82c161eb3a778cbe464a9386d79a29e469502931a98099684347781b0"
            }
        ]
    },
    {
        "type": "library",
        "name": "sqlcipher-android",
        "group": "net.zetetic",
        "version": "4.19.1",
        "description": "Full Database Encryption for SQLite on Android",
        "purl": "pkg:maven/net.zetetic/sqlcipher-android@4.19.1",
        "scope": "required"
    },
    {
        "type": "file",
        "name": "holistic_landmarker.task",
        "version": "1.0.0",
        "description": "MediaPipe Holistic landmark extraction model bundle (face, hands, pose)",
        "purl": "pkg:generic/mediapipe/holistic_landmarker@1.0.0",
        "scope": "required"
    }
]


def parse_lockfile(lockfile_path: pathlib.Path):
    components = {}
    if not lockfile_path.exists():
        print(f"Error: Lockfile not found at {lockfile_path}", file=sys.stderr)
        return []

    with open(lockfile_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue

            # Format: group:name:version=classpath1,classpath2,...
            if "=" in line:
                coord, classpaths = line.split("=", 1)
                parts = coord.split(":")
                if len(parts) == 3:
                    group, name, version = parts
                    key = f"{group}:{name}"
                    
                    # Determine scope: test vs runtime
                    is_test_only = all("Test" in cp for cp in classpaths.split(","))
                    scope = "excluded" if is_test_only else "required"

                    if key not in components or scope == "required":
                        components[key] = {
                            "type": "library",
                            "group": group,
                            "name": name,
                            "version": version,
                            "purl": f"pkg:maven/{group}/{name}@{version}",
                            "scope": scope
                        }

    return list(components.values())


def generate_sbom():
    maven_components = parse_lockfile(LOCKFILE_PATH)
    all_components = VENDORED_COMPONENTS + sorted(maven_components, key=lambda c: (c.get("group", ""), c["name"]))

    sbom = {
        "$schema": "http://cyclonedx.org/schema/bom-1.5.schema.json",
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "serialNumber": f"urn:uuid:{uuid.uuid4()}",
        "version": 1,
        "metadata": {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "tools": [
                {
                    "vendor": "Sign Setu Core Security Engineering",
                    "name": "generate_sbom.py",
                    "version": "1.0.0"
                }
            ],
            "component": {
                "type": "application",
                "group": "in.signsetu",
                "name": "sign-setu",
                "version": "0.2.0",
                "description": "Offline-first communication aid for police triage with deaf and non-verbal individuals."
            }
        },
        "components": all_components
    }

    OUTPUT_SBOM_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_SBOM_PATH, "w", encoding="utf-8") as f:
        json.dump(sbom, f, indent=2)

    print(f"CycloneDX 1.5 SBOM generated successfully with {len(all_components)} components.")
    print(f"Output: {OUTPUT_SBOM_PATH}")


if __name__ == "__main__":
    generate_sbom()
