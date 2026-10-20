#!/usr/bin/env python3
"""Static audit of Android application IDs, namespaces, versions, and signing setup."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {".git", "build", ".gradle"}
PATTERNS = {
    "applicationId": re.compile(r"""applicationId\s+['"]([^'"]+)['"]"""),
    "namespace": re.compile(r"""namespace\s+['"]([^'"]+)['"]"""),
    "versionCode": re.compile(r"""versionCode\s+(\d+)"""),
    "versionName": re.compile(r"""versionName\s+['"]([^'"]+)['"]"""),
}


def main() -> int:
    issues: list[tuple[str, str]] = []
    apps: list[dict[str, str]] = []
    for gradle in sorted(ROOT.rglob("app/build.gradle")):
        if any(part in EXCLUDED for part in gradle.parts):
            continue
        text = gradle.read_text(encoding="utf-8", errors="replace")
        item = {"path": str(gradle.relative_to(ROOT))}
        for key, pattern in PATTERNS.items():
            match = pattern.search(text)
            if match:
                item[key] = match.group(1)
        if "applicationId" not in item:
            issues.append(("ERROR", f"{item['path']}: applicationId not found statically"))
        if "namespace" not in item:
            issues.append(("WARN", f"{item['path']}: namespace not found statically"))
        if "versionCode" not in item:
            issues.append(("ERROR", f"{item['path']}: versionCode not found statically"))
        elif int(item["versionCode"]) < 1:
            issues.append(("ERROR", f"{item['path']}: versionCode must be >= 1"))
        if item.get("applicationId") and item.get("namespace") and item["applicationId"] != item["namespace"]:
            issues.append(("INFO", f"{item['path']}: applicationId differs from namespace; this is allowed and is not automatically an error"))
        if "signingConfigs" in text and "System.getenv(" in text:
            issues.append(("INFO", f"{item['path']}: signing appears environment-driven; confirm release secrets and certificate fingerprint in CI"))
        apps.append(item)

    seen: dict[str, str] = {}
    for app in apps:
        app_id = app.get("applicationId")
        if not app_id:
            continue
        if app_id in seen:
            issues.append(("ERROR", f"duplicate applicationId {app_id}: {seen[app_id]} and {app['path']}"))
        else:
            seen[app_id] = app["path"]

    for level, message in issues:
        print(f"{level}: {message}")
    errors = sum(level == "ERROR" for level, _ in issues)
    warnings = sum(level == "WARN" for level, _ in issues)
    print(f"\nANDROID PACKAGE AUDIT: {'FAIL' if errors else 'PASS'} | apps={len(apps)} errors={errors} warnings={warnings}")
    for app in apps:
        print("APP: " + " | ".join(f"{k}={v}" for k, v in app.items()))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
