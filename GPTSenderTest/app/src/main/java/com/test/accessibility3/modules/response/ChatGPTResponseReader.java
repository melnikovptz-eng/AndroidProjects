package com.test.accessibility3.modules.response;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;

/** Bounded iterative reader; never lets a deeply nested UI tree overflow the stack. */
public class ChatGPTResponseReader {

    private static final int MAX_NODES = 1500;
    private static final long MAX_SCAN_NS = 400_000_000L;

    public String read(AccessibilityNodeInfo root) {
        if (root == null) return "";

        StringBuilder out = new StringBuilder();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        ArrayList<AccessibilityNodeInfo> owned = new ArrayList<>();
        long deadline = System.nanoTime() + MAX_SCAN_NS;
        int visited = 0;

        queue.add(root);
        try {
            while (!queue.isEmpty()
                    && visited < MAX_NODES
                    && System.nanoTime() < deadline) {
                AccessibilityNodeInfo node = queue.removeFirst();
                visited++;

                try {
                    CharSequence text = node.getText();
                    if (text != null) {
                        String value = text.toString().trim();
                        if (!value.isEmpty()) {
                            if (out.length() > 0) out.append("\n");
                            out.append(value);
                        }
                    }

                    int count = node.getChildCount();
                    for (int i = 0; i < count; i++) {
                        if (visited + queue.size() >= MAX_NODES
                                || System.nanoTime() >= deadline) break;
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) {
                            owned.add(child);
                            queue.addLast(child);
                        }
                    }
                } catch (Exception ignored) {
                    // Continue with the remaining nodes if one node becomes stale.
                }
            }
            return out.toString().trim();
        } finally {
            // The root belongs to the caller. Child references are owned here.
            for (AccessibilityNodeInfo node : owned) {
                try { node.recycle(); } catch (Exception ignored) {}
            }
        }
    }
}
