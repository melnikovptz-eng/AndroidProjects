package com.pmca.bridge.overlay;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pmca.bridge.MainActivity;
import com.pmca.bridge.core.PmcaController;
import com.pmca.bridge.logging.PmcaLogger;
import com.pmca.bridge.termux.TermuxResultService;

public final class FloatingPanelService extends Service {
    public static final String ACTION_SHOW = "com.pmca.bridge.overlay.SHOW";
    public static final String ACTION_HIDE = "com.pmca.bridge.overlay.HIDE";
    public static final String ACTION_STATE_UPDATE =
            "com.pmca.bridge.overlay.STATE_UPDATE";

    private static final String PREFS = "pmca_results";
    private static final String KEY_COMMAND = "compact_command";
    private static final String KEY_STATE = "compact_state";

    private static final int READY = 0;
    private static final int PASTED = 1;
    private static final int WAITING = 2;
    private static final int RECEIVED = 3;

    private WindowManager wm;
    private View panel;
    private TextView dot;
    private WindowManager.LayoutParams params;
    private PmcaController controller;
    private boolean receiverRegistered;

    private float downX, downY, startX, startY;
    private boolean dragging;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (ACTION_STATE_UPDATE.equals(intent.getAction())) {
                updateState(intent.getIntExtra("state", READY));
                return;
            }

            if (TermuxResultService.ACTION_RESULT.equals(intent.getAction())) {
                String result = intent.getStringExtra("result_text");
                if (result != null && !result.isEmpty()) {
                    // last_result сохраняет TermuxResultService.
                    // Здесь обновляется только состояние плавающей панели.
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putInt(KEY_STATE, RECEIVED)
                            .apply();

                    updateState(RECEIVED);
                    PmcaLogger.log("Панель: получен ответ Termux");
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        controller = new PmcaController(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(TermuxResultService.ACTION_RESULT);
        filter.addAction(ACTION_STATE_UPDATE);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(resultReceiver, filter);
        }

        receiverRegistered = true;
        PmcaLogger.log("Плавающая панель PMCA запущена");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_HIDE.equals(intent.getAction())) {
            removePanel();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(
                    this,
                    "Разрешите PMCA отображение поверх других приложений",
                    Toast.LENGTH_LONG
            ).show();

            Intent settings = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            settings.setData(android.net.Uri.parse("package:" + getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(settings);

            stopSelf();
            return START_NOT_STICKY;
        }

        if (panel == null) createPanel();
        return START_NOT_STICKY;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), Color.rgb(195, 205, 218));
        return drawable;
    }

    private TextView icon(String symbol, String description) {
        TextView view = new TextView(this);
        view.setText(symbol);
        view.setTextSize(23);
        view.setTextColor(Color.rgb(35, 48, 62));
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setContentDescription(description);
        view.setBackground(background(Color.rgb(255, 255, 255), 12));
        view.setClickable(true);
        view.setFocusable(false);
        return view;
    }

    private void addIcon(
            LinearLayout box,
            String symbol,
            String description,
            View.OnClickListener listener
    ) {
        TextView view = icon(symbol, description);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        lp.topMargin = dp(4);

        box.addView(view, lp);
        view.setOnClickListener(listener);
    }

    private void addActionButton(
            LinearLayout box,
            String symbol,
            String title,
            View.OnClickListener listener
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, dp(8), 0);
        row.setBackground(background(Color.rgb(255, 255, 255), 10));
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        rowParams.topMargin = dp(5);

        TextView symbolView = new TextView(this);
        symbolView.setText(symbol);
        symbolView.setTextSize(21);
        symbolView.setTextColor(Color.rgb(35, 48, 62));
        symbolView.setGravity(Gravity.CENTER);
        row.addView(symbolView, new LinearLayout.LayoutParams(dp(34), dp(44)));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTextColor(Color.rgb(35, 48, 62));
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(true);
        titleView.setContentDescription(title);

        row.addView(titleView, new LinearLayout.LayoutParams(
                0, dp(44), 1f
        ));

