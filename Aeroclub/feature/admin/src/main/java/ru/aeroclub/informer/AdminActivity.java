package ru.aeroclub.informer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Local prototype of the Aeroclub administrator workspace.
 * Data is intentionally stored on-device until a real club API is configured.
 */
public class AdminActivity extends Activity {
    private static final int BG = AeroclubUi.BACKGROUND;
    private static final int NAVY = AeroclubUi.NAVY;
    private static final int MUTED = AeroclubUi.MUTED;
    private static final int BLUE = AeroclubUi.BLUE;
    private static final int GREEN = AeroclubUi.GREEN;
    private LinearLayout root, content;
    private String section = "Обзор";
    private final java.util.ArrayDeque<String> navigationHistory = new java.util.ArrayDeque<>();
    private final Calendar flightDate = Calendar.getInstance();
    private String draftTitle = "Полёт выходного дня";
    private String draftPlace = "Аэродром «Северный»";
    private String editingFlightId = null;
    private boolean storageWarningShown;
    private boolean refreshOnResume = false;
    private DebugOperation screenOpenOperation;
    private Calendar displayedMonth = Calendar.getInstance();
    private boolean calendarMonthInitialized;
    private String selectedMessageFlightId;
    private String recipientMode = "ALL";
    private String recipientId = "all";
    private String recipientLabel = "Всем";
    private Button recipientButton;
    private final Handler flightSyncHandler = new Handler(Looper.getMainLooper());
    private final Runnable flightSyncPoll = new Runnable() {
        @Override public void run() {
            if (isFinishing()) return;
            if (FlightRepository.syncFromDownload(AdminActivity.this)) {  render(); }
            flightSyncHandler.postDelayed(this, 5000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        screenOpenOperation = ModuleDebugLog.startOperation(this, "ADM-01", "admin_screen_open", "admin_screen_created");
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(12), dp(16), dp(8));
        FlightRepository.ensureInitialized(this);
        ParticipantRepository.ensureInitialized(this);
        FlightReminderScheduler.syncAll(this);
        loadDraftFromActiveFlight();
        setContentView(root);
        render();
        if (screenOpenOperation != null) { screenOpenOperation.finish("success", "screen_ready"); screenOpenOperation = null; }
    }

    private void openNotificationSettings() {
        refreshOnResume = true;
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "Откройте настройки приложения вручную", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1002) {
            refreshOnResume = false;
            FlightReminderScheduler.ensureChannel(this);
            FlightReminderScheduler.syncAll(this);
            render();
        }
    }

    @Override protected void onPause() {
        flightSyncHandler.removeCallbacks(flightSyncPoll);
        LocalFilePreferences.get(this).flushPendingWrites(500L);
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        flightSyncHandler.removeCallbacks(flightSyncPoll);
        if (FlightRepository.syncFromDownload(this) && root != null) {  render(); }
        flightSyncHandler.postDelayed(flightSyncPoll, 5000L);
        LocalFilePreferences storage = LocalFilePreferences.get(this);
        if (!storage.isStorageHealthy() && !storageWarningShown) {
            storageWarningShown = true;
            Toast.makeText(this, storage.getStorageStatus(), Toast.LENGTH_LONG).show();
        }
        if (refreshOnResume && root != null) {
            refreshOnResume = false;
            FlightReminderScheduler.syncAll(this);
            render();
        }
    }

    private void navigateTo(String target) {
        if (target == null || target.equals(section)) return;
        navigationHistory.addLast(section);
        section = target;
        render();
    }

    private void goBack() {
        if (!navigationHistory.isEmpty()) section = navigationHistory.removeLast();
        else section = "Обзор";
        render();
    }

    private void goOverview() {
        navigationHistory.clear();
        section = "Обзор";
        render();
    }

