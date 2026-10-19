package com.test.accessibility3.modules.clicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

public class AutoClicker {

    private final AccessibilityService service;

    public AutoClicker(AccessibilityService service) {
        this.service = service;
    }

    public boolean click(
            AccessibilityNodeInfo node,
            StringBuilder debug) {

        if (node == null) {
            debug.append("AUTOCLICKER: NODE NULL\n");
            return false;
        }

        Rect r = new Rect();

        try {
            node.getBoundsInScreen(r);
        } catch (Exception e) {
            debug.append(
                    "AUTOCLICKER: BOUNDS ERROR: "
            ).append(e.getMessage()).append("\n");

            return false;
        }

        debug.append("\n=== AUTOCLICKER ===\n");

        debug.append("BUTTON BOUNDS: ")
                .append(r.left).append(",")
                .append(r.top)
                .append(" - ")
                .append(r.right).append(",")
                .append(r.bottom)
                .append("\n");

        debug.append("BUTTON CENTER: ")
                .append(r.centerX()).append(",")
                .append(r.centerY())
                .append("\n");

        boolean actionClick = false;

        try {

            actionClick =
                    node.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                    );

            debug.append("ACTION_CLICK: ")
                    .append(actionClick)
                    .append("\n");

        } catch (Exception e) {

            debug.append(
                    "ACTION_CLICK ERROR: "
            ).append(e.getMessage()).append("\n");
        }

        debug.append(
                "GESTURE: DISABLED\n"
        );

        debug.append(
                "AUTOCLICKER RESULT: "
        ).append(actionClick)
         .append("\n");

        return actionClick;
    }
}
