package ru.aeroclub.informer;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(23, 43, 77);
    private static final int MUTED = Color.rgb(82, 100, 119);
    private static final int GREEN = Color.rgb(19, 117, 71);
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(245, 247, 250));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(Color.rgb(245, 247, 250));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(18));
        cardBg.setStroke(dp(1), Color.rgb(220, 228, 236));
        card.setBackground(cardBg);
        card.addView(text("АЭРОКЛУБ  •  БЛИЖАЙШИЙ ВЫЛЕТ", 11, MUTED, true));
        LinearLayout.LayoutParams cardParams = lp(-1, -2);
        cardParams.topMargin = dp(8);
        root.addView(card, cardParams);

        FlightRepository.ensureInitialized(this);
        Flight activeFlight = FlightRepository.getActive(this);
        card.addView(text(activeFlight == null ? "Ближайший вылет" : activeFlight.title, 18, NAVY, true));
        addLine(card, activeFlight == null ? "Дата и время уточняются" :
            activeFlight.date + "  •  " + activeFlight.time, 14, Color.rgb(52, 70, 90), 2);
        addLine(card, "Место встречи: " + (activeFlight == null ? "не указано" : activeFlight.place), 13, MUTED, 2);
        status = text(AttendanceRepository.getResponse(this, FlightRepository.getActiveId(this)), 12, GREEN, false);
        LinearLayout.LayoutParams statusParams = lp(-1, -2);
        statusParams.topMargin = dp(5);
        card.addView(status, statusParams);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = lp(-1, -2);
        rowParams.topMargin = dp(5);
        card.addView(row1, rowParams);
        Button going = button("Я еду"), no = button("Я не еду");
        row1.addView(going, weightedButtonParams(false));
        row1.addView(no, weightedButtonParams(true));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Params = lp(-1, -2);
        row2Params.topMargin = dp(2);
        card.addView(row2, row2Params);
        Button route = button("Маршрут"), telegram = button("Telegram");
        row2.addView(route, weightedButtonParams(false));
        row2.addView(telegram, weightedButtonParams(true));

        going.setOnClickListener(v -> setStatus("Вы подтвердили участие"));
        no.setOnClickListener(v -> setStatus("Вы не участвуете"));
        route.setOnClickListener(v -> {
            Flight active = FlightRepository.getActive(this);
            String place = active == null ? "" : active.place;
            if (place.trim().isEmpty()) {
                android.widget.Toast.makeText(this,"Место встречи не указано",android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            Intent map = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)));
            try {
                startActivity(map);
                EventLogRepository.record(this,"MAP-01","open_route","intent_started",active.id);
            } catch (Exception e) {
                android.widget.Toast.makeText(this,"Не найдено приложение для карт",android.widget.Toast.LENGTH_SHORT).show();
                EventLogRepository.record(this,"MAP-01","open_route","no_maps_app",active.id);
            }
        });
        telegram.setOnClickListener(v -> {
            Flight active = FlightRepository.getActive(this);
            if (active == null) {
                android.widget.Toast.makeText(this,"Нет данных о вылете",android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            String body = "Аэроклуб — " + active.title + ": " + active.date + ", " + active.time +
                ". Место встречи: " + active.place + ".";
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, body);
            try {
                startActivity(Intent.createChooser(share, "Отправить информацию о полёте"));
                MessageRepository.recordShareChooserOpened(this,active.id,body);
                EventLogRepository.record(this,"SHR-01","share_chooser_opened","delivery_unconfirmed",active.id);
            } catch (Exception e) {
                android.widget.Toast.makeText(this,"Нет приложения для отправки",android.widget.Toast.LENGTH_SHORT).show();
                EventLogRepository.record(this,"SHR-01","share_chooser_opened","no_share_app",active.id);
            }
        });

        Button admin = button("Панель администратора");
        LinearLayout.LayoutParams adminParams = lp(-1, dp(48));
        adminParams.topMargin = dp(12);
        root.addView(admin, adminParams);
        admin.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));

        setContentView(root);
    }

    private void setStatus(String value) {
        FlightRepository.ensureInitialized(this);
        String flightId = FlightRepository.getActiveId(this);
        AttendanceRepository.setResponse(this, flightId, value);
        EventLogRepository.record(this,"ATT-01","response_update","success",flightId);
        status.setText(value);
        // Keep the home-screen widget synchronized with the preview screen.
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        int[] ids = manager.getAppWidgetIds(new ComponentName(this, FlightWidgetProvider.class));
        if (ids.length > 0) {
            Intent update = new Intent(this, FlightWidgetProvider.class);
            update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            sendBroadcast(update);
        }
    }

    private void addLine(LinearLayout box, String value, int size, int color, int top) {
        TextView t = text(value, size, color, false);
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(top);
        box.addView(t, p);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));
        return b;
    }

    private LinearLayout.LayoutParams weightedButtonParams(boolean spaced) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
        if (spaced) p.leftMargin = dp(6);
        return p;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
