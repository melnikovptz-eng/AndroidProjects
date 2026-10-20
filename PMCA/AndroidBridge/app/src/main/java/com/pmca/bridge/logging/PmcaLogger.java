package com.pmca.bridge.logging;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded UI ring buffer plus optional persistent app-private operational log. */
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
        String line = timestamp() + "  " + safe;
        synchronized (PmcaLogger.class) {
            while (LINES.size() >= MAX_LINES) LINES.removeFirst();
            LINES.addLast(line);
        }
        File target = persistentFile;
        if (target != null) {
            byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
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

    private static synchronized void appendBounded(File file, byte[] bytes) {
        try {
            if (file.exists() && file.length() + bytes.length > MAX_LOG_BYTES) {
                File archive = new File(file.getParentFile(), "pmca.log.1");
                if (archive.exists() && !archive.delete()) return;
                if (!file.renameTo(archive)) return;
                if (archive.length() > MAX_ARCHIVE_BYTES) {
                    try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(archive, "r")) {
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
            // Logging must not crash or re-run the operation; never recurse into this logger.
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
