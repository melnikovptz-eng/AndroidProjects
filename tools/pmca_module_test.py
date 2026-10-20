#!/usr/bin/env python3
"""Validate PMCA module passports and basic dependency/entry-point consistency.

Uses only the Python standard library. It does not execute Android code or replace
Gradle unit/instrumentation tests.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = ("schema_version", "module_id", "name", "version", "api_version",
            "type", "category", "status", "description", "capabilities",
            "dependencies", "limitations")
SEMVER = re.compile(r"^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$")


def emit(level: str, path: str, message: str, issues: list[dict[str, str]]) -> None:
    issues.append({"level": level, "path": path, "message": message})
    print(f"{level}: {path}: {message}")


def entrypoints_for(data: dict[str, Any]) -> list[str]:
    result: list[str] = []
    for key in ("entrypoint", "entrypoints"):
        value = data.get(key)
        if isinstance(value, str):
            result.append(value)
        elif isinstance(value, list):
            result.extend(v for v in value if isinstance(v, str))
    return result


def java_or_kotlin_exists(package_name: str) -> bool:
    if not package_name or not re.fullmatch(r"[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*", package_name):
        return False
    relative = Path(*package_name.split("."))
    roots = [
        ROOT / "GPTSenderTest/app/src/main/java",
        ROOT / "GPTSenderTest/app/src/main/kotlin",
        ROOT / "PMCA/src/main/java",
        ROOT / "PMCA/src/main/kotlin",
    ]
    return any((base / relative).with_suffix(ext).is_file()
               for base in roots for ext in (".java", ".kt"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--all", action="store_true", help="check all module.json files in repository")
    group.add_argument("--module", help="module directory or path to module.json")
    parser.add_argument("--strict", action="store_true",
                        help="treat missing test files/docs as errors")
    parser.add_argument("--json", action="store_true", help="print machine-readable JSON summary")
    args = parser.parse_args()

    if args.all:
        passports = sorted(ROOT.rglob("module.json"))
        passports = [p for p in passports if not any(part in {".git", "build", ".gradle"} for part in p.parts)]
    else:
        candidate = (ROOT / args.module).resolve()
        if candidate.is_dir():
            candidate = candidate / "module.json"
        passports = [candidate]

    issues: list[dict[str, str]] = []
    modules: dict[str, dict[str, Any]] = {}
    parsed: list[tuple[Path, dict[str, Any]]] = []

    for passport in passports:
        try:
            passport.relative_to(ROOT)
        except ValueError:
            emit("ERROR", str(passport), "passport must be inside the repository", issues)
            continue
        label = str(passport.relative_to(ROOT))
        if not passport.is_file():
            emit("ERROR", label, "module.json not found", issues)
            continue
        try:
            data = json.loads(passport.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            emit("ERROR", label, f"invalid JSON: {exc}", issues)
            continue
        if not isinstance(data, dict):
            emit("ERROR", label, "top-level JSON value must be an object", issues)
            continue
        parsed.append((passport, data))
        for key in REQUIRED:
            if key not in data:
                emit("ERROR", label, f"missing required field: {key}", issues)
        for key in ("module_id", "name", "version", "api_version", "description", "type", "category", "status"):
            if key in data and (not isinstance(data[key], str) or not data[key].strip()):
                emit("ERROR", label, f"{key} must be a non-empty string", issues)
        for key in ("capabilities", "dependencies", "limitations"):
            if key in data and (not isinstance(data[key], list) or not all(isinstance(x, str) and x.strip() for x in data[key])):
                emit("ERROR", label, f"{key} must be an array of non-empty strings", issues)
        if isinstance(data.get("version"), str) and not SEMVER.fullmatch(data["version"]):
            emit("ERROR", label, "version must use MAJOR.MINOR.PATCH format", issues)
        if isinstance(data.get("api_version"), str) and not re.fullmatch(r"^\d+\.\d+(?:\.\d+)?(?:[-+][0-9A-Za-z.-]+)?$", data["api_version"]):
            emit("ERROR", label, "api_version must use MAJOR.MINOR or MAJOR.MINOR.PATCH format", issues)
        if data.get("schema_version") != "1.0":
            emit("ERROR", label, "unsupported or missing schema_version (currently 1.0)", issues)
        mid = data.get("module_id")
        if isinstance(mid, str) and mid:
            if mid in modules:
                emit("ERROR", label, f"duplicate module_id {mid!r}; also used by {modules[mid]['path']}", issues)
            else:
                modules[mid] = {"path": label, "data": data}

        eps = entrypoints_for(data)
        if not eps:
            emit("WARN" if not args.strict else "ERROR", label,
                 "no entrypoint/entrypoints declared; add implementation class names", issues)
        for ep in eps:
            if not java_or_kotlin_exists(ep):
                emit("WARN" if not args.strict else "ERROR", label,
                     f"entry point source not found in known Android source roots: {ep}", issues)

        module_dir = passport.parent
        has_tests = any(p.is_file() for folder in ("src/test", "src/androidTest", "tests")
                        for p in (module_dir / folder).rglob("*")) if any(
                            (module_dir / folder).exists() for folder in ("src/test", "src/androidTest", "tests")) else False
        has_readme = (module_dir / "README.md").is_file()
        if not has_tests:
            emit("WARN" if not args.strict else "ERROR", label,
                 "no module-local tests found (src/test, src/androidTest, or tests)", issues)
        if not has_readme:
            emit("WARN" if not args.strict else "ERROR", label,
                 "no adjacent README.md; document purpose, I/O, failures, and manual test steps", issues)

    # Detect dependency cycles only where dependency strings explicitly name module IDs.
    graph: dict[str, list[str]] = {}
    for mid, item in modules.items():
        deps = item["data"].get("dependencies", [])
        graph[mid] = [dep for dep in deps if isinstance(dep, str) and dep in modules]
    visiting: list[str] = []
    visited: set[str] = set()

    def visit(node: str) -> None:
        if node in visiting:
            cycle = visiting[visiting.index(node):] + [node]
            emit("ERROR", modules[node]["path"], "dependency cycle: " + " -> ".join(cycle), issues)
            return
        if node in visited:
            return
        visiting.append(node)
        for dep in graph.get(node, []):
            visit(dep)
        visiting.pop()
        visited.add(node)

    for node in graph:
        visit(node)

    errors = sum(i["level"] == "ERROR" for i in issues)
    warnings = sum(i["level"] == "WARN" for i in issues)
    summary = {"checked_passports": len(parsed), "errors": errors, "warnings": warnings,
               "result": "FAIL" if errors else "PASS"}
    print(f"\nPMCA MODULE TEST: {summary['result']} | passports={len(parsed)} errors={errors} warnings={warnings}")
    if args.json:
        print(json.dumps({"summary": summary, "issues": issues}, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
