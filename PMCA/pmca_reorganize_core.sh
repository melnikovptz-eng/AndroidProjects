#!/data/data/com.termux/files/usr/bin/bash

set -u

PROJECT="$HOME/PMCA/AndroidBridge"
VERSION="0.3.0"
BUILD="4"
STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP="$HOME/PMCA_BACKUP_REORG_$STAMP"
REPORT="$HOME/PMCA_REORGANIZATION_RESULT.txt"
APK="$HOME/storage/downloads/PMCA.apk"

cd "$PROJECT" || exit 1

echo "===== PMCA: ЕДИНАЯ РЕОРГАНИЗАЦИЯ ====="
echo "PMCA_VERSION: $VERSION"
echo "PMCA_BUILD: $BUILD"
echo

echo "===== 1. ПРОВЕРКА ИСХОДНИКОВ ====="

CONTROLLER="app/src/main/java/com/pmca/bridge/core/PmcaController.java"
ROUTER="app/src/main/java/com/pmca/bridge/router/ModuleRouter.java"
STATE="app/src/main/java/com/pmca/bridge/core/ExecutionState.java"
CONTEXT="app/src/main/java/com/pmca/bridge/core/ExecutionContext.java"
QUEUE="app/src/main/java/com/pmca/bridge/queue/ExecutionQueue.java"
GRADLE="app/build.gradle"

for f in "$CONTROLLER" "$ROUTER" "$STATE" "$CONTEXT" "$QUEUE" "$GRADLE"; do
    if [ ! -f "$f" ]; then
        echo "ОШИБКА: отсутствует $f"
        exit 1
    fi
done

echo "Все обязательные файлы найдены."

echo
echo "===== 2. РЕЗЕРВНАЯ КОПИЯ ====="

mkdir -p "$BACKUP" || exit 1

tar -czf "$BACKUP/PMCA_source_before.tar.gz" \
    app build.gradle settings.gradle gradle.properties gradlew \
    2>"$BACKUP/backup_stderr.txt"

if [ $? -ne 0 ] || [ ! -s "$BACKUP/PMCA_source_before.tar.gz" ]; then
    echo "ОШИБКА: резервная копия не создана. Исходники не изменены."
    cat "$BACKUP/backup_stderr.txt"
    exit 1
fi

echo "Резервная копия: $BACKUP/PMCA_source_before.tar.gz"

echo
echo "===== 3. СОГЛАСОВАННОЕ ОБНОВЛЕНИЕ СОСТОЯНИЙ ====="

python - <<'PY'
from pathlib import Path

root = Path("app/src/main/java/com/pmca/bridge")

state_file = root / "core/ExecutionState.java"
state = state_file.read_text()

if "ACCEPTED" not in state:
    state = state.replace(
        "    RUNNING,\n    SUCCESS,",
        "    RUNNING,\n    ACCEPTED,\n    SUCCESS,"
    )

if "COMPLETED" not in state:
    state = state.replace(
        "    ACCEPTED,\n    SUCCESS,",
        "    ACCEPTED,\n    COMPLETED,\n    SUCCESS,"
    )

state_file.write_text(state)

router_file = root / "router/ModuleRouter.java"
router = router_file.read_text()

old = """            context.setState(accepted
                    ? com.pmca.bridge.core.ExecutionState.SUCCESS
                    : com.pmca.bridge.core.ExecutionState.ERROR);

            PmcaLogger.log("Router: " + context.getExecutionId()
                    + (accepted ? " принят исполнителем" : " отклонён"));"""

new = """            /*
             * ACCEPTED означает только, что исполнитель принял запрос.
             * Фактическое завершение подтверждается каналом результата
             * Termux, а не возвращаемым boolean.
             */
            context.setState(accepted
                    ? com.pmca.bridge.core.ExecutionState.ACCEPTED
                    : com.pmca.bridge.core.ExecutionState.ERROR);

            PmcaLogger.log("Router: " + context.getExecutionId()
                    + (accepted
                    ? " принят к запуску; ожидается результат Termux"
                    : " отклонён исполнителем"));"""

if old in router:
    router = router.replace(old, new)
elif "ExecutionState.ACCEPTED" not in router:
    raise SystemExit(
        "Не удалось безопасно обновить ModuleRouter: "
        "исходный блок отличается. Сборку не запускать."
    )

router_file.write_text(router)

queue_file = root / "queue/ExecutionQueue.java"
queue = queue_file.read_text()

old_log = '            return Boolean.TRUE.equals(operation.call());'
new_log = '''            boolean accepted = Boolean.TRUE.equals(operation.call());

            PmcaLogger.log("Queue: " + executionId
                    + (accepted
                    ? " — запрос обработан, результат ожидается отдельно"
                    : " — операция не принята"));

            return accepted;'''

if old_log in queue:
    queue = queue.replace(old_log, new_log)
elif "результат ожидается отдельно" not in queue:
    raise SystemExit(
        "Не удалось безопасно обновить ExecutionQueue."
    )

