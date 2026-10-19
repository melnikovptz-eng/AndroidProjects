package com.test.accessibility3.modules.debug;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.UUID;

public class TermuxBridge {

    private static final String TAG = "TERMUX_BRIDGE";

    private final File baseDir =
            new File("/storage/emulated/0/Download/Taper");

    private final File commandFile =
            new File(baseDir, "command.json");

    private final File resultFile =
            new File(baseDir, "result.json");

    private static final long TIMEOUT_MS = 15000;

    public TermuxBridge(Context context) {
        log("INIT");
        log("BASE=" + baseDir.getAbsolutePath());
    }

    private void log(String s) {
        Log.d(TAG, "[TERMUX] " + s);
    }

    private String readFile(File file) throws Exception {

        log("READ_START=" + file.getAbsolutePath());
        log("READ_EXISTS=" + file.exists());
        log("READ_LENGTH=" + file.length());

        BufferedReader br =
                new BufferedReader(new FileReader(file));

        StringBuilder sb = new StringBuilder();

        String line;

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        br.close();

        log("READ_OK length=" + sb.length());

        return sb.toString();
    }

    private void writeFile(File file, String text)
            throws Exception {

        File parent = file.getParentFile();

        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new Exception(
                        "MKDIR_FAILED:" +
                        parent.getAbsolutePath()
                );
            }
        }

        FileWriter fw = new FileWriter(file, false);

        fw.write(text);
        fw.flush();
        fw.close();

        log("WRITE_OK=" + file.getAbsolutePath());
        log("WRITE_SIZE=" + file.length());
    }

    public synchronized Result send(
            String type,
            String command,
            String data) {

        final String operationId =
                UUID.randomUUID().toString();

        final long timestamp =
                System.currentTimeMillis();

        log("========================================");
        log("SEND_START");
        log("operation_id=" + operationId);
        log("timestamp=" + timestamp);
        log("type=" + type);
        log("command=" + command);
        log("data=" + data);

        try {

            if (!baseDir.exists()) {

                if (!baseDir.mkdirs()) {

                    return Result.error(
                            operationId,
                            "BASE_CREATE_FAILED"
                    );
                }
            }

            /*
             * КРИТИЧНО:
             * удаляем старый результат ДО отправки
             */
            if (resultFile.exists()) {

                log("DELETE_OLD_RESULT");

                if (!resultFile.delete()) {

                    return Result.error(
                            operationId,
                            "OLD_RESULT_DELETE_FAILED"
                    );
                }
            }

            JSONObject json =
                    new JSONObject();

            json.put(
                    "operation_id",
                    operationId
            );

            json.put(
                    "timestamp",
                    timestamp
            );

            json.put(
                    "type",
                    type
            );

            json.put(
                    "command",
                    command
            );

            json.put(
                    "data",
                    data
            );

            String commandText =
                    json.toString();

            log("COMMAND_JSON=" + commandText);

            writeFile(
                    commandFile,
                    commandText
            );

            log("COMMAND_SENT");

            long start =
                    System.currentTimeMillis();

            while (
                    System.currentTimeMillis()
                            - start
                            < TIMEOUT_MS) {

                if (resultFile.exists()) {

                    log("RESULT_DETECTED");

                    try {

                        String raw =
                                readFile(resultFile);

                        log("RESULT_RAW=" + raw);

                        JSONObject result =
                                new JSONObject(raw);

                        String resultId =
                                result.optString(
                                        "operation_id",
                                        ""
                                );

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

                        log("EXPECTED_ID="
                                + operationId);

                        log("RESULT_ID="
                                + resultId);

                        /*
                         * Старый результат НИКОГДА
                         * не принимаем.
                         */
                        if (!operationId.equals(resultId)) {

                            log(
                                "RESULT_OLD_OR_WRONG_ID"
                            );

                            /*
                             * Удаляем чужой результат
                             * и продолжаем ждать.
                             */
                            resultFile.delete();

                            continue;
                        }

                        if ("SUCCESS".equals(status)) {

                            log("RESULT_SUCCESS");
                            log("RESPONSE=" + response);

                            return Result.success(
                                    operationId,
                                    response
                            );
                        }

                        log("RESULT_STATUS="
                                + status);

                        return Result.error(
                                operationId,
                                status
                        );

                    } catch (Exception e) {

                        log("RESULT_READ_ERROR="
                                + e.getMessage());

                        /*
                         * Файл мог находиться
                         * в момент записи daemon.
                         * Не падаем сразу.
                         */
                    }
                }

                Thread.sleep(100);
            }

            log("TIMEOUT");

            return Result.error(
                    operationId,
                    "TIMEOUT"
            );

        } catch (Exception e) {

            log("EXCEPTION="
                    + e.getClass().getName());

            log("MESSAGE="
                    + e.getMessage());

            return Result.error(
                    operationId,
                    "EXCEPTION:"
                            + e.getMessage()
            );
        }
    }

    public Result test() {

        String data =
                "ACCESSIBILITY_TEST_"
                + System.currentTimeMillis();

        log("TEST_DATA=" + data);

        Result result =
                send(
                        "COMMAND",
                        "TEST",
                        data
                );

        log("TEST_STATUS="
                + result.status);

        log("TEST_RESPONSE="
                + result.response);

        return result;
    }

    public static class Result {

        public final String operationId;
        public final String status;
        public final String response;

        private Result(
                String operationId,
                String status,
                String response) {

            this.operationId = operationId;
            this.status = status;
            this.response = response;
        }

        public static Result success(
                String id,
                String response) {

            return new Result(
                    id,
                    "SUCCESS",
                    response
            );
        }

        public static Result error(
                String id,
                String error) {

            return new Result(
                    id,
                    "ERROR",
                    error
            );
        }
    }
}
