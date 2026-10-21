# AndroidProjects

Репозиторий Android-приложений и инструментов Termux.

## Проекты

- **Aeroclub Informer** — Android-приложение и виджет информера аэроклуба. Исходники и workflow находятся в ветке [`aeroclub-informer`](https://github.com/melnikovptz-eng/AndroidProjects/tree/aeroclub-informer).
- **Vario Synthesizer** — отдельное Android-приложение для генерации, записи и экспорта звуков вариометра. Единственная подтверждённая ветка исходников: [`rebuild/vario-synthesizer-v1`](https://github.com/melnikovptz-eng/AndroidProjects/tree/rebuild/vario-synthesizer-v1). Сборка: [GitHub Actions](https://github.com/melnikovptz-eng/AndroidProjects/actions/workflows/build-vario-synthesizer.yml).
- **PMCA / GPTSender** — отдельные ветки разработки; не смешивать их с приложением Aeroclub или синтезатором.

## Звуковые образцы вариометра

Сохранённые записи WAV и их JSON-профили находятся в `Aeroclub/docs/samples/vario-sounds/` в ветке `aeroclub-informer`. Это архивные образцы для справки, а не встроенный синтезатор.

## Правило структуры

Исходники приложения хранятся в одной канонической папке проекта и развиваются в соответствующей ветке. APK и временные результаты сборки следует брать из GitHub Actions artifacts, а не хранить повторными копиями в исходниках.
