package com.test.accessibility3.modules.taper;

import android.content.Context;
import android.content.SharedPreferences;

public final class TaperProtocol {

    public static final String BEGIN = "⟦TAPER:BEGIN⟧";
    public static final String END = "⟦TAPER:END⟧";
    public static final String CMD_PREFIX = "⟦TAPER:CMD_ID:";
    public static final String CMD_SUFFIX = "⟧";

    private static final String PREFS = "taper_protocol";
    private static final String KEY_NEXT_ID = "next_cmd_id";

    private TaperProtocol() {}

    public static synchronized long nextCommandId(Context context) {
        SharedPreferences p =
            context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        long id = p.getLong(KEY_NEXT_ID, 1L);

        p.edit()
            .putLong(KEY_NEXT_ID, id + 1L)
            .apply();

        return id;
    }

    public static String tagCommand(long id, String command) {
        return BEGIN + "\n" +
               CMD_PREFIX + id + CMD_SUFFIX + "\n" +
               command.trim() + "\n" +
               END;
    }

    public static String tagResponse(long id, String response) {
        return BEGIN + "\n" +
               CMD_PREFIX + id + CMD_SUFFIX + "\n" +
               response.trim() + "\n" +
               END;
    }

    public static String extractPayloadWithoutId(String text) {
        if (text == null) return null;

        int begin = text.indexOf(BEGIN);
        if (begin < 0) return null;

        int start = begin + BEGIN.length();

        int end = text.indexOf(END, start);
        if (end < 0) return null;

        String payload =
            text.substring(start, end).trim();

        if (payload.isEmpty()) return null;

        return payload;
    }

    public static Long extractId(String text) {
        if (text == null) return null;

        int start = text.indexOf(CMD_PREFIX);
        if (start < 0) return null;

        start += CMD_PREFIX.length();

        int end = text.indexOf(CMD_SUFFIX, start);
        if (end < 0) return null;

        try {
            return Long.parseLong(
                text.substring(start, end).trim()
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static String extractPayload(String text) {
        if (text == null) return null;

        int begin = text.indexOf(BEGIN);
        if (begin < 0) return null;

        int contentStart =
            text.indexOf(CMD_SUFFIX, begin);

        if (contentStart < 0) return null;

        contentStart += CMD_SUFFIX.length();

        int end =
            text.indexOf(END, contentStart);

        if (end < 0) return null;

        String payload =
            text.substring(contentStart, end).trim();

        if (payload.isEmpty()) return null;

        return payload;
    }

    public static boolean isTagged(String text) {
        return text != null &&
               text.contains(BEGIN) &&
               text.contains(END) &&
               extractId(text) != null;
    }
}
