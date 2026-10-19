package com.test.accessibility3.modules.debug;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DebugLogger {

    private static final String FILE_NAME = "debug_log.txt";
    private static final String LEGACY_FILE_NAME = "last_scan.txt";

    private static String time() {
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
        ).format(new Date());
    }

    public static synchronized void log(
            Context context,
            String level,
            String message
    ) {
        if (context == null)
            return;

        try {
            File file = new File(
                    context.getFilesDir(),
                    FILE_NAME
            );

            String line =
                    "[" + time() + "] " +
                    "[" + level + "] " +
                    message + "\n";

            FileOutputStream fos =
                    new FileOutputStream(file, true);

            fos.write(line.getBytes("UTF-8"));
            fos.close();

        } catch (Exception ignored) {
        }
    }

    public static void info(
            Context context,
            String message
    ) {
        log(context, "INFO", message);
    }

    public static void debug(
            Context context,
            String message
    ) {
        log(context, "DEBUG", message);
    }

    public static void warn(
            Context context,
            String message
    ) {
        log(context, "WARN", message);
    }

    public static void error(
            Context context,
            String message
    ) {
        log(context, "ERROR", message);
    }

    public static void clipboardCopy(
            Context context,
            int length
    ) {
        info(
                context,
                "CLIPBOARD: LOG COPIED, length=" + length
        );
    }

    public static void clipboardCopyData(
            Context context,
            String text
    ) {
        String value = text == null ? "" : text;
        info(
                context,
                "CLIPBOARD: DATA COPIED, length=" + value.length()
                        + ", data=" + oneLine(value)
        );
    }

    public static void sendEvent(
            Context context,
            String event
    ) {
        info(
                context,
                "SEND: " + oneLine(event)
        );
    }

    public static void sendData(
            Context context,
            String text
    ) {
        String value = text == null ? "" : text;
        info(
                context,
                "SEND: DATA, length=" + value.length()
                        + ", data=" + oneLine(value)
        );
    }

    private static String oneLine(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static void save(
            Context context,
            String text
    ) {
        if (context == null)
            return;

        try {
            File file =
                    new File(
                            context.getFilesDir(),
                            LEGACY_FILE_NAME
                    );

            FileOutputStream fos =
                    new FileOutputStream(
                            file,
                            false
                    );

            fos.write(
                    text.getBytes("UTF-8")
            );

            fos.close();

        } catch (Exception ignored) {
        }
    }

    public static String read(
            Context context
    ) {
        if (context == null)
            return "CONTEXT: NULL";

        try {
            File file =
                    new File(
                            context.getFilesDir(),
                            LEGACY_FILE_NAME
                    );

            if (!file.exists())
                return "Файл last_scan.txt не найден";

            FileInputStream fis =
                    new FileInputStream(file);

            byte[] data =
                    new byte[(int) file.length()];

            int count =
                    fis.read(data);

            fis.close();

            return new String(
                    data,
                    0,
                    count,
                    "UTF-8"
            );

        } catch (Exception e) {

            return "Ошибка чтения: " +
                    e.getMessage();
        }
    }

    public static String readLog(
            Context context
    ) {
        if (context == null)
            return "CONTEXT: NULL";

        try {
            File file =
                    new File(
                            context.getFilesDir(),
                            FILE_NAME
                    );

            if (!file.exists())
                return "Лог пока пуст";

            return readLastLines(
                    file,
                    100
            );

        } catch (Exception e) {
            return "Ошибка чтения лога: " +
                    e.getMessage();
        }
    }

    public static String readLastLines(
            File file,
            int maxLines
    ) throws Exception {

        if (file == null || !file.exists()) {
            return "Лог пока пуст";
        }

        if (maxLines < 1) {
            maxLines = 1;
        }

        long length = file.length();

        if (length == 0) {
            return "Лог пока пуст";
        }

        final int MAX_BYTES = 64 * 1024;

        int bytesToRead = (int) Math.min(length, MAX_BYTES);

        byte[] data = new byte[bytesToRead];

        java.io.RandomAccessFile raf =
                new java.io.RandomAccessFile(file, "r");

        raf.seek(length - bytesToRead);

        int total = 0;

        while (total < bytesToRead) {
            int read = raf.read(
                    data,
                    total,
                    bytesToRead - total
            );

            if (read < 0) {
                break;
            }

            total += read;
        }

        raf.close();

        String text =
                new String(
                        data,
                        0,
                        total,
                        "UTF-8"
                );

        String[] allLines =
                text.split("\\r?\\n");

        int start =
                Math.max(
                        0,
                        allLines.length - maxLines
                );

        StringBuilder result =
                new StringBuilder();

        for (int i = start; i < allLines.length; i++) {
            result.append(allLines[i]);
            result.append("\n");
        }

        return result.toString();
    }
}
