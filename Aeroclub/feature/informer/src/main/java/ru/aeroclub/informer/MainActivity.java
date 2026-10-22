package ru.aeroclub.informer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int NAVY = AeroclubUi.NAVY;
    private static final int MUTED = AeroclubUi.MUTED;
    private static final int GREEN = AeroclubUi.GREEN;
    private static final int REQUEST_EXPORT_MESSAGES = 7101;
    private static final int REQUEST_IMPORT_MESSAGES = 7102;
    private TextView status, clubLabel, flightTitle, flightDateTime, flightPlace, localAnnouncement;
    private Flight selectedCalendarFlight;
    private Calendar displayedCalendarMonth = Calendar.getInstance();
    private LocalMessageFileWatcher messageWatcher;
    private DebugOperation screenOpenOperation;
    private boolean storageWarningShown;
    private final Handler flightSyncHandler = new Handler(Looper.getMainLooper());
    private final Runnable flightSyncPoll = new Runnable() { public void run() { if (isFinishing()) return; if (FlightRepository.syncFromDownload(MainActivity.this)) renderCalendarScreen(); flightSyncHandler.postDelayed(this, 5000L); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        screenOpenOperation = ModuleDebugLog.startOperation(this, "UI-01", "calendar_screen_open", "screen_created");
        getWindow().setStatusBarColor(AeroclubUi.BACKGROUND);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        FlightRepository.ensureInitialized(this);
        FlightReminderScheduler.syncAll(this);
        renderCalendarScreen();
        ensureLegacyDownloadsPermission();
        if (screenOpenOperation != null) {
            screenOpenOperation.finish("success", "calendar_ready");
            screenOpenOperation = null;
        }
    }

    /** Main flow: calendar -> red event day -> event card with four actions. */
    private void renderCalendarScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(AeroclubUi.BACKGROUND);

        TextView title = text("S01  •  АЭРОКЛУБ • КАЛЕНДАРЬ МЕРОПРИЯТИЙ", 16, NAVY, true);
        root.addView(title, lp(-1, -2));

        LinearLayout monthRow = new LinearLayout(this);
        monthRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        monthRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams monthParams = lp(-1, dp(48));
        monthParams.topMargin = dp(12);
        root.addView(monthRow, monthParams);

        Button previous = button("‹");
        Button next = button("›");
        TextView monthLabel = text("", 16, NAVY, true);
        monthLabel.setGravity(android.view.Gravity.CENTER);
        monthRow.addView(previous, new LinearLayout.LayoutParams(dp(52), dp(48)));
        monthRow.addView(monthLabel, new LinearLayout.LayoutParams(0, -1, 1));
        monthRow.addView(next, new LinearLayout.LayoutParams(dp(52), dp(48)));

        Calendar month = (Calendar) displayedCalendarMonth.clone();
        month.set(Calendar.DAY_OF_MONTH, 1);
        monthLabel.setText(new SimpleDateFormat("LLLL yyyy", new Locale("ru")).format(month.getTime()));
        previous.setOnClickListener(v -> { displayedCalendarMonth.add(Calendar.MONTH, -1); renderCalendarScreen(); });
        next.setOnClickListener(v -> { displayedCalendarMonth.add(Calendar.MONTH, 1); renderCalendarScreen(); });

        LinearLayout week = new LinearLayout(this);
        week.setOrientation(LinearLayout.HORIZONTAL);
        String[] weekdays = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (String day : weekdays) {
            TextView cell = text(day, 12, MUTED, true);
            cell.setGravity(android.view.Gravity.CENTER);
            week.addView(cell, new LinearLayout.LayoutParams(0, dp(32), 1));
        }
        LinearLayout.LayoutParams weekParams = lp(-1, dp(32));
        weekParams.topMargin = dp(8);
        root.addView(week, weekParams);

        java.util.Map<String, java.util.List<Flight>> flightsByDate = new java.util.HashMap<>();
        for (Flight flight : FlightRepository.getFlights(this)) {
            if (flight.dateIso == null || flight.dateIso.isEmpty()) continue;
            java.util.List<Flight> sameDate = flightsByDate.get(flight.dateIso);
            if (sameDate == null) { sameDate = new java.util.ArrayList<>(); flightsByDate.put(flight.dateIso, sameDate); }
            sameDate.add(flight);
        }

        LinearLayout calendarGrid = new LinearLayout(this);
        calendarGrid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridParams = lp(-1, -2);
        gridParams.topMargin = dp(2);
        root.addView(calendarGrid, gridParams);
        int offset = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        int cells = ((offset + daysInMonth + 6) / 7) * 7;
        for (int base = 0; base < cells; base += 7) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            calendarGrid.addView(row, lp(-1, dp(44)));
            for (int col = 0; col < 7; col++) {
                int index = base + col;
                int dayNumber = index - offset + 1;
                TextView dayCell = text(dayNumber >= 1 && dayNumber <= daysInMonth ? String.valueOf(dayNumber) : "", 14, AeroclubUi.BODY, false);
                dayCell.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(0, dp(40), 1);
                dayParams.setMargins(dp(2), dp(2), dp(2), dp(2));
                row.addView(dayCell, dayParams);
                if (dayNumber < 1 || dayNumber > daysInMonth) continue;
                Calendar date = (Calendar) month.clone();
                date.set(Calendar.DAY_OF_MONTH, dayNumber);
                String iso = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(date.getTime());
                java.util.List<Flight> dayFlights = flightsByDate.get(iso);
                if (dayFlights != null && !dayFlights.isEmpty()) {
                    dayCell.setTextColor(Color.WHITE);
                    dayCell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    dayCell.setBackground(AeroclubUi.rounded(this, Color.rgb(190, 45, 55), 12, Color.rgb(190, 45, 55)));
                    dayCell.setContentDescription(dayNumber + ", мероприятий: " + dayFlights.size());
                    dayCell.setOnClickListener(v -> showEventCardForDate(iso, dayFlights));
                } else {
                    dayCell.setBackground(AeroclubUi.rounded(this, Color.WHITE, 12, AeroclubUi.BORDER));
                    dayCell.setOnClickListener(v -> android.widget.Toast.makeText(this, "На эту дату мероприятий нет.", android.widget.Toast.LENGTH_SHORT).show());
                }
            }
        }

        TextView hint = text("Красный день — есть мероприятие. Нажмите на него, чтобы открыть карточку.", 12, MUTED, false);
        LinearLayout.LayoutParams hintParams = lp(-1, -2);
        hintParams.topMargin = dp(8);
        root.addView(hint, hintParams);

        Button admin = button("Панель администратора");
        LinearLayout.LayoutParams adminParams = lp(-1, dp(52));
        adminParams.topMargin = dp(16);
        root.addView(admin, adminParams);
        admin.setOnClickListener(v -> runTimedAction("ADM-01", "open_admin", () ->
            startActivity(new Intent().setComponent(new android.content.ComponentName(getPackageName(), "ru.aeroclub.informer.AdminActivity")))));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(root);
        setContentView(scroll);
        clubLabel = title;
        flightTitle = title;
    }

    private void showEventCardForDate(String iso, java.util.List<Flight> flights) {
        if (flights == null || flights.isEmpty()) return;
        if (flights.size() == 1) { showEventCard(flights.get(0)); return; }
        String[] labels = new String[flights.size()];
        for (int i = 0; i < flights.size(); i++) labels[i] = FlightPresentation.title(flights.get(i)) + " • " + flights.get(i).time;
        new AlertDialog.Builder(this).setTitle("Мероприятия " + iso)
            .setItems(labels, (dialog, which) -> showEventCard(flights.get(which)))
            .setNegativeButton("Закрыть", null).show();
    }

    private void showEventCard(Flight flight) {
        if (flight == null) return;
        selectedCalendarFlight = flight;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(12), dp(18), dp(8));
        TextView eventTitle = text(FlightPresentation.title(flight), 19, NAVY, true);
        card.addView(eventTitle, lp(-1, -2));
        TextView dateTime = text(flight.date + " • " + flight.time, 14, AeroclubUi.BODY, false);
        LinearLayout.LayoutParams dateParams = lp(-1, -2); dateParams.topMargin = dp(8); card.addView(dateTime, dateParams);
        TextView place = text("Место встречи: " + (flight.place == null || flight.place.trim().isEmpty() ? "не указано" : flight.place), 14, MUTED, false);
        LinearLayout.LayoutParams placeParams = lp(-1, -2); placeParams.topMargin = dp(6); card.addView(place, placeParams);
        TextView response = text("Выберите действие", 12, MUTED, false);
        LinearLayout.LayoutParams responseParams = lp(-1, -2); responseParams.topMargin = dp(8); card.addView(response, responseParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row1 = new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
        Button going = button("Я поеду"), notGoing = button("Я не поеду");
        Button route = button("Маршрут"), telegram = button("Telegram");
        row1.addView(going, weightedButtonParams(false)); row1.addView(notGoing, weightedButtonParams(true));
        row2.addView(route, weightedButtonParams(false)); row2.addView(telegram, weightedButtonParams(true));
        actions.addView(row1, lp(-1, dp(52)));
        LinearLayout.LayoutParams row2Params = lp(-1, dp(52)); row2Params.topMargin = dp(4); actions.addView(row2, row2Params);
        LinearLayout.LayoutParams actionParams = lp(-1, -2); actionParams.topMargin = dp(12); card.addView(actions, actionParams);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(card).setNegativeButton("Закрыть", null).create();
        going.setOnClickListener(v -> { dialog.dismiss(); runTimedAction("ATT-01", "event_card_going", () -> chooseParticipantAndSetStatus(AttendanceRepository.STATUS_GOING, flight)); });
        notGoing.setOnClickListener(v -> { dialog.dismiss(); runTimedAction("ATT-01", "event_card_not_going", () -> chooseParticipantAndSetStatus(AttendanceRepository.STATUS_NOT_GOING, flight)); });
        route.setOnClickListener(v -> runTimedAction("MAP-01", "event_card_route", () -> FlightActionLauncher.openRoute(this, flight)));
        telegram.setOnClickListener(v -> runTimedAction("SHR-01", "event_card_telegram", () -> FlightActionLauncher.shareFlight(this, flight)));
        dialog.show();
    }

    private void ensureLegacyDownloadsPermission(){if(Build.VERSION.SDK_INT<29&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE},7103);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==7103){if(grantResults.length>1&&grantResults[1]==android.content.pm.PackageManager.PERMISSION_GRANTED){FlightRepository.ensureInitialized(this);FlightDownloadExchange.publish(this,LocalFilePreferences.get(this),"flights_json","active_flight_id");refreshFlightSummary();}else{android.widget.Toast.makeText(this,"Без доступа к Download автоматический файл полётов недоступен.",android.widget.Toast.LENGTH_LONG).show();ModuleDebugLog.log(this,"FLIGHT-FILE","legacy_permission","denied","downloads_unavailable");}}}
    private void refreshFlightSummary(){ FlightReminderScheduler.syncAll(this); renderCalendarScreen(); }

    private void launchMessageExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "aeroclub-messages.json");
        try { startActivityForResult(intent, REQUEST_EXPORT_MESSAGES); }
        catch (RuntimeException error) {
            android.widget.Toast.makeText(this, "Не удалось открыть выбор места сохранения файла", android.widget.Toast.LENGTH_LONG).show();
            ModuleDebugLog.log(this, "MSG-02", "file_export_picker", "error", error.getClass().getSimpleName());
        }
    }

    private void launchMessageImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        try { startActivityForResult(intent, REQUEST_IMPORT_MESSAGES); }
        catch (RuntimeException error) {
            android.widget.Toast.makeText(this, "Не удалось открыть выбор файла", android.widget.Toast.LENGTH_LONG).show();
            ModuleDebugLog.log(this, "MSG-02", "file_import_picker", "error", error.getClass().getSimpleName());
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == REQUEST_EXPORT_MESSAGES || requestCode == REQUEST_IMPORT_MESSAGES) {
                ModuleDebugLog.log(this, "MSG-02", "file_exchange", "cancelled", "user_cancelled_or_no_uri");
            }
            return;
        }
        android.net.Uri uri = data.getData();
        LocalFileMessageTransport transport = new LocalFileMessageTransport();
        try {
            if (requestCode == REQUEST_EXPORT_MESSAGES) {
                int count;
                try (java.io.OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new java.io.IOException("cannot_open_output");
                    count = transport.exportTo(this, output);
                }
                android.widget.Toast.makeText(this, "Экспортировано записей: " + count, android.widget.Toast.LENGTH_LONG).show();
            } else if (requestCode == REQUEST_IMPORT_MESSAGES) {
                int added;
                try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new java.io.IOException("cannot_open_input");
                    added = transport.importFrom(this, input);
                }
                android.widget.Toast.makeText(this, "Импортировано новых записей: " + added, android.widget.Toast.LENGTH_LONG).show();
                if (localAnnouncement != null) refreshAnnouncementWatcher();
            }
        } catch (Exception error) {
            ModuleDebugLog.log(this, "MSG-02", "file_exchange", "error", error.getClass().getSimpleName());
            android.widget.Toast.makeText(this, "Файловый обмен не выполнен: " + error.getClass().getSimpleName(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void refreshAnnouncementWatcher() {
        if (messageWatcher != null) messageWatcher.stop();
        messageWatcher = new LocalMessageFileWatcher(
            MessageTransportProvider.get(), LocalFileMessageTransport.ANNOUNCEMENT_CHANNEL);
        messageWatcher.start(this, new LocalMessageFileWatcher.Listener() {
            @Override public void onMessagesChanged(java.util.List<LocalTransportMessage> messages) {
                if (localAnnouncement == null) return;
                LocalTransportMessage newest = null;
                for (LocalTransportMessage item : messages) {
                    if (newest == null || item.createdAt > newest.createdAt) newest = item;
                }
                localAnnouncement.setText(newest == null ? "Объявлений в локальном файле пока нет"
                    : "Локальное объявление (только этот телефон):\\n" + newest.body);
            }
            @Override public void onReadError() {
                if (localAnnouncement != null) localAnnouncement.setText("Не удалось прочитать локальный файл объявлений");
            }
        });
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

    private void chooseParticipantAndSetStatus(String value) { chooseParticipantAndSetStatus(value, FlightRepository.getActive(this)); }

    private void chooseParticipantAndSetStatus(String value, Flight targetFlight) {
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
                String flightId = targetFlight == null ? null : targetFlight.id;
                if (flightId == null) {
                    dialog.dismiss();
                    android.widget.Toast.makeText(this, "Мероприятие не выбрано", android.widget.Toast.LENGTH_SHORT).show();
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
        LocalFilePreferences storage = LocalFilePreferences.get(this);
        if (!storage.isStorageHealthy() && !storageWarningShown) {
            storageWarningShown = true;
            android.widget.Toast.makeText(this, storage.getStorageStatus(), android.widget.Toast.LENGTH_LONG).show();
        }
        ModuleDebugLog.log(this, "UI-01", "informer_onResume", "started", "screen_foreground");
        flightSyncHandler.removeCallbacks(flightSyncPoll);
        if (FlightRepository.syncFromDownload(this)) renderCalendarScreen();
        flightSyncHandler.postDelayed(flightSyncPoll, 5000L);
        if (flightTitle == null) return;
        FlightReminderScheduler.syncAll(this);
        refreshFlightSummary();
        if (messageWatcher == null) {
            messageWatcher = new LocalMessageFileWatcher(
                MessageTransportProvider.get(), LocalFileMessageTransport.ANNOUNCEMENT_CHANNEL);
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
                    localAnnouncement.setText("Локальное объявление (только этот телефон):\n" + newest.body);
                }
            }
            @Override public void onReadError() {
                ModuleDebugLog.log(MainActivity.this, "MSG-02", "announcement_refresh", "error", "read_failed");
                if (localAnnouncement != null) localAnnouncement.setText("Не удалось прочитать локальный файл объявлений");
            }
        });
    }

    @Override protected void onPause() {
        flightSyncHandler.removeCallbacks(flightSyncPoll);
        LocalFilePreferences.get(this).flushPendingWrites(500L);
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
