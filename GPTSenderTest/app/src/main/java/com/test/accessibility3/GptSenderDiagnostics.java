package com.test.accessibility3;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class GptSenderDiagnostics {
    private static final String TAG = "GPTSenderDiag";
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile Context appContext;
    private static volatile boolean monitoring;
    private static long lastHeartbeat;
    private static long heartbeatNumber;

    private static final Runnable HEARTBEAT = new Runnable() {
        @Override
        public void run() {
            if (!monitoring) return;

            long now = SystemClock.elapsedRealtime();
            long gap = lastHeartbeat == 0 ? 0 : now - lastHeartbeat;
            lastHeartbeat = now;
            heartbeatNumber++;

            if (gap > 2500) {
                write("MAIN_THREAD", "MAIN_THREAD_STALL",
                        "delay_ms=" + gap
                        + "; tick=" + heartbeatNumber
                        + "; thread=" + Thread.currentThread().getName());
            } else if (heartbeatNumber % 10 == 0) {
                write("MAIN_THREAD", "HEARTBEAT",
                        "gap_ms=" + gap + "; tick=" + heartbeatNumber);
            }

            MAIN.postDelayed(this, 500);
        }
    };

    private GptSenderDiagnostics() {}

    public static void start(Context context) {
        if (context == null) return;

        appContext = context.getApplicationContext();
        log(context, "DIAGNOSTICS", "START");

        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!monitoring) {
                monitoring = true;
                lastHeartbeat = SystemClock.elapsedRealtime();
                MAIN.removeCallbacks(HEARTBEAT);
                MAIN.postDelayed(HEARTBEAT, 500);
            }
        } else {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    if (!monitoring) {
                        monitoring = true;
                        lastHeartbeat = SystemClock.elapsedRealtime();
                        MAIN.postDelayed(HEARTBEAT, 500);
                    }
                }
            });
        }

        log(context, "DIAGNOSTICS", "LOG_PATH=" + getLogPath(context));
    }

    public static void stop(Context context) {
        log(context, "ACTIVITY", "DIAGNOSTIC_MONITOR_STOP");
        monitoring = false;
        MAIN.removeCallbacks(HEARTBEAT);
    }

    public static void log(Context context, String component,
                           String event) {
        write(component, event, "");
    }

    public static void log(Context context, String component,
                           String event, String details) {
        write(component, event, details);
    }

    public static void exception(Context context, String component,
                                 String event, Throwable error) {
        StringWriter sw = new StringWriter();
        if (error != null) {
            error.printStackTrace(new PrintWriter(sw));
        }
        write(component, event, sw.toString());
    }

    public static void activityState(Activity activity, String event) {
        if (activity == null) return;

        String details = "multiWindow=" + activity.isInMultiWindowMode()
                + "; changingConfig=" + activity.isChangingConfigurations()
                + "; finishing=" + activity.isFinishing()
                + "; thread=" + Thread.currentThread().getName();

        try {
            View decor = activity.getWindow().getDecorView();
            Rect rect = new Rect();
            decor.getWindowVisibleDisplayFrame(rect);

            details += "; decor=" + decor.getWidth() + "x"
                    + decor.getHeight()
                    + "; visible=" + rect.width() + "x" + rect.height();
        } catch (Throwable t) {
            details += "; dimensions_error=" + t.getClass().getSimpleName();
        }

        write("ACTIVITY", event, details);
    }

    public static void configuration(Context context,
                                     String event,
                                     Configuration config) {
        String details = "";

        if (config != null) {
            details = "orientation=" + config.orientation
                    + "; screenWidthDp=" + config.screenWidthDp
                    + "; screenHeightDp=" + config.screenHeightDp
                    + "; smallestScreenWidthDp=" + config.smallestScreenWidthDp;
        }

        write("MULTI_WINDOW", event, details);
    }

    public static String getLogPath(Context context) {
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir != null) {
                return new File(dir, "gpt_sender_diagnostics.log").getAbsolutePath();
            }
        } catch (Throwable ignored) {}

        return new File(context.getFilesDir(),
                "gpt_sender_diagnostics.log").getAbsolutePath();
    }

    private static void write(String component, String event, String details) {
        String safeComponent = component == null ? "UNKNOWN" : component;
        String safeEvent = event == null ? "UNKNOWN" : event;
        String safeDetails = details == null ? "" : details;

        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());

        String line = timestamp
                + " | " + safeComponent
                + " | " + safeEvent
                + " | thread=" + Thread.currentThread().getName()
                + " | " + safeDetails.replace('\n', ' ')
                + "\n";

        Log.i(TAG, line.trim());

        Context context = appContext;
        if (context == null) return;

        synchronized (LOCK) {
            try {
                File file = new File(getLogPath(context));
                File parent = file.getParentFile();

                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                if (file.exists() && file.length() > 2 * 1024 * 1024L) {
                    File old = new File(file.getParentFile(),
                            "gpt_sender_diagnostics.previous.log");
                    if (old.exists()) old.delete();
                    file.renameTo(old);
                }

                try (FileWriter writer = new FileWriter(file, true)) {
                    writer.write(line);
                    writer.flush();
                }
            } catch (Throwable t) {
                Log.e(TAG, "LOG_WRITE_FAILED", t);
            }
        }
    }
}
