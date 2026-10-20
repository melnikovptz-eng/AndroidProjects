package com.test.accessibility3;

import com.test.accessibility3.modules.taper.TaperClipboardReceiver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.test.accessibility3.modules.clicker.AutoClicker;
import com.test.accessibility3.modules.debug.DebugLogger;
import com.test.accessibility3.modules.finder.SendButtonFinder;
import com.test.accessibility3.modules.input.ChatGPTInput;
import com.test.accessibility3.modules.input.ChatGPTInputCleaner;
import com.test.accessibility3.modules.scanner.ChatGPTScanner;
import com.test.accessibility3.modules.response.TaperAutoReceiver;

public class TesterAccessibilityService extends AccessibilityService {

    private TaperClipboardReceiver taperClipboardReceiver;
    private BroadcastReceiver pmcaSendReceiver;
    private volatile boolean pmcaSendBusy;



    private static final String CHATGPT = "com.openai.chatgpt";

    private ChatGPTScanner scanner;
    private ChatGPTInput input;
    private ChatGPTInputCleaner inputCleaner;
    private SendButtonFinder finder;
    private AutoClicker clicker;
    private com.test.accessibility3.modules.overlay.GptSenderDebugOverlay debugOverlay;

    private SendButtonFinder.Result lastResult;

    private TaperAutoReceiver taperAutoReceiver;

    @Override
    public void onServiceConnected() {
        // GPT_DIAG_SERVICE_PATCH_V1
        GptSenderDiagnostics.log(this, "ACCESSIBILITY_SERVICE", "SERVICE_ON_CONNECTED_ENTER");

        super.onServiceConnected();
        
        GptSenderLog.write(this, "SERVICE_CONNECTED", "Accessibility service connected");
taperClipboardReceiver =
            new TaperClipboardReceiver(this);

        taperClipboardReceiver.start();

        DebugLogger.log(this, "TAPER", 
            "TAPER_CLIPBOARD: SERVICE_CONNECTED_AND_RECEIVER_STARTED"
        );

        TesterAccessibilityServiceHolder.service = this;

        scanner = new ChatGPTScanner(this);
        input = new ChatGPTInput();
        inputCleaner = new ChatGPTInputCleaner();
        finder = new SendButtonFinder();
        clicker = new AutoClicker(this);
        debugOverlay = new com.test.accessibility3.modules.overlay.GptSenderDebugOverlay(this);

        taperAutoReceiver =
                new TaperAutoReceiver(this);
    
        GptSenderLog.write(this, "SERVICE_READY", "Modules initialized");
        registerPmcaSendReceiver();
}


