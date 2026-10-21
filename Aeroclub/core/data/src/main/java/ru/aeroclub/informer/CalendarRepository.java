package ru.aeroclub.informer;

import android.content.Context;
import android.content.SharedPreferences;

/** Single bounded access point for the locally selected calendar date. */
public final class CalendarRepository {
    private static final String PREFS = "flight_informer";
    private static final String DATE_ISO = "calendar_date_iso";
    private static final String LEGACY_DATE = "calendar_date";

    private CalendarRepository() {}

    private static SharedPreferences prefs(Context context) {
        return LocalFilePreferences.get(context);
    }

    public static synchronized String getSelectedDateIso(Context context, String fallbackIso) {
        SharedPreferences p = prefs(context);
        String value = FlightDateUtils.toIso(p.getString(DATE_ISO, ""));
        if (!value.isEmpty()) return value;
        value = FlightDateUtils.toIso(p.getString(LEGACY_DATE, ""));
        if (!value.isEmpty()) return value;
        value = FlightDateUtils.toIso(fallbackIso);
        return value;
    }

    public static synchronized boolean setSelectedDate(Context context, String iso, String displayLabel) {
        String canonical = FlightDateUtils.toIso(iso);
        if (canonical.isEmpty()) return false;
        prefs(context).edit()
            .putString(DATE_ISO, canonical)
            .putString(LEGACY_DATE, displayLabel == null ? FlightDateUtils.toDisplay(canonical) : displayLabel)
            .apply();
        return true;
    }
}
