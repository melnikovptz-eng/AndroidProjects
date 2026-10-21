package ru.aeroclub.informer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Shared, non-recursive diagnostic logger and durable upload queue. */
public final class ModuleDebugLog {
    private static final String FILE_NAME = "module-debug.log";
    private static final String BACKUP_NAME = "module-debug.log.1";
    private static final String PENDING_NAME = "module-debug-pending.jsonl";
    private static final String PREFS = "module_debug_upload";
    private static final String PENDING_COUNT = "pending_count";
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final Object LOCK = new Object();

    private ModuleDebugLog() { }

    public static boolean log(Context context, String module, String action,
                              String outcome, String detail) {
        return write(context, module, action, outcome, detail, null, true);
    }

    /** Monotonic timer for a single operation; call finish exactly once. */
    public static DebugOperation startOperation(Context context, String module,
                                                String action, String detail) {
        log(context, module, action, "started", detail);
        return new DebugOperation(context, module, action, System.nanoTime());
    }

    static boolean logDuration(Context context, String module, String action,
                               String outcome, String detail, long durationMs) {
        return write(context, module, action, outcome, detail, Math.max(0L, durationMs), true);
    }

    private static boolean write(Context context, String module, String action,
                                 String outcome, String detail, Long durationMs,
                                 boolean queueForUpload) {
        if (context == null) return false;
        Context app = context.getApplicationContext();
        boolean ok = false;
        boolean urgent = isError(outcome);
        synchronized (LOCK) {
            File dir = app.getFilesDir();
            File target = new File(dir, FILE_NAME);
            File backup = new File(dir, BACKUP_NAME);
            JSONObject event = new JSONObject();
            try {
                event.put("time", System.currentTimeMillis());
                event.put("module", clean(module, 80));
                event.put("action", clean(action, 100));
                event.put("outcome", clean(outcome, 100));
                event.put("detail", clean(detail, 240));
                event.put("thread", clean(Thread.currentThread().getName(), 80));
                if (durationMs != null) event.put("duration_ms", durationMs);
                byte[] line = (event.toString() + "\n").getBytes(StandardCharsets.UTF_8);
                if (!dir.isDirectory() && !dir.mkdirs()) return false;
                if (target.exists() && target.length() + line.length > MAX_BYTES) {
                    if (backup.exists() && !backup.delete()) return false;
                    if (!target.renameTo(backup)) return false;
                }
                try (FileOutputStream output = new FileOutputStream(target, true)) {
                    output.write(line);
                    output.flush();
                }
                ok = true;
                if (queueForUpload) {
                    File pending = new File(dir, PENDING_NAME);
                    try (FileOutputStream output = new FileOutputStream(pending, true)) {
                        output.write(line);
                        output.flush();
                        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                        prefs.edit().putInt(PENDING_COUNT, prefs.getInt(PENDING_COUNT, 0) + 1).apply();
                    } catch (Exception queueError) {
                        // Do not call this logger again: that would create recursive logging.
                        ok = false;
                    }
                }
            } catch (Exception ignored) {
                return false;
            }
        }
        if (ok && queueForUpload) GitHubDebugUploader.onNewRecord(app, urgent);
        return ok;
    }

    static boolean isError(String outcome) {
        if (outcome == null) return false;
        String value = outcome.toLowerCase(Locale.ROOT);
        return value.contains("error") || value.contains("fail") || value.contains("exception")
                || value.contains("timeout");
    }

    static int getPendingCount(Context context) {
        if (context == null) return 0;
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PENDING_COUNT, 0);
    }

    /** Snapshot taken under the same lock used by appends. */
    static String getPendingSnapshot(Context context) {
        if (context == null) return "";
        synchronized (LOCK) {
            File file = new File(context.getApplicationContext().getFilesDir(), PENDING_NAME);
            if (!file.isFile()) return "";
            try (FileInputStream input = new FileInputStream(file);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                return output.toString("UTF-8");
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    /** Remove only the successfully uploaded prefix; keep records appended during upload. */
    static void markPendingUploaded(Context context, String uploadedSnapshot) {
        if (context == null || uploadedSnapshot == null || uploadedSnapshot.isEmpty()) return;
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            File file = new File(app.getFilesDir(), PENDING_NAME);
            try {
                String current = getPendingSnapshotUnlocked(file);
                if (!current.startsWith(uploadedSnapshot)) return;
                String remainder = current.substring(uploadedSnapshot.length());
                try (FileOutputStream output = new FileOutputStream(file, false)) {
                    output.write(remainder.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                int uploadedLines = countLines(uploadedSnapshot);
                SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                prefs.edit().putInt(PENDING_COUNT, Math.max(0, prefs.getInt(PENDING_COUNT, 0) - uploadedLines)).apply();
            } catch (Exception ignored) {
                // Leave the queue untouched on failure; the next upload can retry.
            }
        }
    }

    private static String getPendingSnapshotUnlocked(File file) throws Exception {
        if (!file.isFile()) return "";
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString("UTF-8");
        }
    }

    private static int countLines(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == '\n') count++;
        return count;
    }

    /** Complete local journal (backup first, then active file) for explicit manual upload. */
    static String getFullLogSnapshot(Context context) {
        if (context == null) return "";
        synchronized (LOCK) {
            File dir = context.getApplicationContext().getFilesDir();
            StringBuilder out = new StringBuilder();
            appendFile(out, new File(dir, BACKUP_NAME));
            appendFile(out, new File(dir, FILE_NAME));
            return out.toString();
        }
    }

    private static void appendFile(StringBuilder out, File file) {
        if (!file.isFile()) return;
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
            out.append(bytes.toString("UTF-8"));
        } catch (Exception ignored) {
            // Keep the rest of the journal available if one rotated file cannot be read.
        }
    }

    /** Returns newest active-file lines first, for the admin viewer. */
    public static String getRecent(Context context, int limit) {
        if (context == null) return "Контекст недоступен.";
        synchronized (LOCK) {
            File file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
            if (!file.isFile()) return "Файл отладки пока не создан.";
            ArrayList<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                int max = Math.max(1, Math.min(limit, 1000));
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    if (lines.size() > max) lines.remove(0);
                }
                StringBuilder out = new StringBuilder();
                SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT);
                format.setTimeZone(TimeZone.getDefault());
                for (int i = lines.size() - 1; i >= 0; i--) {
                    try {
                        JSONObject event = new JSONObject(lines.get(i));
                        out.append(format.format(new Date(event.optLong("time", 0))))
                           .append(" | ").append(event.optString("module"))
                           .append(" | ").append(event.optString("action"))
                           .append(" | ").append(event.optString("outcome"));
                        if (event.has("duration_ms")) out.append(" | ").append(event.optLong("duration_ms")).append(" ms");
                        String detail = event.optString("detail", "");
                        if (!detail.isEmpty()) out.append(" | ").append(detail);
                        out.append("\n");
                    } catch (Exception malformed) {
                        out.append("[повреждённая строка журнала]\n");
                    }
                }
                return out.length() == 0 ? "Журнал отладки пуст." : out.toString();
            } catch (Exception error) {
                return "Не удалось прочитать файл отладки (" + error.getClass().getSimpleName() + ").";
            }
        }
    }

    public static String getFilePath(Context context) {
        if (context == null) return FILE_NAME;
        return new File(context.getApplicationContext().getFilesDir(), FILE_NAME).getAbsolutePath();
    }

    private static String clean(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.replace('\r', ' ').replace('\n', ' ').trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}