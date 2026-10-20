#!/usr/bin/env python3
"""Static review aid for duplicate Android services and obvious unbounded loops."""
from __future__ import annotations

import hashlib
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {".git", "build", ".gradle", "node_modules"}
ANDROID = "{http://schemas.android.com/apk/res/android}"
UNBOUNDED = (
    re.compile(r"\bwhile\s*\(\s*true\s*\)"),
    re.compile(r"\bwhile\s+true\s*;\s*do\b"),
    re.compile(r"\bwhile\s*:\s*;\s*do\b"),
)


def ignored(path: Path) -> bool:
    try:
        parts = path.relative_to(ROOT).parts
    except ValueError:
        return True
    return any(part in EXCLUDED for part in parts)


def main() -> int:
    errors: list[str] = []
    warnings: list[str] = []
    manifests = sorted(p for p in ROOT.rglob("AndroidManifest.xml") if not ignored(p))
    for manifest in manifests:
        try:
            root = ET.parse(manifest).getroot()
        except (ET.ParseError, OSError) as exc:
            errors.append(f"{manifest.relative_to(ROOT)}: invalid manifest XML ({type(exc).__name__})")
            continue
        seen: set[str] = set()
        for service in root.findall(".//service"):
            name = service.attrib.get(ANDROID + "name", "").strip()
            if not name:
                continue
            if name in seen:
                errors.append(f"{manifest.relative_to(ROOT)}: duplicate service declaration {name}")
            seen.add(name)

    daemon_files: dict[str, list[Path]] = {}
    for path in ROOT.rglob("*"):
        if not path.is_file() or ignored(path) or path.suffix.lower() not in {".sh", ".bash"}:
            continue
        if re.search(r"(daemon|bridge|watchdog)", path.name, re.IGNORECASE):
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            daemon_files.setdefault(digest, []).append(path)
    for paths in daemon_files.values():
        if len(paths) > 1:
            warnings.append("byte-identical daemon/bridge scripts: " +
                            ", ".join(str(p.relative_to(ROOT)) for p in paths))

    source_suffixes = {".java", ".kt", ".sh", ".bash"}
    for path in ROOT.rglob("*"):
        if not path.is_file() or ignored(path) or path.suffix.lower() not in source_suffixes:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for pattern in UNBOUNDED:
            if pattern.search(text):
                warnings.append(
                    f"{path.relative_to(ROOT)}: infinite-loop pattern requires review for bounded exit/cancellation"
                )
                break

    for message in errors:
        print("ERROR: " + message)
    for message in warnings:
        print("WARN: " + message)
    print(f"\nPMCA RUNTIME AUDIT: {'FAIL' if errors else 'PASS'} | manifests={len(manifests)} "
          f"errors={len(errors)} warnings={len(warnings)}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
