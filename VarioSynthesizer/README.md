# Vario Synthesizer

Канонический Android-проект синтезатора вариометра в репозитории `AndroidProjects`.

## Где находится исходный код

- Проект: `VarioSynthesizer/`
- Главный экран и генерация/запись звука: `app/src/main/java/ru/aeroclub/variosynth/MainActivity.java`
- Сборка GitHub Actions: `.github/workflows/build-vario-synthesizer.yml`
- Ветка проекта: `rebuild/vario-synthesizer-v1`

Синтезатор остаётся отдельным проектом. Он не подключён к приложению Aeroclub Informer.

## Сборка

Запустите workflow **Build Vario Synthesizer APK** во вкладке Actions. Workflow собирает `assembleDebug` и публикует APK как артефакт `VarioSynthesizer-debug-1`.

## Сохранённые звуковые образцы

Отдельные WAV/JSON-записи, сделанные в синтезаторе, хранятся в проекте информера только как образцы и документация:
`Aeroclub/docs/samples/vario-sounds/`

Это не вторая копия приложения и не runtime-ресурсы синтезатора.
