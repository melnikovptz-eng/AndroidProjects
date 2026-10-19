package com.test.accessibility3;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.text.InputType;
import android.widget.EditText;
import android.util.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GptSenderActivity extends Activity {
    private TextView status;
    private EditText message;
    private Button prepareButton;
    private Button sendButton;

    private boolean busy = false;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor();

    private void log(String event, String details) {
        GptSenderLog.write(this, event, details);
    }

    private void refreshLog() {
        if (status != null) {
            status.setText(GptSenderLog.read(this));
        }
    }

    private Button button(
            LinearLayout parent,
            String label,
            View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setMinHeight(54);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(8, 6, 8, 6);

        parent.addView(b, p);
        b.setOnClickListener(listener);
        return b;
    }

    private void setBusy(boolean value) {
        busy = value;
        if (prepareButton != null) {
            prepareButton.setEnabled(!value);
        }
        if (sendButton != null) {
            sendButton.setEnabled(!value);
        }
    }

    private void runOperation(
            String name,
            Callable<Boolean> operation) {
        GptSenderDiagnostics.log(this, "OPERATION", "runOperation_ENTER");

        if (busy) {
            log("OPERATION_REJECTED",
                    name + ": another operation is running");
            refreshLog();
            return;
        }

        setBusy(true);
        log("OPERATION_QUEUED", name);

        worker.execute(() -> {
            long start = android.os.SystemClock.elapsedRealtime();
            boolean result = false;

            try {
                log("OPERATION_BEGIN", name);
                result = operation.call();

                log("OPERATION_RESULT",
                        name + ": result=" + result);

            } catch (Exception e) {
                log("OPERATION_EXCEPTION",
                        name + ": " + Log.getStackTraceString(e));

            } finally {
                long elapsed =
                        android.os.SystemClock.elapsedRealtime() - start;

                log("OPERATION_END",
                        name + ": duration_ms=" + elapsed
                                + ", result=" + result);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBusy(false);
                    refreshLog();
                });
            }
        });
    }

    @Override
    protected void onCreate(Bundle saved) {
        // GPT_DIAG_ACTIVITY_PATCH_V1
        GptSenderDiagnostics.start(this);
        GptSenderDiagnostics.activityState(this, "onCreate_ENTER");

        super.onCreate();

        log("APP_OPEN", "PMCA 0.2.0 / Build 3");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("GPTSenderTest — PMCA 0.2.0 / Build 3");
        title.setTextColor(Color.BLACK);
        title.setTextSize(20);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText(
                "Действия выполняются в фоновом потоке. "
                + "Отправка — только вручную. "
                + "Клик не подтверждает доставку сообщения."
        );
        hint.setTextColor(Color.DKGRAY);
        hint.setTextSize(14);
        root.addView(hint);

        message = new EditText(this);
        message.setText("Тест GPTSenderTest");
        message.setHint("Текст тестового сообщения");
        message.setMinLines(2);
        message.setMaxLines(4);
        message.setInputType(
                InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );
        root.addView(message,
                new LinearLayout.LayoutParams(-1, -2));

        button(root, "1. Открыть настройки Accessibility", v -> {
            log("ACCESSIBILITY_SETTINGS_OPEN",
                    "User requested settings");
            startActivity(
                    new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            );
        });

        prepareButton = button(
                root,
                "2. Подготовить тест в ChatGPT",
                v -> {
                    String text = message.getText().toString();

                    if (text.trim().isEmpty()) {
                        log("PREPARE_REJECTED", "Empty message");
                        refreshLog();
                        return;
                    }

                    runOperation("PREPARE_CHATGPT_INPUT", () -> {
                        log("PREPARE_REQUEST",
                                "message_length=" + text.length());

                        boolean ok =
                                TesterAccessibilityService
                                        .prepareChatGPTCommand(text);

                        log(ok ? "INPUT_PREPARE_SUCCESS"
                                : "INPUT_PREPARE_FAILED",
                                "result=" + ok);
                        return ok;
                    });
                }
        );

        sendButton = button(
                root,
                "3. Отправить подготовленное сообщение",
                v -> runOperation("MANUAL_SEND", () -> {
                    log("SEND_REQUEST",
                            "Explicit button press");

                    boolean clicked =
                            TesterAccessibilityService.clickSend();

                    log("SEND_CLICK_RESULT",
                            "clicked=" + clicked
                                    + "; delivery not confirmed");

                    log("SEND_CONFIRMATION_PENDING",
                            "Click is not proof of delivery");
                    return clicked;
                })
        );

        button(root, "Обновить журнал", v -> refreshLog());

        status = new TextView(this);
        status.setTextColor(Color.BLACK);
        status.setTextSize(12);
        status.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(status);
        root.addView(scroll,
                new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        refreshLog();
    }

    @Override
    protected void onResume() {
        GptSenderDiagnostics.activityState(this, "onResume_ENTER");
        super.onResume();
        GptSenderDiagnostics.activityState(this, "onResume_EXIT");
        refreshLog();
    }

    @Override
    protected void onDestroy() {
        log("APP_DESTROY", "Activity destroyed");
        // Stop the shared heartbeat monitor so it does not outlive the UI.
        GptSenderDiagnostics.stop(this);
        worker.shutdownNow();
        super.onDestroy();
    }

}
