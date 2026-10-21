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
 * Foreground-only watcher for the local message file.
 *
 * Polling is scheduled on the main looper, but file metadata and JSON reads happen
 * on one background thread. A new read is not queued while the previous read runs.
 * Stop this watcher when the messages screen stops; it is not a background service.
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
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.running = true;
        this.lastModified = Long.MIN_VALUE;
        this.lastLength = Long.MIN_VALUE;
        poll();
    }

    private void poll() {
        synchronized (this) {
            if (!running || readInProgress || appContext == null) {
                if (running) main.postDelayed(this::poll, intervalMs);
                return;
            }
            File file = new File(new File(appContext.getFilesDir(), "local_message_transport"), "messages.json");
            long modified = file.isFile() ? file.lastModified() : 0L;
            long length = file.isFile() ? file.length() : 0L;
            if (modified == lastModified && length == lastLength) {
                main.postDelayed(this::poll, intervalMs);
                return;
            }
            lastModified = modified;
            lastLength = length;
            readInProgress = true;
            Context context = appContext;
            Listener callback = listener;
            pendingRead = io.submit(() -> {
                List<LocalTransportMessage> messages = Collections.emptyList();
                boolean failed = false;
                try {
                    messages = transport.getMessages(context, conversationId);
                } catch (RuntimeException e) {
                    failed = true;
                }
                final List<LocalTransportMessage> result = messages;
                final boolean error = failed;
                main.post(() -> {
                    Listener target;
                    synchronized (LocalMessageFileWatcher.this) {
                        readInProgress = false;
                        target = running ? listener : null;
                    }
                    if (target != null) {
                        if (error) target.onReadError();
                        else target.onMessagesChanged(result);
                    }
                });
            });
        }
        main.postDelayed(this::poll, intervalMs);
    }

    public synchronized void stop() {
        stopInternal();
    }

    private void stopInternal() {
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