    private void registerPmcaSendReceiver() {
        if (pmcaSendReceiver != null) {
            return;
        }

        pmcaSendReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null
                        || !"com.test.gptsender.PMCA_SEND_RESULT".equals(intent.getAction())) {
                    return;
                }
                handlePmcaSendRequest(intent.getStringExtra("text"));
            }
        };

        IntentFilter filter = new IntentFilter("com.test.gptsender.PMCA_SEND_RESULT");
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(pmcaSendReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(pmcaSendReceiver, filter);
            }
            GptSenderLog.write(this, "PMCA_RECEIVER_READY", "PMCA result receiver registered");
        } catch (Exception e) {
            pmcaSendReceiver = null;
            GptSenderLog.write(this, "PMCA_RECEIVER_FAILED",
                    e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    private synchronized void handlePmcaSendRequest(String command) {
        if (command == null || command.trim().isEmpty()) {
            GptSenderLog.write(this, "PMCA_REQUEST_REJECTED", "Empty result text");
            return;
        }

        if (pmcaSendBusy) {
            GptSenderLog.write(this, "PMCA_REQUEST_REJECTED", "Another send operation is running");
            return;
        }

        pmcaSendBusy = true;
        GptSenderLog.write(this, "PMCA_REQUEST_RECEIVED", "length=" + command.length());

        Thread worker = new Thread(() -> {
            try {
                boolean prepared = prepareCommand(command);
                if (!prepared) {
                    GptSenderLog.write(this, "PMCA_PREPARE_FAILED",
                            "Text could not be inserted into ChatGPT input");
                    return;
                }

                GptSenderLog.write(this, "PMCA_PREPARE_SUCCESS",
                        "Text inserted; wait 1000ms before one-pass send search");

                // Даём интерфейсу ChatGPT время обновить поле ввода.
                Thread.sleep(1000L);

                boolean clicked = clickCurrentSend();
                GptSenderLog.write(
                        this,
                        clicked ? "PMCA_SEND_CLICK_ACCEPTED" : "PMCA_SEND_CLICK_FAILED",
                        clicked
                                ? "Accessibility click accepted; delivery not independently verified"
                                : "Send button not safely found or click failed"
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                GptSenderLog.write(this, "PMCA_SEND_INTERRUPTED", "Worker interrupted");
            } catch (Exception e) {
                GptSenderLog.write(this, "PMCA_SEND_EXCEPTION",
                        e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            } finally {
                pmcaSendBusy = false;
            }
        }, "PMCA-GPT-Send");

        worker.start();
    }

    @Override
    public void onDestroy() {
        if (debugOverlay != null) {
            debugOverlay.hide();
        }

        if (pmcaSendReceiver != null) {
            try {
                unregisterReceiver(pmcaSendReceiver);
            } catch (Exception ignored) {
            }
            pmcaSendReceiver = null;
        }

        if (TesterAccessibilityServiceHolder.service == this) {
            TesterAccessibilityServiceHolder.service = null;
        }

        GptSenderLog.write(this, "SERVICE_DESTROYED", "Accessibility service destroyed");
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        /*
         * ВАЖНО:
         *
         * Здесь НИЧЕГО автоматически не вводим.
         * Никаких MODULAR TEST.
         * Никакого startTest().
         *
         * Сервис только получает события Accessibility.
         * Реальные действия запускаются вручную.
         */
        if (event == null) return;
        if (event.getPackageName() == null) return;

        String pkg = event.getPackageName().toString();

        if (!CHATGPT.equals(pkg)) return;

        if (taperAutoReceiver == null) {
            return;
        }

        int type = event.getEventType();

        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
                type != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return;
        }

        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();

            if (root == null) {
                return;
            }

            String text =
                    new com.test.accessibility3.modules.response.ChatGPTResponseReader()
                            .read(root);

            root.recycle();

            if (text.isEmpty()) {
                return;
            }

            taperAutoReceiver.onAccessibilityText(text);

        } catch (Exception e) {
            GptSenderDiagnostics.exception(this, "ACCESSIBILITY_SERVICE", "CAUGHT_EXCEPTION", e);

            DebugLogger.info(
                    this,
                    "TAPER: AUTO_EVENT_EXCEPTION=" +
                            e.getClass().getSimpleName() + ":" +
                            String.valueOf(e.getMessage())
            );
        }
    }

    private String readSystemClipboard() {
        android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cb == null || !cb.hasPrimaryClip()) return "";
        android.content.ClipData clip = cb.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return "";
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        return text == null ? "" : text.toString();
    }

    public boolean prepareCommand(String command) {
        // GPT_DIAG_SERVICE_PATCH_V1
        GptSenderDiagnostics.log(this, "ACCESSIBILITY_SERVICE", "PREPARE_COMMAND_ENTER");


        
        GptSenderLog.write(this, "PREPARE_BEGIN", "message_length=" + (command == null ? 0 : command.length()));
DebugLogger.sendEvent(this, "PREPARE START");

        StringBuilder debug = new StringBuilder();

        debug.append("=== PREPARE CHATGPT COMMAND ===\n");

        if (scanner == null ||
                input == null ||
                inputCleaner == null) {

            debug.append("MODULES: NOT READY\n");
            DebugLogger.save(this, debug.toString());
            return false;
        }

        // GPTSENDER_FIXED_COORDINATES_V1: coordinates measured from the supplied screenshot.
        final int inputX = 280;
        final int inputY = 1393;
        GptSenderLog.write(this, "FIXED_INPUT_TAP_BEGIN", "x=" + inputX + ",y=" + inputY);
        if (debugOverlay != null) debugOverlay.showInput(inputX, inputY);
        boolean inputTapped = clicker != null && clicker.clickAt(inputX, inputY, debug);
        GptSenderLog.write(this, inputTapped ? "FIXED_INPUT_TAP_COMPLETED" : "FIXED_INPUT_TAP_FAILED",
                "x=" + inputX + ",y=" + inputY);
        if (!inputTapped) {
            debug.append("FIXED INPUT TAP FAILED at ").append(inputX).append(',').append(inputY).append('\\n');
            DebugLogger.save(this, debug.toString());
            return false;
        }

        AccessibilityNodeInfo root = scanner.getRoot();

        if (root == null) {

            debug.append("ROOT: NULL\n");
            DebugLogger.save(this, debug.toString());
            return false;
        }

        debug.append("ROOT: OK\n");

        AccessibilityNodeInfo edit = input.find(root);

        if (edit == null) {

            debug.append("EDITTEXT: NOT FOUND\n");
            DebugLogger.save(this, debug.toString());
            return false;
        }

        debug.append("EDITTEXT: FOUND\n");

        Rect rect = new Rect();
        edit.getBoundsInScreen(rect);

        debug.append("EDIT BOUNDS: ")
                .append(rect.left)
                .append(",")
                .append(rect.top)
                .append(" - ")
                .append(rect.right)
                .append(",")
                .append(rect.bottom)
                .append("\n");

        /*
         * Сначала полностью очищаем старое содержимое.
         */
        boolean cleared = inputCleaner.clear(
                edit,
                debug
        );

        if (!cleared) {

            debug.append("CLEAR: FAILED\n");
            DebugLogger.save(this, debug.toString());
            return false;
        }

        debug.append("CLEAR: OK\n");

        /*
         * После SET_TEXT ChatGPT/Compose может создать
         * новый EditText. Поэтому старый AccessibilityNodeInfo
         * больше не используем.
         *
         * Сразу получаем свежее дерево.
         */
        root = scanner.getRoot();

        if (root == null) {

            debug.append("ROOT AFTER CLEAR: NULL\n");
            DebugLogger.save(this, debug.toString());
            return false;
        }

        edit = input.find(root);

        if (edit == null) {

            debug.append("FRESH EDITTEXT: NOT FOUND\n");
            DebugLogger.save(this, debug.toString());
            return false;
        }

        debug.append("FRESH EDITTEXT: FOUND\n");

        /*
         * Пока тестируем только вставку.
         * Отправку здесь НЕ выполняем.
         */
        DebugLogger.info(this, "CLIPBOARD: RECEIVED_FOR_CHATGPT, length=" + (command == null ? 0 : command.length()));

        // REAL AUTOMATIC COPY TO ANDROID CLIPBOARD
        try {
            android.content.ClipboardManager cb =
                    (android.content.ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);

            if (cb == null) {
                DebugLogger.error(this, "CLIPBOARD: AUTO_COPY_FAILED, manager=null");
                return false;
            }

            cb.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                            "AccessibilityTester3",
                            command
                    )
            );

            DebugLogger.info(
                    this,
                    "CLIPBOARD: AUTO_COPIED, length=" + command.length()
            );

        } catch (Exception e) {
            GptSenderDiagnostics.exception(this, "ACCESSIBILITY_SERVICE", "CAUGHT_EXCEPTION", e);

            DebugLogger.error(
                    this,
                    "CLIPBOARD: AUTO_COPY_EXCEPTION=" +
                    e.getClass().getSimpleName() + ":" +
                    String.valueOf(e.getMessage())
            );
            return false;
        }

        DebugLogger.info(this, "CLIPBOARD: READ_FOR_CHATGPT, length=" + command.length());
        if (command.trim().isEmpty()) {
            DebugLogger.info(this, "CLIPBOARD: EMPTY_FOR_CHATGPT");
            debug.append("CLIPBOARD: EMPTY\n");
            DebugLogger.save(this, debug.toString());
            return false;
        }
        debug.append("CLIPBOARD: READ OK, length=").append(command.length()).append("\n");
        DebugLogger.sendData(this, command);

        boolean inserted = inputCleaner.insert(
                edit,
                command,
                debug
        );

        debug.append("INSERT: ")
                .append(inserted)
                .append("\n");

        DebugLogger.sendEvent(
                this,
                inserted ? "PREPARE INSERT SUCCESS" : "PREPARE INSERT FAILED"
        );

        DebugLogger.save(this, debug.toString());

        return inserted;
    }

    public boolean clickCurrentSend() {
        GptSenderDiagnostics.log(this, "ACCESSIBILITY_SERVICE", "CLICK_CURRENT_SEND_ENTER");
        GptSenderLog.write(this, "SEND_BEGIN", "Single-pass search after 1000ms wait");
        DebugLogger.sendEvent(this, "REQUEST");
        StringBuilder debug = new StringBuilder("=== SINGLE-PASS SEND ===\n");

        if (scanner == null || input == null || finder == null || clicker == null) {
            debug.append("MODULES: NOT READY\n");
            GptSenderLog.write(this, "SEND_ABORT", "Required module is null");
            DebugLogger.save(this, debug.toString());
            return false;
        }

        AccessibilityNodeInfo root = null;
        AccessibilityNodeInfo edit = null;
        try {
            root = scanner.getRoot();
            if (root == null) {
                GptSenderLog.write(this, "SEND_ABORT", "Active root is null");
                debug.append("ROOT: NULL\n");
                DebugLogger.save(this, debug.toString());
                if (debugOverlay != null) debugOverlay.showNotFound();
                return false;
            }

            edit = input.find(root);
            if (edit == null) {
                GptSenderLog.write(this, "INPUT_NOT_FOUND_AFTER_INSERT", "Fresh tree has no input field");
                debug.append("INPUT: NOT FOUND\n");
                DebugLogger.save(this, debug.toString());
                if (debugOverlay != null) debugOverlay.showNotFound();
                return false;
            }

            Rect inputBounds = new Rect();
            edit.getBoundsInScreen(inputBounds);
            debug.append("INPUT_BOUNDS=").append(inputBounds).append('\n');
            GptSenderLog.write(this, "INPUT_FOUND_AFTER_INSERT", "bounds=" + inputBounds);
            if (debugOverlay != null && !inputBounds.isEmpty())
                debugOverlay.showInput(inputBounds.centerX(), inputBounds.centerY());

            // GPTSENDER_FIXED_COORDINATES_V1: use the screenshot-measured send point.
            final int sendX = 602;
            final int sendY = 1394;
            debug.append("FIXED_SEND_POINT=").append(sendX).append(',').append(sendY).append('\\n');
            GptSenderLog.write(this, "FIXED_SEND_TAP_BEGIN", "x=" + sendX + ",y=" + sendY);
            if (debugOverlay != null) debugOverlay.showTarget(sendX, sendY);

            boolean clicked = clicker.clickAt(sendX, sendY, debug);
            if (debugOverlay != null) debugOverlay.setClickResult(clicked);
            GptSenderLog.write(this, clicked ? "FIXED_SEND_TAP_COMPLETED" : "FIXED_SEND_TAP_FAILED",
                "x=" + sendX + ",y=" + sendY
                    + "; gesture callback result=" + clicked
                    + "; message delivery not independently verified");
            DebugLogger.sendEvent(this, clicked ? "SUCCESS" : "FAILED");
            DebugLogger.save(this, debug.toString());
            return clicked;
        } catch (Exception e) {
            GptSenderDiagnostics.exception(this, "ACCESSIBILITY_SERVICE", "SEND_FLOW_EXCEPTION", e);
            GptSenderLog.write(this, "SEND_FLOW_EXCEPTION",
                e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            debug.append("EXCEPTION=").append(e.getClass().getSimpleName()).append('\n');
            DebugLogger.save(this, debug.toString());
            if (debugOverlay != null) debugOverlay.showNotFound();
            return false;
        } finally {
            if (edit != null) try { edit.recycle(); } catch (Exception ignored) {}
            if (root != null) try { root.recycle(); } catch (Exception ignored) {}
            if (lastResult != null && lastResult.node != null)
                try { lastResult.node.recycle(); } catch (Exception ignored) {}
        }
    }

    public static boolean prepareChatGPTCommand(String command) {

        if (!(TesterAccessibilityServiceHolder.service
                instanceof TesterAccessibilityService)) {

            return false;
        }

        TesterAccessibilityService service =
                (TesterAccessibilityService)
                        TesterAccessibilityServiceHolder.service;

        return service.prepareCommand(command);
    }

    public static boolean clickSend() {

        if (!(TesterAccessibilityServiceHolder.service
                instanceof TesterAccessibilityService)) {

            return false;
        }

        TesterAccessibilityService service =
                (TesterAccessibilityService)
                        TesterAccessibilityServiceHolder.service;

        return service.clickCurrentSend();
    }

    public static String readResult(
            AccessibilityService service
    ) {
        return DebugLogger.read(service);
    }

    private void dumpNode(
            AccessibilityNodeInfo node,
            StringBuilder out,
            String name
    ) {

        if (node == null) return;

        Rect r = new Rect();
        node.getBoundsInScreen(r);

        out.append(name).append("\n");

        out.append("CLASS: ")
                .append(node.getClassName())
                .append("\n");

        out.append("TEXT: ")
                .append(node.getText())
                .append("\n");

        out.append("DESC: ")
                .append(node.getContentDescription())
                .append("\n");

        out.append("CLICKABLE: ")
                .append(node.isClickable())
                .append("\n");

        out.append("FOCUSABLE: ")
                .append(node.isFocusable())
                .append("\n");

        out.append("FOCUSED: ")
                .append(node.isFocused())
                .append("\n");

        out.append("ENABLED: ")
                .append(node.isEnabled())
                .append("\n");

        out.append("EDITABLE: ")
                .append(node.isEditable())
                .append("\n");

        out.append("BOUNDS: ")
                .append(r.left)
                .append(",")
                .append(r.top)
                .append(" - ")
                .append(r.right)
                .append(",")
                .append(r.bottom)
                .append("\n");

        out.append("SIZE: ")
                .append(r.width())
                .append(" x ")
                .append(r.height())
                .append("\n");

        out.append("CENTER: ")
                .append(r.centerX())
                .append(",")
                .append(r.centerY())
                .append("\n");
    }

    @Override
    public void onInterrupt() {
        // GPT_DIAG_SERVICE_PATCH_V1
        GptSenderDiagnostics.log(this, "ACCESSIBILITY_SERVICE", "SERVICE_ON_INTERRUPT");

    
        GptSenderLog.write(this, "SERVICE_INTERRUPTED", "Accessibility service interrupted");
}

}
