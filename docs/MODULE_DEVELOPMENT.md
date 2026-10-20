# PMCA module development and isolated testing

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
6. failure logging without retry loops;
7. boundary conditions and screen/device configuration changes for UI modules;
8. no side effects when validation fails.

For asynchronous operations, expose a result/callback/future with a defined completion and failure state. Avoid hidden sleeps, recursive retries, global mutable state, and direct calls back into the controller.

## Definition of done

- [ ] Passport parses and passes `tools/pmca_module_test.py`.
- [ ] Unit tests cover success and failure paths.
- [ ] Android-dependent behavior has an integration/device test plan.
- [ ] Public API, input/output, permissions, dependencies, and limitations are documented.
- [ ] Logs record request ID, start/end, result, and failure without sensitive text.
- [ ] No uncontrolled recursion, duplicate registration, or unlimited retry.
- [ ] Existing app builds and unrelated modules remain unchanged.
- [ ] CI result is checked before merge.
