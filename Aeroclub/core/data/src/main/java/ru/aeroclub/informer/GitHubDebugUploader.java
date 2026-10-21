package ru.aeroclub.informer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Direct GitHub Contents API uploader. The token is injected at build time via
 * AEROCLUB_DEBUG_GITHUB_TOKEN; never place a real token in source control.
 */
public final class GitHubDebugUploader {
    private static final String REPOSITORY = "melnikovptz-eng/Aeroclub-Debug";
    private static final String BRANCH = "main";
    private static final int BATCH_THRESHOLD = 50;
    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final String PREFS = "module_debug_upload";
    private static final String RETRY_NEEDED = "retry_needed";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "aeroclub-debug-upload");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean UPLOADING = new AtomicBoolean(false);\n    private static final AtomicBoolean UPLOAD_REQUESTED = new AtomicBoolean(false);
    private static final AtomicBoolean RECEIVER_REGISTERED = new AtomicBoolean(false);

    private GitHubDebugUploader() { }

    static void onNewRecord(Context context, boolean urgent) {
        if (context == null) return;
        if (urgent || ModuleDebugLog.getPendingCount(context) >= BATCH_THRESHOLD) {
            uploadAsync(context.getApplicationContext(), null, false);
        }
    }

    /** Call once from the launcher Activity. Retries failed batches when connectivity returns. */
    public static void registerNetworkRetry(Context context) {
        if (context == null || !RECEIVER_REGISTERED.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        try {
            app.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent intent) {
                    try {
                        ConnectivityManager manager = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
                        NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
                        boolean connected = info != null && info.isConnected();
                        boolean retry = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .getBoolean(RETRY_NEEDED, false);
                        boolean thresholdReached = ModuleDebugLog.getPendingCount(app) >= BATCH_THRESHOLD;
                        if (connected && ModuleDebugLog.getPendingCount(app) > 0 && (retry || thresholdReached)) {
                            uploadAsync(app, null, false);
                        }
                    } catch (Exception ignoredError) {
                        // Connectivity monitoring must never affect app behavior.
                    }
                }
            }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        } catch (Exception ignored) {
            RECEIVER_REGISTERED.set(false);
        }
    }

    /** Manual action uploads the complete current log plus rotated backup, not just pending lines. */
    public static void uploadNow(Context context, Callback callback) {
        if (context == null) {
            if (callback != null) callback.onComplete(false, "Контекст недоступен", 0);
            return;
        }
        uploadAsync(context.getApplicationContext(), callback, true);
    }

    private static void uploadAsync(Context context, Callback callback, boolean fullJournal) {
        if (!UPLOADING.compareAndSet(false, true)) {
            if (callback != null) callback.onComplete(false, "Отправка уже выполняется", 0);
            return;
        }
        EXECUTOR.execute(() -> {
            boolean success = false;
            String message;
            int lines = 0;
            try {
                String token = ru.aeroclub.data.BuildConfig.DEBUG_GITHUB_TOKEN;
                if (token == null || token.trim().isEmpty()) {
                    throw new IllegalStateException("Не задан AEROCLUB_DEBUG_GITHUB_TOKEN при сборке APK");
                }
                String pendingSnapshot = ModuleDebugLog.getPendingSnapshot(context);
                String uploadContent = fullJournal ? ModuleDebugLog.getFullLogSnapshot(context) : pendingSnapshot;
                lines = countLines(uploadContent);
                if (uploadContent.isEmpty() || lines == 0) {
                    success = true;
                    message = "Журнал пуст: отправлять нечего";
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(RETRY_NEEDED, false).apply();
                } else {
                    String path = "uploads/" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".jsonl";
                    JSONObject payload = new JSONObject();
                    payload.put("message", "Aeroclub diagnostic log: " + lines + " lines");
                    payload.put("branch", BRANCH);
                    payload.put("content", Base64.encodeToString(uploadContent.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                    HttpURLConnection connection = null;
                    try {
                        URL url = new URL("https://api.github.com/repos/" + REPOSITORY + "/contents/" + path);
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("PUT");
                        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                        connection.setReadTimeout(READ_TIMEOUT_MS);
                        connection.setDoOutput(true);
                        connection.setRequestProperty("Accept", "application/vnd.github+json");
                        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                        connection.setRequestProperty("Authorization", "Bearer " + token.trim());
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                        connection.setFixedLengthStreamingMode(body.length);
                        try (OutputStream output = connection.getOutputStream()) {
                            output.write(body);
                        }
                        int code = connection.getResponseCode();
                        InputStream stream = code >= 200 && code < 300
                                ? connection.getInputStream() : connection.getErrorStream();
                        String response = readSmall(stream);
                        if (code < 200 || code >= 300) {
                            throw new IllegalStateException("GitHub HTTP " + code + ": " + safeResponse(response));
                        }
                        if (!pendingSnapshot.isEmpty()) ModuleDebugLog.markPendingUploaded(context, pendingSnapshot);
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(RETRY_NEEDED, false).apply();
                        success = true;
                        message = "Отправлено строк: " + lines;
                    } finally {
                        if (connection != null) connection.disconnect();
                    }
                }
            } catch (Exception error) {
                message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(RETRY_NEEDED, true).apply();
            } finally {
                UPLOADING.set(false);
            }
            if (callback != null) callback.onComplete(success, message, lines);
        });
    }

    private static int countLines(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == '\n') count++;
        return count;
    }

    private static String readSmall(InputStream stream) {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int total = 0, read;
            while ((read = input.read(buffer)) != -1 && total < 8192) {
                int take = Math.min(read, 8192 - total);
                output.write(buffer, 0, take);
                total += take;
            }
            return output.toString("UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String safeResponse(String response) {
        if (response == null) return "";
        String cleaned = response.replace('\n', ' ').replace('\r', ' ');
        return cleaned.length() > 180 ? cleaned.substring(0, 180) : cleaned;
    }

    public interface Callback {
        void onComplete(boolean success, String message, int lines);
    }
}