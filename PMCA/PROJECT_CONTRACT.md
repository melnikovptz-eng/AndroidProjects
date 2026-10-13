# PMCA — PROJECT CONTRACT

## Project
Portable Modular Controller Architecture

## Version
0.1.0

## Core Principle
Сначала контракт — потом реализация.

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

