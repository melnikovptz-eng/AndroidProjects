package ru.aeroclub.informer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.app.NotificationManager;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Schedules an inexact local reminder one hour before each future flight. */
public final class FlightReminderScheduler {
    private static final long REMINDER_LEAD_MS=60L*60L*1000L;
    private FlightReminderScheduler() {}

    public static synchronized boolean schedule(Context context, Flight flight) {
        if(flight==null || flight.id==null) return false;
        long start=parseStart(flight);
        if(start<=System.currentTimeMillis()+REMINDER_LEAD_MS) {
            cancel(context,flight.id);
            EventLogRepository.record(context,"NTF-01","schedule_local_reminder","not_future_enough",flight.id);
            return false;
        }
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(alarm==null) return false;
        PendingIntent pending=pendingIntent(context,flight);
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,start-REMINDER_LEAD_MS,pending);
        EventLogRepository.record(context,"NTF-01","schedule_local_reminder","scheduled",flight.id);
        return true;
    }

    public static synchronized void syncAll(Context context) {
        List<Flight> flights=FlightRepository.getFlights(context);
        for(Flight flight:flights) schedule(context,flight);
    }

    public static synchronized void cancel(Context context,String flightId) {
        if(flightId==null) return;
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(alarm!=null) {
            Intent intent=new Intent(context,FlightReminderReceiver.class).setAction("ru.aeroclub.informer.FLIGHT_REMINDER");
            intent.putExtra("flight_id",flightId);
            PendingIntent pending=PendingIntent.getBroadcast(context,flightId.hashCode(),intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            alarm.cancel(pending);
            pending.cancel();
        }
    }

    public static boolean notificationsEnabled(Context context) {
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null) return false;
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N) return manager.areNotificationsEnabled();
        return true;
    }

    private static PendingIntent pendingIntent(Context context,Flight flight) {
        Intent intent=new Intent(context,FlightReminderReceiver.class).setAction("ru.aeroclub.informer.FLIGHT_REMINDER");
        intent.putExtra("flight_id",flight.id);
        intent.putExtra("flight_title",flight.title);
        intent.putExtra("flight_date",flight.date);
        intent.putExtra("flight_time",flight.time);
        intent.putExtra("flight_place",flight.place);
        return PendingIntent.getBroadcast(context,flight.id.hashCode(),intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static long parseStart(Flight flight) {
        try {
            Locale ru=Locale.forLanguageTag("ru");
            Date date=new SimpleDateFormat("EEE, d MMM",ru).parse(flight.date);
            String[] time=flight.time.split(":");
            if(date==null || time.length!=2) return 0L;
            Calendar c=Calendar.getInstance();
            Calendar parsed=Calendar.getInstance();
            parsed.setTime(date);
            c.set(Calendar.MONTH,parsed.get(Calendar.MONTH));
            c.set(Calendar.DAY_OF_MONTH,parsed.get(Calendar.DAY_OF_MONTH));
            c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(time[0]));
            c.set(Calendar.MINUTE,Integer.parseInt(time[1]));
            c.set(Calendar.SECOND,0);
            c.set(Calendar.MILLISECOND,0);
            return c.getTimeInMillis();
        } catch(Exception ignored) { return 0L; }
    }
}
