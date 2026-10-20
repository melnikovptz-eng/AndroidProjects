package com.pmca.bridge.overlay;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pmca.bridge.core.PmcaController;
import com.pmca.bridge.logging.PmcaLogger;
import com.pmca.bridge.termux.TermuxResultService;

/**
 * Compact G-mode edge panel. It deliberately reuses PMCA's existing
 * ClipboardPasteActivity, PmcaController and result storage.
 */
public final class GModePanelService extends Service {
    public static final String ACTION_SHOW = "com.pmca.bridge.gmode.SHOW";
    public static final String ACTION_HIDE = "com.pmca.bridge.gmode.HIDE";

    private static final String PREFS = "pmca_g_mode";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_RIGHT = "side_right";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private LinearLayout panel;
    private TextView moveIcon;
    private TextView termuxIcon;
    private TextView copyIcon;
    private PmcaController controller;
    private boolean receiverRegistered;
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !FloatingPanelService.ACTION_STATE_UPDATE.equals(intent.getAction())) {
                return;
            }
            int state = intent.getIntExtra("state", 0);
            if (termuxIcon == null) return;
            if (state == 1) {
                termuxIcon.setBackground(tileBackground(Color.rgb(255, 232, 180)));
            } else if (state == 2) {
                termuxIcon.setBackground(tileBackground(Color.rgb(210, 230, 250)));
            } else if (state == 3) {
                termuxIcon.setBackground(tileBackground(Color.rgb(207, 240, 220)));
            } else {
                termuxIcon.setBackground(tileBackground(Color.WHITE));
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        controller = new PmcaController(this);

        IntentFilter filter = new IntentFilter(FloatingPanelService.ACTION_STATE_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_HIDE.equals(intent.getAction())) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_ACTIVE, false).apply();
            removePanel();
            stopSelf();
            PmcaLogger.log("Режим G выключен");
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Для режима G разрешите отображение PMCA поверх других приложений",
                    Toast.LENGTH_LONG).show();
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_ACTIVE, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_ACTIVE, true).apply();
        if (panel == null) createPanel();
        return START_NOT_STICKY;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable tileBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(1), Color.rgb(195, 205, 218));
        return drawable;
    }

    private TextView makeIcon(String glyph, String description) {
        TextView view = new TextView(this);
        view.setText(glyph);
        view.setTextSize(25);
        view.setTextColor(Color.rgb(35, 48, 62));
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setContentDescription(description);
        view.setBackground(tileBackground(Color.WHITE));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private void addTile(LinearLayout container, TextView icon, Runnable action) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        container.addView(icon, lp);
        icon.setOnClickListener(v -> animateAction(icon, action));
    }

    private void animateAction(View view, Runnable action) {
        view.animate().cancel();
        view.setBackground(tileBackground(Color.rgb(220, 232, 245)));
        view.animate().scaleX(0.90f).scaleY(0.90f).alpha(0.78f)
                .setDuration(75)
                .withEndAction(() -> {
                    try {
                        action.run();
                    } finally {
                        view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                                .setDuration(110).start();
                        handler.postDelayed(() -> {
                            if (view == termuxIcon) {
                                termuxIcon.setBackground(tileBackground(Color.WHITE));
                            } else if (view == moveIcon) {
                                moveIcon.setBackground(tileBackground(Color.WHITE));
                            } else if (view == copyIcon && !"✓".contentEquals(copyIcon.getText())) {
                                copyIcon.setBackground(tileBackground(Color.WHITE));
                            }
                        }, 180);
                    }
                }).start();
    }

    private void createPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(5), dp(5), dp(5), dp(5));
        panel.setBackground(tileBackground(Color.rgb(240, 244, 249)));
        panel.setElevation(dp(7));

        moveIcon = makeIcon("↔", "Перенести панель на противоположный край");
        termuxIcon = makeIcon("T", "Отправить текст из буфера в Termux");
        termuxIcon.setTextSize(22);
        termuxIcon.setTextColor(Color.WHITE);
        termuxIcon.setBackground(tileBackground(Color.BLACK));
        copyIcon = makeIcon("⧉", "Скопировать результат Termux");

        // Порядок сверху вниз: перенос панели, отправка в Termux, копирование результата.
        addTile(panel, moveIcon, this::moveToOppositeSide);
        addTile(panel, termuxIcon, this::openClipboardCommand);
        addTile(panel, copyIcon, this::copyTermuxResult);

        boolean right = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_RIGHT, false);
        windowParams = new WindowManager.LayoutParams(
                dp(58),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        applySide(right, false);

        try {
            windowManager.addView(panel, windowParams);
            installKeyboardAvoidance();
            PmcaLogger.log("Режим G: панель показана у " + (right ? "правого" : "левого") + " края");
        } catch (Exception e) {
            panel = null;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_ACTIVE, false).apply();
            PmcaLogger.log("Режим G: ошибка показа панели: " + e.getClass().getSimpleName());
            stopSelf();
        }
    }

    /**
     * Best-effort keyboard avoidance. A non-focusable overlay may not receive
     * IME insets on every device, so this is a best-effort adjustment.
     */
    private void installKeyboardAvoidance() {
        if (panel == null) return;
        keyboardLayoutListener = () -> {
            if (panel == null || windowParams == null) return;
            Rect visible = new Rect();
            panel.getWindowVisibleDisplayFrame(visible);
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            int obscuredHeight = Math.max(0, screenHeight - visible.bottom);
            boolean plausibleImeInset = visible.bottom > dp(100)
                    && obscuredHeight > dp(150)
                    && obscuredHeight < screenHeight / 2;
            int targetY = plausibleImeInset ? obscuredHeight + dp(8) : dp(8);
            if (windowParams.y != targetY) {
                windowParams.y = targetY;
                try {
                    windowManager.updateViewLayout(panel, windowParams);
                } catch (Exception ignored) {
                }
            }
        };
        panel.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);
    }

    private void moveToOppositeSide() {
        boolean currentlyRight = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_RIGHT, false);
        boolean moveRight = !currentlyRight;
        if (panel == null || windowParams == null) return;

        panel.animate().cancel();
        panel.animate().translationX(moveRight ? dp(30) : -dp(30))
                .alpha(0.65f).setDuration(100).withEndAction(() -> {
                    applySide(moveRight, true);
                    panel.setTranslationX(0f);
                    panel.setAlpha(1f);
                    PmcaLogger.log("Режим G: панель перенесена к "
                            + (moveRight ? "правому" : "левому") + " краю");
                }).start();
    }

    private void applySide(boolean right, boolean updateLayout) {
        if (windowParams == null) return;
        windowParams.gravity = Gravity.BOTTOM | (right ? Gravity.RIGHT : Gravity.LEFT);
        windowParams.x = dp(8);
        windowParams.y = dp(8);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_RIGHT, right).apply();
        if (updateLayout && panel != null) {
            try {
                windowManager.updateViewLayout(panel, windowParams);
            } catch (Exception e) {
                PmcaLogger.log("Режим G: не удалось обновить сторону панели");
            }
        }
    }

    private void openClipboardCommand() {
        try {
            Intent intent = new Intent(this, ClipboardPasteActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            PmcaLogger.log("Режим G: запущено штатное чтение буфера и отправка команды в Termux");
        } catch (Exception e) {
            termuxIcon.setBackground(tileBackground(Color.rgb(255, 210, 210)));
            Toast.makeText(this, "Не удалось открыть чтение буфера", Toast.LENGTH_LONG).show();
            PmcaLogger.log("Режим G: ошибка запуска команды: " + e.getClass().getSimpleName());
        }
    }

    private void copyTermuxResult() {
        SharedPreferences prefs = getSharedPreferences("pmca_results", MODE_PRIVATE);
        String result = prefs.getString("last_result", "");
        if (result == null || result.isEmpty()) {
            copyIcon.setText("×");
            copyIcon.setBackground(tileBackground(Color.rgb(255, 220, 220)));
            Toast.makeText(this, "Ответ Termux ещё не получен", Toast.LENGTH_SHORT).show();
            PmcaLogger.log("Режим G: копирование отклонено — результата Termux нет");
            handler.postDelayed(() -> {
                if (copyIcon != null) {
                    copyIcon.setText("⧉");
                    copyIcon.setBackground(tileBackground(Color.WHITE));
                }
            }, 900);
            return;
        }

        boolean ok = controller.copy(result, "PMCA результат");
        copyIcon.setText(ok ? "✓" : "×");
        copyIcon.setBackground(tileBackground(ok
                ? Color.rgb(207, 240, 220)
                : Color.rgb(255, 220, 220)));
        Toast.makeText(this, ok ? "Результат скопирован" : "Не удалось скопировать результат",
                Toast.LENGTH_SHORT).show();
        PmcaLogger.log(ok ? "Режим G: результат Termux скопирован"
                : "Режим G: ошибка копирования результата");
        handler.postDelayed(() -> {
            if (copyIcon != null) {
                copyIcon.setText("▢");
                copyIcon.setBackground(tileBackground(Color.WHITE));
            }
        }, 900);
    }

    private void removePanel() {
        if (panel != null) {
            try {
                if (keyboardLayoutListener != null) {
                    ViewTreeObserver observer = panel.getViewTreeObserver();
                    if (observer.isAlive()) {
                        observer.removeOnGlobalLayoutListener(keyboardLayoutListener);
                    }
                    keyboardLayoutListener = null;
                }
                windowManager.removeView(panel);
            } catch (Exception ignored) {
            }
            panel = null;
        }
    }

    @Override
    public void onDestroy() {
        removePanel();
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            try {
                unregisterReceiver(stateReceiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, false).apply();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
