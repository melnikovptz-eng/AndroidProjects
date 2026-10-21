package ru.aeroclub.informer;

import android.content.Context;

/** Settings use cases; club-wide settings remain separate from flight data. */
public final class ClubSettingsActions {
    private ClubSettingsActions() {}

    public static ClubSettings get(Context context) {
        return ClubSettingsRepository.get(context);
    }

    public static boolean save(Context context, String clubName, String defaultPlace) {
        try {
            ClubSettingsRepository.save(context, clubName, defaultPlace);
            EventLogRepository.record(context, "SET-01", "save", "success", null);
            return true;
        } catch (RuntimeException error) {
            EventLogRepository.record(context, "SET-01", "save", "failed", null);
            return false;
        }
    }
}
