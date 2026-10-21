package ru.aeroclub.informer;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** Calendar use cases; compares only canonical ISO dates. */
public final class CalendarActions {
    private CalendarActions() {}

    public static String selectedDate(Context context, String fallbackIso) {
        String selected = CalendarRepository.getSelectedDateIso(context, fallbackIso);
        return FlightDateUtils.toIso(selected);
    }

    public static boolean selectDate(Context context, String iso) {
        String canonical = FlightDateUtils.toIso(iso);
        if (canonical.isEmpty()) {
            EventLogRepository.record(context, "CAL-01", "select_date", "invalid_iso", null);
            return false;
        }
        boolean saved = CalendarRepository.setSelectedDate(context, canonical, FlightDateUtils.toDisplay(canonical));
        EventLogRepository.record(context, "CAL-01", "select_date", saved ? "success_local_only" : "save_failed", canonical);
        return saved;
    }

    public static List<Flight> flightsOnDate(Context context, String iso) {
        String canonical = FlightDateUtils.toIso(iso);
        ArrayList<Flight> matches = new ArrayList<>();
        if (canonical.isEmpty()) return matches;
        for (Flight flight : FlightRepository.getFlights(context)) {
            if (canonical.equals(flight.dateIso)) matches.add(flight);
        }
        return matches;
    }
}
