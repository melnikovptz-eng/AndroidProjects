package com.pmca.bridge.overlay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.pmca.bridge.core.PmcaController;
import com.pmca.bridge.logging.PmcaLogger;

public final class ClipboardPasteActivity extends Activity {

    private boolean handled = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && !handled) {
            handled = true;

            // Даём Android время активировать окно и предоставить
            // доступ к системному буферу обмена.
            handler.postDelayed(this::processClipboard, 200);
        }
    }

    private void processClipboard() {
        try {
            // Используем штатный механизм чтения PMCA.
            String text = new PmcaController(this).readClipboard();

            if (text == null || text.trim().isEmpty()) {
                setState(0);
                Toast.makeText(
                        this,
                        "Буфер обмена пуст",
                        Toast.LENGTH_SHORT
                ).show();
                PmcaLogger.log(
                        "Буфер PMCA пуст после получения фокуса"
                );
                return;
            }

            getSharedPreferences("pmca_results", MODE_PRIVATE)
                    .edit()
                    .putString("compact_command", text)
                    .putInt("compact_state", 1)
                    .apply();

            setState(1);
            PmcaLogger.log(
                    "Текст буфера прочитан после получения фокуса"
            );

            boolean accepted;

            try {
                accepted = new PmcaController(this)
                        .runNativeCommand(text);
            } catch (Exception e) {
                accepted = false;
                PmcaLogger.log(
                        "Ошибка передачи команды в Termux: "
                                + e.getMessage()
                );
            }

            if (accepted) {
                setState(2);
                Toast.makeText(
                        this,
                        "Команда передана в Termux",
                        Toast.LENGTH_SHORT
                ).show();
                PmcaLogger.log(
                        "Команда из плавающей панели передана в Termux"
                );
            } else {
                setState(1);
                Toast.makeText(
                        this,
                        "Текст прочитан, но Termux не принял команду",
                        Toast.LENGTH_LONG
                ).show();
                PmcaLogger.log(
                        "Termux не принял команду из ClipboardPasteActivity"
                );
            }

        } catch (Exception e) {
            setState(0);
            Toast.makeText(
                    this,
                    "Ошибка чтения буфера",
                    Toast.LENGTH_SHORT
            ).show();
            PmcaLogger.log(
                    "Ошибка ClipboardPasteActivity: " + e.getMessage()
            );
        } finally {
            handler.removeCallbacksAndMessages(null);
            finish();
        }
    }

    private void setState(int state) {
        getSharedPreferences("pmca_results", MODE_PRIVATE)
                .edit()
                .putInt("compact_state", state)
                .apply();

        Intent update = new Intent(
                FloatingPanelService.ACTION_STATE_UPDATE
        );
        update.setPackage(getPackageName());
        update.putExtra("state", state);
        sendBroadcast(update);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
