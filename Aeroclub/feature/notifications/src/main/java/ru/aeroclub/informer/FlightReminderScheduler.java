package ru.aeroclub.informer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.os.Build;
import android.content.Context;
import android.content.Intent;
import java.util.List;

/** Schedules an inexact local reminder one hour before each future flight. */
public final class FlightReminderScheduler {
    private static final long REMINDER_LEAD_MS=60L*60L*1000L;
    private FlightReminderScheduler() {}

    public static void ensureChannel(Context context) {
        NotificationSoundPolicy.ensureChannel(context);
    }

    public static synchronized boolean schedule(Context context, Flight flight) {
        ensureChannel(context);
        if(flight==null || flight.id==null) {
            ModuleDebugLog.log(context, "NTF-01", "schedule", "rejected", "missing_flight_or_id");
            return false;
        }
        long start=parseStart(flight);
        if(start<=System.currentTimeMillis()+REMINDER_LEAD_MS) {
            cancel(context,flight.id);
            ModuleDebugLog.log(context, "NTF-01", "schedule", "skipped", "flight_not_far_enough_in_future");
            return false;
        }
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(alarm==null) {
            ModuleDebugLog.log(context, "NTF-01", "schedule", "error", "alarm_manager_unavailable");
            return false;
        }
        PendingIntent pending=pendingIntent(context,flight);
        try {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,start-REMINDER_LEAD_MS,pending);
            ModuleDebugLog.log(context, "NTF-01", "schedule", "success_local", "reminder_scheduled");
            return true;
        } catch (RuntimeException error) {
            ModuleDebugLog.log(context, "NTF-01", "schedule", "error", error.getClass().getSimpleName());
            return false;
        }
    }

    public static synchronized void syncAll(Context context) {
        ensureChannel(context);
        List<Flight> flights=FlightRepository.getFlights(context);
        ModuleDebugLog.log(context, "NTF-01", "sync_all", "started", "flight_count=" + flights.size());
        if (!notificationsEnabled(context)) {
            for (Flight flight : flights) cancel(context, flight.id);
            ModuleDebugLog.log(context, "NTF-01", "sync_all", "skipped", "notifications_disabled");
            return;
        }
        for(Flight flight:flights) schedule(context,flight);
        ModuleDebugLog.log(context, "NTF-01", "sync_all", "completed", "flight_count=" + flights.size());
    }

    public static synchronized void cancel(Context context,String flightId) {
        if(flightId==null) {
            ModuleDebugLog.log(context, "NTF-01", "cancel", "skipped", "missing_flight_id");
            return;
        }
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(alarm!=null) {
            Intent intent=new Intent(context,FlightReminderReceiver.class).setAction("ru.aeroclub.informer.FLIGHT_REMINDER");
            intent.putExtra("flight_id",flightId);
            PendingIntent pending=PendingIntent.getBroadcast(context,flightId.hashCode(),intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            alarm.cancel(pending);
            pending.cancel();
            ModuleDebugLog.log(context, "NTF-01", "cancel", "success_local", "reminder_cancelled");
        }
    }

    public static boolean permissionRequestAttempted(Context context) {
        return context.getSharedPreferences("flight_informer", Context.MODE_PRIVATE)
            .getBoolean("notification_permission_requested_v1", false);
    }

    public static void markPermissionRequestAttempted(Context context) {
        context.getSharedPreferences("flight_informer", Context.MODE_PRIVATE).edit()
            .putBoolean("notification_permission_requested_v1", true).apply();
    }

    public static boolean notificationsEnabled(Context context) {
        return "enabled".equals(notificationState(context));
    }

    public static String notificationState(Context context) {
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null) return "unavailable";
        if(Build.VERSION.SDK_INT>=33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return "permission_required";
        }
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N && !manager.areNotificationsEnabled()) return "app_disabled";
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel=manager.getNotificationChannel(FlightReminderReceiver.CHANNEL_ID);
            if(channel==null) return "not_configured";
            if(channel.getImportance()==NotificationManager.IMPORTANCE_NONE) return "channel_disabled";
        }
        return "enabled";
    }

    private static PendingIntent pendingIntent(Context context,Flight flight) {
        Intent intent=new Intent(context,FlightReminderReceiver.class).setAction("ru.aeroclub.informer.FLIGHT_REMINDER");
        intent.putExtra("flight_id",flight.id);
        intent.putExtra("flight_title", FlightPresentation.title(flight));
        intent.putExtra("flight_date",flight.date);
        intent.putExtra("flight_time",flight.time);
        intent.putExtra("flight_place",flight.place);
        return PendingIntent.getBroadcast(context,flight.id.hashCode(),intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static long parseStart(Flight flight) {
        return FlightDateUtils.toMillis(flight.dateIso,flight.time);
    }
}
