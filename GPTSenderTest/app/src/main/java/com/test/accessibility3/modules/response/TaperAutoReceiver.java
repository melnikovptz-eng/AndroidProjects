package com.test.accessibility3.modules.response;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.test.accessibility3.TesterAccessibilityService;
import com.test.accessibility3.GptSenderLog;
import com.test.accessibility3.modules.debug.DebugLogger;
import com.test.accessibility3.modules.taper.TaperProtocol;

import java.util.concurrent.atomic.AtomicBoolean;

public final class TaperAutoReceiver {

    private static final String TAG = "TAPER";
    private static final long START_DELAY_MS = 350L;
    private static final long RETRY_DELAY_MS = 500L;

    private final Context context;
    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private final AtomicBoolean running =
        new AtomicBoolean(false);

    private String lastCompleteSignature = "";

    public TaperAutoReceiver(Context context) {
        this.context = context.getApplicationContext();
    }

    public void onAccessibilityText(String text) {

        if (text == null || text.trim().isEmpty()) {
            return;
        }

        if (!TaperProtocol.isTagged(text)) {
            return;
        }

        Long id = TaperProtocol.extractId(text);

        if (id == null) {
            GptSenderLog.write(context, "TAPER_COMMAND_REJECTED", "reason=missing_cmd_id");
            DebugLogger.info(
                context,
                TAG + ": REJECT_NO_CMD_ID"
            );
            return;
        }

        String command =
            TaperProtocol.extractPayload(text);

        if (command == null ||
            command.trim().isEmpty()) {
            GptSenderLog.write(context, "TAPER_COMMAND_REJECTED",
                "cmd_id=" + id + "; reason=empty_payload");
            DebugLogger.info(
                context,
                TAG + ": REJECT_EMPTY_COMMAND CMD_ID=" + id
            );
            return;
        }

        String signature =
            id + ":" +
            Integer.toHexString(command.hashCode()) +
            ":" + command.length();

        if (signature.equals(lastCompleteSignature)) {
            GptSenderLog.write(context, "TAPER_COMMAND_IGNORED",
                "cmd_id=" + id + "; reason=duplicate_signature");
            DebugLogger.info(
                context,
                TAG + ": DUPLICATE_IGNORED CMD_ID=" + id
            );

            return;
        }

        if (!running.compareAndSet(false, true)) {
            GptSenderLog.write(context, "TAPER_COMMAND_IGNORED",
                "cmd_id=" + id + "; reason=operation_busy");
            DebugLogger.info(
                context,
                TAG + ": BUSY_IGNORED CMD_ID=" + id
            );

            return;
        }

        lastCompleteSignature = signature;
        GptSenderLog.write(context, "TAPER_COMMAND_ACCEPTED",
            "cmd_id=" + id + "; length=" + command.length());

        DebugLogger.info(
            context,
            TAG + ": COMMAND_ACCEPTED CMD_ID=" + id +
            " length=" + command.length()
        );

        final long commandId = id;

        handler.postDelayed(
            () -> execute(commandId, command),
            START_DELAY_MS
        );
    }

    private void execute(long commandId, String command) {

        new Thread(() -> {

            try {

                DebugLogger.info(
                    context,
                    TAG + ": TERMUX_START CMD_ID=" + commandId
                );

                String taggedCommand =
                    TaperProtocol.tagCommand(
                        commandId,
                        command
                    );

                String response =
                    TaperTermuxBridge.handle(
                        context,
                        taggedCommand
                    );

                if (response == null) {
                    GptSenderLog.write(context, "TAPER_TERMUX_FAILED",
                        "cmd_id=" + commandId + "; reason=NULL_RESPONSE");

                    DebugLogger.info(
                        context,
                        TAG + ": NULL_RESPONSE CMD_ID=" +
                        commandId
                    );

                    running.set(false);
                    return;
                }

                GptSenderLog.write(context, "TAPER_TERMUX_SUCCESS",
                    "cmd_id=" + commandId + "; response_length=" + response.length());

                DebugLogger.info(
                    context,
                    TAG + ": TERMUX_RESPONSE CMD_ID=" +
                    commandId +
                    " length=" +
                    response.length()
                );

                handler.post(
                    () -> sendResponseToChatGPT(
                        commandId,
                        response
                    )
                );

            } catch (Exception e) {

                DebugLogger.info(
                    context,
                    TAG + ": EXCEPTION CMD_ID=" +
                    commandId +
                    " " +
                    e.getClass().getSimpleName() +
                    ":" +
                    String.valueOf(e.getMessage())
                );

                GptSenderLog.write(context, "TAPER_EXECUTION_EXCEPTION",
                    "cmd_id=" + commandId + "; "
                        + e.getClass().getSimpleName() + ": "
                        + String.valueOf(e.getMessage()));
                running.set(false);
            }

        }, "TaperAutoReceiver").start();
    }

