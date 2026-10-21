package ru.aeroclub.informer;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Single entry point for refreshing all installed flight widgets after local data changes. */
public final class WidgetRefreshManager {
    private WidgetRefreshManager() {}

    public static void refresh(Context context, String providerClassName) {
        if (context == null || providerClassName == null || providerClassName.trim().isEmpty()) return;
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            if (manager == null) return;
            ComponentName provider = new ComponentName(context.getPackageName(), providerClassName);
            int[] ids = manager.getAppWidgetIds(provider);
            if (ids == null || ids.length == 0) return;
            Intent update = new Intent().setComponent(provider);
            update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            context.sendBroadcast(update);
        } catch (RuntimeException error) {
            try { EventLogRepository.record(context, "WID-01", "refresh", "failed", null); }
            catch (RuntimeException ignored) { /* Keep a widget refresh failure from crashing the caller. */ }
        }
    }
}
