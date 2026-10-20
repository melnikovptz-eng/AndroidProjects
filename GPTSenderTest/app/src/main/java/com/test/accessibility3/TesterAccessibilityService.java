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

import java.util.ArrayDeque;

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


    /** Shows a diagnostic marker only; it never performs a tap. Coordinates are physical screen pixels. */
    public static boolean showFixedTargetDiagnostic(android.content.Context context, int x, int y) {
        android.content.Context app = context == null ? null : context.getApplicationContext();
        TesterAccessibilityService service = null;
        android.accessibilityservice.AccessibilityService current = TesterAccessibilityServiceHolder.service;
        if (current instanceof TesterAccessibilityService) service = (TesterAccessibilityService) current;

        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        android.view.WindowManager wm = app == null ? null
                : (android.view.WindowManager) app.getSystemService(android.content.Context.WINDOW_SERVICE);
        if (wm != null) wm.getDefaultDisplay().getRealMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        boolean inBounds = width > 0 && height > 0 && x >= 0 && y >= 0 && x < width && y < height;
        boolean overlayAllowed = app != null && android.provider.Settings.canDrawOverlays(app);
        String details = "x=" + x + "; y=" + y + "; real_width_px=" + width
                + "; real_height_px=" + height + "; in_bounds=" + inBounds
                + "; overlay_permission=" + overlayAllowed
                + "; coordinate_space=physical_display_pixels; tap_performed=false";
        GptSenderLog.write(app, "COORDINATE_DIAGNOSTIC_CHECK", details);

        if (service == null) {
            GptSenderLog.write(app, "COORDINATE_DIAGNOSTIC_FAILED",
                    "Accessibility service is not connected; " + details);
            return false;
        }
        if (!overlayAllowed || !inBounds || service.debugOverlay == null) {
            GptSenderLog.write(app, "COORDINATE_DIAGNOSTIC_FAILED",
                    "Cannot show marker; " + details);
            return false;
        }
        service.debugOverlay.showCoordinate(x, y);
        GptSenderLog.write(app, "COORDINATE_MARKER_REQUESTED",
                details + "; marker_color=red; marker_auto_hide_ms=15000");
        return true;
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
        GptSenderDiagnostics.log(this, "ACCESSIBILITY_SERVICE", "PREPARE_COMMAND_ENTER");
        GptSenderLog.write(this, "PREPARE_BEGIN",
                "message_length=" + (command == null ? 0 : command.length()));

        StringBuilder debug = new StringBuilder("=== PREPARE CHATGPT COMMAND ===\n");
        DebugLogger.sendEvent(this, "PREPARE START");

        if (command == null || command.trim().isEmpty()) {
            GptSenderLog.write(this, "PREPARE_REJECTED", "Command is null or empty");
            debug.append("COMMAND: EMPTY\n");
            DebugLogger.save(this, debug.toString());
            return false;
        }
        if (scanner == null || input == null || inputCleaner == null || clicker == null) {
            GptSenderLog.write(this, "PREPARE_ABORT", "Required module is not ready");
            debug.append("MODULES: NOT READY\n");
            DebugLogger.save(this, debug.toString());
            return false;
        }

        AccessibilityNodeInfo root = null;
        AccessibilityNodeInfo edit = null;
        try {
            root = scanner.getRoot();
            if (root == null) {
                GptSenderLog.write(this, "PREPARE_ABORT", "ChatGPT accessibility root is null");
                debug.append("ROOT: NULL\n");
                DebugLogger.save(this, debug.toString());
                return false;
            }

            if (!CHATGPT.equals(String.valueOf(root.getPackageName()))) {
                GptSenderLog.write(this, "PREPARE_ABORT",
                        "Wrong active package: " + root.getPackageName());
                debug.append("PACKAGE: NOT CHATGPT\n");
                DebugLogger.save(this, debug.toString());
                return false;
            }

            edit = input.find(root);
            if (edit == null) {
                GptSenderLog.write(this, "PREPARE_ABORT", "ChatGPT input field not found");
                debug.append("EDITTEXT: NOT FOUND\n");
                DebugLogger.save(this, debug.toString());
                if (debugOverlay != null) debugOverlay.showNotFound();
                return false;
            }

            Rect inputBounds = new Rect();
            edit.getBoundsInScreen(inputBounds);
            GptSenderLog.write(this, "INPUT_FOUND",
                    "bounds=" + inputBounds + "; focused=" + edit.isFocused()
                    + "; clickable=" + edit.isClickable());
            debug.append("INPUT_BOUNDS=").append(inputBounds).append('\n');

            if (inputBounds.isEmpty()) {
                GptSenderLog.write(this, "PREPARE_ABORT", "Input bounds are empty");
                debug.append("INPUT: INVALID BOUNDS\n");
                DebugLogger.save(this, debug.toString());
                return false;
            }

            if (debugOverlay != null) {
                debugOverlay.showInput(inputBounds.centerX(), inputBounds.centerY());
            }

            // Use the detected input node instead of a hard-coded point.
            // The input position changes with keyboard visibility and screen layout.
            if (!edit.isFocused()) {
                boolean focused = edit.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                GptSenderLog.write(this, focused ? "INPUT_FOCUS_ACCEPTED" : "INPUT_FOCUS_ACTION_REJECTED",
                        "bounds=" + inputBounds);
                debug.append("INPUT_FOCUS_ACTION=").append(focused).append('\n');

                if (focused) {
                    // Opening the keyboard may rebuild the Compose accessibility tree.
                    edit.recycle();
                    edit = null;
                    root.recycle();
                    root = null;
                    root = scanner.getRoot();
                    if (root == null) {
                        GptSenderLog.write(this, "PREPARE_ABORT", "Root missing after focusing input");
                        debug.append("ROOT AFTER FOCUS: NULL\n");
                        DebugLogger.save(this, debug.toString());
                        return false;
                    }
                    edit = input.find(root);
                    if (edit == null) {
                        GptSenderLog.write(this, "PREPARE_ABORT", "Input missing after focusing");
                        debug.append("INPUT AFTER FOCUS: NOT FOUND\n");
                        DebugLogger.save(this, debug.toString());
                        return false;
                    }
                    Rect refreshedBounds = new Rect();
                    edit.getBoundsInScreen(refreshedBounds);
                    GptSenderLog.write(this, "INPUT_REFRESHED_AFTER_FOCUS",
                            "bounds=" + refreshedBounds + "; focused=" + edit.isFocused());
                }
            }

            if (!inputCleaner.clear(edit, debug)) {
                GptSenderLog.write(this, "PREPARE_ABORT", "Input clear action failed");
                debug.append("CLEAR: FAILED\n");
                DebugLogger.save(this, debug.toString());
                return false;
            }
            GptSenderLog.write(this, "INPUT_CLEAR_ACCEPTED", "Accessibility SET_TEXT empty accepted");

            // Refresh the accessibility tree after changing Compose text; old nodes may be stale.
            edit.recycle();
            edit = null;
            root.recycle();
            root = null;

            root = scanner.getRoot();
            if (root == null) {
                GptSenderLog.write(this, "PREPARE_ABORT", "Root disappeared after clear");
                debug.append("ROOT AFTER CLEAR: NULL\n");
                DebugLogger.save(this, debug.toString());
                return false;
            }

            edit = input.find(root);
            if (edit == null) {
                GptSenderLog.write(this, "PREPARE_ABORT", "Fresh input field not found after clear");
                debug.append("FRESH EDITTEXT: NOT FOUND\n");
                DebugLogger.save(this, debug.toString());
                return false;
            }

            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) {
                GptSenderLog.write(this, "PREPARE_ABORT", "ClipboardManager is null");
                debug.append("CLIPBOARD: MANAGER NULL\n");
                DebugLogger.save(this, debug.toString());
                return false;
            }

            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PMCA result", command));
            GptSenderLog.write(this, "CLIPBOARD_COPY_SUCCESS", "length=" + command.length());

            boolean inserted = inputCleaner.insert(edit, command, debug);
            GptSenderLog.write(this, inserted ? "INPUT_INSERT_ACTION_ACCEPTED" : "INPUT_INSERT_ACTION_FAILED",
                    "length=" + command.length());
            if (!inserted) {
                debug.append("INSERT: FAILED\n");
                DebugLogger.save(this, debug.toString());
                DebugLogger.sendEvent(this, "PREPARE INSERT FAILED");
                return false;
            }

            // Verify the actual text exposed by ChatGPT after the insertion action.
            edit.recycle();
            edit = null;
            root.recycle();
            root = null;
            root = scanner.getRoot();
            if (root == null) {
                GptSenderLog.write(this, "PREPARE_VERIFY_FAILED", "Root missing after insertion");
                debug.append("VERIFY ROOT: NULL\n");
                DebugLogger.save(this, debug.toString());
                return false;
            }

            edit = input.find(root);
            if (edit == null) {
                GptSenderLog.write(this, "PREPARE_VERIFY_FAILED", "Input missing after insertion");
                debug.append("VERIFY INPUT: NOT FOUND\n");
                DebugLogger.save(this, debug.toString());
                return false;
            }

            CharSequence actualText = edit.getText();
            String actual = actualText == null ? "" : actualText.toString();
            boolean verified = command.equals(actual);
            GptSenderLog.write(this, verified ? "INPUT_INSERT_VERIFIED" : "INPUT_INSERT_VERIFY_MISMATCH",
                    "expected_length=" + command.length() + "; actual_length=" + actual.length()
                    + "; exact_match=" + verified);
            debug.append("INSERT_VERIFY exact_match=").append(verified)
                    .append(" expected_chars=").append(command.length())
                    .append(" actual_chars=").append(actual.length()).append('\n');

            DebugLogger.sendEvent(this, verified ? "PREPARE INSERT SUCCESS" : "PREPARE INSERT VERIFY FAILED");
            DebugLogger.save(this, debug.toString());
            return verified;
        } catch (Exception e) {
            GptSenderDiagnostics.exception(this, "ACCESSIBILITY_SERVICE", "PREPARE_FLOW_EXCEPTION", e);
            GptSenderLog.write(this, "PREPARE_EXCEPTION",
                    e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            debug.append("EXCEPTION=").append(e.getClass().getSimpleName()).append('\n');
            DebugLogger.save(this, debug.toString());
            return false;
        } finally {
            if (edit != null) try { edit.recycle(); } catch (Exception ignored) {}
            if (root != null) try { root.recycle(); } catch (Exception ignored) {}
        }
    }

    public boolean clickCurrentSend() {
        GptSenderDiagnostics.log(this, "ACCESSIBILITY_SERVICE", "CLICK_CURRENT_SEND_ENTER");
        GptSenderLog.write(this, "SEND_BEGIN", "Dynamic accessibility search for ChatGPT send button");
        DebugLogger.sendEvent(this, "REQUEST");
        StringBuilder debug = new StringBuilder("=== DYNAMIC CHATGPT SEND ===\n");

        if (scanner == null || input == null || finder == null || clicker == null) {
            GptSenderLog.write(this, "SEND_ABORT", "Required module is not ready");
            debug.append("MODULES: NOT READY\n");
            DebugLogger.save(this, debug.toString());
            return false;
        }

        AccessibilityNodeInfo root = null;
        AccessibilityNodeInfo edit = null;
        SendButtonFinder.Result found = null;
        try {
            root = scanner.getRoot();
            if (root == null) {
                GptSenderLog.write(this, "SEND_ABORT", "ChatGPT accessibility root is null");
                debug.append("ROOT: NULL\n");
                DebugLogger.save(this, debug.toString());
                if (debugOverlay != null) debugOverlay.showNotFound();
                return false;
            }

            if (!CHATGPT.equals(String.valueOf(root.getPackageName()))) {
                GptSenderLog.write(this, "SEND_ABORT", "Wrong package: " + root.getPackageName());
                debug.append("PACKAGE: NOT CHATGPT\n");
                DebugLogger.save(this, debug.toString());
                return false;
            }

            edit = input.find(root);
            if (edit == null) {
                GptSenderLog.write(this, "SEND_ABORT", "Input field not found");
                debug.append("INPUT: NOT FOUND\n");
                DebugLogger.save(this, debug.toString());
                if (debugOverlay != null) debugOverlay.showNotFound();
                return false;
            }

            Rect inputBounds = new Rect();
            edit.getBoundsInScreen(inputBounds);
            CharSequence value = edit.getText();
            String inputValue = value == null ? "" : value.toString();
            boolean inputReady = edit.isEnabled() && !inputValue.trim().isEmpty() && !inputBounds.isEmpty();
            GptSenderLog.write(this, inputReady ? "SEND_INPUT_CONFIRMED" : "SEND_INPUT_REJECTED",
                    "enabled=" + edit.isEnabled() + "; chars=" + inputValue.length()
                    + "; bounds=" + inputBounds);
            debug.append("INPUT_BOUNDS=").append(inputBounds).append('\n')
                    .append("INPUT_CHARS=").append(inputValue.length()).append('\n');

            if (!inputReady) {
                GptSenderLog.write(this, "SEND_ABORT", "Input empty, disabled, or bounds invalid");
                debug.append("SEND_ABORT: INPUT NOT READY\n");
                DebugLogger.save(this, debug.toString());
                if (debugOverlay != null) debugOverlay.setClickResult(false);
                return false;
            }

            found = finder.find(root, edit, debug);
            if (found == null || !found.found()) {
                String reason = found == null ? "FINDER_RETURNED_NULL" : found.reason;
                GptSenderLog.write(this, "SEND_BUTTON_NOT_FOUND", "reason=" + reason);
                debug.append("SEND BUTTON: NOT FOUND reason=").append(reason).append('\n');
                DebugLogger.save(this, debug.toString());
                if (debugOverlay != null) debugOverlay.showNotFound();
                return false;
            }

            Rect target = new Rect(found.bounds);
            if (target.isEmpty()) {
                GptSenderLog.write(this, "SEND_ABORT", "Finder returned empty button bounds");
                debug.append("SEND BUTTON: EMPTY BOUNDS\n");
                DebugLogger.save(this, debug.toString());
                return false;
            }

            int targetX = target.centerX();
            int targetY = target.centerY();
            GptSenderLog.write(this, "SEND_BUTTON_FOUND",
                    "reason=" + found.reason + "; score=" + found.score
                    + "; candidates=" + found.candidateNumber + "; bounds=" + target);
            if (debugOverlay != null) debugOverlay.showTarget(targetX, targetY);

            // Prefer semantic Accessibility click; fallback is limited to the freshly
            // detected clickable target. No fixed coordinates are used.
            boolean clicked = found.node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            GptSenderLog.write(this, clicked ? "SEND_ACCESSIBILITY_CLICK_ACCEPTED" : "SEND_ACCESSIBILITY_CLICK_REJECTED",
                    "reason=" + found.reason + "; target=" + target);

            if (!clicked) {
                GptSenderLog.write(this, "SEND_GESTURE_FALLBACK_BEGIN", "target=" + target);
                clicked = clicker.clickAt(targetX, targetY, debug);
            }

            if (debugOverlay != null) debugOverlay.setClickResult(clicked);
            GptSenderLog.write(this, clicked ? "SEND_CLICK_ACCEPTED" : "SEND_CLICK_FAILED",
                    "target=" + target + "; reason=" + found.reason
                    + "; message delivery is not independently verified");
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
            if (found != null && found.node != null) try { found.node.recycle(); } catch (Exception ignored) {}
            if (edit != null) try { edit.recycle(); } catch (Exception ignored) {}
            if (root != null) try { root.recycle(); } catch (Exception ignored) {}
        }
    }

    /**
     * Fail-closed state check before a coordinate tap.
     * Only an enabled, clickable node whose bounds contain the exact target and whose
     * accessibility text/description/id identifies SEND is accepted. Unknown state = no tap.
     */
    private boolean verifySendButtonReady(AccessibilityNodeInfo root, int x, int y,
                                          StringBuilder debug) {
        if (root == null) {
            debug.append("SEND_STATE_CHECK=REJECTED_ROOT_NULL\n");
            return false;
        }

        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        boolean confirmed = false;
        final int maxNodes = 1000;

        while (!queue.isEmpty() && visited < maxNodes) {
            AccessibilityNodeInfo node = queue.removeFirst();
            boolean isRoot = node == root;
            visited++;
            try {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                int childCount = node.getChildCount();

                if (bounds.contains(x, y)) {
                    String text = String.valueOf(node.getText());
                    String desc = String.valueOf(node.getContentDescription());
                    String id = "";
                    try {
                        id = String.valueOf(node.getViewIdResourceName());
                    } catch (Exception ignored) {
                    }
                    String labels = (text + " " + desc + " " + id).toLowerCase(java.util.Locale.ROOT);
                    boolean voice = labels.contains("voice") || labels.contains("microphone")
                            || labels.contains("audio") || labels.contains("голос")
                            || labels.contains("микрофон") || labels.contains("диктов");
                    boolean semanticSend = labels.contains("send") || labels.contains("submit")
                            || labels.contains("отправ") || labels.contains("послать");
                    debug.append("TARGET_NODE bounds=").append(bounds)
                            .append(" class=").append(node.getClassName())
                            .append(" enabled=").append(node.isEnabled())
                            .append(" clickable=").append(node.isClickable())
                            .append(" labels=").append(labels).append('\n');

                    if (!voice && semanticSend && node.isEnabled() && node.isClickable()) {
                        confirmed = true;
                        debug.append("SEND_STATE_CHECK=CONFIRMED_SEMANTIC_SEND_BUTTON\n");
                    }
                }

                for (int i = 0; i < childCount && visited + queue.size() < maxNodes; i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) queue.addLast(child);
                }
            } catch (Exception e) {
                debug.append("SEND_STATE_NODE_ERROR=").append(e.getClass().getSimpleName()).append('\n');
            } finally {
                if (!isRoot) {
                    try { node.recycle(); } catch (Exception ignored) {}
                }
            }
        }

        debug.append("SEND_STATE_NODES_SCANNED=").append(visited).append('\n');
        if (!confirmed) {
            debug.append("SEND_STATE_CHECK=REJECTED_UNCONFIRMED_OR_VOICE_MODE\n");
        }
        return confirmed;
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
