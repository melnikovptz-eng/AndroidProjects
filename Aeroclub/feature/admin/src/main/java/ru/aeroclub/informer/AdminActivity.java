package ru.aeroclub.informer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
    private final Calendar flightDate = Calendar.getInstance();
    private String draftTitle = "Полёт выходного дня";
    private String draftPlace = "Аэродром «Северный»";
    private String editingFlightId = null;
    private boolean refreshOnResume = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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
    }

    @Override protected void onResume() {
        super.onResume();
        if (refreshOnResume && root != null) {
            refreshOnResume = false;
            render();
        }
    }

    private void render() {
        root.removeAllViews();
        TextView eyebrow = text("АЭРОКЛУБ  •  УПРАВЛЕНИЕ", 11, MUTED, true);
        root.addView(eyebrow);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = text(section, 25, NAVY, true);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button back = smallButton("К виджету");
        back.setOnClickListener(v -> finish());
        heading.addView(back);
        LinearLayout.LayoutParams hp = lp(-1, -2); hp.topMargin = dp(6);
        root.addView(heading, hp);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        String[] items = {"Обзор", "Вылеты", "Участники", "Сообщения"};
        for (String item : items) {
            Button b = smallButton(item);
            b.setTextSize(11);
            b.setTextColor(section.equals(item) ? Color.WHITE : NAVY);
            b.setBackground(round(section.equals(item) ? BLUE : Color.WHITE, 12, Color.rgb(220,228,236)));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(42), 1);
            np.setMargins(0, dp(10), dp(4), dp(8));
            nav.addView(b, np);
            b.setOnClickListener(v -> { section = item; render(); });
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
            overview.setOnClickListener(v -> { section = "Обзор"; render(); });
            root.addView(overview, lp(-1, dp(44)));
        }
    }

    private void showOverview() {
        Flight active = FlightRepository.getActive(this);
        String flightSummary = active == null ? "Нет выбранного вылета" :
            (active.isDemo ? "ДЕМО · " : "") + displayFlightTitle(active) + "\n" + active.date + " • " + active.time + "\nМесто встречи: " + active.place;
        card("Ближайший вылет", flightSummary,
             "Открыть вылеты", () -> { section = "Вылеты"; render(); });
        int total = localParticipants().size();
        int going = countLocalParticipantStatus("Подтвердил участие");
        int noReply = countLocalParticipantStatus("Ожидается ответ");
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stat(stats, String.valueOf(total), "локальных участников");
        stat(stats, String.valueOf(going), "едут");
        stat(stats, String.valueOf(noReply), "без ответа");
        content.addView(stats);
        card("Участники", "Показываются только записи, сохранённые на этом устройстве.",
             "Управлять участниками", () -> { section = "Участники"; render(); });
        card("Сообщения", "Подготовка текста; доставка и массовая рассылка не подключены.",
             "Открыть сообщения", () -> { section = "Сообщения"; render(); });
        card("Календарь", "Даты локальных вылетов.",
             "Открыть календарь", () -> { section = "Календарь"; render(); });
        card("Сервис", "Настройки клуба и состояние уведомлений.",
             "Настройки", () -> { section = "Настройки"; render(); });
        Button notifications = actionButton("Уведомления", false);
        notifications.setOnClickListener(v -> { section = "Уведомления"; render(); });
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
                (flight.isDemo ? "ДЕМО · " : "") + flight.title + "\n" + flight.date + " • " + flight.time + "\nМесто встречи: " + flight.place +
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
                        WidgetRefreshManager.refresh(this, "ru.aeroclub.informer.FlightWidgetProvider");
                        render();
                    }
                });
            Button publish = smallButton(flight.published ? "Снять отметку публикации" : "Отметить публикацию локально");
            LinearLayout.LayoutParams publishParams = lp(-1,dp(40));
            publishParams.bottomMargin = dp(6);
            content.addView(publish,publishParams);
            publish.setOnClickListener(v -> {
                FlightRepository.setPublished(this,flight.id,!flight.published);
                EventLogRepository.record(this,"FLT-01","publication_status_update","local_only",flight.id);
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
                    .setMessage((flight.isDemo ? "ДЕМО · " : "") + flight.title + "\nЭто действие нельзя отменить.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Удалить", (dialog, which) -> {
                        if (!FlightAdminActions.delete(this, flight.id)) {
                            Toast.makeText(this, "Вылет не удалён: он уже отсутствует или является последним.", Toast.LENGTH_LONG).show();
                            render();
                            return;
                        }
                        loadDraftFromActiveFlight();
                        WidgetRefreshManager.refresh(this, "ru.aeroclub.informer.FlightWidgetProvider");
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
        new AlertDialog.Builder(this).setTitle(existing == null ? "Создать вылет" : "Редактировать вылет").setView(form)
            .setNegativeButton("Отмена", null).setPositiveButton("Сохранить локально", (d,w) -> {
                draftTitle = title.getText().toString().trim();
                draftPlace = place.getText().toString().trim();
                if (draftTitle.isEmpty()) draftTitle = "Новый вылет";
                if (draftPlace.isEmpty()) draftPlace = "Место встречи не указано";
                if (editingFlightId == null) {
                    if (FlightAdminActions.createAndActivate(this, draftTitle, draftDateLabel(),
                            draftTimeLabel(), draftPlace, draftDateIso()) == null) {
                        Toast.makeText(this, "Не удалось сохранить вылет: проверьте дату и время.", Toast.LENGTH_LONG).show();
                        return;
                    }
                } else {
                    if (!FlightAdminActions.updateActive(this, editingFlightId, draftTitle,
                            draftDateLabel(), draftTimeLabel(), draftPlace, draftDateIso())) {
                        Toast.makeText(this, "Не удалось сохранить вылет: проверьте дату и время.", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                editingFlightId = null;
                loadDraftFromActiveFlight();
                WidgetRefreshManager.refresh(this, "ru.aeroclub.informer.FlightWidgetProvider");
                Toast.makeText(this,"Сохранено на этом устройстве",Toast.LENGTH_SHORT).show();
                render();
            }).show();
    }

    private void showParticipants() {
        java.util.List<Participant> participants = localParticipants();
        int going = countLocalParticipantStatus("Подтвердил участие");
        int notGoing = countLocalParticipantStatus("Не участвует");
        int pending = countLocalParticipantStatus("Ожидается ответ");
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
            int color=state.equals("Не участвует")?Color.rgb(170,65,55):
                state.equals("Подтвердил участие")?GREEN:MUTED;
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
        Flight active = FlightRepository.getActive(this);
        String flightId = active == null ? null : active.id;
        String summary = active == null ? "Нет выбранного вылета" :
            displayFlightTitle(active) + "\n" + active.date + " • " + active.time + "\nМесто встречи: " + active.place;
        card("Объявление о вылете", summary, null, null);
        EditText message = field("Текст сообщения", MessageTemplates.defaultReminder(active, ClubSettingsActions.get(this).clubName));
        content.addView(message);

        Button templates = actionButton("Выбрать шаблон", false);
        templates.setOnClickListener(v -> {
            String[] names = {"Напоминание о вылете","Изменение данных вылета","Просьба подтвердить участие"};
            new AlertDialog.Builder(this).setTitle("Шаблоны сообщения").setItems(names,(dialog,which) -> {
                Flight current = FlightRepository.getActive(this);
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
        preview.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Предпросмотр")
            .setMessage(message.getText().toString()).setPositiveButton("Закрыть",null).show());
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
            send.putExtra(Intent.EXTRA_TEXT,body);
            try {
                startActivity(Intent.createChooser(send,"Отправить сообщение"));
                MessageRepository.recordShareChooserOpened(this, flightId, body);
                EventLogRepository.record(this,"MSG-01","share_chooser_opened","delivery_unconfirmed",flightId);
            } catch (Exception e) {
                Toast.makeText(this,"Нет приложения для отправки",Toast.LENGTH_SHORT).show();
            }
        });
        content.addView(share, lp(-1,dp(48)));
        note("История хранит черновики и факт открытия системного меню. Доставка получателю не подтверждается.");
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
        String selectedIso = CalendarActions.selectedDate(this, draftDateIso());
        if (selectedIso.isEmpty()) selectedIso = draftDateIso();
        try {
            java.util.Date selected=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).parse(selectedIso);
            if(selected!=null) flightDate.setTime(selected);
        } catch(Exception ignored) {}
        card("Календарь клуба", "Выбранная дата: " + FlightDateUtils.toDisplay(selectedIso), null, null);
        Button choose = actionButton("Выбрать дату", true);
        choose.setOnClickListener(v -> new DatePickerDialog(this, (view,y,m,d) -> {
            flightDate.set(y,m,d);
            String iso=draftDateIso();
            if (!CalendarActions.selectDate(this, iso)) {
                Toast.makeText(this, "Не удалось сохранить выбранную дату", Toast.LENGTH_SHORT).show();
                return;
            }
            render();
        }, flightDate.get(Calendar.YEAR), flightDate.get(Calendar.MONTH), flightDate.get(Calendar.DAY_OF_MONTH)).show());
        content.addView(choose, lp(-1,dp(48)));
        java.util.List<Flight> flights = CalendarActions.flightsOnDate(this, selectedIso);
        int matches = 0;
        for (Flight flight : flights) {
            matches++;
            boolean active = flight.id.equals(FlightRepository.getActiveId(this));
                card(active ? "Активный вылет" : "Вылет",
                    (flight.isDemo ? "ДЕМО · " : "") + flight.title + "\n" + flight.date + " • " + flight.time + "\n" + flight.place,
                    active ? "Открыть вылеты" : "Сделать активным", () -> {
                        if (!active) {
                            if (!FlightAdminActions.activate(this, flight.id)) {
                            Toast.makeText(this, "Не удалось выбрать вылет.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                            loadDraftFromActiveFlight();
                            WidgetRefreshManager.refresh(this, "ru.aeroclub.informer.FlightWidgetProvider");
                        }
                        section = "Вылеты";
                        render();
                    });
        }
        if (matches == 0) note("На выбранную дату в локальном списке вылетов событий нет.");
        note("Календарь сравнивает полные даты; данные остаются локальными.");
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
        boolean notificationsEnabled = FlightReminderScheduler.notificationsEnabled(this);
        card("Локальные напоминания", notificationsEnabled
            ? "Состояние: разрешены. Для будущих вылетов ставится напоминание за 1 час."
            : "Состояние: уведомления запрещены в настройках Android. Разрешите их для приложения.",
            notificationsEnabled ? "Обновить напоминания" : "Открыть настройки приложения", () -> {
                if (notificationsEnabled) {
                    FlightReminderScheduler.syncAll(this);
                    Toast.makeText(this,"Расписание локальных напоминаний обновлено",Toast.LENGTH_SHORT).show();
                } else {
                    refreshOnResume = true;
                    try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + getPackageName()))); }
                    catch (Exception e) { Toast.makeText(this,"Откройте настройки приложения вручную",Toast.LENGTH_SHORT).show(); }
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
        note("Журнал хранит технические события без имён участников и текста сообщений.");
    }

        private void addParticipant() {
        EditText input = field("Имя участника", "");
        new AlertDialog.Builder(this).setTitle("Новый участник").setView(input)
            .setNegativeButton("Отмена", null).setPositiveButton("Добавить", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(this, "Введите имя участника", Toast.LENGTH_SHORT).show();
                    return;
                }
                Participant created = ParticipantAdminActions.add(this, name);
                if (created == null) {
                    Toast.makeText(this, "Имя пустое или такой участник уже есть", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(this, "Участник добавлен", Toast.LENGTH_SHORT).show();
                WidgetRefreshManager.refresh(this, "ru.aeroclub.informer.FlightWidgetProvider");
                render();
            }).show();
    }

    private void shareParticipantList() {
        Flight active = FlightRepository.getActive(this);
        StringBuilder report = new StringBuilder("Аэроклуб — участники: ");
        if (active != null) report.append(active.isDemo ? "ДЕМО · " : "").append(active.title).append("\n").append(active.date)
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
        try { startActivity(Intent.createChooser(send, "Поделиться списком участников")); }
        catch (Exception e) { Toast.makeText(this,"Нет приложения для отправки",Toast.LENGTH_SHORT).show(); }
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
                            WidgetRefreshManager.refresh(this, "ru.aeroclub.informer.FlightWidgetProvider");
                        }
                        render();
                    }).show();
                } else if (action == 1) {
                    EditText name = field("Имя участника", participant.displayName);
                    new AlertDialog.Builder(this).setTitle("Переименовать участника").setView(name)
                        .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d, which) -> {
                            if (!ParticipantAdminActions.rename(this, participant.id, name.getText().toString())) {
                                Toast.makeText(this,"Имя пустое или уже используется",Toast.LENGTH_SHORT).show();
                            } else {
                                WidgetRefreshManager.refresh(this, "ru.aeroclub.informer.FlightWidgetProvider");
                            }
                            render();
                        }).show();
                } else {
                    new AlertDialog.Builder(this).setTitle("Удалить участника?")
                        .setMessage("Будут удалены запись участника и его локальные статусы.")
                        .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d, which) -> {
                            if (!ParticipantAdminActions.delete(this, participant.id)) {
                                Toast.makeText(this, "Участник уже отсутствует.", Toast.LENGTH_SHORT).show();
                            } else {
                                WidgetRefreshManager.refresh(this, "ru.aeroclub.informer.FlightWidgetProvider");
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
            if(click!=null) b.setOnClickListener(v->click.run());
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
        b.setBackground(round(primary?BLUE:Color.WHITE,12,primary?BLUE:Color.rgb(220,228,236)));
        return b;
    }

    private Button smallButton(String label) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(12);
        b.setMinHeight(dp(40)); b.setMinimumHeight(dp(40)); b.setPadding(dp(8),0,dp(8),0);
        return b;
    }

    private String displayFlightTitle(Flight flight) {
        return flight == null ? "Ближайший вылет" : (flight.isDemo ? "ДЕМО · " : "") + flight.title;
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
