package ru.aeroclub.informer;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Delivers an on-device reminder; this is not an FCM push notification. */
public class FlightReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "aeroclub_flight_reminders_v2";

    @Override public void onReceive(Context context, Intent intent) {
        String flightId = intent == null ? null : intent.getStringExtra("flight_id");
        if (flightId == null || flightId.trim().isEmpty()) {
            EventLogRepository.record(context, "NTF-01", "local_reminder_delivered", "rejected_missing_flight_id", null);
            return;
        }

        // Always read the current record from the local source of truth. A stale alarm
        // must not announce a deleted flight or old date/place from PendingIntent extras.
        Flight currentFlight = FlightRepository.getById(context, flightId);
        if (currentFlight == null) {
            FlightReminderScheduler.cancel(context, flightId);
            EventLogRepository.record(context, "NTF-01", "local_reminder_delivered", "skipped_flight_deleted", flightId);
            return;
        }
        long flightStart = FlightDateUtils.toMillis(currentFlight.dateIso, currentFlight.time);
        long now = System.currentTimeMillis();
        if (flightStart <= now) {
            EventLogRepository.record(context, "NTF-01", "local_reminder_delivered", "skipped_flight_time_passed", flightId);
            return;
        }
        long reminderLead = 60L * 60L * 1000L;
        if (flightStart - now > reminderLead + 2L * 60L * 1000L) {
            FlightReminderScheduler.schedule(context, currentFlight);
            EventLogRepository.record(context, "NTF-01", "local_reminder_delivered", "stale_alarm_rescheduled", flightId);
            return;
        }

        String title = FlightPresentation.title(currentFlight);
        String date = currentFlight.date == null ? "" : currentFlight.date;
        String time = currentFlight.time == null ? "" : currentFlight.time;
        String place = currentFlight.place == null ? "" : currentFlight.place;

        // Ensure the sound-enabled channel exists even if this receiver is invoked
        // independently of the scheduler. Android 8+ channel sound is immutable;
        // the v2 ID avoids inheriting a previously-created silent channel.
        NotificationSoundPolicy.ensureChannel(context);
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null) {
            EventLogRepository.record(context,"NTF-01","local_reminder_delivered","notification_service_unavailable",flightId);
            return;
        }
        if(Build.VERSION.SDK_INT>=33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            EventLogRepository.record(context,"NTF-01","local_reminder_delivered","permission_denied",flightId);
            return;
        }
        if (!FlightReminderScheduler.notificationsEnabled(context)) {
            EventLogRepository.record(context,"NTF-01","local_reminder_delivered","notifications_disabled",flightId);
            return;
        }
        Intent open=new Intent();
        open.setComponent(new ComponentName(context.getPackageName(), "ru.aeroclub.informer.MainActivity"));
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(context,flightId==null?0:flightId.hashCode(),open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder builder=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O
            ?new android.app.Notification.Builder(context,CHANNEL_ID)
            :new android.app.Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Напоминание о вылете: "+title)
            .setContentText(date+" • "+time)
            .setStyle(new android.app.Notification.BigTextStyle().bigText(date+" • "+time+"\nМесто встречи: "+place))
            .setContentIntent(content).setAutoCancel(true);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setDefaults(android.app.Notification.DEFAULT_ALL)
                .setPriority(android.app.Notification.PRIORITY_DEFAULT);
        }
        manager.notify(flightId==null?1:flightId.hashCode(),builder.build());
        EventLogRepository.record(context,"NTF-01","local_reminder_delivered","notification_posted",flightId);
    }
}
