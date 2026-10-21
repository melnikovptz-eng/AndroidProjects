package ru.aeroclub.informer;

import android.content.Context;

/** Orchestrates flight-admin use cases without putting persistence logic in the Activity. */
public final class FlightAdminActions {
    private FlightAdminActions() {}

    public static Flight createAndActivate(Context context, String title, String date,
                                           String time, String place, String dateIso) {
        Flight created = FlightRepository.create(context, title, date, time, place, dateIso);
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
        if (!FlightRepository.save(context, id, title, date, time, place, dateIso)) {
            EventLogRepository.record(context, "FLT-01", "update", "invalid_date_or_time", id);
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

    private static void recordReminderState(Context context, Flight flight) {
        boolean scheduled = FlightReminderScheduler.schedule(context, flight);
        EventLogRepository.record(context, "NTF-01", "reminder_schedule",
            scheduled ? "scheduled" : "not_scheduled_or_notifications_disabled", flight.id);
    }
}
