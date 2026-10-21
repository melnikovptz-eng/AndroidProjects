# Реестр проектов и рабочих артефактов

Обновлено: 2026-10-21. Этот реестр — единая точка входа для ИИ. Он фиксирует каталоги, ветки, способы сборки и правила выбора правильного файла. Текущие статусы сборок перепроверять по GitHub Actions перед выдачей APK.

## 1. Карта проектов

| ID | Проект | Корень | Рабочая ветка / источник | Workflow | Ожидаемый результат |
|---|---|---|---|---|---|
| AEROCLUB | Aeroclub Informer | Aeroclub/ | aeroclub-informer | .github/workflows/build-aeroclub-informer.yml | Debug APK из :app:assembleDebug |
| PMCA | Portable Modular Controller Architecture / AndroidBridge | PMCA/; Android-приложение: PMCA/AndroidBridge/ | CI настроен для main; проверить актуальный рабочий ref перед изменением | .github/workflows/pmca-android-build.yml | PMCA debug APK; release APK только при настроенных signing secrets |
| VARIO | Vario Synthesizer | VarioSynthesizer/ | Последний известный успешный run — rebuild/vario-synthesizer-v1; проверить HEAD перед новой работой | .github/workflows/build-vario-synthesizer.yml | Debug APK |
| GPTSENDER | GPTSenderTest | GPTSenderTest/ | CI настроен для main; проверить актуальный рабочий ref перед изменением | .github/workflows/gptsender-build.yml | Debug APK; signed release только при наличии signing secrets |
| COORDGRID | Coordinate Grid Android | CoordinateGridAndroid/ | Workflow реагирует на изменения пути и ручной запуск; проверить branch/run перед сборкой | .github/workflows/build-coordinate-grid.yml | Debug APK |

## 2. Текущие контрольные точки

Checkpoint — это последняя документированная точка, а не гарантия, что код после неё не менялся. Если HEAD другой, просмотреть только изменения после checkpoint.

### AEROCLUB
- Текущий известный HEAD ветки aeroclub-informer: 24b8630185caf41ebbe8143e10b525e435050721.
- Последний найденный успешный workflow run: [run #274](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/39650851139).
- SHA сборки: c573778f62c92a8c8187f0ab07d3885ee2a4a5ec.
- **Статус артефакта: УСТАРЕЛ ОТНОСИТЕЛЬНО HEAD** — SHA run не совпадает с HEAD. Не выдавать его за сборку текущего коммита.
- Workflow сейчас настроен на ручной запуск (workflow_dispatch). Для проверки текущего HEAD потребуется контрольный запуск после завершения логического блока.

### VARIO
- Последний найденный успешный run: [run #9](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/39640433399).
- Ветка: rebuild/vario-synthesizer-v1; SHA: 2a3b1fd653371298ab4434b086ea4c27c10c1461.
- **Статус артефакта: ПОСЛЕДНЯЯ ИЗВЕСТНАЯ УСПЕШНАЯ СБОРКА; соответствие текущему HEAD не проверено.**

### PMCA, GPTSENDER, COORDGRID
- На момент обновления этого реестра не зафиксирован проверенный source SHA последней успешной сборки в этом документе.
- Перед выдачей APK найти соответствующий workflow run, проверить ветку, SHA, conclusion и артефакт. Не подставлять файл из другого проекта.
- В GPTSenderTest/artifacts/GPTSenderTest.apk есть APK в репозитории, но без доказательства соответствия текущему HEAD он считается **непроверенным сохранённым артефактом**, а не последней рабочей сборкой.

## 3. Однозначная идентификация сборки

Любой APK должен идентифицироваться кортежем:

PROJECT_ID + BRANCH + SOURCE_SHA + WORKFLOW + RUN_ID + BUILD_VARIANT + PACKAGE_ID + APK_SHA256

Правила:
1. Имя workflow artifact должно содержать проект и полный source SHA.
2. Артефакт должен содержать build-info.txt с проектом, веткой, source SHA, run ID, вариантом сборки, application/package ID и SHA-256 APK.
3. Имя app-debug.apk само по себе не уникально и не является идентификатором сборки.
4. APK без manifest/checksum допускается выдать только с явной пометкой «метаданные сборки не подтверждены».
5. Если source SHA сборки не равен HEAD рабочей ветки, не называть её актуальной сборкой.
6. Не считать сборку «рабочей на устройстве», пока отдельно не выполнена проверка на устройстве.
7. Ссылку давать на конкретный Actions run/artifact, а не на произвольный ZIP репозитория.

## 4. Как ИИ ориентируется без повторного сканирования

1. Прочитать этот файл.
2. Прочитать соответствующую секцию в WORKLOG.md.
3. Сравнить сохранённый checkpoint SHA с HEAD зарегистрированной ветки.
4. Если совпадает — использовать записи журнала и читать только файлы, относящиеся к новой задаче.
5. Если отличается — посмотреть список commits/diff между checkpoint и HEAD, кратко обновить журнал; не перечитывать весь проект.
6. Полное сканирование разрешено только при изменении архитектуры, неизвестном состоянии, противоречии журналу или явной просьбе пользователя.
