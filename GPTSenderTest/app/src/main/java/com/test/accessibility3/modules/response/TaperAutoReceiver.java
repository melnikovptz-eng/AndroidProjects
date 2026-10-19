package com.test.accessibility3.modules.response;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.test.accessibility3.TesterAccessibilityService;
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

            DebugLogger.info(
                context,
                TAG + ": DUPLICATE_IGNORED CMD_ID=" + id
            );

            return;
        }

        if (!running.compareAndSet(false, true)) {

            DebugLogger.info(
                context,
                TAG + ": BUSY_IGNORED CMD_ID=" + id
            );

            return;
        }

        lastCompleteSignature = signature;

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

                    DebugLogger.info(
                        context,
                        TAG + ": NULL_RESPONSE CMD_ID=" +
                        commandId
                    );

                    running.set(false);
                    return;
                }

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

        DebugLogger.info(
            context,
            TAG + ": INSERT_RESPONSE CMD_ID=" +
            commandId +
            " length=" +
            taggedResponse.length()
        );

        boolean inserted =
            TesterAccessibilityService.prepareChatGPTCommand(
                taggedResponse
            );

        if (!inserted) {

            DebugLogger.info(
                context,
                TAG + ": INSERT_FAILED CMD_ID=" +
                commandId
            );

            running.set(false);

            handler.postDelayed(
                () -> retryInsert(
                    commandId,
                    taggedResponse
                ),
                RETRY_DELAY_MS
            );

            return;
        }

        DebugLogger.info(
            context,
            TAG + ": INSERT_OK CMD_ID=" +
            commandId
        );

        handler.postDelayed(() -> {

            boolean sent =
                TesterAccessibilityService.clickSend();

            DebugLogger.info(
                context,
                TAG + ": SEND=" +
                sent +
                " CMD_ID=" +
                commandId
            );

            running.set(false);

        }, 700L);
    }

    private void retryInsert(
        long commandId,
        String response
    ) {

        if (!running.get()) {
            return;
        }

        boolean inserted =
            TesterAccessibilityService.prepareChatGPTCommand(
                response
            );

        DebugLogger.info(
            context,
            TAG + ": RETRY_INSERT=" +
            inserted +
            " CMD_ID=" +
            commandId
        );

        if (inserted) {

            handler.postDelayed(() -> {

                boolean sent =
                    TesterAccessibilityService.clickSend();

                DebugLogger.info(
                    context,
                    TAG + ": RETRY_SEND=" +
                    sent +
                    " CMD_ID=" +
                    commandId
                );

                running.set(false);

            }, 700L);

        } else {

            running.set(false);
        }
    }
}
