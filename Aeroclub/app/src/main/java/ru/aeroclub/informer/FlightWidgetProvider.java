package ru.aeroclub.informer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class FlightWidgetProvider extends AppWidgetProvider {
    private static final String GOING = "ru.aeroclub.informer.GOING";
    private static final String NOT_GOING = "ru.aeroclub.informer.NOT_GOING";
    private static final String ROUTE = "ru.aeroclub.informer.ROUTE";
    private static final String SHARE = "ru.aeroclub.informer.SHARE";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        ModuleDebugLog.log(context, "WID-01", "widget_update", "started", "instances=" + ids.length);
        FlightRepository.ensureInitialized(context);
        for (int id : ids) render(context, manager, id);
        ModuleDebugLog.log(context, "WID-01", "widget_update", "completed", "instances=" + ids.length);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) return;
        String action = intent.getAction();
        ModuleDebugLog.log(context, "WID-01", "widget_action", "received", action == null ? "null_action" : action);
        FlightRepository.ensureInitialized(context);
        if (GOING.equals(action) || NOT_GOING.equals(action)) {
            Intent choose = new Intent(context, MainActivity.class);
            choose.putExtra("attendance_action", GOING.equals(action)
                ? AttendanceRepository.STATUS_GOING : AttendanceRepository.STATUS_NOT_GOING);
            choose.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(choose);
        } else if (ROUTE.equals(action)) {
            FlightActionLauncher.openRoute(context, FlightRepository.getActive(context));
        } else if (SHARE.equals(action)) {
            FlightActionLauncher.shareFlight(context, FlightRepository.getActive(context));
        }
    }

    private static void render(Context c, AppWidgetManager manager, int id) {
        FlightRepository.ensureInitialized(c);
        Flight active = FlightRepository.getActive(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.flight_widget);
        String selectedId = AttendanceRepository.getSelectedParticipantId(c);
        String status = "Выберите участника для ответа";
        if (selectedId != null) {
            for (Participant participant : ParticipantRepository.getAll(c)) {
                if (selectedId.equals(participant.id)) {
                    status = participant.displayName + ": " +
                        AttendanceRepository.getResponse(c, participant.id, FlightRepository.getActiveId(c));
                    break;
                }
            }
        }
        v.setTextViewText(R.id.flight_status, status);
        v.setTextViewText(R.id.flight_title, FlightPresentation.title(active));
        v.setTextViewText(R.id.flight_time, active == null ? "Дата и время уточняются" : active.date + "  •  " + active.time);
        v.setTextViewText(R.id.flight_place, "Место встречи: " +
            (active == null ? "не указано" : active.place));

        bindBroadcast(c, v, R.id.btn_going, GOING, 1);
        bindBroadcast(c, v, R.id.btn_not_going, NOT_GOING, 2);
        bindBroadcast(c, v, R.id.btn_route, ROUTE, 3);
        bindBroadcast(c, v, R.id.btn_telegram, SHARE, 4);
        manager.updateAppWidget(id, v);
        ModuleDebugLog.log(c, "WID-01", "widget_render", "success", "instance_updated");
    }

    private static void bindBroadcast(Context c, RemoteViews v, int viewId, String action, int request) {
        Intent intent = new Intent(c, FlightWidgetProvider.class).setAction(action);
        PendingIntent pending = PendingIntent.getBroadcast(c, request, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(viewId, pending);
    }
}
