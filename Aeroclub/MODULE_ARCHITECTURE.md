# Архитектура модулей и общая библиотека — «Аэроклуб · информер»

Версия: 1.0  
Ветка: `aeroclub-informer`  
Область: только `Aeroclub/`

## 1. Цель разбиения

Разделить проект по ответственности так, чтобы каждая бизнес-функция имела одного владельца, а повторно используемые модели, правила, UI-компоненты и Android-интеграции не копировались между календарём, карточкой мероприятия и административной панелью.

**Фактическое состояние на текущем этапе:** созданы реальные Gradle-модули core:common, core:data, core:ui, core:android, feature:notifications, feature:admin-flights, feature:admin-participants, feature:calendar, feature:messages, feature:settings, feature:admin и feature:informer. Модуль app содержит манифест; домашний виджет удалён по решению пользователя. AdminActivity остаётся общим навигационным UI-хостом; записи/обновления и основные сценарии вынесены в feature use cases.

## 2. Целевая структура Gradle

```text
Aeroclub/
  app/                         # точка входа, навигация и сборка приложения
  core/
    common/                    # Android-free: модели, типы результата, валидация, правила
    data/                      # контракты репозиториев и локальные реализации/миграции
    ui/                        # общие визуальные компоненты и оформление Android UI
    android/                   # адаптеры Android: карты и share chooser
  feature/
    informer/                  # карточка ближайшего вылета / экран-превью
    admin-flights/              # управление вылетами
    admin-participants/         # участники и ответы
    calendar/                   # календарное представление
    messages/                   # шаблоны, черновики, история и каналы
    settings/                   # настройки клуба
    notifications/              # локальные напоминания и экран состояния
  integration/
    api/                        # HTTP-клиент и DTO серверного API (когда API согласован)
    auth/                       # сессия и роли (когда выбран провайдер)
    sync/                       # outbox, повторы, конфликты и синхронизация
```

Это целевая карта. Фактически созданы core-библиотеки и feature-модули для уведомлений, вылетов, участников, календаря, сообщений, настроек, админ-панели и информера. UI-экраны вылетов, участников, календаря, сообщений и настроек пока размещены в одном `AdminActivity` внутри `:feature:admin`; выносить их нужно по одному функциональному блоку с реальными потребителями и тестами. Не создавать пустые модули и отдельные библиотеки для каждой кнопки.

## 3. Библиотеки общего использования

### LIB-COMMON — `:core:common` (обычная Java/Kotlin-библиотека без Android UI)
Единый владелец:
- модели и идентификаторы `Flight`, `Participant`, `Attendance`, `MessageRecord`, `ClubSettings`;
- типы статусов ответа, публикации, синхронизации и результата операции;
- правила выбора активного вылета, подсчёта ответов и валидации полей;
- общие контракты результата/ошибки и формат дат ISO.

Запрещено: Android Context, Activity, RemoteViews, SharedPreferences, HTTP-клиент и UI.

### LIB-DATA — `:core:data`
Единый владелец чтения/записи:
- интерфейсы репозиториев;
- `LocalFilePreferences`: единый JSON-файл `aeroclub-local-data.json` в приватной папке приложения;
- однократное копирование значений старого `SharedPreferences` `flight_informer` с сохранением исходных данных;
- атомарная запись файла и запрет молчаливого сброса повреждённого хранилища;
- единая точка изменения данных, устойчивые ID и правила сохранения;
- все доменные репозитории используют один файл, UI не читает его напрямую;
- граница локального хранилища и будущего API.

Запрещено: самостоятельное чтение данных из Activity или экранов; дублирующие ключи одного поля; сообщение об успешной серверной синхронизации без подтверждения.

### LIB-UI — `:core:ui` (Android library)
Повторно используемые компоненты:
- карточка вылета, типографика, отступы, цвета и скругления;
- единые кнопки действий и состояния загрузки/пустых данных/ошибки;
- общие форматтеры для отображения даты/времени и статуса, если они зависят от Android resources;
- доступность, content descriptions и единые размеры интерактивных элементов.

Домашний экранный виджет удалён; интерфейс реализуется нативными Android View-компонентами в приложении.

### LIB-ANDROID — `:core:android`
Общие Android-адаптеры:
- открытие карты через один маршрутный сервис;
- формирование Intent для системного «Поделиться»;
- единая обработка отсутствующего приложения и отмены chooser;
- безопасная обёртка системных уведомлений при необходимости.

