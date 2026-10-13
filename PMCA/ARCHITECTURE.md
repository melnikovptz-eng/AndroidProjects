# PMCA — ARCHITECTURE

## 1. System

PMCA — Portable Modular Controller Architecture.

PMCA является переносимой платформой для управления независимыми модулями.

## 2. Core Flow

INPUT
  ↓
Controller
  ↓
Parser
  ↓
Duplicate Guard
  ↓
Processing Gate
  ↓
Queue
  ↓
Router
  ↓
Module / Adapter
  ↓
Logger
  ↓
OUTPUT

## 3. Controller

Controller — единственный центр управления.

Controller отвечает за:

- приём задач
- создание Execution ID
- проверку состояния
- передачу задач Parser
- управление Queue
- управление Router
- контроль модулей
- обработку ошибок
- завершение выполнения

Controller не содержит внутреннюю реализацию модулей.

## 4. Module System

Каждый модуль является независимым компонентом.

Модуль взаимодействует с системой только через Module Contract.

Модуль не должен напрямую обращаться к внутренним функциям другого модуля.

## 5. Module Contract

Каждый модуль поддерживает:

start()
stop()
restart()
status()
health()
execute()

## 6. Module Manifest

Manifest описывает:

- module_id
- name
- version
- contract_version
- capabilities
- dependencies
- permissions
- inputs
- outputs
- limits
- adapter
- tests

## 7. Parser

Parser определяет:

- тип входных данных
- предполагаемый маршрут
- параметры задачи
- допустимость обработки

Parser не выполняет задачу самостоятельно.

## 8. Duplicate Guard

Duplicate Guard предотвращает повторную обработку одной задачи.

Основные идентификаторы:

MESSAGE_ID
ROUTE_ID
COMMAND_HASH
CONTENT_HASH
PARENT_ID
EXECUTION_ID

## 9. Processing Gate

Processing Gate решает, разрешено ли выполнение.

Состояния:

ALLOW
HOLD
CONFIRM_REQUIRED
REJECT

## 10. Queue

Queue отвечает за порядок выполнения задач.

Queue не определяет способ выполнения задачи.

## 11. Router

Router выбирает исполнитель или модуль.

Примеры:

Termux
ChatGPT
BLE
Android
API

Router не содержит бизнес-логику модулей.

## 12. Adapters

Adapters связывают PMCA с внешней средой.

Примеры:

Android Adapter
Termux Adapter
BLE Adapter
API Adapter

Adapter не должен становиться частью Core.

## 13. Logger

Logger записывает:

- Execution ID
- время
- модуль
- действие
- состояние
- результат
- ошибку

Logger должен работать независимо от UI.

## 14. State Machine

State Machine контролирует состояния выполнения.

Пример:

NEW
↓
QUEUED
↓
RUNNING
↓
SUCCESS

или:

RUNNING
↓
ERROR

или:

RUNNING
↓
TIMEOUT

## 15. Health Check

Health Check проверяет техническую работоспособность модуля.

Health Check не заменяет Functional Test.

## 16. Functional Test

Functional Test проверяет реальную работу модуля.

STUB не может получить PASS за Functional Test.

## 17. Watchdog

Watchdog контролирует зависшие или не отвечающие компоненты.

Watchdog не должен автоматически запускать бесконечные retry.

## 18. Error Isolation

Ошибка одного модуля не должна останавливать Controller или другие независимые модули.

## 19. Developer Mode

Developer Mode предоставляет:

- состояние PMCA
- состояние модулей
- Health Check
- Functional Test
- последние ошибки
- историю выполнения
- последние 50 строк Logger
- диагностический отчёт

Developer Mode по умолчанию read-only.

## 20. UI Independence

UI обращается к Controller.

UI не обращается напрямую к внутренним функциям модулей.

Изменение UI не должно требовать изменения Core,
если Module Contract не изменился.

## 21. Portability

Модуль должен иметь возможность быть перенесённым в другой проект.

Для переносимости запрещены прямые зависимости от:

- MainActivity
- конкретного UI
- внутренних классов другого модуля
- project-specific implementation

## 22. Execution ID

Каждое выполнение получает уникальный идентификатор.

Формат:

EXEC-000001

Execution ID используется Logger и Developer Mode.

## 23. Priority

Приоритет архитектуры:

1. Stability
2. Correctness
3. Simplicity
4. Portability
5. Optimization

## 24. Golden Rule

Сначала контракт.

Потом тест.

Потом реализация.

Потом интеграция.

