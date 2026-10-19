package com.test.accessibility3;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class GptSenderLog {
    private static final String TAG = "GPTSenderTest";
    private static final String FILE_NAME = "gpt_sender.log";
    private static final String BACKUP_NAME = "gpt_sender.previous.log";
    private static final long MAX_BYTES = 2L * 1024L * 1024L;

    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    private GptSenderLog() {}

    private static String timestamp() {
        return new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.US
        ).format(new Date());
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace("\r", "\\r")
                    .replace("\n", "\\n")
                    .replace("|", "\\|");
    }

    private static File logFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    private static void rotateIfNeeded(File file) {
        try {
            if (!file.exists() || file.length() < MAX_BYTES) return;

            File backup = new File(file.getParentFile(), BACKUP_NAME);
            if (backup.exists()) backup.delete();

            if (!file.renameTo(backup)) {
                // Если переименование невозможно, обрезаем журнал безопасно.
                try (FileOutputStream out = new FileOutputStream(file, false)) {
                    out.write(
                        (timestamp() +
                         " | WARN | LOGGER | LOG_ROTATION_FALLBACK\n")
                        .getBytes(StandardCharsets.UTF_8)
                    );
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "LOG_ROTATION_FAILED", e);
        }
    }

    public static synchronized void write(
            Context context, String event, String details) {
        if (context == null) {
            Log.e(TAG, "LOG_CONTEXT_NULL event=" + clean(event));
            return;
        }

        String line = timestamp()
            + " | INFO | "
            + clean(event)
            + " | seq="
            + SEQUENCE.incrementAndGet()
            + " | "
            + clean(details)
            + "\n";

        File file = logFile(context);

        try {
            rotateIfNeeded(file);

            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
            }
        } catch (Exception e) {
            Log.e(TAG, "LOG_WRITE_FAILED event=" + clean(event), e);
        }

        Log.i(TAG, line.trim());
    }

    public static void info(Context context, String component,
                            String event, String details) {
        writeLevel(context, "INFO", component, event, details);
    }

    public static void warn(Context context, String component,
                            String event, String details) {
        writeLevel(context, "WARN", component, event, details);
    }

    public static void error(Context context, String component,
                             String event, String details) {
        writeLevel(context, "ERROR", component, event, details);
    }

    private static synchronized void writeLevel(
            Context context, String level, String component,
            String event, String details) {
        if (context == null) {
            Log.e(TAG, "LOG_CONTEXT_NULL event=" + clean(event));
            return;
        }

        String line = timestamp()
            + " | " + clean(level)
            + " | " + clean(component)
            + " | " + clean(event)
            + " | seq=" + SEQUENCE.incrementAndGet()
            + " | " + clean(details)
            + "\n";

        try {
            File file = logFile(context);
            rotateIfNeeded(file);

            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
            }
        } catch (Exception e) {
            Log.e(TAG, "LOG_WRITE_FAILED event=" + clean(event), e);
        }

        if ("ERROR".equals(level)) {
            Log.e(TAG, line.trim());
        } else if ("WARN".equals(level)) {
            Log.w(TAG, line.trim());
        } else {
            Log.i(TAG, line.trim());
        }
    }

    public static void exception(
            Context context, String component, String event,
            Throwable throwable) {
        StringWriter buffer = new StringWriter();

        if (throwable != null) {
            throwable.printStackTrace(new PrintWriter(buffer));
        } else {
            buffer.append("Throwable is null");
        }

        writeLevel(
            context,
            "ERROR",
            component,
            event,
            buffer.toString()
        );
    }

    public static String beginOperation(
            Context context, String component, String operation) {
        String id = UUID.randomUUID().toString().substring(0, 8);

        info(
            context, component, "OPERATION_BEGIN",
            "id=" + id + "; name=" + operation
        );

        return id;
    }

    public static void endOperation(
            Context context, String component, String operationId,
            String operation, long startMillis, boolean success,
            String details) {
        long duration = Math.max(0L,
            android.os.SystemClock.elapsedRealtime() - startMillis);

        writeLevel(
            context,
            success ? "INFO" : "ERROR",
            component,
            success ? "OPERATION_SUCCESS" : "OPERATION_FAILED",
            "id=" + operationId
                + "; name=" + operation
                + "; duration_ms=" + duration
                + "; details=" + details
        );
    }

    public static String read(Context context) {
        if (context == null) return "ОШИБКА: Context отсутствует.";

        File file = logFile(context);
        if (!file.exists()) return "Журнал пока пуст.";

        try {
            long length = file.length();
            int max = 60000;
            long skip = Math.max(0L, length - max);

            try (FileInputStream in = new FileInputStream(file)) {
                if (skip > 0) {
                    long remaining = skip;
                    while (remaining > 0) {
                        long n = in.skip(remaining);
                        if (n <= 0) break;
                        remaining -= n;
                    }
                }

                byte[] bytes = new byte[max];
                int n = in.read(bytes);

                String text = n > 0
                    ? new String(bytes, 0, n, StandardCharsets.UTF_8)
                    : "Журнал пока пуст.";

                if (skip > 0) {
                    return "[Показана последняя часть журнала]\n" + text;
                }
                return text;
            }
        } catch (Exception e) {
            Log.e(TAG, "LOG_READ_FAILED", e);
            return "Ошибка чтения журнала: "
                + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    public static String diagnostics(Context context) {
        if (context == null) return "Context отсутствует";

        File file = logFile(context);
        File backup = new File(context.getFilesDir(), BACKUP_NAME);

        return "logger_version=2"
            + "\nlog_exists=" + file.exists()
            + "\nlog_bytes=" + (file.exists() ? file.length() : 0)
            + "\nprevious_log_exists=" + backup.exists()
            + "\nprevious_log_bytes=" + (backup.exists() ? backup.length() : 0)
            + "\nsequence=" + SEQUENCE.get();
    }
}
