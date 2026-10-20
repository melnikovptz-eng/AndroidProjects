package com.pmca.bridge.logging;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded UI ring buffer plus optional persistent app-private JSONL operational log. */
public final class PmcaLogger {
    private static final int MAX_LINES = 50;
    private static final long MAX_LOG_BYTES = 512 * 1024L;
    private static final long MAX_ARCHIVE_BYTES = 256 * 1024L;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "pmca-log-writer");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile File persistentFile;

    private PmcaLogger() {}

    /** Call once from Application.onCreate or the main Activity before logging. */
    public static void initialize(Context context) {
        if (context == null) return;
        persistentFile = new File(context.getApplicationContext().getFilesDir(), "pmca.log");
    }

    public static void log(String message) {
        String safe = redact(message);
        String time = timestamp();
        String line = time + "  " + safe;
        synchronized (PmcaLogger.class) {
            while (LINES.size() >= MAX_LINES) LINES.removeFirst();
            LINES.addLast(line);
        }
        File target = persistentFile;
        if (target != null) {
            String event = field(safe, "event", "PMCA_LEGACY_EVENT");
            String status = field(safe, "status", "RECORDED");
            String stage = field(safe, "stage", "GENERAL");
            String executionId = field(safe, "execution_id", null);
            String correlationId = field(safe, "correlation_id", null);
            String duration = field(safe, "duration_ms", null);
            String errorCode = "FAILED".equals(status) || "REJECTED".equals(status)
                    ? field(safe, "error", "OPERATION_FAILED") : null;
            String jsonLine = "{\"timestamp\":\"" + json(time)
                    + "\",\"level\":\"" + ("FAILED".equals(status) || "REJECTED".equals(status) ? "ERROR" : "INFO")
                    + "\",\"event\":\"" + json(event)
                    + "\",\"status\":\"" + json(status)
                    + "\",\"stage\":\"" + json(stage)
                    + "\",\"execution_id\":" + nullableJson(executionId)
                    + ",\"correlation_id\":" + nullableJson(correlationId)
                    + ",\"module_id\":\"pmca.androidbridge\""
                    + ",\"duration_ms\":" + (duration != null && duration.matches("\\d+") ? duration : "null")
                    + ",\"error_code\":" + nullableJson(errorCode)
                    + ",\"details\":\"" + json(safe) + "\"}\n";
            byte[] bytes = jsonLine.getBytes(StandardCharsets.UTF_8);
            WRITER.execute(() -> appendBounded(target, bytes));
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                .format(new Date());
    }

    private static String redact(String input) {
        if (input == null) return "event=UNKNOWN";
        String value = input.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        value = value.replaceAll("(?i)(password|token|secret|api[_-]?key)=\\S+", "$1=[REDACTED]");
        return value.length() > 700 ? value.substring(0, 700) + "…" : value;
    }

    private static String field(String text, String key, String fallback) {
        Matcher matcher = Pattern.compile("(?:^|[; ,])" + Pattern.quote(key)
                + "=([A-Za-z0-9_.:-]+)").matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String nullableJson(String value) {
        return value == null ? "null" : "\"" + json(value) + "\"";
    }

    private static String json(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }

    private static synchronized void appendBounded(File file, byte[] bytes) {
        try {
            if (file.exists() && file.length() + bytes.length > MAX_LOG_BYTES) {
                File archive = new File(file.getParentFile(), "pmca.log.1");
                if (archive.exists() && !archive.delete()) return;
                if (!file.renameTo(archive)) return;
                if (archive.length() > MAX_ARCHIVE_BYTES) {
                    try (RandomAccessFile raf = new RandomAccessFile(archive, "r")) {
                        long start = Math.max(0L, raf.length() - MAX_ARCHIVE_BYTES);
                        byte[] tail = new byte[(int) Math.min(raf.length(), MAX_ARCHIVE_BYTES)];
                        raf.seek(start);
                        raf.readFully(tail);
                        try (FileOutputStream out = new FileOutputStream(archive, false)) {
                            out.write(tail);
                        }
                    }
                }
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(bytes);
            }
        } catch (Exception ignored) {
            // Never recurse into the logger or re-run the operation.
        }
    }

    public static synchronized List<String> getLastLines() {
        return new ArrayList<>(LINES);
    }

    public static synchronized String getText() {
        StringBuilder result = new StringBuilder();
        for (String line : LINES) {
            if (result.length() > 0) result.append('\n');
            result.append(line);
        }
        return result.toString();
    }
}
