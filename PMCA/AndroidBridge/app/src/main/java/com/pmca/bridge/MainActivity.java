package com.pmca.bridge;


import android.Manifest;
import android.app.PendingIntent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.pmca.bridge.core.PmcaController;
import com.pmca.bridge.logging.PmcaLogger;

public class MainActivity extends Activity {
    private PmcaController controller;
    private EditText input;
    private TextView status;
    private TextView output;
    private TextView logs;

    private final BroadcastReceiver termuxResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String result = intent.getStringExtra("result_text");
            if (result != null) {
                output.setText(result);
                setStatus("ОТВЕТ TERMUX ПОЛУЧЕН", true);
                refreshLogs();
            }
        }
    };
    private boolean resultReceiverRegistered = false;


    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return layout;
    }

    private void addGap(LinearLayout parent, int heightDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        return button;
    }

    private void addButton(LinearLayout parent, Button button, float weight) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(54), weight);
        params.setMargins(dp(4), 0, dp(4), 0);
        parent.addView(button, params);
    }

    private TextView makeLabel(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(16);
        view.setTypeface(null, Typeface.BOLD);
        view.setTextColor(Color.rgb(30, 45, 60));
        return view;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        controller = new PmcaController(this);
        buildScreen();
        refreshLogs();
        String lastResult = getSharedPreferences("pmca_results", MODE_PRIVATE).getString("last_result", "");
        if (!lastResult.isEmpty()) output.setText(lastResult);
        setStatus("ГОТОВ К РАБОТЕ", false);
    }

    private void buildScreen() {
        // Внешний контейнер: верхняя панель остаётся неподвижной.
        LinearLayout screen = column();
        screen.setBackgroundColor(Color.rgb(244, 247, 250));

        // Закреплённая строка: название слева, запуск панели справа.
        LinearLayout topBar = row();
        topBar.setPadding(dp(8), dp(4), dp(8), dp(4));

        TextView title = makeLabel("PMCA");
        title.setTextSize(22);
        topBar.addView(title, new LinearLayout.LayoutParams(
                0, dp(48), 1f));

        Button compactMode = makeButton("▣ Открыть панель");
        compactMode.setTextSize(13);
        compactMode.setMinWidth(0);
        compactMode.setPadding(dp(8), 0, dp(8), 0);
        topBar.addView(compactMode, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        compactMode.setOnClickListener(v -> openCompactPanel());

        Button modeG = makeButton("G Режим G");
        modeG.setTextSize(13);
        modeG.setMinWidth(0);
        modeG.setPadding(dp(7), 0, dp(7), 0);
        topBar.addView(modeG, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        modeG.setOnClickListener(v -> openModeGPanel());

        screen.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        // Прокручивается только содержимое, а не верхняя строка.
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = column();
        root.setPadding(dp(16), dp(8), dp(16), dp(16));

        TextView subtitle = new TextView(this);
        subtitle.setText("Portable Modular Controller Architecture");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle);

        addGap(root, 12);
        status = makeLabel("");
        root.addView(status);

        addGap(root, 14);
        root.addView(makeLabel("Команда / текст"));
        addGap(root, 6);

        input = new EditText(this);
        input.setHint("Введите команду для Termux…");
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setTextSize(16);
        input.setMinLines(3);
        input.setMaxLines(3);
        input.setVerticalScrollBarEnabled(true);
        input.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackgroundColor(Color.WHITE);

        // Фиксированная высота поля ввода: 100 dp.
        root.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100)));

        addGap(root, 8);
        LinearLayout firstRow = row();
        Button send = makeButton("Выполнить в Termux");
        Button paste = makeButton("Вставить из буфера");
        addButton(firstRow, send, 1.15f);
        addButton(firstRow, paste, 1f);
        root.addView(firstRow);

        addGap(root, 8);
        LinearLayout secondRow = row();
        Button installApp = makeButton("Установить приложение");
        Button clear = makeButton("Очистить ввод");
        addButton(secondRow, installApp, 1f);
        addButton(secondRow, clear, 1f);
        root.addView(secondRow);
        addGap(root, 8);
        addGap(root, 8);
        Button copyResult = makeButton("Скопировать результат");
        root.addView(copyResult, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        addGap(root, 8);
        Button copyLog = makeButton("Скопировать лог");
        root.addView(copyLog, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        addGap(root, 16);
        root.addView(makeLabel("Результат"));
        addGap(root, 6);

        output = new TextView(this);
        output.setText("Здесь появятся сообщения о действиях PMCA.");
        output.setTextSize(14);
        output.setTextColor(Color.rgb(35, 45, 55));
        output.setPadding(dp(12), dp(12), dp(12), dp(12));
        output.setBackgroundColor(Color.WHITE);
        root.addView(output, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));


        addGap(root, 16);
        LinearLayout logHeader = row();
        TextView logTitle = makeLabel("Журнал — последние 50 строк");
        logHeader.addView(logTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button refresh = makeButton("Обновить");
        refresh.setMinHeight(dp(44));
        logHeader.addView(refresh);
        root.addView(logHeader);

        addGap(root, 6);
        logs = new TextView(this);
        logs.setTextSize(12);
        logs.setTextColor(Color.rgb(40, 50, 60));
        logs.setPadding(dp(10), dp(10), dp(10), dp(10));
        logs.setBackgroundColor(Color.WHITE);
        root.addView(logs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));


        scroll.addView(root);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(screen);

        send.setOnClickListener(v -> {
            hideKeyboard();
            String command = input.getText().toString().trim();
            if (command.isEmpty()) {
                setStatus("НУЖНА КОМАНДА", false);
                Toast.makeText(this, "Сначала введите команду", Toast.LENGTH_SHORT).show();
                return;
            }

            output.setText("Команда отправляется через штатный RUN_COMMAND…");
            setStatus("ЗАПУСК TERMUX", false);
            boolean ok = controller.runNativeCommand(command);

            if (ok) {
                output.setText("Команда передана непосредственно Termux.\n"
                        + "Ожидается автоматический возврат результата…");
                setStatus("КОМАНДА ЗАПУЩЕНА", true);
                PmcaLogger.log("UI: RUN_COMMAND принят");
            } else {
                output.setText("Не удалось запустить команду через RUN_COMMAND.\n"
                        + "Проверьте разрешение Termux для PMCA и настройку "
                        + "allow-external-apps.");
                setStatus("ЗАПУСК НЕ УДАЛСЯ", false);
            }
            refreshLogs();
        });

        paste.setOnClickListener(v -> {
            // Сначала очищаем старый текст.
            input.setText("");

            // Затем читаем буфер и вставляем его содержимое.
            String text = controller.readClipboard();

            if (text != null && !text.isEmpty()) {
                input.setText(text);
                input.setSelection(input.length());
                setStatus("ТЕКСТ ВСТАВЛЕН", true);
            } else {
                setStatus("БУФЕР ПУСТ", false);
            }

            refreshLogs();
        });

        installApp.setOnClickListener(v -> installLatestApk());

clear.setOnClickListener(v -> {
            input.setText("");
            output.setText("Ввод очищен.");
            setStatus("ВВОД ОЧИЩЕН", true);
            PmcaLogger.log("UI: поле ввода очищено");
            refreshLogs();
        });

        copyResult.setOnClickListener(v -> {
            boolean ok = controller.copy(output.getText().toString(), "PMCA результат");
            setStatus(ok ? "РЕЗУЛЬТАТ СКОПИРОВАН" : "КОПИРОВАНИЕ НЕ УДАЛОСЬ", ok);
            refreshLogs();
        });

        refresh.setOnClickListener(v -> refreshLogs());

        copyLog.setOnClickListener(v -> {
            refreshLogs();
            boolean ok = controller.copy(PmcaLogger.getText(), "PMCA лог");
            setStatus(ok ? "ЛОГ СКОПИРОВАН" : "КОПИРОВАНИЕ НЕ УДАЛОСЬ", ok);
            refreshLogs();
        });
    }

    private void openModeGPanel() {
        if (android.os.Build.VERSION.SDK_INT >= 23
                && !Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(permission);
            Toast.makeText(this,
                    "Разрешите PMCA отображение поверх других приложений, затем нажмите «Режим G» ещё раз.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent panel = new Intent(this,
                com.pmca.bridge.overlay.ModeGPanelService.class);
        panel.setAction(com.pmca.bridge.overlay.ModeGPanelService.ACTION_TOGGLE);
        startService(panel);
        Toast.makeText(this, "Режим G переключён", Toast.LENGTH_SHORT).show();
    }

    private void openCompactPanel() {
        if (android.os.Build.VERSION.SDK_INT >= 23
                && !Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(permission);
            Toast.makeText(this,
                    "Разрешите PMCA отображение поверх других приложений, затем нажмите кнопку ещё раз.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent panel = new Intent(this,
                com.pmca.bridge.overlay.FloatingPanelService.class);
        panel.setAction(com.pmca.bridge.overlay.FloatingPanelService.ACTION_SHOW);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            startService(panel);
        } else {
            startService(panel);
        }
        Toast.makeText(this, "Плавающая панель PMCA открыта", Toast.LENGTH_SHORT).show();
    }

    private void setStatus(String text, boolean success) {
        status.setText("● " + text);
        status.setTextColor(success ? Color.rgb(20, 125, 70) : Color.rgb(155, 75, 35));
    }

    private void refreshLogs() {
        if (logs != null) logs.setText(PmcaLogger.getText());
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!resultReceiverRegistered) {
            IntentFilter filter = new IntentFilter(
                    com.pmca.bridge.termux.TermuxResultService.ACTION_RESULT);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(termuxResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(termuxResultReceiver, filter);
            }
            resultReceiverRegistered = true;
        }

        // Восстанавливаем ответ, если callback пришёл,
        // пока MainActivity была на паузе.
        String savedResult = getSharedPreferences(
                "pmca_results", MODE_PRIVATE)
                .getString("last_result", "");

        if (savedResult != null && !savedResult.isEmpty()) {
            output.setText(savedResult);
            setStatus("СОХРАНЁННЫЙ ОТВЕТ TERMUX", true);
            refreshLogs();
        }
    }

    @Override
    protected void onPause() {
        if (resultReceiverRegistered) {
            try {
                unregisterReceiver(termuxResultReceiver);
            } catch (Exception ignored) {}
            resultReceiverRegistered = false;
        }
        super.onPause();
    }


    private static final int PMCA_INSTALL_PERMISSION_REQUEST = 7401;
    private static final int PMCA_INSTALL_STORAGE_REQUEST = 7402;

    private void installLatestApk() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PMCA_INSTALL_STORAGE_REQUEST);
            setStatus("НУЖЕН ДОСТУП К APK", false);
            return;
        }

        continueLatestApkInstall();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PMCA_INSTALL_STORAGE_REQUEST) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                continueLatestApkInstall();
            } else {
                setStatus("НЕТ ДОСТУПА К APK", false);
                android.widget.Toast.makeText(
                        this,
                        "Разреши доступ к файлам, затем нажми кнопку ещё раз.",
                        android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    private void continueLatestApkInstall() {
        if (Build.VERSION.SDK_INT >= 26
                && !getPackageManager().canRequestPackageInstalls()) {
            setStatus("РАЗРЕШИ УСТАНОВКУ ПРИЛОЖЕНИЙ", false);
            try {
                Intent settingsIntent = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(
                        settingsIntent, PMCA_INSTALL_PERMISSION_REQUEST);
            } catch (Exception e) {
                PmcaLogger.log("Не удалось открыть настройки установки: " + e);
                android.widget.Toast.makeText(
                        this,
                        "Разреши установку приложений из PMCA в настройках Android.",
                        android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }

        startLatestApkInstall();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PMCA_INSTALL_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT < 26
                    || getPackageManager().canRequestPackageInstalls()) {
                startLatestApkInstall();
            } else {
                setStatus("РАЗРЕШЕНИЕ НЕ ПОЛУЧЕНО", false);
            }
        }
    }

    private void startLatestApkInstall() {
        File downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        File apk = new File(downloads, "PMCA.apk");

        if (!apk.isFile() || apk.length() == 0) {
            setStatus("APK НЕ НАЙДЕН", false);
            android.widget.Toast.makeText(
                    this,
                    "Сначала собери проект и скопируй APK в Download/PMCA.apk.",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        int sessionId = -1;
        try {
            PackageInstaller installer = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sessionId = installer.createSession(params);

            try (PackageInstaller.Session session = installer.openSession(sessionId);
                 FileInputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("PMCA.apk", 0, apk.length())) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
                session.fsync(out);
            }

            Intent callback = new Intent(this, InstallStatusReceiver.class);
            callback.setAction("com.pmca.bridge.INSTALL_STATUS");

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent statusIntent =
                    PendingIntent.getBroadcast(this, sessionId, callback, flags);

            try (PackageInstaller.Session commitSession =
                         installer.openSession(sessionId)) {
                commitSession.commit(statusIntent.getIntentSender());
            }

            setStatus("ЗАПРОС УСТАНОВКИ ОТПРАВЛЕН", true);
            PmcaLogger.log("Запрошена установка: " + apk.getAbsolutePath());
        } catch (Exception e) {
            if (sessionId >= 0) {
                try {
                    getPackageManager().getPackageInstaller().abandonSession(sessionId);
                } catch (Exception ignored) {
                }
            }
            setStatus("ОШИБКА УСТАНОВКИ APK", false);
            PmcaLogger.log("Ошибка установки APK: " + e);
            android.widget.Toast.makeText(
                    this,
                    "Не удалось запустить установку: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }

        refreshLogs();
    }

}
