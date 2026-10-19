package com.test.accessibility3.modules.finder;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Locale;

/** One tree traversal; labelled send buttons first, then expected zones around input. */
public class SendButtonFinder {
    public static class Result {
        public AccessibilityNodeInfo node;
        public Rect bounds = new Rect();
        public int score;
        public int candidateNumber;
        public String reason;
        public boolean found() { return node != null; }
    }

    public Result find(AccessibilityNodeInfo root, AccessibilityNodeInfo edit, StringBuilder debug) {
        Result result = new Result();
        if (root == null || edit == null) {
            result.reason = "ROOT_OR_EDIT_NULL";
            debug.append("FINDER: ROOT_OR_EDIT_NULL\n");
            return result;
        }
        Rect er = new Rect(), rr = new Rect();
        edit.getBoundsInScreen(er);
        root.getBoundsInScreen(rr);
        debug.append("INPUT_BOUNDS=").append(er).append('\n');
        debug.append("ROOT_BOUNDS=").append(rr).append('\n');
        if (er.isEmpty()) {
            result.reason = "EMPTY_INPUT_BOUNDS";
            return result;
        }

        ArrayList<AccessibilityNodeInfo> nodes = new ArrayList<>();
        boolean complete = collect(root, nodes, System.nanoTime() + 500_000_000L);
        debug.append("TREE_NODES=").append(nodes.size()).append(" complete=").append(complete).append('\n');

        int screenRight = rr.isEmpty() ? er.right + Math.max(240, er.width()) : rr.right;
        int band = Math.max(100, Math.min(220, er.height()));
        AccessibilityNodeInfo best = null;
        Rect bestRect = new Rect();
        int bestScore = Integer.MIN_VALUE, candidates = 0;
        String bestReason = "";

        for (AccessibilityNodeInfo n : nodes) {
            if (n == null || !n.isEnabled()) continue;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.isEmpty() || r.width() < 12 || r.height() < 12
                    || r.width() > 220 || r.height() > 220) continue;

            String text = norm(n.getText()), desc = norm(n.getContentDescription()), id = "";
            try { id = norm(n.getViewIdResourceName()); } catch (Exception ignored) {}
            String labels = text + " " + desc + " " + id;
            if (containsAny(labels, "microphone", "voice", "audio", "dictat",
                    "голос", "микрофон", "диктов", "record", "speak")) {
                debug.append("SKIP_VOICE bounds=").append(r).append(" labels=").append(labels).append('\n');
                continue;
            }

            int dy = Math.abs(r.centerY() - er.centerY());
            boolean right = r.centerX() > er.centerX();
            boolean sameRow = dy <= band;
            boolean rightZone = r.centerX() >= er.left + (int)(er.width() * 0.55f)
                    && r.centerX() <= screenRight + 8
                    && r.top <= er.bottom + band && r.bottom >= er.top - band;
            boolean semantic = containsAny(labels, "send", "отправ", "submit");
            if (!semantic && !(right && sameRow && rightZone)) continue;

            int score = semantic ? 10000 : 0;
            score -= Math.min(1500, dy * 5);
            score += Math.min(900, Math.max(0, r.centerX() - er.centerX()) / 2);
            score -= Math.abs(r.width() - r.height()) * 2;
            if (rightZone) score += 350;
            if (sameRow) score += 350;
            if (n.isClickable()) score += 250;
            candidates++;
            debug.append("ZONE_CANDIDATE ").append(candidates)
                .append(" zone=").append(semantic ? "SEMANTIC" : "RIGHT_INPUT_ZONE")
                .append(" bounds=").append(r).append(" labels=").append(labels)
                .append(" score=").append(score).append('\n');
            if (score > bestScore) {
                bestScore = score; best = n; bestRect.set(r);
                bestReason = semantic ? "SEMANTIC_SEND_MATCH" : "EXPECTED_RIGHT_ZONE";
            }
        }

        result.candidateNumber = candidates;
        if (best == null) {
            result.reason = complete ? "NO_CANDIDATE_IN_EXPECTED_ZONES" : "TREE_SCAN_LIMIT_NO_CANDIDATE";
            debug.append("FINDER_RESULT=NOT_FOUND reason=").append(result.reason).append('\n');
            return result;
        }
        result.node = AccessibilityNodeInfo.obtain(best);
        result.bounds.set(bestRect);
        result.score = bestScore;
        result.reason = bestReason;
        debug.append("FINDER_RESULT=FOUND\n").append("FINDER_REASON=").append(result.reason).append('\n')
             .append("FINDER_BOUNDS=").append(bestRect).append('\n')
             .append("FINDER_CANDIDATES=").append(candidates).append('\n');
        return result;
    }

    private static String norm(CharSequence v) {
        return String.valueOf(v == null ? "" : v).toLowerCase(Locale.ROOT);
    }
    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
    private static boolean collect(AccessibilityNodeInfo root,
            ArrayList<AccessibilityNodeInfo> out, long deadline) {
        final int maxNodes = 1000;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        if (root == null) return true;
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty()) {
            if (visited >= maxNodes || System.nanoTime() >= deadline) return false;
            AccessibilityNodeInfo node = queue.removeFirst();
            visited++;
            try {
                if (node.isClickable() && node.isEnabled()) out.add(node);
                int count = node.getChildCount();
                for (int i = 0; i < count; i++) {
                    if (visited + queue.size() >= maxNodes || System.nanoTime() >= deadline) return false;
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) queue.addLast(child);
                }
            } catch (Exception ignored) {}
        }
        return true;
    }
}
