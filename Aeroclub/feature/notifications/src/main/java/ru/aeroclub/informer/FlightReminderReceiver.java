package ru.aeroclub.informer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Delivers an on-device reminder; this is not an FCM push notification. */
public class FlightReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "aeroclub_flight_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        String flightId=intent==null?null:intent.getStringExtra("flight_id");
        String title=intent==null?"":intent.getStringExtra("flight_title");
        String date=intent==null?"":intent.getStringExtra("flight_date");
        String time=intent==null?"":intent.getStringExtra("flight_time");
        String place=intent==null?"":intent.getStringExtra("flight_place");
        if(title==null || title.trim().isEmpty()) title="Ближайший вылет";
        if(date==null) date="";
        if(time==null) time="";
        if(place==null) place="";

        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null) return;
        if(Build.VERSION.SDK_INT>=33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            EventLogRepository.record(context,"NTF-01","local_reminder_delivered","permission_denied",flightId);
            return;
        }
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
            NotificationChannel channel=new NotificationChannel(CHANNEL_ID,"Напоминания о вылетах",NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Локальные напоминания перед вылетом");
            manager.createNotificationChannel(channel);
        }
        Intent open=new Intent(context,MainActivity.class);
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
        manager.notify(flightId==null?1:flightId.hashCode(),builder.build());
        EventLogRepository.record(context,"NTF-01","local_reminder_delivered","notification_posted",flightId);
    }
}
