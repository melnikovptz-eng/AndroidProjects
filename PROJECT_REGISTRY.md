# Реестр проектов и рабочих артефактов

Обновлено: 2026-10-21. Этот реестр — единая точка входа для ИИ. Он фиксирует каталоги, ветки, способы сборки и правила выбора правильного файла. Текущие статусы сборок перепроверять по GitHub Actions перед выдачей APK.

## Глобальное обязательное правило

Для журналов отладки и доступа к файлам на Android действует [GLOBAL_PROJECT_RULES.md](GLOBAL_PROJECT_RULES.md): публичный `Download/`, имя с названием проекта, максимум 80 000 символов для отправки в ChatGPT, ротация и явная проверка записи. Это правило распространяется на все проекты, а не только AEROCLUB.

## Первое правило: реестр задач и карта экранов

Перед любой работой по проекту ИИ обязан открыть проектный `TASK_REGISTRY.md`, затем `SCREEN_MAP.md` и `WORKLOG.md`; сверить HEAD; выполнять верхнюю незакрытую задачу. Новые дефекты записываются в реестр до изменения кода. Экраны имеют постоянные ID. Подробный стандарт: [TASK_REGISTRY_RULES.md](TASK_REGISTRY_RULES.md). Для Aeroclub: [реестр задач](Aeroclub/TASK_REGISTRY.md), [карта экранов](Aeroclub/SCREEN_MAP.md).

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
- Current branch HEAD: `58420dde6b47ee5621cb5717e1720587cda68c01`; runtime source built by latest successful CI is the same SHA.
- Latest successful workflow run: [run #414](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/44687191465), source SHA `58420dde6b47ee5621cb5717e1720587cda68c01`; artifact ID `14606583675`; APK SHA-256 `3c7b95819ffc460e6a2b82bbea0c95c1319e2605eff078bf79c9edfe7a949d0a`; package ID `ru.aeroclub.informer.mobile`; signing certificate SHA-256 `5167486c217aa60f9be53f2290ecca9b4f4f4c2b87d01483f4d51bc439d36db2`.
- **Status: BUILD SUCCESS.** Configured unit-test tasks, APK package verification and signing-certificate comparison passed. UI acceptance was **SKIPPED** because GitHub-hosted runner has no usable `/dev/kvm`; do not call this a successful emulator/device launch.
- Important correction: run #412's saved screenshot was inspected and showed only the Android system splash screen, not KD PILOT S01. The previous workflow's success was a false positive for UI acceptance. The workflow now verifies foreground UI content with UIAutomator, process/Activity state and logcat when KVM is available; it explicitly skips UI acceptance otherwise.
- W01 visual match, two-cell launcher width, pressed feedback and all four widget actions remain unverified on a physical device. AERO-009 and AERO-011 remain OPEN.
- Main screen/calendar and admin functionality remain subject to the existing open tasks in `Aeroclub/TASK_REGISTRY.md`. There is no API/authentication/FCM/remote incoming-message delivery; file exchange and local reminders must not be described as server sync or guaranteed delivery.
- Markdown-only updates do not trigger another build; rebuild after code/resources/Gradle changes or a device-test fix.

### VARIO
- Последний найденный успешный run: [run #10](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/39656240472).
- Ветка: rebuild/vario-synthesizer-v1; SHA: 47312aed9b983cbe61e841641cda3e3f6e60176f. Артефакт содержит build-info.txt с package ID и SHA-256.
- **Статус артефакта: BUILD SUCCESS.** Run #10 собран после обновления workflow и добавляет manifest. Перед выдачей повторно сверить HEAD ветки и артефакт.

### PMCA
- Последний найденный успешный run: [run #21](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/39656275187), SHA: 6276d6170bc66b12944e249ff521bd66d0c11610, branch main.
- Artifacts: PMCA-debug-apk-6276d6170bc66b12944e249ff521bd66d0c11610 и PMCA-release-apk-6276d6170bc66b12944e249ff521bd66d0c11610. Оба содержат build-info.txt; run успешен, release artifact присутствует. После этого run менялись workflow других проектов; PMCA исходники и его workflow не менялись.

### GPTSENDER
- Последний найденный успешный run: [run #46](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/39656310954), SHA: 166d899e1c2ab599fac0eae9854ff4103048dd81, branch main.
- Artifact: GPTSenderTest-debug-apk-166d899e1c2ab599fac0eae9854ff4103048dd81. В артефакте предусмотрен build-info.txt. После run менялся workflow Coordinate Grid, не исходники GPTSenderTest.

### COORDGRID
- Последний найденный успешный run: [run #2](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/39656350817), SHA: 1ad7067af89d9051de186bb5be20696d8dd3465d, branch main, conclusion=success.
- Artifact: CoordinateGrid-debug-1ad7067af89d9051de186bb5be20696d8dd3465d. В архив включён build-info.txt.
- В GPTSenderTest/artifacts/GPTSenderTest.apk есть APK в репозитории, но без доказательства соответствия source SHA он считается **непроверенным сохранённым артефактом**, а не последней рабочей сборкой.

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