        row.setOnClickListener(listener);
        box.addView(row, rowParams);
    }

    private void createPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(5), dp(5), dp(5), dp(5));
        box.setBackground(background(Color.rgb(240, 244, 249), 14));
        box.setElevation(dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);

        dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(18);
        dot.setTextColor(Color.GRAY);
        dot.setGravity(Gravity.CENTER);
        dot.setContentDescription("Состояние PMCA");

        top.addView(dot, new LinearLayout.LayoutParams(dp(27), dp(34)));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(18);
        close.setTextColor(Color.DKGRAY);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Закрыть панель");
        close.setBackground(background(Color.rgb(255, 255, 255), 9));

        top.addView(close, new LinearLayout.LayoutParams(dp(27), dp(32)));

        close.setOnClickListener(v -> {
            removePanel();
            stopSelf();
            PmcaLogger.log("Плавающая панель закрыта пользователем");
        });

        top.setOnTouchListener(this::dragTouch);
        dot.setOnTouchListener(this::dragTouch);
        box.addView(top);

        addActionButton(box, "▶", "Выполнить из буфера",
                v -> runClipboardCommand());

        addActionButton(box, "▣", "Копировать результат",
                v -> copyResult());

        panel = box;

        params = new WindowManager.LayoutParams(
                dp(230),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(100);

        try {
            wm.addView(panel, params);

            // Не считаем старый сохранённый ответ новым полученным ответом.
            int state = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getInt(KEY_STATE, READY);
            updateState(state);

            PmcaLogger.log("Узкая панель PMCA отображена");
        } catch (Exception e) {
            PmcaLogger.log("Ошибка отображения панели: " + e.getMessage());
            panel = null;
            stopSelf();
        }
    }

    private boolean dragTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                startX = params.x;
                startY = params.y;
                dragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;

                if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) {
                    dragging = true;
                }

                if (dragging && panel != null) {
                    params.x = Math.max(0, (int) (startX + dx));
                    params.y = Math.max(0, (int) (startY + dy));

                    try {
                        wm.updateViewLayout(panel, params);
                    } catch (Exception ignored) {
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return true;
    }

    private void pasteClipboard() {
        // Пробуем прочитать буфер напрямую, не запуская Activity.
        try {
            String command = controller.readClipboard();

            if (command == null || command.trim().isEmpty()) {
                updateState(READY);
                Toast.makeText(
                        this,
                        "Буфер пуст или не содержит доступного текста",
                        Toast.LENGTH_SHORT
                ).show();
                PmcaLogger.log("Буфер: текст отсутствует");
                return;
            }

            if (command.trim().isEmpty()) {
                updateState(READY);
                Toast.makeText(
                        this,
                        "В буфере нет текста команды",
                        Toast.LENGTH_SHORT
                ).show();
                PmcaLogger.log("Буфер: текст команды пуст");
                return;
            }

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_COMMAND, command)
                    .putInt(KEY_STATE, PASTED)
                    .apply();

            PmcaLogger.log(
                    "Буфер прочитан напрямую из плавающей панели"
            );

            runCommand();

        } catch (SecurityException e) {
            updateState(READY);
            Toast.makeText(
                    this,
                    "Android запретил чтение буфера в фоне",
                    Toast.LENGTH_LONG
            ).show();
            PmcaLogger.log(
                    "Чтение буфера запрещено Android: "
                            + e.getClass().getSimpleName()
            );
        } catch (Exception e) {
            updateState(READY);
            Toast.makeText(
                    this,
                    "Ошибка чтения буфера",
                    Toast.LENGTH_LONG
            ).show();
            PmcaLogger.log(
                    "Ошибка чтения буфера: "
                            + e.getClass().getSimpleName()
            );
        }
    }

    private void runCommand() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String command = prefs.getString(KEY_COMMAND, "");

        if (command == null || command.trim().isEmpty()) {
            Toast.makeText(this, "Сначала нажмите 📋", Toast.LENGTH_LONG).show();
            updateState(READY);
            return;
        }

        updateState(WAITING);

        boolean ok;
        try {
            ok = controller.runNativeCommand(command);
        } catch (Exception e) {
            ok = false;
            PmcaLogger.log("Ошибка запуска Termux: " + e.getMessage());
        }

        if (!ok) {
            updateState(PASTED);
            Toast.makeText(
                    this,
                    "Termux не принял команду. Проверьте RUN_COMMAND.",
                    Toast.LENGTH_LONG
            ).show();
            PmcaLogger.log("Команда не принята Termux");
        } else {
            PmcaLogger.log("Команда отправлена в Termux");
        }
    }

    private void runClipboardCommand() {
        // Читаем буфер в активном прозрачном Activity.
        // Основное окно PMCA не запускаем.
        try {
            Intent intent = new Intent(
                    FloatingPanelService.this,
                    ClipboardPasteActivity.class
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION
            );

            startActivity(intent);

            PmcaLogger.log(
                    "Панель: запущено активное окно для чтения буфера"
            );
        } catch (Exception e) {
            PmcaLogger.log(
                    "Не удалось открыть окно чтения буфера: "
                            + e.getClass().getSimpleName()
            );

            Toast.makeText(
                    this,
                    "Не удалось открыть окно чтения буфера",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void copyResult() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String result = prefs.getString("last_result", "");

        if (result == null || result.isEmpty()) {
            Toast.makeText(this, "Ответ Termux ещё не получен", Toast.LENGTH_SHORT).show();
            return;
        }

        if (controller.copy(result, "PMCA результат")) {
            Toast.makeText(this, "Ответ скопирован", Toast.LENGTH_SHORT).show();
            PmcaLogger.log("Результат скопирован в буфер");
        } else {
            Toast.makeText(this, "Не удалось скопировать ответ", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateState(int state) {
        if (dot == null) return;

        int color;
        String description;

        switch (state) {
            case PASTED:
                color = Color.rgb(225, 165, 0);
                description = "Команда подготовлена";
                break;

            case WAITING:
                color = Color.rgb(210, 55, 45);
                description = "Ожидание ответа Termux";
                break;

            case RECEIVED:
                color = Color.rgb(20, 150, 75);
                description = "Ответ получен";
                break;

            default:
                state = READY;
                color = Color.GRAY;
                description = "Готово к команде";
                break;
        }

        dot.setTextColor(color);
        dot.setContentDescription(description);

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_STATE, state)
                .apply();
    }

    private void removePanel() {
        if (panel != null) {
            try {
                wm.removeView(panel);
            } catch (Exception ignored) {
            }
            panel = null;
        }
    }

    @Override
    public void onDestroy() {
        removePanel();

        if (receiverRegistered) {
            try {
                unregisterReceiver(resultReceiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }

        PmcaLogger.log("Плавающая панель PMCA остановлена");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
