package com.pmca.bridge.logging;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class PmcaLogger {
    private static final int MAX_LINES = 50;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private PmcaLogger() {}

    public static synchronized void log(String message) {
        String line = FORMAT.format(new Date()) + "  " + message;
        while (LINES.size() >= MAX_LINES) {
            LINES.removeFirst();
        }
        LINES.addLast(line);
    }

    public static synchronized List<String> getLastLines() {
        return new ArrayList<>(LINES);
    }

    public static synchronized String getText() {
        StringBuilder result = new StringBuilder();
        for (String line : LINES) {
            if (result.length() > 0) result.append('\n');
            result.append(line);
        }
        return result.toString();
    }
}
