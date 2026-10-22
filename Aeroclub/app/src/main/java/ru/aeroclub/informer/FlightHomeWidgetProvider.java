package ru.aeroclub.informer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.Toast;
import java.util.List;

/** W01: nearest upcoming flight and four actions. */
public final class FlightHomeWidgetProvider extends AppWidgetProvider {
    private static final String P = "ru.aeroclub.informer.widget.";
    public static final String GOING = P + "GOING", NOT_GOING = P + "NOT_GOING", ROUTE = P + "ROUTE", TELEGRAM = P + "TELEGRAM", REFRESH = P + "REFRESH";
    private static final String FLIGHT_ID = "flight_id";

    public static void refresh(Context c) {
        if (c == null) return;
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, FlightHomeWidgetProvider.class));
        if (ids != null && ids.length > 0) new FlightHomeWidgetProvider().onUpdate(c, m, ids);
    }

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        FlightRepository.ensureInitialized(c);
        FlightRepository.syncFromDownload(c);
        Flight f = nearest(c);
        for (int id : ids) render(c, m, id, f);
    }

    @Override public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (i == null || i.getAction() == null || !i.getAction().startsWith(P)) return;
        if (REFRESH.equals(i.getAction())) { refresh(c); return; }
        Flight f = FlightRepository.getById(c, i.getStringExtra(FLIGHT_ID));
        if (f == null) {
            Toast.makeText(c, "Мероприятие не найдено. Откройте календарь.", Toast.LENGTH_SHORT).show();
            refresh(c); return;
        }
        String a = i.getAction();
        if (GOING.equals(a) || NOT_GOING.equals(a)) {
            boolean going = GOING.equals(a);
            String participant = AttendanceRepository.getSelectedParticipantId(c);
            boolean ok = participant == null ? AttendanceRepository.setResponse(c, f.id, going ? AttendanceRepository.STATUS_GOING : AttendanceRepository.STATUS_NOT_GOING) : AttendanceRepository.setResponse(c, participant, f.id, going ? AttendanceRepository.STATUS_GOING : AttendanceRepository.STATUS_NOT_GOING);
            Toast.makeText(c, ok ? (going ? "Ответ сохранён: Я поеду" : "Ответ сохранён: Я не поеду") : "Не удалось сохранить ответ", Toast.LENGTH_SHORT).show();
            refresh(c);
        } else if (ROUTE.equals(a)) FlightActionLauncher.openRoute(c, f);
        else if (TELEGRAM.equals(a)) FlightActionLauncher.shareFlight(c, f);
    }

    private static Flight nearest(Context c) {
        long now = System.currentTimeMillis(), earliest = Long.MAX_VALUE;
        Flight best = null;
        List<Flight> list = FlightRepository.getFlights(c);
        for (Flight f : list) {
            if (f == null || f.isDemo) continue;
            long t = FlightDateUtils.toMillis(f.dateIso, f.time);
            if (t > now && t < earliest) { best = f; earliest = t; }
        }
        return best;
    }

    private static void render(Context c, AppWidgetManager m, int id, Flight f) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_flight_home);
        v.setTextViewText(R.id.widget_club_name, ClubSettingsRepository.get(c).clubName.toUpperCase(java.util.Locale.getDefault()));
        if (f == null) {
            v.setTextViewText(R.id.widget_flight_title, "Нет запланированных вылетов");
            v.setTextViewText(R.id.widget_flight_datetime, "Добавьте мероприятие в календаре");
            v.setTextViewText(R.id.widget_flight_place, "Аэроклуб • информер");
            v.setTextViewText(R.id.widget_flight_status, "ОЖИДАЕТСЯ МЕРОПРИЯТИЕ");
        } else {
            v.setTextViewText(R.id.widget_flight_title, FlightPresentation.title(f));
            v.setTextViewText(R.id.widget_flight_datetime, f.date + " • " + f.time);
            v.setTextViewText(R.id.widget_flight_place, "Место: " + (f.place == null || f.place.trim().isEmpty() ? "не указано" : f.place));
            String participant = AttendanceRepository.getSelectedParticipantId(c);
            String response = participant == null ? AttendanceRepository.getResponse(c, f.id) : AttendanceRepository.getResponse(c, participant, f.id);
            v.setTextViewText(R.id.widget_flight_status, "УЧАСТИЕ: " + response.toUpperCase(java.util.Locale.getDefault()));
        }
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent op = PendingIntent.getActivity(c, 8100 + id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_header, op);
        v.setOnClickPendingIntent(R.id.widget_flight_title, op);
        bind(c, v, id, R.id.widget_going, GOING, f);
        bind(c, v, id, R.id.widget_not_going, NOT_GOING, f);
        bind(c, v, id, R.id.widget_route, ROUTE, f);
        bind(c, v, id, R.id.widget_telegram, TELEGRAM, f);
        m.updateAppWidget(id, v);
    }

    private static void bind(Context c, RemoteViews v, int wid, int view, String action, Flight f) {
        Intent i = new Intent(c, FlightHomeWidgetProvider.class).setAction(action);
        if (f != null) i.putExtra(FLIGHT_ID, f.id);
        PendingIntent p = PendingIntent.getBroadcast(c, (action.hashCode() & 0x00ffffff) + wid, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(view, p);
    }
}
