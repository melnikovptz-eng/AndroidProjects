package com.test.accessibility3.modules.response;

import android.content.Context;

import com.test.accessibility3.modules.debug.DebugLogger;
import com.test.accessibility3.modules.termux.TermuxBridge;

public final class TaperTermuxBridge {

    private static final String BEGIN = "⟦TAPER:BEGIN⟧";
    private static final String END = "⟦TAPER:END⟧";

    private static final String TAG = "TAPER";

    private TaperTermuxBridge() {
    }

    public static String handle(
            Context context,
            String text
    ) {

        DebugLogger.info(
                context,
                TAG +
                        ": CHECK length=" +
                        (text == null ? 0 : text.length())
        );

        if (text == null ||
                text.trim().isEmpty()) {

            DebugLogger.info(
                    context,
                    TAG + ": EMPTY"
            );

            return "TAPER: ПУСТО";
        }

        TaperResponseChecker checker =
                new TaperResponseChecker();

        TaperResponseChecker.Result result =
                checker.check(text);

        if (
                result ==
                        TaperResponseChecker.Result.NOT_FOUND
        ) {

            DebugLogger.info(
                    context,
                    TAG + ": NOT_FOUND"
            );

            return "TAPER: МЕТКИ НЕ НАЙДЕНЫ";
        }

        if (
                result ==
                        TaperResponseChecker.Result.LOADING
        ) {

            DebugLogger.info(
                    context,
                    TAG + ": LOADING"
            );

            return "TAPER: ОЖИДАЕТСЯ END";
        }

        int beginPos =
                text.indexOf(BEGIN);

        int endPos =
                text.indexOf(
                        END,
                        beginPos + BEGIN.length()
                );

        if (
                beginPos < 0 ||
                endPos < 0 ||
                endPos <= beginPos
        ) {

            DebugLogger.info(
                    context,
                    TAG + ": EXTRACTION_FAILED"
            );

            return "TAPER: ОШИБКА ИЗВЛЕЧЕНИЯ";
        }

        String command =
                text.substring(
                        beginPos + BEGIN.length(),
                        endPos
                ).trim();

        /*
         * Старый Taper формат мог содержать
         * экранированные \n.
         *
         * Преобразуем их в реальные переводы строк.
         */
        command =
                command.replace(
                        "\\n",
                        "\n"
                );

        DebugLogger.info(
                context,
                TAG +
                        ": COMPLETE"
        );

        DebugLogger.info(
                context,
                TAG +
                        ": COMMAND_EXTRACTED length=" +
                        command.length()
        );

        if (command.isEmpty()) {

            DebugLogger.info(
                    context,
                    TAG + ": COMMAND_EMPTY"
            );

            return "TAPER: КОМАНДА ПУСТА";
        }

        DebugLogger.info(
                context,
                TAG +
                        ": COMMAND_DATA=" +
                        oneLine(command)
        );

        /*
         * Новый рабочий мост:
         *
         * Taper
         *   ↓
         * TermuxBridge
         *   ↓
         * command.json
         *   ↓
         * termux_bridge_daemon.sh
         *   ↓
         * result.json
         *   ↓
         * TermuxBridge
         *   ↓
         * response
         */
        try {

            DebugLogger.info(
                    context,
                    TAG +
                            ": SEND_TO_TERMUX START"
            );

            String response =
                    TermuxBridge.sendAndReceive(
                            context,
                            "TAPER",
                            command
                    );

            if (response == null) {

                DebugLogger.info(
                        context,
                        TAG +
                                ": SEND_TO_TERMUX FAILED"
                );

                return "TAPER: ОШИБКА ПОЛУЧЕНИЯ ОТВЕТА";
            }

            DebugLogger.info(
                    context,
                    TAG +
                            ": RESPONSE_LENGTH=" +
                            response.length()
            );

            DebugLogger.info(
                    context,
                    TAG +
                            ": RESPONSE=" +
                            oneLine(response)
            );

            DebugLogger.info(
                    context,
                    TAG +
                            ": ROUND_TRIP_SUCCESS"
            );

            return response;

        } catch (Exception e) {

            DebugLogger.info(
                    context,
                    TAG +
                            ": EXCEPTION=" +
                            e.getClass().getName() +
                            ":" +
                            oneLine(e.getMessage())
            );

            return "TAPER: ОШИБКА: " +
                    e.getClass().getSimpleName();
        }
    }

    private static String oneLine(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
