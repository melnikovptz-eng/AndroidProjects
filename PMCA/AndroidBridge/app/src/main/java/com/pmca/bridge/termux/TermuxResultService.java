package com.pmca.bridge.termux;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.pmca.bridge.core.DuplicateGuard;
import com.pmca.bridge.logging.PmcaLogger;

import java.util.Locale;

public final class TermuxResultService extends IntentService {

    public static final String ACTION_RESULT =
            "com.pmca.bridge.TERMUX_RESULT";

    private static final String RESULT_BUNDLE_KEY =
            "com.termux.app.shell.TermuxService.EXTRA_PLUGIN_RESULT_BUNDLE";

    public TermuxResultService() {
        super("PMCA-TermuxResultService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            PmcaLogger.log("Результат Termux: получен пустой Intent");
            return;
        }

        String guardToken =
                intent.getStringExtra(DuplicateGuard.EXTRA_TOKEN);

        try {
            Bundle extras = intent.getExtras();

            Bundle result = null;
            String source = "не найден";

            try {
                result = intent.getBundleExtra(RESULT_BUNDLE_KEY);
                if (result != null) {
                    source = "стандартный ключ";
                }
            } catch (Exception e) {
                PmcaLogger.log("Ошибка чтения стандартного Bundle");
            }

            if (result == null && extras != null) {
                result = findResultBundle(extras, 0);
                if (result != null) {
                    source = "вложенный Bundle с полями результата";
                }
            }

            if (result == null && extras != null
                    && containsResultFields(extras)) {
                result = extras;
                source = "extras Intent";
            }

            StringBuilder output = new StringBuilder();

            output.append("===== РЕЗУЛЬТАТ TERMUX =====\n")
                    .append("Источник: ")
                    .append(source)
                    .append("\n");

            if (result == null) {
                output.append("\nОШИБКА: не удалось найти поля результата.")
                        .append("\nAction: ")
                        .append(String.valueOf(intent.getAction()));

                if (extras != null) {
                    output.append("\nКлючи Intent:");
                    for (String key : extras.keySet()) {
                        output.append("\n- ").append(key);
                    }
                }

                PmcaLogger.log(
                        "RUN_COMMAND: callback без распознанного результата");

                saveAndBroadcast(output.toString(), guardToken);
                return;
            }

            String stdout = findString(
                    result, "stdout", "standard_output");

            String stderr = findString(
                    result, "stderr", "standard_error");

            String errorMessage = findString(
                    result,
                    "errmsg",
                    "error_message",
                    "err",
                    "exception");

            int exitCode = findInt(
                    result,
                    -999,
                    "exit_code",
                    "exitCode",
                    "exitcode");

            int errorCode = findInt(
                    result,
                    -999,
                    "plugin_result_bundle_err",
                    "error_code",
                    "errorCode");

            output.append("\n===== STDOUT =====\n")
                    .append(stdout.isEmpty() ? "(пусто)" : stdout)
                    .append("\n\n===== STDERR =====\n")
                    .append(stderr.isEmpty() ? "(пусто)" : stderr)
                    .append("\n\nКод завершения: ")
                    .append(exitCode == -999
                            ? "не передан Termux"
                            : String.valueOf(exitCode));

            if (!errorMessage.isEmpty()) {
                output.append("\n\n===== ОШИБКА TERMUX =====\n")
                        .append(errorMessage);
            }

            if (errorCode != -999) {
                output.append("\nВнутренний код ошибки: ")
                        .append(errorCode);
            }

            PmcaLogger.log(
                    "RUN_COMMAND: результат разобран; source=" + source);

            saveAndBroadcast(output.toString(), guardToken);

        } catch (Exception e) {
            String message =
                    "===== ОШИБКА ОБРАБОТКИ TERMUX =====\n"
                    + e.getClass().getSimpleName()
                    + ": "
                    + String.valueOf(e.getMessage());

            PmcaLogger.log("TermuxResultService: " + message);
            saveAndBroadcast(message, guardToken);
        }
    }

    private Bundle findResultBundle(Bundle bundle, int depth) {
        if (bundle == null || depth > 5) {
            return null;
        }

        if (containsResultFields(bundle)) {
            return bundle;
        }

        for (String key : bundle.keySet()) {
            try {
                Object value = bundle.get(key);

                if (value instanceof Bundle) {
                    Bundle candidate =
                            findResultBundle((Bundle) value, depth + 1);

                    if (candidate != null) {
                        return candidate;
                    }
                }
            } catch (Exception e) {
                PmcaLogger.log(
                        "Не удалось прочитать вложенный Bundle: " + key);
            }
        }

        return null;
    }