queue_file.write_text(queue)

controller_file = root / "core/PmcaController.java"
controller = controller_file.read_text()

old = '        execution.setState(ExecutionState.QUEUED);'
new = '''        execution.setState(ExecutionState.QUEUED);
        PmcaLogger.log("Controller: " + executionId
                + " поставлен в очередь");'''

if old in controller:
    controller = controller.replace(old, new)
elif "поставлен в очередь" not in controller:
    raise SystemExit(
        "Не удалось безопасно обновить PmcaController."
    )

controller_file.write_text(controller)

print("ExecutionState: обновлён")
print("ModuleRouter: ACCEPTED больше не считается SUCCESS")
print("ExecutionQueue: логирует принятие запроса отдельно от результата")
print("PmcaController: логирует переход в очередь")
PY

if [ $? -ne 0 ]; then
    echo "ОШИБКА обновления. Восстанавливаем исходники."

    tar -xzf "$BACKUP/PMCA_source_before.tar.gz" \
        -C "$PROJECT" 2>/dev/null

    echo "Восстановление выполнено."
    exit 1
fi

echo
echo "===== 4. ПРОВЕРКА ИЗМЕНЁННЫХ СОСТОЯНИЙ ====="

grep -n -A 12 'enum ExecutionState' \
    app/src/main/java/com/pmca/bridge/core/ExecutionState.java

grep -n -A 24 'boolean accepted = module.execute' \
    app/src/main/java/com/pmca/bridge/router/ModuleRouter.java

echo
echo "===== 5. СБОРКА ANDROID-ПРИЛОЖЕНИЯ ====="

if [ ! -x ./gradlew ]; then
    chmod +x ./gradlew
fi

./gradlew --no-daemon assembleDebug \
    > "$BACKUP/build.log" 2>&1

BUILD_STATUS=$?

if [ "$BUILD_STATUS" -ne 0 ]; then
    echo "ОШИБКА СБОРКИ. APK не заменяем."
    echo
    tail -n 100 "$BACKUP/build.log"
    echo
    echo "Исходники с изменениями сохранены для диагностики."
    echo "Резервная копия: $BACKUP/PMCA_source_before.tar.gz"
    exit 1
fi

echo "BUILD SUCCESSFUL"

echo
echo "===== 6. КОПИРОВАНИЕ APK ====="

SOURCE_APK="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -s "$SOURCE_APK" ]; then
    echo "ОШИБКА: APK не найден. Старый файл не заменяем."
    exit 1
fi

mkdir -p "$HOME/storage/downloads" || {
    echo "ОШИБКА: нет доступа к Downloads."
    exit 1
}

if ! cp -f "$SOURCE_APK" "$APK"; then
    echo "ОШИБКА копирования APK."
    exit 1
fi

echo "APK: $APK"
ls -lh "$APK"

echo
echo "===== 7. ИТОГОВЫЙ ОТЧЁТ ====="

{
    echo "PMCA_VERSION: $VERSION"
    echo "PMCA_BUILD: $BUILD"
    echo "Дата: $(date)"
    echo "Проект: $PROJECT"
    echo "Резервная копия: $BACKUP/PMCA_source_before.tar.gz"
    echo "APK: $APK"
    echo "Сборка: BUILD SUCCESSFUL"
    echo
    echo "Изменения:"
    echo "- Добавлено состояние ACCEPTED."
    echo "- Добавлено состояние COMPLETED для дальнейшего согласования."
    echo "- Принятие команды больше не обозначается как SUCCESS."
    echo "- Очередь журналирует принятие команды отдельно от результата."
    echo "- Контроллер журналирует помещение команды в очередь."
    echo
    echo "Важно:"
    echo "ACCEPTED не означает завершение команды."
    echo "Фактический результат по-прежнему приходит через существующий канал Termux."
    echo "Файловый мост и UI не удалялись."
    echo
    echo "Изменения исходников не означают, что проверены все сценарии устройства."
} > "$REPORT"

cat "$REPORT"

echo
echo "===== 8. АВТОКОПИРОВАНИЕ КРАТКОГО РЕЗУЛЬТАТА ====="

if command -v termux-clipboard-set >/dev/null 2>&1; then
    (
        timeout 3s termux-clipboard-set < "$REPORT"
    ) >/dev/null 2>&1 || true
    echo "Попытка копирования отчёта выполнена."
    echo "Если буфер недоступен, работа не прерывается."
else
    echo "termux-clipboard-set недоступен; отчёт сохранён в $REPORT"
fi

echo
echo "===== ГОТОВО — ОТПРАВЬ РЕЗУЛЬТАТ ====="
echo "PMCA_VERSION: $VERSION"
echo "PMCA_BUILD: $BUILD"
echo "ОТЧЁТ: $REPORT"
echo "РЕЗЕРВНАЯ КОПИЯ: $BACKUP"
echo "APK: $APK"
