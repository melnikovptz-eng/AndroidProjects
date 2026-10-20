package com.test.accessibility3;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class GptSenderLog {
    private static final String TAG = "GPTSenderTest";
    private static final String FILE_NAME = "gpt_sender.log";
    private static final String BACKUP_NAME = "gpt_sender.previous.log";
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final long MAX_BACKUP_BYTES = 512L * 1024L;
    private static final AtomicLong SEQUENCE = new AtomicLong(0);
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "gptsender-log-writer");
        thread.setDaemon(true);
        return thread;
    });

    private GptSenderLog() {}

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                .format(new Date());
    }

    private static String clean(String value) {
        if (value == null) return "";
        String safe = value.replace("\r", "\\r").replace("\n", "\\n").replace("|", "\\|");
        safe = safe.replaceAll("(?i)(password|token|secret|api[_-]?key)=\\S+", "$1=[REDACTED]");
        return safe.length() > 1200 ? safe.substring(0, 1200) + "…" : safe;
    }

    private static File logFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    private static String statusFor(String event, String level) {
        String name = event == null ? "" : event.toUpperCase(Locale.US);
        if ("ERROR".equals(level) || name.contains("FAILED") || name.contains("FAIL")
                || name.contains("EXCEPTION") || name.contains("REJECTED")
                || name.contains("TIMEOUT")) return "FAILED";
        if (name.contains("SUCCESS") || name.contains("COMPLETED")
                || name.contains("CONNECTED") || name.contains("READY")
                || name.contains("ACCEPTED")) return "SUCCESS";
        if (name.contains("BEGIN") || name.contains("START") || name.contains("REQUEST"))
            return "STARTED";
        return "RECORDED";
    }

    private static void enqueue(Context context, String level, String component,
                                String event, String details) {
        if (context == null) {
            Log.e(TAG, "LOG_CONTEXT_NULL event=" + clean(event));
            return;
        }
        final String safeEvent = clean(event);
        final String line = timestamp()
                + " | " + clean(level)
                + " | " + clean(component)
                + " | " + safeEvent
                + " | seq=" + SEQUENCE.incrementAndGet()
                + " | status=" + statusFor(event, level)
                + " | execution_id=null"
                + " | correlation_id=null"
                + " | details=" + clean(details)
                + "\n";
        final File file = logFile(context);
        WRITER.execute(() -> appendBounded(file, line.getBytes(StandardCharsets.UTF_8)));
        if ("ERROR".equals(level)) Log.e(TAG, line.trim());
        else if ("WARN".equals(level)) Log.w(TAG, line.trim());
        else Log.i(TAG, line.trim());
    }

    private static synchronized void appendBounded(File file, byte[] bytes) {
        try {
            if (file.exists() && file.length() + bytes.length > MAX_BYTES) {
                File backup = new File(file.getParentFile(), BACKUP_NAME);
                if (backup.exists() && !backup.delete()) return;
                if (!file.renameTo(backup)) return;
                if (backup.length() > MAX_BACKUP_BYTES) {
                    try (RandomAccessFile raf = new RandomAccessFile(backup, "r")) {
                        long start = Math.max(0L, raf.length() - MAX_BACKUP_BYTES);
                        byte[] tail = new byte[(int) Math.min(raf.length(), MAX_BACKUP_BYTES)];
                        raf.seek(start);
                        raf.readFully(tail);
                        try (FileOutputStream out = new FileOutputStream(backup, false)) {
                            out.write(tail);
                        }
                    }
                }
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(bytes);
            }
        } catch (Exception e) {
            // Never recurse into the logger or re-run the operation.
            Log.e(TAG, "LOG_WRITE_FAILED", e);
        }
    }

    public static void write(Context context, String event, String details) {
        enqueue(context, "INFO", "GPTSENDER", event, details);
    }

    public static void info(Context context, String component, String event, String details) {
        enqueue(context, "INFO", component, event, details);
    }

    public static void warn(Context context, String component, String event, String details) {
        enqueue(context, "WARN", component, event, details);
    }

    public static void error(Context context, String component, String event, String details) {
        enqueue(context, "ERROR", component, event, details);
    }

    public static void exception(Context context, String component, String event, Throwable throwable) {
        String type = throwable == null ? "UnknownException" : throwable.getClass().getSimpleName();
        // Avoid full stack traces and arbitrary exception messages in persistent logs by default.
        enqueue(context, "ERROR", component, event, "exception_type=" + type);
    }

    public static String beginOperation(Context context, String component, String operation) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        info(context, component, "OPERATION_BEGIN", "execution_id=" + id + "; name=" + operation);
        return id;
    }

    public static void endOperation(Context context, String component, String operationId,
                                    String operation, long startMillis, boolean success,
                                    String details) {
        long duration = Math.max(0L,
                android.os.SystemClock.elapsedRealtime() - startMillis);
        enqueue(context, success ? "INFO" : "ERROR", component,
                success ? "OPERATION_SUCCESS" : "OPERATION_FAILED",
                "execution_id=" + clean(operationId) + "; name=" + clean(operation)
                        + "; duration_ms=" + duration + "; " + clean(details));
    }

    public static String read(Context context) {
        if (context == null) return "ОШИБКА: Context отсутствует.";
        File file = logFile(context);
        if (!file.exists()) return "Журнал пока пуст.";
        try {
            long length = file.length();
            int max = 60_000;
            long skip = Math.max(0L, length - max);
            try (FileInputStream in = new FileInputStream(file);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (skip > 0) {
                    long remaining = skip;
                    while (remaining > 0) {
                        long n = in.skip(remaining);
                        if (n <= 0) break;
                        remaining -= n;
                    }
                }
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) >= 0 && out.size() < max) {
                    out.write(buffer, 0, Math.min(n, max - out.size()));
                }
                String text = out.toString("UTF-8");
                return skip > 0 ? "[Показана последняя часть журнала]\n" + text : text;
            }
        } catch (Exception e) {
            Log.e(TAG, "LOG_READ_FAILED", e);
            return "Ошибка чтения журнала: " + e.getClass().getSimpleName();
        }
    }

    public static String diagnostics(Context context) {
        if (context == null) return "Context отсутствует";
        File file = logFile(context);
        File backup = new File(context.getApplicationContext().getFilesDir(), BACKUP_NAME);
        return "logger_version=3"
                + "\nlog_exists=" + file.exists()
                + "\nlog_bytes=" + (file.exists() ? file.length() : 0)
                + "\nprevious_log_exists=" + backup.exists()
                + "\nprevious_log_bytes=" + (backup.exists() ? backup.length() : 0)
                + "\nsequence=" + SEQUENCE.get();
    }
}
