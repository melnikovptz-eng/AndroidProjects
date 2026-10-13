#!/data/data/com.termux/files/usr/bin/bash

set -u

PROJECT="$HOME/PMCA/AndroidBridge"
STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP="$HOME/PMCA_BACKUP_RESULT_$STAMP"
REPORT="$HOME/PMCA_RESULT_LIFECYCLE_REPORT.txt"
APK="$HOME/storage/downloads/PMCA.apk"

cd "$PROJECT" || exit 1

echo "===== PMCA: ЖИЗНЕННЫЙ ЦИКЛ РЕЗУЛЬТАТА ====="
echo "PMCA_VERSION: 0.4.0"
echo "PMCA_BUILD: 5"
echo

SERVICE="app/src/main/java/com/pmca/bridge/termux/TermuxResultService.java"
STATE="app/src/main/java/com/pmca/bridge/core/ExecutionState.java"
GRADLE="app/build.gradle"

for f in "$SERVICE" "$STATE" "$GRADLE"; do
    if [ ! -f "$f" ]; then
        echo "ОШИБКА: отсутствует $f"
        exit 1
    fi
done

echo "===== 1. РЕЗЕРВНАЯ КОПИЯ ====="

mkdir -p "$BACKUP" || exit 1

tar -czf "$BACKUP/source.tar.gz" \
    app build.gradle settings.gradle gradle.properties gradlew \
    2>"$BACKUP/backup.log"

if [ $? -ne 0 ] || [ ! -s "$BACKUP/source.tar.gz" ]; then
    echo "ОШИБКА: резервная копия не создана. Изменений нет."
    exit 1
fi

echo "Создана: $BACKUP/source.tar.gz"

echo
echo "===== 2. СОЗДАНИЕ МЕНЕДЖЕРА СОСТОЯНИЙ РЕЗУЛЬТАТА ====="

python - <<'PY'
from pathlib import Path
import re

root = Path("app/src/main/java/com/pmca/bridge")
state_file = root / "core/ExecutionState.java"
state = state_file.read_text()

if "RESULT_RECEIVED" not in state:
    marker = "    ACCEPTED,"
    if marker not in state:
        raise SystemExit("Не найдено состояние ACCEPTED. Остановка.")
    state = state.replace(
        marker,
        marker + "\n    RESULT_RECEIVED,",
        1
    )
    state_file.write_text(state)

result_dir = root / "result"
result_dir.mkdir(parents=True, exist_ok=True)

manager = result_dir / "ResultManager.java"

manager.write_text(r'''package com.pmca.bridge.result;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Единая точка фиксации состояния полученного результата Termux.
 *
 * Не запускает команды и не создаёт фоновых потоков.
 * Не заменяет существующий канал доставки результата.
 */
public final class ResultManager {
    private static final String PREFS = "pmca_execution_lifecycle";

    private ResultManager() {}

    public static String recordTermuxResult(
            Context context,
            String operationId,
            String output) {

        String text = output == null ? "" : output;
        String id = operationId == null || operationId.trim().isEmpty()
                ? "UNKNOWN"
                : operationId;

        String state = classify(text);

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        prefs.edit()
                .putString("last_operation_id", id)
                .putString("last_operation_state", state)
                .putLong("last_operation_time", System.currentTimeMillis())
                .putString("last_operation_result", text)
                .apply();

        return state;
    }

    private static String classify(String output) {
        /*
         * Считаем операцию завершённой успешно только при наличии
         * явного нулевого кода завершения.
         */
        Pattern[] patterns = new Pattern[] {
            Pattern.compile(
                "(?im)^\\s*(?:Код завершения|exit_code|exitCode)\\s*[:=]\\s*(-?\\d+)\\s*$"
            ),
            Pattern.compile(
                "(?im)^\\s*EXIT_CODE\\s*=\\s*(-?\\d+)\\s*$"
            )
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(output);
            if (matcher.find()) {
                try {
                    int code = Integer.parseInt(matcher.group(1));
                    return code == 0 ? "COMPLETED" : "ERROR";
                } catch (NumberFormatException ignored) {
                    return "RESULT_RECEIVED";
                }
            }
        }

        /*
         * Не подменяем отсутствие кода завершения успешным статусом.
         * Сам результат получен, но его окончательный статус неизвестен.
         */
        return "RESULT_RECEIVED";
    }
}
''')

service_file = root / "termux/TermuxResultService.java"
service = service_file.read_text()

signature = re.compile(
    r'(private\s+void\s+saveAndBroadcast\s*\(\s*'
    r'String\s+output\s*,\s*String\s+guardToken\s*\)\s*\{)'
)

