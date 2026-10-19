package com.test.accessibility3.modules.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Small draggable and resizable sender panel.
 * The title bar moves the panel; the bottom-right grip resizes it.
 */
public final class FloatingPanel {
    public interface Listener {
        void onSend();
        void onClose();
    }

    private final Context context;
    private final Listener listener;
    private WindowManager windowManager;
    private FrameLayout panel;
    private WindowManager.LayoutParams params;

    private int minWidth;
    private int minHeight;
    private int maxWidth;
    private int maxHeight;
    private float lastRawX;
    private float lastRawY;
    private int startX;
    private int startY;
    private int startWidth;
    private int startHeight;

    public FloatingPanel(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        float density = context.getResources().getDisplayMetrics().density;
        minWidth = dp(180, density);
        minHeight = dp(150, density);
        maxWidth = Math.max(minWidth, context.getResources().getDisplayMetrics().widthPixels - dp(16, density));
        maxHeight = Math.max(minHeight, context.getResources().getDisplayMetrics().heightPixels - dp(48, density));
    }

    private int dp(int value, float density) {
        return (int) (value * density + 0.5f);
    }

    public boolean show() {
        if (!Settings.canDrawOverlays(context)) return false;
        if (panel != null) return true;

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return false;

        float density = context.getResources().getDisplayMetrics().density;
        int initialWidth = Math.min(maxWidth, dp(240, density));
        int initialHeight = Math.min(maxHeight, dp(190, density));

        panel = new FrameLayout(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(242, 245, 248, 252));
        background.setCornerRadius(dp(12, density));
        background.setStroke(dp(1, density), Color.rgb(70, 120, 180));
        panel.setBackground(background);
        panel.setElevation(dp(8, density));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        // Reserve space for the resize grip so it never covers the buttons.
        column.setPadding(dp(8, density), dp(6, density), dp(40, density), dp(34, density));
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        panel.addView(column, columnParams);

        TextView title = new TextView(context);
        title.setText("GPTSenderTest  ⋮⋮");
        title.setTextColor(Color.rgb(25, 55, 85));
        title.setTextSize(14);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8, density), dp(5, density), dp(8, density), dp(8, density));
        title.setContentDescription("Перетащите, чтобы переместить панель");
        column.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36, density)));
        attachMoveGesture(title);

        Button send = new Button(context);
        send.setText("Отправить");
        send.setAllCaps(false);
        send.setTextSize(14);
        column.addView(send, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        send.setOnClickListener(v -> {
            if (listener != null) listener.onSend();
        });

        Button close = new Button(context);
        close.setText("Скрыть панель");
        close.setAllCaps(false);
        close.setTextSize(12);
        column.addView(close, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        close.setOnClickListener(v -> {
            if (listener != null) listener.onClose();
            else hide();
        });

        TextView grip = new TextView(context);
        grip.setText("◢");
        grip.setTextSize(18);
        grip.setTextColor(Color.rgb(30, 95, 160));
        grip.setGravity(Gravity.CENTER);
        grip.setBackgroundColor(Color.argb(35, 30, 95, 160));
        grip.setContentDescription("Изменить размер панели");
        FrameLayout.LayoutParams gripParams = new FrameLayout.LayoutParams(
                dp(32, density), dp(28, density), Gravity.BOTTOM | Gravity.RIGHT);
        panel.addView(grip, gripParams);
        attachResizeGesture(grip);

        params = new WindowManager.LayoutParams(
                initialWidth,
                initialHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12, density);
        params.y = dp(120, density);

        try {
            windowManager.addView(panel, params);
            return true;
        } catch (Exception e) {
            panel = null;
            params = null;
            return false;
        }
    }

    private void attachMoveGesture(View handle) {
        handle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastRawX = event.getRawX();
                    lastRawY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = clamp(startX + (int) (event.getRawX() - lastRawX),
                            0, Math.max(0, context.getResources().getDisplayMetrics().widthPixels - params.width));
                    params.y = clamp(startY + (int) (event.getRawY() - lastRawY),
                            0, Math.max(0, context.getResources().getDisplayMetrics().heightPixels - params.height));
                    update();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        });
    }

    private void attachResizeGesture(View handle) {
        handle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastRawX = event.getRawX();
                    lastRawY = event.getRawY();
                    startWidth = params.width;
                    startHeight = params.height;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.width = clamp(startWidth + (int) (event.getRawX() - lastRawX),
                            minWidth, maxWidth);
                    params.height = clamp(startHeight + (int) (event.getRawY() - lastRawY),
                            minHeight, maxHeight);
                    params.x = clamp(params.x, 0,
                            Math.max(0, context.getResources().getDisplayMetrics().widthPixels - params.width));
                    params.y = clamp(params.y, 0,
                            Math.max(0, context.getResources().getDisplayMetrics().heightPixels - params.height));
                    update();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        });
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void update() {
        if (windowManager == null || panel == null || params == null) return;
        try {
            windowManager.updateViewLayout(panel, params);
        } catch (Exception ignored) {
            // Panel may be removed while a touch event is being delivered.
        }
    }

    public void hide() {
        if (windowManager == null || panel == null) return;
        try {
            windowManager.removeView(panel);
        } catch (Exception ignored) {
        }
        panel = null;
        params = null;
    }

    public boolean isShown() {
        return panel != null;
    }
}
