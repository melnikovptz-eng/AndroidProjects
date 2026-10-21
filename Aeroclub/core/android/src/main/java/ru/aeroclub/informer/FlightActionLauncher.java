package ru.aeroclub.informer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** One implementation for route and share actions used by the screen and home-screen widget. */
public final class FlightActionLauncher {
    private FlightActionLauncher() {}

    public static boolean openRoute(Context context, Flight flight) {
        String flightId = flight == null ? null : flight.id;
        String place = flight == null || flight.place == null ? "" : flight.place.trim();
        if (place.isEmpty()) {
            Toast.makeText(context, "Место встречи не указано", Toast.LENGTH_SHORT).show();
            EventLogRepository.record(context, "MAP-01", "open_route", "missing_place", flightId);
            return false;
        }
        Intent map = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)));
        if (!(context instanceof Activity)) map.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(map);
            EventLogRepository.record(context, "MAP-01", "open_route", "intent_started", flightId);
            return true;
        } catch (Exception e) {
            Toast.makeText(context, "Не найдено приложение для карт", Toast.LENGTH_SHORT).show();
            EventLogRepository.record(context, "MAP-01", "open_route", "no_maps_app", flightId);
            return false;
        }
    }

    public static boolean shareFlight(Context context, Flight flight) {
        if (flight == null) {
            Toast.makeText(context, "Нет данных о вылете", Toast.LENGTH_SHORT).show();
            EventLogRepository.record(context, "SHR-01", "share_chooser_opened", "no_active_flight", null);
            return false;
        }
        String body = (flight.isDemo ? "ДЕМО · " : "") + ClubSettingsRepository.get(context).clubName + " — " + flight.title + ": " + flight.date + ", " + flight.time +
            ". Место встречи: " + flight.place + ".";
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, body);
        Intent chooser = Intent.createChooser(send, "Отправить информацию о полёте");
        if (!(context instanceof Activity)) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(chooser);
            MessageRepository.recordShareChooserOpened(context, flight.id, body);
            EventLogRepository.record(context, "SHR-01", "share_chooser_opened", "delivery_unconfirmed", flight.id);
            return true;
        } catch (Exception e) {
            Toast.makeText(context, "Нет приложения для отправки", Toast.LENGTH_SHORT).show();
            EventLogRepository.record(context, "SHR-01", "share_chooser_opened", "no_share_app", flight.id);
            return false;
        }
    }
}
