#!/usr/bin/env python3
"""Validate PMCA module passports, source entry points, dependencies, docs, and tests.

Standard-library only. This is a static contract check, not an Android build or
proof that runtime behavior is correct.
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
JAVA_ID = re.compile(r"^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*$")
EXCLUDED = {".git", "build", ".gradle", ".idea", "node_modules"}


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


def source_roots() -> list[Path]:
    # Discover Gradle source roots across the repository, including nested
    # projects such as PMCA/AndroidBridge; do not maintain a brittle allowlist.
    roots: list[Path] = []
    for base in ROOT.rglob("src/main"):
        try:
            rel = base.relative_to(ROOT)
        except ValueError:
            continue
        if any(part in EXCLUDED for part in rel.parts):
            continue
        for language in ("java", "kotlin"):
            candidate = base / language
            if candidate.is_dir():
                roots.append(candidate)
    return roots


def entrypoint_exists(package_name: str, roots: list[Path]) -> bool:
    if not package_name or not JAVA_ID.fullmatch(package_name):
        return False
    relative = Path(*package_name.split("."))
    return any((base / relative).with_suffix(ext).is_file()
               for base in roots for ext in (".java", ".kt"))


def dependency_ids(data: dict[str, Any]) -> list[str]:
    """Read explicit dependency IDs while preserving legacy prose dependencies."""
    result: list[str] = []
    explicit = data.get("dependency_ids", [])
    if isinstance(explicit, list):
        result.extend(x.strip() for x in explicit if isinstance(x, str) and x.strip())
    deps = data.get("dependencies", [])
    if isinstance(deps, list):
        for dep in deps:
            if isinstance(dep, dict):
                value = dep.get("module_id") or dep.get("id")
                if isinstance(value, str) and value.strip():
                    result.append(value.strip())
            elif isinstance(dep, str) and dep.strip() and dep.strip().startswith("pmca."):
                # Backward-compatible recognition of canonical PMCA IDs only.
                result.append(dep.strip())
    return list(dict.fromkeys(result))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--all", action="store_true", help="check all module.json files")
    group.add_argument("--module", help="module directory or path to module.json")
    parser.add_argument("--strict", action="store_true",
                        help="promote all contract warnings to errors")
    parser.add_argument("--json", action="store_true", help="print JSON summary")
    args = parser.parse_args()

    if args.all:
        passports = sorted(p for p in ROOT.rglob("module.json")
                           if not any(part in EXCLUDED for part in p.parts))
    else:
        candidate = (ROOT / args.module).resolve()
        if candidate.is_dir():
            candidate = candidate / "module.json"
        passports = [candidate]

    issues: list[dict[str, str]] = []
    modules: dict[str, dict[str, Any]] = {}
    parsed: list[tuple[Path, dict[str, Any]]] = []
    roots = source_roots()

    def warn(path: str, message: str) -> None:
        emit("ERROR" if args.strict else "WARN", path, message, issues)

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
        for key in ("module_id", "name", "version", "api_version", "description",
                    "type", "category", "status"):
            if key in data and (not isinstance(data[key], str) or not data[key].strip():
                                ):
                emit("ERROR", label, f"{key} must be a non-empty string", issues)
        for key in ("capabilities", "dependencies", "limitations"):
            value = data.get(key)
            if key in data and (not isinstance(value, list) or not all(
                    (isinstance(x, str) and x.strip()) or
                    (key == "dependencies" and isinstance(x, dict) and
                     isinstance(x.get("module_id") or x.get("id"), str) and
                     bool((x.get("module_id") or x.get("id")).strip()))
                    for x in value)):
                emit("ERROR", label, f"{key} must be an array of non-empty strings"
                     + (" or dependency objects" if key == "dependencies" else ""), issues)
        if isinstance(data.get("version"), str) and not SEMVER.fullmatch(data["version"]):
            emit("ERROR", label, "version must use MAJOR.MINOR.PATCH format", issues)
        if isinstance(data.get("api_version"), str) and not re.fullmatch(
                r"^\d+\.\d+(?:\.\d+)?(?:[-+][0-9A-Za-z.-]+)?$", data["api_version"]):
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
            warn(label, "no entrypoint/entrypoints declared; document implementation class names")
        for ep in eps:
            if not entrypoint_exists(ep, roots):
                warn(label, f"entry point source not found in discovered Android source roots: {ep}")

        module_dir = passport.parent
        test_dirs = [module_dir / name for name in ("src/test", "src/androidTest", "tests")]
        has_tests = any(folder.is_dir() and any(p.is_file() for p in folder.rglob("*"))
                        for folder in test_dirs)
        if not has_tests:
            warn(label, "no module-local tests found (src/test, src/androidTest, or tests)")
        if not (module_dir / "README.md").is_file():
            warn(label, "no adjacent README.md; document purpose, I/O, failures, and manual test steps")

        for key in ("inputs", "outputs", "permissions", "limits", "tests"):
            if key not in data:
                warn(label, f"recommended passport field missing: {key}")

    # Validate explicit machine-readable dependencies and detect cycles.
    graph: dict[str, list[str]] = {}
    for mid, item in modules.items():
        ids = dependency_ids(item["data"])
        graph[mid] = []
        for dep in ids:
            if dep not in modules:
                emit("ERROR", item["path"], f"unknown dependency module_id: {dep}", issues)
            else:
                graph[mid].append(dep)

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
    summary = {"checked_passports": len(parsed), "source_roots": len(roots),
               "errors": errors, "warnings": warnings,
               "result": "FAIL" if errors else "PASS"}
    print(f"\nPMCA MODULE TEST: {summary['result']} | passports={len(parsed)} "
          f"source_roots={len(roots)} errors={errors} warnings={warnings}")
    if args.json:
        print(json.dumps({"summary": summary, "issues": issues}, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
