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
 * Foreground-only file watcher. Both metadata checks and JSON reads run on one
 * background executor. At most one check/read cycle can be in flight.
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
    private boolean checkInProgress;
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
        ModuleDebugLog.log(appContext, "MSG-02", "file_watcher_start", "success",
                "interval_ms=" + intervalMs);
        lastModified = Long.MIN_VALUE;
        lastLength = Long.MIN_VALUE;
        checkInProgress = false;
        final long runId = ++generation;
        poll(runId);
    }

    /** Schedules exactly one metadata/read cycle; the next cycle starts after completion. */
    private void poll(long runId) {
        final Context context;
        synchronized (this) {
            if (!running || generation != runId || appContext == null || checkInProgress) return;
            checkInProgress = true;
            context = appContext;
        }

        try {
            pendingRead = io.submit(() -> checkFile(runId, context));
        } catch (RuntimeException error) {
            synchronized (this) {
                if (generation == runId) checkInProgress = false;
            }
            ModuleDebugLog.log(context, "MSG-02", "file_watcher_schedule", "error",
                    error.getClass().getSimpleName());
        }
    }

    private void checkFile(long runId, Context context) {
        File file = new File(new File(context.getFilesDir(),
                LocalFileMessageTransport.DIR), LocalFileMessageTransport.NAME);
        long modified = file.isFile() ? file.lastModified() : 0L;
        long length = file.isFile() ? file.length() : 0L;
        boolean changed;
        synchronized (this) {
            if (!running || generation != runId) return;
            changed = modified != lastModified || length != lastLength;
        }

        List<LocalTransportMessage> messages = Collections.emptyList();
        boolean failed = false;
        if (changed) {
            try {
                messages = transport.getMessages(context, conversationId);
            } catch (RuntimeException error) {
                failed = true;
                ModuleDebugLog.log(context, "MSG-02", "file_watcher_read", "error",
                        error.getClass().getSimpleName());
            }
        }

        final List<LocalTransportMessage> result = messages;
        final boolean readFailed = failed;
        final boolean metadataChanged = changed;
        main.post(() -> {
            Listener target;
            synchronized (LocalMessageFileWatcher.this) {
                if (generation != runId || !running) return;
                if (metadataChanged && !readFailed) {
                    lastModified = modified;
                    lastLength = length;
                }
                checkInProgress = false;
                target = listener;
                pendingRead = null;
            }

            if (target != null && metadataChanged) {
                if (readFailed) target.onReadError();
                else target.onMessagesChanged(result);
            }
            main.postDelayed(() -> poll(runId), intervalMs);
        });
    }

    public synchronized void stop() {
        if (running) ModuleDebugLog.log(appContext, "MSG-02", "file_watcher_stop",
                "success", "foreground_poll_stopped");
        stopInternal();
    }

    private void stopInternal() {
        generation++;
        running = false;
        main.removeCallbacksAndMessages(null);
        if (pendingRead != null) pendingRead.cancel(true);
        pendingRead = null;
        checkInProgress = false;
        listener = null;
        appContext = null;
    }

    @Override public void close() {
        stop();
        io.shutdownNow();
    }
}