if "ResultManager.recordTermuxResult(" not in service:
    match = signature.search(service)

    if not match:
        raise SystemExit(
            "Не найдена ожидаемая сигнатура saveAndBroadcast. "
            "Безопасная остановка."
        )

    insertion = match.group(1) + '''
        String pmcaResultState =
                com.pmca.bridge.result.ResultManager.recordTermuxResult(
                        this, guardToken, output);

        com.pmca.bridge.logging.PmcaLogger.log(
                "ResultManager: операция " + guardToken
                        + ", состояние=" + pmcaResultState);
'''
    service = service[:match.start()] + insertion + service[match.end():]
    service_file.write_text(service)

print("ExecutionState: добавлено RESULT_RECEIVED")
print("ResultManager.java: создан")
print("TermuxResultService: подключён к ResultManager")
PY

if [ $? -ne 0 ]; then
    echo "ОШИБКА изменения исходников."
    echo "Восстанавливаем исходную копию."
    tar -xzf "$BACKUP/source.tar.gz" -C "$PROJECT" 2>/dev/null
    exit 1
fi

echo
echo "===== 3. СОГЛАСОВАНИЕ ВЕРСИИ GRADLE ====="

python - <<'PY'
from pathlib import Path
import re

p = Path("app/build.gradle")
s = p.read_text()

version = re.sub(
    r'versionName\s+["\'][^"\']+["\']',
    'versionName "0.4.0"',
    s,
    count=1
)

code = re.sub(
    r'versionCode\s+\d+',
    'versionCode 5',
    version,
    count=1
)

if version == s or code == version:
    raise SystemExit(
        "Не удалось определить оба поля versionCode/versionName. "
        "Сборка остановлена."
    )

p.write_text(code)
print("versionName = 0.4.0")
print("versionCode = 5")
PY

if [ $? -ne 0 ]; then
    echo "ОШИБКА версии Gradle. Восстанавливаем исходники."
    tar -xzf "$BACKUP/source.tar.gz" -C "$PROJECT" 2>/dev/null
    exit 1
fi

echo
echo "===== 4. ПРОВЕРКА ИНТЕГРАЦИИ ====="

grep -n -A 9 'saveAndBroadcast' "$SERVICE" | head -n 24

echo
echo "===== 5. СБОРКА ====="

./gradlew --no-daemon assembleDebug \
    > "$BACKUP/build.log" 2>&1

if [ $? -ne 0 ]; then
    echo "ОШИБКА СБОРКИ. Старый APK не заменяем."
    tail -n 100 "$BACKUP/build.log"
    echo
    echo "Исходники восстанавливаются из резервной копии."
    tar -xzf "$BACKUP/source.tar.gz" -C "$PROJECT" 2>/dev/null
    exit 1
fi

SOURCE_APK="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -s "$SOURCE_APK" ]; then
    echo "ОШИБКА: собранный APK не найден. Старый APK не заменяем."
    exit 1
fi

echo "BUILD SUCCESSFUL"

echo
echo "===== 6. УСТАНОВКА ФАЙЛА В DOWNLOADS ====="

mkdir -p "$HOME/storage/downloads" || exit 1
cp -f "$SOURCE_APK" "$APK" || {
    echo "ОШИБКА копирования APK."
    exit 1
}

ls -lh "$APK"

echo
echo "===== 7. ИТОГ ====="

{
    echo "PMCA_VERSION: 0.4.0"
    echo "PMCA_BUILD: 5"
    echo "Сборка: BUILD SUCCESSFUL"
    echo "APK: $APK"
    echo "Резервная копия: $BACKUP/source.tar.gz"
    echo
    echo "Добавлен ResultManager."
    echo "Результат Termux фиксируется в SharedPreferences."
    echo "Код 0 означает COMPLETED."
    echo "Ненулевой код означает ERROR."
    echo "Без явного кода результат получает RESULT_RECEIVED."
    echo
    echo "Существующий файловый мост не удалялся."
    echo "UI не перестраивался."
    echo "Получение результата продолжает использовать TermuxResultService."
} > "$REPORT"

cat "$REPORT"

echo
echo "===== 8. КОПИРОВАНИЕ ОТЧЁТА ====="

if command -v termux-clipboard-set >/dev/null 2>&1; then
    (timeout 3s termux-clipboard-set < "$REPORT") \
        >/dev/null 2>&1 || true
    echo "Попытка автокопирования выполнена."
else
    echo "Clipboard API недоступен; отчёт сохранён в $REPORT"
fi

echo
echo "===== ГОТОВО — ОТПРАВЬ РЕЗУЛЬТАТ ====="
echo "PMCA_VERSION: 0.4.0"
echo "PMCA_BUILD: 5"
echo "ОТЧЁТ: $REPORT"
echo "APK: $APK"
echo "BACKUP: $BACKUP"
