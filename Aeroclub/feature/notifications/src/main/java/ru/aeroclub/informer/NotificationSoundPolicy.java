package ru.aeroclub.informer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

/**
 * Single owner of the local flight-reminder notification channel and its default sound policy.
 * Android 8+ lets the user override channel sound; an existing channel is never reset behind
 * the user's back. This is local notification sound, not an incoming-message or FCM sound.
 */
public final class NotificationSoundPolicy {
    private static final String CHANNEL_NAME = "Напоминания о вылетах";
    private static final String CHANNEL_DESCRIPTION = "Звуковые локальные напоминания перед вылетом";
    private static final long[] VIBRATION_PATTERN = new long[] {0, 250, 150, 250};

    private NotificationSoundPolicy() {}

    public static synchronized void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            ModuleDebugLog.log(context, "NTF-SOUND", "ensure_channel", "legacy_system",
                "system_notification_defaults_used");
            return;
        }

        NotificationManager manager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            ModuleDebugLog.log(context, "NTF-SOUND", "ensure_channel", "error",
                "notification_manager_unavailable");
            return;
        }

        NotificationChannel existing = manager.getNotificationChannel(
            FlightReminderReceiver.CHANNEL_ID);
        if (existing != null) {
            String soundState = existing.getSound() == null ? "sound_disabled_by_channel" : "sound_configured";
            String vibrationState = existing.shouldVibrate() ? "vibration_enabled" : "vibration_disabled";
            ModuleDebugLog.log(context, "NTF-SOUND", "ensure_channel", "already_exists",
                soundState + ";" + vibrationState + ";importance=" + existing.getImportance());
            return;
        }

        NotificationChannel channel = new NotificationChannel(
            FlightReminderReceiver.CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(CHANNEL_DESCRIPTION);
        channel.enableVibration(true);
        channel.setVibrationPattern(VIBRATION_PATTERN);
        channel.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        manager.createNotificationChannel(channel);
        ModuleDebugLog.log(context, "NTF-SOUND", "ensure_channel", "success_local",
            "default_notification_sound_and_vibration_configured");
    }

    /** Returns diagnostic state only; Android/user settings remain the source of truth. */
    public static String getState(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "legacy_system_defaults";
        NotificationManager manager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return "notification_manager_unavailable";
        NotificationChannel channel = manager.getNotificationChannel(FlightReminderReceiver.CHANNEL_ID);
        if (channel == null) return "channel_not_configured";
        if (channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return "channel_disabled";
        return (channel.getSound() == null ? "sound_disabled" : "sound_enabled") + ";"
            + (channel.shouldVibrate() ? "vibration_enabled" : "vibration_disabled");
    }
}
