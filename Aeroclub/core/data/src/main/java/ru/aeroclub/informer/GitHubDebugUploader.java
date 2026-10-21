package ru.aeroclub.informer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/**
 * Compatibility-named entry point for explicitly sharing the local diagnostic journal.
 * No GitHub token or network upload is used: secrets must never be embedded in an APK.
 */
public final class GitHubDebugUploader {
    private GitHubDebugUploader() { }

    /** Legacy method name retained for callers; this opens a share sheet, not a GitHub upload. */
    public static void uploadNow(Context context, Callback callback) {
        if (context == null) {
            complete(callback, false, "Контекст недоступен", 0);
            return;
        }
        String journal = ModuleDebugLog.getFullLogSnapshot(context);
        if (journal.trim().isEmpty()) {
            complete(callback, false, "Журнал отладки пуст", 0);
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Aeroclub — журнал отладки");
        send.putExtra(Intent.EXTRA_TEXT, journal);
        Intent chooser = Intent.createChooser(send, "Поделиться журналом отладки");
        if (!(context instanceof Activity)) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(chooser);
            complete(callback, true,
                    "Открыто меню передачи журнала. Загрузка в GitHub и доставка не выполнялись.",
                    countLines(journal));
        } catch (Exception error) {
            complete(callback, false, "Не удалось открыть меню передачи журнала", 0);
        }
    }

    private static int countLines(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == '\n') count++;
        return count;
    }

    private static void complete(Callback callback, boolean success, String message, int lines) {
        if (callback != null) callback.onComplete(success, message, lines);
    }

    public interface Callback {
        void onComplete(boolean success, String message, int lines);
    }
}
