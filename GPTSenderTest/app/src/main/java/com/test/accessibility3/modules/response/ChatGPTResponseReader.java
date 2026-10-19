package com.test.accessibility3.modules.response;

import android.view.accessibility.AccessibilityNodeInfo;

public class ChatGPTResponseReader {

    public String read(AccessibilityNodeInfo root) {
        if (root == null) {
            return "";
        }

        StringBuilder out = new StringBuilder();

        collectText(root, out);

        return out.toString().trim();
    }

    private void collectText(
            AccessibilityNodeInfo node,
            StringBuilder out
    ) {
        if (node == null) {
            return;
        }

        CharSequence text = node.getText();

        if (text != null) {
            String value = text.toString().trim();

            if (!value.isEmpty()) {
                if (out.length() > 0) {
                    out.append("\n");
                }

                out.append(value);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);

            if (child != null) {
                collectText(child, out);
                child.recycle();
            }
        }
    }
}
