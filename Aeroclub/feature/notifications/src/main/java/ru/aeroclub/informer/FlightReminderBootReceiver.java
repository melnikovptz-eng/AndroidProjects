package ru.aeroclub.informer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores local flight reminders after the operating system restarts. */
public class FlightReminderBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !"android.intent.action.DATE_CHANGED".equals(action)) return;
        FlightRepository.ensureInitialized(context);
        FlightReminderScheduler.syncAll(context);
        String result = FlightReminderScheduler.notificationsEnabled(context) ? "completed" : "notifications_disabled";
        String event = Intent.ACTION_BOOT_COMPLETED.equals(action)
            ? "restore_after_reboot" : "reschedule_after_clock_change";
        EventLogRepository.record(context,"NTF-01",event,result,"system_broadcast");
    }
}
