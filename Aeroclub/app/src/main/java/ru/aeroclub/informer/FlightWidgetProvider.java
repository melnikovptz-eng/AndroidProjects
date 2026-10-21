package ru.aeroclub.informer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class FlightWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "flight_informer";
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) render(context, manager, id);
    }
    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (action == null) return;
        if (action.equals("ru.aeroclub.informer.GOING") || action.equals("ru.aeroclub.informer.NOT_GOING")) {
            context.getSharedPreferences(PREFS, 0).edit().putString("status",
                action.endsWith("GOING") && !action.endsWith("NOT_GOING") ? "Вы подтвердили участие" : "Вы не участвуете").apply();
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : manager.getAppWidgetIds(new android.content.ComponentName(context, FlightWidgetProvider.class))) render(context, manager, id);
    }
    private static void render(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.flight_widget);
        String status = c.getSharedPreferences(PREFS, 0).getString("status", "Ответьте, будете ли вы участвовать");
        v.setTextViewText(R.id.flight_status, status);
        v.setTextViewText(R.id.flight_title, "Полёт выходного дня");
        v.setTextViewText(R.id.flight_time, "Сб, 25 октября  •  10:30");
        v.setTextViewText(R.id.flight_place, "Место встречи: аэродром «Северный»");
        bind(c, v, R.id.btn_going, "ru.aeroclub.informer.GOING", 1);
        bind(c, v, R.id.btn_not_going, "ru.aeroclub.informer.NOT_GOING", 2);
        bind(c, v, R.id.btn_route, "ru.aeroclub.informer.ROUTE", 3);
        m.updateAppWidget(id, v);
    }
    private static void bind(Context c, RemoteViews v, int viewId, String action, int request) {
        Intent i = new Intent(c, FlightWidgetProvider.class).setAction(action);
        PendingIntent p = PendingIntent.getBroadcast(c, request, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(viewId, p);
    }
}
