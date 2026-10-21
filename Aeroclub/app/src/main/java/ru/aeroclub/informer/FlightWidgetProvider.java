package ru.aeroclub.informer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

public class FlightWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "flight_informer";
    private static final String GOING = "ru.aeroclub.informer.GOING";
    private static final String NOT_GOING = "ru.aeroclub.informer.NOT_GOING";
    private static final String ROUTE = "ru.aeroclub.informer.ROUTE";
    private static final String SHARE = "ru.aeroclub.informer.SHARE";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) render(context, manager, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (GOING.equals(action) || NOT_GOING.equals(action)) {
            FlightRepository.ensureInitialized(context);
            String status = GOING.equals(action) ? "Вы подтвердили участие" : "Вы не участвуете";
            String flightId = FlightRepository.getActiveId(context);
            AttendanceRepository.setResponse(context, flightId, status);
            EventLogRepository.record(context,"ATT-01","response_update","success",flightId);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            for (int id : manager.getAppWidgetIds(new ComponentName(context, FlightWidgetProvider.class))) {
                render(context, manager, id);
            }
        } else if (ROUTE.equals(action)) {
            Flight active = FlightRepository.getActive(context);
            String place = active == null ? "" : active.place;
            try {
                bindBroadcast(c, v, R.id.btn_route, ROUTE, 3);
        bindBroadcast(c, v, R.id.btn_telegram, SHARE, 4);

        m.updateAppWidget(id, v);
    }

    private static void bindBroadcast(Context c, RemoteViews v, int viewId, String action, int request) {
        Intent i = new Intent(c, FlightWidgetProvider.class).setAction(action);
        PendingIntent p = PendingIntent.getBroadcast(c, request, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(viewId, p);
    }
}
