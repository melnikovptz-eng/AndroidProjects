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
    private static final int BG = Color.rgb(245, 247, 250);
    private static final int NAVY = Color.rgb(23, 43, 77);
    private static final int MUTED = Color.rgb(82, 100, 119);
    private static final int BLUE = Color.rgb(25, 94, 168);
    private static final int GREEN = Color.rgb(19, 117, 71);
    private LinearLayout root, content;
    private String section = "Обзор";
    private final Calendar flightDate = Calendar.getInstance();
    private String draftTitle = "Полёт выходного дня";
    private String draftPlace = "Аэродром «Северный»";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(12), dp(16), dp(8));
        android.content.SharedPreferences prefs = getSharedPreferences("flight_informer", 0);
        draftTitle = prefs.getString("flight_title", draftTitle);
        draftPlace = prefs.getString("flight_place", draftPlace);
        setContentView(root);
        render();
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
        back.setOnClickListener(v -> { startActivity(new Intent(this, MainActivity.class)); finish(); });
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
        card("Ближайший вылет", "Полёт выходного дня\n" + dateLabel() + " • 10:30\nМесто встречи: " + draftPlace,
             "Открыть вылеты", () -> { section = "Вылеты"; render(); });
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stat(stats, "12", "участников");
        stat(stats, "9", "едут");
        stat(stats, "3", "без ответа");
        content.addView(stats);
        card("Участники", "Список подтверждений и контактов участников полёта.",
             "Управлять участниками", () -> { section = "Участники"; render(); });
        card("Сообщения", "Подготовка объявления для группы и статусы отправки.",
             "Открыть сообщения", () -> { section = "Сообщения"; render(); });
        card("Календарь", "Даты вылетов и предстоящие события клуба.",
             "Открыть календарь", () -> { section = "Календарь"; render(); });
        card("Сервис", "Настройки клуба и уведомления.",
             "Настройки", () -> { section = "Настройки"; render(); });
        Button notifications = actionButton("Уведомления", false);
        notifications.setOnClickListener(v -> { section = "Уведомления"; render(); });
        content.addView(notifications, lp(-1, dp(48)));
        note("Демонстрационные данные. Сервер и рассылка пока не подключены.");
    }

    private void showFlights() {
        card("Активный вылет", draftTitle + "\n" + dateLabel() + " • " + timeLabel() +
                "\nМесто встречи: " + draftPlace, "Редактировать вылет", this::editFlight);
        card("Статус публикации", "Черновик хранится на этом устройстве. Публикация для всех участников требует подключения серверного API.",
                "Изменить данные вылета", this::editFlight);
        Button create = actionButton("＋  Создать вылет", true);
        create.setOnClickListener(v -> editFlight());
        content.addView(create, lp(-1, dp(50)));
    }

    private void editFlight() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(4), dp(4), dp(4));
        EditText title = field("Название вылета", draftTitle);
        EditText place = field("Место встречи", draftPlace);
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
        new AlertDialog.Builder(this).setTitle("Данные вылета").setView(form)
            .setNegativeButton("Отмена", null).setPositiveButton("Сохранить локально", (d,w) -> {
                draftTitle = title.getText().toString().trim();
                draftPlace = place.getText().toString().trim();
                if (draftTitle.isEmpty()) draftTitle = "Новый вылет";
                if (draftPlace.isEmpty()) draftPlace = "Место встречи не указано";
                getSharedPreferences("flight_informer",0).edit()
                    .putString("flight_title", draftTitle).putString("flight_place", draftPlace)
                    .putString("flight_date", draftDateLabel()).putString("flight_time", draftTimeLabel()).apply();
                Toast.makeText(this,"Сохранено на этом устройстве",Toast.LENGTH_SHORT).show();
                render();
            }).show();
    }

    private void showParticipants() {
        card("Сводка", "Всего: 12  •  Подтвердили: 9  •  Не ответили: 3", null, null);
        participant("Александр Иванов", "Подтвердил участие", GREEN);
        participant("Мария Смирнова", "Подтвердила участие", GREEN);
        participant("Дмитрий Петров", "Ожидается ответ", MUTED);
        participant("Елена Соколова", "Не участвует", Color.rgb(170, 65, 55));
        participant("Ещё 8 участников", "Демо-список; реальные контакты не подключены", MUTED);
        Button export = actionButton("Экспорт списка", false);
        export.setOnClickListener(v -> Toast.makeText(this,"Экспорт станет доступен после подключения данных участников",Toast.LENGTH_LONG).show());
        content.addView(export, lp(-1,dp(48)));
    }

    private void showMessages() {
        card("Объявление о вылете", "Полёт выходного дня\n" + dateLabel() + " • " + timeLabel() +
             "\nМесто встречи: " + draftPlace, null, null);
        EditText message = field("Текст сообщения", "Напоминаем о ближайшем вылете. Подтвердите, пожалуйста, участие.");
        content.addView(message);
        Button preview = actionButton("Предпросмотр сообщения", false);
        preview.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Предпросмотр")
            .setMessage(message.getText().toString()).setPositiveButton("Закрыть",null).show());
        content.addView(preview, lp(-1,dp(48)));
        Button share = actionButton("Поделиться…", true);
        share.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,message.getText().toString());
            startActivity(Intent.createChooser(send,"Отправить сообщение"));
        });
        content.addView(share, lp(-1,dp(48)));
        note("Отправка всем участникам и Telegram-бот пока не подключены.");
    }

    private void showCalendar() {
        card("Календарь клуба", "Выбранная дата: " + dateLabel() + "\nБлижайшее событие: " + draftTitle, null, null);
        Button choose = actionButton("Выбрать дату", true);
        choose.setOnClickListener(v -> new DatePickerDialog(this, (view,y,m,d) -> {
            flightDate.set(y,m,d); render();
        }, flightDate.get(Calendar.YEAR), flightDate.get(Calendar.MONTH), flightDate.get(Calendar.DAY_OF_MONTH)).show());
        content.addView(choose, lp(-1,dp(48)));
        card("События", draftTitle + " • " + timeLabel() + "\n" + draftPlace, "Открыть вылет", () -> { section="Вылеты"; render(); });
    }

    private void showSettings() {
        card("Профиль клуба", "Название: Аэроклуб\nМесто встречи по умолчанию: " + draftPlace, "Изменить место встречи", () -> {
            EditText e = field("Место встречи", draftPlace);
            new AlertDialog.Builder(this).setTitle("Настройки клуба").setView(e)
                .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w) -> {
                    draftPlace=e.getText().toString().trim();
                    if(draftPlace.isEmpty()) draftPlace="Не указано";
                    getSharedPreferences("flight_informer",0).edit().putString("flight_place",draftPlace).apply(); render();
                }).show();
        });
        card("Уведомления", "Локальные напоминания: не настроены\nPush-уведомления: требуется Firebase и серверный ключ.",
                "Настроить уведомления", () -> { section="Уведомления"; render(); });
        card("Доступ администратора", "Вход и роли пользователей не реализованы. Не публикуйте конфиденциальные данные в этом прототипе.", null, null);
    }

    private void showNotifications() {
        card("Напоминание о вылете", "Можно подготовить текст напоминания и поделиться им через приложения на телефоне.", "Подготовить сообщение", () -> { section="Сообщения"; render(); });
        card("Push-уведомления", "FCM не настроен: отсутствует конфигурация Firebase и сервер отправки. Экран показывает состояние, а не имитирует успешную рассылку.", null, null);
        card("Статус", "Локальный прототип • сервер не подключён", null, null);
    }

    private void participant(String name, String state, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14),dp(12),dp(14),dp(12));
        row.setBackground(round(Color.WHITE,14,Color.rgb(220,228,236)));
        TextView n = text(name,15,NAVY,true);
        TextView s = text(state,13,color,false);
        LinearLayout.LayoutParams sp=lp(-1,-2); sp.topMargin=dp(4);
        row.addView(n); row.addView(s,sp);
        LinearLayout.LayoutParams p=lp(-1,-2); p.bottomMargin=dp(8);
        content.addView(row,p);
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
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1),stroke); return d;
    }
    private LinearLayout.LayoutParams lp(int w,int h) { return new LinearLayout.LayoutParams(w,h); }
    private int dp(int v) { return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private String dateLabel() {
        String stored=getSharedPreferences("flight_informer",0).getString("flight_date",null);
        return stored != null ? stored : draftDateLabel();
    }
    private String timeLabel() {
        String stored=getSharedPreferences("flight_informer",0).getString("flight_time",null);
        return stored != null ? stored : draftTimeLabel();
    }
    private String draftDateLabel() {
        return new SimpleDateFormat("EEE, d MMM",Locale.forLanguageTag("ru")).format(flightDate.getTime());
    }
    private String draftTimeLabel() {
        return new SimpleDateFormat("HH:mm",Locale.getDefault()).format(flightDate.getTime());
    }
}