    private void sendResponseToChatGPT(
        long commandId,
        String response
    ) {

        String taggedResponse =
            TaperProtocol.tagResponse(
                commandId,
                response
            );

        GptSenderLog.write(context, "TAPER_CHATGPT_INSERT_BEGIN",
            "cmd_id=" + commandId + "; text_length=" + taggedResponse.length());

        DebugLogger.info(
            context,
            TAG + ": INSERT_RESPONSE CMD_ID=" +
            commandId +
            " length=" +
            taggedResponse.length()
        );

        boolean inserted = false;
        try {
            inserted = TesterAccessibilityService.prepareChatGPTCommand(taggedResponse);
        } catch (Exception e) {
            GptSenderLog.write(context, "TAPER_CHATGPT_INSERT_EXCEPTION",
                "cmd_id=" + commandId + "; "
                    + e.getClass().getSimpleName() + ": "
                    + String.valueOf(e.getMessage()));
        }

        if (!inserted) {
            GptSenderLog.write(context, "TAPER_CHATGPT_INSERT_FAILED",
                "cmd_id=" + commandId);

            DebugLogger.info(
                context,
                TAG + ": INSERT_FAILED CMD_ID=" +
                commandId
            );

            // Keep the operation locked while the scheduled retry is pending.
            // Clearing this flag here makes retryInsert() exit immediately.
            handler.postDelayed(
                () -> retryInsert(
                    commandId,
                    taggedResponse
                ),
                RETRY_DELAY_MS
            );

            return;
        }

        GptSenderLog.write(context, "TAPER_CHATGPT_INSERT_SUCCESS",
            "cmd_id=" + commandId + "; text_length=" + taggedResponse.length());

        DebugLogger.info(
            context,
            TAG + ": INSERT_OK CMD_ID=" +
            commandId
        );

        handler.postDelayed(() -> new Thread(() -> {
            boolean sent = false;
            try {
                // clickCurrentSend waits for the gesture callback; do not run it
                // on the main looper, where that callback must execute.
                sent = TesterAccessibilityService.clickSend();
                GptSenderLog.write(context,
                    sent ? "TAPER_SEND_GESTURE_ACCEPTED" : "TAPER_SEND_GESTURE_FAILED",
                    "cmd_id=" + commandId + "; accepted=" + sent
                        + "; delivery_confirmation=NOT_VERIFIED");
                DebugLogger.info(context,
                    TAG + ": SEND=" + sent + " CMD_ID=" + commandId);
            } catch (Exception e) {
                GptSenderLog.write(context, "TAPER_SEND_EXCEPTION",
                    "cmd_id=" + commandId + "; "
                        + e.getClass().getSimpleName() + ": "
                        + String.valueOf(e.getMessage()));
            } finally {
                running.set(false);
            }
        }, "TaperAutoSend-" + commandId).start(), 700L);
    }

    private void retryInsert(
        long commandId,
        String response
    ) {

        if (!running.get()) {
            GptSenderLog.write(context, "TAPER_RETRY_SKIPPED",
                "cmd_id=" + commandId + "; reason=operation_not_running");
            return;
        }

        GptSenderLog.write(context, "TAPER_RETRY_BEGIN",
            "cmd_id=" + commandId + "; text_length=" + response.length());

        boolean inserted = false;
        try {
            inserted = TesterAccessibilityService.prepareChatGPTCommand(response);
        } catch (Exception e) {
            GptSenderLog.write(context, "TAPER_RETRY_EXCEPTION",
                "cmd_id=" + commandId + "; "
                    + e.getClass().getSimpleName() + ": "
                    + String.valueOf(e.getMessage()));
            DebugLogger.info(context,
                TAG + ": RETRY_EXCEPTION CMD_ID=" + commandId + " "
                    + e.getClass().getSimpleName());
        }

        DebugLogger.info(
            context,
            TAG + ": RETRY_INSERT=" +
            inserted +
            " CMD_ID=" +
            commandId
        );

        if (inserted) {

            handler.postDelayed(() -> new Thread(() -> {
                boolean sent = false;
                try {
                    sent = TesterAccessibilityService.clickSend();
                    GptSenderLog.write(context,
                        sent ? "TAPER_RETRY_SEND_GESTURE_ACCEPTED" : "TAPER_RETRY_SEND_GESTURE_FAILED",
                        "cmd_id=" + commandId + "; accepted=" + sent
                            + "; delivery_confirmation=NOT_VERIFIED");
                    DebugLogger.info(context,
                        TAG + ": RETRY_SEND=" + sent + " CMD_ID=" + commandId);
                } catch (Exception e) {
                    GptSenderLog.write(context, "TAPER_RETRY_SEND_EXCEPTION",
                        "cmd_id=" + commandId + "; "
                            + e.getClass().getSimpleName() + ": "
                            + String.valueOf(e.getMessage()));
                } finally {
                    running.set(false);
                }
            }, "TaperAutoRetrySend-" + commandId).start(), 700L);

        } else {
            GptSenderLog.write(context, "TAPER_RETRY_FAILED",
                "cmd_id=" + commandId + "; reason=insert_failed");
            running.set(false);
        }
    }
}