Бизнес-правила не размещать в адаптерах. FCM не добавлять в библиотеку как фиктивную заглушку: подключить только вместе с Firebase-конфигурацией и серверным отправителем.

## 4. Владение функциями

| Функция | Единственный владелец | Использует общие библиотеки |
|---|---|---|
| Главный календарь и карточка мероприятия | feature:informer | common, data, ui, android |
| Ответ «еду/не еду» | feature:admin-participants (сценарий ответа — общий domain use case) | common, data, android |
| CRUD и активный вылет | feature:admin-flights | common, data |
| Список участников и агрегаты | feature:admin-participants | common, data |
| Календарь | feature:calendar | common, data |
| Шаблоны/черновики/история | feature:messages | common, data, android |

Для канала локальных объявлений оба потребителя (`feature:admin` и `feature:informer`) получают `MessageTransport` через единственный `MessageTransportProvider` в `feature:messages`. Не создавать транспорт в экранах напрямую; конкретная локальная реализация выбирается только в провайдере.
| Настройки клуба | feature:settings | common, data |
| Локальные напоминания | feature:notifications | common, data, android |
| API и DTO | integration:api | common |
| Вход и роли | integration:auth | common, api |
| Outbox/повторы/конфликты | integration:sync | common, data, api, auth |
| Журнал | core:data + единый EventLogger контракт | common |
| Проверки и выпуск | app / GitHub Actions | все собранные модули |

## 5. Правила против дублирования

1. Модель каждого предметного объекта объявляется один раз в LIB-COMMON.
2. Данные читаются и изменяются через один репозиторий/контракт из LIB-DATA. UI не хранит собственную копию списка.
3. Правило расчёта счётчиков/статусов реализуется один раз в domain/common, а экраны только отображают результат.
4. Формирование текста сообщения — единый formatter; каналы отправки — отдельные адаптеры. Открытие chooser не является доставкой.
5. Маршрут строится одним сервисом; карточка мероприятия не формирует geo-URI самостоятельно.
6. Дата хранится в ISO-формате, а локализованная подпись вычисляется единым форматтером.
7. Изменения вылета/ответа отражаются при повторном отображении календаря/карточки.
8. Журналирование вызывается через общий контракт. Нельзя логировать токены, пароли, полные сообщения и лишние персональные данные.
9. Общий элемент извлекается в библиотеку только после определения его API и минимум двух реальных потребителей. Не создавать копию ради будущего использования.
10. Перед переносом проверять импорты, сохранность SharedPreferences-ключей и миграцию. Удалять старую реализацию только после подтверждения отсутствия потребителей и прохождения тестов.

## 6. Порядок миграции без большого переписывания

1. Зафиксировать текущую сборку и SHA; сохранить все существующие исходники и историю.
2. Вынести в `:core:common` чистые модели/статусы/валидацию, не меняя поведение.
3. Вынести контракты и текущие репозитории в `:core:data`; сохранить старые ключи и миграцию.
4. Создать `:core:android` и перенести общие операции карты/share только после тестов текущего поведения.
5. Создать `:core:ui` и извлечь реально повторяющиеся токены/компоненты для экранов; RemoteViews оставить адаптированным представлением.
6. Отделить функциональные пакеты/Gradle-модули по одному: informer → flights → participants/attendance → calendar → messages → settings → notifications.
7. Только после согласования сервера создавать api/auth/sync модули; локальную очередь не называть сетевой синхронизацией.
8. После каждого этапа запускать GitHub Actions, проверять APK и ключевые сценарии. Один логический этап — один проверяемый коммит.

## 7. Критерии готовности архитектуры

- В `settings.gradle` перечислены только реальные модули; все Gradle-зависимости разрешаются.
- У каждой модели и бизнес-правила один владелец.
- UI не обращается напрямую к SharedPreferences и не содержит сетевого кода.
- Календарь, карточка мероприятия и админка показывают данные из одного источника.
- Повторяющиеся цвета/отступы/форматирование не разъезжаются между экранами.
- Миграция старых локальных данных проверена на обновлении поверх предыдущей версии.
- Проверки сборки и функциональные сценарии относятся к точному SHA.
- Документы README, ТЗ и паспорта обновлены одновременно с реальными изменениями кода.


## Runtime safety rules (mandatory)

