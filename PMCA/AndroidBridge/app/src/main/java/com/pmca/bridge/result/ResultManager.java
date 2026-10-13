package com.pmca.bridge.result;

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
