package ru.aeroclub.informer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.Toast;

public class FlightWidgetProvider extends AppWidgetProvider {
    private static final String GOING = "ru.aeroclub.informer.GOING";
    private static final String NOT_GOING = "ru.aeroclub.informer.NOT_GOING";
    private static final String ROUTE = "ru.aeroclub.informer.ROUTE";
    private static final String SHARE = "ru.aeroclub.informer.SHARE";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        FlightRepository.ensureInitialized(context);
        for (int id : ids) render(context, manager, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) return;
        String action = intent.getAction();
        FlightRepository.ensureInitialized(context);
        String flightId = FlightRepository.getActiveId(context);

        if (GOING.equals(action) || NOT_GOING.equals(action)) {
            String status = GOING.equals(action) ? "Вы подтвердили участие" : "Вы не участвуете";
            AttendanceRepository.setResponse(context, flightId, status);
            EventLogRepository.record(context,"ATT-01","response_update","success",flightId);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            for (int id : manager.getAppWidgetIds(new ComponentName(context, FlightWidgetProvider.class))) {
                render(context, manager, id);
            }
        } else if (ROUTE.equals(action)) {
            Flight active = FlightRepository.getActive(context);
            String place = active == null ? "" : active.place;
            if (place.trim().isEmpty()) {
                Toast.makeText(context,"Место встречи не указано",Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                Intent map = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)));
                map.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(map);
                EventLogRepository.record(context,"MAP-01","open_route","intent_started",flightId);
            } catch (Exception e) {
                Toast.makeText(context,"Не найдено приложение для карт",Toast.LENGTH_SHORT).show();
                EventLogRepository.record(context,"MAP-01","open_route","no_maps_app",flightId);
            }
        } else if (SHARE.equals(action)) {
            Flight active = FlightRepository.getActive(context);
            if (active == null) return;
            String body = "Аэроклуб — " + active.title + ": " + active.date + ", " + active.time +
                ". Место встречи: " + active.place + ".";
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT,body);
                Intent chooser = Intent.createChooser(send,"Отправить информацию о полёте");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
                MessageRepository.recordShareChooserOpened(context,active.id,body);
                EventLogRepository.record(context,"SHR-01","share_chooser_opened","delivery_unconfirmed",active.id);
            } catch (Exception e) {
                Toast.makeText(context,"Нет приложения для отправки",Toast.LENGTH_SHORT).show();
                EventLogRepository.record(context,"SHR-01","share_chooser_opened","no_share_app",active.id);
            }
        }
    }

    private static void render(Context c, AppWidgetManager manager, int id) {
        FlightRepository.ensureInitialized(c);
        Flight active = FlightRepository.getActive(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.flight_widget);
        String status = AttendanceRepository.getResponse(c, FlightRepository.getActiveId(c));
        v.setTextViewText(R.id.flight_status, status);
        v.setTextViewText(R.id.flight_title, active == null ? "Ближайший вылет" : active.title);
        v.setTextViewText(R.id.flight_time, active == null ? "Дата и время уточняются" : active.date + "  •  " + active.time);
        v.setTextViewText(R.id.flight_place, "Место встречи: " +
            (active == null ? "не указано" : active.place));

        bindBroadcast(c, v, R.id.btn_going, GOING, 1);
        bindBroadcast(c, v, R.id.btn_not_going, NOT_GOING, 2);
        bindBroadcast(c, v, R.id.btn_route, ROUTE, 3);
        bindBroadcast(c, v, R.id.btn_telegram, SHARE, 4);
        manager.updateAppWidget(id, v);
    }

    private static void bindBroadcast(Context c, RemoteViews v, int viewId, String action, int request) {
        Intent intent = new Intent(c, FlightWidgetProvider.class).setAction(action);
        PendingIntent pending = PendingIntent.getBroadcast(c, request, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(viewId, pending);
    }
}