    private boolean containsResultFields(Bundle bundle) {
        if (bundle == null) {
            return false;
        }

        for (String key : bundle.keySet()) {
            if (key == null) {
                continue;
            }

            String normalized = normalize(key);

            if (normalized.equals("STDOUT")
                    || normalized.endsWith("STDOUT")
                    || normalized.equals("STDERR")
                    || normalized.endsWith("STDERR")
                    || normalized.equals("EXITCODE")
                    || normalized.endsWith("EXITCODE")
                    || normalized.equals("ERRMSG")
                    || normalized.endsWith("ERRMSG")
                    || normalized.equals("ERRORMESSAGE")
                    || normalized.endsWith("ERRORMESSAGE")) {
                return true;
            }
        }

        return false;
    }

    private void saveAndBroadcast(String output, String guardToken) {
        String pmcaResultState =
                com.pmca.bridge.result.ResultManager.recordTermuxResult(
                        this, guardToken, output);

        com.pmca.bridge.logging.PmcaLogger.log(
                "ResultManager: операция " + guardToken
                        + ", состояние=" + pmcaResultState);

        // Версия и номер сборки берутся из метаданных APK.
        output = "PMCA_VERSION: " + getPackageVersionName()
                + "\nPMCA_BUILD: " + getPackageVersionCode()
                + "\n\n"
                + output
                + "\n\n===== КОНЕЦ РЕЗУЛЬТАТА PMCA "
                + getPackageVersionName()
                + " / BUILD " + getPackageVersionCode() + " =====";


        try {
            SharedPreferences prefs =
                    getSharedPreferences("pmca_results", MODE_PRIVATE);

            // Записываем только уже полученный callback.
            // Результат сохраняется до отправки уведомления панели.
            prefs.edit()
                    .putString("last_result", output)
                    .putLong("last_result_time", System.currentTimeMillis())
                    .putInt("compact_state", 3)
                    .commit();

            Intent update = new Intent(ACTION_RESULT);
            update.setPackage(getPackageName());
            update.putExtra("result_text", output);

            sendBroadcast(update);

            PmcaLogger.log(
                    "RUN_COMMAND RESULT: результат сохранён и отправлен");

        } catch (Exception e) {
            PmcaLogger.log(
                    "Ошибка сохранения результата: "
                    + e.getClass().getSimpleName()
                    + ": "
                    + String.valueOf(e.getMessage()));
        } finally {
            DuplicateGuard.finish(guardToken);
        }
    }

    private String getPackageVersionName() {
        try {
            android.content.pm.PackageInfo info =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "unknown" : info.versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getPackageVersionCode() {
        try {
            android.content.pm.PackageInfo info =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                return String.valueOf(info.getLongVersionCode());
            }
            //noinspection deprecation
            return String.valueOf(info.versionCode);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String findString(Bundle bundle, String... names) {
        if (bundle == null) {
            return "";
        }

        for (String key : bundle.keySet()) {
            if (key == null || key.endsWith("_ORIGINAL_LENGTH")) {
                continue;
            }

            if (!matches(key, names)) {
                continue;
            }

            try {
                Object value = bundle.get(key);

                if (value instanceof String) {
                    return (String) value;
                }

                if (value instanceof CharSequence) {
                    return value.toString();
                }
            } catch (Exception ignored) {
            }
        }

        return "";
    }

    private static int findInt(
            Bundle bundle, int fallback, String... names) {

        if (bundle == null) {
            return fallback;
        }

        for (String key : bundle.keySet()) {
            if (key == null || !matches(key, names)) {
                continue;
            }

            try {
                Object value = bundle.get(key);

                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }

                if (value instanceof String) {
                    return Integer.parseInt(
                            ((String) value).trim());
                }
            } catch (Exception ignored) {
            }
        }

        return fallback;
    }

    private static boolean matches(String key, String... names) {
        String normalizedKey = normalize(key);

        for (String name : names) {
            String normalizedName = normalize(name);

            if (normalizedKey.equals(normalizedName)
                    || normalizedKey.endsWith(normalizedName)) {
                return true;
            }
        }

        return false;
    }

    private static String normalize(String value) {
        return value.toUpperCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace(".", "");
    }
}
