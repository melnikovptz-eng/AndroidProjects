package com.test.accessibility3.modules.debug;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * App-private bounded logger. Payload content is never written by default.
 * Writes are serialized on a dedicated worker so UI calls do not perform file I/O.
 */
public final class DebugLogger {
    private static final String FILE_NAME = "debug_log.txt";
    private static final String LEGACY_FILE_NAME = "last_scan.txt";
    private static final long MAX_LOG_BYTES = 512 * 1024L;
    private static final long MAX_ARCHIVE_BYTES = 256 * 1024L;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "gptsender-log-writer");
        thread.setDaemon(true);
        return thread;
    });

    private DebugLogger() {}

    private static String time() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                .format(new Date());
    }

    public static void log(Context context, String level, String message) {
        if (context == null) return;
        final File file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
        final String safeLevel = safeToken(level, "INFO");
        final String safeMessage = redact(message);
        final byte[] bytes = ("{\"timestamp\":\"" + time()
                + "\",\"level\":\"" + safeLevel
                + "\",\"event\":\"LEGACY_LOG\",\"status\":\"RECORDED\",\"stage\":\"DIAGNOSTIC\","
                + "\"execution_id\":null,\"correlation_id\":null,\"module_id\":\"gptsender.test\","
                + "\"duration_ms\":null,\"error_code\":null,\"details\":\""
                + jsonEscape(safeMessage) + "\"}\n").getBytes(StandardCharsets.UTF_8);
        WRITER.execute(() -> appendBounded(file, bytes));
    }

    private static String safeToken(String value, String fallback) {
        if (value == null || !value.matches("[A-Za-z_]{1,16}")) return fallback;
        return value.toUpperCase(Locale.US);
    }

    private static String redact(String value) {
        if (value == null) return "";
        // Cap untrusted diagnostic text; payloads must be represented by length only.
        String clean = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return clean.length() > 600 ? clean.substring(0, 600) + "…" : clean;
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\b", "\\b").replace("\f", "\\f");
    }

    private static synchronized void appendBounded(File file, byte[] bytes) {
        try {
            if (file.exists() && file.length() + bytes.length > MAX_LOG_BYTES) {
                File archive = new File(file.getParentFile(), FILE_NAME + ".1");
                if (archive.exists() && !archive.delete()) return;
                if (!file.renameTo(archive)) return;
                if (archive.length() > MAX_ARCHIVE_BYTES) {
                    try (RandomAccessFile raf = new RandomAccessFile(archive, "rw")) {
                        raf.seek(Math.max(0L, raf.length() - MAX_ARCHIVE_BYTES));
                        byte[] tail = new byte[(int) Math.min(raf.length(), MAX_ARCHIVE_BYTES)];
                        raf.readFully(tail);
                        raf.setLength(0);
                        raf.write(tail);
                    }
                }
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(bytes);
            }
        } catch (Exception ignored) {
            // Never recursively log a logger failure or re-run the original operation.
        }
    }

    public static void info(Context context, String message) { log(context, "INFO", message); }
    public static void debug(Context context, String message) { log(context, "DEBUG", message); }
    public static void warn(Context context, String message) { log(context, "WARN", message); }
    public static void error(Context context, String message) { log(context, "ERROR", message); }

    public static void clipboardCopy(Context context, int length) {
        info(context, "event=CLIPBOARD_LOG_COPY status=SUCCESS length=" + Math.max(0, length));
    }

    /** Compatibility API: never store clipboard text, only its length. */
    public static void clipboardCopyData(Context context, String text) {
        int length = text == null ? 0 : text.length();
        info(context, "event=CLIPBOARD_DATA_COPY status=SUCCESS length=" + length);
    }

    public static void sendEvent(Context context, String event) {
        info(context, "event=SEND_STAGE details=" + redact(event));
    }

    /** Compatibility API: never store message contents, only its length. */
    public static void sendData(Context context, String text) {
        int length = text == null ? 0 : text.length();
        info(context, "event=SEND_PAYLOAD_PREPARED status=RECORDED length=" + length);
    }

    /** Legacy scan snapshot API retained for existing UI readers. */
    public static synchronized void save(Context context, String text) {
        if (context == null) return;
        try {
            File file = new File(context.getApplicationContext().getFilesDir(), LEGACY_FILE_NAME);
            byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 128 * 1024) {
                byte[] tail = new byte[128 * 1024];
                System.arraycopy(bytes, bytes.length - tail.length, tail, 0, tail.length);
                bytes = tail;
            }
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(bytes);
            }
        } catch (Exception ignored) {}
    }

    public static String read(Context context) {
        if (context == null) return "CONTEXT: NULL";
        File file = new File(context.getApplicationContext().getFilesDir(), LEGACY_FILE_NAME);
        if (!file.exists()) return "Файл last_scan.txt не найден";
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) >= 0 && out.size() < 128 * 1024) {
                out.write(buffer, 0, Math.min(n, 128 * 1024 - out.size()));
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "Ошибка чтения: " + e.getClass().getSimpleName();
        }
    }

    public static String readLog(Context context) {
        if (context == null) return "CONTEXT: NULL";
        File file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
        if (!file.exists()) return "Лог пока пуст";
        try { return readLastLines(file, 100); }
        catch (Exception e) { return "Ошибка чтения лога: " + e.getClass().getSimpleName(); }
    }

    public static String readLastLines(File file, int maxLines) throws Exception {
        if (file == null || !file.exists() || file.length() == 0) return "Лог пока пуст";
        int limit = Math.max(1, Math.min(maxLines, 1000));
        long length = file.length();
        int bytesToRead = (int) Math.min(length, 64 * 1024L);
        byte[] data = new byte[bytesToRead];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(length - bytesToRead);
            raf.readFully(data);
        }
        String text = new String(data, StandardCharsets.UTF_8);
        String[] lines = text.split("\\r?\\n");
        StringBuilder result = new StringBuilder();
        for (int i = Math.max(0, lines.length - limit); i < lines.length; i++) {
            result.append(lines[i]).append('\n');
        }
        return result.toString();
    }
}
