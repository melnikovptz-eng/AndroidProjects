# Журнал выполненных работ по проектам

Обновлено: 2026-10-21. Журнал — компактный checkpoint для ИИ, а не замена тестам и не утверждение, что все функции готовы. Старые записи не удалять; новые добавлять сверху соответствующей секции. Если SHA проекта изменился, читать diff после checkpoint, а не сканировать весь репозиторий.

## Глобальное правило для всех проектов — журналы и Downloads
- **Зафиксировано 2026-10-21:** диагностические журналы должны храниться в Android `Download/` под понятным именем с названием проекта; предел передаваемого текста — 80 000 символов; доступ к Downloads и запись необходимо проверять явно.
- Подробный обязательный стандарт: [GLOBAL_PROJECT_RULES.md](GLOBAL_PROJECT_RULES.md). Применять к Aeroclub, Vario Synthesizer, PMCA, GPTSenderTest, Coordinate Grid и всем будущим приложениям.

## AEROCLUB — Aeroclub Informer
- **Checkpoint:** branch `aeroclub-informer`, current HEAD `4fa26960418dd2b47ebf9ae9cb6d1d586c84baa7`.
- **Новая функция прототипа:** на главном экране добавлены «Экспортировать файл сообщений» и «Импортировать файл сообщений». Через Android Storage Access Framework пользователь выбирает куда сохранить JSON и какой JSON импортировать. Формат `aeroclub-message-exchange`, version 1; лимит импорта 2 MiB; проверка схемы/записей до изменения локального хранилища; merge по уникальному ID, повторный импорт не должен добавлять те же записи; диагностируются успех/ошибка/отмена. Обмен пока ручной: файл нужно передать на другое устройство и импортировать там. Автоматической доставки/синхронизации нет.
- **Последняя подтверждённая CI-сборка:** [run #280](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/39662566836), success, source SHA `4fa26960418dd2b47ebf9ae9cb6d1d586c84baa7`. Доступные unit-тесты прошли; у части модулей `NO-SOURCE`. APK SHA-256: `90080ea204f410abc832149d695f9ec5f066dc530e4e383f9636709518abf6c0`. Artifact: [скачать APK из run #280](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/39662566836/artifacts/12374572143).
- **Другие подтверждённые изменения:** локальный JSON persistence и миграция SharedPreferences; безопасное расписание напоминаний при изменениях вылета, перезагрузке и изменении времени; GitHub-токен удалён из APK; журнал экспортируется только по явному действию.
- **Не подтверждено на телефоне:** сохранение после реального перезапуска, звук/вибрация напоминаний, обновление нескольких виджетов, реальный файловый цикл экспорта → перенос файла → импорт на второй установке, обработка отмены через системный picker и импорт на TECNO. CI успешен, но не является тестом устройства.
- **Ограничения:** сервер/API, аккаунты/роли, FCM, удалённые входящие и автоматическая межустройственная синхронизация не реализованы и не имитируются.
- **Следующий шаг:** установить именно APK run #280, проверить экспорт файла, перенести его на вторую установку/устройство, импортировать и импортировать повторно (проверить отсутствие дублей); затем проверить звук, напоминания и виджет на телефоне. Не запускать новые сборки без изменения исходников или исправления по результатам теста.

## PMCA — Portable Modular Controller Architecture
- **Корень:** PMCA/; Android-приложение/bridge: PMCA/AndroidBridge/.
- **Зафиксированные проектные правила:** центральный контроллер, единый владелец каждой функции, защита от дублирования обработчиков/демонов, сохранение существующих данных и резервных копий, явные контракты модулей.
- **CI:** .github/workflows/pmca-android-build.yml; собирает debug APK, release — только при наличии ключей подписи. Артефакты теперь включают build-info.txt, SHA-256 и source SHA в имени.
- **Последний подтверждённый build:** [run #21](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/39656275187), success, SHA 6276d6170bc66b12944e249ff521bd66d0c11610. После него менялись только workflow других проектов; исходники PMCA и его workflow не менялись.
- **Не считать проверенным:** текущее состояние установленного APK, фактическую работу на телефоне и отсутствие дублей процессов без соответствующих диагностических результатов.
- **Следующий шаг:** перед работой прочитать PMCA/README.md, PROJECT_PASSPORT.md, CURRENT_STATE.md, PROJECT_CONTRACT.md, ARCHITECTURE.md, MODULE_REGISTRY.md, CLIPBOARD_AUTOCOPY_SPEC.md, DECISIONS.md, HANDOFF.md; читать RECOVERY.md только при восстановлении после сбоя.

## VARIO — Vario Synthesizer
- **Корень:** VarioSynthesizer/.
- **Последний найденный успешный CI run:** [#10](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/39656240472), ветка rebuild/vario-synthesizer-v1, SHA 47312aed9b983cbe61e841641cda3e3f6e60176f.
- **Статус:** сборка run #10 успешна; артефакт содержит build-info.txt с package ID и SHA-256. Реальная работа звука на устройстве отдельно не подтверждена.
- **Важно:** Vario Synthesizer — отдельный проект. WAV-профили, сохранённые как примеры в Aeroclub, не означают объединение приложений.
- **Следующий шаг:** при запросе последнего APK сверить HEAD ветки и взять артефакт именно из run #9 или более нового успешного run.

## GPTSENDER — GPTSenderTest
- **Корень:** GPTSenderTest/; workflow: .github/workflows/gptsender-build.yml.
- **Назначение:** отдельное приложение для проверки вставки текста в поле сообщения ChatGPT и нажатия Send; это не модуль PMCA и не часть Aeroclub.
- **Известно:** в репозитории присутствует GPTSenderTest/artifacts/GPTSenderTest.apk; workflow предусматривает debug APK и условный signed release APK. Workflow обновлён: artifact name включает source SHA, а архив содержит build-info.txt.
- **Статус APK в artifacts/:** не считать последним рабочим без source SHA, run ID и checksum.
- **Требование проекта:** лог gpt_sender.log должен фиксировать исход каждого действия, включая успешную отправку; debug-оверлей областей обнаружения не должен блокировать кнопки приложения. Текущую реализацию этих требований проверять по коду/тесту, не считать автоматически завершённой.
- **Последний подтверждённый CI run:** [#46](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/39656310954), success, SHA 166d899e1c2ab599fac0eae9854ff4103048dd81. После run менялся только workflow Coordinate Grid, не исходники GPTSenderTest.

## COORDGRID — Coordinate Grid Android
- **Корень:** CoordinateGridAndroid/; workflow: .github/workflows/build-coordinate-grid.yml.
- **Известно:** workflow собирает debug APK и публикует Actions artifact с build-info.txt, SHA-256 и source SHA в имени.
- **Последний подтверждённый run #2:** [GitHub Actions](https://github.com/melnikovptz-eng/AndroidProjects/actions/runs/39656350817), success, SHA 1ad7067af89d9051de186bb5be20696d8dd3465d. Artifact CoordinateGrid-debug-1ad7067af89d9051de186bb5be20696d8dd3465d содержит build-info.txt.
- **Следующий шаг:** при работе сначала проверить README/Gradle-конфигурацию и соответствующий workflow, затем сохранить результат в этот журнал.

## Формат новой записи
YYYY-MM-DD | PROJECT_ID | branch | source SHA | изменение | проверки (точно) | результат/артефакт | остаток

Всегда различать:
- код изменён;
- тесты пройдены;
- сборка успешна;
- APK соответствует текущему HEAD;
- APK проверен на реальном устройстве.

Это пять разных утверждений.
