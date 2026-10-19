package com.test.accessibility3.modules.termux;

import android.content.Context;

import com.test.accessibility3.modules.debug.DebugLogger;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TermuxBridge {

    private static final String TAG = "TERMUX";

    private static final String BASE =
            "/storage/emulated/0/Download/Taper";

    private static final String COMMAND_FILE =
            BASE + "/command.json";

    private static final String RESULT_FILE =
            BASE + "/result.json";

    private static final long TIMEOUT =
            15000L;

    private static final AtomicBoolean BUSY =
            new AtomicBoolean(false);

    private TermuxBridge() {
    }

    public static void initialize(Context context) {

        File dir = new File(BASE);

        if (!dir.exists()) {
            boolean created = dir.mkdirs();

            DebugLogger.info(
                    context,
                    TAG + ": MKDIR=" + created
            );
        }

        DebugLogger.info(
                context,
                TAG + ": BRIDGE_INITIALIZED"
        );

        DebugLogger.info(
                context,
                TAG + ": BASE=" + BASE
        );

        DebugLogger.info(
                context,
                TAG + ": PROTOCOL=TAPER_DATA"
        );
    }

    public static String sendAndReceive(
            Context context,
            String command,
            String data
    ) {

        if (!BUSY.compareAndSet(false, true)) {

            DebugLogger.info(
                    context,
                    TAG + ": BUSY"
            );

            return null;
        }

        final String operationId =
                UUID.randomUUID().toString();

        try {

            File baseDir =
                    new File(BASE);

            if (!baseDir.exists() &&
                    !baseDir.mkdirs()) {

                DebugLogger.info(
                        context,
                        TAG + ": BASE_CREATE_FAILED"
                );

                return null;
            }

            File commandFile =
                    new File(COMMAND_FILE);

            File resultFile =
                    new File(RESULT_FILE);

            /*
             * Очень важно:
             * старый результат удаляем ДО новой операции.
             */
            if (resultFile.exists()) {

                boolean deleted =
                        resultFile.delete();

                DebugLogger.info(
                        context,
                        TAG +
                                ": OLD_RESULT_DELETE=" +
                                deleted
                );
            }

            /*
             * Для нашего daemon всегда используем
             * именно TAPER_DATA.
             */
            String realCommand =
                    "TAPER_DATA";

            String realType =
                    "TAPER_DATA";

            String realData =
                    data == null ? "" : data;

            JSONObject json =
                    new JSONObject();

            json.put(
                    "operation_id",
                    operationId
            );

            json.put(
                    "timestamp",
                    System.currentTimeMillis()
            );

            json.put(
                    "type",
                    realType
            );

            json.put(
                    "command",
                    realCommand
            );

            json.put(
                    "data",
                    realData
            );

            String payload =
                    json.toString();

            DebugLogger.info(
                    context,
                    TAG + ": SEND_START"
            );

            DebugLogger.info(
                    context,
                    TAG +
                            ": operation_id=" +
                            operationId
            );

            DebugLogger.info(
                    context,
                    TAG +
                            ": type=" +
                            realType
            );

            DebugLogger.info(
                    context,
                    TAG +
                            ": command=" +
                            realCommand
            );

            DebugLogger.info(
                    context,
                    TAG +
                            ": data_length=" +
                            realData.length()
            );

            DebugLogger.info(
                    context,
                    TAG +
                            ": PAYLOAD=" +
                            safeLog(payload)
            );

            /*
             * Пишем через временный файл,
             * чтобы daemon не увидел JSON
             * в процессе записи.
             */
            File tempFile =
                    new File(
                            COMMAND_FILE + ".tmp"
                    );

            FileWriter writer =
                    new FileWriter(
                            tempFile,
                            false
                    );

            writer.write(payload);
            writer.flush();
            writer.close();

            if (commandFile.exists()) {
                commandFile.delete();
            }

            if (!tempFile.renameTo(commandFile)) {

                DebugLogger.info(
                        context,
                        TAG +
                                ": COMMAND_RENAME_FAILED"
                );

                return null;
            }

            DebugLogger.info(
                    context,
                    TAG + ": COMMAND_SENT"
            );

            /*
             * Ждём только результат
             * с нашим operation_id.
             */
            long started =
                    System.currentTimeMillis();

            while (
                    System.currentTimeMillis()
                            - started <
                            TIMEOUT
            ) {

                if (!resultFile.exists()) {

                    Thread.sleep(100);
                    continue;
                }

                if (resultFile.length() <= 0) {

                    Thread.sleep(100);
                    continue;
                }

                try {

                    String raw =
                            readFile(resultFile);

                    if (
                            raw == null ||
                            raw.trim().isEmpty()
                    ) {

                        Thread.sleep(100);
                        continue;
                    }

                    DebugLogger.info(
                            context,
                            TAG +
                                    ": RESULT_RAW=" +
                                    safeLog(raw)
                    );

                    JSONObject result =
                            new JSONObject(raw);

                    String resultId =
                            result.optString(
                                    "operation_id",
                                    ""
                            );

                    /*
                     * Это может быть старый/
                     * чужой ответ.
                     */
                    if (!operationId.equals(resultId)) {

                        DebugLogger.info(
                                context,
                                TAG +
                                        ": WRONG_OPERATION_ID expected=" +
                                        operationId +
                                        " received=" +
                                        resultId
                        );

                        Thread.sleep(100);
                        continue;
                    }

                    String status =
                            result.optString(
                                    "status",
                                    ""
                            );

                    String response =
                            result.optString(
                                    "response",
                                    ""
                            );

                    DebugLogger.info(
                            context,
                            TAG +
                                    ": RESULT_STATUS=" +
                                    status
                    );

                    DebugLogger.info(
                            context,
                            TAG +
                                    ": RESPONSE_LENGTH=" +
                                    response.length()
                    );

                    if ("SUCCESS".equals(status)) {

                        DebugLogger.info(
                                context,
                                TAG +
                                        ": ROUND_TRIP_SUCCESS"
                        );

                        DebugLogger.info(
                                context,
                                TAG +
                                        ": RESPONSE=" +
                                        safeLog(response)
                        );

                        return response;
                    }

                    DebugLogger.info(
                            context,
                            TAG +
                                    ": RESULT_ERROR=" +
                                    safeLog(
                                            result.optString(
                                                    "error",
                                                    status
                                            )
                                    )
                    );

                    return null;

                } catch (Exception parseError) {

                    /*
                     * daemon мог находиться
                     * в момент записи result.json.
                     * Просто ждём следующую проверку.
                     */
                    DebugLogger.info(
                            context,
                            TAG +
                                    ": RESULT_NOT_READY=" +
                                    parseError.getClass()
                                            .getSimpleName()
                    );

                    Thread.sleep(100);
                }
            }

            DebugLogger.info(
                    context,
                    TAG + ": TIMEOUT"
            );

            return null;

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            DebugLogger.info(
                    context,
                    TAG + ": INTERRUPTED"
            );

            return null;

        } catch (Exception e) {

            DebugLogger.info(
                    context,
                    TAG +
                            ": EXCEPTION=" +
                            e.getClass().getName() +
                            ":" +
                            safeLog(e.getMessage())
            );

            return null;

        } finally {

            BUSY.set(false);
        }
    }

    public static String sendTaperData(
            Context context,
            String data
    ) {

        return sendAndReceive(
                context,
                "TAPER_DATA",
                data
        );
    }

    public static String testRoundTrip(
            Context context
    ) {

        String testData =
                "TAPER_ANDROID_REAL_TEST_" +
                        System.currentTimeMillis();

        DebugLogger.info(
                context,
                TAG +
                        ": TEST_DATA=" +
                        testData
        );

        String response =
                sendTaperData(
                        context,
                        testData
                );

        if (response == null) {

            DebugLogger.info(
                    context,
                    TAG +
                            ": TEST_RESULT=FAILED"
            );

            return null;
        }

        DebugLogger.info(
                context,
                TAG +
                        ": TEST_RESPONSE=" +
                        safeLog(response)
        );

        return response;
    }

    public static boolean isAvailable() {

        return new File(BASE).exists();
    }

    public static String getBasePath() {
        return BASE;
    }

    public static String getVersion() {
        return "TAPER_DATA_V1";
    }

    private static String readFile(
            File file
    ) throws Exception {

        FileInputStream input =
                new FileInputStream(file);

        try {

            StringBuilder sb =
                    new StringBuilder();

            byte[] buffer =
                    new byte[4096];

            int count;

            while (
                    (count = input.read(buffer)) != -1
            ) {

                sb.append(
                        new String(
                                buffer,
                                0,
                                count,
                                StandardCharsets.UTF_8
                        )
                );
            }

            return sb.toString();

        } finally {

            input.close();
        }
    }

    private static String safeLog(
            String value
    ) {

        if (value == null) {
            return "null";
        }

        String s =
                value.replace(
                        "\n",
                        "\\n"
                ).replace(
                        "\r",
                        "\\r"
                );

        if (s.length() > 1500) {

            return s.substring(
                    0,
                    1500
            ) + "...";
        }

        return s;
    }
}
