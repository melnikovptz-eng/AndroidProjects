# PMCA / AndroidProjects project rules

This document is the project-wide source of truth for contribution workflow. Module-specific details belong in `docs/MODULE_DEVELOPMENT.md` and the module's own README/passport.

## Mandatory AI preflight: read rules before work

Before creating a new module, app, adapter, workflow, or making a substantial change to an existing one, the AI/developer must read the current versions of **all** applicable project rules before planning or editing code. Reading only the file that appears relevant is not sufficient.

Required baseline reading:
1. `README.md` — repository purpose and current entry points.
2. `docs/PROJECT_RULES.md` — this workflow and project-wide rules.
3. `docs/MODULE_DEVELOPMENT.md` — module structure, tests, and completion criteria.
4. `docs/PMCA_SHARED_PLATFORM_RULES.md` — single-owner architecture, isolation, migration, and validation.
5. `docs/PMCA_LOGGING_CONTRACT.md` — mandatory event coverage, storage, privacy, and logging tests.
6. `PMCA/ARCHITECTURE.md` and `PMCA/PROJECT_CONTRACT.md` — architecture and contracts.
7. The target module/app's `README.md`, `module.json`/passport, architecture/contract docs, build configuration, tests, and all existing implementations of the capability being changed.
8. Relevant `.github/workflows/` files and existing CI/build instructions.
9. docs/ANDROID_APP_REGISTRY.md plus existing package/application IDs, namespace, versioning and signing/build configuration when an Android app or APK is involved.

Then search for existing implementations and consumers before adding new code. Record a short preflight summary in the task/PR (use .github/pull_request_template.md where applicable): rules read, existing implementation reused or reason for a new one, affected IDs/dependencies, tests planned, and unresolved constraints. If a required file cannot be accessed, report that limitation and do not claim the preflight is complete; ask for access or proceed only with clearly bounded non-code analysis.

This is a mandatory workflow gate, not optional advice. Do not start implementation until the preflight is complete.

## Change workflow

1. Inspect the current branch, open pull requests, and affected files before editing.
2. Complete the mandatory AI preflight above.
3. Make one coherent change per branch/PR; do not overwrite unrelated in-progress work.
4. Keep project-specific configuration in the host project. Keep reusable behavior behind a public API.
5. Run python3 tools/pmca_module_test.py --all, python3 -m unittest tools/test_pmca_module_test.py, and python3 tools/pmca_android_audit.py, then run the relevant unit tests/build before reporting success.
6. Review CI results. Do not claim a build or device behavior passed unless the result was observed.
7. Merge only after review and validation; never silently push unfinished work to the default branch.

## Architecture boundaries

- The controller coordinates a workflow; it must not be called recursively by modules.
- The router chooses a module; modules must not route to one another directly.
- Modules expose explicit inputs and outputs and report completion/failure.
- The duplicate guard and processing gate run before side effects.
- Retry loops must be bounded, cancellable, and logged.
- A result/event must carry an execution or correlation ID so it cannot be mistaken for a new command.
- Logging must not re-enter the command-processing pipeline.
- One capability has one authoritative implementation. Do not add parallel implementations without a documented migration plan.

## Module requirements

Every new module needs a passport, a public contract, deterministic unit tests, failure-path tests, and documented Android/device checks when applicable. Run:

```sh
python3 tools/pmca_module_test.py --all
python3 tools/pmca_module_test.py --module path/to/module
```

For Android implementation tests, run the applicable Gradle unit tests, lint, and build. Passport validation alone does not prove runtime correctness.

## Logging and privacy

Follow `docs/PMCA_LOGGING_CONTRACT.md`. Logs must capture real success and failure outcomes, use execution/correlation IDs where applicable, and never log credentials, tokens, signing secrets, clipboard contents, or full user messages by default. Audit existing loggers and call sites before adding or replacing a logger. Logs generated on a device belong in local diagnostics; do not commit runtime logs or personal data to Git.

## Versioning and compatibility

- Use MAJOR.MINOR.PATCH for module versions and API versions.
- MAJOR: breaking public API or contract changes.
- MINOR: backward-compatible capability additions.
- PATCH: backward-compatible fixes.
- State dependencies and limitations explicitly in the passport.
- Update docs and tests in the same change as behavior changes.
