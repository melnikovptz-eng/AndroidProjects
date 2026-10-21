package ru.aeroclub.informer;

import android.content.Context;
import android.content.SharedPreferences;
import android.system.Os;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One-device persistence adapter for the existing repository API.
 *
 * Domain data is stored in one private JSON file. On first use, values are copied
 * from the legacy "flight_informer" SharedPreferences file. The legacy file is
 * deliberately retained until the migration has been verified on real devices.
 *
 * Writes use a temporary file plus atomic rename. If the existing JSON cannot be
 * parsed, writes are refused rather than silently replacing the user's data.
 */
public final class LocalFilePreferences implements SharedPreferences {
    private static final String FILE_NAME = "aeroclub-local-data.json";
    private static final String LEGACY_PREFS = "flight_informer";
    private static final int FORMAT_VERSION = 1;
    private static final Object LOCK = new Object();
    private static final Object FILE_LOCK = new Object();
    private static final AtomicLong WRITE_VERSION = new AtomicLong();
    private static final ExecutorService DISK_WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "aeroclub-json-store");
        thread.setDaemon(true);
        return thread;
    });
    private static LocalFilePreferences instance;

    private final File file;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Set<OnSharedPreferenceChangeListener> listeners = new HashSet<>();
    private volatile boolean readError;
    private volatile boolean writeError;

    private LocalFilePreferences(Context context) {
        file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
        synchronized (LOCK) {
            if (file.exists()) {
                readFile();
            } else {
                Map<String, ?> legacy = context.getApplicationContext()
                    .getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).getAll();
                values.putAll(copyMap(legacy));
                // A transient first-write failure may be retried by the next repository write.
                // There is no existing JSON file to protect from accidental replacement yet.
                writeError = !writeFile(values);
            }
        }
    }

    public static LocalFilePreferences get(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required");
        synchronized (LOCK) {
            if (instance == null) instance = new LocalFilePreferences(context);
            return instance;
        }
    }

    public static String getFilePath(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), FILE_NAME).getAbsolutePath();
    }

    private void readFile() {
        synchronized (LOCK) {
            try {
                byte[] bytes = new byte[(int) Math.min(file.length(), 8L * 1024L * 1024L)];
                try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
                    int offset = 0;
                    while (offset < bytes.length) {
                        int count = input.read(bytes, offset, bytes.length - offset);
                        if (count < 0) break;
                        offset += count;
                    }
                    if (offset != bytes.length) throw new IllegalStateException("Incomplete local data file");
                }
                JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                if (root.optInt("formatVersion", -1) != FORMAT_VERSION) {
                    throw new IllegalStateException("Unsupported local data file version");
                }
                JSONObject data = root.getJSONObject("data");
                values.clear();
                java.util.Iterator<String> keys = data.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object raw = data.get(key);
                    if (raw instanceof JSONArray) {
                        JSONArray array = (JSONArray) raw;
                        Set<String> set = new HashSet<>();
                        for (int i = 0; i < array.length(); i++) {
                            Object item = array.opt(i);
                            if (item instanceof String) set.add((String) item);
                        }
                        values.put(key, set);
                    } else if (raw == JSONObject.NULL) {
                        values.put(key, null);
                    } else {
                        values.put(key, raw);
                    }
                }
                readError = false;
                writeError = false;
            } catch (Exception error) {
                // Preserve the damaged file for recovery; never auto-reset user data.
                readError = true;
                values.clear();
            }
        }
    }

    private boolean writeFile(Map<String, Object> data) {
        return writeFile(data, -1L);
    }

    private boolean writeFile(Map<String, Object> data, long expectedVersion) {
        synchronized (FILE_LOCK) {
            if (expectedVersion >= 0 && expectedVersion != WRITE_VERSION.get()) return true;
            File parent = file.getParentFile();
            File temp = new File(parent, FILE_NAME + ".tmp");
            try {
                if (!parent.isDirectory() && !parent.mkdirs()) return false;
                JSONObject root = new JSONObject();
                JSONObject payload = new JSONObject();
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Set) {
                        JSONArray array = new JSONArray();
                        ArrayList<String> sorted = new ArrayList<>();
                        for (Object item : (Set<?>) value) if (item instanceof String) sorted.add((String) item);
                        Collections.sort(sorted);
                        for (String item : sorted) array.put(item);
                        payload.put(entry.getKey(), array);
                    } else {
                        payload.put(entry.getKey(), value == null ? JSONObject.NULL : value);
                    }
                }
                root.put("formatVersion", FORMAT_VERSION);
                root.put("data", payload);
                byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
                try (FileOutputStream output = new FileOutputStream(temp, false)) {
                    output.write(bytes);
                    output.flush();
                    output.getFD().sync();
                }
                Os.rename(temp.getAbsolutePath(), file.getAbsolutePath());
                return true;
            } catch (Exception error) {
                // Keep the old file intact and leave the temporary file for diagnosis.
                return false;
            }
        }
    }

    public boolean isStorageHealthy() {
        return !readError && !writeError;
    }

    public String getStorageStatus() {
        if (readError) return "Файл данных повреждён или имеет неподдерживаемую версию; запись заблокирована для защиты данных.";
        if (writeError) return "Не удалось сохранить файл данных; последняя запись может быть не сохранена.";
        return "Локальный файл данных доступен.";
    }

    @Override public Map<String, ?> getAll() {
        synchronized (LOCK) { return Collections.unmodifiableMap(copyMap(values)); }
    }
    @Override public String getString(String key, String defValue) {
        synchronized (LOCK) { Object v=values.get(key); return v instanceof String ? (String)v : defValue; }
    }
    @Override public Set<String> getStringSet(String key, Set<String> defValues) {
        synchronized (LOCK) {
            Object v=values.get(key);
            if (!(v instanceof Set)) return defValues;
            Set<String> result=new HashSet<>();
            for(Object item:(Set<?>)v) if(item instanceof String) result.add((String)item);
            return result;
        }
    }
    @Override public int getInt(String key, int defValue) {
        synchronized (LOCK) { Object v=values.get(key); return v instanceof Number ? ((Number)v).intValue() : defValue; }
    }
    @Override public long getLong(String key, long defValue) {
        synchronized (LOCK) { Object v=values.get(key); return v instanceof Number ? ((Number)v).longValue() : defValue; }
    }
    @Override public float getFloat(String key, float defValue) {
        synchronized (LOCK) { Object v=values.get(key); return v instanceof Number ? ((Number)v).floatValue() : defValue; }
    }
    @Override public boolean getBoolean(String key, boolean defValue) {
        synchronized (LOCK) { Object v=values.get(key); return v instanceof Boolean ? (Boolean)v : defValue; }
    }
    @Override public boolean contains(String key) {
        synchronized (LOCK) { return values.containsKey(key); }
    }
    @Override public Editor edit() { return new FileEditor(); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        if (listener != null) synchronized (LOCK) { listeners.add(listener); }
    }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        synchronized (LOCK) { listeners.remove(listener); }
    }

    private static Map<String, Object> copyMap(Map<String, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) return copy;
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Set) {
                Set<String> set = new HashSet<>();
                for (Object item : (Set<?>) value) if (item instanceof String) set.add((String) item);
                copy.put(entry.getKey(), set);
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    private final class FileEditor implements Editor {
        private final Map<String, Object> changes = new HashMap<>();
        private final Set<String> removals = new HashSet<>();
        private boolean clear;

        @Override public Editor putString(String key, String value) {
            if (value == null) return remove(key);
            changes.put(key, value); removals.remove(key); return this;
        }
        @Override public Editor putStringSet(String key, Set<String> values) {
            if (values == null) return remove(key);
            changes.put(key, new HashSet<>(values)); removals.remove(key); return this;
        }
        @Override public Editor putInt(String key, int value) { changes.put(key,value); removals.remove(key); return this; }
        @Override public Editor putLong(String key, long value) { changes.put(key,value); removals.remove(key); return this; }
        @Override public Editor putFloat(String key, float value) { changes.put(key,value); removals.remove(key); return this; }
        @Override public Editor putBoolean(String key, boolean value) { changes.put(key,value); removals.remove(key); return this; }
        @Override public Editor remove(String key) { changes.remove(key); removals.add(key); return this; }
        @Override public Editor clear() { clear=true; changes.clear(); removals.clear(); return this; }
        @Override public boolean commit() { return persistChanges(true); }
        @Override public void apply() { persistChanges(false); }

        private boolean persistChanges(boolean synchronous) {
            final Set<String> changed = new HashSet<>();
            final Map<String, Object> snapshot;
            final long writeVersion;
            synchronized (LOCK) {
                if (readError) return false;
                Map<String, Object> next = copyMap(values);
                if (clear) { changed.addAll(next.keySet()); next.clear(); }
                for (String key : removals) {
                    if (next.containsKey(key)) changed.add(key);
                    next.remove(key);
                }
                for (Map.Entry<String,Object> entry : changes.entrySet()) {
                    Object old = next.get(entry.getKey());
                    Object value = entry.getValue();
                    if (old == null || !old.equals(value)) changed.add(entry.getKey());
                    next.put(entry.getKey(), value instanceof Set ? new HashSet<>((Set<?>)value) : value);
                }
                snapshot = copyMap(next);
                writeVersion = WRITE_VERSION.incrementAndGet();
                if (synchronous) {
                    if (!writeFile(snapshot, writeVersion)) {
                        writeError = true;
                        return false;
                    }
                    values.clear();
                    values.putAll(next);
                    writeError = false;
                } else {
                    values.clear();
                    values.putAll(next);
                    DISK_WRITER.execute(() -> {
                        if (writeVersion != WRITE_VERSION.get()) return;
                        boolean ok = writeFile(snapshot, writeVersion);
                        synchronized (LOCK) {
                            if (writeVersion == WRITE_VERSION.get()) writeError = !ok;
                        }
                    });
                }
            }
            for (OnSharedPreferenceChangeListener listener : snapshotListeners()) {
                for (String key : changed) {
                    try { listener.onSharedPreferenceChanged(LocalFilePreferences.this, key); }
                    catch (Exception ignored) { }
                }
            }
            return true;
        }
    }

    private Set<OnSharedPreferenceChangeListener> snapshotListeners() {
        synchronized (LOCK) { return new HashSet<>(listeners); }
    }
}
