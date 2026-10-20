package com.test.accessibility3.modules.scanner;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

public class ChatGPTScanner {

    private static final String DEFAULT_TARGET_PACKAGE = "com.openai.chatgpt";

    private final AccessibilityService service;
    private final String targetPackage;

    /** Backward-compatible constructor for GPTSenderTest. */
    public ChatGPTScanner(AccessibilityService service) {
        this(service, DEFAULT_TARGET_PACKAGE);
    }

    /** Reusable constructor for projects that target another package. */
    public ChatGPTScanner(AccessibilityService service, String targetPackage) {
        this.service = service;
        this.targetPackage = targetPackage == null ? "" : targetPackage.trim();
    }

    public AccessibilityNodeInfo getRoot() {

        if (service == null) {
            return null;
        }

        try {
            for (AccessibilityWindowInfo window : service.getWindows()) {

                if (window == null) {
                    continue;
                }

                AccessibilityNodeInfo root = window.getRoot();

                if (root == null) {
                    continue;
                }

                CharSequence packageName = root.getPackageName();

                if (packageName != null
                        && targetPackage.equals(
                                packageName.toString())) {

                    return root;
                }

                root.recycle();
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    public boolean isAvailable() {
        return getRoot() != null;
    }

    public boolean isChatGPT(
            AccessibilityNodeInfo root) {

        if (root == null) {
            return false;
        }

        CharSequence packageName =
                root.getPackageName();

        return packageName != null
                && targetPackage.equals(
                        packageName.toString());
    }
}
