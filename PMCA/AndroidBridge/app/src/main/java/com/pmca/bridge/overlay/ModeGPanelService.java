package com.pmca.bridge.overlay;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pmca.bridge.core.PmcaController;
import com.pmca.bridge.logging.PmcaLogger;

/**
 * Mode G: compact bottom-edge action strip.
 * It intentionally reuses ClipboardPasteActivity and PmcaController instead
 * of introducing a second Termux execution/copy implementation.
 */
public final class ModeGPanelService extends Service {
    public static final String ACTION_TOGGLE = "com.pmca.bridge.modeg.TOGGLE";
    private static final String PREFS = "pmca_mode_g";
    private static final String KEY_SIDE = "panel_side";
    private static final int LEFT = 0;
    private static final int RIGHT = 1;

    private WindowManager windowManager;
    private View panel;
    private WindowManager.LayoutParams params;
    private PmcaController controller;
    private boolean chatLaunchAttempted;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        controller = new PmcaController(this);
        PmcaLogger.log("Режим G: сервис запущен");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Разрешите PMCA отображение поверх других приложений", Toast.LENGTH_LONG).show();
            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            permission.setData(android.net.Uri.parse("package:" + getPackageName()));
            permission.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permission);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (panel != null) {
            removePanel();
            PmcaLogger.log("Режим G: панель скрыта повторным нажатием");
            stopSelf();
            return START_NOT_STICKY;
        }

        createPanel();
        launchChatGptIfAvailable();
        return START_NOT_STICKY;
    }

    private GradientDrawable background(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(13));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView makeIcon(String symbol, String description) {
        TextView v = new TextView(this);
        v.setText(symbol);
        v.setTextSize(22);
        v.setTextColor(Color.rgb(32, 43, 55));
        v.setGravity(Gravity.CENTER);
        v.setContentDescription(description);
        v.setBackground(background(Color.WHITE, Color.rgb(190, 201, 214)));
        v.setClickable(true);
        v.setFocusable(true);
        v.setElevation(dp(2));
        return v;
    }

    private void addIcon(LinearLayout box, String symbol, String description,
                         View.OnClickListener action) {
        TextView icon = makeIcon(symbol, description);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
        lp.topMargin = dp(5);
        lp.bottomMargin = dp(2);
        box.addView(icon, lp);
        icon.setOnClickListener(v -> {
            v.animate().cancel();
            v.setScaleX(0.91f);
            v.setScaleY(0.91f);
            v.setAlpha(0.72f);
            v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(150).setInterpolator(new DecelerateInterpolator()).start();
            action.onClick(v);
        });
    }

    private void createPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(5), dp(4), dp(5));
        box.setBackground(background(Color.rgb(242, 245, 249), Color.rgb(185, 197, 210)));
        box.setElevation(dp(8));

        addIcon(box, "↔", "Переместить панель на противоположный край", v -> moveToOtherSide());

        // The black tile with a white T is visually distinct, as agreed.
        TextView termux = makeIcon("T", "Отправить команду из буфера в Termux");
        termux.setTextColor(Color.WHITE);
        termux.setTextSize(23);
        termux.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        termux.setBackground(background(Color.BLACK, Color.BLACK));
        LinearLayout.LayoutParams termuxLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        termuxLp.topMargin = dp(5);
        termuxLp.bottomMargin = dp(2);
        box.addView(termux, termuxLp);
        termux.setOnClickListener(v -> {
            animatePress(v);
            try {
                Intent intent = new Intent(this, ClipboardPasteActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                PmcaLogger.log("Режим G: запущен существующий путь отправки команды из буфера");
            } catch (Exception e) {
                PmcaLogger.log("Режим G: запуск Termux-команды не удался: "
                        + e.getClass().getSimpleName());
                Toast.makeText(this, "Не удалось отправить команду", Toast.LENGTH_SHORT).show();
            }
        });

        addIcon(box, "▢", "Скопировать результат Termux", v -> copyResult());

        panel = box;
        params = new WindowManager.LayoutParams(
                dp(54),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        int side = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_SIDE, LEFT);
        applySide(side, false);

        try {
            windowManager.addView(panel, params);
            PmcaLogger.log("Режим G: панель отображена у " + (side == LEFT ? "левого" : "правого") + " края");
        } catch (Exception e) {
            PmcaLogger.log("Режим G: ошибка отображения панели: " + e.getClass().getSimpleName());
            panel = null;
            stopSelf();
        }
    }

    private void animatePress(View v) {
        v.animate().cancel();
        v.setScaleX(0.91f);
        v.setScaleY(0.91f);
        v.setAlpha(0.68f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void moveToOtherSide() {
        int current = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_SIDE, LEFT);
        int next = current == LEFT ? RIGHT : LEFT;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_SIDE, next).apply();
        applySide(next, true);
        PmcaLogger.log("Режим G: панель перемещена к " + (next == LEFT ? "левому" : "правому") + " краю");
    }

    private void applySide(int side, boolean update) {
        if (params == null) return;
        params.gravity = Gravity.BOTTOM | (side == LEFT ? Gravity.LEFT : Gravity.RIGHT);
        params.x = dp(4);
        params.y = dp(8);
        if (update && panel != null) {
            try {
                windowManager.updateViewLayout(panel, params);
                panel.animate().translationX(side == LEFT ? -dp(3) : dp(3))
                        .setDuration(90).withEndAction(() -> panel.animate()
                                .translationX(0).setDuration(110).start()).start();
            } catch (Exception e) {
                PmcaLogger.log("Режим G: не удалось переместить панель: " + e.getClass().getSimpleName());
            }
        }
    }

    private void copyResult() {
        SharedPreferences prefs = getSharedPreferences("pmca_results", MODE_PRIVATE);
        String result = prefs.getString("last_result", "");
        if (result == null || result.isEmpty()) {
            Toast.makeText(this, "Ответ Termux ещё не получен", Toast.LENGTH_SHORT).show();
            PmcaLogger.log("Режим G: копирование невозможно — результата нет");
            return;
        }
        boolean ok = controller.copy(result, "PMCA результат");
        Toast.makeText(this, ok ? "Результат скопирован ✓" : "Копирование не удалось",
                Toast.LENGTH_SHORT).show();
        PmcaLogger.log(ok ? "Режим G: результат Termux скопирован"
                : "Режим G: ошибка копирования результата");
    }

    private void launchChatGptIfAvailable() {
        if (chatLaunchAttempted) return;
        chatLaunchAttempted = true;
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
            if (launch == null) {
                PmcaLogger.log("Режим G: приложение ChatGPT не найдено; панель оставлена открытой");
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            PmcaLogger.log("Режим G: запрошен запуск приложения ChatGPT");
        } catch (Exception e) {
            PmcaLogger.log("Режим G: не удалось открыть ChatGPT: " + e.getClass().getSimpleName());
        }
    }

    private void removePanel() {
        if (panel != null) {
            try {
                windowManager.removeView(panel);
            } catch (Exception ignored) {
            }
            panel = null;
        }
    }

    @Override
    public void onDestroy() {
        removePanel();
        super.onDestroy();
        PmcaLogger.log("Режим G: сервис остановлен");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
