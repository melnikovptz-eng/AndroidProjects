package com.test.accessibility3.modules.input;

import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

public class ChatGPTInputCleaner {

    public boolean clear(AccessibilityNodeInfo edit, StringBuilder debug) {
        if (edit == null) {
            log(debug, "INPUT CLEAR: EDITTEXT NULL");
            return false;
        }

        int beforeLength = text(edit).length();

        log(debug, "=== INPUT CLEAR ===");
        log(debug, "BEFORE_LENGTH: " + beforeLength);

        Bundle args = new Bundle();
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                ""
        );

        boolean result = edit.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                args
        );

        log(debug, "CLEAR RESULT: " + result);

        return result;
    }

    public boolean insert(
            AccessibilityNodeInfo edit,
            String command,
            StringBuilder debug
    ) {
        if (edit == null) {
            log(debug, "INPUT INSERT: EDITTEXT NULL");
            return false;
        }

        if (command == null) {
            command = "";
        }

        log(debug, "=== INPUT INSERT ===");
        log(debug, "COMMAND LENGTH: " + command.length());

        Bundle args = new Bundle();
        args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                command
        );

        boolean result = edit.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                args
        );

        log(debug, "INSERT RESULT: " + result);

        return result;
    }

    public boolean clearAndInsert(
            AccessibilityNodeInfo edit,
            String command,
            StringBuilder debug
    ) {
        if (edit == null) {
            log(debug, "INPUT: EDITTEXT NULL");
            return false;
        }

        int beforeLength = text(edit).length();

        log(debug, "=== CLEAR + INSERT ===");
        log(debug, "OLD_TEXT_LENGTH: " + beforeLength);

        if (!clear(edit, debug)) {
            log(debug, "INPUT: CLEAR FAILED");
            return false;
        }

        /*
         * После очистки Compose может пересоздать EditText.
         * Поэтому этот метод намеренно не пытается использовать
         * старый объект для отправки.
         */
        log(debug, "CLEAR OK");
        log(debug, "READY FOR FRESH EDITTEXT");

        return true;
    }

    private String text(AccessibilityNodeInfo node) {
        CharSequence value = node.getText();
        return value == null ? "" : value.toString();
    }

    private void log(StringBuilder debug, String text) {
        if (debug != null) {
            debug.append(text).append("\n");
        }
    }
}
