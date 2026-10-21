package ru.aeroclub.informer;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Shared diagnostic logger. Keeps an internal recovery copy and a bounded, AI-readable
 * project log in the public Android Download folder. No network access is performed.
 */
public final class ModuleDebugLog {
    private static final String FILE_NAME = "Aeroclub_Informer_debug.log";
    private static final String BACKUP_NAME = "module-debug.log.1";
    // Conservative limit so the complete log can be pasted into a ChatGPT message.
    public static final int MAX_LOG_CHARACTERS = 80000;
    private static final long INTERNAL_MAX_BYTES = 1024L * 1024L;
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
            File target = new File(dir, "module-debug.log");
            File backup = new File(dir, BACKUP_NAME);
            try {
                JSONObject event = new JSONObject();
                event.put("time", System.currentTimeMillis());
                event.put("project", "Aeroclub Informer");
                event.put("module", clean(module, 80));
                event.put("action", clean(action, 100));
                event.put("outcome", clean(outcome, 100));
                event.put("detail", clean(detail, 240));
                event.put("thread", clean(Thread.currentThread().getName(), 80));
                if (durationMs != null) event.put("duration_ms", durationMs);
                String text = event.toString() + "\n";
                byte[] line = text.getBytes(StandardCharsets.UTF_8);
                if (!dir.isDirectory() && !dir.mkdirs()) return false;
                if (target.exists() && target.length() + line.length > INTERNAL_MAX_BYTES) {
                    if (backup.exists() && !backup.delete()) return false;
                    if (!target.renameTo(backup)) return false;
                }
                try (FileOutputStream output = new FileOutputStream(target, true)) {
                    output.write(line);
                    output.flush();
                    output.getFD().sync();
                }
                // Keep the user-visible Download copy current and strictly bounded.
                boolean downloadWritten = appendDownloadLog(app, text);
                if (!downloadWritten) {
                    // Do not silently hide a Downloads failure: retain a diagnostic in the
                    // private recovery log without recursively trying to write Downloads.
                    appendInternalDiagnostic(app, clean(module, 80), clean(action, 100),
                            "downloads_write_failed", "public_path=Download/" + FILE_NAME);
                }
                return downloadWritten;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private static void appendInternalDiagnostic(Context context, String module,
                                                  String action, String outcome, String detail) {
        try {
            File dir = context.getFilesDir();
            File target = new File(dir, "module-debug.log");
            File backup = new File(dir, BACKUP_NAME);
            JSONObject event = new JSONObject();
            event.put("time", System.currentTimeMillis());
            event.put("project", "Aeroclub Informer");
            event.put("module", module);
            event.put("action", action);
            event.put("outcome", outcome);
            event.put("detail", detail);
            event.put("thread", clean(Thread.currentThread().getName(), 80));
            byte[] line = (event.toString() + "\n").getBytes(StandardCharsets.UTF_8);
            if (target.exists() && target.length() + line.length > INTERNAL_MAX_BYTES) {
                if (backup.exists() && !backup.delete()) return;
                if (!target.renameTo(backup)) return;
            }
            try (FileOutputStream output = new FileOutputStream(target, true)) {
                output.write(line);
                output.flush();
                output.getFD().sync();
            }
        } catch (Exception ignored) {
            // The original operation already reports failure to its caller.
        }
    }

    private static boolean appendDownloadLog(Context context, String line) {
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri uri = findOrCreateDownload(resolver);
            if (uri == null) return false;
            long currentChars = queryDownloadSize(resolver, uri);
            boolean replace = currentChars + line.length() > MAX_LOG_CHARACTERS;
            if (replace && !"file".equals(uri.getScheme())) {
                resolver.delete(uri, null, null);
                uri = createDownload(resolver);
                if (uri == null) return false;
            }
            if ("file".equals(uri.getScheme())) {
                File file = new File(uri.getPath());
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
                try (FileOutputStream output = new FileOutputStream(file, !replace)) {
                    output.write(line.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    output.getFD().sync();
                }
            } else {
                try (OutputStream output = resolver.openOutputStream(uri, "wa")) {
                    if (output == null) return false;
                    output.write(line.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
            }
            return queryDownloadSize(resolver, uri) <= MAX_LOG_CHARACTERS;
        } catch (Exception ignored) {
            // Internal copy remains available; false explicitly reports that Downloads failed.
            return false;
        }
    }

    private static Uri findOrCreateDownload(ContentResolver resolver) {
        if (Build.VERSION.SDK_INT >= 29) {
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String[] projection = { MediaStore.Downloads._ID };
            String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                    + MediaStore.Downloads.RELATIVE_PATH + "=?";
            try (Cursor cursor = resolver.query(collection, projection, selection,
                    new String[]{FILE_NAME, Environment.DIRECTORY_DOWNLOADS + "/"}, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    return Uri.withAppendedPath(collection, cursor.getString(0));
                }
            }
            return createDownload(resolver);
        }
        // Legacy Android versions use the public Downloads directory.
        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!directory.isDirectory() && !directory.mkdirs()) return null;
        return Uri.fromFile(new File(directory, FILE_NAME));
    }

    private static Uri createDownload(ContentResolver resolver) {
        if (Build.VERSION.SDK_INT < 29) {
            File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!directory.isDirectory() && !directory.mkdirs()) return null;
            return Uri.fromFile(new File(directory, FILE_NAME));
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/");
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
    }

    private static long queryDownloadSize(ContentResolver resolver, Uri uri) {
        if ("file".equals(uri.getScheme())) {
            File file = new File(uri.getPath());
            return file.isFile() ? readFileLimited(file, MAX_LOG_CHARACTERS + 1).length() : 0L;
        }
        StringBuilder text = new StringBuilder();
        try (InputStream input = resolver.openInputStream(uri);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (input == null) return 0L;
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1 && bytes.size() <= MAX_LOG_CHARACTERS * 4) {
                bytes.write(buffer, 0, read);
            }
            text.append(bytes.toString("UTF-8"));
        } catch (Exception ignored) { }
        return text.length();
    }

    private static String readFileLimited(File file, int maxBytes) {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1 && bytes.size() < maxBytes) {
                int keep = Math.min(read, maxBytes - bytes.size());
                bytes.write(buffer, 0, keep);
            }
            return bytes.toString("UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    /** Bounded snapshot of the public Download log; internal journal is a fallback. */
    static String getFullLogSnapshot(Context context) {
        if (context == null) return "";
        synchronized (LOCK) {
            String publicLog = readDownloadLog(context.getApplicationContext());
            if (!publicLog.isEmpty()) return tail(publicLog, MAX_LOG_CHARACTERS);
            File dir = context.getApplicationContext().getFilesDir();
            StringBuilder out = new StringBuilder();
            appendFile(out, new File(dir, BACKUP_NAME));
            appendFile(out, new File(dir, "module-debug.log"));
            return tail(out.toString(), MAX_LOG_CHARACTERS);
        }
    }

    private static String readDownloadLog(Context context) {
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri uri = findExistingDownload(resolver);
            if (uri == null) return "";
            if ("file".equals(uri.getScheme())) return readFileLimited(new File(uri.getPath()), MAX_LOG_CHARACTERS * 4);
            try (InputStream input = resolver.openInputStream(uri);
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                if (input == null) return "";
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1 && bytes.size() < MAX_LOG_CHARACTERS * 4) {
                    int keep = Math.min(read, MAX_LOG_CHARACTERS * 4 - bytes.size());
                    bytes.write(buffer, 0, keep);
                }
                return bytes.toString("UTF-8");
            }
        } catch (Exception ignored) { return ""; }
    }

