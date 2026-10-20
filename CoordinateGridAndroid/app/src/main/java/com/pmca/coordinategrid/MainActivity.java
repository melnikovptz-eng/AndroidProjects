package com.pmca.coordinategrid;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(20));
        root.setBackgroundColor(Color.rgb(247, 249, 252));

        TextView title = new TextView(this);
        title.setText("Сетка координат");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(20, 35, 55));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("Прозрачная сетка поверх экрана.\nШаг линий: 50 px · Подписи: каждые 100 px\nСетка не перехватывает нажатия — можно пользоваться ChatGPT.");
        info.setTextSize(16);
        info.setTextColor(Color.DKGRAY);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 0, 0, dp(22));
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        Button permission = new Button(this);
        permission.setText("1. Разрешить показ поверх приложений");
        root.addView(permission, buttonParams());
        permission.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                showStatus("Разрешение уже выдано.");
            }
        });

        Button start = new Button(this);
        start.setText("2. Включить сетку");
        root.addView(start, buttonParams());
        start.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                showStatus("Сначала разреши показ поверх приложений.");
                return;
            }
            Intent i = new Intent(this, GridOverlayService.class);
            i.setAction(GridOverlayService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
            showStatus("Сетка включена. Вернись в ChatGPT.");
        });

        Button stop = new Button(this);
        stop.setText("Выключить сетку");
        root.addView(stop, buttonParams());
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, GridOverlayService.class);
            i.setAction(GridOverlayService.ACTION_STOP);
            startService(i);
            showStatus("Сетка выключена.");
        });

        TextView note = new TextView(this);
        note.setText("\nКоординаты отсчитываются от левого верхнего угла экрана: X вправо, Y вниз. Значения на сетке — экранные пиксели, а не dp. Системные панели могут занимать часть экрана.");
        note.setTextSize(14);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(14), 0, 0);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(56));
        p.setMargins(0, dp(5), 0, dp(7));
        return p;
    }

    private void showStatus(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
