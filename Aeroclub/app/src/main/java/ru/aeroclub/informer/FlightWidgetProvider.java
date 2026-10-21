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

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) render(context, manager, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (GOING.equals(action) || NOT_GOING.equals(action)) {
            String status = GOING.equals(action) ? "Вы подтвердили участие" : "Вы не участвуете";
            context.getSharedPreferences(PREFS, 0).edit().putString("status", status).apply();
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            for (int id : manager.getAppWidgetIds(new ComponentName(context, FlightWidgetProvider.class))) {
                render(context, manager, id);
            }
        }
    }

    private static void render(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.flight_widget);
        String status = c.getSharedPreferences(PREFS, 0).getString("status",
            "Ответьте, будете ли вы участвовать");
        v.setTextViewText(R.id.flight_status, status);
        android.content.SharedPreferences prefs = c.getSharedPreferences(PREFS, 0);
        String title = prefs.getString("flight_title", "Полёт выходного дня");
        String date = prefs.getString("flight_date", "Вс, 25 октября");
        String time = prefs.getString("flight_time", "10:30");
        String place = prefs.getString("flight_place", "аэродром «Северный»");
        v.setTextViewText(R.id.flight_title, title);
        v.setTextViewText(R.id.flight_time, date + "  •  " + time);
        v.setTextViewText(R.id.flight_place, "Место встречи: " + place);

        bindBroadcast(c, v, R.id.btn_going, GOING, 1);
        bindBroadcast(c, v, R.id.btn_not_going, NOT_GOING, 2);

        Intent map = new Intent(Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=" + Uri.encode(c.getSharedPreferences(PREFS, 0)
                .getString("flight_place", "аэродром Северный"))));
        PendingIntent mapPending = PendingIntent.getActivity(c, 3, map,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.btn_route, mapPending);

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, "Аэроклуб — " +
            prefs.getString("flight_title", "ближайший вылет") + ": " +
            prefs.getString("flight_date", "дата уточняется") + ", " +
            prefs.getString("flight_time", "время уточняется") +
            ". Место встречи: " + prefs.getString("flight_place", "уточняется") + ".");
        Intent chooser = Intent.createChooser(share, "Отправить информацию о полёте");
        PendingIntent sharePending = PendingIntent.getActivity(c, 4, chooser,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.btn_telegram, sharePending);

        m.updateAppWidget(id, v);
    }

    private static void bindBroadcast(Context c, RemoteViews v, int viewId, String action, int request) {
        Intent i = new Intent(c, FlightWidgetProvider.class).setAction(action);
        PendingIntent p = PendingIntent.getBroadcast(c, request, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(viewId, p);
    }
}
