# PMCA — PROJECT CONTRACT

## Project
Portable Modular Controller Architecture

## Version
0.1.0

## Core Principle
Сначала контракт — потом реализация.

## Mandatory AI/developer preflight
Before creating a module/application or materially changing an existing one, read the current repository and applicable rules: `README.md`, `docs/PROJECT_RULES.md`, `docs/MODULE_DEVELOPMENT.md`, `docs/PMCA_SHARED_PLATFORM_RULES.md`, `docs/PMCA_LOGGING_CONTRACT.md`, this contract, `PMCA/ARCHITECTURE.md`, and the target component's passport, docs, source, tests, build configuration, and relevant workflows. Search for existing implementations and call sites before adding code. Record the review and test plan. If required documents cannot be read, disclose the gap and do not claim the preflight passed.

For Android apps, audit `applicationId`, `namespace`, versioning, signing identity, and APK workflows before changes that might break update/install compatibility.

## Architecture
Controller
Module System
Module Contract
Module Manifest
Queue
Router
Duplicate Guard
Processing Gate
Logger
State Machine
Adapters
Health Check
Watchdog
Error Isolation
Versioning
Portability Check
Module Export
Developer Mode

## Controller Rule
Controller — единственный центр управления.

UI не управляет внутренней логикой модулей напрямую.

## Module Rule
Каждый модуль независим и должен иметь:
- code
- Contract
- Module Manifest
- README
- Version
- Dependencies
- Permissions
- Health Check
- Tests
- logging contract coverage (success and failure outcomes)

## Module Lifecycle
start()
stop()
restart()
status()
health()
execute()

## Isolation
Ошибка одного модуля не должна ломать Controller или другие модули.

## Stub Rule
STUB не считается PASS.

## Legacy Rule
Рабочий legacy-код не переписывается без необходимости.

Legacy evaluation:
REUSE
ADAPT
EXTRACT
REBUILD
REJECT

## UI Rule
UI зависит от контрактов Controller/Modules,
но внутреннее устройство модулей от UI не зависит.

## Processing Rule
Все команды проходят через:

UI
-> Controller
-> Parser
-> Duplicate Guard
-> Processing Gate
-> Queue
-> Router
-> Executor
-> Logger

Only stages that actually exist in a given implementation may be reported as completed.

## Logging Rule
`docs/PMCA_LOGGING_CONTRACT.md` is mandatory. Existing logger implementations and call sites must be audited before a new logger is introduced. Operational events must record actual success and failure outcomes, use execution/correlation IDs when applicable, and redact sensitive data. A click callback is not proof that a message was delivered. Logger failure must not re-run the original command or crash the main operation.

## Stability Priority
1. Stability
2. Correctness
3. Simplicity
4. Portability
5. Optimization

## Testing Rule
Health Check и Functional Test — разные проверки.

Green PASS разрешён только после фактической проверки.

## Developer Mode
Developer Mode предназначен для диагностики,
проверки модулей, логов и состояния системы.

По умолчанию Developer Mode не должен изменять рабочее состояние системы.

## Safety
Не выполнять автоматические повторные запуски,
если причина запуска не подтверждена Controller.

Не обрабатывать одну и ту же команду повторно
без прохождения Duplicate Guard.
