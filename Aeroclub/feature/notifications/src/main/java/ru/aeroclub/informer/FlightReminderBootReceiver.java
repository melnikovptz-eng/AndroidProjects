package ru.aeroclub.informer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores local flight reminders after the operating system restarts. */
public class FlightReminderBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if(intent==null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        FlightRepository.ensureInitialized(context);
        FlightReminderScheduler.syncAll(context);
        EventLogRepository.record(context,"NTF-01","restore_after_reboot","scheduled","device");
    }
}
