package ru.aeroclub.informer;

import android.content.Context;
import android.content.SharedPreferences;

/** Single access point for local club configuration. */
public final class ClubSettingsRepository {
    private static final String PREFS="flight_informer";
    private static final String CLUB_NAME="club_name";
    private static final String DEFAULT_PLACE="club_default_place";
    private ClubSettingsRepository() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS,0);
    }

    public static synchronized ClubSettings get(Context c) {
        SharedPreferences p=prefs(c);
        return new ClubSettings(clean(p.getString(CLUB_NAME,"Аэроклуб"),"Аэроклуб"),
            clean(p.getString(DEFAULT_PLACE,"Аэродром «Северный»"),"Место встречи не указано"));
    }

    public static synchronized void save(Context c, String clubName, String defaultPlace) {
        SharedPreferences p=prefs(c);
        p.edit().putString(CLUB_NAME,clean(clubName,"Аэроклуб"))
            .putString(DEFAULT_PLACE,clean(defaultPlace,"Место встречи не указано")).apply();
    }

    private static String clean(String value,String fallback) {
        return value==null || value.trim().isEmpty()?fallback:value.trim();
    }
}
