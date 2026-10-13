package com.pmca.bridge.termux;

import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Environment;

import com.pmca.bridge.logging.PmcaLogger;
import com.pmca.bridge.core.DuplicateGuard;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class TermuxBridgeAdapter {
    private final File bridgeDir;

    public TermuxBridgeAdapter(Context context) {
        File downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        bridgeDir = new File(downloads, "Taper");
    }

    public static final class Submission {
        public final boolean accepted;
        public final String operationId;
        public final String message;

        Submission(boolean accepted, String operationId, String message) {
            this.accepted = accepted;
            this.operationId = operationId;
            this.message = message;
        }
    }

    public Submission submitShellCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return new Submission(false, "", "Пустая команда");
        }

        File commandFile = new File(bridgeDir, "command.json");
        File tempFile = new File(
                bridgeDir, "command.pmca_" + UUID.randomUUID() + ".tmp");

        String operationId = UUID.randomUUID().toString();

        try {
            if (!bridgeDir.isDirectory() && !bridgeDir.mkdirs()) {
                return new Submission(false, operationId,
                        "Папка моста недоступна");
            }

            // Не затираем команду, которую ещё обрабатывает демон.
            if (commandFile.exists()) {
                PmcaLogger.log(
                        "Termux: предыдущая команда ещё не обработана");
                return new Submission(false, operationId,
                        "Предыдущая команда ещё обрабатывается");
            }

            JSONObject json = new JSONObject();
            json.put("operation_id", operationId);
            json.put("type", "PMCA");
            json.put("command", "SHELL");
            json.put("data", command);

            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);

            try (FileOutputStream out = new FileOutputStream(tempFile, false)) {
                out.write(bytes);
                out.getFD().sync();
            }

            // Повторная проверка перед передачей команды.
            if (commandFile.exists()) {
                return new Submission(false, operationId,
                        "Мост занят другой командой");
            }

            if (!tempFile.renameTo(commandFile)) {
                return new Submission(false, operationId,
                        "Не удалось передать команду мосту");
            }

            PmcaLogger.log("Termux: команда отправлена, ID=" + operationId);
            return new Submission(true, operationId, "Команда передана");

        } catch (Exception e) {
            PmcaLogger.log("Termux: ошибка отправки: "
                    + e.getClass().getSimpleName());
            return new Submission(false, operationId,
                    "Ошибка отправки: " + e.getClass().getSimpleName());
        } finally {
            // Удаляем только собственный временный файл.
            if (tempFile.exists()) {
                // Оставляем его при ошибке для диагностики.
            }
        }
    }

    public String waitForResult(String operationId, long timeoutMs) {
        if (operationId == null || operationId.isEmpty()) {
            return "ОШИБКА: отсутствует ID команды";
        }

        File resultFile = new File(bridgeDir, "result.json");
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            try {
                if (resultFile.isFile() && resultFile.length() > 0) {
                    byte[] bytes = java.nio.file.Files.readAllBytes(
                            resultFile.toPath());

                    JSONObject result = new JSONObject(
                            new String(bytes, StandardCharsets.UTF_8));

                    String resultId = result.optString("operation_id", "");

                    // Старый ответ не принимается.
                    if (operationId.equals(resultId)) {
                        String status = result.optString("status", "UNKNOWN");
                        String response = result.optString(
                                "response", "Ответ пуст");

                        PmcaLogger.log(
                                "Termux: получен ответ для ID=" + operationId);

                        return "Статус: " + status + "\n"
                                + "ID: " + operationId + "\n\n"
                                + response;
                    }
                }
            } catch (Exception ignored) {
                // Демон может ещё записывать JSON. Повторяем чтение.
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "ОЖИДАНИЕ ПРЕРВАНО";
            }
        }

        PmcaLogger.log("Termux: тайм-аут ожидания ID=" + operationId);
        return "ТАЙМ-АУТ: ответ пока не получен.\n"
                + "Команда могла выполниться; проверь журнал Termux.\n"
                + "ID: " + operationId;
    }

    // Совместимость с прежним интерфейсом контроллера.
    public synchronized boolean sendShellCommand(String command) {
        return submitShellCommand(command).accepted;
    }

    /**
     * Запускает команду через штатный Termux RUN_COMMAND API.
     * Результат возвращается в TermuxResultService.
     */
    public boolean runNativeCommand(Context context, String command) {
        if (command == null || command.trim().isEmpty()) {
            PmcaLogger.log("RUN_COMMAND: пустая команда");
            return false;
        }

        String guardToken = DuplicateGuard.begin(command);
        if (guardToken == null) {
            PmcaLogger.log(
                    "DuplicateGuard: повторная активная команда заблокирована");
            return false;
        }

        try {
            Intent resultIntent = new Intent(context, TermuxResultService.class);
            resultIntent.putExtra(DuplicateGuard.EXTRA_TOKEN, guardToken);
            int requestCode = (int) (System.currentTimeMillis() & 0x7fffffff);

            int flags = PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= 31) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getService(
                    context, requestCode, resultIntent, flags);

            Intent run = new Intent();
            run.setClassName("com.termux", "com.termux.app.RunCommandService");
            run.setAction("com.termux.RUN_COMMAND");
            run.putExtra("com.termux.RUN_COMMAND_PATH",
                    "/data/data/com.termux/files/usr/bin/bash");
            run.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
                    new String[]{"-lc", command});
            run.putExtra("com.termux.RUN_COMMAND_WORKDIR",
                    "/data/data/com.termux/files/home");
            run.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            run.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent);
            run.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "PMCA");
            run.putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION",
                    "Команда запущена из PMCA");

            context.startService(run);
            PmcaLogger.log("RUN_COMMAND: команда передана Termux");
            return true;
        } catch (Exception e) {
            DuplicateGuard.finish(guardToken);
            PmcaLogger.log("RUN_COMMAND: ошибка запуска: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

}
