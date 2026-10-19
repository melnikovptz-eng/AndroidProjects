package com.test.accessibility3.modules.input;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;

/** Bounded breadth-first search for the editable ChatGPT input. */
public class ChatGPTInput {

    private static final int MAX_NODES = 800;
    private static final long MAX_SEARCH_NS = 350_000_000L;

    public AccessibilityNodeInfo find(AccessibilityNodeInfo root) {
        if (root == null) return null;

        final long deadline = System.nanoTime() + MAX_SEARCH_NS;
        final ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        final ArrayList<AccessibilityNodeInfo> owned = new ArrayList<>();
        AccessibilityNodeInfo found = null;

        queue.add(root);
        int visited = 0;
        try {
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
                        // Caller owns this returned reference and must recycle it.
                        found = node;
                        return found;
                    }

                    int count = node.getChildCount();
                    for (int i = 0; i < count; i++) {
                        if (visited + queue.size() >= MAX_NODES
                                || System.nanoTime() >= deadline) {
                            break;
                        }

                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) {
                            owned.add(child);
                            queue.addLast(child);
                        }
                    }
                } catch (Exception ignored) {
                    // Skip an inaccessible node without aborting the scan.
                }
            }
            return null;
        } finally {
            // getChild() creates owned references. Release every one except the
            // returned match; the root reference belongs to the caller.
            for (AccessibilityNodeInfo node : owned) {
                if (node != found) {
                    try { node.recycle(); } catch (Exception ignored) {}
                }
            }
        }
    }
}
