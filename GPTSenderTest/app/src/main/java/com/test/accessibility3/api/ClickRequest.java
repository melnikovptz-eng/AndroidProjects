package com.test.accessibility3.api;

/** Immutable, project-neutral request to execute one UI click. */
public final class ClickRequest {
    private final int x;
    private final int y;
    private final String actionId;
    private final String expectedPackage;

    public ClickRequest(int x, int y, String actionId, String expectedPackage) {
        this.x = x;
        this.y = y;
        this.actionId = actionId == null ? "" : actionId.trim();
        this.expectedPackage = expectedPackage == null ? "" : expectedPackage.trim();
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public String getActionId() { return actionId; }
    public String getExpectedPackage() { return expectedPackage; }

    public boolean isValid() {
        return x >= 0 && y >= 0 && !actionId.isEmpty() && !expectedPackage.isEmpty();
    }
}
