package ru.aeroclub.informer;

import android.content.Context;
import java.util.concurrent.atomic.AtomicBoolean;

/** Measures elapsed time with a monotonic clock and records exactly one completion event. */
public final class DebugOperation implements AutoCloseable {
    private final Context context;
    private final String module;
    private final String action;
    private final long startedNanos;
    private final AtomicBoolean finished = new AtomicBoolean(false);

    DebugOperation(Context context, String module, String action, long startedNanos) {
        this.context = context == null ? null : context.getApplicationContext();
        this.module = module;
        this.action = action;
        this.startedNanos = startedNanos;
    }

    public void finish(String outcome, String detail) {
        if (!finished.compareAndSet(false, true)) return;
        long elapsedMs = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        ModuleDebugLog.logDuration(context, module, action, outcome, detail, elapsedMs);
    }

    @Override public void close() {
        finish("success", "completed");
    }
}