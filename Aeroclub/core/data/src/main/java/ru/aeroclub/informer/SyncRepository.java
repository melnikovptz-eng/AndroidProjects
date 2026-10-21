package ru.aeroclub.informer;

import android.content.Context;

/**
 * Compatibility facade for older repository calls.
 * In single-device mode, changes are persisted by LocalFilePreferences; no server
 * outbox, network request, or duplicate diagnostic event is created here.
 */
public final class SyncRepository {
    private SyncRepository() {}

    public static synchronized void enqueue(Context context, String entityType,
                                            String entityId, String operation) {
        // Intentionally no-op: the caller has already persisted the local domain change.
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
}