    private void render() {
        ModuleDebugLog.log(this, "UI", "screen_transition", "success", "screen=" + section);
        root.removeAllViews();
        TextView eyebrow = text("АЭРОКЛУБ  •  " + screenIdForSection(section) + "  •  УПРАВЛЕНИЕ", 11, MUTED, true);
        root.addView(eyebrow);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = text(section, 25, NAVY, true);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button back = smallButton(section.equals("Обзор") ? "К информеру" : "← Назад");
        back.setOnClickListener(v -> {
            if (!"Обзор".equals(section)) goBack();
            else finish();
        });
        heading.addView(back);
        LinearLayout.LayoutParams hp = lp(-1, -2); hp.topMargin = dp(6);
        root.addView(heading, hp);

        TextView demoWarning = text("ДЕМО-РЕЖИМ. Данные хранятся только на этом устройстве; серверная авторизация и общая база не подключены. Не используйте конфиденциальные данные.", 11, Color.rgb(120, 80, 20), false);
        demoWarning.setPadding(dp(10), dp(8), dp(10), dp(8));
        demoWarning.setBackground(AeroclubUi.rounded(this, Color.rgb(255, 248, 225), 10, Color.rgb(240, 220, 170)));
        LinearLayout.LayoutParams warningParams = lp(-1, -2);
        warningParams.topMargin = dp(8);
        root.addView(demoWarning, warningParams);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        String[] items = {"Обзор", "Вылеты", "Участники", "Сообщения"};
        for (String item : items) {
            Button b = smallButton(item);
            b.setTextSize(11);
            b.setTextColor(section.equals(item) ? Color.WHITE : NAVY);
            b.setBackground(rippleBackground(section.equals(item) ? BLUE : Color.WHITE, 12, Color.rgb(220,228,236)));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(42), 1);
            np.setMargins(0, dp(10), dp(4), dp(8));
            nav.addView(b, np);
            b.setOnClickListener(v -> navigateTo(item));
        }
        root.addView(nav);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.setFillViewport(true);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        switch (section) {
            case "Вылеты": showFlights(); break;
            case "Участники": showParticipants(); break;
            case "Сообщения": showMessages(); break;
            case "Календарь": showCalendar(); break;
            case "Настройки": showSettings(); break;
            case "Уведомления": showNotifications(); break;
            default: showOverview();
        }
        if (!section.equals("Обзор")) {
            Button overview = smallButton("Обзор");
            overview.setOnClickListener(v -> goOverview());
            root.addView(overview, lp(-1, dp(44)));
        }
    }

    private void showOverview() {
        Flight active = FlightRepository.getActive(this);
        String flightSummary = active == null ? "Нет выбранного вылета" :
            FlightPresentation.title(active) + "\n" + active.date + " • " + active.time + "\nМесто встречи: " + active.place;
        card("Ближайший вылет", flightSummary,
             "Открыть вылеты", () -> navigateTo("Вылеты"));
        int total = localParticipants().size();
        int going = countLocalParticipantStatus(AttendanceRepository.STATUS_GOING);
        int noReply = countLocalParticipantStatus(AttendanceRepository.STATUS_PENDING);
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stat(stats, String.valueOf(total), "локальных участников");
        stat(stats, String.valueOf(going), "едут");
        stat(stats, String.valueOf(noReply), "без ответа");
        content.addView(stats);
        card("Участники", "Показываются только записи, сохранённые на этом устройстве.",
             "Управлять участниками", () -> navigateTo("Участники"));
        card("Сообщения", "Подготовка текста; доставка и массовая рассылка не подключены.",
             "Открыть сообщения", () -> navigateTo("Сообщения"));
        card("Календарь", "Даты локальных вылетов.",
             "Открыть календарь", () -> navigateTo("Календарь"));
        card("Сервис", "Настройки клуба и состояние уведомлений.",
             "Настройки", () -> navigateTo("Настройки"));
        Button notifications = actionButton("Уведомления", false);
        notifications.setOnClickListener(v -> navigateTo("Уведомления"));
        content.addView(notifications, lp(-1, dp(48)));
        note("Локальный прототип. Эти числа не отражают участников клуба на других устройствах.");
    }

    private java.util.List<Participant> localParticipants() {
        return ParticipantRepository.getAll(this);
    }

    private String participantStatus(Participant participant) {
        return AttendanceRepository.getResponse(this, participant.id, FlightRepository.getActiveId(this));
    }

    private int countLocalParticipantStatus(String status) {
        int count = 0;
        for (Participant participant : localParticipants()) {
            if (status.equals(participantStatus(participant))) count++;
        }
        return count;
    }

    private void showFlights() {
        java.util.List<Flight> flights = FlightRepository.getFlights(this);
        String activeId = FlightRepository.getActiveId(this);
        for (Flight flight : flights) {
            boolean active = flight.id.equals(activeId);
            card(active ? "АКТИВНЫЙ ВЫЛЕТ" : "Вылет",
                FlightPresentation.title(flight) + "\n" + flight.date + " • " + flight.time + "\nМесто встречи: " + flight.place +
                    "\nПубликация: " + (flight.published ? "отмечен опубликованным локально" : "черновик"),
                active ? "Редактировать" : "Сделать активным", () -> {
                    if (flight.id.equals(FlightRepository.getActiveId(this))) {
                        editFlight(flight.id);
                    } else {
                        if (!FlightAdminActions.activate(this, flight.id)) {
                            Toast.makeText(this, "Не удалось выбрать вылет.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        loadDraftFromActiveFlight();
                        
                        render();
                    }
                });
            Button publish = smallButton(flight.published ? "Снять отметку публикации" : "Отметить публикацию локально");
            LinearLayout.LayoutParams publishParams = lp(-1,dp(40));
            publishParams.bottomMargin = dp(6);
            content.addView(publish,publishParams);
            publish.setOnClickListener(v -> {
                if (!FlightAdminActions.setPublished(this, flight.id, !flight.published)) {
                    Toast.makeText(this, "Не удалось изменить локальную отметку публикации.", Toast.LENGTH_SHORT).show();
                    return;
                }
                render();
            });
            if (flights.size() > 1) {
                Button delete = smallButton("Удалить этот вылет");
                delete.setTextColor(Color.rgb(170,65,55));
                LinearLayout.LayoutParams deleteParams = lp(-1, dp(40));
                deleteParams.bottomMargin = dp(8);
                content.addView(delete, deleteParams);
                delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Удалить вылет?")
                    .setMessage(FlightPresentation.title(flight) + "\nЭто действие нельзя отменить.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить", (dialog, which) -> {
                        if (!FlightAdminActions.delete(this, flight.id)) {
                            Toast.makeText(this, "Вылет не удалён: он уже отсутствует или является последним.", Toast.LENGTH_LONG).show();
                            render();
                            return;
                        }
                        loadDraftFromActiveFlight();
                        FlightReminderScheduler.cancel(this, flight.id);
                        FlightReminderScheduler.syncAll(this);
                        
                        render();
                    }).show());
            }
        }
        Button create = actionButton("＋  Создать вылет", true);
        create.setOnClickListener(v -> editFlight(null));
        content.addView(create, lp(-1, dp(50)));
        note("Отметка публикации хранится локально и не рассылает вылет участникам. Общая публикация требует подключения сервера.");
    }

    private void loadDraftFromActiveFlight() {
        Flight active = FlightRepository.getActive(this);
        if (active != null) {
            draftTitle = active.title;
            draftPlace = active.place;
            try {
                java.util.Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(active.dateIso);
                if (parsed != null) {
                    Calendar parsedDate = Calendar.getInstance();
                    parsedDate.setTime(parsed);
                    flightDate.set(Calendar.YEAR, parsedDate.get(Calendar.YEAR));
                    flightDate.set(Calendar.MONTH, parsedDate.get(Calendar.MONTH));
                    flightDate.set(Calendar.DAY_OF_MONTH, parsedDate.get(Calendar.DAY_OF_MONTH));
                }
            } catch (Exception ignored) {}
            try {
                String[] parts = active.time.split(":");
                flightDate.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                flightDate.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            } catch (Exception ignored) {}
        }
    }

    private void editFlight(String flightId) {
        editingFlightId = flightId;
        Flight existing = flightId == null ? null : FlightRepository.getById(this, flightId);
        if (flightId != null && existing == null) {
            Toast.makeText(this, "Вылет больше не существует. Обновите список.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (existing == null) {
            flightDate.setTimeInMillis(System.currentTimeMillis());
            flightDate.add(Calendar.DAY_OF_MONTH,1);
            flightDate.set(Calendar.HOUR_OF_DAY,10);
            flightDate.set(Calendar.MINUTE,30);
            flightDate.set(Calendar.SECOND,0);
            draftTitle = "Новый вылет";
            draftPlace = ClubSettingsActions.get(this).defaultMeetingPlace;
        }
        if (existing != null) {
            draftTitle = existing.title;
            draftPlace = existing.place;
            try {
                java.util.Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(existing.dateIso);
                if (parsed != null) {
                    Calendar parsedDate = Calendar.getInstance();
                    parsedDate.setTime(parsed);
                    flightDate.set(Calendar.YEAR, parsedDate.get(Calendar.YEAR));
                    flightDate.set(Calendar.MONTH, parsedDate.get(Calendar.MONTH));
                    flightDate.set(Calendar.DAY_OF_MONTH, parsedDate.get(Calendar.DAY_OF_MONTH));
                }
            } catch (Exception ignored) {}
            try {
                String[] parts = existing.time.split(":");
                flightDate.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                flightDate.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            } catch (Exception ignored) {}
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(4), dp(4), dp(4));
        EditText title = field("Название вылета", existing == null ? "Новый вылет" : existing.title);
        EditText place = field("Место встречи", existing == null ? ClubSettingsActions.get(this).defaultMeetingPlace : existing.place);
        form.addView(title); form.addView(place);
        Button date = smallButton("Дата: " + draftDateLabel());
        Button time = smallButton("Время: " + draftTimeLabel());
        form.addView(date); form.addView(time);
        date.setOnClickListener(v -> new DatePickerDialog(this, (view,y,m,d) -> {
            flightDate.set(y,m,d); date.setText("Дата: " + draftDateLabel());
        }, flightDate.get(Calendar.YEAR), flightDate.get(Calendar.MONTH), flightDate.get(Calendar.DAY_OF_MONTH)).show());
        time.setOnClickListener(v -> new TimePickerDialog(this, (view,h,m) -> {
            flightDate.set(Calendar.HOUR_OF_DAY,h); flightDate.set(Calendar.MINUTE,m);
            time.setText("Время: " + draftTimeLabel());
        }, flightDate.get(Calendar.HOUR_OF_DAY), flightDate.get(Calendar.MINUTE), true).show());
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(existing == null ? "Создать вылет" : "Редактировать вылет")
            .setView(form)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить локально", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newTitle = title.getText().toString().trim();
            String newPlace = place.getText().toString().trim();
            if (!FlightAdminActions.isValidTitle(newTitle)) {
                title.setError("Укажите название вылета");
                title.requestFocus();
                return;
            }
            boolean saved;
            if (editingFlightId == null) {
                saved = FlightAdminActions.createAndActivate(this, newTitle, draftDateLabel(),
                    draftTimeLabel(), newPlace, draftDateIso()) != null;
            } else {
                saved = FlightAdminActions.updateActive(this, editingFlightId, newTitle,
                    draftDateLabel(), draftTimeLabel(), newPlace, draftDateIso());
            }
            if (!saved) {
                Toast.makeText(this, "Не удалось сохранить. Проверьте дату и время.", Toast.LENGTH_LONG).show();
                return;
            }
            draftTitle = newTitle;
            draftPlace = newPlace;
            editingFlightId = null;
            dialog.dismiss();
            loadDraftFromActiveFlight();
            
            FlightReminderScheduler.syncAll(this);
            Toast.makeText(this, "Сохранено на этом устройстве", Toast.LENGTH_SHORT).show();
            render();
        }));
        dialog.show();
    }

    private void showParticipants() {
        java.util.List<Participant> participants = localParticipants();
        int going = countLocalParticipantStatus(AttendanceRepository.STATUS_GOING);
        int notGoing = countLocalParticipantStatus(AttendanceRepository.STATUS_NOT_GOING);
        int pending = countLocalParticipantStatus(AttendanceRepository.STATUS_PENDING);
        card("Сводка", "Участников: " + participants.size() + " • Едут: " + going +
            " • Не едут: " + notGoing + " • Без ответа: " + pending, null, null);

        EditText search = field("Поиск участника по имени", "");
        content.addView(search,lp(-1,dp(48)));
        LinearLayout participantList = new LinearLayout(this);
        participantList.setOrientation(LinearLayout.VERTICAL);
        content.addView(participantList,lp(-1,-2));
        renderParticipantRows(participantList,participants,"");
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence value,int start,int before,int count) {
                renderParticipantRows(participantList,participants,value.toString());
            }
            @Override public void afterTextChanged(android.text.Editable value) {}
        });

        Button add = actionButton("＋  Добавить участника", true);
        add.setOnClickListener(v -> addParticipant());
        content.addView(add, lp(-1,dp(48)));
        Button export = actionButton("Поделиться списком", false);
        export.setOnClickListener(v -> shareParticipantList());
        LinearLayout.LayoutParams ep=lp(-1,dp(48)); ep.topMargin=dp(6); content.addView(export,ep);
        note("Участники и ответы хранятся локально. Общая база клуба появится после подключения сервера.");
    }

    private void renderParticipantRows(LinearLayout parent, java.util.List<Participant> participants, String query) {
        parent.removeAllViews();
        String filter=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        int shown=0;
        for(Participant participant:participants) {
            if(!participant.displayName.toLowerCase(Locale.ROOT).contains(filter)) continue;
            String state=participantStatus(participant);
            int color=state.equals(AttendanceRepository.STATUS_NOT_GOING)?Color.rgb(170,65,55):
                state.equals(AttendanceRepository.STATUS_GOING)?GREEN:MUTED;
            participantRow(parent,participant,state,color);
            shown++;
        }
        if(shown==0) {
            TextView empty=text(filter.isEmpty()?"Список участников пуст.":"Ничего не найдено.",13,MUTED,false);
            empty.setPadding(dp(4),dp(8),dp(4),dp(12));
            parent.addView(empty,lp(-1,-2));
        }
    }

    private void showMessages() {
        Flight active = selectedMessageFlightId == null ? FlightRepository.getActive(this) : FlightRepository.getById(this, selectedMessageFlightId);
        if (active == null) { selectedMessageFlightId = null; active = FlightRepository.getActive(this); }
        String flightId = active == null ? null : active.id;
        String summary = active == null ? "Нет выбранного вылета" :
            FlightPresentation.title(active) + "\n" + active.date + " • " + active.time + "\nМесто встречи: " + active.place;
        card("Объявление о вылете", summary, null, null);
        EditText message = field("Текст сообщения", MessageTemplates.defaultReminder(active, ClubSettingsActions.get(this).clubName));
        content.addView(message);
        recipientButton = actionButton(recipientButtonText() + " — изменить", false);
        recipientButton.setOnClickListener(v -> chooseMessageRecipient());
        content.addView(recipientButton, lp(-1, dp(48)));

        Button templates = actionButton("Выбрать шаблон", false);
        templates.setOnClickListener(v -> {
            String[] names = {"Напоминание о вылете","Изменение данных вылета","Просьба подтвердить участие"};
            new AlertDialog.Builder(this).setTitle("Шаблоны сообщения").setItems(names,(dialog,which) -> {
                Flight current = selectedMessageFlightId == null ? FlightRepository.getActive(this) : FlightRepository.getById(this, selectedMessageFlightId);
                if(current==null) {
                    Toast.makeText(this, "Сначала выберите вылет.", Toast.LENGTH_SHORT).show();
                    return;
                }
                String body = MessageTemplates.forTemplate(current, which, ClubSettingsActions.get(this).clubName);
                message.setText(body);
                EventLogRepository.record(this,"MSG-01","template_selected","success",current.id);
            }).show();
        });
        content.addView(templates,lp(-1,dp(48)));

        Button preview = actionButton("Предпросмотр сообщения", false);
        preview.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Предпросмотр — " + recipientLabel)
            .setMessage("Кому: " + recipientLabel + "\n\n" + message.getText().toString()).setPositiveButton("Закрыть",null).show());
        content.addView(preview, lp(-1,dp(48)));

        Button save = actionButton("Сохранить черновик", false);
        save.setOnClickListener(v -> {
            if (flightId == null) {
                Toast.makeText(this, "Сначала выберите вылет.", Toast.LENGTH_SHORT).show();
                return;
            }
            MessageRecord saved = MessageRepository.saveDraft(this, flightId, message.getText().toString());
            if (saved != null) EventLogRepository.record(this,"MSG-01","save_draft","success",flightId);
            Toast.makeText(this, saved == null ? "Введите текст сообщения" : "Черновик сохранён", Toast.LENGTH_SHORT).show();
        });
        content.addView(save, lp(-1,dp(48)));

        Button localPublish = actionButton("Записать для информера (локальный файл)", true);
        localPublish.setOnClickListener(v -> {
            String body = message.getText().toString().trim();
            if (body.isEmpty()) {
                Toast.makeText(this, "Введите текст сообщения", Toast.LENGTH_SHORT).show();
                return;
            }
            LocalTransportMessage savedLocal = MessageTransportProvider.get().send(
                this, LocalFileMessageTransport.ANNOUNCEMENT_CHANNEL, "Объявление информера", body);
            if (savedLocal == null) {
                Toast.makeText(this, "Не удалось записать файл. Существующие данные сохранены.", Toast.LENGTH_LONG).show();
                EventLogRepository.record(this, "MSG-02", "local_file_write", "error", "write_failed");
                return;
            }
            EventLogRepository.record(this, "MSG-02", "local_file_write", "success", savedLocal.id);
            Toast.makeText(this, "Записано в локальный файл. Только на этом телефоне.", Toast.LENGTH_LONG).show();
        });
        content.addView(localPublish, lp(-1,dp(48)));

        Button history = actionButton("История сообщений", false);
        history.setOnClickListener(v -> {
            if (flightId == null) {
                Toast.makeText(this, "Сначала выберите вылет.", Toast.LENGTH_SHORT).show();
                return;
            }
            showMessageHistory(flightId,message);
        });
        content.addView(history, lp(-1,dp(48)));

        Button share = actionButton("Поделиться…", true);
        share.setOnClickListener(v -> {
            String body = message.getText().toString().trim();
            if (flightId == null) {
                Toast.makeText(this, "Сначала выберите вылет.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (body.isEmpty()) {
                Toast.makeText(this,"Введите текст сообщения",Toast.LENGTH_SHORT).show();
                return;
            }
            Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain");
            java.util.List<Participant> recipients = resolveMessageRecipients();
            if (recipients.isEmpty()) {
                Toast.makeText(this, "В выбранной группе нет участников. Сообщение не отправлено.", Toast.LENGTH_LONG).show();
                EventLogRepository.record(this, "MSG-01", "share_chooser_opened", "blocked_empty_recipient_set", flightId);
                return;
            }
            StringBuilder recipientNames = new StringBuilder();
            for (Participant person : recipients) { if (recipientNames.length() > 0) recipientNames.append(", "); recipientNames.append(person.displayName); }
            String addressedBody = "Адресат: " + recipientLabel + "\nПолучатели (локальный список, доставка не подтверждена): " + recipientNames + "\n\n" + body;
            send.putExtra(Intent.EXTRA_TEXT,addressedBody);
            try {
                startActivity(Intent.createChooser(send,"Отправить сообщение"));
                MessageRepository.recordShareChooserOpened(this, flightId, addressedBody);
                EventLogRepository.record(this,"MSG-01","share_chooser_opened","delivery_unconfirmed",flightId);
            } catch (Exception e) {
                Toast.makeText(this,"Нет приложения для отправки",Toast.LENGTH_SHORT).show();
            }
        });
        content.addView(share, lp(-1,dp(48)));
        note("Локальная запись обновляет файл на этом телефоне. Она не отправляет сообщение на другие устройства. «Поделиться…» открывает системное меню, доставка не подтверждается.");
    }

    private void chooseMessageRecipient(){
        String[] types={"Всем","Одному участнику","Группе"};int checked="ONE".equals(recipientMode)?1:("GROUP".equals(recipientMode)?2:0);
        new AlertDialog.Builder(this).setTitle("Кому отправить?").setSingleChoiceItems(types,checked,(dialog,which)->{dialog.dismiss();
            if(which==0){recipientMode="ALL";recipientId="all";recipientLabel="Всем";updateRecipientButton();}
            else if(which==1){java.util.List<Participant> ps=localParticipants();if(ps.isEmpty()){Toast.makeText(this,"Сначала добавьте участника на экране S06.",Toast.LENGTH_LONG).show();return;}
                String[] names=new String[ps.size()];for(int i=0;i<ps.size();i++)names[i]=ps.get(i).displayName;
                new AlertDialog.Builder(this).setTitle("Выберите участника").setItems(names,(d,index)->{Participant p=ps.get(index);recipientMode="ONE";recipientId=p.id;recipientLabel=p.displayName;updateRecipientButton();}).setNegativeButton("Отмена",null).show();
            }else{String[] groups={"Все участники","Участники, которые едут","Участники без ответа"};String[] ids={"group:all","group:going","group:pending"};
                new AlertDialog.Builder(this).setTitle("Выберите группу").setItems(groups,(d,index)->{recipientMode="GROUP";recipientId=ids[index];recipientLabel=groups[index];updateRecipientButton();}).setNegativeButton("Отмена",null).show();}
        }).setNegativeButton("Отмена",null).show();
    }
    private String recipientButtonText() {
        java.util.List<Participant> recipients = resolveMessageRecipients();
        if ("ONE".equals(recipientMode)) return "Кому: " + recipientLabel;
        return "Кому: " + recipientLabel + " (" + recipients.size() + ")";
    }

    private java.util.List<Participant> resolveMessageRecipients() {
        java.util.List<Participant> all = localParticipants();
        java.util.List<Participant> result = new java.util.ArrayList<>();
        String flightId = selectedMessageFlightId == null ? FlightRepository.getActiveId(this) : selectedMessageFlightId;
        for (Participant participant : all) {
            if ("ONE".equals(recipientMode)) {
                if (participant.id.equals(recipientId)) result.add(participant);
            } else if ("GROUP".equals(recipientMode) && "group:going".equals(recipientId)) {
                if (AttendanceRepository.STATUS_GOING.equals(AttendanceRepository.getResponse(this, participant.id, flightId))) result.add(participant);
            } else if ("GROUP".equals(recipientMode) && "group:pending".equals(recipientId)) {
                if (AttendanceRepository.STATUS_PENDING.equals(AttendanceRepository.getResponse(this, participant.id, flightId))) result.add(participant);
            } else {
                result.add(participant);
            }
        }
        return result;
    }

    private void updateRecipientButton() {
        if (recipientButton != null) recipientButton.setText(recipientButtonText() + " — изменить");
        EventLogRepository.record(this, "MSG-01", "recipient_selected", "local_only",
            recipientMode + ":" + recipientId + ":count=" + resolveMessageRecipients().size());
    }

    private void showMessageHistory(String flightId, EditText editor) {
        java.util.List<MessageRecord> history = MessageRepository.getHistory(this, flightId);
        if (history.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("История сообщений")
                .setMessage("Для этого вылета ещё нет сохранённых сообщений.")
                .setPositiveButton("Закрыть",null).show();
            return;
        }
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        String[] labels = new String[history.size()];
        for(int i=0;i<history.size();i++) {
            MessageRecord item=history.get(i);
            String preview=item.text.replace("\n"," ");
            if(preview.length()>90) preview=preview.substring(0,87)+"…";
            labels[i]=format.format(new Date(item.updatedAt))+" • "+item.state+"\n"+preview;
        }
        new AlertDialog.Builder(this).setTitle("История сообщений")
            .setItems(labels,(dialog,which) -> {
                MessageRecord selected=history.get(which);
                editor.setText(selected.text);
                EventLogRepository.record(this,"MSG-01","history_item_loaded","success",selected.flightId);
                Toast.makeText(this,"Текст загружен в редактор; отправка не выполнялась",Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Закрыть",null).show();
    }

    private void showCalendar() {
        String selectedIso=CalendarActions.selectedDate(this,draftDateIso());if(selectedIso.isEmpty())selectedIso=draftDateIso();
        try{java.util.Date d=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).parse(selectedIso);if(d!=null)flightDate.setTime(d);}catch(Exception ignored){}
        if(!calendarMonthInitialized){displayedMonth.setTimeInMillis(flightDate.getTimeInMillis());displayedMonth.set(Calendar.DAY_OF_MONTH,1);calendarMonthInitialized=true;}
        card("Календарь клуба","Выберите дату. Точки отмечают даты с вылетами.",null,null);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);
        Button previous=smallButton("‹"),next=smallButton("›");TextView month=text("",17,NAVY,true);
        header.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(42)));header.addView(month,new LinearLayout.LayoutParams(0,dp(42),1));header.addView(next,new LinearLayout.LayoutParams(dp(48),dp(42)));content.addView(header,lp(-1,dp(42)));
        LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);content.addView(grid,lp(-1,-2));renderCalendarGridInto(grid,month);
        previous.setOnClickListener(v->{displayedMonth.add(Calendar.MONTH,-1);render();});next.setOnClickListener(v->{displayedMonth.add(Calendar.MONTH,1);render();});
        TextView chosen=text("Выбрано: "+FlightDateUtils.toDisplay(selectedIso),14,NAVY,true);LinearLayout.LayoutParams cp=lp(-1,-2);cp.topMargin=dp(8);content.addView(chosen,cp);
        java.util.List<Flight> matches=CalendarActions.flightsOnDate(this,selectedIso);if(matches.isEmpty())note("На выбранную дату вылетов нет.");
        for(Flight flight:matches){
            card("Вылет",FlightPresentation.title(flight)+"\n"+flight.date+" • "+flight.time+"\n"+flight.place,"Открыть в вылетах",()->{
                if(!flight.id.equals(FlightRepository.getActiveId(this)))FlightAdminActions.activate(this,flight.id);
                loadDraftFromActiveFlight();section="Вылеты";render();});
            Button prepare=actionButton("Подготовить сообщение по этому вылету",true);content.addView(prepare,lp(-1,dp(48)));
            prepare.setOnClickListener(v->{selectedMessageFlightId=flight.id;navigateTo("Сообщения");});
        }
        Button today=actionButton("Сегодня",false);today.setOnClickListener(v->{flightDate.setTimeInMillis(System.currentTimeMillis());displayedMonth.setTimeInMillis(flightDate.getTimeInMillis());displayedMonth.set(Calendar.DAY_OF_MONTH,1);calendarMonthInitialized=true;
            if(CalendarActions.selectDate(this,draftDateIso()))render();else Toast.makeText(this,"Не удалось выбрать сегодняшнюю дату",Toast.LENGTH_SHORT).show();});
        content.addView(today,lp(-1,dp(44)));note("Файл полётов: "+FlightDownloadExchange.getPath()+". Системное меню отправки не подтверждает доставку.");
    }
    private void renderCalendarGridInto(LinearLayout grid,TextView monthLabel){
        grid.removeAllViews();monthLabel.setText(new SimpleDateFormat("LLLL yyyy",Locale.forLanguageTag("ru")).format(displayedMonth.getTime()));
        String[] weekdays={"Пн","Вт","Ср","Чт","Пт","Сб","Вс"};LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);
        for(String day:weekdays){TextView t=text(day,11,MUTED,true);t.setGravity(Gravity.CENTER);header.addView(t,new LinearLayout.LayoutParams(0,dp(28),1));}grid.addView(header);
        Calendar first=(Calendar)displayedMonth.clone();first.set(Calendar.DAY_OF_MONTH,1);int offset=(first.get(Calendar.DAY_OF_WEEK)+5)%7,days=first.getActualMaximum(Calendar.DAY_OF_MONTH);
        java.util.Set<String> flightDates=new java.util.HashSet<>();for(Flight f:FlightRepository.getFlights(this))flightDates.add(f.dateIso);
        for(int row=0;row<6;row++){LinearLayout week=new LinearLayout(this);week.setOrientation(LinearLayout.HORIZONTAL);
            for(int col=0;col<7;col++){int day=row*7+col-offset+1;if(day<1||day>days){week.addView(text("",12,MUTED,false),new LinearLayout.LayoutParams(0,dp(40),1));continue;}
                Calendar date=(Calendar)displayedMonth.clone();date.set(Calendar.DAY_OF_MONTH,day);String iso=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(date.getTime());
                boolean selected=iso.equals(draftDateIso()),hasFlight=flightDates.contains(iso);Button cell=new Button(this);cell.setText(hasFlight?day+" •":String.valueOf(day));cell.setTextSize(11);cell.setAllCaps(false);
                cell.setMinWidth(0);cell.setMinimumWidth(0);cell.setMinHeight(0);cell.setMinimumHeight(0);cell.setPadding(0,0,0,0);cell.setTextColor(selected?Color.WHITE:NAVY);
                cell.setBackground(rippleBackground(selected?BLUE:Color.WHITE,8,hasFlight?GREEN:Color.rgb(225,230,237)));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(40),1);p.setMargins(dp(1),dp(1),dp(1),dp(1));week.addView(cell,p);
                final int selectedDay=day;cell.setOnClickListener(v->{Calendar picked=(Calendar)displayedMonth.clone();picked.set(Calendar.DAY_OF_MONTH,selectedDay);flightDate.setTimeInMillis(picked.getTimeInMillis());
                    if(!CalendarActions.selectDate(this,iso)){Toast.makeText(this,"Не удалось сохранить дату",Toast.LENGTH_SHORT).show();return;}render();});
            }grid.addView(week);if((row+1)*7-offset>=days)break;
        }
    }

    private void showSettings() {
        ClubSettings settings = ClubSettingsActions.get(this);
        card("Профиль клуба", "Название: " + settings.clubName +
            "\nМесто встречи по умолчанию: " + settings.defaultMeetingPlace,
            "Изменить настройки клуба", () -> {
                LinearLayout form = new LinearLayout(this);
                form.setOrientation(LinearLayout.VERTICAL);
                EditText club = field("Название клуба", settings.clubName);
                EditText place = field("Место встречи по умолчанию", settings.defaultMeetingPlace);
                form.addView(club);
                form.addView(place);
                new AlertDialog.Builder(this).setTitle("Настройки клуба").setView(form)
                    .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w) -> {
                        if (!ClubSettingsActions.save(this, club.getText().toString(), place.getText().toString())) {
                            Toast.makeText(this, "Не удалось сохранить настройки клуба.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Toast.makeText(this,"Настройки клуба сохранены",Toast.LENGTH_SHORT).show();
                        render();
                    }).show();
            });
        String reminderState = FlightReminderScheduler.notificationsEnabled(this)
            ? "разрешены в настройках Android" : "запрещены в настройках Android";
        card("Уведомления", "Локальные напоминания: " + reminderState
                + "\nPush-уведомления: требуется Firebase и серверный отправитель.",
                "Настроить уведомления", () -> { section="Уведомления"; render(); });
        card("Синхронизация", SyncRepository.getStatus(this), "Просмотреть очередь изменений", () ->
            new AlertDialog.Builder(this).setTitle("Ожидающие изменения")
                .setMessage(SyncRepository.getPendingSummary(this,60))
                .setPositiveButton("Закрыть",null).show());
        card("Доступ администратора", "Авторизация и роли пока не подключены. Не храните конфиденциальные данные в этом прототипе.", null, null);
        note("Настройки клуба хранятся отдельно от вылетов. Изменение адреса по умолчанию не меняет уже созданные вылеты.");
    }

    private void showNotifications() {
        String notificationState = FlightReminderScheduler.notificationState(this);
        boolean notificationsEnabled = "enabled".equals(notificationState);
        String notificationStatus;
        if ("enabled".equals(notificationState)) {
            notificationStatus = "Разрешены. Для будущих вылетов ставится напоминание за 1 час.";
        } else if ("permission_required".equals(notificationState)) {
            notificationStatus = "Android 13+: разрешение на уведомления ещё не выдано.";
        } else if ("not_configured".equals(notificationState)) {
            notificationStatus = "Канал напоминаний ещё не создан. Нажмите, чтобы включить.";
        } else if ("channel_disabled".equals(notificationState)) {
            notificationStatus = "Канал напоминаний отключён в настройках Android.";
        } else if ("app_disabled".equals(notificationState)) {
            notificationStatus = "Уведомления приложения отключены в настройках Android.";
        } else {
            notificationStatus = "Служба уведомлений недоступна на устройстве.";
        }
        card("Локальные напоминания", "Состояние: " + notificationStatus,
            notificationsEnabled ? "Обновить напоминания" : "Включить / настроить", () -> {
                if (notificationsEnabled) {
                    FlightReminderScheduler.syncAll(this);
                    Toast.makeText(this,"Расписание локальных напоминаний обновлено",Toast.LENGTH_SHORT).show();
                } else {
                    refreshOnResume = true;
                    if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        boolean requestedBefore = FlightReminderScheduler.permissionRequestAttempted(this);
                        if (!requestedBefore || shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                            FlightReminderScheduler.markPermissionRequestAttempted(this);
                            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1002);
                        } else {
                            openNotificationSettings();
                        }
                    } else {
                        FlightReminderScheduler.ensureChannel(this);
                        if (FlightReminderScheduler.notificationsEnabled(this)) {
                            refreshOnResume = false;
                            FlightReminderScheduler.syncAll(this);
                            Toast.makeText(this, "Локальные напоминания включены.", Toast.LENGTH_SHORT).show();
                        } else {
                            openNotificationSettings();
                        }
                    }
                }
                render();
            });
        card("Push-уведомления", "FCM не настроен: отсутствует конфигурация Firebase и сервер отправки. Локальные напоминания не заменяют push-рассылку.", null, null);
        card("Состояние", "Локальные напоминания работают на этом устройстве. Удалённая отправка уведомлений пока недоступна.", null, null);
        Button log = actionButton("Открыть журнал событий", false);
        log.setOnClickListener(v -> {
            ScrollView scroll = new ScrollView(this);
            TextView logText = text(EventLogRepository.getRecent(this,80),13,NAVY,false);
            logText.setPadding(dp(14),dp(10),dp(14),dp(10));
            scroll.addView(logText);
            new AlertDialog.Builder(this).setTitle("Журнал событий").setView(scroll)
                .setPositiveButton("Закрыть",null).show();
        });
        content.addView(log,lp(-1,dp(48)));
        Button debugLog = actionButton("Открыть файл отладки всех модулей", false);
        debugLog.setOnClickListener(v -> {
            ScrollView debugScroll = new ScrollView(this);
            TextView debugText = text(ModuleDebugLog.getRecent(this, 300), 12, NAVY, false);
            debugText.setTextIsSelectable(true);
            debugText.setPadding(dp(14), dp(10), dp(14), dp(10));
            debugScroll.addView(debugText);
            new AlertDialog.Builder(this)
                .setTitle("Отладка модулей")
                .setMessage("Файл: " + ModuleDebugLog.getFilePath(this))
                .setView(debugScroll)
                .setPositiveButton("Закрыть", null)
                .show();
        });
        content.addView(debugLog, lp(-1, dp(48)));

        Button uploadDebug = actionButton("Поделиться журналом отладки", false);
        uploadDebug.setOnClickListener(v -> {
            uploadDebug.setEnabled(false);
            uploadDebug.setText("Отправка…");
            GitHubDebugUploader.uploadNow(this, (success, message, lines) -> runOnUiThread(() -> {
                uploadDebug.setEnabled(true);
                uploadDebug.setText("Поделиться журналом отладки");
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }));
        });
        LinearLayout.LayoutParams uploadParams = lp(-1, dp(48));
        uploadParams.topMargin = dp(8);
        content.addView(uploadDebug, uploadParams);
        note("Журнал событий и отдельный файл отладки не должны содержать пароли, токены или другие секреты.");
    }

        private void addParticipant() {
        EditText input = field("Имя участника", "");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Новый участник").setView(input)
            .setNegativeButton("Отмена", null).setPositiveButton("Добавить", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!ParticipantAdminActions.isValidName(name)) {
                input.setError("Введите имя участника");
                input.requestFocus();
                return;
            }
            Participant created = ParticipantAdminActions.add(this, name);
            if (created == null) {
                input.setError("Такой участник уже есть");
                input.requestFocus();
                return;
            }
            dialog.dismiss();
            Toast.makeText(this, "Участник добавлен", Toast.LENGTH_SHORT).show();
            
            render();
        }));
        dialog.show();
    }

    private void shareParticipantList() {
        Flight active = FlightRepository.getActive(this);
        StringBuilder report = new StringBuilder(ClubSettingsActions.get(this).clubName).append(" — участники: ");
        if (active != null) report.append(FlightPresentation.title(active)).append("\n").append(active.date)
            .append(" • ").append(active.time).append("\n\n");
        java.util.List<Participant> participants = localParticipants();
        if (participants.isEmpty()) report.append("Список участников пуст.\n");
        for (Participant participant : participants) {
            report.append(participant.displayName).append(" — ")
                .append(participantStatus(participant)).append("\n");
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, report.toString());
        try {
            startActivity(Intent.createChooser(send, "Поделиться списком участников"));
            EventLogRepository.record(this, "PAR-01", "share_list", "chooser_opened_delivery_unconfirmed",
                active == null ? null : active.id);
        } catch (Exception e) {
            EventLogRepository.record(this, "PAR-01", "share_list", "failed", active == null ? null : active.id);
            Toast.makeText(this, "Нет приложения для отправки", Toast.LENGTH_SHORT).show();
        }
    }

    private void participantRow(LinearLayout parent, Participant participant, String state, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14),dp(12),dp(14),dp(12));
        row.setBackground(round(Color.WHITE,14,Color.rgb(220,228,236)));
        TextView n = text(participant.displayName,15,NAVY,true);
        TextView statusView = text(state,13,color,false);
        LinearLayout.LayoutParams sp=lp(-1,-2); sp.topMargin=dp(4);
        row.addView(n); row.addView(statusView,sp);
        row.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(participant.displayName)
            .setItems(new String[]{"Изменить статус","Переименовать","Удалить участника"}, (dialog, action) -> {
                if (action == 0) {
                    String[] options = {AttendanceRepository.STATUS_GOING, AttendanceRepository.STATUS_PENDING, AttendanceRepository.STATUS_NOT_GOING};
                    new AlertDialog.Builder(this).setTitle("Статус участника").setItems(options, (d, which) -> {
                        if (!ParticipantAdminActions.setResponse(this, participant.id,
                                FlightRepository.getActiveId(this), options[which])) {
                            Toast.makeText(this, "Не удалось изменить ответ: проверьте активный вылет.", Toast.LENGTH_SHORT).show();
                        } else {
                            
                        }
                        render();
                    }).show();
                } else if (action == 1) {
                    EditText name = field("Имя участника", participant.displayName);
                    AlertDialog renameDialog = new AlertDialog.Builder(this).setTitle("Переименовать участника").setView(name)
                        .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
                    renameDialog.setOnShowListener(ignored -> renameDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                        String newName = name.getText().toString().trim();
                        if (!ParticipantAdminActions.rename(this, participant.id, newName)) {
                            name.setError("Имя пустое или уже используется");
                            name.requestFocus();
                            return;
                        }
                        renameDialog.dismiss();
                        
                        render();
                    }));
                    renameDialog.show();
                } else {
                    new AlertDialog.Builder(this).setTitle("Удалить участника?")
                        .setMessage("Будут удалены запись участника и его локальные статусы.")
                        .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d, which) -> {
                            if (!ParticipantAdminActions.delete(this, participant.id)) {
                                Toast.makeText(this, "Участник уже отсутствует.", Toast.LENGTH_SHORT).show();
                            } else {
                                
                            }
                            render();
                        }).show();
                }
            }).setNegativeButton("Закрыть",null).show());
        LinearLayout.LayoutParams p=lp(-1,-2); p.bottomMargin=dp(8);
        parent.addView(row,p);
    }

    private void card(String title, String body, String action, Runnable click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14),dp(13),dp(14),dp(12));
        box.setBackground(round(Color.WHITE,16,Color.rgb(220,228,236)));
        TextView h=text(title,16,NAVY,true); box.addView(h);
        TextView t=text(body,14,MUTED,false);
        LinearLayout.LayoutParams tp=lp(-1,-2); tp.topMargin=dp(7); box.addView(t,tp);
        if(action!=null) {
            Button b=smallButton(action);
            LinearLayout.LayoutParams bp=lp(-1,dp(44)); bp.topMargin=dp(8); box.addView(b,bp);
            if (click != null) b.setOnClickListener(v -> {
                String actionName = "admin_action_" + action.trim().replace(' ', '_');
                DebugOperation operation = ModuleDebugLog.startOperation(this, "ADM-01", actionName, "section=" + section);
                try {
                    click.run();
                    operation.finish("dispatched", "handler_returned; outcome_recorded_by_action");
                } catch (RuntimeException error) {
                    operation.finish("error", error.getClass().getSimpleName());
                    Toast.makeText(this, "Не удалось выполнить действие.", Toast.LENGTH_SHORT).show();
                }
            });
        }
        LinearLayout.LayoutParams p=lp(-1,-2); p.bottomMargin=dp(10); content.addView(box,p);
    }

    private void stat(LinearLayout parent, String value, String label) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER); box.setPadding(dp(5),dp(12),dp(5),dp(12));
        box.setBackground(round(Color.WHITE,14,Color.rgb(220,228,236)));
        box.addView(text(value,22,NAVY,true));
        TextView l=text(label,11,MUTED,false); box.addView(l);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1); p.setMargins(0,dp(2),dp(6),dp(10));
        parent.addView(box,p);
    }

    private EditText field(String hint, String value) {
        EditText e=new EditText(this); e.setSingleLine(false); e.setHint(hint); e.setText(value);
        e.setTextSize(15); e.setTextColor(NAVY); e.setHintTextColor(MUTED);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setPadding(dp(10),dp(8),dp(10),dp(8));
        return e;
    }

    private Button actionButton(String label, boolean primary) {
        Button b=smallButton(label); b.setTextSize(14);
        b.setTextColor(primary?Color.WHITE:NAVY);
        b.setBackground(rippleBackground(primary?BLUE:Color.WHITE,12,primary?BLUE:Color.rgb(220,228,236)));
        return b;
    }

    private Button smallButton(String label) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(12);
        b.setMinHeight(dp(40)); b.setMinimumHeight(dp(40)); b.setPadding(dp(8),0,dp(8),0);
        b.setBackground(rippleBackground(Color.WHITE, 10, Color.rgb(220,228,236)));
        return b;
    }

    private void note(String value) {
        TextView t=text(value,12,MUTED,false);
        LinearLayout.LayoutParams p=lp(-1,-2); p.setMargins(dp(2),dp(4),dp(2),dp(12)); content.addView(t,p);
    }

    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private GradientDrawable round(int color,int radius,int stroke) {
        return AeroclubUi.rounded(this, color, radius, stroke);
    }
    private RippleDrawable rippleBackground(int color, int radius, int stroke) {
        return new RippleDrawable(ColorStateList.valueOf(Color.rgb(190, 210, 235)), round(color, radius, stroke), null);
    }
    private String screenIdForSection(String value) {
        switch (value) {
            case "Вылеты": return "S03"; case "Сообщения": return "S04"; case "Календарь": return "S05";
            case "Участники": return "S06"; case "Настройки": return "S07"; case "Уведомления": return "S08"; default: return "S02";
        }
    }
    private LinearLayout.LayoutParams lp(int w,int h) { return new LinearLayout.LayoutParams(w,h); }
    private int dp(int v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private String draftDateLabel() {
        return new SimpleDateFormat("EEE, d MMM",Locale.forLanguageTag("ru")).format(flightDate.getTime());
    }
    private String draftDateIso() {
        return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(flightDate.getTime());
    }
    private String draftTimeLabel() {
        return new SimpleDateFormat("HH:mm",Locale.getDefault()).format(flightDate.getTime());
    }
}
