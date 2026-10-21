package ru.aeroclub.informer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int NAVY = AeroclubUi.NAVY;
    private static final int MUTED = AeroclubUi.MUTED;
    private static final int GREEN = AeroclubUi.GREEN;
    private TextView status, clubLabel, flightTitle, flightDateTime, flightPlace, localAnnouncement;
    private LocalMessageFileWatcher messageWatcher;
    private DebugOperation screenOpenOperation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        GitHubDebugUploader.registerNetworkRetry(this);
        screenOpenOperation = ModuleDebugLog.startOperation(this, "UI-01", "informer_screen_open", "screen_created");
        getWindow().setStatusBarColor(AeroclubUi.BACKGROUND);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(AeroclubUi.BACKGROUND);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable cardBg = AeroclubUi.rounded(this, Color.WHITE, 18, AeroclubUi.BORDER);
        card.setBackground(cardBg);
        clubLabel = text(ClubSettingsActions.get(this).clubName.toUpperCase(java.util.Locale.forLanguageTag("ru")) + "  •  БЛИЖАЙШИЙ ВЫЛЕТ", 11, MUTED, true);
        card.addView(clubLabel);
        LinearLayout.LayoutParams cardParams = lp(-1, -2);
        cardParams.topMargin = dp(8);
        root.addView(card, cardParams);

        FlightRepository.ensureInitialized(this);
        FlightReminderScheduler.syncAll(this);
        Flight activeFlight = FlightRepository.getActive(this);
        flightTitle = text(FlightPresentation.title(activeFlight), 18, NAVY, true);
        card.addView(flightTitle);
        flightDateTime = addLine(card, activeFlight == null ? "Дата и время уточняются" :
            activeFlight.date + "  •  " + activeFlight.time, 14, AeroclubUi.BODY, 2);
        flightPlace = addLine(card, "Место встречи: " + (activeFlight == null ? "не указано" : activeFlight.place), 13, MUTED, 2);
        localAnnouncement = text("Объявления: проверка локального файла…", 12, MUTED, false);
        LinearLayout.LayoutParams announcementParams = lp(-1, -2);
        announcementParams.topMargin = dp(8);
        card.addView(localAnnouncement, announcementParams);
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

        going.setOnClickListener(v -> runTimedAction("ATT-01", "open_going_response", () -> chooseParticipantAndSetStatus(AttendanceRepository.STATUS_GOING)));
        no.setOnClickListener(v -> runTimedAction("ATT-01", "open_not_going_response", () -> chooseParticipantAndSetStatus(AttendanceRepository.STATUS_NOT_GOING)));
        route.setOnClickListener(v -> runTimedAction("MAP-01", "open_route", () -> FlightActionLauncher.openRoute(this, FlightRepository.getActive(this))));
        telegram.setOnClickListener(v -> runTimedAction("SHR-01", "open_share", () -> FlightActionLauncher.shareFlight(this, FlightRepository.getActive(this))));


        Button admin = button("Панель администратора");
        LinearLayout.LayoutParams adminParams = lp(-1, dp(48));
        adminParams.topMargin = dp(12);
        root.addView(admin, adminParams);
        admin.setOnClickListener(v -> runTimedAction("ADM-01", "open_admin", () ->
            startActivity(new Intent().setComponent(new android.content.ComponentName(getPackageName(), "ru.aeroclub.informer.AdminActivity")))));

        setContentView(root);
        refreshStatusLabel();
        handleAttendanceAction(getIntent());
        if (screenOpenOperation != null) { screenOpenOperation.finish("success", "screen_ready"); screenOpenOperation = null; }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        ModuleDebugLog.log(this, "UI-01", "informer_onNewIntent", "received", "intent_received");
        setIntent(intent);
        handleAttendanceAction(intent);
    }

    private void handleAttendanceAction(Intent intent) {
        if (intent == null) return;
        String requestedStatus = intent.getStringExtra("attendance_action");
        if (AttendanceRepository.STATUS_GOING.equals(requestedStatus) ||
            AttendanceRepository.STATUS_NOT_GOING.equals(requestedStatus)) {
            intent.removeExtra("attendance_action");
            chooseParticipantAndSetStatus(requestedStatus);
        }
    }

    private void chooseParticipantAndSetStatus(String value) {
        java.util.List<Participant> participants = ParticipantRepository.getAll(this);
        if (participants.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Нет участников")
                .setMessage("Сначала добавьте участника в панели администратора. Ответ не сохранён.")
                .setPositiveButton("Открыть панель", (dialog, which) ->
                    startActivity(new Intent().setComponent(new android.content.ComponentName(getPackageName(), "ru.aeroclub.informer.AdminActivity"))))
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
                DebugOperation saveOperation = ModuleDebugLog.startOperation(this, "ATT-01", "save_attendance_response", "flight_response");
                boolean saved;
                try {
                    saved = ParticipantAdminActions.setResponse(this, participant.id, flightId, value);
                    saveOperation.finish(saved ? "success" : "error", saved ? "saved" : "save_failed");
                } catch (RuntimeException error) {
                    saveOperation.finish("error", error.getClass().getSimpleName());
                    throw error;
                }
                if (!saved) {
                    dialog.dismiss();
                    android.widget.Toast.makeText(this, "Не удалось сохранить ответ.", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                refreshStatusLabel();
                WidgetRefreshManager.refresh(this, "ru.aeroclub.informer.FlightWidgetProvider");
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

    private void runTimedAction(String module, String action, Runnable body) {
        DebugOperation operation = ModuleDebugLog.startOperation(this, module, action, "user_action");
        try {
            body.run();
            operation.finish("dispatched", "handler_returned; outcome_recorded_by_action");
        } catch (RuntimeException error) {
            operation.finish("error", error.getClass().getSimpleName());
            android.widget.Toast.makeText(this, "Не удалось выполнить действие.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private TextView addLine(LinearLayout box, String value, int size, int color, int top) {
        TextView t = text(value, size, color, false);
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(top);
        box.addView(t, p);
        return t;
    }

    @Override protected void onResume() {
        super.onResume();
        ModuleDebugLog.log(this, "UI-01", "informer_onResume", "started", "screen_foreground");
        if (flightTitle == null) return;
        FlightReminderScheduler.syncAll(this);
        clubLabel.setText(ClubSettingsActions.get(this).clubName.toUpperCase(java.util.Locale.forLanguageTag("ru")) + "  •  БЛИЖАЙШИЙ ВЫЛЕТ");
        Flight active = FlightRepository.getActive(this);
        flightTitle.setText(FlightPresentation.title(active));
        flightDateTime.setText(active == null ? "Дата и время уточняются" : active.date + "  •  " + active.time);
        flightPlace.setText("Место встречи: " + (active == null ? "не указано" : active.place));
        refreshStatusLabel();
        if (messageWatcher == null) {
            messageWatcher = new LocalMessageFileWatcher(
                new LocalFileMessageTransport(), LocalFileMessageTransport.ANNOUNCEMENT_CHANNEL);
        }
        messageWatcher.start(this, new LocalMessageFileWatcher.Listener() {
            @Override public void onMessagesChanged(java.util.List<LocalTransportMessage> messages) {
                ModuleDebugLog.log(MainActivity.this, "MSG-02", "announcement_refresh", "success", "count=" + messages.size());
                if (localAnnouncement == null) return;
                LocalTransportMessage newest = null;
                for (LocalTransportMessage item : messages) {
                    if (newest == null || item.createdAt > newest.createdAt) newest = item;
                }
                if (newest == null) {
                    localAnnouncement.setText("Объявлений в локальном файле пока нет");
                } else {
                    localAnnouncement.setText("Локальное объявление (только этот телефон):\\n" + newest.body);
                }
            }
            @Override public void onReadError() {
                ModuleDebugLog.log(MainActivity.this, "MSG-02", "announcement_refresh", "error", "read_failed");
                if (localAnnouncement != null) localAnnouncement.setText("Не удалось прочитать локальный файл объявлений");
            }
        });
    }

    @Override protected void onPause() {
        ModuleDebugLog.log(this, "UI-01", "informer_onPause", "stopped", "screen_background");
        if (messageWatcher != null) messageWatcher.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        ModuleDebugLog.log(this, "UI-01", "informer_onDestroy", "stopped", "screen_destroyed");
        if (messageWatcher != null) {
            messageWatcher.close();
            messageWatcher = null;
        }
        super.onDestroy();
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
