package com.test.accessibility3.modules.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public final class GptSenderDebugOverlay {

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());

    private WindowManager manager;
    private View inputDot;
    private View targetDot;
    private View statusDot;

    private int inputX;
    private int inputY;
    private int targetX;
    private int targetY;

    private boolean hasInput;
    private boolean hasTarget;
    private boolean hasStatus;

    private final int diameter;
    private final int offset;

    private final Runnable hideRunnable = this::hideInternal;

    public GptSenderDebugOverlay(Context context) {
        this.context = context.getApplicationContext();

        float density = context.getResources()
                .getDisplayMetrics().density;

        diameter = Math.max(12, (int) (14 * density));
        offset = Math.max(16, (int) (20 * density));

        manager = (WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE
        );
    }

    public void showInput(int x, int y) {
        main.post(() -> {
            inputX = x;
            inputY = y;
            hasInput = true;

            ensureDots();
            place(inputDot, inputX, inputY, Color.rgb(35, 145, 255));

            scheduleHide();
        });
    }

    public void showTarget(int x, int y) {
        main.post(() -> {
            targetX = x;
            targetY = y;
            hasTarget = true;

            ensureDots();
            place(targetDot, targetX, targetY, Color.rgb(255, 190, 25));
            place(statusDot, targetX + offset, targetY,
                    Color.rgb(255, 190, 25));

            scheduleHide();
        });
    }

    public void showNotFound() {
        main.post(() -> {
            if (!hasInput) {
                return;
            }

            ensureDots();

            hasStatus = true;
            ensureDots();
            int x = inputX + offset * 2;
            int y = inputY;

            place(statusDot, x, y, Color.rgb(255, 55, 55));
            scheduleHide();
        });
    }

    public void setClickResult(boolean success) {
        main.post(() -> {
            if (!hasTarget) {
                return;
            }

            hasStatus = true;
            ensureDots();

            place(statusDot, targetX + offset, targetY,
                    success
                            ? Color.rgb(30, 220, 95)
                            : Color.rgb(255, 55, 55));

            scheduleHide();
        });
    }

    private void ensureDots() {
        if (manager == null || !Settings.canDrawOverlays(context)) {
            return;
        }

        if (hasInput && inputDot == null) {
            inputDot = createDot(Color.rgb(35, 145, 255));
            add(inputDot, inputX, inputY);
        }

        if (hasTarget && targetDot == null) {
            targetDot = createDot(Color.rgb(255, 190, 25));
            add(targetDot, targetX, targetY);
        }

        if (hasStatus && statusDot == null) {
            statusDot = createDot(Color.rgb(255, 190, 25));
            add(statusDot, targetX + offset, targetY);
        }
    }

    private View createDot(int color) {
        View view = new View(context);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(
                Math.max(1, diameter / 10),
                Color.argb(180, 255, 255, 255)
        );

        view.setBackground(drawable);
        view.setAlpha(0.48f);

        return view;
    }

    private WindowManager.LayoutParams params(int x, int y) {
        WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        diameter,
                        diameter,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );

        p.gravity = Gravity.TOP | Gravity.START;
        p.x = x - diameter / 2;
        p.y = y - diameter / 2;
        p.alpha = 0.48f;

        return p;
    }

    private void add(View view, int x, int y) {
        try {
            manager.addView(view, params(x, y));
        } catch (Exception ignored) {
            // Например, разрешение overlay ещё не выдано.
        }
    }

    private void place(View view, int x, int y, int color) {
        if (view == null || manager == null) {
            return;
        }

        try {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            drawable.setStroke(
                    Math.max(1, diameter / 10),
                    Color.argb(180, 255, 255, 255)
            );

            view.setBackground(drawable);
            view.setAlpha(0.48f);

            WindowManager.LayoutParams p = params(x, y);
            manager.updateViewLayout(view, p);
        } catch (Exception ignored) {
            // Повторное обновление произойдёт при следующем сканировании.
        }
    }

    private void scheduleHide() {
        main.removeCallbacks(hideRunnable);
        main.postDelayed(hideRunnable, 15000L);
    }

    public void hide() {
        main.post(this::hideInternal);
    }

    private void hideInternal() {
        main.removeCallbacks(hideRunnable);

        remove(inputDot);
        remove(targetDot);
        remove(statusDot);

        inputDot = null;
        targetDot = null;
        statusDot = null;

        hasInput = false;
        hasTarget = false;
        hasStatus = false;
    }

    private void remove(View view) {
        if (view == null || manager == null) {
            return;
        }

        try {
            manager.removeView(view);
        } catch (Exception ignored) {
        }
    }
}
