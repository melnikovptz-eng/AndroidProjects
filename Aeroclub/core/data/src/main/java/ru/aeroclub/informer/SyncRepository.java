package ru.aeroclub.informer;

import android.content.Context;

/**
 * Compatibility facade for callers written for the earlier server-outbox prototype.
 * In the current single-device release, domain changes are saved to LocalFilePreferences.
 * There is no configured server, so this class must not queue fake remote operations.
 */
public final class SyncRepository {
    private SyncRepository() {}

    public static synchronized void enqueue(Context context, String entityType,
                                            String entityId, String operation) {
        ModuleDebugLog.log(context, "DATA-01", "local_persist", "local_only",
            "server_outbox_disabled; type=" + safe(entityType) + "; operation=" + safe(operation));
    }

    public static synchronized void removeDemoFlightOperations(Context context, String flightId) {
        // No server outbox is used in single-device mode.
    }

    public static synchronized int getPendingCount(Context context) {
        return 0;
    }

    public static String getStatus(Context context) {
        return "Режим одного устройства: данные сохраняются в локальном JSON-файле. Серверная синхронизация отключена.";
    }

    public static synchronized String getPendingSummary(Context context, int limit) {
        return "Серверная очередь отключена. Все изменения хранятся локально на этом устройстве.";
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
