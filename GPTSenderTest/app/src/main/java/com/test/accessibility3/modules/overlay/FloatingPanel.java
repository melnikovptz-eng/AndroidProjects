package com.test.accessibility3.modules.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingPanel {

    public interface Listener {
        void onSend();
        void onClose();
    }

    private final Context context;
    private final Listener listener;

    private WindowManager windowManager;
    private View panel;

    public FloatingPanel(
            Context context,
            Listener listener) {

        this.context = context;
        this.listener = listener;
    }

    public boolean show() {

        if (!Settings.canDrawOverlays(context)) {
            return false;
        }

        if (panel != null) {
            return true;
        }

        windowManager =
                (WindowManager)
                        context.getSystemService(
                                Context.WINDOW_SERVICE);

        LinearLayout layout =
                new LinearLayout(context);

        layout.setOrientation(
                LinearLayout.VERTICAL);

        layout.setPadding(
                16,
                16,
                16,
                16);

        layout.setBackgroundColor(
                Color.WHITE);

        TextView title =
                new TextView(context);

        title.setText("AUTOCLICKER");
        title.setTextSize(18);
        title.setTextColor(Color.BLACK);
        title.setPadding(8, 8, 8, 12);

        layout.addView(title);

        Button send =
                new Button(context);

        send.setText("ОТПРАВИТЬ");
        send.setTextSize(16);

        send.setOnClickListener(
                v -> onSend());

        layout.addView(send);

        Button close =
                new Button(context);

        close.setText("ЗАКРЫТЬ");
        close.setTextSize(16);

        close.setOnClickListener(
                v -> onClose());

        layout.addView(close);

        panel = layout;

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);

        params.gravity =
                Gravity.TOP | Gravity.START;

        params.x = 20;
        params.y = 120;

        try {

            windowManager.addView(
                    panel,
                    params);

            return true;

        } catch (Exception e) {

            panel = null;
            return false;
        }
    }

    private void onSend() {

        if (listener != null) {
            listener.onSend();
        }
    }

    private void onClose() {

        if (listener != null) {
            listener.onClose();
        } else {
            hide();
        }
    }

    public void hide() {

        if (windowManager == null ||
                panel == null) {
            return;
        }

        try {

            windowManager.removeView(panel);

        } catch (Exception ignored) {
        }

        panel = null;
    }

    public boolean isShown() {
        return panel != null;
    }
}
