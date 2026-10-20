# AndroidProjects

Android projects and Termux builds.

## Project standards

- [PMCA shared platform and isolated application rules](docs/PMCA_SHARED_PLATFORM_RULES.md) — mandatory rules for shared resources, module ownership, application/build isolation, dependency checks, and safe migration.
- [Project rules](docs/PROJECT_RULES.md) — architecture boundaries, change workflow, logging/privacy, and versioning.
- [Module development and testing](docs/MODULE_DEVELOPMENT.md) — how to create and test a module independently.
- [PMCA logging contract](docs/PMCA_LOGGING_CONTRACT.md) — required events, redaction, retention, and success/failure semantics.
- [Android app registry](docs/ANDROID_APP_REGISTRY.md) — package IDs, namespace, signing, and update compatibility.
- [GPTSenderTest architecture contract](GPTSenderTest/ARCHITECTURE.md) — current module boundaries and ownership.

## Command-line module tester

Run from the repository root:

```sh
python3 tools/pmca_module_test.py --all
python3 tools/pmca_module_test.py --module GPTSenderTest/modules/clicker
python3 tools/pmca_module_test.py --module GPTSenderTest/modules/input --json
python3 -m unittest tools/test_pmca_module_test.py
python3 tools/pmca_android_audit.py
```

Use `--strict` to promote contract warnings to errors. The validator discovers nested Android Java/Kotlin source roots, checks passport fields, entry points, duplicate module IDs, explicit structured dependency IDs, and dependency cycles. The Android audit checks statically visible application IDs and version metadata. These checks do not replace Android builds, device tests, signing-certificate verification, or review of actual runtime behavior.
