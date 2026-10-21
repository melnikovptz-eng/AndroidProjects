package ru.aeroclub.informer;

import android.content.Context;

/** Orchestrates flight-admin use cases without putting persistence logic in the Activity. */
public final class FlightAdminActions {
    private FlightAdminActions() {}

    public static boolean isValidTitle(String title) {
        return title != null && !title.trim().isEmpty();
    }

    public static Flight createAndActivate(Context context, String title, String date,
                                           String time, String place, String dateIso) {
        if (!isValidTitle(title))
            EventLogRepository.record(context, "FLT-01", "create", "invalid_title", null);
            return null;
        }
        String safePlace = cleanPlace(context, place);
        Flight created = FlightRepository.create(context, title.trim(), date, time, safePlace, dateIso);
        if (created == null) {
            EventLogRepository.record(context, "FLT-01", "create", "invalid_date_or_time", null);
            return null;
        }
        FlightRepository.activate(context, created.id);
        recordReminderState(context, created);
        EventLogRepository.record(context, "FLT-01", "create", "success", created.id);
        return created;
    }

    public static boolean updateActive(Context context, String id, String title, String date,
                                       String time, String place, String dateIso) {
        if (title == null || title.trim().isEmpty()) {
            EventLogRepository.record(context, "FLT-01", "update", "invalid_title", id);
            return false;
        }
        String safePlace = cleanPlace(context, place);
        boolean explicitIsoProvided = dateIso != null && !dateIso.trim().isEmpty();
        String canonicalDate = FlightDateUtils.toIso(dateIso);
        if (canonicalDate.isEmpty() && !explicitIsoProvided) canonicalDate = FlightDateUtils.toIso(date);
        if (canonicalDate.isEmpty() || !FlightDateUtils.isValidTime(time)) {
            EventLogRepository.record(context, "FLT-01", "update", "invalid_date_or_time", id);
            return false;
        }
        Flight existing = FlightRepository.getById(context, id);
        if (existing == null) {
            EventLogRepository.record(context, "FLT-01", "update", "rejected_missing", id);
            return false;
        }
        if (existing.isDemo) {
            AttendanceRepository.deleteFlight(context, id, false);
            MessageRepository.deleteForFlight(context, id);
            EventLogRepository.record(context, "FLT-01", "demo_conversion_cleanup", "completed_local_only", id);
        }
        if (!FlightRepository.save(context, id, title.trim(), date, time, safePlace, canonicalDate)) {
            EventLogRepository.record(context, "FLT-01", "update", "save_rejected", id);
            return false;
        }
        Flight updated = FlightRepository.getById(context, id);
        if (updated == null) {
            EventLogRepository.record(context, "FLT-01", "update", "record_missing_after_save", id);
            return false;
        }
        recordReminderState(context, updated);
        EventLogRepository.record(context, "FLT-01", "update", "success", id);
        return true;
    }

    public static boolean activate(Context context, String id) {
        Flight target = FlightRepository.getById(context, id);
        if (target == null) {
            EventLogRepository.record(context, "FLT-01", "activate", "rejected_missing", id);
            return false;
        }
        FlightRepository.activate(context, id);
        EventLogRepository.record(context, "FLT-01", "activate", "local_only", id);
        return true;
    }

    public static boolean setPublished(Context context, String id, boolean published) {
        Flight target = FlightRepository.getById(context, id);
        if (target == null) {
            EventLogRepository.record(context, "FLT-01", "publication_change", "rejected_missing", id);
            return false;
        }
        FlightRepository.setPublished(context, id, published);
        EventLogRepository.record(context, "FLT-01", "publication_change", "local_only", id);
        return true;
    }

    public static boolean delete(Context context, String id) {
        Flight target = FlightRepository.getById(context, id);
        if (target == null || !FlightRepository.delete(context, id)) {
            EventLogRepository.record(context, "FLT-01", "delete", "rejected", id);
            return false;
        }
        FlightReminderScheduler.cancel(context, id);
        AttendanceRepository.deleteFlight(context, id, !target.isDemo);
        MessageRepository.deleteForFlight(context, id);
        EventLogRepository.record(context, "FLT-01", "delete", "success", id);
        return true;
    }

    private static String cleanPlace(Context context, String place) {
        if (place != null && !place.trim().isEmpty()) return place.trim();
        ClubSettings settings = ClubSettingsRepository.get(context);
        return settings.defaultMeetingPlace == null || settings.defaultMeetingPlace.trim().isEmpty()
            ? "Место встречи не указано" : settings.defaultMeetingPlace.trim();
    }

    private static void recordReminderState(Context context, Flight flight) {
        boolean scheduled = FlightReminderScheduler.schedule(context, flight);
        EventLogRepository.record(context, "NTF-01", "reminder_schedule",
            scheduled ? "scheduled" : "not_scheduled_or_notifications_disabled", flight.id);
    }
}
