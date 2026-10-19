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
                .append(e.getClass().getSimpleName()).append(": ")
                .append(String.valueOf(e.getMessage())).append('\n');
            return false;
        }
        debug.append("\n=== AUTOCLICKER ===\n");
        debug.append("TAP TARGET BOUNDS: ").append(bounds).append('\n');
        if (bounds.isEmpty()) {
            debug.append("AUTOCLICKER: EMPTY BOUNDS\n");
            return false;
        }
        int x = bounds.centerX(), y = bounds.centerY();
        debug.append("ACTUAL TAP POINT: ").append(x).append(',').append(y).append('\n');

        // Tap the exact coordinate shown by the overlay first.
        boolean tapped = performTap(x, y, debug);
        if (tapped) {
            debug.append("AUTOCLICKER RESULT: coordinate tap completed\n");
            return true;
        }
        debug.append("COORDINATE TAP FAILED; TRY ACTION_CLICK FALLBACK\n");
        try {
            boolean action = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            debug.append("ACTION_CLICK_FALLBACK: ").append(action).append('\n');
            return action;
        } catch (Exception e) {
            debug.append("ACTION_CLICK FALLBACK ERROR: ")
                .append(e.getClass().getSimpleName()).append(": ")
                .append(String.valueOf(e.getMessage())).append('\n');
            return false;
        }
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
