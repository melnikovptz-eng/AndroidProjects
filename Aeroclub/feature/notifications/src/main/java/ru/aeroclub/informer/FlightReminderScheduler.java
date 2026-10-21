package ru.aeroclub.informer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.app.NotificationManager;
import android.os.Build;
import java.util.List;

/** Schedules an inexact local reminder one hour before each future flight. */
public final class FlightReminderScheduler {
    private static final long REMINDER_LEAD_MS=60L*60L*1000L;
    private FlightReminderScheduler() {}

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager manager=(android.app.NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            if(manager!=null) {
                android.app.NotificationChannel channel=new android.app.NotificationChannel(
                    FlightReminderReceiver.CHANNEL_ID,"Напоминания о вылетах",android.app.NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Локальные напоминания перед вылетом");
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static synchronized boolean schedule(Context context, Flight flight) {
        if(flight==null || flight.id==null) return false;
        long start=parseStart(flight);
        if(start<=System.currentTimeMillis()+REMINDER_LEAD_MS) {
            cancel(context,flight.id);
            return false;
        }
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(alarm==null) return false;
        PendingIntent pending=pendingIntent(context,flight);
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,start-REMINDER_LEAD_MS,pending);
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
        intent.putExtra("flight_title",flight.isDemo ? "ДЕМО · " + flight.title : flight.title);
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
