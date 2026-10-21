package ru.aeroclub.informer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Foreground-only file watcher. File reads run off the UI thread and never overlap.
 * Start while the messages screen is visible and stop it when that screen stops.
 */
public final class LocalMessageFileWatcher implements AutoCloseable {
    public interface Listener {
        void onMessagesChanged(List<LocalTransportMessage> messages);
        default void onReadError() { }
    }

    private static final long DEFAULT_INTERVAL_MS = 15_000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final long intervalMs;
    private final MessageTransport transport;
    private final String conversationId;

    private Context appContext;
    private Listener listener;
    private boolean running;
    private boolean readInProgress;
    private long generation;
    private long lastModified = Long.MIN_VALUE;
    private long lastLength = Long.MIN_VALUE;
    private Future<?> pendingRead;

    public LocalMessageFileWatcher(MessageTransport transport, String conversationId) {
        this(transport, conversationId, DEFAULT_INTERVAL_MS);
    }

    public LocalMessageFileWatcher(MessageTransport transport, String conversationId, long intervalMs) {
        if (transport == null) throw new IllegalArgumentException("transport == null");
        this.transport = transport;
        this.conversationId = conversationId == null ? "" : conversationId.trim();
        this.intervalMs = Math.max(5_000L, intervalMs);
    }

    public synchronized void start(Context context, Listener listener) {
        if (context == null || listener == null) return;
        stopInternal();
        appContext = context.getApplicationContext();
        this.listener = listener;
        running = true;
        lastModified = Long.MIN_VALUE;
        lastLength = Long.MIN_VALUE;
        readInProgress = false;
        final long runId = ++generation;
        poll(runId);
    }

    private void poll(long runId) {
        synchronized (this) {
            if (!running || generation != runId || appContext == null) return;
            if (readInProgress) {
                main.postDelayed(() -> poll(runId), intervalMs);
                return;
            }

            File file = new File(new File(appContext.getFilesDir(),
                    LocalFileMessageTransport.DIR), LocalFileMessageTransport.NAME);
            long modified = file.isFile() ? file.lastModified() : 0L;
            long length = file.isFile() ? file.length() : 0L;
            if (modified == lastModified && length == lastLength) {
                main.postDelayed(() -> poll(runId), intervalMs);
                return;
            }

            lastModified = modified;
            lastLength = length;
            readInProgress = true;
            Context context = appContext;
            pendingRead = io.submit(() -> {
                List<LocalTransportMessage> messages = Collections.emptyList();
                boolean failed = false;
                try {
                    messages = transport.getMessages(context, conversationId);
                } catch (RuntimeException error) {
                    failed = true;
                }
                final List<LocalTransportMessage> result = messages;
                final boolean readFailed = failed;
                main.post(() -> {
                    Listener target;
                    synchronized (LocalMessageFileWatcher.this) {
                        if (generation != runId) return;
                        readInProgress = false;
                        target = running ? listener : null;
                    }
                    if (target != null) {
                        if (readFailed) target.onReadError();
                        else target.onMessagesChanged(result);
                    }
                });
            });
            main.postDelayed(() -> poll(runId), intervalMs);
        }
    }

    public synchronized void stop() {
        stopInternal();
    }

    private void stopInternal() {
        generation++;
        running = false;
        main.removeCallbacksAndMessages(null);
        if (pendingRead != null) pendingRead.cancel(true);
        pendingRead = null;
        readInProgress = false;
        listener = null;
        appContext = null;
    }

    @Override public void close() {
        stop();
        io.shutdownNow();
    }
}
