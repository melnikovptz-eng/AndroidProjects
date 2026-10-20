# AndroidProjects

Android projects and Termux builds.

## Project standards

- [Project rules](docs/PROJECT_RULES.md) — architecture boundaries, change workflow, logging/privacy, and versioning.
- [Module development and testing](docs/MODULE_DEVELOPMENT.md) — how to create and test a module independently.
- [GPTSenderTest architecture contract](GPTSenderTest/ARCHITECTURE.md) — current module boundaries and ownership.

## Command-line module tester

Run from the repository root:

```sh
python3 tools/pmca_module_test.py --all
python3 tools/pmca_module_test.py --module GPTSenderTest/modules/clicker
python3 tools/pmca_module_test.py --module GPTSenderTest/modules/input --json
```

Use `--strict` to make missing module-local tests or README documentation fail validation. The CLI checks passports, entry-point source paths, duplicate IDs, and explicit dependency cycles; it does not execute Android code. Android implementation tests and device verification remain separate steps.
