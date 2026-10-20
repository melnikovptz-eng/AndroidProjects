# PMCA module development and isolated testing

## Mandatory preflight before implementation

Before creating or substantially changing any module, the AI/developer must read:
- `README.md`
- `docs/PROJECT_RULES.md`
- this document, `docs/MODULE_DEVELOPMENT.md`
- `docs/PMCA_SHARED_PLATFORM_RULES.md`
- `docs/PMCA_LOGGING_CONTRACT.md`
- `PMCA/ARCHITECTURE.md`
- `PMCA/PROJECT_CONTRACT.md`
- the target app/module's README, passport (`module.json` or equivalent), contracts, source, tests, and build configuration;
- relevant GitHub Actions workflows and existing implementations/call sites of the same capability.

For an Android application, also inspect existing `applicationId`, `namespace`, versioning, signing configuration and APK workflows before changing them. Do not change package IDs or signing identity without a documented compatibility reason and explicit authorization where installation/update compatibility could be affected.

Search before coding. Reuse or safely extend the canonical implementation; do not create a parallel logger, daemon, handler, service, or duplicate module for the same capability without a documented migration plan.

Record the preflight in the task/PR: rules reviewed, current implementation found, reuse/new-implementation decision, package/dependency impact, and planned tests. If a required source cannot be read, disclose that and do not say the rules were fully reviewed. This is a mandatory gate.

## Goal

A module must be understandable, testable, and diagnosable without launching the full PMCA workflow. Keep one capability per module and make dependencies explicit.

## Required module structure

A module passport is stored as `module.json` beside the module's documentation or implementation directory. It must identify the module, version, API version, entry point(s), capabilities, dependencies, and limitations. Passport metadata describes the module; it does not prove the implementation works.

For new modules, add:
- `module.json` — machine-readable passport;
- implementation source — one responsibility only;
- unit tests for deterministic logic;
- Android/integration tests when behavior depends on Android services, accessibility, UI, or device state;
- a short `README.md` with purpose, inputs, outputs, failure cases, and manual device-test steps where required.

Do not make one module call another module's implementation directly. Depend on a public API/interface and inject the dependency. The host/coordinator owns workflow and project-specific values.

## Command-line checks

From the repository root:

```sh
python3 tools/pmca_module_test.py --all
python3 tools/pmca_module_test.py --module GPTSenderTest/modules/clicker
python3 tools/pmca_module_test.py --module GPTSenderTest/modules/input --json
```

The command-line tester validates passport structure, required metadata, duplicate module IDs, declared entry points, and dependency cycles between PMCA module IDs. Warnings are reported when test files or module documentation are missing. Use `--strict` to make warnings fail the check.

## Run implementation tests

For Android code, run from `GPTSenderTest/`:

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

A successful passport check is not a substitute for Gradle tests or a real-device test. A successful build is not proof that an accessibility action was accepted by the target app.

## Required test cases

Every module should test, as applicable:
1. valid input and expected output;
2. missing, malformed, and out-of-range input;
3. unavailable dependency/service;
4. timeout and cancellation;
5. repeated request / duplicate execution;
6. success and failure logging according to `docs/PMCA_LOGGING_CONTRACT.md`;
7. failure logging without retry loops;
8. boundary conditions and screen/device configuration changes for UI modules;
9. no side effects when validation fails.

For asynchronous operations, expose a result/callback/future with a defined completion and failure state. Avoid hidden sleeps, recursive retries, global mutable state, and direct calls back into the controller.

## Definition of done

- [ ] Mandatory project-rule preflight completed and summarized.
- [ ] Passport parses and passes `tools/pmca_module_test.py`.
- [ ] Unit tests cover success and failure paths.
- [ ] Android-dependent behavior has an integration/device test plan.
- [ ] Public API, input/output, permissions, dependencies, and limitations are documented.
- [ ] Logs meet the logging contract and record real success/failure outcomes without sensitive text.
- [ ] No uncontrolled recursion, duplicate registration, or unlimited retry.
- [ ] Existing app builds and unrelated modules remain unchanged.
- [ ] CI result is checked before merge.
