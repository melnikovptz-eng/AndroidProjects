package ru.aeroclub.informer;

import android.app.Activity;
import android.app.AlertDialog;
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
        FlightReminderScheduler.ensureChannel(this);
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
        FlightReminderScheduler.syncAll(this);
        Flight activeFlight = FlightRepository.getActive(this);
        card.addView(text(activeFlight == null ? "Ближайший вылет" : activeFlight.title, 18, NAVY, true));
        addLine(card, activeFlight == null ? "Дата и время уточняются" :
            activeFlight.date + "  •  " + activeFlight.time, 14, Color.rgb(52, 70, 90), 2);
        addLine(card, "Место встречи: " + (activeFlight == null ? "не указано" : activeFlight.place), 13, MUTED, 2);
        status = text("Выберите участника, чтобы указать ответ", 12, GREEN, false);
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

        going.setOnClickListener(v -> chooseParticipantAndSetStatus(AttendanceRepository.STATUS_GOING));
        no.setOnClickListener(v -> chooseParticipantAndSetStatus(AttendanceRepository.STATUS_NOT_GOING));
        route.setOnClickListener(v -> FlightActionLauncher.openRoute(this, FlightRepository.getActive(this)));
        telegram.setOnClickListener(v -> FlightActionLauncher.shareFlight(this, FlightRepository.getActive(this)));


        Button admin = button("Панель администратора");
        LinearLayout.LayoutParams adminParams = lp(-1, dp(48));
        adminParams.topMargin = dp(12);
        root.addView(admin, adminParams);
        admin.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));

        setContentView(root);
        refreshStatusLabel();
        String requestedStatus = getIntent() == null ? null : getIntent().getStringExtra("attendance_action");
        if (AttendanceRepository.STATUS_GOING.equals(requestedStatus) ||
            AttendanceRepository.STATUS_NOT_GOING.equals(requestedStatus)) {
            chooseParticipantAndSetStatus(requestedStatus);
            getIntent().removeExtra("attendance_action");
        }
    }

    private void chooseParticipantAndSetStatus(String value) {
        java.util.List<Participant> participants = ParticipantRepository.getAll(this);
        if (participants.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Нет участников")
                .setMessage("Сначала добавьте участника в панели администратора. Ответ не сохранён.")
                .setPositiveButton("Открыть панель", (dialog, which) ->
                    startActivity(new Intent(this, AdminActivity.class)))
                .setNegativeButton("Закрыть", null)
                .show();
            EventLogRepository.record(this, "ATT-01", "response_update", "no_participants", FlightRepository.getActiveId(this));
            return;
        }
        String[] names = new String[participants.size()];
        for (int i = 0; i < participants.size(); i++) names[i] = participants.get(i).displayName;
        String selectedId = AttendanceRepository.getSelectedParticipantId(this);
        int checked = -1;
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).id.equals(selectedId)) { checked = i; break; }
        }
        new AlertDialog.Builder(this)
            .setTitle("Для кого сохранить ответ?")
            .setSingleChoiceItems(names, checked, (dialog, which) -> {
                Participant participant = participants.get(which);
                String flightId = FlightRepository.getActiveId(this);
                if (flightId == null || FlightRepository.getActive(this) == null) {
                    dialog.dismiss();
                    android.widget.Toast.makeText(this, "Нет активного вылета", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                AttendanceRepository.setSelectedParticipantId(this, participant.id);
                AttendanceRepository.setResponse(this, participant.id, flightId, value);
                EventLogRepository.record(this, "ATT-01", "response_update", "success", participant.id);
                dialog.dismiss();
                refreshStatusLabel();
                WidgetRefreshManager.refresh(this, FlightWidgetProvider.class);
            })
            .setNegativeButton("Отмена", (dialog, which) ->
                EventLogRepository.record(this, "ATT-01", "response_update", "cancelled", FlightRepository.getActiveId(this)))
            .show();
    }

    private void refreshStatusLabel() {
        if (status == null) return;
        String flightId = FlightRepository.getActiveId(this);
        String selectedId = AttendanceRepository.getSelectedParticipantId(this);
        Participant selected = null;
        for (Participant p : ParticipantRepository.getAll(this)) {
            if (p.id.equals(selectedId)) { selected = p; break; }
        }
        if (selected == null) {
            status.setText("Выберите участника, чтобы указать ответ");
            return;
        }
        status.setText(selected.displayName + ": " + AttendanceRepository.getResponse(this, selected.id, flightId));
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