1. Repository initialization must be one-way: a repository's `ensureInitialized` may call lower-level repositories only if the dependency graph stays acyclic. Never call back into the initializer through a getter.
2. Run legacy migrations once and mark completion atomically. Do not rescan all entities on every screen render.
3. No unbounded retry loops, recursive worker starts, duplicate services, or self-rescheduling immediate work. Scheduled Android operations must use stable PendingIntent identities and explicit cancel/reschedule behavior.
4. All loops must terminate over a finite, validated collection. Any future API retry policy must be bounded and use backoff; no server request may recursively enqueue another request without an explicit guard.
5. Verify one instance of each responsibility before adding a worker, receiver, repository, or utility. Shared implementations must have a clear owner.
6. UI callbacks must not block waiting for network operations. Local repository calls must stay bounded; large migrations should move out of the UI lifecycle.
7. Lock order is one-way: ParticipantRepository → AttendanceRepository → FlightRepository → SyncRepository. FlightRepository may enqueue to SyncRepository, but SyncRepository never calls back into repositories. FlightReminderScheduler may call FlightRepository while holding its own lock; repositories never call the scheduler while holding repository locks. Do not introduce reverse calls or wait on futures while holding these locks.
7. After each code change, run a CI build tied to the exact commit SHA. Do not infer success from an older green run.


## 9. Фактические Gradle-модули и граф зависимостей

| Модуль | Реальное содержимое | Зависит от |
|---|---|---|
| :core:common | Доменные модели, канонические статусы ответов, единое форматирование DEMO, ISO/date-time validation; unit-тесты | Java standard library |
| :core:data | Единый локальный JSON-файл, репозитории, миграции и event log; серверный outbox отключён | :core:common |
| :core:ui | AeroclubUi, общая палитра, shape helper и цвета | :core:common |
| :core:android | FlightActionLauncher | :core:common, :core:data |
| :feature:notifications | Планировщик локальных напоминаний, receiver и восстановление после перезагрузки | :core:common, :core:data |
| :feature:admin-flights | FlightAdminActions: create/update/delete, reminder side effects, журналирование | common, data, notifications |
| :feature:admin-participants | ParticipantAdminActions: CRUD, ответы, валидация и журналирование | common, data |
| :feature:calendar | CalendarActions: ISO-дата и поиск вылетов по дате | common, data |
| :feature:messages | MessageTemplates, MessageTransport, локальный JSON-транспорт и foreground file watcher | common |
| :feature:settings | ClubSettingsActions: чтение/сохранение и журналирование | common, data |
| :feature:admin | AdminActivity и UI разделов админ-панели | common, data, ui, android, notifications, admin use cases |
| :feature:informer | MainActivity — превью, действия участника и показ локальных объявлений | common, data, ui, android, notifications, admin-participants, messages |
| :app | manifest и упаковка приложения | общие библиотеки и feature-модули |

Граф ацикличен: use-case модули зависят от data/common; feature:messages не зависит от UI; репозитории не зависят от feature-модулей. feature:informer использует общие сценарии participants/settings без обратной зависимости. Домашний виджет и его обновляющий адаптер удалены; `:core:android` обслуживает маршрут и системное «Поделиться». AdminActivity остаётся общим навигационным UI-хостом, а операции вылетов, участников, календаря, шаблонов сообщений и настроек принадлежат отдельным feature-модулям.

Остаётся незавершённым: разбиение административного UI на отдельные экранные модули, серверный API, авторизация/роли, FCM и подтверждаемая синхронизация.


## 10. Локальный файловый режим (текущий приоритет)

- Каноническое доменное хранилище: `LocalFilePreferences` → `files/aeroclub-local-data.json`.
- Сохранены прежние ключи репозиториев, чтобы перенос не требовал переписывания всех сценариев сразу.
- При первом запуске выполняется миграция из `flight_informer`; старый SharedPreferences не удаляется автоматически.
- `SyncRepository` оставлен как совместимый фасад для старых вызовов, но серверную очередь не создаёт.
- Локальный транспорт сообщений использует собственный `local_message_transport/messages.json`; он не является каналом между устройствами.
- Файл хранится в приватной папке приложения Android. Для резервного копирования во внешнюю папку нужен отдельный явный экспорт/импорт; нельзя писать в общую папку Download без согласованного UX/разрешений.
- Контрольные сборки выполнять после завершения связанного блока, а не после каждого изменения. До контрольной сборки файловый режим считается реализованным в исходниках, но не подтверждённым на устройстве.