    private static Uri findExistingDownload(ContentResolver resolver) {
        if (Build.VERSION.SDK_INT >= 29) {
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                    + MediaStore.Downloads.RELATIVE_PATH + "=?";
            try (Cursor cursor = resolver.query(collection, new String[]{MediaStore.Downloads._ID},
                    selection, new String[]{FILE_NAME, Environment.DIRECTORY_DOWNLOADS + "/"}, null)) {
                if (cursor != null && cursor.moveToFirst()) return Uri.withAppendedPath(collection, cursor.getString(0));
            }
            return null;
        }
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FILE_NAME);
        return file.isFile() ? Uri.fromFile(file) : null;
    }

    private static String tail(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) return text == null ? "" : text;
        int start = text.length() - maxChars;
        int newline = text.indexOf('\n', start);
        if (newline >= 0 && newline + 1 < text.length()) start = newline + 1;
        return text.substring(start);
    }

    private static void appendFile(StringBuilder out, File file) {
        if (!file.isFile()) return;
        out.append(readFileLimited(file, (int) (INTERNAL_MAX_BYTES + 1)));
    }

    /** Returns newest active-file lines first, for the admin viewer. */
    public static String getRecent(Context context, int limit) {
        if (context == null) return "Контекст недоступен.";
        synchronized (LOCK) {
            File file = new File(context.getApplicationContext().getFilesDir(), "module-debug.log");
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
        if (context == null) return "Download/" + FILE_NAME;
        return "Download/" + FILE_NAME;
    }

    public static String getDownloadPath() {
        return "Download/" + FILE_NAME;
    }

    private static String clean(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.replace('\r', ' ').replace('\n', ' ').trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
