package com.test.accessibility3.modules.clicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

        Rect bounds = new Rect();

        try {
            node.getBoundsInScreen(bounds);
        } catch (Exception e) {
            debug.append("AUTOCLICKER: BOUNDS ERROR: ")
                    .append(e.getClass().getSimpleName())
                    .append(": ")
                    .append(String.valueOf(e.getMessage()))
                    .append("\n");
            return false;
        }

        debug.append("\n=== AUTOCLICKER ===\n");
        debug.append("BUTTON BOUNDS: ").append(bounds).append("\n");

        if (bounds.isEmpty()) {
            debug.append("AUTOCLICKER: EMPTY BOUNDS\n");
            return false;
        }

        debug.append("BUTTON CENTER: ")
                .append(bounds.centerX()).append(",")
                .append(bounds.centerY()).append("\n");

        boolean actionClick = false;

        try {
            actionClick = node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
            );
            debug.append("ACTION_CLICK: ")
                    .append(actionClick).append("\n");
        } catch (Exception e) {
            debug.append("ACTION_CLICK ERROR: ")
                    .append(e.getClass().getSimpleName())
                    .append(": ")
                    .append(String.valueOf(e.getMessage()))
                    .append("\n");
        }

        if (actionClick) {
            debug.append("GESTURE: SKIPPED; ACTION_CLICK returned true\n");
            debug.append("AUTOCLICKER RESULT: true (action accepted, delivery unconfirmed)\n");
            return true;
        }

        debug.append("GESTURE: TRYING COORDINATE TAP\n");

        boolean gestureAccepted = performTap(
                bounds.centerX(),
                bounds.centerY(),
                debug
        );

        debug.append("AUTOCLICKER RESULT: ")
                .append(gestureAccepted)
                .append(" (gesture acceptance does not confirm message delivery)\n");

        return gestureAccepted;
    }

    private boolean performTap(
            int x,
            int y,
            StringBuilder debug) {

        final Path path = new Path();
        path.moveTo(x, y);

        final GestureDescription gesture =
                new GestureDescription.Builder()
                        .addStroke(
                                new GestureDescription.StrokeDescription(
                                        path, 0, 100
                                )
                        )
                        .build();

        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicBoolean completedSuccessfully = new AtomicBoolean(false);

        try {
            boolean accepted = service.dispatchGesture(
                    gesture,
                    new AccessibilityService.GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription description) {
                            completedSuccessfully.set(true);
                            debug.append("GESTURE CALLBACK: COMPLETED\n");
                            finished.countDown();
                        }

                        @Override
                        public void onCancelled(GestureDescription description) {
                            completedSuccessfully.set(false);
                            debug.append("GESTURE CALLBACK: CANCELLED\n");
                            finished.countDown();
                        }
                    },
                    new Handler(Looper.getMainLooper())
            );

            debug.append("DISPATCH_GESTURE_ACCEPTED: ")
                    .append(accepted).append("\n");

            if (!accepted) {
                debug.append("GESTURE ERROR: dispatchGesture returned false\n");
                return false;
            }

            boolean callbackReceived = finished.await(2, TimeUnit.SECONDS);

            if (!callbackReceived) {
                debug.append("GESTURE ERROR: callback timeout\n");
                return false;
            }

            debug.append("GESTURE COMPLETED SUCCESSFULLY: ")
                    .append(completedSuccessfully.get()).append("\n");

            return completedSuccessfully.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            debug.append("GESTURE ERROR: interrupted\n");
            return false;
        } catch (Exception e) {
            debug.append("GESTURE ERROR: ")
                    .append(e.getClass().getSimpleName())
                    .append(": ")
                    .append(String.valueOf(e.getMessage()))
                    .append("\n");
            return false;
        }
    }
}
