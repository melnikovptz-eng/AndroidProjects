package ru.aeroclub.informer;

import android.content.Context;
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

/** Shared local-only diagnostic logger. Logs remain private until the user explicitly shares them. */
public final class ModuleDebugLog {
    private static final String FILE_NAME = "module-debug.log";
    private static final String BACKUP_NAME = "module-debug.log.1";
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final Object LOCK = new Object();

    private ModuleDebugLog() { }

    public static boolean log(Context context, String module, String action, String outcome, String detail) {
        return write(context, module, action, outcome, detail, null);
    }

    /** Monotonic timer for a single operation; call finish exactly once. */
    public static DebugOperation startOperation(Context context, String module, String action, String detail) {
        log(context, module, action, "started", detail);
        return new DebugOperation(context, module, action, System.nanoTime());
    }

    static boolean logDuration(Context context, String module, String action, String outcome,
                               String detail, long durationMs) {
        return write(context, module, action, outcome, detail, Math.max(0L, durationMs));
    }

    /** Internal diagnostics use the same local journal and never trigger network activity. */
    static boolean logInternal(Context context, String module, String action,
                               String outcome, String detail, long durationMs) {
        return write(context, module, action, outcome, detail, Math.max(0L, durationMs));
    }

    private static boolean write(Context context, String module, String action, String outcome,
                                 String detail, Long durationMs) {
        if (context == null) return false;
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            File dir = app.getFilesDir();
            File target = new File(dir, FILE_NAME);
            File backup = new File(dir, BACKUP_NAME);
            try {
                JSONObject event = new JSONObject();
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
                    output.getFD().sync();
                }
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    /** Complete local journal (rotated backup first, then active file) for explicit user sharing. */
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
