package ru.aeroclub.informer;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Separate, local-only diagnostic file shared by all Aeroclub modules.
 * Keep records short and never pass names, message bodies, tokens or credentials.
 */
public final class ModuleDebugLog {
    private static final String FILE_NAME = "module-debug.log";
    private static final String BACKUP_NAME = "module-debug.log.1";
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final Object LOCK = new Object();

    private ModuleDebugLog() { }

    public static boolean log(Context context, String module, String action,
                              String outcome, String detail) {
        if (context == null) return false;
        synchronized (LOCK) {
            File target = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
            File backup = new File(target.getParentFile(), BACKUP_NAME);
            try {
                JSONObject event = new JSONObject();
                event.put("time", System.currentTimeMillis());
                event.put("module", clean(module, 80));
                event.put("action", clean(action, 100));
                event.put("outcome", clean(outcome, 100));
                event.put("detail", clean(detail, 240));
                event.put("thread", Thread.currentThread().getName());
                byte[] line = (event.toString() + "\n").getBytes(StandardCharsets.UTF_8);

                File parent = target.getParentFile();
                if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return false;
                if (target.exists() && target.length() + line.length > MAX_BYTES) {
                    if (backup.exists() && !backup.delete()) return false;
                    if (!target.renameTo(backup)) return false;
                }
                try (FileOutputStream output = new FileOutputStream(target, true)) {
                    output.write(line);
                    output.flush();
                }
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    /** Returns newest lines first, suitable for the admin diagnostic viewer. */
    public static String getRecent(Context context, int limit) {
        if (context == null) return "Контекст недоступен.";
        synchronized (LOCK) {
            File file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
            if (!file.isFile()) return "Файл отладки пока не создан.";
            java.util.ArrayList<String> lines = new java.util.ArrayList<>();
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
                    String lineValue = lines.get(i);
                    try {
                        JSONObject event = new JSONObject(lineValue);
                        out.append(format.format(new Date(event.optLong("time", 0))))
                           .append(" | ").append(event.optString("module"))
                           .append(" | ").append(event.optString("action"))
                           .append(" | ").append(event.optString("outcome"));
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
