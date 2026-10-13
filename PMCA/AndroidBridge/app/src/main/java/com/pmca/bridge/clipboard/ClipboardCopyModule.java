package com.pmca.bridge.clipboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

public final class ClipboardCopyModule {
    private final Context context;
    private final ClipboardManager clipboard;

    public ClipboardCopyModule(Context context) {
        this.context = context.getApplicationContext();
        this.clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    public boolean copy(String text, String label) {
        if (clipboard == null || text == null) return false;
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        return true;
    }

    public String read() {
        if (clipboard == null || !clipboard.hasPrimaryClip()) return "";
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return "";
        CharSequence text = clip.getItemAt(0).coerceToText(context);
        return text == null ? "" : text.toString();
    }
}
