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
import android.widget.Toast;
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
    private final Runnable flightSyncPoll = new Runnable() { public void run() { if (isFinishing()) return; if (FlightRepository.syncFromDownload(MainActivity.this)) renderAllFlightsScreen(); flightSyncHandler.postDelayed(this, 5000L); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        screenOpenOperation = ModuleDebugLog.startOperation(this, "UI-01", "calendar_screen_open", "screen_created");
        getWindow().setStatusBarColor(AeroclubUi.BACKGROUND);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        FlightRepository.ensureInitialized(this);
        FlightReminderScheduler.syncAll(this);
        renderAllFlightsScreen();
        handleWidgetRouteIntent(getIntent());
        ensureLegacyDownloadsPermission();
        if (screenOpenOperation != null) {
            screenOpenOperation.finish("success", "calendar_ready");
            screenOpenOperation = null;
        }
    }

    /** S12 participant home: upcoming flights list, separate from the S01 calendar. */
    private void renderAllFlightsScreen() { renderFlightsScreen(false); }

    private void renderCompetitionsScreen() { renderFlightsScreen(true); }

    private void renderFlightsScreen(boolean competitions) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AeroclubUi.BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(14), dp(14), dp(10));
        header.setBackgroundColor(Color.WHITE);
        TextView brand = text("✈", 30, AeroclubUi.BLUE, true);
        header.addView(brand, lp(dp(36), dp(42)));
        TextView brandName = text("Аэроклуб", 26, Color.rgb(31, 41, 55), true);
        brandName.setSingleLine(true);
        brandName.setIncludeFontPadding(false);
        header.addView(brandName, lp(-2, -2));
        Button create = button("＋  Создать полёт");
        create.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, AeroclubUi.rootRelativeSp(this, 14));
        create.setTextColor(Color.WHITE);
        create.setAllCaps(false);
        create.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        create.setPadding(dp(10), dp(8), dp(10), dp(8));
        create.setBackground(AeroclubUi.rounded(this, Color.rgb(0, 102, 255), 32, Color.rgb(0, 102, 255)));
        header.addView(create, lp(-2, dp(44)));
        create.setOnClickListener(v -> {
            Intent admin = new Intent().setComponent(new android.content.ComponentName(
                getPackageName(), "ru.aeroclub.informer.AdminActivity"));
            admin.putExtra("initial_section", "Вылеты");
            startActivity(admin);
        });
        root.addView(header, lp(-1, -2));
        header.setMinimumHeight(dp(76));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(18), dp(6), dp(18), dp(5));
        tabs.setBackgroundColor(Color.WHITE);
        TextView allTab = text("Все полёты", 18, competitions ? Color.rgb(156, 163, 175) : Color.rgb(0, 102, 255), !competitions);
        TextView competitionsTab = text("Соревнования", 18, competitions ? Color.rgb(0, 102, 255) : Color.rgb(156, 163, 175), competitions);
        allTab.setGravity(android.view.Gravity.CENTER);
        competitionsTab.setGravity(android.view.Gravity.CENTER);
        tabs.addView(allTab, new LinearLayout.LayoutParams(0, dp(52), 1));
        tabs.addView(competitionsTab, new LinearLayout.LayoutParams(0, dp(52), 1));
        allTab.setOnClickListener(v -> renderAllFlightsScreen());
        competitionsTab.setOnClickListener(v -> renderCompetitionsScreen());
        root.addView(tabs, lp(-1, -2));
        View tabLine = new View(this);
        tabLine.setBackgroundColor(AeroclubUi.BLUE);
        LinearLayout.LayoutParams lineParams = lp(Math.max(dp(150), getResources().getDisplayMetrics().widthPixels / 2 - dp(18)), dp(3));
        lineParams.leftMargin = competitions ? getResources().getDisplayMetrics().widthPixels / 2 + dp(2) : dp(20);
        root.addView(tabLine, lineParams);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(24), dp(20), dp(24), dp(24));
        long nowMillis = System.currentTimeMillis();
        java.util.List<Flight> flights = new java.util.ArrayList<>();
        for (Flight candidate : FlightRepository.getFlights(this)) {
            if (!candidate.published && !candidate.isDemo) continue;
            long departureMillis = FlightDateUtils.toMillis(candidate.dateIso, candidate.time);
            if (departureMillis >= nowMillis) flights.add(candidate);
        }
        java.util.Collections.sort(flights, (a,b) -> {
            String ad = a.dateIso == null ? "" : a.dateIso;
            String bd = b.dateIso == null ? "" : b.dateIso;
            int dateOrder = ad.compareTo(bd);
            return dateOrder != 0 ? dateOrder : String.valueOf(a.time).compareTo(String.valueOf(b.time));
        });
        int visible = 0;
        for (Flight flight : flights) {
            if (competitions) continue; // No competition data model/API exists yet; never mislabel ordinary flights.
            if (!flight.published && !flight.isDemo) continue;
            visible++;
            String response = AttendanceRepository.getResponse(this, flight.id);
            boolean going = AttendanceRepository.STATUS_GOING.equals(response);
            boolean notGoing = AttendanceRepository.STATUS_NOT_GOING.equals(response);
            int accent = going ? Color.rgb(52, 190, 139) :
                notGoing ? Color.rgb(244, 91, 119) : Color.rgb(112, 132, 160);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.setPadding(0, 0, dp(12), 0);
            card.setBackground(AeroclubUi.rounded(this, Color.WHITE, 16,
                visible == 1 ? Color.rgb(255, 255, 255) : Color.rgb(255, 255, 255)));
            card.setElevation(dp(2));
            LinearLayout.LayoutParams cardParams = lp(-1, -2);
            cardParams.bottomMargin = dp(14);
            list.addView(card, cardParams);

            View accentBar = new View(this);
            accentBar.setBackgroundColor(visible == 1 ? AeroclubUi.BLUE : accent);
            card.addView(accentBar, lp(dp(5), visible == 1 ? dp(132) : dp(118)));

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(dp(15), dp(14), dp(8), dp(14));
            card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

            if (visible == 1) {
                TextView nearest = text("Ближайший полёт", 20, Color.rgb(31, 41, 55), true);
                LinearLayout.LayoutParams nearestParams = lp(-1, -2);
                nearestParams.bottomMargin = dp(5);
                details.addView(nearest, nearestParams);
            }

            LinearLayout dateRow = new LinearLayout(this);
            dateRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView date = text(formatFlightDate(flight), 18, Color.rgb(31, 41, 55), true);
            dateRow.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
            TextView statusText = text((going ? "♟  Едут" : notGoing ? "⊘  Не едут" : "ⓘ  Нет ответа"),
                12, going ? Color.rgb(13, 143, 91) : notGoing ? Color.rgb(221, 53, 83) : Color.rgb(76, 96, 125), true);
            statusText.setGravity(android.view.Gravity.CENTER);
            statusText.setPadding(dp(10), dp(7), dp(10), dp(7));
            statusText.setBackground(AeroclubUi.rounded(this,
                going ? Color.rgb(224, 248, 240) : notGoing ? Color.rgb(255, 232, 238) : Color.rgb(237, 241, 247), 22,
                Color.TRANSPARENT));
            dateRow.addView(statusText, lp(-2, -2));
            details.addView(dateRow, lp(-1, -2));

            TextView time = text(flight.time == null ? "Время не указано" : flight.time, 16, Color.rgb(31, 41, 55), false);
            LinearLayout.LayoutParams timeParams = lp(-1, -2);
            timeParams.topMargin = dp(8);
            details.addView(time, timeParams);
            TextView place = text(flight.place == null ? "Место встречи не указано" : flight.place,
                14, Color.rgb(107, 114, 128), false);
            LinearLayout.LayoutParams placeParams = lp(-1, -2);
            placeParams.topMargin = dp(7);
            details.addView(place, placeParams);

            TextView demo = text(flight.isDemo ? "Демонстрационный полёт" : "", 10, MUTED, false);
            if (flight.isDemo) {
                LinearLayout.LayoutParams demoParams = lp(-1, -2);
                demoParams.topMargin = dp(5);
                details.addView(demo, demoParams);
            }
            TextView chevron = text("›", 30, Color.rgb(103, 127, 160), false);
            chevron.setGravity(android.view.Gravity.CENTER);
            card.addView(chevron, lp(dp(24), dp(56)));
            card.setOnClickListener(v -> showEventCard(flight));
            card.setFocusable(true);
            card.setContentDescription(FlightPresentation.title(flight) + ", " + flight.date + ", " +
                flight.time + ", " + flight.place + ". " + response + ". Открыть карточку.");
        }
        if (visible == 0) {
            TextView empty = text(competitions ? "Соревнования пока не опубликованы." : "Пока нет опубликованных полётов.\nНовые мероприятия появятся здесь.",
                15, MUTED, false);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(dp(24), dp(48), dp(24), dp(48));
            list.addView(empty, lp(-1, -2));
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(10));
        String[] labels = {"Главная", "Участники", "Календарь", "Настройки"};
        String[] icons = {"⌂", "♙", "▦", "⚙"};
        for (int i = 0; i < labels.length; i++) {
            final int item = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER);
            TextView icon = text(icons[i], 24, i == 0 ? Color.rgb(0,102,255) : Color.rgb(156,163,175), true);
            TextView label = text(labels[i], 12, i == 0 ? Color.rgb(0,102,255) : Color.rgb(156,163,175), i == 0);
            icon.setGravity(android.view.Gravity.CENTER);
            label.setGravity(android.view.Gravity.CENTER);
            cell.addView(icon, lp(-1, dp(25)));
            cell.addView(label, lp(-1, dp(20)));
            nav.addView(cell, new LinearLayout.LayoutParams(0, dp(100), 1));
            cell.setOnClickListener(v -> {
                if (item == 2) renderCalendarScreen();
                else if (item == 1 || item == 3) {
                    Intent admin = new Intent().setComponent(new android.content.ComponentName(
                        getPackageName(), "ru.aeroclub.informer.AdminActivity"));
                    admin.putExtra("initial_section", item == 1 ? "Участники" : "Настройки");
                    startActivity(admin);
                }
            });
        }
        root.addView(nav, lp(-1, dp(100)));
        setContentView(root);
        clubLabel = brandName;
        flightTitle = brandName;
    }

    private String formatFlightDate(Flight flight) {
        if (flight == null) return "";
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            input.setLenient(false);
            java.util.Date parsed = input.parse(flight.dateIso);
            if (parsed != null) return new SimpleDateFormat("d MMMM yyyy", new Locale("ru")).format(parsed);
        } catch (Exception ignored) {}
        return flight.date == null ? "" : flight.date;
    }

    /** Main flow: calendar -> red event day -> event card with four actions. */
    private void renderCalendarScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(AeroclubUi.BACKGROUND);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(18), 0, dp(18), 0);
        toolbar.setBackgroundColor(AeroclubUi.BLUE);
        TextView mark = text("✈", 25, Color.WHITE, true);
        toolbar.addView(mark, lp(dp(42), dp(58)));
        TextView title = text("Календарь", 19, Color.WHITE, true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(58), 1));
        page.addView(toolbar, lp(-1, dp(58)));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(18));

        LinearLayout calendarCard = new LinearLayout(this);
        calendarCard.setOrientation(LinearLayout.VERTICAL);
        calendarCard.setPadding(dp(12), dp(10), dp(12), dp(12));
        calendarCard.setBackground(AeroclubUi.rounded(this, Color.WHITE, 16, Color.rgb(229,236,246)));
        calendarCard.setElevation(dp(2));

        LinearLayout monthRow = new LinearLayout(this);
        monthRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView previous = text("‹", 29, AeroclubUi.BLUE, true);
        previous.setGravity(android.view.Gravity.CENTER);
        monthRow.addView(previous, lp(dp(44), dp(44)));
        TextView monthLabel = text("", 17, NAVY, true);
        monthLabel.setGravity(android.view.Gravity.CENTER);
        monthRow.addView(monthLabel, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView next = text("›", 29, AeroclubUi.BLUE, true);
        next.setGravity(android.view.Gravity.CENTER);
        monthRow.addView(next, lp(dp(44), dp(44)));
        calendarCard.addView(monthRow);
        Calendar month = (Calendar) displayedCalendarMonth.clone();
        month.set(Calendar.DAY_OF_MONTH, 1);
        monthLabel.setText(new SimpleDateFormat("LLLL yyyy", new Locale("ru")).format(month.getTime()));
        previous.setOnClickListener(v -> { displayedCalendarMonth.add(Calendar.MONTH, -1); renderCalendarScreen(); });
        next.setOnClickListener(v -> { displayedCalendarMonth.add(Calendar.MONTH, 1); renderCalendarScreen(); });

        LinearLayout week = new LinearLayout(this);
        String[] weekdays = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (String day : weekdays) {
            TextView cell = text(day, 11, MUTED, true);
            cell.setGravity(android.view.Gravity.CENTER);
            week.addView(cell, new LinearLayout.LayoutParams(0, dp(30), 1));
        }
        calendarCard.addView(week);

        java.util.Map<String, java.util.List<Flight>> flightsByDate = new java.util.HashMap<>();
        java.util.List<Flight> flights = new java.util.ArrayList<>();
        for (Flight flight : FlightRepository.getFlights(this)) {
            if (!flight.published && !flight.isDemo) continue;
            if (flight.dateIso == null || flight.dateIso.isEmpty()) continue;
            flights.add(flight);
            java.util.List<Flight> sameDate = flightsByDate.get(flight.dateIso);
            if (sameDate == null) { sameDate = new java.util.ArrayList<>(); flightsByDate.put(flight.dateIso, sameDate); }
            sameDate.add(flight);
        }
        int offset = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        int cells = ((offset + daysInMonth + 6) / 7) * 7;
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int base = 0; base < cells; base += 7) {
            LinearLayout row = new LinearLayout(this);
            for (int col = 0; col < 7; col++) {
                int dayNumber = base + col - offset + 1;
                TextView dayCell = text(dayNumber >= 1 && dayNumber <= daysInMonth ? String.valueOf(dayNumber) : "",
                    13, AeroclubUi.BODY, false);
                dayCell.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(0, dp(39), 1);
                dayParams.setMargins(dp(2), dp(2), dp(2), dp(2));
                row.addView(dayCell, dayParams);
                if (dayNumber < 1 || dayNumber > daysInMonth) continue;
                Calendar date = (Calendar) month.clone();
                date.set(Calendar.DAY_OF_MONTH, dayNumber);
                String iso = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(date.getTime());
                java.util.List<Flight> dayFlights = flightsByDate.get(iso);
                boolean today = iso.equals(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new java.util.Date()));
                if (dayFlights != null && !dayFlights.isEmpty()) {
                    dayCell.setTextColor(Color.WHITE);
                    dayCell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    dayCell.setBackground(AeroclubUi.rounded(this, Color.rgb(255,59,48), 12, Color.rgb(255,59,48)));
                    dayCell.setContentDescription(dayNumber + ", мероприятий: " + dayFlights.size());
                    dayCell.setOnClickListener(v -> showEventCardForDate(iso, dayFlights));
                } else {
                    dayCell.setBackground(AeroclubUi.rounded(this,
                        today ? Color.rgb(231,241,255) : Color.WHITE, 12,
                        today ? AeroclubUi.BLUE : Color.rgb(237,241,247)));
                    dayCell.setOnClickListener(v -> Toast.makeText(this, "На эту дату мероприятий нет.", Toast.LENGTH_SHORT).show());
                }
            }
            grid.addView(row, lp(-1, dp(43)));
        }
        calendarCard.addView(grid);
        TextView hint = text("Красным отмечены дни с мероприятиями. Нажмите на дату, чтобы открыть вылет.",
            11, MUTED, false);
        LinearLayout.LayoutParams hintParams = lp(-1, -2);
        hintParams.topMargin = dp(8);
        calendarCard.addView(hint, hintParams);
        body.addView(calendarCard, lp(-1, -2));

        TextView upcomingTitle = text("Предстоящие полёты", 18, NAVY, true);
        LinearLayout.LayoutParams upcomingTitleParams = lp(-1, -2);
        upcomingTitleParams.topMargin = dp(18);
        upcomingTitleParams.bottomMargin = dp(10);
        body.addView(upcomingTitle, upcomingTitleParams);
        java.util.Collections.sort(flights, (a,b) -> {
            int order = String.valueOf(a.dateIso).compareTo(String.valueOf(b.dateIso));
            return order != 0 ? order : String.valueOf(a.time).compareTo(String.valueOf(b.time));
        });
        if (flights.isEmpty()) {
            TextView empty = text("Предстоящих опубликованных полётов пока нет.", 13, MUTED, false);
            empty.setPadding(dp(4), dp(12), dp(4), dp(12));
            body.addView(empty);
        }
        for (Flight flight : flights) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackground(AeroclubUi.rounded(this, Color.WHITE, 14, Color.rgb(229,236,246)));
            card.setElevation(dp(1));
            TextView status = text(flight.isDemo ? "ДЕМО-МЕРОПРИЯТИЕ" :
                (flight.published ? "ОПУБЛИКОВАНО ЛОКАЛЬНО" : "ЧЕРНОВИК"), 10,
                flight.isDemo ? Color.rgb(191,112,24) : Color.rgb(13,143,91), true);
            card.addView(status);
            TextView date = text(formatFlightDate(flight), 16, NAVY, true);
            LinearLayout.LayoutParams dateParams = lp(-1, -2);
            dateParams.topMargin = dp(5);
            card.addView(date, dateParams);
            TextView details = text(flight.time + "  •  " +
                (flight.place == null ? "Место встречи не указано" : flight.place), 13, MUTED, false);
            LinearLayout.LayoutParams detailsParams = lp(-1, -2);
            detailsParams.topMargin = dp(5);
            card.addView(details, detailsParams);
            LinearLayout.LayoutParams cardParams = lp(-1, -2);
            cardParams.bottomMargin = dp(10);
            body.addView(card, cardParams);
            card.setOnClickListener(v -> showEventCard(flight));
        }
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(9));
        String[] labels = {"Главная", "Участники", "Календарь", "Настройки"};
        String[] icons = {"⌂", "♙", "▦", "⚙"};
        for (int i = 0; i < labels.length; i++) {
            final int item = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER);
            int color = i == 2 ? AeroclubUi.BLUE : Color.rgb(104,124,155);
            TextView icon = text(icons[i], 24, color, true);
            icon.setGravity(android.view.Gravity.CENTER);
            TextView label = text(labels[i], 12, color, i == 2);
            label.setGravity(android.view.Gravity.CENTER);
            cell.addView(icon, lp(-1, dp(30)));
            cell.addView(label, lp(-1, dp(22)));
            nav.addView(cell, new LinearLayout.LayoutParams(0, dp(100), 1));
            cell.setOnClickListener(v -> {
                if (item == 0) renderAllFlightsScreen();
                else if (item == 2) renderCalendarScreen();
                else {
                    Intent admin = new Intent().setComponent(new android.content.ComponentName(
                        getPackageName(), "ru.aeroclub.informer.AdminActivity"));
                    admin.putExtra("initial_section", item == 1 ? "Участники" : "Настройки");
                    startActivity(admin);
                }
            });
        }
        page.addView(nav, lp(-1, dp(100)));
        getWindow().setStatusBarColor(AeroclubUi.BLUE);
        getWindow().getDecorView().setSystemUiVisibility(0);
        setContentView(page);
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
        route.setOnClickListener(v -> runTimedAction("MAP-01", "event_card_route", () -> showRouteConfirmation(flight)));
        telegram.setOnClickListener(v -> runTimedAction("SHR-01", "event_card_telegram", () -> FlightActionLauncher.shareFlight(this, flight)));
        dialog.show();
    }

    private void ensureLegacyDownloadsPermission(){if(Build.VERSION.SDK_INT<29&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE},7103);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==7103){if(grantResults.length>1&&grantResults[1]==android.content.pm.PackageManager.PERMISSION_GRANTED){FlightRepository.ensureInitialized(this);FlightDownloadExchange.publish(this,LocalFilePreferences.get(this),"flights_json","active_flight_id");refreshFlightSummary();}else{android.widget.Toast.makeText(this,"Без доступа к Download автоматический файл полётов недоступен.",android.widget.Toast.LENGTH_LONG).show();ModuleDebugLog.log(this,"FLIGHT-FILE","legacy_permission","denied","downloads_unavailable");}}}
    private void refreshFlightSummary(){ FlightReminderScheduler.syncAll(this); renderAllFlightsScreen(); }

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
        handleWidgetRouteIntent(intent);
    }

    private void handleWidgetRouteIntent(Intent intent) {
        if (intent == null) return;
        String flightId = intent.getStringExtra("widget_route_flight_id");
        if (flightId == null || flightId.trim().isEmpty()) return;
        intent.removeExtra("widget_route_flight_id");
        Flight flight = FlightRepository.getById(this, flightId);
        if (flight == null) {
            android.widget.Toast.makeText(this, "Мероприятие не найдено. Обновите календарь.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        showRouteConfirmation(flight);
    }

    private void showRouteConfirmation(Flight flight) {
        if (flight == null) return;
        new AlertDialog.Builder(this)
            .setTitle("Маршрут к месту полётов")
            .setMessage("Вы выехали к месту полётов?")
            .setPositiveButton("Да, и построить маршрут", (dialog, which) -> {
                android.widget.Toast.makeText(this,
                    "Канал уведомления руководителю не настроен. Сообщение не отправлено; открываю маршрут.",
                    android.widget.Toast.LENGTH_LONG).show();
                FlightActionLauncher.openRoute(this, flight);
            })
            .setNegativeButton("Нет, и построить маршрут", (dialog, which) ->
                FlightActionLauncher.openRoute(this, flight))
            .setNeutralButton("Отмена", null)
            .show();
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
                String responseLabel = AttendanceRepository.STATUS_GOING.equals(value) ? "Я поеду" : "Я не поеду";
                android.widget.Toast.makeText(this,
                    "Сохранено: " + participant.displayName + " — " + responseLabel,
                    android.widget.Toast.LENGTH_LONG).show();
                EventLogRepository.record(this, "ATT-01", "response_update", "success",
                    "flight=" + flightId + "; participant=" + participant.id + "; status=" + value);
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
        Intent refreshWidget = new Intent("ru.aeroclub.informer.widget.REFRESH");
        refreshWidget.setComponent(new android.content.ComponentName(getPackageName(), "ru.aeroclub.informer.FlightHomeWidgetProvider"));
        sendBroadcast(refreshWidget);
        LocalFilePreferences storage = LocalFilePreferences.get(this);
        if (!storage.isStorageHealthy() && !storageWarningShown) {
            storageWarningShown = true;
            android.widget.Toast.makeText(this, storage.getStorageStatus(), android.widget.Toast.LENGTH_LONG).show();
        }
        ModuleDebugLog.log(this, "UI-01", "informer_onResume", "started", "screen_foreground");
        flightSyncHandler.removeCallbacks(flightSyncPoll);
        if (FlightRepository.syncFromDownload(this)) renderAllFlightsScreen();
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
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, AeroclubUi.rootRelativeSp(this, size));
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, AeroclubUi.rootRelativeSp(this, 14));
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
