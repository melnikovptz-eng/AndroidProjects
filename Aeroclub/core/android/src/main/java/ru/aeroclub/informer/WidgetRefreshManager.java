package ru.aeroclub.informer;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Single entry point for refreshing all installed flight widgets after local data changes. */
public final class WidgetRefreshManager {
    private WidgetRefreshManager() {}

    public static void refresh(Context context, Class<?> providerClass) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, providerClass));
        if (ids == null || ids.length == 0) return;
        Intent update = new Intent(context, providerClass);
        update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(update);
    }
}
