package com.test.accessibility3.modules.input;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;

public class ChatGPTInput {

    private static final int MAX_NODES = 800;
    private static final long MAX_SEARCH_NS = 350_000_000L;

    public AccessibilityNodeInfo find(AccessibilityNodeInfo root) {
        if (root == null) return null;

        final long deadline = System.nanoTime() + MAX_SEARCH_NS;
        final ArrayDeque<AccessibilityNodeInfo> queue =
                new ArrayDeque<>();

        queue.add(root);
        int visited = 0;

        while (!queue.isEmpty()
                && visited < MAX_NODES
                && System.nanoTime() < deadline) {

            AccessibilityNodeInfo node = queue.removeFirst();
            visited++;

            try {
                CharSequence className = node.getClassName();

                if ("android.widget.EditText".contentEquals(
                            className == null ? "" : className)
                        && node.isEditable()
                        && node.isEnabled()) {
                    return node;
                }

                int count = node.getChildCount();

                for (int i = 0; i < count; i++) {
                    if (visited + queue.size() >= MAX_NODES
                            || System.nanoTime() >= deadline) {
                        break;
                    }

                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        queue.addLast(child);
                    }
                }
            } catch (Exception ignored) {
                // Недоступный узел пропускаем.
            }
        }

        return null;
    }
}
