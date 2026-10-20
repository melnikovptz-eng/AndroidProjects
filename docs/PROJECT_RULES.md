# PMCA / AndroidProjects project rules

This document is the project-wide source of truth for contribution workflow. Module-specific details belong in `docs/MODULE_DEVELOPMENT.md` and the module's own README/passport.

## Change workflow

1. Inspect the current branch, open pull requests, and affected files before editing.
2. Make one coherent change per branch/PR; do not overwrite unrelated in-progress work.
3. Keep project-specific configuration in the host project. Keep reusable behavior behind a public API.
4. Run the module passport tester and the relevant unit tests/build before reporting success.
5. Review CI results. Do not claim a build or device behavior passed unless the result was observed.
6. Merge only after review and validation; never silently push unfinished work to the default branch.

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

Logs should include execution/correlation ID, module ID, operation, start/end, outcome, and concise error details. Do not log credentials, tokens, clipboard contents, or full user messages by default. Logs generated on a device belong in local diagnostics; do not commit runtime logs or personal data to Git.

## Versioning and compatibility

- Use MAJOR.MINOR.PATCH for module versions and API versions.
- MAJOR: breaking public API or contract changes.
- MINOR: backward-compatible capability additions.
- PATCH: backward-compatible fixes.
- State dependencies and limitations explicitly in the passport.
- Update docs and tests in the same change as behavior changes.
