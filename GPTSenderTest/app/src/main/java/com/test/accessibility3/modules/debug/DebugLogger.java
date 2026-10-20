package com.test.accessibility3.modules.debug;

import android.content.Context;

import com.test.accessibility3.GptSenderLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Compatibility adapter for older module call sites.
 * Operational events go to the single canonical gpt_sender.log; last_scan.txt
 * remains only as a bounded legacy diagnostic snapshot.
 */
public final class DebugLogger {
    private static final String LEGACY_FILE_NAME = "last_scan.txt";

    private DebugLogger() {}

    public static void log(Context context, String level, String message) {
        if ("ERROR".equalsIgnoreCase(level)) {
            GptSenderLog.error(context, "LEGACY_DEBUG_ADAPTER", "DEBUG_EVENT", message);
        } else if ("WARN".equalsIgnoreCase(level)) {
            GptSenderLog.warn(context, "LEGACY_DEBUG_ADAPTER", "DEBUG_EVENT", message);
        } else if ("DEBUG".equalsIgnoreCase(level)) {
            GptSenderLog.info(context, "LEGACY_DEBUG_ADAPTER", "DEBUG_EVENT", message);
        } else {
            GptSenderLog.info(context, "LEGACY_DEBUG_ADAPTER", "DEBUG_EVENT", message);
        }
    }

    public static void info(Context context, String message) { log(context, "INFO", message); }
    public static void debug(Context context, String message) { log(context, "DEBUG", message); }
    public static void warn(Context context, String message) { log(context, "WARN", message); }
    public static void error(Context context, String message) { log(context, "ERROR", message); }

    public static void clipboardCopy(Context context, int length) {
        info(context, "event=CLIPBOARD_LOG_COPY status=SUCCESS length=" + Math.max(0, length));
    }

    /** Legacy API retained, but never records clipboard contents. */
    public static void clipboardCopyData(Context context, String text) {
        info(context, "event=CLIPBOARD_DATA_COPY status=RECORDED length="
                + (text == null ? 0 : text.length()));
    }

    public static void sendEvent(Context context, String event) {
        info(context, "event=SEND_STAGE details=" + event);
    }

    /** Legacy API retained, but never records message contents. */
    public static void sendData(Context context, String text) {
        info(context, "event=SEND_PAYLOAD_PREPARED status=RECORDED length="
                + (text == null ? 0 : text.length()));
    }

    public static synchronized void save(Context context, String text) {
        if (context == null) return;
        try {
            File file = new File(context.getApplicationContext().getFilesDir(), LEGACY_FILE_NAME);
            byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            int max = 128 * 1024;
            if (bytes.length > max) {
                byte[] tail = new byte[max];
                System.arraycopy(bytes, bytes.length - max, tail, 0, max);
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
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 128 * 1024L)];
            int count = in.read(data);
            return count <= 0 ? "" : new String(data, 0, count, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Ошибка чтения: " + e.getClass().getSimpleName();
        }
    }

    public static String readLog(Context context) {
        return GptSenderLog.read(context);
    }

    public static String readLastLines(File file, int maxLines) throws Exception {
        if (file == null || !file.exists() || file.length() == 0) return "Лог пока пуст";
        int max = Math.max(1, Math.min(maxLines, 1000));
        long length = file.length();
        int bytesToRead = (int) Math.min(length, 64 * 1024L);
        byte[] data = new byte[bytesToRead];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(length - bytesToRead);
            raf.readFully(data);
        }
        String[] lines = new String(data, StandardCharsets.UTF_8).split("\\r?\\n");
        StringBuilder result = new StringBuilder();
        for (int i = Math.max(0, lines.length - max); i < lines.length; i++) {
            result.append(lines[i]).append('\n');
        }
        return result.toString();
    }
}
